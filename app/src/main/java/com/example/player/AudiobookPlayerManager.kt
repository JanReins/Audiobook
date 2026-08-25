package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.model.Audiobook
import com.example.data.model.SleepTimerOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Central controller managing audio playback, position tracking, playback speed,
 * and the sleep timer.
 */
object AudiobookPlayerManager {

    private const val TAG = "AudiobookPlayerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaPlayer: MediaPlayer? = null
    private var prefsManager: PreferencesManager? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

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

    /**
     * Initializes the player with context and preferences.
     */
    fun initialize(context: Context) {
        if (prefsManager == null) {
            prefsManager = PreferencesManager(context.applicationContext)
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            _playbackSpeed.value = prefsManager?.getPlaybackSpeed() ?: 1.0f
        }
    }

    /**
     * Plays a selected audiobook, automatically restoring the last known playback position.
     */
    fun playBook(context: Context, book: Audiobook, customStartPosMs: Long? = null) {
        initialize(context)
        _errorMessage.value = null

        // If user tapped on the currently loaded book, toggle play/pause
        if (_currentBook.value?.id == book.id && mediaPlayer != null) {
            if (customStartPosMs != null) {
                seekTo(customStartPosMs)
            }
            if (!_isPlaying.value) {
                resume()
            }
            return
        }

        // Save position of previous book before switching
        saveCurrentPosition()

        // Release old player
        releasePlayer()

        _currentBook.value = book
        prefsManager?.saveLastPlayedBookId(book.id)

        val targetPosition = customStartPosMs ?: prefsManager?.getPlaybackPosition(book.id) ?: 0L

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context.applicationContext, book.uri)
                prepare()
            }

            val actualDuration = if (player.duration > 0) player.duration.toLong() else book.durationMs
            _durationMs.value = actualDuration

            val safeTargetPos = if (targetPosition in 0..actualDuration) targetPosition else 0L
            if (safeTargetPos > 0) {
                player.seekTo(safeTargetPos.toInt())
                _currentPositionMs.value = safeTargetPos
            } else {
                _currentPositionMs.value = 0L
            }

            // Apply current playback speed
            applySpeed(player, _playbackSpeed.value)

            player.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = _durationMs.value
                saveCurrentPosition()
                stopPositionTracker()
                AudiobookService.updateNotification(context)
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                _errorMessage.value = "Unable to play audio file. It might be corrupted or moved."
                _isPlaying.value = false
                stopPositionTracker()
                AudiobookService.updateNotification(context)
                true
            }

            mediaPlayer = player

            // Request Audio Focus and start
            if (requestAudioFocus()) {
                player.start()
                _isPlaying.value = true
                startPositionTracker()
                AudiobookService.start(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
            _errorMessage.value = "Could not open audio file: ${e.localizedMessage ?: "Unknown error"}"
            _isPlaying.value = false
            releasePlayer()
        }
    }

    /**
     * Toggles between play and pause.
     */
    fun togglePlayPause(context: Context? = null) {
        if (_isPlaying.value) {
            pause()
        } else {
            resume(context)
        }
    }

    /**
     * Resumes playback if a player is available.
     */
    fun resume(context: Context? = null) {
        val player = mediaPlayer
        if (player != null && !_isPlaying.value) {
            if (requestAudioFocus()) {
                try {
                    player.start()
                    _isPlaying.value = true
                    startPositionTracker()
                    context?.let { AudiobookService.start(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resuming playback", e)
                }
            }
        }
    }

    /**
     * Pauses playback and saves current position.
     */
    fun pause(context: Context? = null) {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            try {
                player.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing playback", e)
            }
        }
        _isPlaying.value = false
        saveCurrentPosition()
        stopPositionTracker()
        context?.let { AudiobookService.updateNotification(it) }
    }

    /**
     * Seeks to an exact millisecond position.
     */
    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val maxDuration = _durationMs.value.coerceAtLeast(0L)
        val clamped = positionMs.coerceIn(0L, maxDuration)
        try {
            player.seekTo(clamped.toInt())
            _currentPositionMs.value = clamped
            saveCurrentPosition()
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking to $positionMs", e)
        }
    }

    /**
     * Skips playback by delta milliseconds (+/- 15s, +/- 60s).
     */
    fun skip(deltaMs: Long) {
        val newPos = _currentPositionMs.value + deltaMs
        seekTo(newPos)
    }

    /**
     * Sets playback speed (e.g. 0.75x, 1.0x, 1.25x, 1.5x).
     */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        prefsManager?.savePlaybackSpeed(speed)
        mediaPlayer?.let { applySpeed(it, speed) }
    }

    private fun applySpeed(player: MediaPlayer, speed: Float) {
        try {
            val params = player.playbackParams ?: PlaybackParams()
            player.playbackParams = params.setSpeed(speed)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed $speed", e)
        }
    }

    /**
     * Configures the sleep timer. Pauses audio once elapsed.
     */
    fun setSleepTimer(option: SleepTimerOption, context: Context? = null) {
        _activeSleepOption.value = option
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (option == SleepTimerOption.OFF || option.minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            return
        }

        var remainingSeconds = option.minutes * 60
        _sleepTimerRemainingSeconds.value = remainingSeconds

        sleepTimerJob = scope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
                _sleepTimerRemainingSeconds.value = remainingSeconds
            }
            if (isActive && remainingSeconds <= 0) {
                // Time is up, gently pause the audiobook
                pause(context)
                _activeSleepOption.value = SleepTimerOption.OFF
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    /**
     * Saves the current position to SharedPreferences.
     */
    fun saveCurrentPosition() {
        val book = _currentBook.value ?: return
        val pos = _currentPositionMs.value
        prefsManager?.savePlaybackPosition(book.id, pos)
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val current = player.currentPosition.toLong()
                        _currentPositionMs.value = current
                        // Auto-save position every 5 seconds while listening
                        val book = _currentBook.value
                        if (book != null && current > 0) {
                            prefsManager?.savePlaybackPosition(book.id, current)
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    private fun releasePlayer() {
        stopPositionTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore release exceptions
        }
        mediaPlayer = null
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    pause()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Resume if desired, or stay paused
                }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
