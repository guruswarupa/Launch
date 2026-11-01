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

internal fun ScreenLockAccessibilityService.showEdgeHandle() {

    if (!isScreenOn || keyguardManager?.isKeyguardLocked == true) {
        return
    }

    if (edgeHandleView != null) {
        edgeHandleView?.let { view ->
            (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                applySavedEdgeHandlePosition(params)
                updateEdgeHandlePosition(params)
                applyHandleAppearance()
            }
        }
        return
    }

    val themedContext = ThemeManager.themedContext(this, R.style.Theme_Launch)
    edgeHandleView = LayoutInflater.from(themedContext).inflate(R.layout.layout_edge_panel_handle, null)

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    )
    applySavedEdgeHandlePosition(params)

    edgeHandleView?.setOnTouchListener(object : View.OnTouchListener {
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var initialY = 0
        private var moved = false
        private var openedBySwipe = false
        private val touchSlop = ViewConfiguration.get(this@showEdgeHandle).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialY = params.y
                    moved = false
                    openedBySwipe = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    persistEdgeHandlePosition(params)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val isLocked = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(Constants.Prefs.EDGE_PANEL_HANDLE_LOCKED, false)
                    val dx = event.rawX - initialTouchX
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > touchSlop / 4 || abs(dy) > touchSlop / 4) {
                        moved = true
                    }

                    if (!isLocked && moved && abs(dy) > abs(dx) * 3.0) {
                        params.y = clampEdgeHandleY(initialY + dy, v)
                        updateEdgeHandlePosition(params)
                        if (isEdgePanelVisible) {
                            positionEdgePanelSheet()
                        }
                        return true
                    }

                    if (!openedBySwipe && isSwipeToOpen(dx)) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (!isLocked) {
                            params.gravity = if (dx > 0) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
                            updateEdgeHandlePosition(params)
                        }
                        persistEdgeHandlePosition(params)
                        showEdgePanel()
                        openedBySwipe = true
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    params.y = clampEdgeHandleY(params.y, v)
                    val dx = event.rawX - initialTouchX
                    val isLocked = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(Constants.Prefs.EDGE_PANEL_HANDLE_LOCKED, false)
                    if (!isLocked && moved && abs(dx) > 30.dpToPx()) {
                        params.gravity = if (dx > 0) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
                    }
                    updateEdgeHandlePosition(params)
                    persistEdgeHandlePosition(params)
                    if (!openedBySwipe && !moved) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        toggleEdgePanel()
                    }
                    return true
                }
            }
            return false
        }
    })

    edgeHandleView?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateSystemGestureExclusion()
    }

    try {
        windowManager?.addView(edgeHandleView, params)
        applyHandleAppearance()
    } catch (_: Exception) {}
}

internal fun ScreenLockAccessibilityService.updateSystemGestureExclusion() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        edgeHandleView?.let { view ->
            val rects = listOf(Rect(0, 0, view.width, view.height))
            view.systemGestureExclusionRects = rects
        }
    }
}

internal fun ScreenLockAccessibilityService.toggleEdgePanel() {
    if (isEdgePanelVisible) {
        hideEdgePanel()
    } else {
        showEdgePanel()
    }
}

