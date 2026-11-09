package com.guruswarupa.launch.services

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
import androidx.core.app.ServiceCompat
import com.guruswarupa.launch.R

/** Lets a web app keep playing audio or video after you leave it, with a notification to pause or close it. */
object WebMediaBridge {
    /** Set by the open web app: receives "play", "pause", "toggle" or "close". */
    @Volatile var controller: ((String) -> Unit)? = null
}

class WebMediaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE -> { WebMediaBridge.controller?.invoke("toggle"); return START_NOT_STICKY }
            ACTION_CLOSE -> { WebMediaBridge.controller?.invoke("close"); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val open = intent?.getParcelableExtra<PendingIntent>(EXTRA_OPEN)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(title, open),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        return START_NOT_STICKY
    }

    private fun action(name: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, WebMediaService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notification(title: String, open: PendingIntent?): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.web_media_channel), NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_browser).setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(getString(R.string.web_media_playing)).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (open != null) setContentIntent(open) }
            .addAction(0, getString(R.string.web_media_pause_play), action(ACTION_TOGGLE, 1))
            .addAction(0, getString(R.string.web_media_close), action(ACTION_CLOSE, 2))
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.guruswarupa.launch.web.STOP"
        const val ACTION_TOGGLE = "com.guruswarupa.launch.web.TOGGLE"
        const val ACTION_CLOSE = "com.guruswarupa.launch.web.CLOSE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OPEN = "open"
        private const val CHANNEL = "web_media_playback"
        private const val NOTIFICATION_ID = 4210
    }
}
