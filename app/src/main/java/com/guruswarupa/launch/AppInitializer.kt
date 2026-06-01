package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruswarupa.launch.core.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.RssFeedPage
import com.guruswarupa.launch.ui.activities.AppDataDisclosureActivity
import com.guruswarupa.launch.widgets.*

class AppInitializer(private val activity: MainActivity) {

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "SourceLockedOrientationActivity")
    fun initialize() {
        with(activity) {
            setContentView(R.layout.activity_main)

            widgetConfigurationManager = WidgetConfigurationManager(activity, sharedPreferences)
            widgetVisibilityManager = WidgetVisibilityManager(activity, widgetConfigurationManager)

            usageStatsCacheManager = UsageStatsCacheManager(sharedPreferences, backgroundExecutor)
            contactManager = ContactManager(activity, contentResolver, backgroundExecutor)
            rssFeedManager = RssFeedManager(activity, sharedPreferences, backgroundExecutor)

            cacheManager.loadAppMetadataFromCacheAsync {
                updateAppSearchManager()
            }

            if (!sharedPreferences.getBoolean(Constants.Prefs.APP_DATA_CONSENT_GIVEN, false)) {
                startActivity(Intent(activity, AppDataDisclosureActivity::class.java))
                finish()
                return@with
            }
            initializeViews()

            val requestPermissionsAfterDisclosure = activity.intent.getBooleanExtra("request_permissions_after_disclosure", false)
            if (requestPermissionsAfterDisclosure) {
                activity.handler.post {
                    activity.startFeatureTutorialAndRequestPermissions()
                }
            }

            initializeTimeDateAndWeather()

            appDockManager = AppDockManager(activity, sharedPreferences, views.appDock)

            widgetThemeManager = WidgetThemeManager(activity) { resources.configuration.uiMode }

            settingsChangeCoordinator = SettingsChangeCoordinator(
                activity = activity,
                adapterProvider = { activity.adapter },
                appDockManagerProvider = { activity.appDockManager },
                widgetSetupManagerProvider = { activity.widgetSetupManager },
                widgetThemeManagerProvider = { activity.widgetThemeManager }
            )

            applyThemeBasedWidgetBackgrounds()

            settingsChangeCoordinator.applyBackgroundTranslucency()

            appList = mutableListOf()
            fullAppList = mutableListOf()

            contactActionHandler = ContactActionHandler(
                activity, packageManager, contentResolver, views.searchBox, appList
            ) { handler ->
                voiceCommandHandler = handler
                activity.activityResultHandler.setVoiceCommandHandler(handler)

                this@AppInitializer.updateRegistryDependencies()
            }

            appListManager.attach(appDockManager)

            appListLoader = AppListLoader(
                activity, packageManager, appListManager, appDockManager,
                cacheManager, webAppManager, backgroundExecutor, handler, views.recyclerView, views.searchBox, views.voiceSearchButton, sharedPreferences
            )


            val viewPreference = sharedPreferences.getString(
                Constants.Prefs.VIEW_PREFERENCE,
                Constants.Prefs.VIEW_PREFERENCE_LIST
            )
            val isGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID
            adapter = AppAdapter(activity, appList, views.searchBox, isGridMode, activity, sharedPreferences)


            if (isGridMode) {
                val columns = activity.getPreferredGridColumns()
                val gridLayoutManager = GridLayoutManager(activity, columns)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR || viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                            columns
                        } else {
                            1
                        }
                    }
                }
                views.recyclerView.layoutManager = gridLayoutManager
            } else {
                views.recyclerView.layoutManager = LinearLayoutManager(activity)
            }

            views.recyclerView.adapter = adapter


            views.recyclerView.setHasFixedSize(true)
            views.recyclerView.itemAnimator = null
            views.recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)

            views.recyclerView.visibility = View.VISIBLE
            updateFastScrollerVisibility()


            views.fastScroller.onScrollToBottom = {
                activity.showAllAppsFromFavorites()
            }


            views.fastScroller.onScrollToTop = {
                activity.showFavoritesFromAllApps()
            }

            appListUIUpdater = AppListUIUpdater(
                activity, views.recyclerView, activity.adapter,
                appList, fullAppList, appListLoader, appListManager,
                backgroundExecutor, views.searchBox
            )
            appListUIUpdater.setupCallbacks()
            appListUIUpdater.setAdapter(adapter)

            usageStatsDisplayManager = UsageStatsDisplayManager(activity, usageStatsManager, views.weeklyUsageGraph, adapter, views.recyclerView, handler)

            if (!appDockManager.getCurrentMode()) {
                refreshAppsForFocusMode()
            }

            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, activity.adapter)


            views.recyclerView.doOnLayout {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    updateAppSearchManager()
                }
            }

            val drawerContentLayout = findViewById<LinearLayout>(R.id.drawer_content_layout)
            widgetManager = WidgetManager(activity, drawerContentLayout)

            val mainContent = findViewById<FrameLayout>(R.id.main_content)
            gestureHandler = GestureHandler(activity, views.drawerLayout, mainContent)
            screenPagerManager = ScreenPagerManager(activity, views.drawerLayout)

            findViewById<View?>(R.id.rss_feed_page)?.let { rssPageView ->
                RssFeedPage(activity, rssPageView).setup()
            }

            drawerManager = DrawerManager(
                activity, screenPagerManager, gestureHandler, usageStatsDisplayManager, activityInitializer,
                themeCheckCallback = { checkAndUpdateThemeIfNeeded() }
            )
            drawerManager.setup()
            navigationManager = drawerManager.navigationManager

            findViewById<ImageButton?>(R.id.widget_config_button)?.setOnClickListener {
                showWidgetConfigurationDialog()
            }

            findViewById<LinearLayout?>(R.id.widget_settings_header)?.setOnClickListener {
                showWidgetConfigurationDialog()
            }

            findViewById<TextView?>(R.id.widget_settings_text)?.setOnClickListener {
                showWidgetConfigurationDialog()
            }

            val drawerWallpaper = findViewById<ImageView>(R.id.drawer_wallpaper_background)
            val rssWallpaper = findViewById<ImageView>(R.id.rss_wallpaper_background)
            wallpaperManagerHelper = WallpaperManagerHelper(activity, views.wallpaperBackground, drawerWallpaper, backgroundExecutor, rssWallpaper)
            wallpaperManagerHelper.setWallpaperBackground()

            activity.refreshRightDrawerWallpaper()

            voiceSearchManager = VoiceSearchManager(activity, packageManager)

            views.voiceSearchButton.setOnClickListener {
                voiceSearchManager.startVoiceSearchWithLauncher(resultRegistry.voiceSearchLauncher)
            }

            views.voiceSearchButton.setOnLongClickListener {
                voiceSearchManager.triggerSystemAssistant()
                true
            }

            usageStatsRefreshManager = UsageStatsRefreshManager(
                activity, backgroundExecutor, usageStatsManager
            )

            activityResultHandler = ActivityResultHandler(
                activity, views.searchBox, voiceCommandHandler, shareManager,
                widgetManager, wallpaperManagerHelper,
                onBlockBackGestures = { navigationManager.blockBackGesturesTemporarily() }
            )

            focusModeApplier = FocusModeApplier(
                activity, backgroundExecutor, appListManager, appDockManager,
                views.searchContainer, activity.adapter, fullAppList, appList,
                onUpdateAppSearchManager = { updateAppSearchManager() },
                onUpdateFastScrollerVisibility = { updateFastScrollerVisibility() },
                showOnlyFavoritesInitially = { activity.showOnlyFavoritesInitially }
            )

            serviceManager = ServiceManager(activity, sharedPreferences)

            AppUsageMonitor.syncMonitoring(activity)

            serviceManager.updateShakeDetectionService()
            serviceManager.updateWalkDetectionService()
            serviceManager.updateScreenDimmerService()
            serviceManager.updateFlipToDndService()
            serviceManager.updateBackTapService()

            initializeLifecycleManager()
            initializeBroadcastReceivers()
            lifecycleManager.updateDependencies {
                copy(broadcastReceiverManager = activity.broadcastReceiverManager)
            }

            this@AppInitializer.updateRegistryDependencies()

            window.decorView.post {
                systemBarManager.makeSystemBarsTransparent()
            }
        }
    }

    fun updateRegistryDependencies() {
        with(activity) {
            val deps = MainActivityResultRegistry.DependencyContainer(
                widgetManager = activity.widgetManager,
                widgetVisibilityManager = activity.widgetVisibilityManager,
                widgetConfigurationManager = activity.widgetConfigurationManager,
                voiceCommandHandler = voiceCommandHandler,
                activityResultHandler = activity.activityResultHandler,
                packageManager = packageManager,
                contentResolver = contentResolver,
                searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
                appList = appList,
                widgetLifecycleCoordinator = widgetLifecycleCoordinator
            )
            resultRegistry.setDependencies(deps)
        }
    }
}
