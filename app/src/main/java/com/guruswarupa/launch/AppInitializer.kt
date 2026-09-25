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

            setupCoreManagers()

            if (!sharedPreferences.getBoolean(Constants.Prefs.APP_DATA_CONSENT_GIVEN, false)) {
                startActivity(Intent(activity, AppDataDisclosureActivity::class.java))
                finish()
                return@with
            }

            initializeViews()
            setupNavigationAndGestures()
            setupAppList()
            setupWidgets()
            setupSystemServices()
            setupLifeCycleAndReceivers()

            window.decorView.post {
                systemBarManager.makeSystemBarsTransparent(isFullyTransparent = true)
            }
        }
    }

    private fun MainActivity.setupCoreManagers() {
        DownloadableFontManager.preloadDownloadedFonts(activity)
        widgetConfigurationManager = WidgetConfigurationManager(activity, sharedPreferences)
        widgetVisibilityManager = WidgetVisibilityManager(activity, widgetConfigurationManager)

        contactManager = ContactManager(activity, contentResolver, backgroundExecutor)
        rssFeedManager = RssFeedManager(activity, sharedPreferences, backgroundExecutor)

        cacheManager.loadAppMetadataFromCacheAsync {
            updateAppSearchManager()
        }
    }

    private fun MainActivity.setupNavigationAndGestures() {
        val mainContent = findViewById<FrameLayout>(R.id.main_content)
        gestureHandler = GestureHandler(activity, views.drawerLayout, mainContent)
        screenPagerManager = ScreenPagerManager(activity, views.drawerLayout)

        val requestPermissionsAfterDisclosure = intent.getBooleanExtra("request_permissions_after_disclosure", false)
        if (requestPermissionsAfterDisclosure) {
            handler.post {
                requestInitialPermissions()
            }
        }

        initializeTimeDateAndWeather()
    }

    private fun MainActivity.setupAppList() {
        appDockManager = AppDockManager(activity, sharedPreferences, views.appDock)
        if (com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {

            appDockManager.turnOffWorkspace()
            if (views.isSearchContainerInitialized()) {
                views.searchContainer.visibility = android.view.View.GONE
            }
        }
        widgetThemeManager = WidgetThemeManager(activity)

        settingsChangeCoordinator = SettingsChangeCoordinator(
            activity = activity,
            adapterProvider = { activity.adapter },
            appDockManagerProvider = { activity.appDockManager },
            widgetThemeManagerProvider = { activity.widgetThemeManager },
        )

        applyThemeBasedWidgetBackgrounds()
        settingsChangeCoordinator.applyBackgroundTranslucency()

        appList.clear()
        fullAppList.clear()

        contactActionHandler = ContactActionHandler(
            activity, packageManager, contentResolver, views.searchBox, appList
        ) { handler ->
            voiceCommandHandler = handler
            activityResultHandler.setVoiceCommandHandler(handler)
            this@AppInitializer.updateRegistryDependencies()
        }

        appListManager.attach(appDockManager)

        appListLoader = AppListLoader(
            activity, packageManager, appListManager, appDockManager,
            cacheManager, webAppManager, backgroundExecutor, resourceLoader, handler, views.recyclerView, views.searchBox, views.voiceSearchButton, sharedPreferences
        )

        val viewPreference = sharedPreferences.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        )
        val isGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID
        adapter = AppAdapter(activity, appList, views.searchBox, isGridMode, activity, sharedPreferences)

        if (isGridMode) {
            setupGridLayout()
        } else {
            views.recyclerView.layoutManager = LinearLayoutManager(activity)
        }

        views.recyclerView.adapter = adapter
        views.recyclerView.setHasFixedSize(true)
        views.recyclerView.itemAnimator = null
        views.recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        views.recyclerView.visibility = View.VISIBLE
        updateFastScrollerVisibility()

        views.fastScroller.onScrollToBottom = { showAllAppsFromFavorites() }
        views.fastScroller.onScrollToTop = { showFavoritesFromAllApps() }

        appListUIUpdater = AppListUIUpdater(
            activity, views.recyclerView, adapter,
            appList, fullAppList, appListLoader, appListManager,
            backgroundExecutor, views.searchBox
        )
        appListUIUpdater.setupCallbacks()
        appListUIUpdater.setAdapter(adapter)

        setupStockDrawer()

        usageStatsDisplayManager = UsageStatsDisplayManager(activity, usageStatsManager, views.weeklyUsageGraph, adapter, views.recyclerView)

        if (!appDockManager.getCurrentMode()) {
            refreshAppsForFocusMode()
        } else {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
        }

        views.recyclerView.doOnLayout {
            if (!isFinishing && !isDestroyed) {
                updateAppSearchManager()
            }
        }
    }

    private fun MainActivity.setupStockDrawer() {
        val drawerRoot = findViewById<View>(R.id.stock_drawer) ?: return
        val drawerRecyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.stock_drawer_recycler_view)
        val drawerSearchBox = findViewById<android.widget.AutoCompleteTextView>(R.id.stock_drawer_search_box)
        val drawerEmptyState = findViewById<View>(R.id.stock_drawer_empty_state)
        val drawerSearchContainer = findViewById<View>(R.id.stock_drawer_search_container)
        val drawerAddToHomeZone = findViewById<View>(R.id.stock_drawer_add_to_home_zone)

        stockDrawerManager = StockDrawerManager(
            activity = activity,
            drawerRoot = drawerRoot,
            recyclerView = drawerRecyclerView,
            searchBox = drawerSearchBox,
            emptyState = drawerEmptyState,
            searchContainer = drawerSearchContainer,
            addToHomeZone = drawerAddToHomeZone,
            appListManager = appListManager,
            appDockManager = appDockManager,
            appOrderManager = appOrderManager,
            folderManager = folderManager,
            favoriteAppManager = favoriteAppManager,
            sharedPreferences = sharedPreferences,
            screenPagerManager = screenPagerManager
        )
        stockDrawerManager.setup()
        stockDrawerManager.attachOpenGesture(views.recyclerView)
        stockDrawerManager.attachOpenGestureToView(views.appListEmptyState)
        val homeRemoveZone = findViewById<View>(R.id.stock_home_remove_zone)
        val homeHotseat = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.stock_home_hotseat)
        stockDrawerManager.attachHomeReorder(views.recyclerView, adapter, homeHotseat, homeRemoveZone)
        gestureHandler.onSwipeUpFromHome = {
            stockDrawerManager.show()
            true
        }

        val previousCallback = appListLoader.onAppListUpdated
        appListLoader.onAppListUpdated = { sortedList, filteredList, isFinal ->
            previousCallback?.invoke(sortedList, filteredList, isFinal)
            stockDrawerManager.onFullAppListUpdated(filteredList)
        }
    }

    private fun MainActivity.setupGridLayout() {
        val columns = getPreferredGridColumns()
        val gridLayoutManager = GridLayoutManager(activity, columns)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = adapter.getItemViewType(position)
                return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR || (viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL)) {
                    columns
                } else {
                    1
                }
            }
        }
        views.recyclerView.layoutManager = gridLayoutManager
    }

    private fun MainActivity.setupWidgets() {
        val drawerContentLayout = findViewById<LinearLayout>(R.id.drawer_content_layout)
        widgetManager = WidgetManager(activity, drawerContentLayout, listenOnlyWhileStarted = true)
        widgetManager.onWidgetsRefreshed = {

            if (deferredWidgetsInitialized) {
                widgetVisibilityManager.update(
                    if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
                    if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
                )
            }
        }

        findViewById<View?>(R.id.rss_feed_page)?.let { rssPageView ->
            RssFeedPage(activity, rssPageView).setup()
        }

        findViewById<View?>(R.id.ai_chat_page)?.let { aiChatPageView ->
            aiChatPage = com.guruswarupa.launch.ui.AiChatPage(activity, aiChatPageView)
            aiChatPage.setup()
        }

        mediaSessionMonitor = MediaSessionMonitor(activity)
        findViewById<View?>(R.id.wallpaper_drawer)?.let { wallpaperDrawerView ->
            wallpaperMediaController = com.guruswarupa.launch.ui.WallpaperMediaController(activity, wallpaperDrawerView)
            wallpaperMediaController.setup()
        }
        findViewById<View?>(R.id.stock_top_widget_media_wrapper)?.let { stockMediaWrapperView ->
            stockTopWidgetMediaController = com.guruswarupa.launch.ui.WallpaperMediaController(activity, stockMediaWrapperView)
            stockTopWidgetMediaController.setup()
        }

        drawerManager = DrawerManager(
            activity, screenPagerManager, gestureHandler, usageStatsDisplayManager, activityInitializer,
            themeCheckCallback = { checkAndUpdateThemeIfNeeded() },
            stockDrawerShownProvider = { isStockDrawerManagerInitialized() && stockDrawerManager.isShown() },
            hideStockDrawer = { stockDrawerManager.hide() }
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
        val aiChatWallpaper = findViewById<ImageView>(R.id.ai_chat_wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(activity, views.wallpaperBackground, drawerWallpaper, backgroundExecutor, rssWallpaper, aiChatWallpaper)
        wallpaperManagerHelper.setWallpaperBackground()

        refreshRightDrawerWallpaper()
    }

    private fun MainActivity.setupSystemServices() {
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
            activity, backgroundExecutor, appListManager, appDockManager, sharedPreferences,
            views.searchContainer, adapter, fullAppList, appList,
            onUpdateAppSearchManager = { updateAppSearchManager() },
            onUpdateFastScrollerVisibility = { updateFastScrollerVisibility() },
            showOnlyFavoritesInitially = { showOnlyFavoritesInitially }
        )

        serviceManager = ServiceManager(activity, sharedPreferences)

        AppUsageMonitor.syncMonitoring(activity)

        serviceManager.updateShakeDetectionService()
        serviceManager.updateWalkDetectionService()
        serviceManager.updateScreenDimmerService()
        serviceManager.updateFlipToDndService()
        serviceManager.updateBackTapService()
    }

    private fun MainActivity.setupLifeCycleAndReceivers() {
        initializeLifecycleManager()
        initializeBroadcastReceivers()
        lifecycleManager.updateDependencies {
            copy(broadcastReceiverManager = activity.broadcastReceiverManager)
        }

        this@AppInitializer.updateRegistryDependencies()
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
