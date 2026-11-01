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

    internal var windowManager: WindowManager? = null
    internal var floatingView: View? = null
    internal var edgeHandleView: View? = null
    internal var controlCenterTriggerView: View? = null
    internal var edgePanelView: View? = null
    internal var shortcutMenu: View? = null
    internal var isMenuVisible = false
    internal var isEdgePanelVisible = false
    internal var isScreenOn = true
    internal var keyguardManager: KeyguardManager? = null

    internal val wifiManager by lazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }
    internal val bluetoothAdapter by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    internal val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    internal val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    internal val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    internal val usageStatsManager by lazy { getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    internal val appUsageStatsManager by lazy { AppUsageStatsManager(this) }

    internal var isTorchOn = false
    internal lateinit var focusModeManager: FocusModeManager
    internal lateinit var sharedPreferences: SharedPreferences

    internal var isFocusModeReceiverRegistered = false
    internal var isSettingsReceiverRegistered = false
    internal var isScreenReceiverRegistered = false

    internal val focusModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.FOCUS_MODE_CHANGED") {

            }
        }
    }
    internal val torchCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                isTorchOn = enabled
            }
        }
    } else null
    internal val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                refreshOverlayState()
                if (isEdgePanelVisible) {
                    populateEdgePanel()
                }
            }
        }
    }

    internal var screenReceiver: BroadcastReceiver? = null

    internal fun registerScreenReceiver() {
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

    internal var unlockMonitorHandler: android.os.Handler? = null
    internal var unlockMonitorRunnable: Runnable? = null

    internal fun startUnlockMonitor() {
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

    internal fun stopUnlockMonitor() {
        unlockMonitorHandler?.removeCallbacksAndMessages(null)
        unlockMonitorHandler = null
        unlockMonitorRunnable = null
    }

    internal fun showEdgeHandleAfterUnlock() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, false)
            if (isEnabled) {
                showEdgeHandle()
            }
        }, 500)
    }

    internal fun showControlCenterTriggerAfterUnlock() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)
            if (isEnabled) {
                showControlCenterTrigger()
            }
        }, 500)
    }

    internal data class EdgePanelAppEntry(
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
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(focusModeReceiver, IntentFilter("com.guruswarupa.launch.FOCUS_MODE_CHANGED"), RECEIVER_NOT_EXPORTED)
            isFocusModeReceiverRegistered = true
            registerReceiver(settingsReceiver, IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED"), RECEIVER_NOT_EXPORTED)
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

    internal fun refreshOverlayState() {
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
    internal fun showFloatingButton() {
        if (floatingView != null) return

        val themedContext = ThemeManager.themedContext(this, R.style.Theme_Launch)
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

    internal fun snapToEdge(params: WindowManager.LayoutParams) {
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

    internal fun removeFloatingButton() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
        hideMenu()
    }

    internal fun Drawable.toBitmapDrawable(): Drawable {
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

    internal fun getScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { displayMetrics.widthPixels = it.width() }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }
        return displayMetrics.widthPixels
    }

    internal fun getScreenHeight(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { displayMetrics.heightPixels = it.height() }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }
        return displayMetrics.heightPixels
    }

    internal fun getBottomSystemInset(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return windowManager?.currentWindowMetrics
            ?.windowInsets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            ?.bottom ?: 0
    }

    internal fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
            }
        }

        if (isSettingsReceiverRegistered) {
            try {
                unregisterReceiver(settingsReceiver)
                isSettingsReceiverRegistered = false
            } catch (e: Exception) {
            }
        }

        if (isScreenReceiverRegistered && screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver)
                isScreenReceiverRegistered = false
            } catch (e: Exception) {
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
                Toast.makeText(this, this.getString(R.string.toast_settings_blocked_strict_focus_mode_is_active), Toast.LENGTH_SHORT).show()
            }

            if (packageName == "com.guruswarupa.launch" && className != null) {
                if (className.contains("SettingsActivity")) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, this.getString(R.string.toast_launcher_settings_blocked_strict_focus_mode_is_a), Toast.LENGTH_SHORT).show()
                }
            }

            if (packageName == "com.android.systemui" && className != null) {
                if (className.contains("Settings") ||
                    className.contains("settings") ||
                    className.contains("Preference")) {

                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, this.getString(R.string.toast_settings_blocked_strict_focus_mode_is_active), Toast.LENGTH_SHORT).show()
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
