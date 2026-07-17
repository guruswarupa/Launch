package com.guruswarupa.launch.core

import android.os.Handler
import android.os.SystemClock
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.widgets.DeviceInfoWidget
import com.guruswarupa.launch.widgets.NetworkStatsWidget
import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView
import com.guruswarupa.launch.widgets.WidgetThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch





class LifecycleManager(
    private val activity: FragmentActivity,
    private val handler: Handler,
    private val sharedPreferences: android.content.SharedPreferences,
    dependencies: Dependencies = Dependencies()
) {
    companion object {
        // Constants moved to Constants.Timeouts for centralized management
    }

    data class Dependencies(
        val systemBarManager: SystemBarManager? = null,
        val appLockManager: AppLockManager? = null,
        val wallpaperManagerHelper: WallpaperManagerHelper? = null,
        val gestureHandler: GestureHandler? = null,
        val appDockManager: AppDockManager? = null,
        val adapter: AppAdapter? = null,
        val appList: MutableList<android.content.pm.ResolveInfo>? = null,
        val appListLoader: AppListLoader? = null,
        val widgetManager: WidgetManager? = null,
        val deviceInfoWidget: DeviceInfoWidget? = null,
        val networkStatsWidget: NetworkStatsWidget? = null,
        val usageStatsManager: AppUsageStatsManager? = null,
        val timeDateManager: TimeDateManager? = null,
        val weeklyUsageGraph: WeeklyUsageGraphView? = null,
        val usageStatsDisplayManager: UsageStatsDisplayManager? = null,
        val todoManager: TodoManager? = null,
        val backgroundExecutor: java.util.concurrent.ExecutorService? = null,
        val widgetLifecycleCoordinator: WidgetLifecycleCoordinator? = null,
        val widgetThemeManager: WidgetThemeManager? = null,
        val views: MainActivityViews? = null,
        val broadcastReceiverManager: BroadcastReceiverManager? = null,
        val shareManager: ShareManager? = null,
        val serviceManager: ServiceManager? = null,
        val appTimerManager: AppTimerManager? = null,
        val hiddenAppManager: HiddenAppManager? = null,
    )

    private var dependencies = dependencies

    var onResumeCallbacks: MutableList<() -> Unit> = java.util.Collections.synchronizedList(mutableListOf())
    var onPauseCallbacks: MutableList<() -> Unit> = java.util.Collections.synchronizedList(mutableListOf())
    var onBatteryUpdate: (() -> Unit)? = null
    var onUsageUpdate: (() -> Unit)? = null
    var onFocusModeApply: ((Boolean) -> Unit)? = null
    var onLoadApps: ((Boolean) -> Unit)? = null

    fun updateDependencies(transform: Dependencies.() -> Dependencies) {
        synchronized(this) {
            dependencies = dependencies.transform()
        }
    }

    private var isBlockingBackGesture = false
    @Volatile
    private var lastAppListRefreshAt = 0L
    @Volatile
    private var lastUsageRefreshAt = 0L

    fun onResume() {
        val deps = dependencies
        val now = SystemClock.elapsedRealtime()

        deps.systemBarManager?.makeSystemBarsTransparent()


        deps.appLockManager?.clearAuthTimeout()

        deps.wallpaperManagerHelper?.setWallpaperBackground()


        if (!isBlockingBackGesture) {
            deps.gestureHandler?.updateGestureExclusion()
        }


        deps.appDockManager?.let {
            onFocusModeApply?.invoke(it.getCurrentMode())
            it.refreshWorkspaceToggle()
        }


        deps.widgetThemeManager?.let { themeManager ->
            themeManager.checkAndUpdateThemeIfNeeded(
                todoManager = deps.todoManager,
                appDockManager = deps.appDockManager,
                searchBox = deps.views?.let { if (it.isSearchBoxInitialized()) it.searchBox else null },
                searchContainer = deps.views?.let { if (it.isSearchContainerInitialized()) it.searchContainer else null },
                voiceSearchButton = deps.views?.let { if (it.isVoiceSearchButtonInitialized()) it.voiceSearchButton else null },
                searchTypeButton = deps.views?.let { if (it.isSearchTypeButtonInitialized()) it.searchTypeButton else null }
            )
        }


        deps.views?.let {
            if (it.isSearchBoxInitialized()) {
                it.searchBox.clearFocus()
            }
        }


        deps.widgetLifecycleCoordinator?.onResume()


        val shouldForceWorkProfileRefresh = deps.appDockManager?.isWorkProfileModeEnabled() == true
        val shouldRefreshAppList =
            shouldForceWorkProfileRefresh ||
                (deps.appList?.isEmpty() == true) ||
                now - lastAppListRefreshAt >= Constants.Timeouts.APP_LIST_REFRESH_INTERVAL_MS
        if (shouldRefreshAppList) {
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    deps.hiddenAppManager?.forceRefresh()
                    deps.appListLoader?.loadApps(forceRefresh = shouldForceWorkProfileRefresh) ?: run {
                        onLoadApps?.invoke(shouldForceWorkProfileRefresh)
                    }
                    lastAppListRefreshAt = SystemClock.elapsedRealtime()
                }
            }, 250)
        }


        deps.widgetManager?.onStart()
        deps.widgetManager?.reloadWidgetsIfPending()
        deps.deviceInfoWidget?.onResume()
        deps.networkStatsWidget?.onResume()


        deps.usageStatsDisplayManager?.refreshPermissionButton()


        val isPowerSaverMode = sharedPreferences.getBoolean(Constants.Prefs.POWER_SAVER_MODE, false)
        deps.timeDateManager?.startUpdates(isPowerSaverMode)


        val shouldRefreshUsage = now - lastUsageRefreshAt >= Constants.Timeouts.USAGE_REFRESH_INTERVAL_MS
        if (shouldRefreshUsage) {
            deps.usageStatsManager?.invalidateCache()


            activity.lifecycleScope.launch {
                delay(50)
                onBatteryUpdate?.invoke()
                onUsageUpdate?.invoke()

                deps.backgroundExecutor?.execute {
                    deps.usageStatsDisplayManager?.checkDateChangeAndRefreshUsage()
                }

                delay(300)
                deps.weeklyUsageGraph?.let {
                    if (it.isVisible) {
                        deps.usageStatsDisplayManager?.loadWeeklyUsageData()
                    }
                }
            }
            lastUsageRefreshAt = now
        }

        deps.todoManager?.rescheduleTodoAlarms()

        onResumeCallbacks.forEach { it.invoke() }
    }

    fun onPause() {
        val deps = dependencies

        deps.timeDateManager?.stopUpdates()


        deps.todoManager?.saveTodoItems()


        deps.widgetManager?.onStop()
        deps.deviceInfoWidget?.onPause()
        deps.networkStatsWidget?.onPause()


        deps.widgetLifecycleCoordinator?.onPause()

        onPauseCallbacks.forEach { it.invoke() }
    }

    fun onDestroy() {
        val deps = dependencies

        deps.timeDateManager?.destroy()
        deps.wallpaperManagerHelper?.cleanup()
        deps.broadcastReceiverManager?.unregisterReceivers()
        deps.shareManager?.cleanup()
        deps.widgetManager?.onDestroy()
        deps.widgetLifecycleCoordinator?.onDestroy()
        deps.serviceManager?.stopAllServices()
        deps.appTimerManager?.cleanup()
        deps.usageStatsManager?.cleanup()
        deps.adapter?.cleanup()
        deps.appListLoader?.cleanup()

        cleanup()
    }




    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}
