package com.guruswarupa.launch.managers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.*

class ServiceManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    fun updateShakeDetectionService() {
        val isTorchEnabled = sharedPreferences.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        if (isTorchEnabled) {
            startShakeDetectionService()
        } else {
            stopShakeDetectionService()
        }
    }

    fun updateBackTapService() {
        val isBackTapEnabled = sharedPreferences.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
        if (isBackTapEnabled) {
            startBackTapService()
        } else {
            stopBackTapService()
        }
    }

    fun updateWalkDetectionService() {
        val isEnabled = sharedPreferences.getBoolean(Constants.Prefs.WALK_DETECT_ENABLED, false)
        if (isEnabled) {
            val intent = Intent(context, WalkDetectionService::class.java).apply {
                action = WalkDetectionService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            val intent = Intent(context, WalkDetectionService::class.java).apply {
                action = WalkDetectionService.ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private fun startBackTapService() {
        val intent = Intent(context, BackTapService::class.java).apply {
            action = BackTapService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopBackTapService() {
        val intent = Intent(context, BackTapService::class.java).apply {
            action = BackTapService.ACTION_STOP
        }
        context.stopService(intent)
    }

    private fun startShakeDetectionService() {
        val intent = Intent(context, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopShakeDetectionService() {
        val intent = Intent(context, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_STOP
        }
        context.stopService(intent)
    }

    fun updateScreenDimmerService() {
        val isDimmerEnabled = sharedPreferences.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        if (isDimmerEnabled && Settings.canDrawOverlays(context)) {
            val dimLevel = sharedPreferences.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 10)
            ScreenDimmerService.startService(context, dimLevel)
        } else {
            ScreenDimmerService.stopService(context)
        }
    }

    fun updateNightModeService() {
        val isNightModeEnabled = sharedPreferences.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        if (isNightModeEnabled && Settings.canDrawOverlays(context)) {
            val intensity = sharedPreferences.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
            NightModeService.startService(context, intensity)
        } else {
            NightModeService.stopService(context)
        }
    }

    fun updateFlipToDndService() {
        val isFlipEnabled = sharedPreferences.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (isFlipEnabled && notificationManager.isNotificationPolicyAccessGranted) {
            FlipToDndService.startService(context)
        } else {
            FlipToDndService.stopService(context)
        }
    }

    fun updateScreenLockAccessibilityService() {
        val isEdgeEnabled = sharedPreferences.getBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, false)
        val isTriggerEnabled = sharedPreferences.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)

        val intent = Intent(context, ScreenLockAccessibilityService::class.java).apply {
            action = "REFRESH_STATE"
        }
        context.startService(intent)
    }

    fun stopAllServices() {
        stopShakeDetectionService()
        val walkIntent = Intent(context, WalkDetectionService::class.java).apply {
            action = WalkDetectionService.ACTION_STOP
        }
        context.stopService(walkIntent)
        FlipToDndService.stopService(context)
    }
}