@SuppressLint("ClickableViewAccessibility", "WrongConstant")
internal fun ScreenLockAccessibilityService.showEdgePanel() {
    if (edgePanelView != null) {
        return
    }

    try {
        val themedContext = ThemeManager.themedContext(this, R.style.Theme_Launch)
        val overlay = LayoutInflater.from(themedContext).inflate(R.layout.layout_edge_panel_overlay, null, false)
        edgePanelView = overlay

        val scrim = overlay.findViewById<View>(R.id.edge_panel_scrim)
        scrim?.setOnClickListener {
            hideEdgePanel()
        }

        scrim?.alpha = 0f

        val customizeButton = overlay.findViewById<ImageView>(R.id.edge_panel_customize_button)
        customizeButton?.setOnClickListener {
            try {
                startActivity(Intent(this, EdgePanelConfigActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
                hideEdgePanel()
            } catch (e: Exception) {
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(overlay, params)
        isEdgePanelVisible = true

        populateEdgePanel()
        positionEdgePanelSheet()

    } catch (e: Exception) {
        edgePanelView = null
        isEdgePanelVisible = false
    }
}

internal fun ScreenLockAccessibilityService.hideEdgePanel() {
    val overlay = edgePanelView ?: return

    try {

        val sheet = overlay.findViewById<View>(R.id.edge_panel_sheet)

        if (sheet != null) {
            val slideOut = if (isEdgeHandleOnLeft())
                -sheet.width.toFloat() - 16.dpToPx()
            else
                sheet.width.toFloat() + 16.dpToPx()

            sheet.animate()
                .translationX(slideOut)
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    cleanupEdgePanel(overlay)
                }
                .start()
        } else {
            cleanupEdgePanel(overlay)
        }
    } catch (e: Exception) {
        cleanupEdgePanel(overlay)
    }
}

internal fun ScreenLockAccessibilityService.cleanupEdgePanel(overlay: View) {
    try {
        windowManager?.removeView(overlay)
    } catch (e: Exception) {
    } finally {
        if (edgePanelView === overlay) {
            edgePanelView = null
            isEdgePanelVisible = false
        }
    }
}

internal fun ScreenLockAccessibilityService.removeEdgePanel() {
    edgeHandleView?.let {
        try {
            windowManager?.removeView(it)
        } catch (_: Exception) {}
        edgeHandleView = null
    }

    edgePanelView?.let {
        try {
            windowManager?.removeView(it)
        } catch (_: Exception) {}
        edgePanelView = null
    }
    isEdgePanelVisible = false
}

internal fun ScreenLockAccessibilityService.populateEdgePanel() {
    val overlay = edgePanelView ?: run {
        return
    }

    try {
        val recentContainer = overlay.findViewById<LinearLayout>(R.id.edge_panel_recent_container)
        val pinnedContainer = overlay.findViewById<LinearLayout>(R.id.edge_panel_pinned_container)
        val recentLabel = overlay.findViewById<TextView>(R.id.edge_panel_recent_label)
        val pinnedLabel = overlay.findViewById<TextView>(R.id.edge_panel_pinned_label)
        val recentEmpty = overlay.findViewById<TextView>(R.id.edge_panel_recent_empty)
        val usagePermissionButton = overlay.findViewById<ImageView>(R.id.edge_panel_usage_permission_button)

        if (recentContainer == null || pinnedContainer == null) {
            return
        }

        recentContainer.removeAllViews()
        pinnedContainer.removeAllViews()

        val showRecent = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Constants.Prefs.EDGE_PANEL_SHOW_RECENT, true)

        val pinnedPackages = getPinnedAppPackages()

        val recentPackages = if (showRecent) {
            getRecentAppPackages(limit = 9)
        } else {
            emptyList()
        }

        val recentApps = mutableListOf<ScreenLockAccessibilityService.EdgePanelAppEntry>()
        recentPackages.forEach { packageName ->
            if (!pinnedPackages.contains(packageName)) {
                resolveEdgePanelAppEntry(packageName)?.let { appInfo ->
                    recentApps.add(appInfo)
                }
            }
        }

        val pinnedApps = mutableListOf<ScreenLockAccessibilityService.EdgePanelAppEntry>()
        pinnedPackages.forEach { packageName ->
            resolveEdgePanelAppEntry(packageName)?.let { appInfo ->
                pinnedApps.add(appInfo)
            }
        }

        recentApps.forEach { entry ->
            val appView = createAppIconView(entry)
            if (appView != null) {
                recentContainer.addView(appView)
            }
        }

        pinnedApps.forEach { entry ->
            val appView = createAppIconView(entry)
            if (appView != null) {
                pinnedContainer.addView(appView)
            }
        }

        recentLabel?.visibility = if (recentApps.isNotEmpty()) View.VISIBLE else View.GONE
        recentContainer.visibility = if (recentApps.isNotEmpty()) View.VISIBLE else View.GONE
        pinnedLabel?.visibility = if (pinnedApps.isNotEmpty()) View.VISIBLE else View.GONE
        pinnedContainer.visibility = if (pinnedApps.isNotEmpty()) View.VISIBLE else View.GONE
        recentEmpty?.visibility = if (recentApps.isEmpty() && pinnedApps.isEmpty()) View.VISIBLE else View.GONE

        val hasUsageAccess = appUsageStatsManager.hasUsageStatsPermission()
        usagePermissionButton?.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
        usagePermissionButton?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                hideEdgePanel()
            } catch (e: Exception) {
            }
        }

    } catch (e: Exception) {
    }
}

internal fun ScreenLockAccessibilityService.resolveEdgePanelAppEntry(packageName: String): ScreenLockAccessibilityService.EdgePanelAppEntry? {
    return try {
        val resolveInfo = findLauncherResolveInfo(packageName)
        val label = resolveInfo?.loadLabel(packageManager)?.toString()
            ?: packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        val icon = loadEdgePanelIcon(packageName, resolveInfo) ?: run {
            return null
        }

        ScreenLockAccessibilityService.EdgePanelAppEntry(
            packageName = packageName,
            label = label,
            icon = icon
        )
    } catch (e: Exception) {
        null
    }
}

