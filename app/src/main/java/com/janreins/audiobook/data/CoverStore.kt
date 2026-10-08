package com.janreins.audiobook.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.janreins.audiobook.data.model.Audiobook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class CoverModel(
    val bookId: String,
    val title: String,
    val firstTrackUri: Uri,
    val firstTrackKey: String,
    val folderCoverUri: Uri?,
    val folderCoverKey: String?
)

fun Audiobook.coverModel(): CoverModel {
    val first = tracks.firstOrNull()
    return CoverModel(id, title, first?.uri ?: uri,
        DurationCache.key(first?.documentId ?: id, first?.sizeBytes ?: sizeBytes, first?.lastModified ?: 0L),
        coverUri, coverKey)
}

class CoverStore(
    context: Context,
    private val embeddedArtReader: (Uri) -> ByteArray? = { uri -> readEmbeddedArt(context, uri) }
) {
    private val context = context.applicationContext
    private val cacheDirectory = File(context.cacheDir, "covers")

    suspend fun thumbnailFile(model: CoverModel): File? = withContext(Dispatchers.IO) {
        // Fast path without the lock: cache hits and remembered "no art" never wait behind an extraction.
        val key = hashOf(cacheKey(model))
        File(cacheDirectory, "$key.jpg").takeIf { it.isFile }?.let { return@withContext it }
        if (File(cacheDirectory, "$key.none").isFile) return@withContext null
        // Serialize cache writes and pruning, including simultaneous requests for the same book.
        cacheMutex.withLock {
            var temp: File? = null
            var bitmap: Bitmap? = null
            try {
                check(cacheDirectory.isDirectory || cacheDirectory.mkdirs())
                val hash = key
                val image = File(cacheDirectory, "$hash.jpg")
                val none = File(cacheDirectory, "$hash.none")
                if (image.isFile) return@withLock image
                if (none.isFile) return@withLock null

                bitmap = embeddedArtReader(model.firstTrackUri)?.let { decodeDownsampled(it, 512) }
                if (bitmap == null) {
                    bitmap = model.folderCoverUri?.let { decodeDownsampled(it, 512) }
                }
                if (bitmap == null) {
                    // An unreadable folder stream throws; it must remain retryable, without a marker.
                    temp = File.createTempFile(hash, ".tmp", cacheDirectory)
                    check(temp.renameTo(none))
                    trimCache()
                    return@withLock null
                }
                val resolved = bitmap
                val temporary = File.createTempFile(hash, ".tmp", cacheDirectory)
                temp = temporary
                temporary.outputStream().use { check(resolved.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
                check(temporary.renameTo(image))
                trimCache()
                image
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                bitmap?.recycle()
                temp?.delete()
            }
        }
    }

    fun decodeDownsampled(bytes: ByteArray, maxPx: Int): Bitmap? = BitmapDecoding.decodeDownsampled(bytes, maxPx)
    fun decodeDownsampled(uri: Uri, maxPx: Int): Bitmap? = BitmapDecoding.decodeDownsampled(context, uri, maxPx)

    private fun trimCache() {
        val files = cacheDirectory.listFiles()?.filter { it.isFile } ?: return
        if (files.size > 400) files.sortedBy { it.lastModified() }.take(files.size - 300).forEach { it.delete() }
    }

    companion object {
        private val cacheMutex = Mutex()
        fun cacheKey(model: CoverModel): String = "v1|${model.firstTrackKey}|${model.folderCoverKey ?: "-"}"

        private fun hashOf(key: String): String = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        private fun readEmbeddedArt(context: Context, uri: Uri): ByteArray? {
            var retriever: MediaMetadataRetriever? = null
            return try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                try { retriever?.release() } catch (_: Exception) { /* Ignore cleanup errors. */ }
            }
        }
    }
}

/** Bounds are read first, so large artwork is never decoded at its original resolution. */
internal object BitmapDecoding {
    fun decodeDownsampled(bytes: ByteArray, maxPx: Int): Bitmap? = decode(maxPx) { options ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun decodeDownsampled(context: Context, uri: Uri, maxPx: Int): Bitmap? = decode(maxPx) { options ->
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open cover: $uri")
        stream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private inline fun decode(maxPx: Int, read: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        require(maxPx > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (longest.toLong() > maxPx.toLong() * 2 * sample) sample *= 2
        val bitmap = read(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val decodedLongest = maxOf(bitmap.width, bitmap.height)
        if (decodedLongest <= maxPx) return bitmap
        val scale = maxPx.toFloat() / decodedLongest
        return try {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } finally {
            bitmap.recycle()
        }
    }
}
