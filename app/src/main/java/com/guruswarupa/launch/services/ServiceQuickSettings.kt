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

internal fun ScreenLockAccessibilityService.toggleMenu() {
    if (isMenuVisible) {
        hideMenu()
    } else {
        showMenu()
    }
}

internal fun ScreenLockAccessibilityService.toggleControlCenter() {

    if (isMenuVisible) {
        hideMenu()
    } else {
        showMenu()
    }
}

internal fun ScreenLockAccessibilityService.showMenu() {
    if (shortcutMenu != null) return

    val themedContext = ThemeManager.themedContext(this, R.style.Theme_Launch)
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

internal fun ScreenLockAccessibilityService.hideMenu() {
    shortcutMenu?.let {
        try {
            windowManager?.removeView(it)
        } catch (_: Exception) {}
        shortcutMenu = null
    }
    isMenuVisible = false
}

internal fun ScreenLockAccessibilityService.setupMenuListeners(view: View) {
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
    val shortcutList = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, ScreenLockAccessibilityService.DEFAULT_SHORTCUTS)
        ?.split(",") ?: ScreenLockAccessibilityService.DEFAULT_SHORTCUTS.split(",")

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
            setTextColor(ThemeManager.colorOrNull(this@setupMenuListeners, R.attr.appTextPrimary) ?: android.graphics.Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        shortcutContainer.addView(label)

        when (id) {
            "wifi" -> {
                icon.setImageResource(R.drawable.ic_wifi_stat)
                icon.contentDescription = getString(R.string.lbl_wifi)
                label.text = getString(R.string.lbl_wifi)
                updateWifiIcon(icon)
                shortcutContainer.setOnClickListener { toggleWifi(); updateWifiIcon(icon); hideMenu() }
            }
            "bluetooth" -> {
                icon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
                icon.contentDescription = getString(R.string.lbl_bluetooth)
                label.text = getString(R.string.lbl_bluetooth)
                updateBluetoothIcon(icon)
                shortcutContainer.setOnClickListener { toggleBluetooth(); hideMenu() }
            }
            "airplane" -> {
                icon.setImageResource(R.drawable.ic_airplane_stat)
                icon.contentDescription = getString(R.string.lbl_airplane)
                label.text = getString(R.string.lbl_airplane)
                updateAirplaneIcon(icon)
                shortcutContainer.setOnClickListener { toggleAirplaneMode(); hideMenu() }
            }
            "torch" -> {
                icon.setImageResource(R.drawable.ic_torch_stat)
                icon.contentDescription = getString(R.string.lbl_torch)
                label.text = getString(R.string.lbl_torch)
                updateTorchIcon(icon)
                shortcutContainer.setOnClickListener { toggleTorch(); updateTorchIcon(icon) }
            }
            "data" -> {
                icon.setImageResource(R.drawable.ic_mobile_data_stat)
                icon.contentDescription = getString(R.string.lbl_data)
                label.text = getString(R.string.lbl_data)
                shortcutContainer.setOnClickListener { toggleMobileData() }
            }
            "rotation" -> {
                icon.setImageResource(R.drawable.ic_rotation_stat)
                icon.contentDescription = getString(R.string.a11y_auto_rotation)
                label.text = getString(R.string.lbl_rotation)
                updateRotationIcon(icon)
                shortcutContainer.setOnClickListener { toggleAutoRotation(); updateRotationIcon(icon) }
            }
            "sound" -> {
                icon.contentDescription = getString(R.string.a11y_sound_mode)
                label.text = getString(R.string.lbl_sound)
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
                icon.contentDescription = getString(R.string.a11y_do_not_disturb)
                label.text = getString(R.string.lbl_dnd)
                updateDndIconOnly(icon)
                shortcutContainer.setOnClickListener { toggleDndOnly(icon) }
            }
            "location" -> {
                icon.setImageResource(android.R.drawable.ic_menu_mylocation)
                icon.contentDescription = getString(R.string.perm_location)
                label.text = getString(R.string.perm_location)
                shortcutContainer.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                    hideMenu()
                }
            }
            "qr_scan" -> {
                icon.setImageResource(R.drawable.ic_qr_scan_stat)
                icon.contentDescription = getString(R.string.lbl_qr_scan)
                label.text = getString(R.string.lbl_qr_scan)
                shortcutContainer.setOnClickListener { launchQrScanner(); hideMenu() }
            }
            "camera" -> {
                icon.setImageResource(android.R.drawable.ic_menu_camera)
                icon.contentDescription = getString(R.string.perm_camera)
                label.text = getString(R.string.perm_camera)
                shortcutContainer.setOnClickListener {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    hideMenu()
                }
            }
            "screenshot" -> {
                icon.setImageResource(R.drawable.ic_screenshot_stat)
                icon.contentDescription = getString(R.string.lbl_screenshot)
                label.text = getString(R.string.lbl_screenshot)
                shortcutContainer.setOnClickListener { takeScreenshot(); hideMenu() }
            }
            "record" -> {
                icon.setImageResource(android.R.drawable.ic_menu_slideshow)
                icon.contentDescription = getString(R.string.a11y_screen_record)
                label.text = getString(R.string.lbl_record)
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
                icon.contentDescription = getString(R.string.lbl_lock)
                label.text = getString(R.string.lbl_lock)
                shortcutContainer.setOnClickListener { lockScreen(); hideMenu() }
            }
            "power" -> {
                icon.setImageResource(android.R.drawable.ic_lock_power_off)
                icon.setColorFilter(0xFFF7768E.toInt())
                icon.contentDescription = getString(R.string.lbl_power)
                label.text = getString(R.string.lbl_power)
                shortcutContainer.setOnClickListener { performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); hideMenu() }
            }
            "hotspot" -> {
                icon.setImageResource(R.drawable.ic_wifi_stat)
                icon.contentDescription = getString(R.string.lbl_hotspot)
                label.text = getString(R.string.lbl_hotspot)
                updateHotspotIcon(icon)
                shortcutContainer.setOnClickListener { toggleHotspot() }
            }
            "screen_timeout" -> {
                icon.setImageResource(R.drawable.ic_settings_icon)
                icon.contentDescription = getString(R.string.a11y_screen_timeout)
                label.text = getString(R.string.lbl_timeout)
                updateScreenTimeoutIconOnly(icon)
                shortcutContainer.setOnClickListener { cycleScreenTimeout(); updateScreenTimeoutIconOnly(icon); hideMenu() }
            }
            else -> continue
        }
        linearLayout?.addView(shortcutContainer)
    }
}