internal fun ScreenLockAccessibilityService.loadEdgePanelIcon(packageName: String, resolveInfo: ResolveInfo?): Drawable? {
    val rawIcon = try {
        resolveInfo?.loadIcon(packageManager)
            ?: packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    } ?: return null

    return rawIcon.toBitmapDrawable()
}

internal fun ScreenLockAccessibilityService.createAppIconView(entry: ScreenLockAccessibilityService.EdgePanelAppEntry): View? {
    return try {
        val view = LayoutInflater.from(this).inflate(R.layout.item_edge_panel_app, null, false)
        val iconView = view.findViewById<ImageView>(R.id.edge_panel_app_icon)

        iconView.setImageDrawable(entry.icon)
        iconView.visibility = View.VISIBLE

        view.setOnClickListener { clickView ->
            try {
                clickView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                launchEdgePanelApp(entry.packageName)
            } catch (e: Exception) {
            }
        }

        view.contentDescription = entry.label
        view
    } catch (e: Exception) {
        null
    }
}

internal fun ScreenLockAccessibilityService.launchEdgePanelApp(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent == null) {
        Toast.makeText(this, getString(R.string.cannot_launch_app), Toast.LENGTH_SHORT).show()
        return
    }

    try {
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        hideEdgePanel()
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.cannot_launch_app), Toast.LENGTH_SHORT).show()
    }
}

internal fun ScreenLockAccessibilityService.getPinnedAppPackages(): List<String> {
    return getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(Constants.Prefs.EDGE_PANEL_PINNED_APPS, null)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
}

internal fun ScreenLockAccessibilityService.getRecentAppPackages(limit: Int): List<String> {
    if (!appUsageStatsManager.hasUsageStatsPermission()) return emptyList()

    val endTime = System.currentTimeMillis()
    val startTime = endTime - (1000L * 60L * 60L * 24L)
    val events = usageStatsManager.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()
    val recentPackages = mutableListOf<String>()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
        val packageName = event.packageName ?: continue
        if (!isEligibleEdgePanelPackage(packageName)) continue

        recentPackages.remove(packageName)
        recentPackages.add(0, packageName)
    }

    return recentPackages.take(limit)
}

internal fun ScreenLockAccessibilityService.isEligibleEdgePanelPackage(packageName: String): Boolean {

    if (packageName == this.packageName ||
        packageName == "android" ||
        packageName.startsWith("com.android.systemui") ||
        packageName.startsWith("com.google.android.apps.nexuslauncher") ||
        packageName.startsWith("com.android.launcher3")) {
        return false
    }

    return try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)

        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (_: Exception) {
        false
    }
}

internal fun ScreenLockAccessibilityService.findLauncherResolveInfo(packageName: String): android.content.pm.ResolveInfo? {
    val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(packageName)
    }
    return try {
        packageManager.queryIntentActivities(launcherIntent, 0).firstOrNull()
    } catch (_: Exception) {
        null
    }
}

internal fun ScreenLockAccessibilityService.positionEdgePanelSheet() {
    val overlay = edgePanelView ?: return
    val sheet = overlay.findViewById<View>(R.id.edge_panel_sheet) ?: return
    val scrim = overlay.findViewById<View>(R.id.edge_panel_scrim)
    val handleParams = edgeHandleView?.layoutParams as? WindowManager.LayoutParams ?: return
    val handleHeight = edgeHandleView?.height?.takeIf { it > 0 } ?: 72.dpToPx()
    val screenHeight = getScreenHeight()
    val screenWidth = getScreenWidth()
    val bottomInset = getBottomSystemInset()

    val isLeft = isEdgeHandleOnLeft()

    val topMargin = 12.dpToPx()
    val bottomMargin = maxOf(40.dpToPx(), bottomInset + 24.dpToPx())
    val sideMargin = 16.dpToPx()
    val triggerSideSpacing = 80.dpToPx()

    val availableHeight = (screenHeight - topMargin - bottomMargin).coerceAtLeast(220.dpToPx())

    val layoutParams = (sheet.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)

    layoutParams.topMargin = topMargin
    layoutParams.bottomMargin = bottomMargin
    layoutParams.height = availableHeight
    layoutParams.marginStart = if (isLeft) sideMargin else 0
    layoutParams.marginEnd = if (isLeft) 0 else sideMargin
    layoutParams.gravity = if (isLeft) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
    sheet.layoutParams = layoutParams

    sheet.requestLayout()

    val slideDistance = if (isLeft) {
        (sideMargin + triggerSideSpacing).toFloat()
    } else {
        -(sideMargin + triggerSideSpacing).toFloat()
    }

    sheet.translationX = slideDistance
    sheet.alpha = 0f
    sheet.visibility = View.VISIBLE

    sheet.animate()
        .translationX(0f)
        .alpha(1f)
        .setDuration(120)
        .setInterpolator(DecelerateInterpolator(3.0f))
        .withStartAction {
        }
        .withEndAction {
        }
        .start()

}

