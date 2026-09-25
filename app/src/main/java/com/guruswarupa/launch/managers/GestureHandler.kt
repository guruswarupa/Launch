package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.guruswarupa.launch.R
import kotlin.math.abs




class GestureHandler(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val drawerLayout: DrawerLayout,
    private val mainContent: FrameLayout
) {
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isSwipeFromLeftEdge = false
    private var isSwipeFromRightEdge = false
    private var isSwipeUpCandidate = false
    private var isGesturesEnabled = true

    private val edgeThresholdPx: Int
    private val minSwipeDistancePx: Int
    private val minSwipeUpDistancePx: Int

    /** Fired on an upward swipe starting on blank home-screen space (not over any clickable view). */
    var onSwipeUpFromHome: (() -> Boolean)? = null

    init {
        val density = activity.resources.displayMetrics.density
        edgeThresholdPx = (50 * density).toInt()
        minSwipeDistancePx = (50 * density).toInt()
        minSwipeUpDistancePx = (24 * density).toInt()
    }




    @Suppress("unused")
    fun setGesturesEnabled(enabled: Boolean) {
        isGesturesEnabled = enabled
        if (!enabled) {

            isSwipeFromLeftEdge = false
            isSwipeFromRightEdge = false
        }
    }




    fun setupGestureExclusion() {
        // Pad main_content_stack (the actual interactive content), not mainContent itself:
        // mainContent also holds wallpaper_background/background_translucency_overlay, which
        // need to stay edge-to-edge under the status bar just like the other pages' own
        // backgrounds do. Padding mainContent directly would inset those too.
        val contentStack = mainContent.findViewById<View>(R.id.main_content_stack) ?: mainContent
        val initialLeft = contentStack.paddingLeft
        val initialTop = contentStack.paddingTop
        val initialRight = contentStack.paddingRight
        val initialBottom = contentStack.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentStack.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                updateGestureExclusion()
            }
            insets
        }
        ViewCompat.requestApplyInsets(mainContent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                updateGestureExclusion()
            }
        }
    }




    fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                val exclusionRects = if (isGesturesEnabled) {
                    listOf(

                        Rect(0, 0, mainContent.width / 2, mainContent.height / 2),

                        Rect(mainContent.width / 2, 0, mainContent.width, mainContent.height / 2)
                    )
                } else {

                    emptyList()
                }
                ViewCompat.setSystemGestureExclusionRects(mainContent, exclusionRects)
            }
        }
    }




    fun updateGestureExclusionForWidgetOpening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                val exclusionRects = listOf(
                    Rect(0, 0, mainContent.width, mainContent.height)
                )
                ViewCompat.setSystemGestureExclusionRects(mainContent, exclusionRects)
            }
        }
    }




    @SuppressLint("ClickableViewAccessibility")
    fun setupTouchListener() {
        mainContent.setOnTouchListener { v, event ->
            if (!isGesturesEnabled) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y

                    val screenWidth = v.width
                    val isLeftSide = event.x < screenWidth / 2
                    isSwipeFromLeftEdge = isLeftSide && event.y < (v.height / 2)
                    isSwipeFromRightEdge = !isLeftSide && event.y < (v.height / 2)
                    isSwipeUpCandidate = onSwipeUpFromHome != null && !isSwipeFromLeftEdge && !isSwipeFromRightEdge
                    isSwipeFromLeftEdge || isSwipeFromRightEdge || isSwipeUpCandidate
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSwipeFromLeftEdge) {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY

                        if (deltaX > 5 && abs(deltaY) < abs(deltaX) * 0.9) {
                            true
                        } else {
                            isSwipeFromLeftEdge = false
                            false
                        }
                    } else if (isSwipeFromRightEdge) {
                        val deltaX = touchStartX - event.x
                        val deltaY = event.y - touchStartY

                        if (deltaX > 5 && abs(deltaY) < abs(deltaX) * 0.9) {
                            true
                        } else {
                            isSwipeFromRightEdge = false
                            false
                        }
                    } else if (isSwipeUpCandidate) {
                        val deltaY = touchStartY - event.y
                        val deltaX = event.x - touchStartX

                        if (deltaY > minSwipeUpDistancePx && deltaY > abs(deltaX) * 1.2f) {
                            isSwipeUpCandidate = false
                            onSwipeUpFromHome?.invoke()
                            true
                        } else if (abs(deltaX) > minSwipeUpDistancePx && abs(deltaX) > abs(deltaY) * 1.2f) {
                            // Requiring the same minimum distance here (not just the ratio) as the
                            // swipe-up branch above stops a few pixels of natural jitter right at
                            // the start of a swipe from permanently declaring "this is horizontal"
                            // and handing the rest of the gesture to the page pager underneath -
                            // that's what was making a genuine swipe-up-to-open-drawer gesture
                            // occasionally page-swipe the home screen instead of opening the drawer.
                            isSwipeUpCandidate = false
                            false
                        } else {
                            true
                        }
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                    if (isSwipeFromLeftEdge) {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        if (deltaX > minSwipeDistancePx && abs(deltaY) < abs(deltaX) * 0.9) {
                            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {

                                openDrawerWithFastAnimation(GravityCompat.START)
                            }
                        }
                        isSwipeFromLeftEdge = false
                        true
                    } else if (isSwipeFromRightEdge) {
                        val deltaX = touchStartX - event.x
                        val deltaY = event.y - touchStartY
                        if (deltaX > minSwipeDistancePx && abs(deltaY) < abs(deltaX) * 0.9) {
                            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {

                                openDrawerWithFastAnimation(GravityCompat.END)
                            }
                        }
                        isSwipeFromRightEdge = false
                        true
                    } else {
                        val wasSwipeUpCandidate = isSwipeUpCandidate
                        isSwipeUpCandidate = false
                        wasSwipeUpCandidate
                    }
                }
                else -> false
            }
        }
    }




    private fun openDrawerWithFastAnimation(gravity: Int) {

        drawerLayout.openDrawer(gravity)







    }
}
