package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenAppManager @Inject constructor(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val HIDDEN_APPS_KEY = "hidden_apps"
        private const val TAG = "HiddenAppManager"
    }


    @Volatile
    private var hiddenAppsCache: Set<String>? = null
    @Volatile
    private var cacheValid = false

    @Synchronized
    private fun getHiddenAppsInternal(): Set<String> {
        if (!cacheValid || hiddenAppsCache == null) {
            try {
                hiddenAppsCache = (sharedPreferences.getStringSet(HIDDEN_APPS_KEY, emptySet()) ?: emptySet()).toSet()
            } catch (e: ClassCastException) {
                Log.e(TAG, "Data corruption: $HIDDEN_APPS_KEY is not a Set. Attempting recovery.", e)
                val stringValue = try { sharedPreferences.getString(HIDDEN_APPS_KEY, null) } catch (_: Exception) { null }
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
                    remove(HIDDEN_APPS_KEY)
                    putStringSet(HIDDEN_APPS_KEY, recoveredSet)
                }
                hiddenAppsCache = recoveredSet.toSet()
            }
            cacheValid = true
        }
        return hiddenAppsCache ?: emptySet()
    }

    @Synchronized
    private fun invalidateCache() {
        cacheValid = false
        hiddenAppsCache = null
    }




    @Synchronized
    fun hideApp(packageName: String) {
        val hiddenApps = getHiddenAppsInternal().toMutableSet()
        hiddenApps.add(packageName)
        sharedPreferences.edit { putStringSet(HIDDEN_APPS_KEY, hiddenApps) }
        hiddenAppsCache = hiddenApps.toSet()
        cacheValid = true
    }




    @Synchronized
    fun unhideApp(packageName: String) {
        val hiddenApps = getHiddenAppsInternal().toMutableSet()
        hiddenApps.remove(packageName)
        sharedPreferences.edit { putStringSet(HIDDEN_APPS_KEY, hiddenApps) }
        hiddenAppsCache = hiddenApps.toSet()
        cacheValid = true
    }




    fun isAppHidden(packageName: String): Boolean {
        return getHiddenAppsInternal().contains(packageName)
    }





    fun forceRefresh() {
        invalidateCache()
    }




    fun getHiddenApps(): Set<String> {
        return getHiddenAppsInternal()
    }




    @Suppress("unused")
    fun filterHiddenApps(apps: List<android.content.pm.ResolveInfo>): List<android.content.pm.ResolveInfo> {
        val hiddenApps = getHiddenApps()
        if (hiddenApps.isEmpty()) {
            return apps
        }
        return apps.filter { !hiddenApps.contains(it.activityInfo.packageName) }
    }
}
