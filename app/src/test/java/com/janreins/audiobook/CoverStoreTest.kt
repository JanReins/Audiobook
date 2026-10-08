package com.janreins.audiobook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.CoverModel
import com.janreins.audiobook.data.CoverStore
import com.janreins.audiobook.data.DurationCache
import com.janreins.audiobook.data.coverModel
import com.janreins.audiobook.data.model.AudioTrack
import com.janreins.audiobook.data.model.Audiobook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CoverStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var cache: File
    private val model = CoverModel("book", "Dune", Uri.parse("content://books/first"), "first|50|1", null, null)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cache = File(context.cacheDir, "covers")
        cache.deleteRecursively()
    }

    private fun image(color: Int, width: Int = 64, height: Int = 64): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun folderModel(): CoverModel {
        val file = temporaryFolder.newFile("cover.jpg")
        file.writeBytes(image(Color.BLUE))
        return model.copy(folderCoverUri = Uri.fromFile(file), folderCoverKey = "cover|100|1")
    }

    private fun assertColor(file: File, red: Int, blue: Int) {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
        try {
            val pixel = bitmap.getPixel(0, 0)
            assertEquals(red.toDouble(), Color.red(pixel).toDouble(), 10.0)
            assertEquals(blue.toDouble(), Color.blue(pixel).toDouble(), 10.0)
        } finally { bitmap.recycle() }
    }

    @Test fun embeddedWinsOverFolderAndRunsOffMainThread() = runBlocking {
        var calls = 0
        val bytes = image(Color.RED)
        val store = CoverStore(context) {
            assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread())
            calls++
            bytes
        }
        val source = folderModel()
        val file = requireNotNull(store.thumbnailFile(source))
        assertColor(file, 255, 0)
        assertEquals(file, store.thumbnailFile(source))
        assertEquals(1, calls)
    }

    @Test fun folderIsUsedWithoutEmbeddedArt() = runBlocking {
        val store = CoverStore(context) { null }
        assertColor(requireNotNull(store.thumbnailFile(folderModel())), 0, 255)
    }

    @Test fun missingArtIsRememberedAcrossStoreInstances() = runBlocking {
        var calls = 0
        val reader: (Uri) -> ByteArray? = { calls++; null }
        assertNull(CoverStore(context, reader).thumbnailFile(model))
        assertEquals(1, cache.listFiles().orEmpty().count { it.extension == "none" })
        assertNull(CoverStore(context, reader).thumbnailFile(model))
        assertEquals(1, calls)
    }

    @Test fun changedFolderTimestampResolvesAgain() = runBlocking {
        var calls = 0
        val store = CoverStore(context) { calls++; null }
        val source = folderModel()
        val first = requireNotNull(store.thumbnailFile(source))
        val folderFile = File(requireNotNull(source.folderCoverUri).path!!)
        folderFile.writeBytes(image(Color.RED))
        val updated = source.copy(folderCoverKey = "cover|100|2")
        assertNotEquals(CoverStore.cacheKey(source), CoverStore.cacheKey(updated))
        val second = requireNotNull(store.thumbnailFile(updated))
        assertNotEquals(first, second)
        assertColor(second, 255, 0)
        assertEquals(2, calls)
    }

    @Test fun changedAudioKeyInvalidatesMissingArtMarker() = runBlocking {
        var calls = 0
        val store = CoverStore(context) { calls++; null }
        assertNull(store.thumbnailFile(model))
        val updated = model.copy(firstTrackKey = "first|50|2")
        assertNotEquals(CoverStore.cacheKey(model), CoverStore.cacheKey(updated))
        assertNull(store.thumbnailFile(updated))
        assertEquals(2, calls)
    }

    @Test fun largeEmbeddedAndFolderImagesAreReducedTo512() = runBlocking {
        val bytes = image(Color.RED, 1600, 1200)
        val embedded = requireNotNull(CoverStore(context) { bytes }.thumbnailFile(model))
        val file = temporaryFolder.newFile("large.jpg").apply { writeBytes(bytes) }
        val folder = requireNotNull(CoverStore(context) { null }.thumbnailFile(model.copy(
            folderCoverUri = Uri.fromFile(file), folderCoverKey = "large|1|1")))
        for (thumbnail in listOf(embedded, folder)) {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(thumbnail.path))
            try {
                assertEquals(512, maxOf(bitmap.width, bitmap.height))
                assertEquals(384, minOf(bitmap.width, bitmap.height))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun folderIoFailureDoesNotWriteMarkerAndCanRetry() = runBlocking {
        val file = File(temporaryFolder.root, "missing.jpg")
        val source = model.copy(folderCoverUri = Uri.fromFile(file), folderCoverKey = "missing|1|1")
        val store = CoverStore(context) { null }
        assertNull(store.thumbnailFile(source))
        assertTrue(cache.listFiles().orEmpty().isEmpty())
        file.writeBytes(image(Color.BLUE))
        assertNotNull(store.thumbnailFile(source))
    }

    @Test fun cachePrunesOldestEntriesTo300() = runBlocking {
        assertTrue(cache.mkdirs())
        repeat(400) { index ->
            File(cache, "old$index.none").apply { writeText(""); setLastModified(index + 1L) }
        }
        assertNull(CoverStore(context) { null }.thumbnailFile(model))
        assertEquals(300, cache.listFiles().orEmpty().size)
        assertFalse(File(cache, "old0.none").exists())
        assertTrue(File(cache, "old399.none").exists())
    }

    @Test fun modelUsesFirstTrackSizeAndTimestampWithLegacyFallback() {
        val book = Audiobook("file:id", model.firstTrackUri, "Dune", "Dune.m4b", sizeBytes = 90)
        assertEquals(DurationCache.key(book.id, 90, 0), book.coverModel().firstTrackKey)
        val track = AudioTrack(Uri.parse("content://books/track"), "track", "1.mp3", "1", 0, 50, 123)
        val grouped = book.copy(tracks = listOf(track), coverUri = Uri.parse("content://books/cover"), coverKey = "cover|2|3")
        assertEquals(track.uri, grouped.coverModel().firstTrackUri)
        assertEquals(DurationCache.key("track", 50, 123), grouped.coverModel().firstTrackKey)
        assertEquals("v1|track|50|123|cover|2|3", CoverStore.cacheKey(grouped.coverModel()))
    }
}
