package com.guruswarupa.launch.managers

import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.handlers.ActivityInitializer
import com.guruswarupa.launch.handlers.NavigationManager




class DrawerManager(
    private val activity: FragmentActivity,
    private val screenPagerManager: ScreenPagerManager,
    private val gestureHandler: GestureHandler,
    private val usageStatsDisplayManager: UsageStatsDisplayManager,
    private val activityInitializer: ActivityInitializer,
    private val themeCheckCallback: () -> Unit,
    private val stockDrawerShownProvider: () -> Boolean = { false },
    private val hideStockDrawer: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    lateinit var navigationManager: NavigationManager
        private set




    fun setup() {
        gestureHandler.setupGestureExclusion()
        activityInitializer.setupDrawerLayout()
        screenPagerManager.setup()
        navigationManager = NavigationManager(screenPagerManager, gestureHandler, handler)
        screenPagerManager.setOnPageChanged { page ->
            themeCheckCallback()
            if (page == ScreenPagerManager.Page.WIDGETS) {
                handler.post { usageStatsDisplayManager.loadWeeklyUsageData() }
            }
        }


        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (stockDrawerShownProvider()) {
                    hideStockDrawer()
                    return
                }
                if (::navigationManager.isInitialized) {
                    navigationManager.handleBackPressed {


                    }
                }
            }
        })
    }
}