internal fun ScreenLockAccessibilityService.safeSetVolume(streamType: Int, progress: Int) {
    try {
        audioManager.setStreamVolume(streamType, progress, 0)
    } catch (e: SecurityException) {
        requestDndPermission()
    }
}

internal fun ScreenLockAccessibilityService.requestDndPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, this.getString(R.string.toast_dnd_access_required_to_change_volume), Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}

internal fun ScreenLockAccessibilityService.launchQrScanner() {
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
            Toast.makeText(this, this.getString(R.string.toast_no_qr_scanner_or_camera_found), Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun ScreenLockAccessibilityService.toggleWifi() {
    try {
        @Suppress("DEPRECATION")
        wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun ScreenLockAccessibilityService.updateWifiIcon(imageView: ImageView?) {
    val enabled = wifiManager.isWifiEnabled
    imageView?.alpha = if (enabled) 1.0f else 0.4f
}

@SuppressLint("MissingPermission")
internal fun ScreenLockAccessibilityService.toggleBluetooth() {
    try {
        if (bluetoothAdapter.isEnabled) bluetoothAdapter.disable() else bluetoothAdapter.enable()
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@SuppressLint("MissingPermission")
internal fun ScreenLockAccessibilityService.updateBluetoothIcon(imageView: ImageView?) {
    try {
        val enabled = bluetoothAdapter.isEnabled
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    } catch (e: SecurityException) {

        imageView?.alpha = 0.4f
    } catch (e: Exception) {
        imageView?.alpha = 0.4f
    }
}

internal fun ScreenLockAccessibilityService.toggleAirplaneMode() {
    try {
        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

internal fun ScreenLockAccessibilityService.updateAirplaneIcon(imageView: ImageView?) {
    val enabled = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    imageView?.alpha = if (enabled) 1.0f else 0.4f
}

internal fun ScreenLockAccessibilityService.toggleTorch() {
    try {
        val cameraId = cameraManager.cameraIdList[0]
        isTorchOn = !isTorchOn
        cameraManager.setTorchMode(cameraId, isTorchOn)
    } catch (e: Exception) {
        Toast.makeText(this, this.getString(R.string.toast_torch_not_available), Toast.LENGTH_SHORT).show()
    }
}

internal fun ScreenLockAccessibilityService.updateTorchIcon(imageView: ImageView?) {
    imageView?.alpha = if (isTorchOn) 1.0f else 0.4f
}

internal fun ScreenLockAccessibilityService.toggleAutoRotation() {
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
        Toast.makeText(this, if (newValue == 1) getString(R.string.toast_auto_rotate_on) else getString(R.string.toast_portrait_locked), Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

internal fun ScreenLockAccessibilityService.updateRotationIcon(imageView: ImageView?) {
    try {
        val isAutoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        imageView?.alpha = if (isAutoRotate) 1.0f else 0.4f
    } catch (_: Exception) {}
}

internal fun ScreenLockAccessibilityService.updateSoundIconOnly(imageView: ImageView?) {
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

internal fun ScreenLockAccessibilityService.toggleDndOnly(icon: ImageView?) {
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

internal fun ScreenLockAccessibilityService.updateDndIconOnly(imageView: ImageView?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val filter = notificationManager.currentInterruptionFilter
        val enabled = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                     filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        imageView?.setImageResource(R.drawable.ic_focus_mode_icon)
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }
}

internal fun ScreenLockAccessibilityService.toggleMobileData() {
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
                Toast.makeText(this, this.getString(R.string.toast_mobile_data_settings_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun ScreenLockAccessibilityService.toggleHotspot() {
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
                Toast.makeText(this, this.getString(R.string.toast_hotspot_settings_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun ScreenLockAccessibilityService.updateHotspotIcon(imageView: ImageView?) {

    imageView?.alpha = 1.0f
}

internal fun ScreenLockAccessibilityService.cycleScreenTimeout() {
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
            Toast.makeText(this, this.getString(R.string.toast_screen_timeout, timeoutLabels[nextIndex]), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(this, this.getString(R.string.toast_failed_to_change_screen_timeout), Toast.LENGTH_SHORT).show()
    }
}

internal fun ScreenLockAccessibilityService.updateScreenTimeoutIconOnly(imageView: ImageView?) {
    try {
        val currentTimeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        imageView?.alpha = if (currentTimeout > 0) 1.0f else 0.4f
    } catch (_: Exception) {
        imageView?.alpha = 0.4f
    }
}

internal fun ScreenLockAccessibilityService.requestWriteSettingsPermission() {
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
