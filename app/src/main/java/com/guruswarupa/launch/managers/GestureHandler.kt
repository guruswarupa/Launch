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
    private val mainScrollView: View? = null
    private val initialPaddingLeft = mainScrollView?.paddingLeft ?: 0
    private val initialPaddingTop = mainScrollView?.paddingTop ?: 0
    private val initialPaddingRight = mainScrollView?.paddingRight ?: 0
    private val initialPaddingBottom = mainScrollView?.paddingBottom ?: 0

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isSwipeFromLeftEdge = false
    private var isSwipeFromRightEdge = false
    private var isGesturesEnabled = true

    private val edgeThresholdPx: Int
    private val minSwipeDistancePx: Int

    init {
        val density = activity.resources.displayMetrics.density
        edgeThresholdPx = (50 * density).toInt()
        minSwipeDistancePx = (50 * density).toInt()
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
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (mainScrollView ?: view).setPadding(
                initialPaddingLeft,
                initialPaddingTop + systemBars.top,
                initialPaddingRight,
                initialPaddingBottom
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
                    isSwipeFromLeftEdge || isSwipeFromRightEdge
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
                        false
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
