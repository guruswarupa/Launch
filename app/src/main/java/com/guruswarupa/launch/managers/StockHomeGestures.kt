package com.guruswarupa.launch.managers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import kotlin.math.abs

class StockHomeGestures(
    private val activity: FragmentActivity,
    private val lockDetector: GestureDetector
) {
    private val density = activity.resources.displayMetrics.density
    private val swipeDownThresholdPx = maxOf(ViewConfiguration.get(activity).scaledTouchSlop * 3f, 48 * density)
    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var fired = false
    private var downOnEmptyArea = false

    fun onEvent(event: MotionEvent, startsOnEmptyArea: Boolean): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                tracking = true
                fired = false
                downOnEmptyArea = startsOnEmptyArea
            }

            MotionEvent.ACTION_MOVE -> {
                if (tracking && !fired) {
                    val dx = abs(event.x - startX)
                    val dy = event.y - startY
                    if (dy > swipeDownThresholdPx && dy > dx * 1.5f) {
                        fired = true
                        tracking = false
                        openNotificationShade()
                    } else if (dx > swipeDownThresholdPx && dx > abs(dy)) {
                        tracking = false
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        if (downOnEmptyArea && !fired) {
            lockDetector.onTouchEvent(event)
        }
        return fired
    }

    private fun openNotificationShade() {
        val service = ScreenLockAccessibilityService.instance
        if (service != null && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) {
            return
        }
        try {
            val statusBar = activity.getSystemService("statusbar")
            statusBar?.javaClass?.getMethod("expandNotificationsPanel")?.invoke(statusBar)
            return
        } catch (_: Exception) {
        }
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_enable_accessibility_service_to_open_notifications),
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        fun launchConfiguredApp(
            context: Context,
            packageName: String,
            launch: (packageName: String, appName: String) -> Unit
        ): Boolean {
            if (packageName.isBlank()) return false
            val pm = context.packageManager
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) {
                return false
            }
            launch(packageName, label)
            return true
        }
    }
}
