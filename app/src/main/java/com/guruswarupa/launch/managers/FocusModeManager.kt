package com.guruswarupa.launch.managers

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log

class FocusModeManager(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"
        private const val FOCUS_MODE_ALLOWED_APPS = "focus_mode_allowed_apps"
        private const val TAG = "FocusModeManager"
    }

    fun isFocusModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(FOCUS_MODE_ENABLED, false)
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(FOCUS_MODE_ENABLED, enabled) }


        val intent = Intent("com.guruswarupa.launch.FOCUS_MODE_CHANGED").apply {
            `package` = context.packageName
            putExtra("focus_mode_enabled", enabled)
        }
        context.sendBroadcast(intent)
    }

    fun updateDndState(enabled: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val filter = if (enabled) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        if (notificationManager.currentInterruptionFilter != filter) {
            try {
                notificationManager.setInterruptionFilter(filter)
            } catch (_: Exception) {}
        }
    }

    fun getAllowedApps(): Set<String> {
        return try {
            sharedPreferences.getStringSet(FOCUS_MODE_ALLOWED_APPS, emptySet()) ?: emptySet()
        } catch (e: ClassCastException) {
            Log.e(TAG, "Data corruption: $FOCUS_MODE_ALLOWED_APPS is not a Set. Attempting recovery.", e)
            val stringValue = try { sharedPreferences.getString(FOCUS_MODE_ALLOWED_APPS, null) } catch (_: Exception) { null }
            val recoveredSet = if (stringValue != null) {
                if (stringValue.startsWith("[") && stringValue.endsWith("]")) {
                    stringValue.substring(1, stringValue.length - 1)
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                } else {
                    setOf(stringValue)
                }
            } else {
                emptySet()
            }

            sharedPreferences.edit {
                remove(FOCUS_MODE_ALLOWED_APPS)
                putStringSet(FOCUS_MODE_ALLOWED_APPS, recoveredSet)
            }
            recoveredSet
        }
    }

    fun updateAllowedApps(packageNames: Set<String>) {
        sharedPreferences.edit { putStringSet(FOCUS_MODE_ALLOWED_APPS, packageNames) }
    }

    fun addAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.add(packageName)
        updateAllowedApps(currentApps)
    }

    fun removeAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.remove(packageName)
        updateAllowedApps(currentApps)
    }
}
