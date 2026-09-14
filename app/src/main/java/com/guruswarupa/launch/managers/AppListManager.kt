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
    private val workProfileManager: WorkProfileManager,
    private val appOrderManager: AppOrderManager,
    private val folderManager: FolderManager,
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



        // In List/Grid, focus/workspace/work-profile mode replaces the favorites-only home view
        // with a flat, curated list of whatever's currently allowed - there's no separate
        // drawer, so the home list has to be the "everything you can use right now" view. Stock
        // always has that separate drawer, so its home page must stay favorites-only regardless
        // of these modes; skip the shortcut and let it fall through to the same routing used
        // when none of these modes are active.
        if ((focusMode || workspaceMode || isWorkProfileEnabled) &&
            !com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
            return apps.sortedWith(comparator)
        }



        if (showOnlyFavorites) {
            return if (com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
                computeStockHomeOrdered(apps)
            } else {
                val favorites = favoriteAppManager.getFavoriteApps()
                apps.filter { favorites.contains(it.activityInfo.packageName) }.sortedWith(comparator)
            }
        } else {
            return apps.sortedWith(comparator)
        }
    }

    /**
     * The Stock apps eligible for the home page and dock: favorites, unless
     * [Constants.Prefs.STOCK_DRAWER_ENABLED] is off, in which case every app is eligible since
     * the home page is then the only place to find them.
     */
    private fun computeStockEligibleApps(filteredApps: List<ResolveInfo>): List<ResolveInfo> {
        val includeAllApps = !sharedPreferences.getBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, true)
        return if (includeAllApps) {
            filteredApps
        } else {
            val favorites = favoriteAppManager.getFavoriteApps()
            filteredApps.filter { favorites.contains(it.activityInfo.packageName) }
        }
    }

    /**
     * The Stock dock's contents: only apps/folders the user explicitly placed there (dragged
     * in), never auto-derived from home order - membership is its own independent, persisted
     * list, capped at [getStockHotseatCount].
     */
    fun computeStockDock(filteredApps: List<ResolveInfo>): List<ResolveInfo> {
        val sourceApps = computeStockEligibleApps(filteredApps)
        val folders = folderManager.getFolders(Constants.Prefs.STOCK_HOME_FOLDERS)
        return appOrderManager.applyOrder(
            sourceApps, this, Constants.Prefs.STOCK_DOCK_ORDER, folders, includeUnlisted = false
        ).take(getStockHotseatCount())
    }

    /**
     * Computes the Stock home page's own ordered grid, from [filteredApps] already filtered for
     * focus/workspace/work-profile - excluding whatever's currently placed in the dock (an app
     * moves out of the grid the moment it's dragged into the dock, and reappears here the moment
     * it's removed from the dock, with no separate bookkeeping needed).
     */
    fun computeStockHomeOrdered(filteredApps: List<ResolveInfo>): List<ResolveInfo> {
        val sourceApps = computeStockEligibleApps(filteredApps)
        val dockCoveredKeys = computeStockDockCoveredKeys()
        val eligibleForGrid = if (dockCoveredKeys.isEmpty()) {
            sourceApps
        } else {
            sourceApps.filter { appOrderManager.keyOf(it) !in dockCoveredKeys }
        }
        val folders = folderManager.getFolders(Constants.Prefs.STOCK_HOME_FOLDERS)
        return appOrderManager.applyOrder(eligibleForGrid, this, Constants.Prefs.STOCK_HOME_APP_ORDER, folders)
    }

    /**
     * Reads the dock's raw saved tokens directly (not via [computeStockDock]'s full apps-list
     * resolution) to determine which app keys the dock currently covers - simpler, and
     * independent of whatever's actually installed/eligible right now.
     */
    private fun computeStockDockCoveredKeys(): Set<String> {
        val tokens = appOrderManager.loadOrderTokens(Constants.Prefs.STOCK_DOCK_ORDER)
        if (tokens.isEmpty()) return emptySet()
        val dockFolders = folderManager.getFolders(Constants.Prefs.STOCK_HOME_FOLDERS)
        val keys = HashSet<String>()
        tokens.forEach { token ->
            if (token.startsWith(AppOrderManager.FOLDER_TOKEN_PREFIX)) {
                val folderId = token.removePrefix(AppOrderManager.FOLDER_TOKEN_PREFIX)
                dockFolders.find { it.id == folderId }?.appKeys?.let { keys.addAll(it) }
            } else {
                keys.add(token)
            }
        }
        return keys
    }

    fun getStockHotseatCount(): Int =
        sharedPreferences.getInt(Constants.Prefs.STOCK_HOTSEAT_COUNT, 4).coerceIn(3, 5)

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
        if (showOnlyFavorites && com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
            // Stock mode's home grid needs none of the spacer/letter-separator scaffolding that
            // exists to fake the scroll-to-all-apps transition the other display styles use -
            // unless the drawer itself is disabled, in which case the launcher shortcuts need to
            // live at the bottom of the home list instead, same as they would in the drawer.
            val drawerEnabled = sharedPreferences.getBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, true)
            return if (drawerEnabled) {
                apps
            } else {
                apps + createSeparatorInfo("SMALL") +
                    createLauncherShortcut("launcher_settings_shortcut") +
                    createLauncherShortcut("launcher_vault_shortcut")
            }
        }

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
                if (!showOnlyFavorites && firstChar != null) {
                    val separatorLetter = if (firstChar.isLetter()) firstChar else '#'
                    if (separatorLetter != lastLetter) {
                        result.add(createSeparatorInfo("letter_separator_$separatorLetter"))
                        lastLetter = separatorLetter
                    }
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
            val isGridMode = com.guruswarupa.launch.utils.LayoutMode.isGridRendering(sharedPreferences)

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
            result.add(createSeparatorInfo("SMALL"))
            result.add(createLauncherShortcut("launcher_settings_shortcut"))
            result.add(createLauncherShortcut("launcher_vault_shortcut"))
        }

        return result
    }

    fun prepareSortedList(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean,
        showOnlyFavorites: Boolean
    ): List<ResolveInfo> {
        val filtered = filterAndPrepareApps(apps, focusMode, workspaceMode)
        return sortAppsAlphabetically(filtered, showOnlyFavorites)
    }

    fun createSeparatorInfo(id: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = "com.guruswarupa.launch.SEPARATOR"
        ri.activityInfo.name = id
        return ri
    }

    fun createLauncherShortcut(shortcutType: String): ResolveInfo {
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

    fun getDisplayLabel(app: ResolveInfo): String {
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
