package com.janreins.audiobook.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.janreins.audiobook.data.PreferencesManager
import com.janreins.audiobook.data.model.AudioTrack
import com.janreins.audiobook.data.model.BookProgress
import com.janreins.audiobook.data.model.Audiobook
import com.janreins.audiobook.data.model.SleepTimerOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
object AudiobookPlayerManager {
    private const val PLAYBACK_ERROR = "Unable to play audio file. It might be corrupted or moved."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var prefsManager: PreferencesManager? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private val saveThrottle = PlaybackPositions.SaveThrottle()
    private var positionTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSleepVolume = 1f
    private var sleepDeadlineMs: Long? = null
    private val knownBooks = mutableMapOf<String, Audiobook>()

    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()
    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex = _currentTrackIndex.asStateFlow()
    private val _trackCount = MutableStateFlow(1)
    val trackCount = _trackCount.asStateFlow()

    private fun bookId(item: MediaItem?) = item?.mediaId?.substringBeforeLast("#")

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val _activeSleepOption = MutableStateFlow(SleepTimerOption.OFF)
    val activeSleepOption: StateFlow<SleepTimerOption> = _activeSleepOption.asStateFlow()
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPositionTracker() else {
                positionTrackerJob?.cancel()
                saveCurrentPosition()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _activeSleepOption.value == SleepTimerOption.END_OF_TRACK) {
                setSleepTimer(SleepTimerOption.OFF)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = controller ?: return
            updatePosition(player)
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                _currentTrackIndex.value = 0
                _currentPositionMs.value = 0L
                persistPosition()
                player.seekTo(0, 0L)
                player.pause()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = controller ?: return
            if (mediaItem != null && _currentBook.value?.id != bookId(mediaItem)) {
                persistPosition()
                _currentBook.value = knownBooks[bookId(mediaItem)]
            }
            updatePosition(player)
            persistPosition()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) {
            val player = controller ?: return
            if (oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) {
                updatePosition(player)
                persistPosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _errorMessage.value = PLAYBACK_ERROR
            saveCurrentPosition()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            val speed = SpeedSteps.snap(playbackParameters.speed)
            _playbackSpeed.value = speed
            prefsManager?.savePlaybackSpeed(speed)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            updatePosition(player)
        }
    }

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            prefsManager = PreferencesManager(requireNotNull(appContext))
            _playbackSpeed.value = SpeedSteps.snap(prefsManager?.getPlaybackSpeed() ?: 1f)
        }
        connect()
    }

    private fun connect() {
        val application = appContext ?: return
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        val future = MediaController.Builder(application, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller !== disconnected) return
                    persistPosition()
                    positionTrackerJob?.cancel()
                    // The service (and its end-of-track flag and volume) is gone; a stale countdown
                    // would otherwise pause a later session unexpectedly.
                    cancelSleepTimerLocally()
                    _isPlaying.value = false
                    controller = null
                    controllerFuture = null
                }
            }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                try {
                    val connected = future.get()
                    controller = connected
                    connected.addListener(listener)
                    // Preserve the stored speed before initial controller state is mirrored.
                    connected.setPlaybackSpeed(_playbackSpeed.value)
                    val actions = pendingActions.toList()
                    pendingActions.clear()
                    actions.forEach { runAction(connected, it) }
                    if (controller === connected) {
                        updatePosition(connected)
                        _isPlaying.value = connected.isPlaying
                        if (connected.isPlaying) startPositionTracker()
                    }
                } catch (_: Exception) {
                    controllerFuture = null
                    _errorMessage.value = PLAYBACK_ERROR
                }
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val connected = controller
        if (connected != null) runAction(connected, action) else {
            pendingActions.add(action)
            connect()
        }
    }

    private fun runAction(player: MediaController, action: (MediaController) -> Unit) {
        try {
            action(player)
        } catch (_: Exception) {
            _errorMessage.value = PLAYBACK_ERROR
        }
    }

    fun playBook(context: Context, book: Audiobook, customStartPosMs: Long? = null,
                 customTrackIndex: Int? = null) {
        initialize(context)
        knownBooks[book.id] = book
        _errorMessage.value = null
        withController { player ->
            val tracks = book.tracks.ifEmpty {
                listOf(AudioTrack(book.uri, book.id, book.fileName, book.title, book.durationMs, book.sizeBytes))
            }
            val sameBook = bookId(player.currentMediaItem) == book.id
            val saved = prefsManager?.getBookProgress(book.id) ?: BookProgress()
            val index = (customTrackIndex ?: if (sameBook) player.currentMediaItemIndex else saved.trackIndex)
                .coerceIn(tracks.indices)
            val position = customStartPosMs ?: if (customTrackIndex != null) 0L else saved.positionMs
            val start = PlaybackPositions.resumePosition(position, tracks[index].durationMs)
            if (sameBook) {
                _currentBook.value = book
                if (customStartPosMs != null || customTrackIndex != null) {
                    player.seekTo(index, start)
                    updatePosition(player)
                    persistPosition()
                }
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            } else {
                saveCurrentPosition()
                _currentBook.value = book
                _currentTrackIndex.value = index
                _trackCount.value = tracks.size
                _durationMs.value = tracks[index].durationMs
                _currentPositionMs.value = start
                prefsManager?.saveLastPlayedBookId(book.id)
                val items = tracks.mapIndexed { trackIndex, track ->
                    MediaItem.Builder().setUri(track.uri).setMediaId("${book.id}#$trackIndex")
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title)
                            .setArtist("Audiobook").setAlbumTitle(book.title).build()).build()
                }
                player.setMediaItems(items, index, start)
                player.prepare()
                player.setPlaybackSpeed(_playbackSpeed.value)
                player.play()
                persistPosition()
            }
        }
    }

    fun previousTrack() = withController { it.seekToPreviousMediaItem() }
    fun nextTrack() = withController { it.seekToNextMediaItem() }

    fun togglePlayPause(context: Context? = null) {
        if (context != null) initialize(context)
        withController { player ->
            if (player.playWhenReady) pause() else resume()
        }
    }

    fun resume(context: Context? = null) {
        if (context != null) initialize(context)
        withController { player ->
            if (player.currentMediaItem == null) {
                val book = _currentBook.value
                val application = appContext
                if (book != null && application != null) playBook(application, book)
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun pause(context: Context? = null) {
        if (context != null) initialize(context)
        withController { player -> player.pause(); saveCurrentPosition() }
    }

    fun seekTo(positionMs: Long) = withController { seek(it, positionMs) }

    private fun seek(player: MediaController, positionMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: _durationMs.value
        val position = SeekMath.clampSeek(positionMs, 0, duration)
        player.seekTo(position)
        _currentPositionMs.value = position
        persistPosition()
    }

    fun skip(deltaMs: Long) = withController { player ->
        seek(player, SeekMath.clampSeek(player.currentPosition, deltaMs, player.duration))
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        val snapped = SpeedSteps.snap(speed)
        _playbackSpeed.value = snapped
        prefsManager?.savePlaybackSpeed(snapped)
        withController { it.setPlaybackSpeed(snapped) }
    }

    private fun cancelSleepTimerLocally() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineMs = null
        lastSleepVolume = 1f
        _activeSleepOption.value = SleepTimerOption.OFF
        _sleepTimerRemainingSeconds.value = null
    }

    fun setSleepTimer(option: SleepTimerOption, context: Context? = null) {
        if (context != null) initialize(context)
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineMs = null
        _activeSleepOption.value = option
        _sleepTimerRemainingSeconds.value = null
        lastSleepVolume = 1f
        withController { player ->
            player.volume = 1f
            player.sendCustomCommand(PlaybackService.END_OF_TRACK, Bundle().apply {
                putBoolean("enabled", option == SleepTimerOption.END_OF_TRACK)
            })
        }
        if (option.minutes <= 0) return
        sleepDeadlineMs = SystemClock.elapsedRealtime() + option.minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remainingMs = ((sleepDeadlineMs ?: break) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _sleepTimerRemainingSeconds.value = ((remainingMs + 999L) / 1000L).toInt()
                if (remainingMs == 0L) {
                    pause()
                    lastSleepVolume = 1f
                    withController { it.volume = 1f }
                    sleepDeadlineMs = null
                    _activeSleepOption.value = SleepTimerOption.OFF
                    _sleepTimerRemainingSeconds.value = null
                    break
                }
                val volume = if (prefsManager?.getSleepFadeOut() != false) SleepFade.volumeFor(remainingMs) else 1f
                if (volume != lastSleepVolume) {
                    lastSleepVolume = volume
                    withController { it.volume = volume }
                }
                delay(250)
            }
        }
    }

    fun extendSleepTimer() {
        val deadline = sleepDeadlineMs ?: return
        sleepDeadlineMs = deadline + 300_000L
        _sleepTimerRemainingSeconds.value =
            ((deadline + 300_000L - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()
        lastSleepVolume = 1f
        withController { it.volume = 1f }
    }

    private fun updatePosition(player: Player) {
        if (bookId(player.currentMediaItem) != _currentBook.value?.id || player.currentMediaItem == null) return
        _currentTrackIndex.value = if (player.playbackState == Player.STATE_ENDED) 0
            else player.currentMediaItemIndex.coerceAtLeast(0)
        _trackCount.value = player.mediaItemCount.coerceAtLeast(1)
        _durationMs.value = _currentBook.value?.tracks?.getOrNull(_currentTrackIndex.value)?.durationMs
            ?: _currentBook.value?.durationMs ?: 0L
        _currentPositionMs.value = if (player.playbackState == Player.STATE_ENDED) {
            PlaybackPositions.positionAfterCompletion()
        } else player.currentPosition.coerceAtLeast(0L)
        if (player.duration != C.TIME_UNSET) _durationMs.value = player.duration.coerceAtLeast(0L)
    }

    fun saveCurrentPosition() {
        controller?.let { updatePosition(it) }
        persistPosition()
    }

    private fun persistPosition() {
        val book = _currentBook.value ?: return
        prefsManager?.saveBookProgress(book.id, BookProgress(_currentTrackIndex.value, _currentPositionMs.value))
        saveThrottle.markSaved(SystemClock.elapsedRealtime())
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            while (isActive && controller?.isPlaying == true) {
                controller?.let { updatePosition(it) }
                if (saveThrottle.shouldSave(SystemClock.elapsedRealtime())) persistPosition()
                delay(500)
            }
        }
    }

    fun stop() = withController { player ->
        player.pause()
        saveCurrentPosition()
        player.stop()
        player.clearMediaItems()
        setSleepTimer(SleepTimerOption.OFF)
        _isPlaying.value = false
        positionTrackerJob?.cancel()
    }

    fun release() {
        stop()
        withController { player ->
            player.removeListener(listener)
            player.release()
            controller = null
            controllerFuture = null
        }
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _activeSleepOption.value = SleepTimerOption.OFF
        _sleepTimerRemainingSeconds.value = null
    }

    fun clearErrorMessage() { _errorMessage.value = null }
}
