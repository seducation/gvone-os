package com.example.data.sync

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

enum class MediaControlAction {
    TOGGLE_PLAY,
    PREVIOUS,
    NEXT,
    STOP
}

/**
 * Foreground Service that holds a partial wake lock and displays a media notification
 * to ensure that YouTube and web media playback continues uninterrupted in the background,
 * even when the screen is locked, minimized, or when multitasking.
 */
class GVONEMediaPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_TOGGLE_PLAY -> {
                mediaActionListener?.invoke(MediaControlAction.TOGGLE_PLAY)
            }
            ACTION_PREVIOUS -> {
                mediaActionListener?.invoke(MediaControlAction.PREVIOUS)
            }
            ACTION_NEXT -> {
                mediaActionListener?.invoke(MediaControlAction.NEXT)
            }
            ACTION_STOP -> {
                mediaActionListener?.invoke(MediaControlAction.STOP)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_MEDIA_TITLE) ?: "Media Playing"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val notification = buildNotification(title, isPlaying)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GVONE:MediaPlaybackWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours timeout max
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignored
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GVONE Background Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for background audio and video playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayIntent = Intent(this, GVONEMediaPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val togglePlayPendingIntent = PendingIntent.getService(
            this,
            1,
            togglePlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, GVONEMediaPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            2,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, GVONEMediaPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GVONEMediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "Web Media Playback" })
            .setContentText(if (isPlaying) "Playing in background • GVONE Browser" else "Paused • GVONE Browser")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "-10s", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePlayPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "+10s", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "gvone_background_playback_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START_OR_UPDATE = "com.example.gvone.action.START_OR_UPDATE"
        const val ACTION_TOGGLE_PLAY = "com.example.gvone.action.TOGGLE_PLAY"
        const val ACTION_PREVIOUS = "com.example.gvone.action.PREVIOUS"
        const val ACTION_NEXT = "com.example.gvone.action.NEXT"
        const val ACTION_STOP = "com.example.gvone.action.STOP"

        const val EXTRA_MEDIA_TITLE = "extra_media_title"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var mediaActionListener: ((MediaControlAction) -> Unit)? = null

        fun startOrUpdate(context: Context, title: String, isPlaying: Boolean) {
            try {
                val intent = Intent(context, GVONEMediaPlaybackService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_MEDIA_TITLE, title)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background execution limit safety
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, GVONEMediaPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Ignored safely
            }
        }
    }
}
