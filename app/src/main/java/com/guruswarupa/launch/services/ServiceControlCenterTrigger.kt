package com.guruswarupa.launch.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import java.lang.ref.WeakReference
import android.content.SharedPreferences
import android.content.Context.RECEIVER_EXPORTED
import androidx.core.content.ContextCompat
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.EdgePanelConfigActivity
import com.guruswarupa.launch.ui.activities.ScreenRecordPermissionActivity
import android.content.pm.ResolveInfo
import kotlin.math.abs

internal fun ScreenLockAccessibilityService.showControlCenterTrigger() {

    if (!isScreenOn || keyguardManager?.isKeyguardLocked == true) {
        return
    }

    if (controlCenterTriggerView != null) {
        controlCenterTriggerView?.let { view ->
            (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                applySavedControlCenterTriggerPosition(params)
                updateControlCenterTriggerPosition(params)
                applyControlCenterTriggerAppearance()
            }
        }
        return
    }

    val themedContext = ThemeManager.themedContext(this, R.style.Theme_Launch)
    controlCenterTriggerView = LayoutInflater.from(themedContext).inflate(R.layout.layout_control_center_trigger, null)

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    )
    applySavedControlCenterTriggerPosition(params)

    controlCenterTriggerView?.setOnTouchListener(object : View.OnTouchListener {
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var initialY = 0
        private var moved = false
        private val touchSlop = ViewConfiguration.get(this@showControlCenterTrigger).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialY = params.y
                    moved = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    persistControlCenterTriggerPosition(params)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val isLocked = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, false)
                    val dx = event.rawX - initialTouchX
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > touchSlop / 4 || abs(dy) > touchSlop / 4) {
                        moved = true
                    }

                    if (!isLocked && moved && abs(dy) > abs(dx) * 3.0) {
                        params.y = clampControlCenterTriggerY(initialY + dy, v)
                        updateControlCenterTriggerPosition(params)
                        return true
                    }

                    if (moved && abs(dx) > 30.dpToPx()) {
                        params.gravity = if (dx > 0) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
                        updateControlCenterTriggerPosition(params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    params.y = clampControlCenterTriggerY(params.y, v)
                    val dx = event.rawX - initialTouchX
                    val isLocked = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, false)
                    if (!isLocked && moved && abs(dx) > 30.dpToPx()) {
                        params.gravity = if (dx > 0) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
                    }
                    updateControlCenterTriggerPosition(params)
                    persistControlCenterTriggerPosition(params)

                    if (moved && isSwipeToOpenControlCenter(dx)) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        toggleControlCenter()
                    } else if (!moved) {

                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        toggleControlCenter()
                    }
                    return true
                }
            }
            return false
        }
    })

    controlCenterTriggerView?.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        updateControlCenterTriggerSystemGestureExclusion()
    }

    try {
        windowManager?.addView(controlCenterTriggerView, params)
        applyControlCenterTriggerAppearance()
    } catch (_: Exception) {}
}

internal fun ScreenLockAccessibilityService.removeControlCenterTrigger() {
    controlCenterTriggerView?.let {
        try {
            windowManager?.removeView(it)
        } catch (_: Exception) {}
        controlCenterTriggerView = null
    }
}

internal fun ScreenLockAccessibilityService.updateControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
    params.x = 0
    controlCenterTriggerView?.let { view ->
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }
}

internal fun ScreenLockAccessibilityService.applySavedControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
    val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    val side = prefs.getString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, "end") ?: "end"
    params.gravity = Gravity.TOP or if (side == "start") Gravity.START else Gravity.END
    params.x = 0
    params.y = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_Y, 180.dpToPx()).coerceIn(24.dpToPx(), (getScreenHeight() - 96.dpToPx()).coerceAtLeast(24.dpToPx()))
}

internal fun ScreenLockAccessibilityService.persistControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
    getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, if ((params.gravity and Gravity.START) == Gravity.START) "start" else "end")
        putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_Y, params.y)
        apply()
    }
}

internal fun ScreenLockAccessibilityService.applyControlCenterTriggerAppearance() {
    val triggerView = controlCenterTriggerView ?: return
    val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    val touchWidth = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_WIDTH_DP, 18).coerceIn(12, 36)
    val triggerHeight = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_HEIGHT_DP, 72).coerceIn(40, 112)
    val alphaPercent = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_ALPHA, 80).coerceIn(20, 100)
    val lineWidth = (touchWidth / 4).coerceIn(3, 8)
    val extraTouchPadding = 32.dpToPx()

    updateOverlayViewSize(
        view = triggerView,
        width = touchWidth.dpToPx() + extraTouchPadding,
        height = triggerHeight.dpToPx()
    )

    val lineView = triggerView.findViewById<View>(R.id.control_center_trigger_line)
    val lineLayoutParams = (lineView.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(
            lineWidth.dpToPx(),
            (triggerHeight - 16).coerceAtLeast(28).dpToPx()
        )
    lineLayoutParams.width = lineWidth.dpToPx()
    lineLayoutParams.height = (triggerHeight - 16).coerceAtLeast(28).dpToPx()
    lineLayoutParams.gravity = if (isControlCenterTriggerOnLeft()) {
        Gravity.START or Gravity.CENTER_VERTICAL
    } else {
        Gravity.END or Gravity.CENTER_VERTICAL
    }
    lineView.layoutParams = lineLayoutParams
    lineView.alpha = alphaPercent / 100f

    val background = (lineView.background?.mutate() as? GradientDrawable)
    background?.setColor(((alphaPercent * 255) / 100 shl 24) or 0x00FFFFFF)
    lineView.background = background
}

internal fun ScreenLockAccessibilityService.isControlCenterTriggerOnLeft(): Boolean {
    val gravity = (controlCenterTriggerView?.layoutParams as? WindowManager.LayoutParams)?.gravity ?: (Gravity.TOP or Gravity.END)
    return (gravity and Gravity.START) == Gravity.START
}

internal fun ScreenLockAccessibilityService.isSwipeToOpenControlCenter(dx: Float): Boolean {
    val threshold = 8.dpToPx().toFloat()
    return if (isControlCenterTriggerOnLeft()) dx > threshold else dx < -threshold
}

internal fun ScreenLockAccessibilityService.clampControlCenterTriggerY(candidate: Int, view: View): Int {
    val inset = 24.dpToPx()
    val viewHeight = view.height.takeIf { it > 0 } ?: 56.dpToPx()
    return candidate.coerceIn(inset, (getScreenHeight() - viewHeight - inset).coerceAtLeast(inset))
}

internal fun ScreenLockAccessibilityService.updateControlCenterTriggerSystemGestureExclusion() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        controlCenterTriggerView?.let { view ->
            val rects = listOf(Rect(0, 0, view.width, view.height))
            view.systemGestureExclusionRects = rects
        }
    }
}
