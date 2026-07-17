package com.guruswarupa.launch.managers

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.models.AppMetadata
import com.guruswarupa.launch.models.Constants

class AppListLoader(
    private val activity: MainActivity,
    private val packageManager: PackageManager,
    private val appListManager: AppListManager,
    private val appDockManager: AppDockManager,
    private val cacheManager: CacheManager?,
    private val webAppManager: WebAppManager,
    private val backgroundExecutor: Executor,
    private val resourceLoader: Executor,
    private val handler: Handler,
    private val recyclerView: RecyclerView,
    private val searchBox: EditText,
    private val voiceSearchButton: ImageButton,
    private val sharedPreferences: android.content.SharedPreferences
) {
    companion object {
        private const val TAG = "AppListLoader"
        // Constants moved to Constants.Timeouts for centralized management
    }

    @Volatile
    private var cachedUnsortedList: List<ResolveInfo>? = null
    @Volatile
    private var lastCacheTime = 0L
    private val cacheDuration = Constants.Timeouts.APP_LIST_CACHE_DURATION_MS
    
    private val retryLock = Any()
    private var workProfileEmptyRetryCount = 0
    private var generalEmptyRetryCount = 0
    
    @Volatile
    private var lastWorkProfileEnabledState = false
    @Volatile
    private var lastWorkProfileRetryTime = 0L
    private val currentUserSerial by lazy {
        val userManager = activity.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()
    }

    var onAppListUpdated: ((List<ResolveInfo>, List<ResolveInfo>, Boolean) -> Unit)? = null
    var onAdapterNeedsUpdate: ((Boolean) -> Unit)? = null

    private fun safeExecute(task: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        try {
            if ((backgroundExecutor as? java.util.concurrent.ExecutorService)?.isShutdown == true) {
                Log.w(TAG, "Background executor is shut down, skipping task")
                return false
            }
            backgroundExecutor.execute(task)
            return true
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Task rejected by executor", e)
            return false
        }
    }

    fun loadApps(forceRefresh: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return

        val adapter = activity.adapter
        loadApps(forceRefresh, activity.fullAppList, activity.appList, adapter)
    }

    fun loadApps(forceRefresh: Boolean = false, fullAppList: MutableList<ResolveInfo>, appList: MutableList<ResolveInfo>, adapter: AppAdapter?) {
        val viewPreference = sharedPreferences.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        )
        val isGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID

        val currentTime = System.currentTimeMillis()
        if (forceRefresh) {
            cachedUnsortedList = null
            cacheManager?.clearCache()
        } else if (cacheManager != null && cacheManager.isCacheValid()) {
            val cachedAppsRaw = cacheManager.loadAppListFromCache()
            if (cachedAppsRaw.isNotEmpty()) {
                val cachedApps = cachedAppsRaw.distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}|${it.preferredOrder}" }
                cacheManager.loadAppMetadataFromCache()

                try {
                    val focusMode = appDockManager.getCurrentMode()
                    val workspaceMode = appDockManager.isWorkspaceModeActive()

                    val cachedAppsWithWebApps = appendWebApps(cachedApps).distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}|${it.preferredOrder}" }
                    val cachedFinalList = appListManager.filterAndPrepareApps(cachedAppsWithWebApps, focusMode, workspaceMode)


                    if (activity.showOnlyFavoritesInitially && !focusMode && !workspaceMode) {
                        val favorites = appListManager.getFavoriteApps()
                        if (favorites.isEmpty()) {
                            activity.showOnlyFavoritesInitially = false
                        }
                    }

                    if (cachedFinalList.isNotEmpty() && adapter != null) {
                        generalEmptyRetryCount = 0
                        val sorted = appListManager.sortAppsAlphabetically(cachedFinalList, activity.showOnlyFavoritesInitially)
                        handler.post {
                            onAppListUpdated?.invoke(sorted, cachedAppsWithWebApps, false)
                        }

                        safeExecute {
                            if (!cacheManager.isVersionCurrent()) {
                                handler.post {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        loadApps(forceRefresh = false, fullAppList, appList, adapter)
                                    }
                                }
                            }
                        }

                        updateSearchVisibility()
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking work profile app list availability", e)
                }
            }
        }

        val currentCachedUnsortedList = cachedUnsortedList
        if (!forceRefresh && currentCachedUnsortedList != null &&
            (System.currentTimeMillis() - lastCacheTime) < cacheDuration &&
            fullAppList.isNotEmpty()) {
            try {
                val focusMode = appDockManager.getCurrentMode()
                val workspaceMode = appDockManager.isWorkspaceModeActive()

                val cachedAppsWithWebApps = appendWebApps(currentCachedUnsortedList).distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}|${it.preferredOrder}" }
                val cachedFinalList = appListManager.filterAndPrepareApps(cachedAppsWithWebApps, focusMode, workspaceMode)

                if (cachedFinalList.isNotEmpty() && adapter != null) {
                    synchronized(retryLock) {
                        generalEmptyRetryCount = 0
                    }
                    val sorted = appListManager.sortAppsAlphabetically(cachedFinalList, activity.showOnlyFavoritesInitially)
                    handler.post {
                        onAppListUpdated?.invoke(sorted, cachedAppsWithWebApps, false)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading apps from cache", e)
            }
        }

        handler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            recyclerView.visibility = View.VISIBLE

            if (adapter == null) {
                recyclerView.layoutManager = if (isGridMode) {
                    GridLayoutManager(activity, activity.getPreferredGridColumns())
                } else {
                    LinearLayoutManager(activity)
                }
                onAdapterNeedsUpdate?.invoke(isGridMode)
            }
        }

        safeExecute {
            try {
                val currentCached = cachedUnsortedList
                val unsortedList = if (!forceRefresh &&
                    currentCached != null &&
                    (currentTime - lastCacheTime) < cacheDuration) {
                    currentCached
                } else {
                    val list = mutableListOf<ResolveInfo>()

                    val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userManager = activity.getSystemService(Context.USER_SERVICE) as UserManager


                    for (user in launcherApps.profiles) {
                        val serial = userManager.getSerialNumberForUser(user).toInt()
                        val apps = launcherApps.getActivityList(null, user)

                        Log.d("AppListLoader", "Loading apps for user profile - Serial: $serial, App count: ${apps.size}")

                        for (app in apps) {
                            val packageName = app.componentName.packageName
                            if (packageName == "com.guruswarupa.launch") continue

                            val resolveInfo = ResolveInfo()
                            val activityInfo = android.content.pm.ActivityInfo()
                            activityInfo.packageName = packageName
                            activityInfo.name = app.componentName.className
                            activityInfo.applicationInfo = app.applicationInfo

                            resolveInfo.activityInfo = activityInfo

                            resolveInfo.preferredOrder = serial
                            list.add(resolveInfo)
                        }
                    }

                    cachedUnsortedList = list
                    lastCacheTime = currentTime

                    cacheManager?.let { cm ->
                        safeExecute {
                            try {
                                cm.saveAppListToCache(list)

                                cm.preloadAppMetadata(list)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error saving app list to cache", e)
                            }
                        }
                    }
                    list
                }

                val fullList = appendWebApps(unsortedList)

                if (fullList.isEmpty()) {
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        onAppListUpdated?.invoke(emptyList(), emptyList(), true)
                        if (adapter == null) {
                            onAdapterNeedsUpdate?.invoke(isGridMode)
                        }
                    }
                } else {
                    synchronized(retryLock) {
                        generalEmptyRetryCount = 0
                    }
                    val focusMode = appDockManager.getCurrentMode()
                    val workspaceMode = appDockManager.isWorkspaceModeActive()
                    val finalAppList = appListManager.filterAndPrepareApps(fullList, focusMode, workspaceMode)

                    if (shouldRetryForEmptyWorkProfileList(fullList, finalAppList)) {
                        return@safeExecute
                    }

                    if (finalAppList.isNotEmpty()) {
                        synchronized(retryLock) {
                            workProfileEmptyRetryCount = 0
                            generalEmptyRetryCount = 0
                        }
                    }



                    resourceLoader.execute {
                        try {
                            val metadataCacheInner = cacheManager?.getMetadataCache() ?: emptyMap()
                            finalAppList.forEach { app ->
                                val packageName = app.activityInfo.packageName
                                val cacheKey = "${packageName}|${app.preferredOrder}"
                                val cached = metadataCacheInner[cacheKey]
                                if (cached == null) {
                                    try {
                                        val label = app.loadLabel(packageManager).toString()
                                        cacheManager?.updateMetadataCache(cacheKey,
                                            AppMetadata(
                                                packageName = packageName,
                                                activityName = app.activityInfo.name,
                                                label = label,
                                                lastUpdated = System.currentTimeMillis()
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error updating metadata cache for $packageName", e)
                                    }
                                }
                            }

                            cacheManager?.saveAppMetadataToCache(cacheManager.getMetadataCache())


                            val sortedApps = appListManager.sortAppsAlphabetically(finalAppList, activity.showOnlyFavoritesInitially)
                            handler.post {
                                if (activity.isFinishing || activity.isDestroyed) return@post
                                onAppListUpdated?.invoke(sortedApps, fullList, true)
                                if (adapter == null) {
                                    onAdapterNeedsUpdate?.invoke(isGridMode)
                                } else if (recyclerView.adapter != adapter) {
                                    recyclerView.adapter = adapter
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error updating adapter with loaded apps", e)
                        }
                    }

















                }
            } catch (e: Exception) {
                handler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (appList.isEmpty() && !forceRefresh) {
                        var shouldRetry = false
                        synchronized(retryLock) {
                            if (generalEmptyRetryCount < Constants.Timeouts.MAX_GENERAL_EMPTY_RETRIES) {
                                generalEmptyRetryCount++
                                shouldRetry = true
                            }
                        }
                        
                        if (shouldRetry) {
                            Log.d(
                                TAG,
                                "App list empty after error; retrying load (${generalEmptyRetryCount}/${Constants.Timeouts.MAX_GENERAL_EMPTY_RETRIES})"
                            )
                            handler.postDelayed(
                                { loadApps(forceRefresh = true, fullAppList, appList, adapter) },
                                Constants.Timeouts.GENERAL_EMPTY_RETRY_DELAY_MS
                            )
                        } else {
                            Log.w(TAG, "App list still empty after max retries; showing error state")
                        }
                    }
                    if (appList.isEmpty()) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.app_list_error_loading_apps, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
        updateSearchVisibility()
    }

    private fun shouldRetryForEmptyWorkProfileList(
        fullList: List<ResolveInfo>,
        finalAppList: List<ResolveInfo>
    ): Boolean {
        val isWorkProfileModeEnabled = sharedPreferences.getBoolean("work_profile_enabled", false)
        val currentTime = System.currentTimeMillis()

        synchronized(retryLock) {
            // Reset retry counter if work profile state changed since last check
            if (lastWorkProfileEnabledState != isWorkProfileModeEnabled) {
                workProfileEmptyRetryCount = 0
                lastWorkProfileEnabledState = isWorkProfileModeEnabled
                Log.d(TAG, "Work profile state changed to $isWorkProfileModeEnabled, resetting retry counter")
            }

            // Reset retry counter if enough time has passed since last retry attempt
            if (lastWorkProfileRetryTime > 0 &&
                (currentTime - lastWorkProfileRetryTime) > Constants.Timeouts.WORK_PROFILE_RETRY_RESET_TIMEOUT_MS) {
                workProfileEmptyRetryCount = 0
                Log.d(TAG, "Work profile retry timeout exceeded, resetting retry counter")
            }

            if (!isWorkProfileModeEnabled || finalAppList.isNotEmpty()) {
                if (!isWorkProfileModeEnabled) {
                    workProfileEmptyRetryCount = 0
                }
                return false
            }

            val hasLoadedWorkApps = fullList.any(::isWorkProfileApp)
            if (hasLoadedWorkApps) {
                workProfileEmptyRetryCount = 0
                return false
            }

            if (workProfileEmptyRetryCount >= Constants.Timeouts.MAX_WORK_PROFILE_EMPTY_RETRIES) {
                Log.w(TAG, "Work profile list still empty after retries; showing empty state")
                return false
            }

            workProfileEmptyRetryCount += 1
            lastWorkProfileRetryTime = currentTime
            Log.d(
                TAG,
                "Work profile apps not available yet; retrying load (${workProfileEmptyRetryCount}/${Constants.Timeouts.MAX_WORK_PROFILE_EMPTY_RETRIES})"
            )
        }
        
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                loadApps(
                    forceRefresh = true,
                    fullAppList = activity.fullAppList,
                    appList = activity.appList,
                    adapter = activity.adapter
                )
            }
        }, Constants.Timeouts.WORK_PROFILE_EMPTY_RETRY_DELAY_MS)
        return true
    }

    private fun isWorkProfileApp(app: ResolveInfo): Boolean {
        val packageName = app.activityInfo.packageName
        return !WebAppManager.isWebAppPackage(packageName) && app.preferredOrder != currentUserSerial
    }

    private fun updateSearchVisibility() {
        if (!activity.isFinishing && !activity.isDestroyed) {
            handler.post {
                searchBox.visibility = View.VISIBLE
                voiceSearchButton.visibility = View.VISIBLE
            }
        }
    }

    fun clearCache() {
        cachedUnsortedList = null
        lastCacheTime = 0L
        synchronized(retryLock) {
            generalEmptyRetryCount = 0
            workProfileEmptyRetryCount = 0
        }
        lastWorkProfileEnabledState = false
        lastWorkProfileRetryTime = 0L
    }

    private fun appendWebApps(installedApps: List<ResolveInfo>): List<ResolveInfo> {
        val webApps = webAppManager.getWebApps()
        if (webApps.isEmpty()) return installedApps

        val fullList = ArrayList<ResolveInfo>(installedApps.size + webApps.size)
        val seenKeys = HashSet<String>(installedApps.size + webApps.size)
        installedApps.forEach { app ->
            val key = buildAppKey(app)
            if (seenKeys.add(key)) {
                fullList.add(app)
            }
        }
        val now = System.currentTimeMillis()
        webApps.forEach { entry ->
            val resolveInfo = webAppManager.createResolveInfo(entry)
            resolveInfo.preferredOrder = currentUserSerial

            val uniqueKey = buildAppKey(resolveInfo)

            if (seenKeys.add(uniqueKey)) {
                cacheManager?.updateMetadataCache(
                    uniqueKey,
                    AppMetadata(
                        packageName = resolveInfo.activityInfo.packageName,
                        activityName = resolveInfo.activityInfo.name,
                        label = entry.name,
                        lastUpdated = now
                    )
                )
                fullList.add(resolveInfo)
            }
        }
        return fullList
    }

    private fun buildAppKey(resolveInfo: ResolveInfo): String {
        return "${resolveInfo.activityInfo.packageName}|${resolveInfo.activityInfo.name}|${resolveInfo.preferredOrder}"
    }

    fun cleanup() {
        // Clear callbacks to prevent memory leaks
        onAppListUpdated = null
        onAdapterNeedsUpdate = null
        
        // Clear cached data
        cachedUnsortedList = null
        cacheManager?.clearCache()
    }
}
