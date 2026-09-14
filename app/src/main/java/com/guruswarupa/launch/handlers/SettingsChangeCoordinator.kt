package com.guruswarupa.launch.handlers

import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.ui.theme.ThemeManager





class SettingsChangeCoordinator(
    private val activity: MainActivity,
    private val adapterProvider: () -> AppAdapter?,
    private val appDockManagerProvider: () -> AppDockManager?,
    private val widgetThemeManagerProvider: () -> WidgetThemeManager?
) {

    private var lastIconPackPackage: String? = null
    private var lastIconPackEnabled: Boolean = false
    private var lastViewPreference: String
    private var lastStockDrawerEnabled: Boolean
    private var lastStockHotseatCount: Int

    init {
        val sharedPreferences = activity.sharedPreferences
        lastIconPackPackage = sharedPreferences.getString(Constants.Prefs.ICON_PACK_PACKAGE, null)
        lastIconPackEnabled = sharedPreferences.getBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
        lastViewPreference = sharedPreferences.getString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST)
            ?: Constants.Prefs.VIEW_PREFERENCE_LIST
        lastStockDrawerEnabled = sharedPreferences.getBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, true)
        lastStockHotseatCount = sharedPreferences.getInt(Constants.Prefs.STOCK_HOTSEAT_COUNT, 4)
    }



    fun applyThemeBasedWidgetBackgrounds() {
        val widgetThemeManager = widgetThemeManagerProvider() ?: return
        val views = activity.views
        val appDockManager = appDockManagerProvider()

        widgetThemeManager.apply(
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            searchContainer = if (views.isSearchContainerInitialized()) views.searchContainer else null,
            voiceSearchButton = if (views.isVoiceSearchButtonInitialized()) views.voiceSearchButton else null,
            searchTypeButton = if (views.isSearchTypeButtonInitialized()) views.searchTypeButton else null,
            appDockManager = appDockManager
        )
    }




    fun applyBackgroundTranslucency() {
        val sharedPreferences = activity.sharedPreferences
        val views = activity.views
        val translucency = sharedPreferences.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(activity, com.guruswarupa.launch.R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))

        if (views.areTranslucencyOverlaysInitialized()) {
            views.backgroundTranslucencyOverlay.setBackgroundColor(color)
            views.widgetsDrawerTranslucencyOverlay.setBackgroundColor(color)
            views.wallpaperDrawerTranslucencyOverlay.setBackgroundColor(color)
        }
        activity.findViewById<android.view.View>(com.guruswarupa.launch.R.id.rss_drawer_translucency_overlay)?.setBackgroundColor(color)
        activity.findViewById<android.view.View>(com.guruswarupa.launch.R.id.ai_chat_translucency_overlay)?.setBackgroundColor(color)

        try {
            val currentPage = activity.screenPagerManager.getCurrentPage()
            val isFullyTransparentPage = currentPage == com.guruswarupa.launch.managers.ScreenPagerManager.Page.WALLPAPER ||
                    currentPage == com.guruswarupa.launch.managers.ScreenPagerManager.Page.WIDGETS ||
                    currentPage == com.guruswarupa.launch.managers.ScreenPagerManager.Page.RSS ||
                    currentPage == com.guruswarupa.launch.managers.ScreenPagerManager.Page.AI_CHAT ||
                    currentPage == com.guruswarupa.launch.managers.ScreenPagerManager.Page.CENTER
            activity.systemBarManager.updateSystemBars(isFullyTransparentPage)
        } catch (e: UninitializedPropertyAccessException) {
            // screenPagerManager not set up yet (e.g. during initial onCreate)
        }
    }




    fun handleSettingsUpdate() {
        val sharedPreferences = activity.sharedPreferences

        // If the color theme (palette/accent/opaque-surfaces) changed, everything below this
        // point is about to be redone by the recreate anyway — bail out early rather than
        // running the full settings-update pass on views that are seconds from being torn down.
        if (ThemeManager.isStale(activity, sharedPreferences)) {
            activity.recreate()
            return
        }

        val views = activity.views
        val adapter = adapterProvider()
        val use24HourClock = sharedPreferences.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)

        val iconPackPackage = sharedPreferences.getString(Constants.Prefs.ICON_PACK_PACKAGE, null)
        val iconPackEnabled = sharedPreferences.getBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
        val iconPackChanged = iconPackPackage != lastIconPackPackage || iconPackEnabled != lastIconPackEnabled
        if (iconPackChanged) {
            lastIconPackPackage = iconPackPackage
            lastIconPackEnabled = iconPackEnabled
            adapter?.refreshIcons()
        }
        if (activity.isStockDrawerManagerInitialized()) {
            activity.stockDrawerManager.refreshAppearance(iconPackChanged)
        }

        applyThemeBasedWidgetBackgrounds()
        applyBackgroundTranslucency()
        TypographyManager.applyToActivity(activity)
        adapter?.refreshTypography()
        views.fastScroller.refreshTypography(sharedPreferences)


        val topWidgetEnabled = sharedPreferences.getBoolean(Constants.Prefs.TOP_WIDGET_ENABLED, true)
        if (views.isRecyclerViewInitialized()) {
            views.topWidgetContainer.visibility = if (topWidgetEnabled) android.view.View.VISIBLE else android.view.View.GONE


            val params = views.searchContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
            if (!topWidgetEnabled) {

                val extraMargin = activity.resources.getDimensionPixelSize(com.guruswarupa.launch.R.dimen.search_top_margin_when_widget_hidden)
                params.topMargin = extraMargin
            } else {

                params.topMargin = 0
            }
            views.searchContainer.layoutParams = params

            // Stock's home page is meant to stay minimal, like a real launcher's - its own
            // drawer already has a search box, so the home page search bar is redundant there.
            views.searchContainer.visibility = if (com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }

            activity.activityInitializer.applyTopWidgetStyle()
        }

        if (activity.isWallpaperMediaControllerInitialized()) {
            activity.wallpaperMediaController.onSettingsUpdated()
        }

        activity.timeDateManager.setUse24HourFormat(use24HourClock)


        val viewPreference = sharedPreferences.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        ) ?: Constants.Prefs.VIEW_PREFERENCE_LIST
        val enteringOrLeavingStock = viewPreference != lastViewPreference &&
            (viewPreference == Constants.Prefs.VIEW_PREFERENCE_STOCK || lastViewPreference == Constants.Prefs.VIEW_PREFERENCE_STOCK)
        lastViewPreference = viewPreference
        val newIsGridMode = com.guruswarupa.launch.utils.LayoutMode.isGridRendering(sharedPreferences)
        val desiredColumns = activity.getPreferredGridColumns()
        val currentIsGridMode = if (views.isRecyclerViewInitialized()) views.recyclerView.layoutManager is GridLayoutManager else false

        if (enteringOrLeavingStock) {
            // main_content_stack has animateLayoutChanges="true" for things like the top widget
            // being toggled on/off - but switching into/out of Stock changes several of its
            // children's visibility at once (search bar, dock, top widget restyling), and
            // animating all of that together as one LayoutTransition can settle the dock at the
            // wrong height (it read as correct only after a cold start, which never animates).
            // Suppress it for just this one change and restore it once the new layout has
            // actually been measured, so future individual toggles still animate normally.
            val mainContentStack = activity.findViewById<android.view.ViewGroup>(com.guruswarupa.launch.R.id.main_content_stack)
            val originalTransition = mainContentStack?.layoutTransition
            mainContentStack?.let { it.layoutTransition = null }

            if (viewPreference == Constants.Prefs.VIEW_PREFERENCE_STOCK) {
                // Stock's home page is always the favorites grid - if the user had scrolled
                // into "all apps" in List/Grid mode right before switching, force back to
                // favorites-only so it doesn't try to render the all-apps letter-separator
                // layout inside what's supposed to be a small favorites grid.
                activity.showOnlyFavoritesInitially = true
                // Workspaces don't fit Stock's model (its drawer is meant to show every app) -
                // fully deactivate any active workspace, not just hide its dock icon.
                appDockManagerProvider()?.turnOffWorkspace()
            } else if (activity.isStockDrawerManagerInitialized()) {
                activity.stockDrawerManager.hide(animated = false)
                activity.stockDrawerManager.hideHotseat()
            }
            activity.appListLoader.loadApps(forceRefresh = false)

            // loadApps() finishes asynchronously (cache/query work on a background thread, then
            // posted back) - a single post{} here would fire before that completes and the dock's
            // own visibility actually changes, re-arming the animation right before the change it
            // was meant to skip. A short delay comfortably covers the normal reload time instead.
            mainContentStack?.let { stack ->
                stack.postDelayed({ stack.layoutTransition = originalTransition }, 300)
            }
        } else if (viewPreference == Constants.Prefs.VIEW_PREFERENCE_STOCK) {
            // Already in Stock - reload if the drawer toggle or hotseat size changed, since
            // neither is reflected until the home/hotseat/drawer lists are recomputed.
            val stockDrawerEnabled = sharedPreferences.getBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, true)
            val stockHotseatCount = sharedPreferences.getInt(Constants.Prefs.STOCK_HOTSEAT_COUNT, 4)
            if (stockDrawerEnabled != lastStockDrawerEnabled || stockHotseatCount != lastStockHotseatCount) {
                lastStockDrawerEnabled = stockDrawerEnabled
                lastStockHotseatCount = stockHotseatCount
                if (!stockDrawerEnabled && activity.isStockDrawerManagerInitialized()) {
                    activity.stockDrawerManager.hide(animated = false)
                }
                activity.appListLoader.loadApps(forceRefresh = false)
            }
        }

        if (newIsGridMode != currentIsGridMode && adapter != null) {

            views.recyclerView.layoutManager = if (newIsGridMode) {
                val gridLayoutManager = GridLayoutManager(activity, desiredColumns)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR || viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                            desiredColumns
                        } else {
                            1
                        }
                    }
                }
                gridLayoutManager
            } else {
                LinearLayoutManager(activity)
            }


            adapter.updateViewMode(newIsGridMode)



            activity.updateAppSearchManager()
        } else if (newIsGridMode && currentIsGridMode) {
            val layoutManager = views.recyclerView.layoutManager as? GridLayoutManager
            if (layoutManager != null && layoutManager.spanCount != desiredColumns) {
                layoutManager.spanCount = desiredColumns

                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter?.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR || viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                            desiredColumns
                        } else {
                            1
                        }
                    }
                }
                layoutManager.requestLayout()

                activity.appListLoader.loadApps(forceRefresh = false)
            }
        }


        // Only update icon style if it actually changed
        val iconStyle = sharedPreferences.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
        val currentIconStyle = adapter?.getCurrentIconStyle()
        if (iconStyle != currentIconStyle) {
            adapter?.updateIconStyle(iconStyle)
        }

        // Only update icon size if it actually changed
        val iconSize = sharedPreferences.getInt(Constants.Prefs.ICON_SIZE, 40)
        val currentIconSize = adapter?.getCurrentIconSize()
        if (iconSize != currentIconSize) {
            adapter?.updateIconSize(iconSize)
        }


        val showAppNamesInGrid = sharedPreferences.getBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)
        adapter?.updateShowAppNamesInGrid(showAppNamesInGrid)

        val hideAppIconInList = sharedPreferences.getBoolean(Constants.Prefs.HIDE_APP_ICON_IN_LIST, false)
        adapter?.updateHideAppIconInList(hideAppIconInList)

        activity.updateFastScrollerVisibility()


        activity.serviceManager.updateShakeDetectionService()
        activity.serviceManager.updateWalkDetectionService()
        activity.serviceManager.updateScreenDimmerService()
        activity.serviceManager.updateNightModeService()
        activity.serviceManager.updateFlipToDndService()
        activity.serviceManager.updateBackTapService()


        activity.hiddenAppManager.forceRefresh()


        activity.appListLoader.loadApps(forceRefresh = false)

        try {
            activity.financeWidgetManager.updateDisplay()
        } catch (_: Exception) {
        }


        activity.wallpaperManagerHelper.applyBlurToViews()
        activity.wallpaperManagerHelper.clearCache()
        activity.wallpaperManagerHelper.setWallpaperBackground(forceReload = true)

        activity.refreshRightDrawerWallpaper()

        try {
            activity.screenPagerManager.reloadPages()
        } catch (e: UninitializedPropertyAccessException) {

        }

        activity.activityInitializer.setupDrawerLayout()


        appDockManagerProvider()?.let { dockManager ->
            try {
                dockManager.refreshDockVisibility()
            } catch (e: Exception) {

            }
        }

    }
}
