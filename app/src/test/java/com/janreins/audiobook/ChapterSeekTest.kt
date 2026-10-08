package com.janreins.audiobook

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.janreins.audiobook.player.ChapterIndex
import com.janreins.audiobook.player.ChapterNavigation
import com.janreins.audiobook.player.RawChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** In-app prev/next: a direct seekTo(track, position), never Media3's seekToPrevious/Next. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterSeekTest {
    private class Recorder(count: Int) : SimpleBasePlayer(Looper.getMainLooper()) {
        val seeks = mutableListOf<Triple<Int, Long, Int>>()
        var snapshot = State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist((0 until count).map { i -> MediaItemData.Builder(i)
                .setMediaItem(MediaItem.Builder().setMediaId("book#$i").build())
                .setIsSeekable(true).setDurationUs(100_000_000).build() })
            .setCurrentMediaItemIndex(0).setContentPositionMs(0).setPlaybackState(Player.STATE_READY).build()
        override fun getState() = snapshot
        fun at(index: Int, position: Long) {
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(index).setContentPositionMs(position).build()
            invalidateState()
            ShadowLooper.idleMainLooper()
        }
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            seeks += Triple(mediaItemIndex, positionMs, seekCommand)
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }
    }

    private val directSeeks = setOf(Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

    @Test fun prevAndNextUseDirectSeeksWithinAndAcrossFiles() {
        val player = Recorder(2)
        val index = ChapterIndex.build(listOf(
            listOf(RawChapter(0, 40_000, "A"), RawChapter(40_000, 100_000, "B")) to 100_000L,
            null to 100_000L), listOf("File 1", "File 2"))
        player.at(0, 10_000)
        assertTrue(ChapterNavigation.seek(player, index, forward = true))
        assertEquals(0 to 40_000L, player.seeks.last().let { it.first to it.second })
        player.at(0, 50_000)
        assertTrue(ChapterNavigation.seek(player, index, forward = true))
        assertEquals(1 to 0L, player.seeks.last().let { it.first to it.second })
        player.at(1, 1_000)
        assertTrue(ChapterNavigation.seek(player, index, forward = false))
        assertEquals(0 to 40_000L, player.seeks.last().let { it.first to it.second })
        assertTrue(player.seeks.all { it.third in directSeeks })
        // Last chapter of the last file: handled, but no seek (never triggers Finished).
        player.at(1, 50_000)
        val before = player.seeks.size
        assertTrue(ChapterNavigation.seek(player, index, forward = true))
        assertEquals(before, player.seeks.size)
    }

    @Test fun nothingToNavigateLeavesThePlayerAlone() {
        val player = Recorder(1)
        val single = ChapterIndex.build(listOf(null to 100_000L), listOf("Only"))
        assertFalse(ChapterNavigation.seek(player, single, forward = true))
        assertFalse(ChapterNavigation.seek(player, null, forward = false))
        assertTrue(player.seeks.isEmpty())
    }
}
