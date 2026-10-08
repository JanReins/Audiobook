package com.janreins.audiobook

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.janreins.audiobook.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionPlayerTest {
    private class Settings : SessionSettings {
        var enabled = true
        var back = 15000L
        var forward = 30000L
        override fun skipBackMs() = back
        override fun skipForwardMs() = forward
        override fun smartRewindEnabled() = enabled
    }

    private class FakePlayer(count: Int = 3) : SimpleBasePlayer(Looper.getMainLooper()) {
        val calls = mutableListOf<String>()
        var snapshot = State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().apply {
                if (count == 1) { remove(Player.COMMAND_SEEK_TO_NEXT); remove(Player.COMMAND_SEEK_TO_PREVIOUS) }
            }.build())
            .setPlaylist((0 until count).map { i -> MediaItemData.Builder(i)
                .setMediaItem(MediaItem.Builder().setMediaId("book#$i").build())
                .setIsSeekable(true).setDurationUs(100_000_000).build() })
            .setCurrentMediaItemIndex(0).setContentPositionMs(0).setPlaybackState(Player.STATE_READY).build()
        override fun getState() = snapshot
        fun change(index: Int = snapshot.currentMediaItemIndex, position: Long = currentPosition,
                   ready: Boolean = snapshot.playWhenReady, playback: Int = snapshot.playbackState) {
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(index).setContentPositionMs(position)
                .setPlayWhenReady(ready, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).setPlaybackState(playback).build()
            invalidateState()
            ShadowLooper.idleMainLooper()
        }
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            calls += "seek:$mediaItemIndex:$positionMs"
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).setContentPositionMs(positionMs)
                .setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, positionMs).build()
            return Futures.immediateVoidFuture()
        }
        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            calls += "play:$playWhenReady"
            snapshot = snapshot.buildUpon().clearPositionDiscontinuity()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }
    }

    @Test fun notificationPlayAfterEndRestartsWholeBook() {
        val wrapped = FakePlayer()
        wrapped.change(index = 2, position = 100000, playback = Player.STATE_ENDED)
        val session = SessionPlayer(wrapped, Settings(), { null })
        Util.handlePlayButtonAction(session)
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("seek:0:0", "play:true"), wrapped.calls)
    }

    @Test fun defaultPositionBeforeTheEndIsUnchanged() {
        val wrapped = FakePlayer()
        wrapped.change(index = 1, position = 5000)
        val session = SessionPlayer(wrapped, Settings(), { null })
        session.seekToDefaultPosition()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("seek:1:${C.TIME_UNSET}"), wrapped.calls)
    }

    @Test fun fileChaptersNavigateAcrossFilesWithThreeSecondRule() {
        val wrapped = FakePlayer(3)
        val index = ChapterIndex.build((0 until 3).map { null to 100000L }, listOf("One", "Two", "Three"))
        val session = SessionPlayer(wrapped, Settings(), { index })
        session.seekToNext()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:1:0", wrapped.calls.last())
        wrapped.change(index = 1, position = 2000)
        session.seekToPrevious()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:0", wrapped.calls.last())
        wrapped.change(index = 2, position = 50000)
        session.seekToPrevious()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:2:0", wrapped.calls.last())
        wrapped.change(index = 2, position = 60000)
        wrapped.calls.clear()
        session.seekToNext()
        ShadowLooper.idleMainLooper()
        assertTrue("next on the last chapter of the last file does nothing", wrapped.calls.isEmpty())
    }

    @Test fun skipsUseSettingsAndClamp() {
        val wrapped = FakePlayer()
        val settings = Settings()
        val session = SessionPlayer(wrapped, settings, { null })
        wrapped.change(position = 1000)
        session.seekBack()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:0", wrapped.calls.last())
        wrapped.change(position = 90000)
        session.seekForward()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:100000", wrapped.calls.last())
        val increments = mutableListOf<Long>()
        session.addListener(object : Player.Listener {
            override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) { increments += seekBackIncrementMs }
            override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) { increments += seekForwardIncrementMs }
        })
        settings.back = 5000
        settings.forward = 10000
        session.invalidate()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf(5000L, 10000L), increments)
        assertEquals(5000L, session.seekBackIncrement)
        assertEquals(10000L, session.seekForwardIncrement)
        wrapped.change(position = 50000)
        session.seekBack()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:45000", wrapped.calls.last())
        session.seekForward()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:55000", wrapped.calls.last())
    }

    @Test fun pauseRewindsBeforePlayAndExplicitSeekCancelsIt() {
        val wrapped = FakePlayer()
        var clock = 0L
        val session = SessionPlayer(wrapped, Settings(), { null }, { clock })
        wrapped.change(position = 50000, ready = true)
        session.pause()
        ShadowLooper.idleMainLooper()
        clock += 120000
        wrapped.calls.clear()
        session.play()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("seek:0:${50000 - SmartRewind.rewindMsFor(120000)}", "play:true"), wrapped.calls)
        session.pause()
        ShadowLooper.idleMainLooper()
        session.seekTo(30000)
        ShadowLooper.idleMainLooper()
        clock += 120000
        wrapped.calls.clear()
        session.play()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("play:true"), wrapped.calls)
    }

    @Test fun disabledSmartRewindDoesNotSeek() {
        val wrapped = FakePlayer()
        var clock = 0L
        val settings = Settings().apply { enabled = false }
        val session = SessionPlayer(wrapped, settings, { null }, { clock })
        wrapped.change(position = 50000, ready = true)
        session.pause()
        ShadowLooper.idleMainLooper()
        clock += 120000
        wrapped.calls.clear()
        session.play()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("play:true"), wrapped.calls)
    }

    @Test fun chaptersProvideCommandsNavigationAndSubtitleOnSingleFile() {
        val wrapped = FakePlayer(1)
        val index = ChapterIndex.build(listOf(listOf(RawChapter(0, 10000, "Opening"),
            RawChapter(10000, 20000, "Middle"), RawChapter(20000, 100000, "Ending")) to 100000L), listOf("Book"))
        val session = SessionPlayer(wrapped, Settings(), { index })
        assertTrue(session.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(session.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals("Opening", session.mediaMetadata.subtitle.toString())
        session.seekToNext()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:10000", wrapped.calls.last())
        assertEquals("Middle", session.mediaMetadata.subtitle.toString())
        wrapped.change(position = 14000)
        session.seekToPrevious()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:10000", wrapped.calls.last())
        wrapped.change(position = 13000)
        session.seekToPrevious()
        ShadowLooper.idleMainLooper()
        assertEquals("seek:0:0", wrapped.calls.last())
        wrapped.change(position = 22000)
        wrapped.calls.clear()
        session.seekToNext()
        ShadowLooper.idleMainLooper()
        assertTrue(wrapped.calls.isEmpty())
        assertEquals("Ending", session.mediaMetadata.subtitle.toString())
    }
}
