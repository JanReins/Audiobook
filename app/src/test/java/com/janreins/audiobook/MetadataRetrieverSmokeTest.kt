package com.janreins.audiobook

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import androidx.test.core.app.ApplicationProvider
import com.janreins.audiobook.data.ChapterRepository
import com.janreins.audiobook.player.ChapterRules
import com.janreins.audiobook.player.RawChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/** The production retriever (ChapterRepository.newRetriever) over the real fixtures, end to end. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataRetrieverSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun fixture(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).apply {
        writeBytes(this@MetadataRetrieverSmokeTest.javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() })
    })

    private fun retrieve(retriever: MetadataRetriever): List<RawChapter> = try {
        val future = retriever.retrieveTrackGroups()
        var waitedMs = 0
        while (!future.isDone && waitedMs < 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
            waitedMs += 10
        }
        ChapterRepository.chaptersOf(future.get(1, TimeUnit.SECONDS) as TrackGroupArray)
    } finally { retriever.close() }

    private fun assertThreeChapters(name: String, expectKnownEnds: Boolean) {
        val raw = retrieve(ChapterRepository.newRetriever(context, fixture(name)))
        val chapters = ChapterRules.buildForTrack(0, raw, 4000)
        assertEquals(name, listOf("Opening", "Middle Part", "Ending"), chapters.map { it.title })
        assertEquals(name, listOf(0L, 1000L, 2500L), chapters.map { it.startMs })
        assertEquals(name, listOf(1000L, 2500L, 4000L), chapters.map { it.endMs })
        if (expectKnownEnds) assertTrue("$name: stated ends", raw.none { it.endMs == C.TIME_UNSET })
    }

    @Test fun quickTimeOnly() = assertThreeChapters("fx_qt_only.m4b", expectKnownEnds = true)

    /** QuickTime chapters (with ends) replace chpl when both are present. */
    @Test fun quickTimeAndNero() = assertThreeChapters("fx.m4b", expectKnownEnds = true)

    @Test fun neroOnly() = assertThreeChapters("fx_chpl_only.m4b", expectKnownEnds = false)

    @Test fun id3Unchanged() = assertThreeChapters("fx.mp3", expectKnownEnds = true)

    /** Documents the bug fixed by newRetriever: the default flags omit the sample table and QT chapters. */
    @Test fun defaultRetrieverMissesQuickTimeOnlyChapters() {
        val raw = retrieve(MetadataRetriever.Builder(context, MediaItem.fromUri(fixture("fx_qt_only.m4b"))).build())
        assertTrue(raw.isEmpty())
    }
}
