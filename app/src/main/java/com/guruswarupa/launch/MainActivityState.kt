package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guruswarupa.launch.ai.llm.OnDeviceAssistant
import com.guruswarupa.launch.ai.prediction.LaunchEventStore
import com.guruswarupa.launch.ai.prediction.SuggestionEngine
import com.guruswarupa.launch.di.BackgroundExecutor
import com.guruswarupa.launch.di.ResourceLoader
import com.guruswarupa.launch.core.*
import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.RssFeedPage
import com.guruswarupa.launch.widgets.*
import com.guruswarupa.launch.utils.*
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun MainActivity.observeViewModelState() {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                if (views.isSearchBoxInitialized()) {
                    val currentQuery = views.searchBox.text?.toString().orEmpty()
                    if (currentQuery != state.searchQuery) {
                        views.searchBox.setText(state.searchQuery)
                        views.searchBox.setSelection(state.searchQuery.length)
                    }
                }

                val targetPage = state.currentPage
                if (targetPage != null && isScreenPagerManagerInitialized() && screenPagerManager.getCurrentPage() != targetPage) {
                    when (targetPage) {
                        ScreenPagerManager.Page.RSS -> screenPagerManager.openRssPage(animated = false)
                        ScreenPagerManager.Page.WIDGETS -> screenPagerManager.openWidgetsPage(animated = false)
                        ScreenPagerManager.Page.CENTER -> screenPagerManager.openCenterPage(animated = false)
                        ScreenPagerManager.Page.WALLPAPER -> screenPagerManager.openWallpaperPage(animated = false)
                        ScreenPagerManager.Page.AI_CHAT -> screenPagerManager.openAiChatPage(animated = false)
                    }
                }
            }
        }
    }

    if (isScreenPagerManagerInitialized()) {
        screenPagerManager.setOnPageChanged { page ->
            viewModel.updateCurrentPage(page)
            val isFullyTransparentPage = page == ScreenPagerManager.Page.WALLPAPER ||
                    page == ScreenPagerManager.Page.WIDGETS ||
                    page == ScreenPagerManager.Page.RSS ||
                    page == ScreenPagerManager.Page.AI_CHAT ||
                    page == ScreenPagerManager.Page.CENTER
            systemBarManager.updateSystemBars(isFullyTransparentPage)
            if (page == ScreenPagerManager.Page.WIDGETS) {
                initializeDeferredWidgets()
            }
            if (isAiChatPageInitialized()) {
                if (page == ScreenPagerManager.Page.AI_CHAT) {
                    aiChatPage.onPageShown()
                } else {
                    aiChatPage.onPageHidden()
                }
            }
            if (isWallpaperMediaControllerInitialized()) {
                if (page == ScreenPagerManager.Page.WALLPAPER) {
                    wallpaperMediaController.onPageShown()
                } else {
                    wallpaperMediaController.onPageHidden()
                }
            }
            if (isStockTopWidgetMediaControllerInitialized()) {
                if (page == ScreenPagerManager.Page.CENTER && com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
                    stockTopWidgetMediaController.onPageShown()
                } else {
                    stockTopWidgetMediaController.onPageHidden()
                }
            }
        }
        if (screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.WIDGETS) {
            initializeDeferredWidgets()
        }
        if (isWallpaperMediaControllerInitialized()) {
            if (screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.WALLPAPER) {
                wallpaperMediaController.onPageShown()
            } else {
                wallpaperMediaController.onPageHidden()
            }
        }
        if (isStockTopWidgetMediaControllerInitialized()) {
            if (screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.CENTER && com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
                stockTopWidgetMediaController.onPageShown()
            } else {
                stockTopWidgetMediaController.onPageHidden()
            }
        }
        val currentPage = screenPagerManager.getCurrentPage()
        val isCurrentFullyTransparent = currentPage == ScreenPagerManager.Page.WALLPAPER ||
                currentPage == ScreenPagerManager.Page.WIDGETS ||
                currentPage == ScreenPagerManager.Page.RSS ||
                currentPage == ScreenPagerManager.Page.AI_CHAT ||
                currentPage == ScreenPagerManager.Page.CENTER
        systemBarManager.updateSystemBars(isCurrentFullyTransparent)
    }
}

internal fun MainActivity.initializeLifecycleManager() {
    lifecycleManager = LifecycleManager(
        activity = this,
        handler = handler,
        sharedPreferences = sharedPreferences,
        dependencies = LifecycleManager.Dependencies(
            systemBarManager = systemBarManager,
            appLockManager = appLockManager,
            wallpaperManagerHelper = wallpaperManagerHelper,
            gestureHandler = gestureHandler,
            appDockManager = appDockManager,
            adapter = adapter,
            appList = appList,
            appListLoader = appListLoader,
            widgetManager = widgetManager,
            usageStatsManager = usageStatsManager,
            timeDateManager = timeDateManager,
            weeklyUsageGraph = views.weeklyUsageGraph,
            usageStatsDisplayManager = usageStatsDisplayManager,
            todoManager = null,
            backgroundExecutor = backgroundExecutor,
            widgetLifecycleCoordinator = widgetLifecycleCoordinator,
            widgetThemeManager = widgetThemeManager,
            views = views,
            broadcastReceiverManager = null,
            shareManager = shareManager,
            serviceManager = serviceManager,
            appTimerManager = appTimerManager,
            hiddenAppManager = hiddenAppManager
        )
    )

    lifecycleManager.onUsageUpdate = { usageStatsRefreshManager.updateUsageInBackground() }
    lifecycleManager.onFocusModeApply = { isFocusMode -> applyFocusMode(isFocusMode) }
    lifecycleManager.onLoadApps = { forceRefresh -> appListLoader.loadApps(forceRefresh) }
}

internal fun MainActivity.syncOptionalPagesAfterSettingsUpdate() {
    if (!isScreenPagerManagerInitialized()) return

    if (sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true) &&
        screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.WIDGETS
    ) {
        handler.post {
            if (!isFinishing && !isDestroyed) {
                initializeDeferredWidgets()
            }
        }
    }

    if (sharedPreferences.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)) {
        handler.post {
            if (!isFinishing && !isDestroyed) {
                findViewById<View?>(R.id.rss_feed_page)?.let { rssPageView ->
                    RssFeedPage(this, rssPageView).setup()
                }
            }
        }
    }

    if (isAiChatPageInitialized()) {
        handler.post {
            if (!isFinishing && !isDestroyed) {
                aiChatPage.updateTypography()
            }
        }
    }
}

internal fun MainActivity.handlePackageChange(packageName: String?, isRemoved: Boolean) {
    if (packageName == null) return
    backgroundExecutor.execute {
        cacheManager.removeMetadata(packageName)

        if (isAdapterInitialized()) {
            handler.post {
                if (!isFinishing && !isDestroyed) {
                    adapter.invalidateIconForPackage(packageName)
                }
            }
        } else {
            cacheManager.removeIconsForPackage(packageName)
        }

        appListLoader.clearCache()
        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
    }
}
