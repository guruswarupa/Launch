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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
    }

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    lateinit var secureStorageManager: SecureStorageManager

    @Inject
    @BackgroundExecutor
    lateinit var backgroundExecutor: ExecutorService

    @Inject
    @ResourceLoader
    lateinit var resourceLoader: ExecutorService

    @Inject
    lateinit var cacheManager: CacheManager

    @Inject
    lateinit var appListManager: AppListManager

    @Inject
    lateinit var appSearchManager: AppSearchManager

    @Inject
    lateinit var favoriteAppManager: FavoriteAppManager

    @Inject
    lateinit var hiddenAppManager: HiddenAppManager

    @Inject
    lateinit var webAppManager: WebAppManager

    @Inject
    lateinit var shareManager: ShareManager

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var systemBarManager: SystemBarManager

    @Inject
    lateinit var activityInitializer: ActivityInitializer

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var appTimerManager: AppTimerManager

    @Inject
    lateinit var appLauncher: AppLauncher

    @Inject
    lateinit var widgetLifecycleCoordinator: WidgetLifecycleCoordinator

    @Inject
    lateinit var resultRegistry: MainActivityResultRegistry

    @Inject
    lateinit var usageStatsManager: AppUsageStatsManager

    @Inject
    lateinit var weatherManager: WeatherManager

    internal class MainActivityHandler(activity: MainActivity) : Handler(Looper.getMainLooper()) {
        private val weakActivity = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            weakActivity.get() ?: return
            // Specific message handling can be added here if needed
        }
    }

    internal val handler = MainActivityHandler(this)

    lateinit var adapter: AppAdapter

    lateinit var searchTypeMenuManager: SearchTypeMenuManager
    lateinit var widgetThemeManager: WidgetThemeManager

    lateinit var gestureHandler: GestureHandler

    lateinit var broadcastReceiverManager: BroadcastReceiverManager

    lateinit var wallpaperManagerHelper: WallpaperManagerHelper

    lateinit var appListLoader: AppListLoader

    lateinit var contactManager: ContactManager

    lateinit var lifecycleManager: LifecycleManager

    lateinit var appDockManager: AppDockManager

    lateinit var timeDateManager: TimeDateManager

    lateinit var todoManager: TodoManager

    lateinit var todoAlarmManager: TodoAlarmManager

    lateinit var financeWidgetManager: FinanceWidgetManager

    lateinit var usageStatsDisplayManager: UsageStatsDisplayManager
    
    lateinit var rssFeedManager: RssFeedManager

    lateinit var widgetSetupManager: WidgetSetupManager

    lateinit var widgetManager: WidgetManager

    lateinit var voiceSearchManager: VoiceSearchManager

    lateinit var usageStatsRefreshManager: UsageStatsRefreshManager

    lateinit var activityResultHandler: ActivityResultHandler

    lateinit var navigationManager: NavigationManager

    lateinit var focusModeApplier: FocusModeApplier

    lateinit var widgetConfigurationManager: WidgetConfigurationManager

    lateinit var widgetVisibilityManager: WidgetVisibilityManager

    lateinit var serviceManager: ServiceManager

    lateinit var appListUIUpdater: AppListUIUpdater

    lateinit var drawerManager: DrawerManager

    lateinit var screenPagerManager: ScreenPagerManager

    lateinit var contactActionHandler: ContactActionHandler

    lateinit var settingsChangeCoordinator: SettingsChangeCoordinator

    lateinit var donationPromptManager: DonationPromptManager

    lateinit var reviewPromptManager: ReviewPromptManager

    var voiceCommandHandler: VoiceCommandHandler? = null
    var widgetPrewarmScheduled = false
    val appList: MutableList<ResolveInfo> = java.util.Collections.synchronizedList(mutableListOf())
    val fullAppList: MutableList<ResolveInfo> = java.util.Collections.synchronizedList(mutableListOf())


    var showOnlyFavoritesInitially = true
    var pendingScrollToTop = false


    private var lastToggleTime = 0L
    private val TOGGLE_DEBOUNCE_MS = 500L

    val views: MainActivityViews get() = activityInitializer.views
    private val viewModel: MainActivityViewModel by viewModels()

    internal val deferredWidgetsInitialized: Boolean
        get() = viewModel.uiState.value.deferredWidgetsInitialized

    fun isTablet(): Boolean {
        return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun applyThemeBasedWidgetBackgrounds() {
        settingsChangeCoordinator.applyThemeBasedWidgetBackgrounds()
    }

    fun showAllAppsFromFavorites() {
        if (!showOnlyFavoritesInitially) return


        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToggleTime < TOGGLE_DEBOUNCE_MS) return

        lastToggleTime = currentTime
        showOnlyFavoritesInitially = false
        pendingScrollToTop = true


        appListLoader.loadApps(forceRefresh = false)
    }

    fun showFavoritesFromAllApps() {
        if (showOnlyFavoritesInitially) return


        val favorites = favoriteAppManager.getFavoriteApps()
        if (favorites.isEmpty()) {

            return
        }


        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToggleTime < TOGGLE_DEBOUNCE_MS) return

        lastToggleTime = currentTime
        showOnlyFavoritesInitially = true
        pendingScrollToTop = true


        appListLoader.loadApps(forceRefresh = false)
    }

    internal fun checkAndUpdateThemeIfNeeded() {
        widgetThemeManager.checkAndUpdateThemeIfNeeded(
            todoManager = todoManager,
            appDockManager = appDockManager,
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            searchContainer = if (views.isSearchContainerInitialized()) views.searchContainer else null,
            voiceSearchButton = if (views.isVoiceSearchButtonInitialized()) views.voiceSearchButton else null,
            searchTypeButton = if (views.isSearchTypeButtonInitialized()) views.searchTypeButton else null
        )
    }

    internal fun initializeBroadcastReceivers() {
        broadcastReceiverManager = BroadcastReceiverManager(
            this,
            sharedPreferences,
            onSettingsUpdated = { handleSettingsUpdate() },
            onPackageChanged = { packageName, isRemoved -> handlePackageChange(packageName, isRemoved) },
            onWallpaperChanged = {
                wallpaperManagerHelper.clearCache()
                wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
                refreshRightDrawerWallpaper()
            },
            onBatteryChanged = {
                usageStatsRefreshManager.updateUsageInBackground()
            },
            onActivityRecognitionPermissionGranted = {
                if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
            },
            onDndStateChanged = {
                if (appDockManager.getCurrentMode()) {
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted &&
                        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {

                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        Toast.makeText(this, this.getString(R.string.toast_dnd_re_enabled_focus_mode_active), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        broadcastReceiverManager.registerReceivers()
    }

    @SuppressLint("MissingPermission")
    internal fun refreshRightDrawerWallpaper() {
        if (!views.isRightDrawerWallpaperInitialized()) return
        WallpaperDisplayHelper.applySystemWallpaper(views.rightDrawerWallpaper)
    }

    internal fun initializeViews() {
        activityInitializer.initializeViews()
        views.fastScroller.refreshTypography(sharedPreferences)

        findViewById<TextView>(R.id.daily_usage_time)?.setOnClickListener {
            if (!usageStatsManager.hasUsageStatsPermission()) {
                startActivity(usageStatsManager.requestUsageStatsPermission())
            } else {
                usageStatsRefreshManager.updateUsageInBackground()
            }
        }

        searchTypeMenuManager = SearchTypeMenuManager(
            context = this,
            searchTypeButton = views.searchTypeButton,
            appSearchManagerProvider = { appSearchManager },
            isFocusModeActive = { appDockManager.getCurrentMode() }
        )
        searchTypeMenuManager.setup()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::activityResultHandler.isInitialized) {
            activityResultHandler.handleActivityResult(requestCode, resultCode, data)
        }
    }



    private fun ensureContactsLoadedForSearch() {
        if (contactManager.hasLoadedContacts()) return

        contactManager.loadContacts { _ ->
            if (isFinishing || isDestroyed) return@loadContacts
            updateAppSearchManager()
        }
    }

    internal fun updateSearchQueryAndUI(query: String) {
        viewModel.updateSearchQuery(query)
        if (query.isNotEmpty()) {
            ensureContactsLoadedForSearch()
            activityInitializer.setHeaderVisibility(false)
        } else {
            activityInitializer.setHeaderVisibility(true)
            handler.postDelayed({
                views.recyclerView.scrollToPosition(0)
            }, 100)
        }
        updateFastScrollerVisibility()
    }

    internal fun updateFastScrollerVisibility() {
        if (!views.isSearchBoxInitialized()) return

        findViewById<View?>(R.id.rss_feed_page)?.let { rssPageView ->
            RssFeedPage(this, rssPageView).updateTypography()
        }

        val focusMode = appDockManager.getCurrentMode()
        val workspaceMode = appDockManager.isWorkspaceModeActive()
        val showFastScroller = sharedPreferences.getBoolean(Constants.Prefs.SHOW_FAST_SCROLLER, true)
        val hasVisibleItems = (::adapter.isInitialized && adapter.getCurrentListSize() > 0) || appList.isNotEmpty()


        if (showOnlyFavoritesInitially && !focusMode && !workspaceMode) {
            views.fastScroller.visibility = View.GONE
            return
        }

        views.fastScroller.setFavoritesVisible(!focusMode && !workspaceMode)

        if (showFastScroller && hasVisibleItems) {
            views.fastScroller.visibility = View.VISIBLE
        } else {
            views.fastScroller.visibility = View.GONE
        }
    }

    internal fun getPreferredGridColumns(): Int {
        val minColumns = resources.getInteger(R.integer.grid_columns_min)
        val maxColumns = resources.getInteger(R.integer.grid_columns_max)
        val storedColumns = sharedPreferences.getInt(Constants.Prefs.GRID_COLUMNS, -1)
        return if (storedColumns in minColumns..maxColumns) {
            storedColumns
        } else {
            resources.getInteger(R.integer.app_grid_columns)
        }
    }

    internal fun requestInitialPermissions(onComplete: () -> Unit = {}) {
        permissionManager.requestContactsPermission {
            contactManager.loadContacts { _ ->
                updateAppSearchManager()
            }

            permissionManager.requestUsageStatsPermission(usageStatsManager) {
                permissionManager.requestDefaultLauncher {
                    viewModel.markDefaultLauncherAsked()
                    onComplete()
                }
            }
        }
    }

    internal fun initializeTimeDateAndWeather() {
        val use24HourClock = sharedPreferences.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)
        timeDateManager = TimeDateManager(
            views.timeTextView,
            views.dateTextView,
            views.rightDrawerTime,
            views.rightDrawerDate,
            use24HourClock
        )
        timeDateManager.startUpdates()

        widgetSetupManager = WidgetSetupManager(this, usageStatsManager, weatherManager, permissionManager, secureStorageManager)
        widgetSetupManager.setupWeather(views.weatherIcon, views.weatherText)
    }

    // deferredWidgetsInitialized (below) lives in the ViewModel's SavedStateHandle, so it
    // survives an Activity recreate - including the one AppCompatDelegate.setApplicationLocales()
    // triggers on language change, since MainActivity doesn't declare "locale" in its manifest
    // configChanges. But widgetLifecycleCoordinator/todoManager/financeWidgetManager etc. are all
    // fresh, uninitialized objects on the new instance. Without this instance-local flag, the
    // ViewModel's stale "already initialized" would make initializeDeferredWidgets() skip
    // DeferredWidgetInitializer forever for that instance, leaving every in-app widget's content
    // unpopulated even though their containers still get toggled visible.
    private var deferredWidgetsReadyForThisInstance = false

    internal fun initializeDeferredWidgets() {
        if (!sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)) {
            return
        }

        if (deferredWidgetsInitialized && deferredWidgetsReadyForThisInstance) {
            // Always update widget visibility to ensure all enabled widgets are shown
            widgetVisibilityManager.update(
                if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
                if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
            )
            return
        }

        val initializer = DeferredWidgetInitializer(
            widgetSetupManager = widgetSetupManager,
            sharedPreferences = sharedPreferences,
            lifecycleManager = lifecycleManager,
            widgetConfigurationManager = widgetConfigurationManager,
            widgetLifecycleCoordinator = widgetLifecycleCoordinator,
            onComplete = {
                initializeFinanceWidgetIfNeeded()
                todoAlarmManager = TodoAlarmManager(this)
                todoManager = TodoManager(this, sharedPreferences, views.todoRecyclerView, views.addTodoButton, todoAlarmManager)
                todoManager.initialize()
                updateLifecycleManagerWithDeferredWidgets()

                TypographyManager.applyToActivity(this)

                widgetVisibilityManager.update(
                    if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
                    if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
                )
            }
        )

        initializer.initialize()
        viewModel.markDeferredWidgetsInitialized()
        deferredWidgetsReadyForThisInstance = true
        updateRegistryDependencies()
    }

    private fun scheduleDeferredWidgetPrewarm() {
        if (widgetPrewarmScheduled ||
            deferredWidgetsReadyForThisInstance ||
            !sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
        ) {
            return
        }

        widgetPrewarmScheduled = true
        lifecycleScope.launch {
            try {
                delay(1200L)
                widgetPrewarmScheduled = false
                if (!isFinishing && !isDestroyed && !deferredWidgetsReadyForThisInstance) {
                    initializeDeferredWidgets()
                }
            } finally {
                widgetPrewarmScheduled = false
            }
        }
    }

    private fun initializeFinanceWidgetIfNeeded() {
        FinanceWidgetInitializer(this, sharedPreferences, secureStorageManager, 0)
            .onInitialized { manager ->
                financeWidgetManager = manager
            }
            .initialize(handler)
    }

    internal fun updateAppSearchManager(
        newFullList: List<ResolveInfo>? = null,
        newHomeList: List<ResolveInfo>? = null
    ) {
        if (isFinishing || isDestroyed) return

        val targetFullList = newFullList ?: fullAppList
        val targetHomeList = newHomeList ?: appList


        if (!appSearchManager.isConfigured()) {

            if (!::adapter.isInitialized) return


            appSearchManager.onSearchQueryChanged = { query ->
                updateSearchQueryAndUI(query)
            }

            appSearchManager.configure(
                fullAppList = targetFullList.toMutableList(),
                homeAppList = targetHomeList,
                adapter = adapter,
                searchBox = views.searchBox,
                contactsList = contactManager.getContactsList(),
                appMetadataCache = cacheManager.getMetadataCache(),
                isAppFiltered = { packageName ->
                    appDockManager.isAppHiddenInFocusMode(packageName)
                },
                isFocusModeActive = { appDockManager.getCurrentMode() }
            )
        } else {
            appSearchManager.updateData(targetFullList, targetHomeList, contactManager.getContactsList())
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        secureStorageManager = SecureStorageManager(this)
        applyOrientationPreference()
        AppInitializer(this).initialize()

        if (isFinishing || isDestroyed) return

        handleSettingsUpdate()
        observeViewModelState()
        if (!isFinishing) {
            reviewPromptManager = ReviewPromptManager(this, sharedPreferences)
            donationPromptManager = DonationPromptManager(this, sharedPreferences)
            reviewPromptManager.recordFirstUseIfNeeded()
        }
        if (!isFinishing) {
            systemBarManager.makeSystemBarsTransparent()
        }

        scheduleDeferredWidgetPrewarm()
        scheduleDeferredContactsLoad()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val isHomeOrLauncher = intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))

        if (isHomeOrLauncher) {
            if (::screenPagerManager.isInitialized) {
                screenPagerManager.openDefaultHomePage(animated = true)
            }
        }

        if (intent.getBooleanExtra("request_permissions_after_disclosure", false)) {
            if (!sharedPreferences.getBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, false)) {
                requestInitialPermissions {
                    sharedPreferences.edit { putBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, true) }
                }
            }
        }
    }

    private fun scheduleDeferredContactsLoad() {
        if (isFinishing || isDestroyed) return

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            ensureContactsLoadedForSearch()
        }, 800)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activityInitializer.handleConfigurationChange()
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.handleConfigurationChange()
        }
    }

    private fun observeViewModelState() {
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
                    if (targetPage != null && ::screenPagerManager.isInitialized && screenPagerManager.getCurrentPage() != targetPage) {
                        when (targetPage) {
                            ScreenPagerManager.Page.RSS -> screenPagerManager.openRssPage(animated = false)
                            ScreenPagerManager.Page.WIDGETS -> screenPagerManager.openWidgetsPage(animated = false)
                            ScreenPagerManager.Page.CENTER -> screenPagerManager.openCenterPage(animated = false)
                            ScreenPagerManager.Page.WALLPAPER -> screenPagerManager.openWallpaperPage(animated = false)
                        }
                    }
                }
            }
        }

        if (::screenPagerManager.isInitialized) {
            screenPagerManager.setOnPageChanged { page ->
                viewModel.updateCurrentPage(page)
                val isFullyTransparentPage = page == ScreenPagerManager.Page.WALLPAPER ||
                        page == ScreenPagerManager.Page.WIDGETS ||
                        page == ScreenPagerManager.Page.RSS
                systemBarManager.updateSystemBars(isFullyTransparentPage)
                if (page == ScreenPagerManager.Page.WIDGETS) {
                    initializeDeferredWidgets()
                }
            }
            if (screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.WIDGETS) {
                initializeDeferredWidgets()
            }
            val currentPage = screenPagerManager.getCurrentPage()
            val isCurrentFullyTransparent = currentPage == ScreenPagerManager.Page.WALLPAPER ||
                    currentPage == ScreenPagerManager.Page.WIDGETS ||
                    currentPage == ScreenPagerManager.Page.RSS
            systemBarManager.updateSystemBars(isCurrentFullyTransparent)
        }
    }

    internal fun initializeLifecycleManager() {
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

    private fun updateLifecycleManagerWithDeferredWidgets() {
        lifecycleManager.updateDependencies {
            copy(
                todoManager = todoManager
            )
        }
    }

    fun refreshAppsForFocusMode() {
        if (::appListUIUpdater.isInitialized) {
            appListUIUpdater.refreshAppsForFocusMode()
        }
    }

    fun openWidgetsPage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openWidgetsPage(animated)
        }
    }

    fun openRssPage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openRssPage(animated)
        }
    }

    fun openHomePage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openCenterPage(animated)
        }
    }

    fun openWallpaperPage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openWallpaperPage(animated)
        }
    }

    fun openDefaultHomePage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openDefaultHomePage(animated)
        }
    }

    fun setWidgetsPageLocked(locked: Boolean) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.setLeftPageLocked(locked)
        }
    }

    fun refreshAppsForWorkspace() {
        if (::appListUIUpdater.isInitialized) {
            appListUIUpdater.refreshAppsForWorkspace()
        }
    }

    fun clearAppCacheAndReload() {
        appListLoader.loadApps(forceRefresh = true)
    }

    fun filterAppsWithoutReload() {
        appListUIUpdater.filterAppsWithoutReload()
    }

    private fun handleSettingsUpdate() {
        applyOrientationPreference()
        settingsChangeCoordinator.handleSettingsUpdate()
        syncOptionalPagesAfterSettingsUpdate()
    }

    private fun applyOrientationPreference() {
        if (!::sharedPreferences.isInitialized) return
        requestedOrientation = if (sharedPreferences.getBoolean(Constants.Prefs.LANDSCAPE_ORIENTATION_ENABLED, false)) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun syncOptionalPagesAfterSettingsUpdate() {
        if (!::screenPagerManager.isInitialized) return

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
    }

    private fun handlePackageChange(packageName: String?, isRemoved: Boolean) {
        if (packageName == null) return
        backgroundExecutor.execute {
            cacheManager.removeMetadata(packageName)

            if (isRemoved) {
                cacheManager.clearCache()
            }

            appListLoader.clearCache()
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::navigationManager.isInitialized) {
            navigationManager.cleanup()
        }
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onDestroy()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            gestureHandler.updateGestureExclusion()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::sharedPreferences.isInitialized) return
        
        val widgetsChanged = sharedPreferences.getBoolean("saved_widgets_changed", false)
        
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onResume()
        }

        // If widgets were changed in configuration, ensure they are reordered correctly
        if (widgetsChanged) {
            sharedPreferences.edit { putBoolean("saved_widgets_changed", false) }
            if (deferredWidgetsInitialized) {
                initializeDeferredWidgets()
            }
        }
        
        // Force refresh fastscroller typography on resume to pick up any settings changes
        if (views.isFastScrollerInitialized()) {
            views.fastScroller.refreshTypography(sharedPreferences)
        }

        if (sharedPreferences.getBoolean(Constants.Prefs.WAITING_FOR_USAGE_STATS_RETURN, false)) {
            sharedPreferences.edit { putBoolean(Constants.Prefs.WAITING_FOR_USAGE_STATS_RETURN, false) }
            permissionManager.requestDefaultLauncher {
                viewModel.markDefaultLauncherAsked()
                if (!sharedPreferences.getBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, false)) {
                    sharedPreferences.edit { putBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, true) }
                }
            }
            return
        }
        if (reviewPromptManager.promptIfEligible()) {
            return
        }
        donationPromptManager.promptIfEligible()
    }

    override fun onPause() {
        super.onPause()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onPause()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            backgroundExecutor.execute {
                cacheManager.getMetadataCache().keys.toList()
                    .filter { it.startsWith("com.guruswarupa.launch.webapp.") }
                    .forEach { cacheManager.removeMetadata(it) }
            }
            wallpaperManagerHelper.clearCache()
        }

        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            cacheManager.clearCache()
            appListLoader.clearCache()
            wallpaperManagerHelper.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        cacheManager.clearCache()
        appListLoader.clearCache()
        wallpaperManagerHelper.clearCache()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionManager.handlePermissionResult(
            requestCode,
            permissions,
            grantResults,
            onContactsGranted = {
                contactManager.loadContacts { _ ->
                    updateAppSearchManager()
                }
            },
            onCallPhoneGranted = {  },
            onNotificationGranted = {
                if (::todoManager.isInitialized) {
                    todoManager.rescheduleTodoAlarms()
                }
            },
            onStorageGranted = {

            },
            onActivityRecognitionGranted = {
                if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
            }
        )

        if (requestCode == PermissionManager.CONTACTS_PERMISSION_REQUEST) {
            if (!sharedPreferences.getBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, false)) {
                handler.postDelayed({
                    permissionManager.requestUsageStatsPermission(usageStatsManager) {
                        permissionManager.requestDefaultLauncher {
                            viewModel.markDefaultLauncherAsked()
                            sharedPreferences.edit { putBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, true) }
                        }
                    }
                }, 300)
            }
        }

        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceSearchManager.startVoiceSearch()
            if (widgetLifecycleCoordinator.isNoiseDecibelWidgetInitialized()) {
                widgetLifecycleCoordinator.noiseDecibelWidget.onPermissionGranted()
            }
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                Constants.RequestCodes.ACTIVITY_RECOGNITION_PERMISSION -> if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
                CalendarEventsWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (widgetLifecycleCoordinator.isCalendarEventsWidgetInitialized()) {
                    widgetLifecycleCoordinator.calendarEventsWidget.onPermissionGranted()
                }
                CountdownWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (widgetLifecycleCoordinator.isCountdownWidgetInitialized()) {
                    widgetLifecycleCoordinator.countdownWidget.onPermissionGranted()
                }
            }
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        focusModeApplier.applyFocusMode(isFocusMode)
    }

    internal fun showWidgetConfigurationDialog() {
        resultRegistry.showWidgetConfigurationDialog()
    }

    fun updateRegistryDependencies() {
        val deps = MainActivityResultRegistry.DependencyContainer(
            widgetManager = widgetManager,
            widgetVisibilityManager = widgetVisibilityManager,
            widgetConfigurationManager = widgetConfigurationManager,
            voiceCommandHandler = voiceCommandHandler,
            activityResultHandler = activityResultHandler,
            packageManager = packageManager,
            contentResolver = contentResolver,
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            appList = appList,
            widgetLifecycleCoordinator = widgetLifecycleCoordinator
        )
        resultRegistry.setDependencies(deps)
    }
}
