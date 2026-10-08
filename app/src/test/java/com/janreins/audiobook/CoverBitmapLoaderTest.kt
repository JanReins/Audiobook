package com.janreins.audiobook

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.CoverPlaceholders
import com.janreins.audiobook.player.CoverBitmapLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CoverBitmapLoaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val loader = CoverBitmapLoader(ApplicationProvider.getApplicationContext())

    @After fun tearDown() = loader.release()

    private fun jpeg(color: Int, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
    }

    @Test fun embeddedArtIsDownsampledForTheNotification() {
        val metadata = MediaMetadata.Builder().setArtworkData(jpeg(Color.RED, 2400, 1600), null).build()
        val bitmap = loader.loadBitmapFromMetadata(metadata)!!.get(5, TimeUnit.SECONDS)
        assertTrue(maxOf(bitmap.width, bitmap.height) <= 512)
    }

    @Test fun embeddedArtWinsOverTheFolderCover() {
        val folder = temporaryFolder.newFile("cover.jpg").apply { writeBytes(jpeg(Color.BLUE, 64, 64)) }
        val metadata = MediaMetadata.Builder()
            .setArtworkData(jpeg(Color.RED, 64, 64), null)
            .setArtworkUri(android.net.Uri.fromFile(folder)).build()
        val pixel = loader.loadBitmapFromMetadata(metadata)!!.get(5, TimeUnit.SECONDS).getPixel(32, 32)
        assertTrue(Color.red(pixel) > 200 && Color.blue(pixel) < 60)
    }

    @Test fun folderCoverIsDownsampledToo() {
        val folder = temporaryFolder.newFile("big.jpg").apply { writeBytes(jpeg(Color.BLUE, 2000, 2000)) }
        val metadata = MediaMetadata.Builder().setArtworkUri(android.net.Uri.fromFile(folder)).build()
        val bitmap = loader.loadBitmapFromMetadata(metadata)!!.get(5, TimeUnit.SECONDS)
        assertTrue(maxOf(bitmap.width, bitmap.height) <= 512)
    }

    @Test fun noArtGivesThePlaceholderInTheBookColour() {
        val metadata = MediaMetadata.Builder().setAlbumTitle("Dune")
            .setExtras(Bundle().apply { putString(CoverBitmapLoader.EXTRA_BOOK_ID, "folder:dune") }).build()
        val bitmap = loader.loadBitmapFromMetadata(metadata)!!.get(5, TimeUnit.SECONDS)
        assertEquals(512, bitmap.width)
        val expected = CoverPlaceholders.PALETTE[CoverPlaceholders.colorIndex("folder:dune", CoverPlaceholders.PALETTE.size)]
        assertEquals(expected, bitmap.getPixel(2, 2))
        // The notification re-requests on every state change; the same book reuses the placeholder.
        assertTrue(loader.loadBitmapFromMetadata(metadata) === loader.loadBitmapFromMetadata(metadata))
    }
}
