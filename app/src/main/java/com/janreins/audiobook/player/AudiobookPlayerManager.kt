package com.janreins.audiobook.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.janreins.audiobook.data.PreferencesManager
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

object AudiobookPlayerManager {
    private const val TAG = "AudiobookPlayerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var mediaPlayer: MediaPlayer? = null
    private var prefsManager: PreferencesManager? = null
    private var audioManager: AudioManager? = null
    private var prepared = false
    private var playWhenPrepared = false
    private var pendingPosition = 0L
    private var seekPending = false
    private var resumeOnFocusGain = false
    private var noisyRegistered = false
    private val saveThrottle = PlaybackPositions.SaveThrottle()
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusListener by lazy {
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    val shouldResume = _isPlaying.value || resumeOnFocusGain
                    pauseInternal(abandon = false)
                    resumeOnFocusGain = shouldResume
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    val shouldResume = resumeOnFocusGain
                    resumeOnFocusGain = false
                    if (shouldResume) startPlayback()
                }
            }
        }
    }
    private val audioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener).build()
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    // --- Observable Player State ---
    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _activeSleepOption = MutableStateFlow(SleepTimerOption.OFF)
    val activeSleepOption: StateFlow<SleepTimerOption> = _activeSleepOption.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Jobs for background periodic updates
    private var positionTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            val application = context.applicationContext
            appContext = application
            prefsManager = PreferencesManager(application)
            audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            _playbackSpeed.value = prefsManager?.getPlaybackSpeed() ?: 1f
        }
    }

    fun playBook(context: Context, book: Audiobook, customStartPosMs: Long? = null) {
        initialize(context)
        _errorMessage.value = null
        if (_currentBook.value?.id == book.id && mediaPlayer != null) {
            if (customStartPosMs != null) seekTo(customStartPosMs)
            if (!_isPlaying.value) resume(context)
            return
        }
        saveCurrentPosition()
        releasePlayer()
        _currentBook.value = book
        _durationMs.value = 0L
        pendingPosition = customStartPosMs ?: prefsManager?.getPlaybackPosition(book.id) ?: 0L
        _currentPositionMs.value = pendingPosition.coerceAtLeast(0L)
        prefsManager?.saveLastPlayedBookId(book.id)
        playWhenPrepared = true
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(audioAttributes)
            player.setWakeMode(requireNotNull(appContext), PowerManager.PARTIAL_WAKE_LOCK)
            player.setOnSeekCompleteListener { sought ->
                if (mediaPlayer === sought) seekPending = false
            }
            player.setOnPreparedListener { ready ->
                if (mediaPlayer !== ready) return@setOnPreparedListener
                try {
                    prepared = true
                    _durationMs.value = ready.duration.toLong().takeIf { it > 0 } ?: book.durationMs
                    val position = PlaybackPositions.resumePosition(pendingPosition, _durationMs.value)
                    _currentPositionMs.value = position
                    seekPending = true
                    ready.seekTo(position.toInt())
                    if (playWhenPrepared) {
                        if (requestAudioFocus()) startPlayback() else playWhenPrepared = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finishing preparation", e)
                    _errorMessage.value = "Unable to play audio file. It might be corrupted or moved."
                    releasePlayer()
                }
            }
            player.setOnCompletionListener { completed ->
                if (mediaPlayer !== completed) return@setOnCompletionListener
                _isPlaying.value = false
                playWhenPrepared = false
                _currentPositionMs.value = PlaybackPositions.positionAfterCompletion()
                persistPosition()
                seekPending = true
                try { completed.seekTo(0) }
                catch (e: Exception) { Log.w(TAG, "Could not rewind completed book", e) }
                stopPositionTracker()
                unregisterNoisy()
                abandonFocus()
            }
            player.setOnErrorListener { failed, what, extra ->
                if (mediaPlayer === failed) {
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _errorMessage.value = "Unable to play audio file. It might be corrupted or moved."
                    persistPosition()
                    releasePlayer()
                }
                true
            }
            player.setDataSource(requireNotNull(appContext), book.uri)
            player.prepareAsync()
            // Start the service from the book tap, never from the prepared callback.
            AudiobookService.start(requireNotNull(appContext))
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
            _errorMessage.value = "Could not open audio file: ${e.localizedMessage ?: "Unknown error"}"
            releasePlayer()
        }
    }

    fun togglePlayPause(context: Context? = null) {
        if (_isPlaying.value || playWhenPrepared) pause() else resume(context)
    }

    fun resume(context: Context? = null) {
        if (mediaPlayer == null || _isPlaying.value) return
        playWhenPrepared = true
        resumeOnFocusGain = false
        if (context != null) appContext?.let { AudiobookService.start(it) }
        if (prepared) {
            if (requestAudioFocus()) startPlayback() else playWhenPrepared = false
        }
    }

    private fun startPlayback() {
        val player = mediaPlayer ?: return
        if (!prepared) return
        try {
            player.start()
            applySpeed(player, _playbackSpeed.value)
            _isPlaying.value = true
            playWhenPrepared = true
            registerNoisy()
            startPositionTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            pause()
        }
    }

    fun pause(context: Context? = null) = pauseInternal(abandon = true)

    private fun pauseInternal(abandon: Boolean) {
        playWhenPrepared = false
        if (prepared) {
            try {
                mediaPlayer?.let { if (it.isPlaying) it.pause() }
            } catch (e: Exception) { Log.e(TAG, "Error pausing playback", e) }
        }
        _isPlaying.value = false
        saveCurrentPosition()
        stopPositionTracker()
        unregisterNoisy()
        if (abandon) abandonFocus()
    }

    fun seekTo(positionMs: Long) {
        val clamped = if (_durationMs.value > 0) positionMs.coerceIn(0, _durationMs.value)
            else positionMs.coerceAtLeast(0)
        pendingPosition = clamped
        _currentPositionMs.value = clamped
        if (prepared) {
            try {
                seekPending = true
                mediaPlayer?.seekTo(clamped.toInt())
            }
            catch (e: Exception) { Log.e(TAG, "Error seeking", e) }
        }
        // Persist the requested position, since MediaPlayer seeking is asynchronous.
        persistPosition()
    }

    fun skip(deltaMs: Long) = seekTo(_currentPositionMs.value + deltaMs)

    fun setPlaybackSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        _playbackSpeed.value = speed
        prefsManager?.savePlaybackSpeed(speed)
        if (_isPlaying.value) mediaPlayer?.let { applySpeed(it, speed) }
    }

    private fun applySpeed(player: MediaPlayer, speed: Float) {
        try { player.playbackParams = PlaybackParams().setSpeed(speed) }
        catch (e: Exception) { Log.e(TAG, "Error setting playback speed", e) }
    }

    fun setSleepTimer(option: SleepTimerOption, context: Context? = null) {
        _activeSleepOption.value = option
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSeconds.value = null
        if (option == SleepTimerOption.OFF || option.minutes <= 0) return
        sleepTimerJob = scope.launch {
            var remaining = option.minutes * 60
            _sleepTimerRemainingSeconds.value = remaining
            while (isActive && remaining > 0) {
                delay(1000)
                _sleepTimerRemainingSeconds.value = --remaining
            }
            if (isActive) {
                pause()
                _activeSleepOption.value = SleepTimerOption.OFF
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    fun saveCurrentPosition() {
        if (prepared && !seekPending) {
            try { mediaPlayer?.let { _currentPositionMs.value = it.currentPosition.toLong() } }
            catch (e: Exception) { Log.w(TAG, "Could not read playback position", e) }
        }
        persistPosition()
    }

    private fun persistPosition() {
        val book = _currentBook.value ?: return
        prefsManager?.savePlaybackPosition(book.id, _currentPositionMs.value)
        saveThrottle.markSaved(SystemClock.elapsedRealtime())
    }

    private fun startPositionTracker() {
        stopPositionTracker()
        positionTrackerJob = scope.launch {
            while (isActive) {
                try {
                    mediaPlayer?.let { if (prepared && !seekPending && it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition.toLong()
                        if (saveThrottle.shouldSave(SystemClock.elapsedRealtime())) persistPosition()
                    } }
                } catch (e: Exception) { Log.w(TAG, "Could not track playback position", e) }
                delay(500)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    private fun registerNoisy() {
        val application = appContext ?: return
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(application, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyRegistered = true
        }
    }

    private fun unregisterNoisy() {
        if (noisyRegistered) {
            appContext?.unregisterReceiver(noisyReceiver)
            noisyRegistered = false
        }
    }

    private fun requestAudioFocus(): Boolean = audioManager?.requestAudioFocus(audioFocusRequest) ==
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonFocus() {
        resumeOnFocusGain = false
        audioManager?.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun releasePlayer() {
        stopPositionTracker()
        unregisterNoisy()
        abandonFocus()
        val old = mediaPlayer
        mediaPlayer = null
        prepared = false
        seekPending = false
        playWhenPrepared = false
        _isPlaying.value = false
        try { old?.release() } catch (e: Exception) { Log.w(TAG, "Could not release player", e) }
    }

    fun stop() {
        pause()
        releasePlayer()
    }

    fun release() {
        stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _activeSleepOption.value = SleepTimerOption.OFF
        _sleepTimerRemainingSeconds.value = null
    }

    fun clearErrorMessage() { _errorMessage.value = null }
}
