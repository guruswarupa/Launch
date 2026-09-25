package com.guruswarupa.launch.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.TodoItem
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.FlipToDndService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.PhysicalActivityTrackingService
import com.guruswarupa.launch.services.ScreenDimmerService
import com.guruswarupa.launch.services.ShakeDetectionService
import com.guruswarupa.launch.services.WalkDetectionService
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val PREF_PHYSICAL_ACTIVITY_TRACKING_ENABLED = "physical_activity_tracking_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)


            val isDimmerEnabled = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
            val dimLevel = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 50)

            if (isDimmerEnabled && Settings.canDrawOverlays(context)) {
                ScreenDimmerService.startService(context, dimLevel)
            }


            val isNightModeEnabled = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
            val intensity = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)

            if (isNightModeEnabled && Settings.canDrawOverlays(context)) {
                NightModeService.startService(context, intensity)
            }


            val isFlipEnabled = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (isFlipEnabled && notificationManager.isNotificationPolicyAccessGranted) {
                FlipToDndService.startService(context)
            }


            val isBackTapEnabled = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
            if (isBackTapEnabled) {
                val backTapIntent = Intent(context, BackTapService::class.java).apply {
                    action = BackTapService.ACTION_START
                }
                ContextCompat.startForegroundService(context, backTapIntent)
            }


            val isShakeEnabled = prefs.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
            if (isShakeEnabled) {
                val shakeIntent = Intent(context, ShakeDetectionService::class.java).apply {
                    action = ShakeDetectionService.ACTION_START
                }
                ContextCompat.startForegroundService(context, shakeIntent)
            }

            val isWalkDetectEnabled = prefs.getBoolean(Constants.Prefs.WALK_DETECT_ENABLED, false)
            if (isWalkDetectEnabled) {
                val walkIntent = Intent(context, WalkDetectionService::class.java).apply {
                    action = WalkDetectionService.ACTION_START
                }
                ContextCompat.startForegroundService(context, walkIntent)
            }

            val isPhysicalActivityTrackingEnabled = prefs.getBoolean(PREF_PHYSICAL_ACTIVITY_TRACKING_ENABLED, false)
            if (isPhysicalActivityTrackingEnabled) {
                val physicalActivityIntent = Intent(context, PhysicalActivityTrackingService::class.java)
                ContextCompat.startForegroundService(context, physicalActivityIntent)
            }

            rescheduleTodoAlarms(context, prefs)
        }
    }

    // AlarmManager drops all setExactAndAllowWhileIdle alarms on reboot. Todo reminders were
    // otherwise only re-armed once MainActivity/TodoManager.initialize() happened to run, which
    // isn't guaranteed to be the first thing that starts after boot.
    private fun rescheduleTodoAlarms(context: Context, prefs: android.content.SharedPreferences) {
        val jsonString = prefs.getString("todo_items_json", null) ?: return
        try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, TodoItem::class.java)
            val items = moshi.adapter<List<TodoItem>>(type).fromJson(jsonString) ?: return
            TodoAlarmManager(context).rescheduleAllAlarms(items)
        } catch (_: Exception) {
        }
    }
}
