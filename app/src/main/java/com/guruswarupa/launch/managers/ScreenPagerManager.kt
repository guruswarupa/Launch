package com.guruswarupa.launch.managers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.ui.views.SafeHorizontalScrollView

class ScreenPagerManager(
    private val activity: FragmentActivity,
    private val drawerLayout: DrawerLayout
) {
    enum class Page {
        RSS,
        WIDGETS,
        CENTER,
        WALLPAPER
    }

    private lateinit var pagerScrollView: HorizontalScrollView
    private var pageWidth = 0
    private var leftPageLocked = false
    private var currentPage = Page.CENTER
    private var pageChangeListener: ((Page) -> Unit)? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartScrollX = 0
    private var touchStartPage = Page.CENTER
    private var velocityTracker: VelocityTracker? = null
    private val viewConfiguration = ViewConfiguration.get(activity)
    private val minimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
    private val touchSlop = viewConfiguration.scaledTouchSlop
    private val pageViews = linkedMapOf<Page, View>()
    private var activePages = listOf(Page.WALLPAPER, Page.CENTER, Page.WIDGETS)
    private var suppressNextLayoutSnap = false

    companion object {
        private const val PAGE_SWITCH_THRESHOLD = 0.12f
        private const val PAGE_SWITCH_SETTLE_THRESHOLD = 0.22f
        private const val FLING_VELOCITY_MULTIPLIER = 4
    }

    fun setup() {
        val rssView = activity.findViewById<View>(R.id.rss_feed_page) ?: return
        val widgetsView = activity.findViewById<View>(R.id.widgets_drawer) ?: return
        val centerView = activity.findViewById<View>(R.id.main_content) ?: return
        val rightView = activity.findViewById<View>(R.id.wallpaper_drawer) ?: return

        pageViews.clear()
        pageViews[Page.RSS] = rssView
        pageViews[Page.WIDGETS] = widgetsView
        pageViews[Page.CENTER] = centerView
        pageViews[Page.WALLPAPER] = rightView

        val pageStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        runWithAccessibilityTraversalSuppressed(drawerLayout) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            ViewCompat.setAccessibilityDelegate(drawerLayout, AccessibilityDelegateCompat())
            drawerLayout.removeAllViews()

            pageViews.values.forEach { page ->
                (page.parent as? ViewGroup)?.removeView(page)
                setupDoubleTapToLock(page)
            }
        }

        pagerScrollView = object : SafeHorizontalScrollView(activity) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchStartScrollX = scrollX
                        touchStartPage = nearestPageFor(scrollX)
                        updatePageWidth()
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain().apply {
                            addMovement(event)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val isHorizontalSwipe =
                            kotlin.math.abs(deltaX) > touchSlop &&
                                kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)

                        if (!isHorizontalSwipe) {
                            return false
                        }

                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                return super.onInterceptTouchEvent(event)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouchEvent(event)
                return super.onTouchEvent(event)
            }
        }.apply {
            layoutParams = DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isSaveEnabled = false
            visibility = View.INVISIBLE
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(pageStrip)
            setOnScrollChangeListener { _, scrollX, _, _, _ ->
                applyPageTransitions(scrollX)
                val page = nearestPageFor(scrollX)
                if (page != currentPage) {
                    notifyPageChanged(page, force = false)
                }
            }
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val newWidth = right - left
                val oldWidth = oldRight - oldLeft
                if (newWidth > 0 && oldWidth > 0 && newWidth != oldWidth) {
                    if (suppressNextLayoutSnap) {
                        suppressNextLayoutSnap = false
                    } else {
                        post {
                            snapToCurrentPageAfterLayout()
                        }
                    }
                }
            }
        }

        runWithAccessibilityTraversalSuppressed(drawerLayout) {
            drawerLayout.addView(pagerScrollView)
        }
        rebuildPageStrip()

        pagerScrollView.post {
            if (pagerScrollView.width > 0) {
                initializeAfterLayout()
            } else {
                pagerScrollView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                        if (pagerScrollView.width > 0) {
                            pagerScrollView.removeOnLayoutChangeListener(this)
                            initializeAfterLayout()
                        }
                    }
                })
            }
        }
    }

    fun reloadPages() {
        if (!::pagerScrollView.isInitialized) {
            return
        }
        val previousPage = currentPage
        rebuildPageStrip()
        updatePageWidth(force = true)
        val restoredPage = if (activePages.contains(previousPage)) previousPage else getDefaultPage()
        scrollToPage(restoredPage, animated = false)
        pagerScrollView.post {
            pagerScrollView.visibility = View.VISIBLE
            notifyPageChanged(restoredPage, force = true)
            applyPageTransitions(pagerScrollView.scrollX)
        }
    }

    private fun rebuildPageStrip() {
        activePages = buildActivePages()
        val pageStrip = pagerScrollView.getChildAt(0) as? LinearLayout ?: return
        runWithAccessibilityTraversalSuppressed(pagerScrollView) {
            pageStrip.removeAllViews()

            activePages.forEach { page ->
                pageViews[page]?.let { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                    pageStrip.addView(
                        view,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }
    }

    private fun buildActivePages(): List<Page> {
        val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val rssEnabled = prefs.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)
        val widgetsEnabled = prefs.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
        val pages = mutableListOf<Page>()
        pages.add(Page.WALLPAPER)
        pages.add(Page.CENTER)
        if (widgetsEnabled) {
            pages.add(Page.WIDGETS)
        }
        if (rssEnabled) {
            pages.add(Page.RSS)
        }
        return pages
    }

    private fun initializeAfterLayout() {
        val width = pagerScrollView.width
        if (width <= 0) {
            return
        }

        pageWidth = width
        val pageStrip = pagerScrollView.getChildAt(0) as? LinearLayout ?: return
        for (index in 0 until pageStrip.childCount) {
            val child = pageStrip.getChildAt(index)
            val params = child.layoutParams
            params.width = pageWidth
            child.layoutParams = params
        }
        pageStrip.requestLayout()

        val defaultPage = getDefaultPage()

        pagerScrollView.post {
            val targetX = pageIndex(defaultPage) * pageWidth
            pagerScrollView.scrollTo(targetX, 0)
            currentPage = defaultPage
            pagerScrollView.post {
                if (pagerScrollView.scrollX != targetX) {
                    pagerScrollView.scrollTo(targetX, 0)
                }
                pagerScrollView.visibility = View.VISIBLE
                notifyPageChanged(defaultPage, force = true)
                applyPageTransitions(pagerScrollView.scrollX)
            }
        }
    }

    private fun applyPageTransitions(scrollX: Int) {
        if (pageWidth <= 0) {
            return
        }
        val pageStrip = pagerScrollView.getChildAt(0) as? LinearLayout ?: return

        for (index in 0 until pageStrip.childCount) {
            val page = pageStrip.getChildAt(index)
            val pageCenterX = index * pageWidth
            val distanceFromCenter = kotlin.math.abs(scrollX - pageCenterX).toFloat()
            val fraction = (distanceFromCenter / pageWidth).coerceIn(0f, 1f)

            page.alpha = 1f - (fraction * 0.08f)
            page.scaleX = 1f
            page.scaleY = 1f
            page.translationX = 0f
        }
    }

    private fun setupDoubleTapToLock(view: View) {
        val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val service = ScreenLockAccessibilityService.instance
                if (service != null) {
                    return service.lockScreen()
                }
                Toast.makeText(activity, activity.getString(R.string.toast_enable_accessibility_service_to_lock_screen), Toast.LENGTH_SHORT).show()
                try {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                }
                return true
            }
        })

        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            gestureDetector.onTouchEvent(event)
        }
    }

    fun setOnPageChanged(listener: ((Page) -> Unit)?) {
        pageChangeListener = listener
    }

    fun cleanup() {
        pageChangeListener = null
        velocityTracker?.recycle()
        velocityTracker = null
    }

    fun isPageOpen(page: Page): Boolean = currentPage == page

    fun openLeftPage(animated: Boolean = true) = openWallpaperPage(animated)

    fun openRssPage(animated: Boolean = true) = scrollToPage(Page.RSS, animated)

    fun openWidgetsPage(animated: Boolean = true) = scrollToPage(Page.WIDGETS, animated)

    fun openCenterPage(animated: Boolean = true) = scrollToPage(Page.CENTER, animated)

    fun openWallpaperPage(animated: Boolean = true) = scrollToPage(Page.WALLPAPER, animated)

    fun setLeftPageLocked(locked: Boolean) {
        leftPageLocked = locked
        if (locked && (currentPage == Page.RSS || currentPage == Page.WIDGETS)) {
            openWallpaperPage(animated = true)
        }
    }

    private fun handleTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartScrollX = pagerScrollView.scrollX
                touchStartPage = nearestPageFor(pagerScrollView.scrollX)
                updatePageWidth()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply {
                    addMovement(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val dragDistance = touchStartX - event.x
                val flingVelocity = velocityTracker?.xVelocity ?: 0f
                pagerScrollView.post { snapToNearestPage(dragDistance, flingVelocity) }
                recycleVelocityTracker()
            }
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun snapToNearestPage(dragDistance: Float, flingVelocity: Float) {
        if (pageWidth <= 0) {
            return
        }
        val threshold = pageWidth * PAGE_SWITCH_THRESHOLD
        val settleThreshold = pageWidth * PAGE_SWITCH_SETTLE_THRESHOLD
        val startPage = touchStartPage

        val target = when {
            flingVelocity < -(minimumFlingVelocity * FLING_VELOCITY_MULTIPLIER) -> nextPage(startPage)
            flingVelocity > (minimumFlingVelocity * FLING_VELOCITY_MULTIPLIER) -> previousPage(startPage)
            dragDistance > threshold -> nextPage(startPage)
            dragDistance < -threshold -> previousPage(startPage)
            kotlin.math.abs(dragDistance) > settleThreshold -> nearestPageFor(pagerScrollView.scrollX)
            else -> startPage
        }

        scrollToPage(target, animated = true)
    }

    private fun scrollToPage(page: Page, animated: Boolean) {
        if (!::pagerScrollView.isInitialized || !activePages.contains(page)) {
            return
        }
        updatePageWidth()
        if (pageWidth <= 0) {
            return
        }

        val targetX = pageIndex(page) * pageWidth
        if (animated) {
            pagerScrollView.smoothScrollTo(targetX, 0)
        } else {
            pagerScrollView.scrollTo(targetX, 0)
        }

        pagerScrollView.postDelayed(
            { notifyPageChanged(page, force = false) },
            if (animated) 250L else 0L
        )
    }

    fun updatePageWidth(force: Boolean = false) {
        val width = pagerScrollView.width
        if (width <= 0) {
            return
        }

        if (force || width != pageWidth) {
            pageWidth = width
            val pageStrip = pagerScrollView.getChildAt(0) as? LinearLayout ?: return
            for (index in 0 until pageStrip.childCount) {
                val child = pageStrip.getChildAt(index)
                val params = child.layoutParams
                params.width = pageWidth
                child.layoutParams = params
            }
            pageStrip.requestLayout()
        }
    }

    fun handleConfigurationChange() {
        if (!::pagerScrollView.isInitialized) {
            return
        }
        suppressNextLayoutSnap = false
        pagerScrollView.requestLayout()
        pagerScrollView.post {
            snapToCurrentPageAfterLayout()
            pagerScrollView.postDelayed({
                snapToCurrentPageAfterLayout()
            }, 32L)
        }
    }

    private fun snapToCurrentPageAfterLayout() {
        updatePageWidth(force = true)
        if (pageWidth <= 0) {
            return
        }

        val targetPage = sanitizePage(currentPage)
        val targetX = pageIndex(targetPage) * pageWidth
        suppressNextLayoutSnap = true
        pagerScrollView.scrollTo(targetX, 0)
        notifyPageChanged(targetPage, force = true)
        applyPageTransitions(targetX)
    }

    private fun nearestPageFor(scrollX: Int): Page {
        if (pageWidth <= 0 || activePages.isEmpty()) {
            return Page.CENTER
        }
        val index = ((scrollX + (pageWidth / 2)) / pageWidth).coerceIn(0, activePages.lastIndex)
        val candidate = activePages[index]
        return if (leftPageLocked && (candidate == Page.RSS || candidate == Page.WIDGETS)) {
            Page.WALLPAPER
        } else {
            candidate
        }
    }

    private fun nextPage(page: Page): Page {
        val pages = navigablePages()
        val index = pages.indexOf(page).takeIf { it >= 0 } ?: return Page.CENTER
        return pages[(index + 1) % pages.size]
    }

    private fun previousPage(page: Page): Page {
        val pages = navigablePages()
        val index = pages.indexOf(page).takeIf { it >= 0 } ?: return Page.CENTER
        return pages[(index - 1 + pages.size) % pages.size]
    }

    private fun navigablePages(): List<Page> {
        return if (leftPageLocked) {

            activePages.filterNot { it == Page.RSS || it == Page.WIDGETS }
        } else {
            activePages
        }
    }

    private fun notifyPageChanged(page: Page, force: Boolean) {
        if (!force && page == currentPage) {
            return
        }
        currentPage = page
        pageChangeListener?.invoke(page)
    }

    fun getDefaultPage(): Page {
        val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val target = prefs.getString(Constants.Prefs.DEFAULT_HOME_PAGE_TARGET, null)
        val defaultPage = when (target) {
            "rss" -> Page.RSS
            "widgets" -> Page.WIDGETS
            "wallpaper" -> Page.WALLPAPER
            "center" -> Page.CENTER
            else -> legacyDefaultPage(prefs)
        }
        return sanitizePage(defaultPage)
    }

    private fun legacyDefaultPage(prefs: android.content.SharedPreferences): Page {
        return when (prefs.getInt(Constants.Prefs.DEFAULT_HOME_PAGE_INDEX, 1)) {
            0 -> Page.WIDGETS
            2 -> Page.WALLPAPER
            else -> Page.CENTER
        }
    }

    private fun sanitizePage(page: Page): Page {
        if (!activePages.contains(page)) {
            return Page.CENTER
        }
        if (leftPageLocked && (page == Page.RSS || page == Page.WIDGETS)) {
            return Page.CENTER
        }
        return page
    }

    private fun pageIndex(page: Page): Int = activePages.indexOf(page).coerceAtLeast(0)

    fun getCurrentPage(): Page = currentPage

    fun openDefaultHomePage(animated: Boolean = true) {
        scrollToPage(getDefaultPage(), animated)
    }

    private inline fun runWithAccessibilityTraversalSuppressed(
        container: ViewGroup,
        block: () -> Unit
    ) {
        val previousAccessibility = container.importantForAccessibility
        val previousContentCapture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            container.importantForContentCapture
        } else {
            null
        }

        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            container.importantForContentCapture =
                View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }

        try {
            block()
        } finally {
            try {
                container.post {
                    try {
                        container.importantForAccessibility = previousAccessibility
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && previousContentCapture != null) {
                            container.importantForContentCapture = previousContentCapture
                        }
                    } catch (e: Exception) {

                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}
