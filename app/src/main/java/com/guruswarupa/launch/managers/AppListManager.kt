package com.guruswarupa.launch.managers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.SharedPreferences
import android.os.Process
import android.os.UserManager
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.models.Constants
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Locale
import javax.inject.Inject

@ActivityScoped
class AppListManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val favoriteAppManager: FavoriteAppManager,
    private val hiddenAppManager: HiddenAppManager,
    private val cacheManager: CacheManager,
    private val workspaceManager: WorkspaceManager,
    private val workProfileManager: WorkProfileManager,
    private val sharedPreferences: SharedPreferences
) {
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()
    private var appDockManager: AppDockManager? = null

    fun attach(appDockManager: AppDockManager) {
        this.appDockManager = appDockManager
    }

    private fun requireAppDockManager(): AppDockManager = requireNotNull(appDockManager) { "AppDockManager not attached" }

    fun getFavoriteApps(): Set<String> {
        return favoriteAppManager.getFavoriteApps()
    }

    fun filterAndPrepareApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        val dockManager = requireAppDockManager()

        return apps.filter { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name

            val isLauncherApp = packageName == "com.guruswarupa.launch"
            val isAllowedInternalActivity = isLauncherApp && (activityName.contains("SettingsActivity") ||
                                              activityName.contains("EncryptedVaultActivity"))

            if (isLauncherApp && !isAllowedInternalActivity) return@filter false

            if (isAllowedInternalActivity) {
                return@filter !(focusMode && activityName.contains("SettingsActivity"))
            }

            if (hiddenAppManager?.isAppHidden(packageName) == true) return@filter false

            val isWorkApp = app.preferredOrder != mainUserSerial


            if (focusMode) {
                return@filter if (isWorkApp) true else !dockManager.isAppHiddenInFocusMode(packageName)
            }

            if (isWorkProfileEnabled) {
                if (!isWorkApp) return@filter false
            } else {
                if (isWorkApp) return@filter false

                if (workspaceMode && !dockManager.isAppInActiveWorkspace(packageName)) {
                    return@filter false
                }
            }

            true
        }
    }

    fun sortAppsAlphabetically(apps: List<ResolveInfo>, showOnlyFavorites: Boolean = false): List<ResolveInfo> {
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()

        val comparator = compareBy<ResolveInfo>(
            { if (isInternalApp(it)) 1 else 0 },
            { getSortKey(getDisplayLabel(it).lowercase(Locale.ROOT)) },
            { it.activityInfo.packageName },
            { it.activityInfo.name }
        )



        if (focusMode || workspaceMode || isWorkProfileEnabled) {
            return apps.sortedWith(comparator)
        }



        if (showOnlyFavorites) {
            val favorites = favoriteAppManager.getFavoriteApps()
            val favoriteApps = apps.filter { favorites.contains(it.activityInfo.packageName) }
            return favoriteApps.sortedWith(comparator)
        } else {
            return apps.sortedWith(comparator)
        }
    }

    fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (!firstChar.isLetter()) {
            "\uFFFF$label"
        } else {
            label
        }
    }

    fun addSeparators(apps: List<ResolveInfo>, showOnlyFavorites: Boolean = false): List<ResolveInfo> {
        if (apps.isEmpty()) return apps

        val result = mutableListOf<ResolveInfo>()
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()


        val showFavoritesSection = showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled



        if (!showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled) {
            val favorites = favoriteAppManager.getFavoriteApps()
            if (favorites.isNotEmpty()) {
                // Add enough spacers to ensure scrollability back to favorites
                for (i in 0 until 3) {
                    result.add(createSeparatorInfo("all_apps_top_spacer_$i"))
                }
            }
        }

        var lastLetter: Char? = null

        for (app in apps) {
            val packageName = app.activityInfo.packageName
            val isInternal = isInternalApp(app)

            if (!isInternal) {
                val label = getDisplayLabel(app)
                val firstChar = if (label.isNotEmpty()) label[0].uppercaseChar() else null
                
                // Only add letter separators when NOT in favorites-only mode
                if (!showOnlyFavorites && firstChar != null && firstChar != lastLetter) {
                    val separatorLetter = if (firstChar.isLetter()) firstChar else '#'
                    result.add(createSeparatorInfo("letter_separator_$separatorLetter"))
                    lastLetter = firstChar
                }
            } else if (lastLetter != null) {
                // Add large separator before system apps (Settings/Vault) to force new row
                if (lastLetter != '⚙') {
                    result.add(createSeparatorInfo("system_separator"))
                    lastLetter = '⚙'
                }
            }

            result.add(app)
        }





        if (showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled) {
            val favorites = favoriteAppManager.getFavoriteApps()
            val favoriteCount = favorites.size

            val isTopWidgetVisible = sharedPreferences.getBoolean(Constants.Prefs.TOP_WIDGET_ENABLED, true)
            val viewPreference = sharedPreferences.getString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST)
            val isGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID

            if (isGridMode) {
                // Grid mode: add more spacers when top widget is hidden
                val spacerCount = if (isTopWidgetVisible) 15 else 20
                for (i in 0 until spacerCount) {
                    result.add(createSeparatorInfo("favorites_bottom_spacer_$i"))
                }
            } else {
                // List mode: dynamic spacer count
                val baseCount = if (isTopWidgetVisible) 20 else 25
                val spacerCount = maxOf(8, baseCount - (favoriteCount - 1).coerceAtLeast(0))

                for (i in 0 until spacerCount) {
                    result.add(createSeparatorInfo("favorites_bottom_spacer_$i"))
                }
            }

            if (result.isNotEmpty()) {
                result.add(createSeparatorInfo("bottom_system_separator"))
            }
        }


        if (!showOnlyFavorites || focusMode || workspaceMode || isWorkProfileEnabled) {
            result.add(createSeparatorInfo("launcher_shortcuts_separator"))
            result.add(createLauncherShortcut("launcher_settings_shortcut"))
            result.add(createLauncherShortcut("launcher_vault_shortcut"))
        }

        return result
    }

    private fun createSeparatorInfo(id: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = "com.guruswarupa.launch.SEPARATOR"
        ri.activityInfo.name = id
        return ri
    }

    private fun createLauncherShortcut(shortcutType: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = shortcutType

        ri.activityInfo.name = when (shortcutType) {
            "launcher_settings_shortcut" -> "Launch Settings"
            "launcher_vault_shortcut" -> "Launch Vault"
            else -> shortcutType
        }

        ri.activityInfo.applicationInfo = android.content.pm.ApplicationInfo().apply {
            packageName = shortcutType
        }
        return ri
    }

    fun getFocusMode(): Boolean = requireAppDockManager().getCurrentMode()
    fun getWorkspaceMode(): Boolean = requireAppDockManager().isWorkspaceModeActive()
    fun isWorkProfileModeEnabled(): Boolean = workProfileManager.isWorkProfileEnabled()
    fun isWorkProfileApp(app: ResolveInfo): Boolean = app.preferredOrder != mainUserSerial

    private fun isInternalApp(app: ResolveInfo): Boolean {
        val packageName = app.activityInfo.packageName
        val activityName = app.activityInfo.name
        return packageName == "com.guruswarupa.launch" &&
            (activityName.contains("SettingsActivity") || activityName.contains("EncryptedVaultActivity"))
    }

    private fun getDisplayLabel(app: ResolveInfo): String {
        val packageName = app.activityInfo.packageName
        if (WebAppManager.isWebAppPackage(packageName)) {
            return app.activityInfo.name.ifBlank { packageName }
        }

        val cacheKey = "${packageName}|${app.preferredOrder}"
        val cachedLabel = cacheManager?.getMetadataCache()?.get(cacheKey)?.label
        if (!cachedLabel.isNullOrBlank()) {
            return cachedLabel
        }

        val activityName = app.activityInfo.name
        if (activityName.isNotBlank() && packageName.startsWith("launcher_")) {
            return activityName
        }

        return try {
            app.loadLabel(context.packageManager).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
