package com.janreins.audiobook.player

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import com.janreins.audiobook.data.PreferencesManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import com.janreins.audiobook.R
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.janreins.audiobook.MainActivity

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var coverBitmapLoader: CoverBitmapLoader? = null
    private var prefs: PreferencesManager? = null
    // Held strongly: SharedPreferences only keeps weak references to listeners.
    private val skipLabelListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferencesManager.KEY_SKIP_BACK_SECONDS || key == PreferencesManager.KEY_SKIP_FORWARD_SECONDS) {
            val p = prefs ?: return@OnSharedPreferenceChangeListener
            session?.setMediaButtonPreferences(mediaButtons(p))
            sessionPlayer?.notifySeekIncrementsChanged()
        }
    }
    private var sessionPlayer: SessionPlayer? = null
    private var pausedAtMs: Long? = null
    private var pausedMediaId: String? = null

    companion object {
        val REWIND_60 = SessionCommand("com.janreins.audiobook.REWIND_60", Bundle.EMPTY)
        val END_OF_TRACK = SessionCommand("com.janreins.audiobook.END_OF_TRACK", Bundle.EMPTY)
        val FORWARD_60 = SessionCommand("com.janreins.audiobook.FORWARD_60", Bundle.EMPTY)
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_stat_audiobook)
        })
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .build()
        val prefs = PreferencesManager(this).also { this.prefs = it }
        prefs.registerChangeListener(skipLabelListener)
        val sessionPlayer = SessionPlayer(player, prefs).also { this.sessionPlayer = it }
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && player.currentMediaItem != null && pausedAtMs == null) {
                    pausedAtMs = SystemClock.elapsedRealtime()
                    pausedMediaId = player.currentMediaItem?.mediaId
                } else if (playWhenReady) {
                    pausedAtMs = null
                    pausedMediaId = null
                }
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                // An explicit seek while paused (scrubbing, bookmark, track jump) picks the exact
                // resume point, so it must not be rewound again.
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    pausedAtMs = null
                    pausedMediaId = null
                }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    pausedAtMs = null
                    pausedMediaId = null
                }
            }
        })
        // ExoPlayer owns this setting; MediaController does not expose it.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    player.pauseAtEndOfMediaItems = false
                }
            }
        })
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val coverLoader = CoverBitmapLoader(this).also { coverBitmapLoader = it }
        session = MediaSession.Builder(this, sessionPlayer)
            // MediaSession wraps this in CacheBitmapLoader; the folder cover travels in extras so that
            // cache never matches it against later embedded art (see playBook).
            .setBitmapLoader(coverLoader)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession, controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(defaults.availableSessionCommands.buildUpon()
                            .add(REWIND_60).add(FORWARD_60).add(END_OF_TRACK).build())
                        .build()
                }

                override fun onMediaButtonEvent(
                    session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent
                ): Boolean {
                    val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        ?: return false
                    val direction = MediaKeySkips.direction(event.keyCode, session.player.mediaItemCount)
                        ?: return false
                    // Consume both key-down and key-up; act once on the first key-down.
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        if (direction < 0) session.player.seekBack() else session.player.seekForward()
                    }
                    return true
                }

                override fun onCustomCommand(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == END_OF_TRACK.customAction) {
                        player.pauseAtEndOfMediaItems = args.getBoolean("enabled", false)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    val delta = when (customCommand.customAction) {
                        REWIND_60.customAction -> -60_000L
                        FORWARD_60.customAction -> 60_000L
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    session.player.run {
                        seekTo(SeekMath.clampSeek(currentPosition, delta, duration))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            // Replace the default previous/next controls with single-file seeks.
            .setMediaButtonPreferences(mediaButtons(prefs))
            .build()
    }

    /**
     * Session-facing player: configured skip increments, clamped seeks and smart rewind, so the app,
     * notification, lock screen and headset buttons all behave the same.
     */
    private inner class SessionPlayer(
        private val exo: ExoPlayer, private val prefs: PreferencesManager
    ) : ForwardingPlayer(exo) {
        private val listeners = mutableListOf<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            super.addListener(listener)
            listeners += listener
        }
        override fun removeListener(listener: Player.Listener) {
            super.removeListener(listener)
            listeners -= listener
        }
        override fun getSeekBackIncrement(): Long = prefs.getSkipBackSeconds() * 1000L
        override fun getSeekForwardIncrement(): Long = prefs.getSkipForwardSeconds() * 1000L
        override fun seekBack() {
            seekTo(SeekMath.clampSeek(currentPosition, -seekBackIncrement, duration))
        }
        override fun seekForward() {
            seekTo(SeekMath.clampSeek(currentPosition, seekForwardIncrement, duration))
        }
        /**
         * Media3 answers "play" after the end (notification, lock screen, headset) with
         * seekToDefaultPosition() + play(), which would replay only the last file. Restart the whole
         * book instead, matching the in-app play button.
         */
        override fun seekToDefaultPosition() {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0, 0L) else super.seekToDefaultPosition()
        }
        override fun play() {
            applySmartRewind(exo, prefs)
            super.play()
        }
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) applySmartRewind(exo, prefs)
            super.setPlayWhenReady(playWhenReady)
        }

        /** ExoPlayer's own increments never change, so tell the session (and its controllers) directly. */
        fun notifySeekIncrementsChanged() {
            val back = seekBackIncrement
            val forward = seekForwardIncrement
            listeners.toList().forEach {
                it.onSeekBackIncrementChanged(back)
                it.onSeekForwardIncrementChanged(forward)
            }
        }
    }

    private fun applySmartRewind(player: Player, prefs: PreferencesManager) {
        val pausedAt = pausedAtMs ?: return
        pausedAtMs = null
        if (player.playWhenReady || !prefs.getSmartRewindEnabled() ||
            pausedMediaId != player.currentMediaItem?.mediaId) return
        val rewind = SmartRewind.rewindMsFor(SystemClock.elapsedRealtime() - pausedAt)
        if (rewind > 0) player.seekTo(SeekMath.clampSeek(player.currentPosition, -rewind, player.duration))
    }

    // Replace the default previous/next controls with single-file seeks.
    private fun mediaButtons(prefs: PreferencesManager) = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
            .setDisplayName("-${prefs.getSkipBackSeconds()}s").setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK).build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setDisplayName("+${prefs.getSkipForwardSeconds()}s").setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD).build(),
        CommandButton.Builder(CommandButton.ICON_REWIND)
            .setDisplayName("-1m").setSessionCommand(REWIND_60)
            .setSlots(CommandButton.SLOT_OVERFLOW).build(),
        CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("+1m").setSessionCommand(FORWARD_60)
            .setSlots(CommandButton.SLOT_OVERFLOW).build()
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.isPlaying || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        prefs?.unregisterChangeListener(skipLabelListener)
        session?.run { player.release(); release() }
        session = null
        coverBitmapLoader?.release()
        coverBitmapLoader = null
        super.onDestroy()
    }
}
