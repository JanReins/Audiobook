package com.janreins.audiobook.data

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.janreins.audiobook.data.model.AudioTrack
import com.janreins.audiobook.data.model.Audiobook
import com.janreins.audiobook.player.ChapterIndex
import com.janreins.audiobook.player.ChapterMetadata
import com.janreins.audiobook.player.RawChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared by the UI and playback service; all file and extraction work runs on IO. */
@OptIn(UnstableApi::class)
object ChapterRepository {
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()
    private val requested = mutableMapOf<String, List<String>>()
    private val mutableIndexes = MutableStateFlow<Map<String, ChapterIndex>>(emptyMap())
    val indexes = mutableIndexes.asStateFlow()

    fun init(context: Context) { this.context = context.applicationContext }
    fun key(documentId: String, sizeBytes: Long, lastModified: Long) =
        "v1|" + DurationCache.key(documentId, sizeBytes, lastModified)

    private fun file(key: String): File = File(context.filesDir, "chapters/${hash(key)}.json")
    private fun hash(key: String) = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun read(key: String): List<RawChapter>? = synchronized(cacheLock) {
        try {
            val array = JSONArray(AtomicFile(file(key)).openRead().bufferedReader().use { it.readText() })
            (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                RawChapter(entry.getLong("startMs"), entry.getLong("endMs"),
                    if (entry.isNull("title")) null else entry.getString("title"), entry.optBoolean("hidden"))
            }
        } catch (_: Exception) { null }
    }

    fun write(key: String, chapters: List<RawChapter>): Unit = synchronized(cacheLock) {
        val target = file(key)
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            val array = JSONArray()
            chapters.forEach { array.put(JSONObject().put("startMs", it.startMs).put("endMs", it.endMs)
                .put("title", it.title ?: JSONObject.NULL).put("hidden", it.hidden)) }
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    fun prune(seenKeys: Set<String>) {
        if (!::context.isInitialized) return
        synchronized(cacheLock) {
            val names = seenKeys.mapTo(mutableSetOf()) { "${hash(it)}.json" }
            File(context.filesDir, "chapters").listFiles()?.forEach {
                if (it.name.removeSuffix(".bak").removeSuffix(".new") !in names) it.delete()
            }
        }
        // A rescan may have replaced a file, so permit rebuilding its book index.
        synchronized(this) { requested.clear() }
    }

    @Synchronized fun ensureLoaded(book: Audiobook) {
        if (!::context.isInitialized) return
        val tracks = book.tracks.ifEmpty { listOf(AudioTrack(book.uri, book.id.removePrefix("file:"),
            book.fileName, book.title, book.durationMs, book.sizeBytes, book.lastModified)) }
        val keys = tracks.map { key(it.documentId, it.sizeBytes, it.lastModified) }
        if (requested[book.id] == keys) return
        requested[book.id] = keys
        val titles = tracks.map { it.title }
        mutableIndexes.update { it + (book.id to ChapterIndex.build(tracks.map { null to it.durationMs }, titles)) }
        scope.launch {
            var failed = false
            val raw = tracks.mapIndexed { i, track ->
                val chapters = read(keys[i]) ?: try {
                    extract(track).also { write(keys[i], it) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true; emptyList() }
                chapters to track.durationMs
            }
            val index = ChapterIndex.build(raw, titles)
            synchronized(this@ChapterRepository) {
                if (requested[book.id] == keys) {
                    mutableIndexes.update { it + (book.id to index) }
                    if (failed) requested.remove(book.id) // Retry a failed extraction when the book is opened again.
                }
            }
        }
    }

    private suspend fun extract(track: AudioTrack): List<RawChapter> {
        val retriever = withContext(Dispatchers.Main) {
            MetadataRetriever.Builder(context, MediaItem.fromUri(track.uri)).build()
        }
        try {
            val groups = retriever.retrieveTrackGroups().await()
            // Formats within an adaptive group share chapters; use one audio format per group.
            return (0 until groups.length).flatMap { i ->
                val group = groups[i]
                if (group.type == C.TRACK_TYPE_AUDIO) ChapterMetadata.map(group.getFormat(0).metadata) else emptyList()
            }
        } finally { retriever.close() }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
        Futures.addCallback(this, object : FutureCallback<T> {
            override fun onSuccess(result: T) { continuation.resume(result) }
            override fun onFailure(t: Throwable) { continuation.resumeWithException(t) }
        }, MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(true) }
    }
}
