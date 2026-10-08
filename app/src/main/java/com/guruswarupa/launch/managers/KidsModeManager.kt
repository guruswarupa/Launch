package com.guruswarupa.launch.managers

import android.content.Context
import androidx.core.content.edit
import com.guruswarupa.launch.models.Constants

class KidsModeManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KIDS_MODE_ENABLED = "kids_mode_enabled"
        private const val PREF_KIDS_MODE_ALLOWED_APPS = "kids_mode_allowed_apps"
    }

    fun isActive(): Boolean = sharedPreferences.getBoolean(PREF_KIDS_MODE_ENABLED, false)

    fun setActive(active: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_KIDS_MODE_ENABLED, active) }
    }

    fun getAllowedApps(): Set<String> {
        return sharedPreferences.getStringSet(PREF_KIDS_MODE_ALLOWED_APPS, emptySet()) ?: emptySet()
    }

    fun setAllowedApps(packageNames: Set<String>) {
        sharedPreferences.edit { putStringSet(PREF_KIDS_MODE_ALLOWED_APPS, packageNames) }
    }

    fun isAppAllowed(packageName: String): Boolean {
        if (!isActive()) return true
        return getAllowedApps().contains(packageName)
    }
}
