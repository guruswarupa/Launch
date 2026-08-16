package com.guruswarupa.launch.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.animation.ValueAnimator
import android.view.View
import android.view.MotionEvent
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Gravity
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.ChangeBounds
import android.transition.Fade
import android.util.TypedValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppLauncher
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.ui.views.FastScroller
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView
import java.util.Locale





class ActivityInitializer(
    private val activity: FragmentActivity,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appLauncher: AppLauncher
) {
    companion object {
        private const val CATEGORY_APP_CLOCK = "android.intent.category.APP_CLOCK"
        private const val CATEGORY_APP_CALENDAR = "android.intent.category.APP_CALENDAR"
    }

    val views = MainActivityViews()
    private lateinit var searchMarginParams: MarginLayoutParams
    private var defaultSearchTopMargin = 0
    private var pinnedSearchTopMargin = 0
    private var headerHidden = false

    fun initializeViews() {
        with(views) {
            searchBox = activity.findViewById(R.id.search_box)
            searchContainer = activity.findViewById(R.id.search_container)
            searchMarginParams = searchContainer.layoutParams as MarginLayoutParams
            defaultSearchTopMargin = searchMarginParams.topMargin
            pinnedSearchTopMargin = activity.resources.getDimensionPixelSize(R.dimen.search_bar_top_margin_pinned)
            recyclerView = activity.findViewById(R.id.app_list)
            appListEmptyState = activity.findViewById(R.id.app_list_empty_state)
            fastScroller = activity.findViewById(R.id.fast_scroller)
            fastScroller.setRecyclerView(recyclerView)

            val mainContentStack = activity.findViewById<LinearLayout>(R.id.main_content_stack)
            ViewCompat.setOnApplyWindowInsetsListener(mainContentStack) { v, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
                insets
            }
            ViewCompat.requestApplyInsets(mainContentStack)

            recyclerView.setHasFixedSize(true)
            applyFastScrollerLayout()


            recyclerView.itemAnimator = null
            voiceSearchButton = activity.findViewById(R.id.voice_search_button)
            appDock = activity.findViewById(R.id.app_dock)
            wallpaperBackground = activity.findViewById(R.id.wallpaper_background)
            weatherIcon = activity.findViewById(R.id.weather_icon)
            weatherText = activity.findViewById(R.id.weather_text)
            timeTextView = activity.findViewById(R.id.time_widget)
            dateTextView = activity.findViewById(R.id.date_widget)
            topWidgetContainer = activity.findViewById(R.id.top_widget_container)


            todoRecyclerView = activity.findViewById(R.id.todo_recycler_view)
            addTodoButton = activity.findViewById(R.id.add_todo_button)


            rightDrawerWallpaper = activity.findViewById(R.id.right_drawer_wallpaper)
            rightDrawerTime = activity.findViewById(R.id.right_drawer_time)
            rightDrawerDate = activity.findViewById(R.id.right_drawer_date)
            weeklyUsageGraph = activity.findViewById(R.id.weekly_usage_graph)
            searchTypeButton = activity.findViewById(R.id.search_type_button)
            drawerLayout = activity.findViewById(R.id.drawer_layout)
            backgroundTranslucencyOverlay = activity.findViewById(R.id.background_translucency_overlay)
            widgetsDrawerTranslucencyOverlay = activity.findViewById(R.id.widgets_drawer_translucency_overlay)

            setupSearchBox(searchBox)
            setupLayoutManager(recyclerView)
            setupHeaderVisibilityOnScroll(recyclerView)
            setupTimeDateListeners(timeTextView, dateTextView)
            applyPhoneLandscapeOptimizations()


            val topWidgetEnabled = sharedPreferences.getBoolean(
                com.guruswarupa.launch.models.Constants.Prefs.TOP_WIDGET_ENABLED,
                true
            )
            topWidgetContainer.visibility = if (topWidgetEnabled) View.VISIBLE else View.GONE


            if (!topWidgetEnabled) {
                val extraMargin = activity.resources.getDimensionPixelSize(R.dimen.search_top_margin_when_widget_hidden)
                val params = searchContainer.layoutParams as MarginLayoutParams
                params.topMargin = extraMargin
                searchContainer.layoutParams = params
            } else {
                val params = searchContainer.layoutParams as MarginLayoutParams
                params.topMargin = 0
                searchContainer.layoutParams = params
            }
        }
    }

    fun handleConfigurationChange() {
        if (!views.isRecyclerViewInitialized()) return
        pinnedSearchTopMargin = activity.resources.getDimensionPixelSize(R.dimen.search_bar_top_margin_pinned)
        applyFastScrollerLayout()
        applyPhoneLandscapeOptimizations()
    }

    private fun applyFastScrollerLayout() {
        val displayMetrics = activity.resources.displayMetrics
        val isLandscape =
            activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val heightRatio = if (isLandscape) 0.5f else 0.65f
        val scrollerParams = views.fastScroller.layoutParams as FrameLayout.LayoutParams
        scrollerParams.height = (displayMetrics.heightPixels * heightRatio).toInt()
        scrollerParams.gravity = Gravity.BOTTOM or Gravity.END
        views.fastScroller.layoutParams = scrollerParams
        views.fastScroller.requestLayout()
    }

    private fun applyPhoneLandscapeOptimizations() {
        val mainActivity = activity as? MainActivity ?: return
        val configuration = activity.resources.configuration
        val isPhoneLandscape =
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                !mainActivity.isTablet()
        if (!isPhoneLandscape) {
            restoreDefaultPhoneLayout()
            return
        }

        val compactPadding = (12 * activity.resources.displayMetrics.density).toInt()
        val compactMargin = (8 * activity.resources.displayMetrics.density).toInt()

        views.topWidgetContainer.setPadding(compactPadding, compactPadding, compactPadding, compactPadding)
        (views.topWidgetContainer.layoutParams as? MarginLayoutParams)?.let { params ->
            params.topMargin = 0
            params.bottomMargin = compactMargin
            views.topWidgetContainer.layoutParams = params
        }

        views.timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        views.dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        views.weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        activity.findViewById<TextView>(R.id.daily_usage_time)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)

        (views.appDock.parent as? View)?.let { dockContainer ->
            dockContainer.setPadding(compactMargin, compactMargin, compactMargin, compactMargin)
        }
    }

    private fun restoreDefaultPhoneLayout() {
        val density = activity.resources.displayMetrics.density
        val defaultContainerPadding = (16 * density).toInt()
        val defaultDockPadding = (8 * density).toInt()

        views.topWidgetContainer.setPadding(
            defaultContainerPadding,
            defaultContainerPadding,
            defaultContainerPadding,
            defaultContainerPadding
        )
        (views.topWidgetContainer.layoutParams as? MarginLayoutParams)?.let { params ->
            params.topMargin = 0
            params.bottomMargin = defaultContainerPadding
            views.topWidgetContainer.layoutParams = params
        }

        views.timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
        views.dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        views.weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        activity.findViewById<TextView>(R.id.daily_usage_time)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

        (views.appDock.parent as? View)?.setPadding(
            defaultDockPadding,
            defaultDockPadding,
            defaultDockPadding,
            defaultDockPadding
        )
    }

    private fun setupSearchBox(searchBox: AutoCompleteTextView) {
        searchBox.setOnLongClickListener {
            val prefs = searchBox.context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val engine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
            val url = when (engine) {
                "Bing" -> "https://www.bing.com"
                "DuckDuckGo" -> "https://duckduckgo.com"
                "Ecosia" -> "https://www.ecosia.org"
                "Brave" -> "https://search.brave.com"
                "Startpage" -> "https://www.startpage.com"
                "Yahoo" -> "https://www.yahoo.com"
                "Qwant" -> "https://www.qwant.com"
                else -> "https://www.google.com"
            }
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                searchBox.context.startActivity(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(searchBox.context, searchBox.context.getString(R.string.toast_no_browser_found), android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun setupLayoutManager(recyclerView: RecyclerView) {
        val viewPreference = sharedPreferences.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        )
        val isGridMode = viewPreference == Constants.Prefs.VIEW_PREFERENCE_GRID

        if (isGridMode) {
            val columns = (activity as? MainActivity)?.getPreferredGridColumns()
                ?: activity.resources.getInteger(R.integer.app_grid_columns)
            val gridLayoutManager = GridLayoutManager(activity, columns)
            recyclerView.layoutManager = gridLayoutManager
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun setupHeaderVisibilityOnScroll(recyclerView: RecyclerView) {
        val hideThreshold = (activity.resources.displayMetrics.density * 40).toInt()
        val showThreshold = (activity.resources.displayMetrics.density * 10).toInt()
        var currentScrollState = RecyclerView.SCROLL_STATE_IDLE

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                currentScrollState = newState
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    return
                }

                val searchBox = activity.findViewById<android.widget.AutoCompleteTextView>(R.id.search_box)
                val isSearching = !searchBox?.text.toString().trim().isNullOrEmpty()
                if (isSearching) {
                    return
                }

                val offset = rv.computeVerticalScrollOffset()
                val scrollRange = rv.computeVerticalScrollRange()
                val viewportHeight = rv.height
                if (headerHidden && (offset <= showThreshold || scrollRange <= viewportHeight)) {
                    setHeaderVisibility(true)
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val searchBox = activity.findViewById<android.widget.AutoCompleteTextView>(R.id.search_box)
                val isSearching = !searchBox?.text.toString().trim().isNullOrEmpty()

                if (!isSearching) {
                    val offset = rv.computeVerticalScrollOffset()
                    val scrollRange = rv.computeVerticalScrollRange()
                    val viewportHeight = rv.height


                    val isShowingFavorites = (activity as? com.guruswarupa.launch.MainActivity)?.showOnlyFavoritesInitially ?: false

                    if (dy > 0 && !headerHidden && offset > hideThreshold && !isShowingFavorites) {

                        if (scrollRange > viewportHeight * 1.5) {
                            setHeaderVisibility(false)
                        }
                    } else if (
                        currentScrollState == RecyclerView.SCROLL_STATE_IDLE &&
                        dy < 0 &&
                        headerHidden &&
                        offset <= showThreshold
                    ) {
                        setHeaderVisibility(true)
                    }
                }
            }
        })


        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startY = event.y
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val deltaY = event.y - startY

                        if (deltaY > 50 && headerHidden) {
                            if (!recyclerView.canScrollVertically(-1)) {
                                setHeaderVisibility(true)
                            }
                        }
                    }
                }
                return false
            }
        })


        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (headerHidden) {
                val scrollRange = recyclerView.computeVerticalScrollRange()
                val viewportHeight = recyclerView.height
                if (scrollRange <= viewportHeight) {
                    recyclerView.post {
                        if (headerHidden) setHeaderVisibility(true)
                    }
                }
            }
        }
    }





    fun setHeaderVisibility(visible: Boolean) {
        if (visible && !headerHidden) return
        if (!visible && headerHidden) return
        headerHidden = !visible

        val stack = views.topWidgetContainer.parent as? ViewGroup ?: return


        val topWidgetEnabled = sharedPreferences.getBoolean(
            com.guruswarupa.launch.models.Constants.Prefs.TOP_WIDGET_ENABLED,
            true
        )

        val transition = TransitionSet().apply {
            addTransition(Fade().apply {
                addTarget(views.topWidgetContainer)
                addTarget(views.appDock.parent as View)
            })
            addTransition(ChangeBounds())
            duration = 250
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }

        TransitionManager.beginDelayedTransition(stack, transition)

        views.topWidgetContainer.isVisible = visible && topWidgetEnabled

        (views.appDock.parent as? View)?.isVisible = visible


        val targetMargin = when {
            !topWidgetEnabled -> {

                activity.resources.getDimensionPixelSize(R.dimen.search_top_margin_when_widget_hidden)
            }
            visible -> 0
            else -> pinnedSearchTopMargin
        }
        val params = views.searchContainer.layoutParams as MarginLayoutParams
        params.topMargin = targetMargin
        views.searchContainer.layoutParams = params
    }

    private fun setupTimeDateListeners(timeTextView: TextView, dateTextView: TextView) {
        timeTextView.setOnClickListener {
            val launched = launchResolvedAppWithLockCheck(
                Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APP_CLOCK),
                "Clock"
            )
            if (!launched) {
                val openedClock = launchIntentDirect(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    || launchIntentDirect(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                    || launchIntentDirect(Intent(AlarmClock.ACTION_SET_ALARM))
                    || launchLikelyClockLauncherApp()
                if (!openedClock) {
                    Toast.makeText(activity, activity.getString(R.string.toast_no_clock_app_found), Toast.LENGTH_SHORT).show()
                }
            }
        }

        dateTextView.setOnClickListener {
            val launched = launchResolvedAppWithLockCheck(
                Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APP_CALENDAR),
                "Calendar"
            )
            if (!launched) {
                val openedCalendar = launchIntentDirect(
                    Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
                )
                if (!openedCalendar) {
                    Toast.makeText(activity, activity.getString(R.string.toast_no_calendar_app_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchResolvedAppWithLockCheck(intent: Intent, fallbackName: String): Boolean {
        val resolveInfo = activity.packageManager.resolveActivity(intent, 0) ?: return false
        val packageName = resolveInfo.activityInfo?.packageName ?: return false
        val appName = try {
            activity.packageManager.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
        } catch (_: Exception) {
            fallbackName
        }
        appLauncher.launchAppWithLockCheck(packageName, appName)
        return true
    }

    private fun launchIntentDirect(intent: Intent): Boolean {
        try {
            activity.startActivity(intent)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun launchLikelyClockLauncherApp(): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = activity.packageManager.queryIntentActivities(launcherIntent, 0)

        val clockApp = candidates
            .asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = try {
                    activity.packageManager.getApplicationLabel(info.activityInfo.applicationInfo).toString()
                } catch (_: Exception) {
                    packageName
                }
                packageName to label
            }
            .distinctBy { it.first }
            .firstOrNull { (packageName, label) ->
                val packageLower = packageName.lowercase(Locale.ROOT)
                val labelLower = label.lowercase(Locale.ROOT)
                "clock" in packageLower || "clock" in labelLower || "alarm" in packageLower || "alarm" in labelLower
            } ?: return false

        appLauncher.launchAppWithLockCheck(clockApp.first, clockApp.second)
        return true
    }

    fun setupDrawerLayout() {
        val drawerLayout = views.drawerLayout

        drawerLayout.post {
            val displayMetrics = activity.resources.displayMetrics
            val drawerWidth = displayMetrics.widthPixels
            val targetWidth = drawerWidth

            val rssView = activity.findViewById<FrameLayout>(R.id.rss_feed_page)
            rssView?.let {
                val params = it.layoutParams as ViewGroup.LayoutParams
                params.width = targetWidth
                it.layoutParams = params

                val header = activity.findViewById<LinearLayout>(R.id.rss_header)
                val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.rss_swipe_refresh)
                if (header != null && swipeRefresh != null) {
                    val headerParams = header.layoutParams as FrameLayout.LayoutParams
                    val initialHeaderTopMargin = (header.getTag(R.id.rss_header) as? Int) ?: headerParams.topMargin.also {
                        header.setTag(R.id.rss_header, it)
                    }
                    val initialSwipeTopPadding = (swipeRefresh.getTag(R.id.rss_swipe_refresh) as? Int) ?: swipeRefresh.paddingTop.also {
                        swipeRefresh.setTag(R.id.rss_swipe_refresh, it)
                    }

                    ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets ->
                        val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

                        val updatedHeaderParams = header.layoutParams as FrameLayout.LayoutParams
                        updatedHeaderParams.topMargin = initialHeaderTopMargin + topInset
                        header.layoutParams = updatedHeaderParams

                        swipeRefresh.setPadding(
                            swipeRefresh.paddingLeft,
                            initialSwipeTopPadding + topInset,
                            swipeRefresh.paddingRight,
                            swipeRefresh.paddingBottom
                        )
                        insets
                    }
                    ViewCompat.requestApplyInsets(it)
                }
            }


            val leftDrawerView = activity.findViewById<FrameLayout>(R.id.widgets_drawer)
            leftDrawerView?.let {
                val params = it.layoutParams as ViewGroup.LayoutParams
                params.width = targetWidth
                it.layoutParams = params

                val header = activity.findViewById<LinearLayout>(R.id.widget_settings_header)
                val drawerScroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.widgets_drawer_scroll)
                if (header != null && drawerScroll != null) {
                    val headerParams = header.layoutParams as FrameLayout.LayoutParams
                    val initialHeaderTopMargin = (header.getTag(R.id.widget_settings_header) as? Int) ?: headerParams.topMargin.also {
                        header.setTag(R.id.widget_settings_header, it)
                    }
                    val initialScrollTopPadding = (drawerScroll.getTag(R.id.widgets_drawer_scroll) as? Int) ?: drawerScroll.paddingTop.also {
                        drawerScroll.setTag(R.id.widgets_drawer_scroll, it)
                    }

                    ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets ->
                        val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

                        val updatedHeaderParams = header.layoutParams as FrameLayout.LayoutParams
                        updatedHeaderParams.topMargin = initialHeaderTopMargin + topInset
                        header.layoutParams = updatedHeaderParams

                        drawerScroll.setPadding(
                            drawerScroll.paddingLeft,
                            initialScrollTopPadding + topInset,
                            drawerScroll.paddingRight,
                            drawerScroll.paddingBottom
                        )
                        insets
                    }
                    ViewCompat.requestApplyInsets(it)
                }
            }


            val rightDrawerView = activity.findViewById<FrameLayout>(R.id.wallpaper_drawer)
            rightDrawerView?.let {
                val params = it.layoutParams as ViewGroup.LayoutParams
                params.width = targetWidth
                it.layoutParams = params
            }
        }


        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.START)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.END)
    }

    fun setupVoiceSearchButton(voiceSearchManager: VoiceSearchManager) {
        views.voiceSearchButton.setOnClickListener {
            voiceSearchManager.startVoiceSearch()
        }

        views.voiceSearchButton.setOnLongClickListener {
            voiceSearchManager.triggerSystemAssistant()
            true
        }
    }
}
