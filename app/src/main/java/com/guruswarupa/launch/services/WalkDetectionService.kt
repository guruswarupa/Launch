package com.guruswarupa.launch.services

import com.guruswarupa.launch.R
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.handlers.GestureCoordinator
import com.guruswarupa.launch.handlers.WalkDetector
import com.guruswarupa.launch.managers.ServiceNotificationManager
import com.guruswarupa.launch.managers.TorchManager
import com.guruswarupa.launch.models.Constants

class WalkDetectionService : Service() {

    private var walkDetector: WalkDetector? = null
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

    private var action = ACTION_LAUNCH_MUSIC

    companion object {
        private const val TAG = "WalkDetectionService"
        private const val SERVICE_NAME = "Walk Detection"

        const val ACTION_START = "com.guruswarupa.launch.START_WALK_DETECTION"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_WALK_DETECTION"

        const val ACTION_NONE = "none"
        const val ACTION_LAUNCH_MUSIC = "launch_music"
        const val ACTION_LAUNCH_APP = "launch_app"
        const val ACTION_TORCH_TOGGLE = "torch_toggle"
        const val ACTION_SOUND_TOGGLE = "sound_toggle"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceStatus()

        torchManager = TorchManager(this)
        walkDetector = WalkDetector(this) {
            if (GestureCoordinator.requestTrigger()) {
                executeAction()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceStatus()

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
                        updateWalkDetectionState()
                        isRunning = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        screenOnReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        settingsReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        walkDetector?.cleanup()
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopSelf()
            }
        }
    }

    private fun applySettings() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        action = prefs.getString(Constants.Prefs.WALK_DETECT_ACTION, ACTION_LAUNCH_MUSIC) ?: ACTION_LAUNCH_MUSIC

        val threshold = prefs.getInt(Constants.Prefs.WALK_DETECT_STEP_THRESHOLD, 10)
        val timeWindowSeconds = prefs.getInt(Constants.Prefs.WALK_DETECT_TIME_WINDOW_SECONDS, 15)
        val cooldownMinutes = prefs.getInt(Constants.Prefs.WALK_DETECT_COOLDOWN_MINUTES, 5)
        walkDetector?.updateSettings(threshold, timeWindowSeconds, cooldownMinutes)
    }

    private fun updateWalkDetectionState() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(Constants.Prefs.WALK_DETECT_ENABLED, false)
        if (isEnabled && powerManager.isInteractive) {
            walkDetector?.start()
        } else {
            walkDetector?.stop()
        }
    }

    private fun registerScreenReceiver() {
        if (screenOnReceiver != null) return

        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> updateWalkDetectionState()
                    Intent.ACTION_SCREEN_OFF -> walkDetector?.stop()
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
                if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                    applySettings()
                    updateWalkDetectionState()
                }
            }
        }

        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        ContextCompat.registerReceiver(this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun executeAction() {
        try {
            when (action) {
                ACTION_NONE -> Unit
                ACTION_LAUNCH_MUSIC -> launchMusicApp()
                ACTION_LAUNCH_APP -> launchCustomApp()
                ACTION_TORCH_TOGGLE -> torchManager?.toggleTorch()
                ACTION_SOUND_TOGGLE -> toggleSound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute walk action", e)
        }
    }

    private fun launchMusicApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, this.getString(R.string.toast_no_music_app_available), Toast.LENGTH_SHORT).show() }
    }

    private fun launchCustomApp() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val packageName = prefs.getString(Constants.Prefs.WALK_DETECT_CUSTOM_APP_PACKAGE, null)
        if (packageName.isNullOrBlank()) {
            Toast.makeText(this, this.getString(R.string.toast_no_app_selected_for_walk_detection), Toast.LENGTH_SHORT).show()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, this.getString(R.string.toast_selected_app_is_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSound() {
        try {
            val currentMode = audioManager.ringerMode
            audioManager.ringerMode = if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling sound", e)
        }
    }
}
