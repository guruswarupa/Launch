package com.guruswarupa.launch.managers

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.EditText
import android.widget.Toast
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView




@Suppress("unused")
class DailyUsageManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("daily_usage_prefs", Context.MODE_PRIVATE)
    private val appUsageStatsManager = AppUsageStatsManager(context)

    companion object {
        private const val PREF_DAILY_LIMIT_PREFIX = "daily_limit_"
        private const val PREF_DAILY_USAGE_PREFIX = "daily_usage_"
        private const val PREF_LAST_RESET_DATE = "last_reset_date"
        private const val PREF_TIMER_ENABLED_PREFIX = "timer_enabled_"
    }




    fun isTimerEnabled(packageName: String): Boolean {
        return prefs.getBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", false)
    }




    fun setTimerEnabled(packageName: String, enabled: Boolean) {
        prefs.edit { putBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", enabled) }
    }




    fun getDailyLimit(packageName: String): Long {
        return prefs.getLong("${PREF_DAILY_LIMIT_PREFIX}$packageName", 0L)
    }




    fun setDailyLimit(packageName: String, limitMs: Long) {
        prefs.edit { putLong("${PREF_DAILY_LIMIT_PREFIX}$packageName", limitMs) }
    }




    fun getTodayUsage(packageName: String): Long {
        resetIfNewDay()
        return appUsageStatsManager.getAppUsageTime(packageName)
    }




    fun getTodayUsageMap(): Map<String, Long> {
        resetIfNewDay()
        return appUsageStatsManager.getUsageMapForToday()
    }




    fun recordAppUsage(packageName: String) {
        resetIfNewDay()
        prefs.edit { putLong("last_used_$packageName", System.currentTimeMillis()) }
    }




    fun canLaunchApp(packageName: String): Boolean {
        if (!isTimerEnabled(packageName)) {
            return true
        }

        val limit = getDailyLimit(packageName)
        if (limit == 0L) {
            return true
        }

        val usage = getTodayUsage(packageName)
        return usage < limit
    }




    fun getRemainingTime(packageName: String): Long {
        if (!isTimerEnabled(packageName)) {
            return Long.MAX_VALUE
        }

        val limit = getDailyLimit(packageName)
        if (limit == 0L) {
            return Long.MAX_VALUE
        }

        val usage = getTodayUsage(packageName)
        return maxOf(0L, limit - usage)
    }




    fun formatTime(ms: Long): String {
        if (ms == Long.MAX_VALUE) {
            return "Unlimited"
        }

        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }




    fun showSetLimitDialog(packageName: String, appName: String, onLimitSet: (Long) -> Unit) {
        val currentLimit = getDailyLimit(packageName)
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.daily_usage_hint_minutes_disable)
            setText(if (currentLimit > 0) (currentLimit / 60000).toString() else "")
            DialogStyler.styleInput(context, this)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.daily_usage_set_limit_title, appName))
            .setMessage(R.string.daily_usage_set_limit_message)
            .setDialogInputView(context, input)
            .setPositiveButton(R.string.daily_usage_action_set) { _, _ ->
                try {
                    val minutes = input.text.toString().toLongOrNull() ?: 0L
                    val limitMs = minutes * 60000L
                    setDailyLimit(packageName, limitMs)
                    setTimerEnabled(packageName, limitMs > 0)
                    onLimitSet(limitMs)
                    Toast.makeText(
                        context,
                        if (limitMs > 0) {
                            context.getString(R.string.daily_usage_limit_set, formatTime(limitMs))
                        } else {
                            context.getString(R.string.daily_usage_limit_disabled)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, R.string.daily_usage_invalid_number, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }




    private fun resetIfNewDay() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastResetDate = prefs.getLong(PREF_LAST_RESET_DATE, 0L)

        if (lastResetDate != today) {
            prefs.edit {
                val allPrefs = prefs.all
                for (key in allPrefs.keys) {
                    if (key.startsWith("last_used_")) {
                        remove(key)
                    }
                }
                putLong(PREF_LAST_RESET_DATE, today)
            }
        }
    }




    fun resetUsage(packageName: String) {
        prefs.edit { remove("${PREF_DAILY_USAGE_PREFIX}$packageName") }
    }




    fun getAppsWithTimers(): Set<String> {
        val apps = mutableSetOf<String>()
        val allPrefs = prefs.all
        for (key in allPrefs.keys) {
            if (key.startsWith(PREF_TIMER_ENABLED_PREFIX) && prefs.getBoolean(key, false)) {
                val packageName = key.removePrefix(PREF_TIMER_ENABLED_PREFIX)
                apps.add(packageName)
            }
        }
        return apps
    }
}