internal fun ScreenLockAccessibilityService.updateEdgeHandlePosition(params: WindowManager.LayoutParams) {
    params.x = 0
    edgeHandleView?.let { view ->
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }
}

internal fun ScreenLockAccessibilityService.applySavedEdgeHandlePosition(params: WindowManager.LayoutParams) {
    val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    val side = prefs.getString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, "end") ?: "end"
    params.gravity = Gravity.TOP or if (side == "start") Gravity.START else Gravity.END
    params.x = 0
    params.y = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_Y, 180.dpToPx()).coerceIn(24.dpToPx(), (getScreenHeight() - 96.dpToPx()).coerceAtLeast(24.dpToPx()))
}

internal fun ScreenLockAccessibilityService.persistEdgeHandlePosition(params: WindowManager.LayoutParams) {
    getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, if ((params.gravity and Gravity.START) == Gravity.START) "start" else "end")
        putInt(Constants.Prefs.EDGE_PANEL_HANDLE_Y, params.y)
        apply()
    }
}

internal fun ScreenLockAccessibilityService.updateOverlayViewSize(view: View, width: Int, height: Int) {
    when (val layoutParams = view.layoutParams) {
        is WindowManager.LayoutParams -> {
            layoutParams.width = width
            layoutParams.height = height
            if (view.isAttachedToWindow) {
                try {
                    windowManager?.updateViewLayout(view, layoutParams)
                } catch (_: Exception) {}
            }
        }
        null -> {
            view.layoutParams = FrameLayout.LayoutParams(width, height)
        }
        else -> {
            layoutParams.width = width
            layoutParams.height = height
            view.layoutParams = layoutParams
        }
    }
}

internal fun ScreenLockAccessibilityService.applyHandleAppearance() {
    val handleView = edgeHandleView ?: return
    val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    val touchWidth = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_WIDTH_DP, 18).coerceIn(12, 36)
    val handleHeight = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_HEIGHT_DP, 72).coerceIn(40, 112)
    val alphaPercent = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_ALPHA, 80).coerceIn(20, 100)
    val lineWidth = (touchWidth / 4).coerceIn(3, 8)
    val extraTouchPadding = 32.dpToPx()

    updateOverlayViewSize(
        view = handleView,
        width = touchWidth.dpToPx() + extraTouchPadding,
        height = handleHeight.dpToPx()
    )

    val lineView = handleView.findViewById<View>(R.id.edge_handle_line)
    val lineLayoutParams = (lineView.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(
            lineWidth.dpToPx(),
            (handleHeight - 16).coerceAtLeast(28).dpToPx()
        )
    lineLayoutParams.width = lineWidth.dpToPx()
    lineLayoutParams.height = (handleHeight - 16).coerceAtLeast(28).dpToPx()
    lineLayoutParams.gravity = if (isEdgeHandleOnLeft()) {
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

internal fun ScreenLockAccessibilityService.isEdgeHandleOnLeft(): Boolean {
    val gravity = (edgeHandleView?.layoutParams as? WindowManager.LayoutParams)?.gravity ?: (Gravity.TOP or Gravity.END)
    return (gravity and Gravity.START) == Gravity.START
}

internal fun ScreenLockAccessibilityService.isSwipeToOpen(dx: Float): Boolean {
    val threshold = 8.dpToPx().toFloat()
    return if (isEdgeHandleOnLeft()) dx > threshold else dx < -threshold
}

internal fun ScreenLockAccessibilityService.clampEdgeHandleY(candidate: Int, view: View): Int {
    val inset = 24.dpToPx()
    val viewHeight = view.height.takeIf { it > 0 } ?: 56.dpToPx()
    return candidate.coerceIn(inset, (getScreenHeight() - viewHeight - inset).coerceAtLeast(inset))
}
