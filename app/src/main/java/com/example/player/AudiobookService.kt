package com.example.player

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Audiobook

/**
 * Foreground Service that keeps the audiobook playing when the app is in the background
 * or when the device screen is locked.
 */
class AudiobookService : Service() {

    companion object {
        const val CHANNEL_ID = "audiobook_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE"
        const val ACTION_REWIND_15 = "com.example.action.REWIND_15"
        const val ACTION_FORWARD_15 = "com.example.action.FORWARD_15"
        const val ACTION_STOP = "com.example.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AudiobookService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, AudiobookService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudiobookService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> AudiobookPlayerManager.resume(this)
            ACTION_PAUSE -> AudiobookPlayerManager.pause(this)
            ACTION_TOGGLE -> AudiobookPlayerManager.togglePlayPause(this)
            ACTION_REWIND_15 -> AudiobookPlayerManager.skip(-15_000L)
            ACTION_FORWARD_15 -> AudiobookPlayerManager.skip(15_000L)
            ACTION_STOP -> {
                AudiobookPlayerManager.pause(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
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

        val title = currentBook?.title ?: "Audiobook Player"
        val statusText = if (isPlaying) "Playing" else "Paused"

        // Open MainActivity when tapping notification
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause Action
        val toggleIntent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Rewind 15s Action
        val rewindIntent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_REWIND_15
        }
        val rewindPendingIntent = PendingIntent.getService(
            this,
            2,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Forward 15s Action
        val forwardIntent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_FORWARD_15
        }
        val forwardPendingIntent = PendingIntent.getService(
            this,
            3,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
            .addAction(android.R.drawable.ic_media_rew, "-15s", rewindPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "+15s", forwardPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }
}
