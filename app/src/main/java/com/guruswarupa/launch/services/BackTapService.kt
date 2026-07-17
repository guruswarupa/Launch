package com.guruswarupa.launch.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaActionSound
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.handlers.BackTapDetector
import com.guruswarupa.launch.handlers.GestureCoordinator
import com.guruswarupa.launch.managers.ServiceNotificationManager
import com.guruswarupa.launch.managers.TorchManager
import com.guruswarupa.launch.models.Constants





class BackTapService : Service() {

    private var backTapDetector: BackTapDetector? = null
    private var torchManager: TorchManager? = null
    private var isRunning = false
    private var screenOnReceiver: BroadcastReceiver? = null
    private var settingsReceiver: BroadcastReceiver? = null
    private val powerManager: PowerManager by lazy {
        getSystemService(POWER_SERVICE) as PowerManager
    }
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }


    private var doubleTapAction = ACTION_SOUND_TOGGLE

    companion object {
        private const val TAG = "BackTapService"
        private const val SERVICE_NAME = "Back Tap Gestures"

        const val ACTION_START = "com.guruswarupa.launch.START_BACK_TAP"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_BACK_TAP"

        const val ACTION_NONE = "none"
        const val ACTION_TORCH_TOGGLE = "torch_toggle"
        const val ACTION_SCREENSHOT = "screenshot"
        const val ACTION_NOTIFICATIONS = "toggle_notifications"
        const val ACTION_SCREEN_OFF = "screen_off"
        const val ACTION_SOUND_TOGGLE = "sound_toggle"

        private const val SHOW_DEBUG_FEEDBACK = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            torchManager = TorchManager(this)

            backTapDetector = BackTapDetector(this) { count ->
                if (GestureCoordinator.requestTrigger()) {
                    handleBackTapAction(count)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    private fun startForegroundServiceStatus() {
        val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun applySettings() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val sensitivity = prefs.getInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 5)



        doubleTapAction = prefs.getString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, ACTION_SOUND_TOGGLE) ?: ACTION_SOUND_TOGGLE

        backTapDetector?.updateSensitivity(sensitivity)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        try {
            startForegroundServiceStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)

            stopSelf()
            return START_NOT_STICKY
        }

        try {
            applySettings()

            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_START, null -> {
                    if (!isRunning) {
                        registerScreenReceiver()
                        registerSettingsReceiver()
                        updateBackTapDetectionState()
                        isRunning = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        if (screenOnReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> updateBackTapDetectionState()
                    Intent.ACTION_SCREEN_OFF -> backTapDetector?.stop()
                }
            }
        }
        screenOnReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun registerSettingsReceiver() {
        if (settingsReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                    applySettings()
                    updateBackTapDetectionState()
                }
            }
        }
        settingsReceiver = receiver

        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")

        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateBackTapDetectionState() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)

        if (isEnabled && powerManager.isInteractive) {
            backTapDetector?.start()
        } else {
            backTapDetector?.stop()
        }
    }

    private fun handleBackTapAction(tapCount: Int) {
        try {

            if (tapCount < 2) return
            val action = doubleTapAction

            if (SHOW_DEBUG_FEEDBACK) {
                val sound = MediaActionSound()
                sound.play(MediaActionSound.SHUTTER_CLICK)
            }

            executeAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling back tap action: ${e.message}")
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            ACTION_TORCH_TOGGLE -> torchManager?.toggleTorch()
            ACTION_SCREENSHOT -> takeScreenshot()
            ACTION_NOTIFICATIONS -> toggleNotificationsPanel()
            ACTION_SCREEN_OFF -> turnScreenOff()
            ACTION_SOUND_TOGGLE -> toggleSound()
        }
    }

    private fun toggleSound() {
        try {
            val currentMode = audioManager.ringerMode
            if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling sound: ${e.message}")
        }
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.takeScreenshot()
            }
        }
    }

    private fun toggleNotificationsPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.toggleNotifications()
            }
        }
    }

    private fun turnScreenOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.lockScreen()
            }
        }
    }

    override fun onDestroy() {
        try {
            backTapDetector?.stop()
            screenOnReceiver?.let { unregisterReceiver(it) }
            settingsReceiver?.let { unregisterReceiver(it) }
            isRunning = false
            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
