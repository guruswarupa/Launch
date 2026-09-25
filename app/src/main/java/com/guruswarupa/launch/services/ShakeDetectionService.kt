package com.guruswarupa.launch.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.handlers.GestureCoordinator
import com.guruswarupa.launch.handlers.ShakeDetector
import com.guruswarupa.launch.managers.ServiceNotificationManager
import com.guruswarupa.launch.managers.TorchManager
import com.guruswarupa.launch.models.Constants

class ShakeDetectionService : Service() {

    private var shakeDetector: ShakeDetector? = null
    private var torchManager: TorchManager? = null
    private var isRunning = false
    private var screenOnReceiver: BroadcastReceiver? = null
    private var settingsReceiver: BroadcastReceiver? = null
    private val powerManager: PowerManager by lazy {
        getSystemService(POWER_SERVICE) as PowerManager
    }

    companion object {
        private const val TAG = "ShakeDetectionService"
        private const val SERVICE_NAME = "Shake Detection"

        const val ACTION_START = "com.guruswarupa.launch.START_SHAKE_DETECTION"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_SHAKE_DETECTION"
    }

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceStatus()

        torchManager = TorchManager(this)
        shakeDetector = ShakeDetector(this) {
            if (GestureCoordinator.requestTrigger()) {
                try {
                    torchManager?.toggleTorch()
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun startForegroundServiceStatus() {
        try {

            val notification = ServiceNotificationManager.createNotification(this)

            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                ServiceCompat.startForeground(
                    this,
                    ServiceNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                stopSelf()
            }
        }
    }

    private fun applySensitivity() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val sensitivity = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5)
        shakeDetector?.updateSensitivity(sensitivity)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForegroundServiceStatus()

        try {
            applySensitivity()

            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_START, null -> {
                    if (!isRunning) {
                        registerScreenReceiver()
                        registerSettingsReceiver()
                        updateShakeDetectionState()
                        isRunning = true
                    }
                }
            }
        } catch (e: Exception) {
        }
        return START_STICKY
    }

    private fun updateShakeDetectionState() {
        val isScreenOn = powerManager.isInteractive
        if (isScreenOn) shakeDetector?.start() else shakeDetector?.stop()
    }

    private fun registerScreenReceiver() {
        if (screenOnReceiver != null) return
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> shakeDetector?.start()
                    Intent.ACTION_SCREEN_OFF -> shakeDetector?.stop()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenOnReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun registerSettingsReceiver() {
        if (settingsReceiver != null) return
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                applySensitivity()
            }
        }
        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        ContextCompat.registerReceiver(this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        screenOnReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        settingsReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        shakeDetector?.cleanup()
        torchManager?.turnOffTorch()
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
