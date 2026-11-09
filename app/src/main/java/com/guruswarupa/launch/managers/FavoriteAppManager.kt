package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

private const val OWN_PACKAGE = "com.guruswarupa.launch"

/**
 * Launch's own apps (Calculator, Calendar, Files...) all live in one package, so a favorite for them has to
 * include the activity name. Every other app is identified by its package name as before.
 */
fun favoriteKey(app: android.content.pm.ResolveInfo): String {
    val pkg = app.activityInfo.packageName
    return if (pkg == OWN_PACKAGE) "$pkg/${app.activityInfo.name}" else pkg
}

@Singleton
class FavoriteAppManager @Inject constructor(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FAVORITE_APPS_KEY = "favorite_apps"
        private const val TAG = "FavoriteAppManager"
    }

    @Volatile
    private var favoritesCache: Set<String>? = null
    @Volatile
    private var cacheValid = false

    @Synchronized
    private fun getFavoriteAppsInternal(): Set<String> {
        if (!cacheValid || favoritesCache == null) {
            try {
                favoritesCache = (sharedPreferences.getStringSet(FAVORITE_APPS_KEY, emptySet()) ?: emptySet()).toSet()
            } catch (e: ClassCastException) {

                val stringValue = try { sharedPreferences.getString(FAVORITE_APPS_KEY, null) } catch (_: Exception) { null }
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
                    remove(FAVORITE_APPS_KEY)
                    putStringSet(FAVORITE_APPS_KEY, recoveredSet)
                }
                favoritesCache = recoveredSet.toSet()
            }
            // Older versions stored a bare package name for Launch's own apps, which marked all of them as favorites at once.
            if (favoritesCache?.contains(OWN_PACKAGE) == true) {
                val cleaned = favoritesCache!!.toMutableSet().apply { remove(OWN_PACKAGE) }
                sharedPreferences.edit { putStringSet(FAVORITE_APPS_KEY, cleaned) }
                favoritesCache = cleaned.toSet()
            }
            cacheValid = true
        }
        return favoritesCache ?: emptySet()
    }

    @Synchronized
    fun addFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.add(packageName)
        sharedPreferences.edit { putStringSet(FAVORITE_APPS_KEY, favorites) }
        favoritesCache = favorites.toSet()
        cacheValid = true
    }

    @Synchronized
    fun removeFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.remove(packageName)
        sharedPreferences.edit { putStringSet(FAVORITE_APPS_KEY, favorites) }
        favoritesCache = favorites.toSet()
        cacheValid = true
    }

    fun isFavoriteApp(packageName: String): Boolean {
        return getFavoriteAppsInternal().contains(packageName)
    }

    fun getFavoriteApps(): Set<String> {
        return getFavoriteAppsInternal()
    }
}
