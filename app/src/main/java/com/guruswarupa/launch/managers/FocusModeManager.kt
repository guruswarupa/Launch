package com.guruswarupa.launch.managers

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit

class FocusModeManager(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"

        private const val FOCUS_MODE_BLOCKED_APPS = "focus_mode_blocked_apps"
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

    fun getBlockedApps(): Set<String> {
        return try {
            sharedPreferences.getStringSet(FOCUS_MODE_BLOCKED_APPS, emptySet()) ?: emptySet()
        } catch (e: ClassCastException) {
            val stringValue = try { sharedPreferences.getString(FOCUS_MODE_BLOCKED_APPS, null) } catch (_: Exception) { null }
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
                remove(FOCUS_MODE_BLOCKED_APPS)
                putStringSet(FOCUS_MODE_BLOCKED_APPS, recoveredSet)
            }
            recoveredSet
        }
    }

    fun updateBlockedApps(packageNames: Set<String>) {
        sharedPreferences.edit { putStringSet(FOCUS_MODE_BLOCKED_APPS, packageNames) }
    }

    fun addBlockedApp(packageName: String) {
        val currentApps = getBlockedApps().toMutableSet()
        currentApps.add(packageName)
        updateBlockedApps(currentApps)
    }

    fun removeBlockedApp(packageName: String) {
        val currentApps = getBlockedApps().toMutableSet()
        currentApps.remove(packageName)
        updateBlockedApps(currentApps)
    }
}
