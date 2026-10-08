package com.janreins.audiobook.player

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.janreins.audiobook.MainActivity
import com.janreins.audiobook.R

/**
 * Foreground Service that handles background audio playback and delivers
 * an active playback notification with -1m, -15s, Play/Pause, +15s, +1m and Close controls.
 */
class AudiobookService : Service() {

    companion object {
        const val CHANNEL_ID = "audiobook_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.janreins.audiobook.action.PLAY"
        const val ACTION_PAUSE = "com.janreins.audiobook.action.PAUSE"
        const val ACTION_TOGGLE = "com.janreins.audiobook.action.TOGGLE"
        const val ACTION_REWIND_60 = "com.janreins.audiobook.action.REWIND_60"
        const val ACTION_REWIND_15 = "com.janreins.audiobook.action.REWIND_15"
        const val ACTION_FORWARD_15 = "com.janreins.audiobook.action.FORWARD_15"
        const val ACTION_FORWARD_60 = "com.janreins.audiobook.action.FORWARD_60"
        const val ACTION_STOP = "com.janreins.audiobook.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AudiobookService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var closing = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            combine(AudiobookPlayerManager.isPlaying, AudiobookPlayerManager.currentBook) { playing, book ->
                playing to book
            }.collect {
                if (started && !closing) refreshNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started = true
        showForeground(buildNotification())
        if (intent == null || AudiobookPlayerManager.currentBook.value == null) {
            closing = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_PLAY -> AudiobookPlayerManager.resume()
            ACTION_PAUSE -> AudiobookPlayerManager.pause()
            ACTION_TOGGLE -> AudiobookPlayerManager.togglePlayPause()
            ACTION_REWIND_60 -> AudiobookPlayerManager.skip(-60_000L)
            ACTION_REWIND_15 -> AudiobookPlayerManager.skip(-15_000L)
            ACTION_FORWARD_15 -> AudiobookPlayerManager.skip(15_000L)
            ACTION_FORWARD_60 -> AudiobookPlayerManager.skip(60_000L)
            ACTION_STOP -> {
                closing = true
                AudiobookPlayerManager.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        refreshNotification()
        return START_NOT_STICKY
    }

    private fun showForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        val notification = buildNotification()
        if (AudiobookPlayerManager.isPlaying.value) {
            try {
                showForeground(notification)
            } catch (e: Exception) {
                // Android 12+ may refuse re-entering the foreground from the background
                // (e.g. auto-resume after an audio focus gain). Keep playing and just update the notification.
                Log.w("AudiobookService", "Could not re-enter foreground", e)
                postNotification(notification)
            }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            postNotification(notification)
        }
    }

    private fun postNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AudiobookPlayerManager.saveCurrentPosition()
        if (!AudiobookPlayerManager.isPlaying.value) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        closing = true
        AudiobookPlayerManager.saveCurrentPosition()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audiobook Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback controls for your audiobooks"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val currentBook = AudiobookPlayerManager.currentBook.value
        val isPlaying = AudiobookPlayerManager.isPlaying.value

        val title = currentBook?.title ?: "My Private Audiobook Player"
        val statusText = if (isPlaying) "Playing" else "Paused"

        // Open MainActivity when tapping the notification
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. Rewind 1 Minute (-60s)
        val rewind60Intent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_REWIND_60
        }
        val rewind60PendingIntent = PendingIntent.getForegroundService(
            this,
            1,
            rewind60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Rewind 15 Seconds (-15s)
        val rewind15Intent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_REWIND_15
        }
        val rewind15PendingIntent = PendingIntent.getForegroundService(
            this,
            2,
            rewind15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Play / Pause Action
        val toggleIntent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getForegroundService(
            this,
            3,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Forward 15 Seconds (+15s)
        val forward15Intent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_FORWARD_15
        }
        val forward15PendingIntent = PendingIntent.getForegroundService(
            this,
            4,
            forward15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Forward 1 Minute (+60s)
        val forward60Intent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_FORWARD_60
        }
        val forward60PendingIntent = PendingIntent.getForegroundService(
            this,
            5,
            forward60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getForegroundService(
            this, 6, Intent(this, AudiobookService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_rew, "-1m", rewind60PendingIntent)
            .addAction(android.R.drawable.ic_media_rew, "-15s", rewind15PendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "+15s", forward15PendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "+1m", forward60PendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    // Shows -15s (index 1), Play/Pause (index 2), +15s (index 3) in compact view; expanded shows all 6
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()
    }
}
