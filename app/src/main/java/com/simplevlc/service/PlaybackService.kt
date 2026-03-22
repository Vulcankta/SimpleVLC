package com.simplevlc.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.simplevlc.R
import com.simplevlc.PlayerActivity

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var isPlaying = false
    private var currentTitle = "Unknown"
    
    companion object {
        const val CHANNEL_ID = "simplevlc_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.simplevlc.ACTION_PLAY"
        const val ACTION_PAUSE = "com.simplevlc.ACTION_PAUSE"
        const val ACTION_STOP = "com.simplevlc.ACTION_STOP"
        const val ACTION_PREVIOUS = "com.simplevlc.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.simplevlc.ACTION_NEXT"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SimpleVLC::PlaybackWakeLock"
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_STOP -> stop()
                ACTION_PREVIOUS -> onSkipToPrevious?.invoke()
                ACTION_NEXT -> onSkipToNext?.invoke()
                else -> { /* Unknown action */ }
            }
        }
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Acquire wakeLock to prevent CPU from sleeping during playback
        wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
        
        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SimpleVLC").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    this@PlaybackService.play()
                }

                override fun onPause() {
                    this@PlaybackService.pause()
                }

                override fun onStop() {
                    this@PlaybackService.stop()
                }

                override fun onSkipToNext() {
                    this@PlaybackService.onSkipToNext?.invoke()
                }

                override fun onSkipToPrevious() {
                    this@PlaybackService.onSkipToPrevious?.invoke()
                }

                override fun onSeekTo(pos: Long) {
                    this@PlaybackService.onSeekTo?.invoke(pos)
                }
            })
            
            isActive = true
        }
    }

    private fun buildNotification(): Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "暂停",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "播放",
                createPendingIntent(ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在播放")
            .setContentText(currentTitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_previous, "上一曲", createPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseAction)
            .addAction(R.drawable.ic_next, "下一曲", createPendingIntent(ACTION_NEXT))
            .build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updatePlaybackState(playing: Boolean, title: String, position: Long = 0) {
        isPlaying = playing
        currentTitle = title
        currentPosition = position
        updateNotification()
        
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, position, 1.0f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }

    private var currentPosition: Long = 0
    var onSkipToNext: (() -> Unit)? = null
    var onSkipToPrevious: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null

    fun play() {
        isPlaying = true
        updateNotification()
        mediaSession?.isActive = true
    }

    fun pause() {
        isPlaying = false
        updateNotification()
    }

    fun stop() {
        isPlaying = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setPosition(position: Long) {
        currentPosition = position
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
