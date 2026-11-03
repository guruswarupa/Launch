package com.guruswarupa.launch.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.managers.ServiceNotificationManager
import com.guruswarupa.launch.ui.activities.LockScreenActivity

class LockScreenService : Service() {

    private var receiver: BroadcastReceiver? = null

    companion object {
        private const val SERVICE_NAME = "Lock screen"
        const val ACTION_START = "com.guruswarupa.launch.START_LOCK_SCREEN"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_LOCK_SCREEN"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundStatus()
    }

    private fun startForegroundStatus() {
        try {
            val notification = ServiceNotificationManager.createNotification(this)
            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, ServiceNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundStatus()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        registerScreenReceiver()
        return START_STICKY
    }

    private fun inCall(): Boolean {
        val mode = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode
        return mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun registerScreenReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> showLockScreen()
                    Intent.ACTION_USER_PRESENT -> LockScreenActivity.dismissCurrent()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun showLockScreen() {
        if (!Settings.canDrawOverlays(this) || inCall()) return
        try {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
        try { ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
