package com.guruswarupa.launch.handlers

import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.managers.TypographyManager





class SettingsChangeCoordinator(
    private val activity: MainActivity,
    private val adapterProvider: () -> AppAdapter?,
    private val appDockManagerProvider: () -> AppDockManager?,
    private val widgetSetupManagerProvider: () -> WidgetSetupManager?,
    private val widgetThemeManagerProvider: () -> WidgetThemeManager?
) {

    private var lastIconPackPackage: String? = null
    private var lastIconPackEnabled: Boolean = false

    init {
        val sharedPreferences = activity.sharedPreferences
        lastIconPackPackage = sharedPreferences.getString(Constants.Prefs.ICON_PACK_PACKAGE, null)
        lastIconPackEnabled = sharedPreferences.getBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
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
        val color = Color.argb(alpha, 0, 0, 0)

        if (views.areTranslucencyOverlaysInitialized()) {
            views.backgroundTranslucencyOverlay.setBackgroundColor(color)
            views.widgetsDrawerTranslucencyOverlay.setBackgroundColor(color)
        }
        activity.findViewById<android.view.View>(com.guruswarupa.launch.R.id.rss_drawer_translucency_overlay)?.setBackgroundColor(color)
    }




    fun handleSettingsUpdate() {
        val sharedPreferences = activity.sharedPreferences
        val views = activity.views
        val adapter = adapterProvider()
        val use24HourClock = sharedPreferences.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)

        val iconPackPackage = sharedPreferences.getString(Constants.Prefs.ICON_PACK_PACKAGE, null)
        val iconPackEnabled = sharedPreferences.getBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
        if (iconPackPackage != lastIconPackPackage || iconPackEnabled != lastIconPackEnabled) {
            lastIconPackPackage = iconPackPackage
            lastIconPackEnabled = iconPackEnabled
            adapter?.refreshIcons()
        }

        applyThemeBasedWidgetBackgrounds()
        applyBackgroundTranslucency()
        TypographyManager.applyToActivity(activity)
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
        }

        activity.timeDateManager.setUse24HourFormat(use24HourClock)


        val viewPreference = sharedPreferences.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        ) ?: Constants.Prefs.VIEW_PREFERENCE_LIST
        val newIsGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID
        val desiredColumns = activity.getPreferredGridColumns()
        val currentIsGridMode = if (views.isRecyclerViewInitialized()) views.recyclerView.layoutManager is GridLayoutManager else false

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
