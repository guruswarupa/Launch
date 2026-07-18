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
import android.util.Log
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
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.EdgePanelConfigActivity
import com.guruswarupa.launch.ui.activities.ScreenRecordPermissionActivity
import android.content.pm.ResolveInfo
import kotlin.math.abs

class ScreenLockAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ScreenLockAccessibility"
        private var _instance: WeakReference<ScreenLockAccessibilityService>? = null
        var instance: ScreenLockAccessibilityService?
            get() = _instance?.get()
            private set(value) {
                _instance = value?.let { WeakReference(it) }
            }

        const val DEFAULT_SHORTCUTS = "wifi,bluetooth,airplane,torch,data,rotation,sound,dnd,location,qr_scan,camera,screenshot,record,lock,power,hotspot,screen_timeout"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var edgeHandleView: View? = null
    private var controlCenterTriggerView: View? = null
    private var edgePanelView: View? = null
    private var shortcutMenu: View? = null
    private var isMenuVisible = false
    private var isEdgePanelVisible = false
    private var isScreenOn = true
    private var keyguardManager: KeyguardManager? = null

    private val wifiManager by lazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val bluetoothAdapter by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    private val appUsageStatsManager by lazy { AppUsageStatsManager(this) }

    private var isTorchOn = false
    private lateinit var focusModeManager: FocusModeManager
    private lateinit var sharedPreferences: SharedPreferences
    
    private var isFocusModeReceiverRegistered = false
    private var isSettingsReceiverRegistered = false
    private var isScreenReceiverRegistered = false
    
    private val focusModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.FOCUS_MODE_CHANGED") {

            }
        }
    }
    private val torchCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                isTorchOn = enabled
            }
        }
    } else null
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                refreshOverlayState()
                if (isEdgePanelVisible) {
                    populateEdgePanel()
                }
            }
        }
    }

    private var screenReceiver: BroadcastReceiver? = null

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        startUnlockMonitor()

                        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                        if (prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)) {
                            showControlCenterTrigger()
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        removeEdgePanel()

                        removeControlCenterTrigger()
                        stopUnlockMonitor()
                    }
                    Intent.ACTION_USER_PRESENT -> {

                        stopUnlockMonitor()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isScreenReceiverRegistered = true
    }

    private var unlockMonitorHandler: android.os.Handler? = null
    private var unlockMonitorRunnable: Runnable? = null

    private fun startUnlockMonitor() {
        stopUnlockMonitor()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        unlockMonitorHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                val isLocked = keyguardManager?.isKeyguardLocked

                if (isLocked == false) {
                    showEdgeHandleAfterUnlock()
                    showControlCenterTriggerAfterUnlock()
                } else {
                    handler.postDelayed(this, 500)
                }
            }
        }
        unlockMonitorRunnable = runnable
        handler.post(runnable)
    }

    private fun stopUnlockMonitor() {
        unlockMonitorHandler?.removeCallbacksAndMessages(null)
        unlockMonitorHandler = null
        unlockMonitorRunnable = null
    }

    private fun showEdgeHandleAfterUnlock() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, false)
            if (isEnabled) {
                showEdgeHandle()
            }
        }, 500)
    }

    private fun showControlCenterTriggerAfterUnlock() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)
            if (isEnabled) {
                showControlCenterTrigger()
            }
        }, 500)
    }


    private data class EdgePanelAppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    override fun onCreate() {
        super.onCreate()
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        registerScreenReceiver()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && torchCallback != null) {
            try {
                cameraManager.registerTorchCallback(torchCallback, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register torch callback", e)
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(focusModeReceiver, IntentFilter("com.guruswarupa.launch.FOCUS_MODE_CHANGED"), RECEIVER_EXPORTED)
            isFocusModeReceiverRegistered = true
            registerReceiver(settingsReceiver, IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED"), RECEIVER_EXPORTED)
            isSettingsReceiverRegistered = true
        } else {
            registerReceiver(focusModeReceiver, IntentFilter("com.guruswarupa.launch.FOCUS_MODE_CHANGED"))
            isFocusModeReceiverRegistered = true
            registerReceiver(settingsReceiver, IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED"))
            isSettingsReceiverRegistered = true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        focusModeManager = FocusModeManager(this, getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE))
        sharedPreferences = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshOverlayState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "TOGGLE_SHORTCUT") {
            val enabled = intent.getBooleanExtra("enabled", false)
            if (enabled) {
                showFloatingButton()
            } else {
                removeFloatingButton()
            }
        } else if (action == "TOGGLE_EDGE_PANEL") {
            val enabled = intent.getBooleanExtra("enabled", false)
            if (enabled) {
                showEdgeHandle()
                applyHandleAppearance()
            } else {
                removeEdgePanel()
            }
        } else if (action == "TOGGLE_CONTROL_CENTER_TRIGGER") {
            val enabled = intent.getBooleanExtra("enabled", false)
            if (enabled) {
                showControlCenterTrigger()
                applyControlCenterTriggerAppearance()
            } else {
                removeControlCenterTrigger()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun refreshOverlayState() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.Prefs.ACCESSIBILITY_SHORTCUT_ENABLED, false)) {
            showFloatingButton()
        } else {
            removeFloatingButton()
        }

        if (prefs.getBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, false)) {
            showEdgeHandle()
            applyHandleAppearance()
        } else {
            removeEdgePanel()
        }

        if (prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)) {
            showControlCenterTrigger()
            applyControlCenterTriggerAppearance()
        } else {
            removeControlCenterTrigger()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (floatingView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_accessibility_shortcut_trigger, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0.0f
            private var initialTouchY: Float = 0.0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)


                        if (!isMoving) {
                            toggleMenu()
                        }

                        snapToEdge(params)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(floatingView, params)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                floatingView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                snapToEdge(params)
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.bounds?.let {
                displayMetrics.widthPixels = it.width()
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }

        val screenWidth = displayMetrics.widthPixels
        val viewWidth = floatingView?.measuredWidth ?: 0
        if (viewWidth == 0) return

        val visibleEdgeWidth = (resources.displayMetrics.density * 24).toInt()

        if (params.x + viewWidth / 2 < screenWidth / 2) {
            params.x = -(viewWidth - visibleEdgeWidth)
        } else {
            params.x = screenWidth - visibleEdgeWidth
        }

        try {
            windowManager?.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
    }

    private fun removeFloatingButton() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
        hideMenu()
    }

    private fun showEdgeHandle() {

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

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
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
            private val touchSlop = ViewConfiguration.get(this@ScreenLockAccessibilityService).scaledTouchSlop

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

    private fun updateSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            edgeHandleView?.let { view ->
                val rects = listOf(Rect(0, 0, view.width, view.height))
                view.systemGestureExclusionRects = rects
            }
        }
    }

    private fun toggleEdgePanel() {
        if (isEdgePanelVisible) {
            hideEdgePanel()
        } else {
            showEdgePanel()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "WrongConstant")
    private fun showEdgePanel() {
        if (edgePanelView != null) {
            return
        }

        try {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
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
                    Log.e("EdgePanel", "Error opening config: ${e.message}")
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
            Log.e("EdgePanel", "Error showing panel: ${e.message}", e)
            edgePanelView = null
            isEdgePanelVisible = false
        }
    }

    private fun hideEdgePanel() {
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
            Log.e("EdgePanel", "Error hiding panel: ${e.message}")
            cleanupEdgePanel(overlay)
        }
    }

    private fun cleanupEdgePanel(overlay: View) {
        try {
            windowManager?.removeView(overlay)
        } catch (e: Exception) {
            Log.e("EdgePanel", "Error removing panel: ${e.message}")
        } finally {
            if (edgePanelView === overlay) {
                edgePanelView = null
                isEdgePanelVisible = false
            }
        }
    }

    private fun removeEdgePanel() {
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

    private fun showControlCenterTrigger() {

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

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
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
            private val touchSlop = ViewConfiguration.get(this@ScreenLockAccessibilityService).scaledTouchSlop

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

    private fun removeControlCenterTrigger() {
        controlCenterTriggerView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            controlCenterTriggerView = null
        }
    }

    private fun populateEdgePanel() {
        val overlay = edgePanelView ?: run {
            Log.w("EdgePanel", "populateEdgePanel called but overlay is null")
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
                Log.e("EdgePanel", "Containers not found in layout")
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

            val recentApps = mutableListOf<EdgePanelAppEntry>()
            recentPackages.forEach { packageName ->
                if (!pinnedPackages.contains(packageName)) {
                    resolveEdgePanelAppEntry(packageName)?.let { appInfo ->
                        recentApps.add(appInfo)
                    } ?: Log.w("EdgePanel", "Recent app not found: $packageName")
                }
            }


            val pinnedApps = mutableListOf<EdgePanelAppEntry>()
            pinnedPackages.forEach { packageName ->
                resolveEdgePanelAppEntry(packageName)?.let { appInfo ->
                    pinnedApps.add(appInfo)
                } ?: Log.w("EdgePanel", "Pinned app not found: $packageName")
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
                    Log.e("EdgePanel", "Error opening usage settings: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e("EdgePanel", "Error populating panel: ${e.message}", e)
        }
    }

    private fun resolveEdgePanelAppEntry(packageName: String): EdgePanelAppEntry? {
        return try {
            val resolveInfo = findLauncherResolveInfo(packageName)
            val label = resolveInfo?.loadLabel(packageManager)?.toString()
                ?: packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            val icon = loadEdgePanelIcon(packageName, resolveInfo) ?: run {
                Log.w("EdgePanel", "Skipping app without icon: $packageName")
                return null
            }

            EdgePanelAppEntry(
                packageName = packageName,
                label = label,
                icon = icon
            )
        } catch (e: Exception) {
            Log.w("EdgePanel", "Error resolving app entry for $packageName: ${e.message}")
            null
        }
    }

    private fun loadEdgePanelIcon(packageName: String, resolveInfo: ResolveInfo?): Drawable? {
        val rawIcon = try {
            resolveInfo?.loadIcon(packageManager)
                ?: packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w("EdgePanel", "Failed to load icon for $packageName: ${e.message}")
            null
        } ?: return null

        return rawIcon.toBitmapDrawable()
    }

    private fun Drawable.toBitmapDrawable(): Drawable {
        if (this is BitmapDrawable && bitmap != null) {
            return this
        }

        val width = intrinsicWidth.takeIf { it > 0 } ?: 96.dpToPx()
        val height = intrinsicHeight.takeIf { it > 0 } ?: 96.dpToPx()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val copy = constantState?.newDrawable(resources)?.mutate() ?: mutate()
        copy.setBounds(0, 0, canvas.width, canvas.height)
        copy.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    private fun createAppIconView(entry: EdgePanelAppEntry): View? {
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
                    Log.e("EdgePanel", "Error launching app: ${e.message}")
                }
            }

            view.contentDescription = entry.label
            view
        } catch (e: Exception) {
            Log.e("EdgePanel", "Error creating app view for ${entry.label}: ${e.message}")
            null
        }
    }

    private fun launchEdgePanelApp(packageName: String) {
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

    private fun getPinnedAppPackages(): List<String> {
        return getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(Constants.Prefs.EDGE_PANEL_PINNED_APPS, null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private fun getRecentAppPackages(limit: Int): List<String> {
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

    private fun isEligibleEdgePanelPackage(packageName: String): Boolean {

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

    private fun findLauncherResolveInfo(packageName: String): android.content.pm.ResolveInfo? {
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

    private fun positionEdgePanelSheet() {
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

    private fun updateEdgeHandlePosition(params: WindowManager.LayoutParams) {
        params.x = 0
        edgeHandleView?.let { view ->
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun applySavedEdgeHandlePosition(params: WindowManager.LayoutParams) {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val side = prefs.getString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, "end") ?: "end"
        params.gravity = Gravity.TOP or if (side == "start") Gravity.START else Gravity.END
        params.x = 0
        params.y = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_Y, 180.dpToPx()).coerceIn(24.dpToPx(), (getScreenHeight() - 96.dpToPx()).coerceAtLeast(24.dpToPx()))
    }

    private fun persistEdgeHandlePosition(params: WindowManager.LayoutParams) {
        getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, if ((params.gravity and Gravity.START) == Gravity.START) "start" else "end")
            putInt(Constants.Prefs.EDGE_PANEL_HANDLE_Y, params.y)
            apply()
        }
    }

    private fun updateOverlayViewSize(view: View, width: Int, height: Int) {
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

    private fun applyHandleAppearance() {
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

    private fun isEdgeHandleOnLeft(): Boolean {
        val gravity = (edgeHandleView?.layoutParams as? WindowManager.LayoutParams)?.gravity ?: (Gravity.TOP or Gravity.END)
        return (gravity and Gravity.START) == Gravity.START
    }

    private fun isSwipeToOpen(dx: Float): Boolean {
        val threshold = 8.dpToPx().toFloat()
        return if (isEdgeHandleOnLeft()) dx > threshold else dx < -threshold
    }

    private fun clampEdgeHandleY(candidate: Int, view: View): Int {
        val inset = 24.dpToPx()
        val viewHeight = view.height.takeIf { it > 0 } ?: 56.dpToPx()
        return candidate.coerceIn(inset, (getScreenHeight() - viewHeight - inset).coerceAtLeast(inset))
    }

    private fun updateControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
        params.x = 0
        controlCenterTriggerView?.let { view ->
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun applySavedControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val side = prefs.getString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, "end") ?: "end"
        params.gravity = Gravity.TOP or if (side == "start") Gravity.START else Gravity.END
        params.x = 0
        params.y = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_Y, 180.dpToPx()).coerceIn(24.dpToPx(), (getScreenHeight() - 96.dpToPx()).coerceAtLeast(24.dpToPx()))
    }

    private fun persistControlCenterTriggerPosition(params: WindowManager.LayoutParams) {
        getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, if ((params.gravity and Gravity.START) == Gravity.START) "start" else "end")
            putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_Y, params.y)
            apply()
        }
    }

    private fun applyControlCenterTriggerAppearance() {
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

    private fun isControlCenterTriggerOnLeft(): Boolean {
        val gravity = (controlCenterTriggerView?.layoutParams as? WindowManager.LayoutParams)?.gravity ?: (Gravity.TOP or Gravity.END)
        return (gravity and Gravity.START) == Gravity.START
    }

    private fun isSwipeToOpenControlCenter(dx: Float): Boolean {
        val threshold = 8.dpToPx().toFloat()
        return if (isControlCenterTriggerOnLeft()) dx > threshold else dx < -threshold
    }

    private fun clampControlCenterTriggerY(candidate: Int, view: View): Int {
        val inset = 24.dpToPx()
        val viewHeight = view.height.takeIf { it > 0 } ?: 56.dpToPx()
        return candidate.coerceIn(inset, (getScreenHeight() - viewHeight - inset).coerceAtLeast(inset))
    }

    private fun updateControlCenterTriggerSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            controlCenterTriggerView?.let { view ->
                val rects = listOf(Rect(0, 0, view.width, view.height))
                view.systemGestureExclusionRects = rects
            }
        }
    }

    private fun getScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { displayMetrics.widthPixels = it.width() }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }
        return displayMetrics.widthPixels
    }

    private fun getScreenHeight(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { displayMetrics.heightPixels = it.height() }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }
        return displayMetrics.heightPixels
    }

    private fun getBottomSystemInset(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return windowManager?.currentWindowMetrics
            ?.windowInsets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            ?.bottom ?: 0
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun toggleControlCenter() {

        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        if (shortcutMenu != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
        val menu = LayoutInflater.from(themedContext).inflate(R.layout.layout_accessibility_shortcut, null)
        shortcutMenu = menu
        setupMenuListeners(menu)

        val params = WindowManager.LayoutParams(
            getScreenWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER


        val menuContainer = shortcutMenu?.findViewById<View>(R.id.menu_container)
        val screenWidth = getScreenWidth()
        val targetWidth = (screenWidth * 0.8).toInt()
        menuContainer?.layoutParams = menuContainer?.layoutParams?.apply {
            width = targetWidth
        }


        shortcutMenu?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideMenu()
                true
            } else {
                false
            }
        }

        try {
            windowManager?.addView(shortcutMenu, params)
            isMenuVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideMenu() {
        shortcutMenu?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            shortcutMenu = null
        }
        isMenuVisible = false
    }

    private fun setupMenuListeners(view: View) {
        val menuContainer = view.findViewById<View>(R.id.menu_container)
        menuContainer?.setOnClickListener {  }


        val brightnessSeekBar = view.findViewById<SeekBar>(R.id.brightness_seekbar)
        if (Settings.System.canWrite(this)) {
            try {
                val currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                brightnessSeekBar?.progress = currentBrightness
            } catch (_: Exception) {
                brightnessSeekBar?.progress = 125
            }

            brightnessSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                        } catch (_: Exception) {
                            requestWriteSettingsPermission()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            brightnessSeekBar?.alpha = 0.5f
            brightnessSeekBar?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    requestWriteSettingsPermission()
                }
                true
            }
        }


        val volumeMediaSeekBar = view.findViewById<SeekBar>(R.id.volume_media_seekbar)
        val imgVolumeMedia = view.findViewById<ImageView>(R.id.img_volume_media_icon)
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeMediaSeekBar?.max = maxVolume
            volumeMediaSeekBar?.progress = currentVolume

            volumeMediaSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        safeSetVolume(AudioManager.STREAM_MUSIC, progress)
                        if (progress == 0) {
                            imgVolumeMedia?.setImageResource(R.drawable.ic_muted_stat)
                        } else {
                            imgVolumeMedia?.setImageResource(R.drawable.ic_volume_up_stat)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (_: Exception) {}


        val volumeRingSeekBar = view.findViewById<SeekBar>(R.id.volume_ring_seekbar)
        val imgVolumeRing = view.findViewById<ImageView>(R.id.img_volume_ring_icon)
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            volumeRingSeekBar?.max = maxVolume
            volumeRingSeekBar?.progress = currentVolume

            volumeRingSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        safeSetVolume(AudioManager.STREAM_RING, progress)
                        if (progress == 0) {
                            imgVolumeRing?.setImageResource(R.drawable.ic_muted_stat)
                        } else {
                            imgVolumeRing?.setImageResource(R.drawable.ic_vibrate_stat)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (_: Exception) {}


        val volumeAlarmSeekBar = view.findViewById<SeekBar>(R.id.volume_alarm_seekbar)
        val imgVolumeAlarm = view.findViewById<ImageView>(R.id.img_volume_alarm_icon)
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            volumeAlarmSeekBar?.max = maxVolume
            volumeAlarmSeekBar?.progress = currentVolume

            volumeAlarmSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        safeSetVolume(AudioManager.STREAM_ALARM, progress)
                        if (progress == 0) {
                            imgVolumeAlarm?.setImageResource(R.drawable.ic_muted_stat)
                        } else {
                            imgVolumeAlarm?.setImageResource(android.R.drawable.ic_lock_silent_mode)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (_: Exception) {}


        val linearLayout = view.findViewById<LinearLayout>(R.id.shortcuts_linear_layout)
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val shortcutList = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, DEFAULT_SHORTCUTS)
            ?.split(",") ?: DEFAULT_SHORTCUTS.split(",")

        linearLayout?.removeAllViews()


        val itemWidth = 64.dpToPx()
        val itemMargin = 8.dpToPx()

        for (id in shortcutList) {

            val shortcutContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = itemMargin
                    marginEnd = itemMargin
                }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
            }


            val iconSlot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(56.dpToPx(), 56.dpToPx()).apply {
                    gravity = Gravity.CENTER
                }
                background = resources.getDrawable(R.drawable.bg_edge_panel_icon_slot, theme)
            }


            val icon = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams((56.dpToPx() * 0.6).toInt(), (56.dpToPx() * 0.6).toInt()).apply {
                    gravity = Gravity.CENTER
                }
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            }


            val slotContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
                addView(iconSlot)
                addView(icon)
            }
            shortcutContainer.addView(slotContainer)


            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    topMargin = 4.dpToPx()
                }
                textSize = 10f
                setTextColor(android.graphics.Color.WHITE)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            shortcutContainer.addView(label)

            when (id) {
                "wifi" -> {
                    icon.setImageResource(R.drawable.ic_wifi_stat)
                    icon.contentDescription = "WiFi"
                    label.text = "WiFi"
                    updateWifiIcon(icon)
                    shortcutContainer.setOnClickListener { toggleWifi(); updateWifiIcon(icon); hideMenu() }
                }
                "bluetooth" -> {
                    icon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
                    icon.contentDescription = "Bluetooth"
                    label.text = "Bluetooth"
                    updateBluetoothIcon(icon)
                    shortcutContainer.setOnClickListener { toggleBluetooth(); hideMenu() }
                }
                "airplane" -> {
                    icon.setImageResource(R.drawable.ic_airplane_stat)
                    icon.contentDescription = "Airplane"
                    label.text = "Airplane"
                    updateAirplaneIcon(icon)
                    shortcutContainer.setOnClickListener { toggleAirplaneMode(); hideMenu() }
                }
                "torch" -> {
                    icon.setImageResource(R.drawable.ic_torch_stat)
                    icon.contentDescription = "Torch"
                    label.text = "Torch"
                    updateTorchIcon(icon)
                    shortcutContainer.setOnClickListener { toggleTorch(); updateTorchIcon(icon) }
                }
                "data" -> {
                    icon.setImageResource(R.drawable.ic_mobile_data_stat)
                    icon.contentDescription = "Data"
                    label.text = "Data"
                    shortcutContainer.setOnClickListener { toggleMobileData() }
                }
                "rotation" -> {
                    icon.setImageResource(R.drawable.ic_rotation_stat)
                    icon.contentDescription = "Auto Rotation"
                    label.text = "Rotation"
                    updateRotationIcon(icon)
                    shortcutContainer.setOnClickListener { toggleAutoRotation(); updateRotationIcon(icon) }
                }
                "sound" -> {
                    icon.contentDescription = "Sound Mode"
                    label.text = "Sound"
                    updateSoundIconOnly(icon)
                    shortcutContainer.setOnClickListener {
                        try {
                            val nextMode = when (audioManager.ringerMode) {
                                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                                else -> AudioManager.RINGER_MODE_NORMAL
                            }
                            audioManager.ringerMode = nextMode
                            updateSoundIconOnly(icon)
                            volumeMediaSeekBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            volumeRingSeekBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                            volumeAlarmSeekBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                        } catch (e: SecurityException) {
                            requestDndPermission()
                        }
                    }
                }
                "dnd" -> {
                    icon.setImageResource(R.drawable.ic_focus_mode_icon)
                    icon.contentDescription = "Do Not Disturb"
                    label.text = "DND"
                    updateDndIconOnly(icon)
                    shortcutContainer.setOnClickListener { toggleDndOnly(icon) }
                }
                "location" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_mylocation)
                    icon.contentDescription = "Location"
                    label.text = "Location"
                    shortcutContainer.setOnClickListener {
                        try {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            Log.e("ScreenLock", "Error opening location settings: ${e.message}")
                        }
                        hideMenu()
                    }
                }
                "qr_scan" -> {
                    icon.setImageResource(R.drawable.ic_qr_scan_stat)
                    icon.contentDescription = "QR Scan"
                    label.text = "QR Scan"
                    shortcutContainer.setOnClickListener { launchQrScanner(); hideMenu() }
                }
                "camera" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_camera)
                    icon.contentDescription = "Camera"
                    label.text = "Camera"
                    shortcutContainer.setOnClickListener {
                        startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        hideMenu()
                    }
                }
                "screenshot" -> {
                    icon.setImageResource(R.drawable.ic_screenshot_stat)
                    icon.contentDescription = "Screenshot"
                    label.text = "Screenshot"
                    shortcutContainer.setOnClickListener { takeScreenshot(); hideMenu() }
                }
                "record" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_slideshow)
                    icon.contentDescription = "Screen Record"
                    label.text = "Record"
                    if (ScreenRecordingService.isRunning) icon.setColorFilter(0xFFF7768E.toInt())
                    shortcutContainer.setOnClickListener {
                        if (ScreenRecordingService.isRunning) {
                            ScreenRecordingService.stopService(this)
                        } else {
                            startActivity(Intent(this, ScreenRecordPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        hideMenu()
                    }
                }
                "lock" -> {
                    icon.setImageResource(android.R.drawable.ic_lock_idle_lock)
                    icon.contentDescription = "Lock"
                    label.text = "Lock"
                    shortcutContainer.setOnClickListener { lockScreen(); hideMenu() }
                }
                "power" -> {
                    icon.setImageResource(android.R.drawable.ic_lock_power_off)
                    icon.setColorFilter(0xFFF7768E.toInt())
                    icon.contentDescription = "Power"
                    label.text = "Power"
                    shortcutContainer.setOnClickListener { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); hideMenu() }
                }
                "hotspot" -> {
                    icon.setImageResource(R.drawable.ic_wifi_stat)
                    icon.contentDescription = "Hotspot"
                    label.text = "Hotspot"
                    updateHotspotIcon(icon)
                    shortcutContainer.setOnClickListener { toggleHotspot() }
                }
                "screen_timeout" -> {
                    icon.setImageResource(R.drawable.ic_settings_icon)
                    icon.contentDescription = "Screen Timeout"
                    label.text = "Timeout"
                    updateScreenTimeoutIconOnly(icon)
                    shortcutContainer.setOnClickListener { cycleScreenTimeout(); updateScreenTimeoutIconOnly(icon); hideMenu() }
                }
                else -> continue
            }
            linearLayout?.addView(shortcutContainer)
        }
    }

    private fun safeSetVolume(streamType: Int, progress: Int) {
        try {
            audioManager.setStreamVolume(streamType, progress, 0)
        } catch (e: SecurityException) {
            requestDndPermission()
        }
    }

    private fun requestDndPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Toast.makeText(this, "DND Access required to change volume", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    private fun launchQrScanner() {
        val qrIntents = mutableListOf<Intent>()


        qrIntents.add(Intent("com.google.android.googlequicksearchbox.GOOGLE_LENS").apply {
            setPackage("com.google.android.googlequicksearchbox")
        })


        val lensIntent = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
        if (lensIntent != null) qrIntents.add(lensIntent)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            qrIntents.add(Intent("android.settings.QR_CODE_SCANNER"))
        }
        
        
        qrIntents.add(Intent(Intent.ACTION_VIEW, Uri.parse("googlelens://v1/scan")))
        
        
        qrIntents.add(Intent("com.google.zxing.client.android.SCAN"))


        qrIntents.add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            putExtra("android.intent.extra.USE_QR_CODE", true)
        })

        var started = false
        for (intent in qrIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                started = true
                break
            } catch (_: Exception) {}
        }
        if (!started) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, "No QR scanner or camera found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleWifi() {
        try {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updateWifiIcon(imageView: ImageView?) {
        val enabled = wifiManager.isWifiEnabled
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    @SuppressLint("MissingPermission")
    private fun toggleBluetooth() {
        try {
            if (bluetoothAdapter.isEnabled) bluetoothAdapter.disable() else bluetoothAdapter.enable()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateBluetoothIcon(imageView: ImageView?) {
        try {
            val enabled = bluetoothAdapter.isEnabled
            imageView?.alpha = if (enabled) 1.0f else 0.4f
        } catch (e: SecurityException) {

            imageView?.alpha = 0.4f
        } catch (e: Exception) {
            imageView?.alpha = 0.4f
        }
    }

    private fun toggleAirplaneMode() {
        try {
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e("ScreenLock", "Error opening airplane mode settings: ${e.message}")
        }
    }

    private fun updateAirplaneIcon(imageView: ImageView?) {
        val enabled = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun toggleTorch() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Toast.makeText(this, "Torch not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTorchIcon(imageView: ImageView?) {
        imageView?.alpha = if (isTorchOn) 1.0f else 0.4f
    }

    private fun toggleAutoRotation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
                return
            }
        }
        try {
            val isAutoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            val newValue = if (isAutoRotate) 0 else 1
            val success = Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, newValue)
            if (success && newValue == 0) {
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            }
            Toast.makeText(this, if (newValue == 1) "Auto-rotate ON" else "Portrait Locked", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun updateRotationIcon(imageView: ImageView?) {
        try {
            val isAutoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            imageView?.alpha = if (isAutoRotate) 1.0f else 0.4f
        } catch (_: Exception) {}
    }

    private fun updateSoundIcon(imageView: ImageView?, textView: TextView?) {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                imageView?.setImageResource(R.drawable.ic_volume_up_stat)
                imageView?.alpha = 1.0f
                textView?.text = "Sound"
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                imageView?.setImageResource(R.drawable.ic_vibrate_stat)
                imageView?.alpha = 1.0f
                textView?.text = "Vibrate"
            }
            AudioManager.RINGER_MODE_SILENT -> {
                imageView?.setImageResource(android.R.drawable.ic_lock_silent_mode)
                imageView?.alpha = 0.5f
                textView?.text = "Muted"
            }
        }
    }

    private fun updateSoundIconOnly(imageView: ImageView?) {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                imageView?.setImageResource(R.drawable.ic_volume_up_stat)
                imageView?.alpha = 1.0f
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                imageView?.setImageResource(R.drawable.ic_vibrate_stat)
                imageView?.alpha = 1.0f
            }
            AudioManager.RINGER_MODE_SILENT -> {
                imageView?.setImageResource(android.R.drawable.ic_lock_silent_mode)
                imageView?.alpha = 0.5f
            }
        }
    }

    private fun toggleDnd(icon: ImageView?, label: TextView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val isOff = currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                            currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN

                val newFilter = if (isOff) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                else NotificationManager.INTERRUPTION_FILTER_ALL

                notificationManager.setInterruptionFilter(newFilter)


                val enabled = isOff
                icon?.alpha = if (enabled) 1.0f else 0.4f
                label?.text = if (enabled) "DND On" else "DND Off"
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun toggleDndOnly(icon: ImageView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val isOff = currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                            currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN

                val newFilter = if (isOff) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                else NotificationManager.INTERRUPTION_FILTER_ALL

                notificationManager.setInterruptionFilter(newFilter)


                updateDndIconOnly(icon)
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun updateDndIcon(imageView: ImageView?, textView: TextView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val filter = notificationManager.currentInterruptionFilter
            val enabled = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                         filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

            imageView?.setImageResource(R.drawable.ic_focus_mode_icon)
            imageView?.alpha = if (enabled) 1.0f else 0.4f
            textView?.text = if (enabled) "DND On" else "DND Off"
        }
    }

    private fun updateDndIconOnly(imageView: ImageView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val filter = notificationManager.currentInterruptionFilter
            val enabled = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                         filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

            imageView?.setImageResource(R.drawable.ic_focus_mode_icon)
            imageView?.alpha = if (enabled) 1.0f else 0.4f
        }
    }

    private fun toggleMobileData() {
        val intent = Intent().apply {
            action = "android.settings.NETWORK_OPERATOR_SETTINGS"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            hideMenu()
        } catch (e: Exception) {
            try {
                intent.action = Settings.ACTION_DATA_ROAMING_SETTINGS
                startActivity(intent)
                hideMenu()
            } catch (e2: Exception) {
                try {
                    intent.action = "android.settings.DATA_USAGE_SETTINGS"
                    startActivity(intent)
                    hideMenu()
                } catch (_: Exception) {
                    Toast.makeText(this, "Mobile data settings not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleHotspot() {
        val intent = Intent().apply {
            action = "android.settings.TETHER_SETTINGS"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            hideMenu()
        } catch (e: Exception) {
            try {

                intent.action = "android.settings.WIFI_TETHER_SETTINGS"
                startActivity(intent)
                hideMenu()
            } catch (e2: Exception) {
                try {
                    intent.action = Settings.ACTION_WIRELESS_SETTINGS
                    startActivity(intent)
                    hideMenu()
                } catch (_: Exception) {
                    Toast.makeText(this, "Hotspot settings not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateHotspotIcon(imageView: ImageView?) {

        imageView?.alpha = 1.0f
    }

    private fun cycleScreenTimeout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
                return
            }
        }

        try {
            val currentTimeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            val timeouts = arrayOf(15000, 30000, 60000, 120000, 300000, 600000, 2147483647)
            val timeoutLabels = arrayOf("15s", "30s", "1min", "2min", "5min", "10min", "Never")

            var nextIndex = 0
            for (i in timeouts.indices) {
                if (currentTimeout == timeouts[i]) {
                    nextIndex = (i + 1) % timeouts.size
                    break
                }
            }

            val newTimeout = timeouts[nextIndex]
            val success = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, newTimeout)
            if (success) {
                Toast.makeText(this, "Screen timeout: ${timeoutLabels[nextIndex]}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to change screen timeout", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateScreenTimeoutIcon(imageView: ImageView?, textView: TextView?) {
        try {
            val currentTimeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            val labels = mapOf(
                15000 to "15s",
                30000 to "30s",
                60000 to "1min",
                120000 to "2min",
                300000 to "5min",
                600000 to "10min",
                2147483647 to "Never"
            )
            val label = labels[currentTimeout] ?: "Timeout"
            textView?.text = label
            imageView?.alpha = 1.0f
        } catch (_: Exception) {
            textView?.text = "Timeout"
            imageView?.alpha = 0.4f
        }
    }

    private fun updateScreenTimeoutIconOnly(imageView: ImageView?) {
        try {
            val currentTimeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            imageView?.alpha = if (currentTimeout > 0) 1.0f else 0.4f
        } catch (_: Exception) {
            imageView?.alpha = 0.4f
        }
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        instance = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && torchCallback != null) {
            cameraManager.unregisterTorchCallback(torchCallback)
        }
        
        if (isFocusModeReceiverRegistered) {
            try {
                unregisterReceiver(focusModeReceiver)
                isFocusModeReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister focus mode receiver", e)
            }
        }
        
        if (isSettingsReceiverRegistered) {
            try {
                unregisterReceiver(settingsReceiver)
                isSettingsReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister settings receiver", e)
            }
        }
        
        if (isScreenReceiverRegistered && screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver)
                isScreenReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screen receiver", e)
            }
        }
        
        removeEdgePanel()
        removeControlCenterTrigger()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeFloatingButton()
        removeEdgePanel()
        removeControlCenterTrigger()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return


        if (!focusModeManager.isFocusModeEnabled()) return


        val modeType = sharedPreferences.getString(
            Constants.Prefs.FOCUS_MODE_TYPE,
            Constants.Prefs.FOCUS_MODE_TYPE_STRICT
        )
        if (modeType != Constants.Prefs.FOCUS_MODE_TYPE_STRICT) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()


            val blockedSettingsPackages = setOf(
                "com.android.settings",
                "com.android.settings.panel",
                "com.android.settings.accessibility",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.android.devicelockcontroller",
                "com.android.managedprovisioning"
            )

            if (packageName in blockedSettingsPackages) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, "Settings blocked - Strict focus mode is active", Toast.LENGTH_SHORT).show()
            }


            if (packageName == "com.guruswarupa.launch" && className != null) {
                if (className.contains("SettingsActivity")) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, "Launcher settings blocked - Strict focus mode is active", Toast.LENGTH_SHORT).show()
                }
            }


            if (packageName == "com.android.systemui" && className != null) {
                if (className.contains("Settings") ||
                    className.contains("settings") ||
                    className.contains("Preference")) {

                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, "Settings blocked - Strict focus mode is active", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onInterrupt() {}

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    fun toggleNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
}
