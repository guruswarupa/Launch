package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ai.llm.AssistantModel
import com.guruswarupa.launch.ai.llm.DeviceCapability
import com.guruswarupa.launch.ai.llm.DeviceCapabilityResult
import com.guruswarupa.launch.ai.llm.ModelDownloadManager
import com.guruswarupa.launch.ai.llm.ModelState
import com.guruswarupa.launch.ai.llm.WebAiProvider
import dagger.hilt.android.AndroidEntryPoint
import com.guruswarupa.launch.managers.DownloadableFontManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.ThemeOption
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.services.WalkDetectionService
import com.guruswarupa.launch.ui.views.SafeHorizontalScrollView
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.IconPackManager
import com.guruswarupa.launch.utils.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.guruswarupa.launch.ui.theme.AccentSwatch
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.ui.theme.ThemePalette

internal fun SettingsActivity.setupAccessibilityShortcut() {
    val sw = findViewById<SwitchCompat>(R.id.accessibility_shortcut_switch)
    val configButton = findViewById<View>(R.id.config_control_center_button)

    fun applyConfigButtonState(enabled: Boolean) {
        configButton.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    sw.isChecked = prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, false)
    applyConfigButtonState(sw.isChecked)
    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !hasAccessibilityServicePermission()) {
            sw.isChecked = false
            showPermissionDialog("Accessibility Required", "Enable Launch in Accessibility settings.") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } else {
            prefs.edit { putBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_ENABLED, isChecked) }
            applyConfigButtonState(isChecked)
            startService(Intent(this, ScreenLockAccessibilityService::class.java).apply {
                action = "TOGGLE_CONTROL_CENTER_TRIGGER"
                putExtra("enabled", isChecked)
            })
        }
    }

    configButton.setOnClickListener {
        startActivity(Intent(this, ControlCenterConfigActivity::class.java))
    }
}

internal fun SettingsActivity.setupEdgePanel() {
    val sw = findViewById<SwitchCompat>(R.id.edge_panel_switch)
    val configButton = findViewById<View>(R.id.config_edge_panel_button)
    val usageStatsManager = AppUsageStatsManager(this)

    fun applyConfigButtonState(enabled: Boolean) {
        configButton.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    sw.isChecked = prefs.getBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, false)
    applyConfigButtonState(sw.isChecked)

    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !hasAccessibilityServicePermission()) {
            sw.isChecked = false
            showPermissionDialog("Accessibility Required", "Enable Launch in Accessibility settings.") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return@setOnCheckedChangeListener
        }

        if (isChecked && !usageStatsManager.hasUsageStatsPermission()) {
            sw.isChecked = false
            showPermissionDialog(
                getString(R.string.edge_panel_permission_usage_title),
                getString(R.string.edge_panel_permission_usage_message)
            ) {
                startActivity(usageStatsManager.requestUsageStatsPermission())
            }
            return@setOnCheckedChangeListener
        }

        prefs.edit { putBoolean(Constants.Prefs.EDGE_PANEL_ENABLED, isChecked) }
        applyConfigButtonState(isChecked)
        startService(Intent(this, ScreenLockAccessibilityService::class.java).apply {
            action = "TOGGLE_EDGE_PANEL"
            putExtra("enabled", isChecked)
        })
        notifySettingsChanged()
    }
}

internal fun SettingsActivity.setupBackTap() {
    val sw = findViewById<SwitchCompat>(R.id.back_tap_switch)
    val cont = findViewById<View>(R.id.back_tap_settings_container)
    val spin = findViewById<Spinner>(R.id.back_tap_double_action_spinner)
    val seek = findViewById<SeekBar>(R.id.back_tap_sensitivity_seekbar)
    val valT = findViewById<TextView>(R.id.back_tap_sensitivity_value)

    val en = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
    sw.isChecked = en
    cont.isVisible = en

    val acts = arrayOf("None", "Torch", "Screenshot", "Notifications", "Screen Off", "Sound")
    val vals = arrayOf(BackTapService.ACTION_NONE, BackTapService.ACTION_TORCH_TOGGLE, BackTapService.ACTION_SCREENSHOT, BackTapService.ACTION_NOTIFICATIONS, BackTapService.ACTION_SCREEN_OFF, BackTapService.ACTION_SOUND_TOGGLE)
    spin.adapter =  ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, acts).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
    spin.setSelection(vals.indexOf(prefs.getString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, BackTapService.ACTION_SOUND_TOGGLE)).coerceAtLeast(0))

    val cur = prefs.getInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 7).coerceIn(1, 10)
    seek.max = 9
    seek.progress = cur - 1
    valT.text = cur.toString()

    sw.setOnCheckedChangeListener { _, isChecked ->
        prefs.edit { putBoolean(Constants.Prefs.BACK_TAP_ENABLED, isChecked) }
        cont.isVisible = isChecked
        notifySettingsChanged()
    }
    spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
            prefs.edit { putString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, vals[pos]) }
            notifySettingsChanged()
        } override fun onNothingSelected(p: AdapterView<*>) {}
    }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
            valT.text = (p + 1).toString()
            if (f) {
                prefs.edit { putInt(Constants.Prefs.BACK_TAP_SENSITIVITY, p + 1) }
                notifySettingsChanged()
            }
        } override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

internal fun SettingsActivity.setupShakeTorch() {
    val sw = findViewById<SwitchCompat>(R.id.shake_torch_switch)
    val cont = findViewById<View>(R.id.shake_sensitivity_container)
    val seek = findViewById<SeekBar>(R.id.shake_sensitivity_seekbar)
    val valT = findViewById<TextView>(R.id.sensitivity_value_text)

    val en = prefs.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
    sw.isChecked = en
    cont.isVisible = en
    val cur = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5).coerceIn(1, 10)
    seek.max = 9
    seek.progress = cur - 1
    valT.text = cur.toString()

    sw.setOnCheckedChangeListener { _, isChecked ->
        prefs.edit { putBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, isChecked) }
        cont.isVisible = isChecked
        notifySettingsChanged()
    }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
            valT.text = (p + 1).toString()
            if (f) {
                prefs.edit { putInt(Constants.Prefs.SHAKE_SENSITIVITY, p + 1) }
                notifySettingsChanged()
            }
        } override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

internal fun SettingsActivity.setupWalkDetection() {
    val sw = findViewById<SwitchCompat>(R.id.walk_detect_switch)
    val cont = findViewById<View>(R.id.walk_detect_settings_container)
    val spin = findViewById<Spinner>(R.id.walk_detect_action_spinner)
    val thresholdSeek = findViewById<SeekBar>(R.id.walk_detect_threshold_seekbar)
    val thresholdVal = findViewById<TextView>(R.id.walk_detect_threshold_value)
    val timeWindowSeek = findViewById<SeekBar>(R.id.walk_detect_time_window_seekbar)
    val timeWindowVal = findViewById<TextView>(R.id.walk_detect_time_window_value)
    val cooldownSeek = findViewById<SeekBar>(R.id.walk_detect_cooldown_seekbar)
    val cooldownVal = findViewById<TextView>(R.id.walk_detect_cooldown_value)
    val chooseAppButton = findViewById<View>(R.id.walk_detect_choose_app_button)
    val selectedAppText = findViewById<TextView>(R.id.walk_detect_selected_app_text)

    val en = prefs.getBoolean(Constants.Prefs.WALK_DETECT_ENABLED, false)
    sw.isChecked = en
    cont.isVisible = en

    val acts = arrayOf("Music App", "Custom App", "Torch", "Sound Toggle", "None")
    val vals = arrayOf(
        WalkDetectionService.ACTION_LAUNCH_MUSIC,
        WalkDetectionService.ACTION_LAUNCH_APP,
        WalkDetectionService.ACTION_TORCH_TOGGLE,
        WalkDetectionService.ACTION_SOUND_TOGGLE,
        WalkDetectionService.ACTION_NONE
    )
    spin.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, acts).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    val currentAction = prefs.getString(
        Constants.Prefs.WALK_DETECT_ACTION,
        WalkDetectionService.ACTION_LAUNCH_MUSIC
    ) ?: WalkDetectionService.ACTION_LAUNCH_MUSIC
    spin.setSelection(vals.indexOf(currentAction).coerceAtLeast(0))

    fun updateSelectedAppText() {
        val pkg = prefs.getString(Constants.Prefs.WALK_DETECT_CUSTOM_APP_PACKAGE, null)
        selectedAppText.text = if (pkg != null) {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
        } else {
            "No app selected"
        }
    }

    fun updateAppChooserVisibility(action: String) {
        val isCustomApp = action == WalkDetectionService.ACTION_LAUNCH_APP
        chooseAppButton.isVisible = isCustomApp
        selectedAppText.isVisible = isCustomApp
        if (isCustomApp) updateSelectedAppText()
    }
    updateAppChooserVisibility(currentAction)

    val curThreshold = prefs.getInt(Constants.Prefs.WALK_DETECT_STEP_THRESHOLD, 10).coerceIn(5, 30)
    thresholdSeek.max = 25
    thresholdSeek.progress = curThreshold - 5
    thresholdVal.text = getString(R.string.steps_format, curThreshold)

    val curTimeWindow = prefs.getInt(Constants.Prefs.WALK_DETECT_TIME_WINDOW_SECONDS, 15).coerceIn(5, 60)
    timeWindowSeek.max = 55
    timeWindowSeek.progress = curTimeWindow - 5
    timeWindowVal.text = getString(R.string.lbl_sec, curTimeWindow)

    val curCooldown = prefs.getInt(Constants.Prefs.WALK_DETECT_COOLDOWN_MINUTES, 5).coerceIn(1, 30)
    cooldownSeek.max = 29
    cooldownSeek.progress = curCooldown - 1
    cooldownVal.text = getString(R.string.walking_time_minutes_format, curCooldown)

    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                sw.isChecked = false
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                    Constants.RequestCodes.ACTIVITY_RECOGNITION_PERMISSION
                )
                return@setOnCheckedChangeListener
            }
        }

        prefs.edit { putBoolean(Constants.Prefs.WALK_DETECT_ENABLED, isChecked) }
        cont.isVisible = isChecked
        notifySettingsChanged()
    }

    spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            prefs.edit { putString(Constants.Prefs.WALK_DETECT_ACTION, vals[position]) }
            updateAppChooserVisibility(vals[position])
            notifySettingsChanged()
        }

        override fun onNothingSelected(parent: AdapterView<*>) = Unit
    }

    thresholdSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            thresholdVal.text = getString(R.string.steps_format, progress + 5)
            if (fromUser) {
                prefs.edit { putInt(Constants.Prefs.WALK_DETECT_STEP_THRESHOLD, progress + 5) }
                notifySettingsChanged()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    timeWindowSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            timeWindowVal.text = getString(R.string.lbl_sec, progress + 5)
            if (fromUser) {
                prefs.edit { putInt(Constants.Prefs.WALK_DETECT_TIME_WINDOW_SECONDS, progress + 5) }
                notifySettingsChanged()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    cooldownSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            cooldownVal.text = getString(R.string.walking_time_minutes_format, progress + 1)
            if (fromUser) {
                prefs.edit { putInt(Constants.Prefs.WALK_DETECT_COOLDOWN_MINUTES, progress + 1) }
                notifySettingsChanged()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })

    chooseAppButton.setOnClickListener {
        showWalkDetectionAppPicker {
            updateSelectedAppText()
            notifySettingsChanged()
        }
    }
}

internal fun SettingsActivity.setupExtraDimmer() {
    val sw = findViewById<SwitchCompat>(R.id.screen_dimmer_switch)
    val seek = findViewById<SeekBar>(R.id.screen_dimmer_seekbar)
    val valT = findViewById<TextView>(R.id.dimmer_value_text)
    val cont = findViewById<View>(R.id.screen_dimmer_container)

    val en = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
    sw.isChecked = en
    seek.progress = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 10)
    valT.text = "${seek.progress}%"
    cont.isVisible = en

    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !Settings.canDrawOverlays(this)) {
            sw.isChecked = false
            showPermissionDialog("Overlay Permission", "Enable Display over other apps.") { startActivity(Intent(this, PermissionsActivity::class.java)) }
        } else {
            prefs.edit { putBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, isChecked) }
            cont.isVisible = isChecked
            if (isChecked) ScreenDimmerService.startService(this, seek.progress) else ScreenDimmerService.stopService(this)
        }
    }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
            valT.text = "$p%"
            if (f) {
                prefs.edit { putInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, p) }
                if (sw.isChecked) ScreenDimmerService.updateDimLevel(this@setupExtraDimmer, p)
            }
        } override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

internal fun SettingsActivity.setupNightMode() {
    val sw = findViewById<SwitchCompat>(R.id.night_mode_switch)
    val seek = findViewById<SeekBar>(R.id.night_mode_intensity_seekbar)
    val valT = findViewById<TextView>(R.id.night_mode_value_text)
    val cont = findViewById<View>(R.id.night_mode_container)

    val en = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
    sw.isChecked = en
    seek.progress = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
    valT.text = "${seek.progress}%"
    cont.isVisible = en

    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !Settings.canDrawOverlays(this)) {
            sw.isChecked = false
            showPermissionDialog("Overlay Permission", "Enable Display over other apps.") { startActivity(Intent(this, PermissionsActivity::class.java)) }
        } else {
            prefs.edit { putBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, isChecked) }
            cont.isVisible = isChecked
            if (isChecked) NightModeService.startService(this, seek.progress) else NightModeService.stopService(this)
        }
    }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
            valT.text = "$p%"
            if (f) {
                prefs.edit { putInt(Constants.Prefs.NIGHT_MODE_INTENSITY, p) }
                if (sw.isChecked) NightModeService.updateIntensity(this@setupNightMode, p)
            }
        } override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

internal fun SettingsActivity.setupFlipToDnd() {
    val sw = findViewById<SwitchCompat>(R.id.flip_dnd_switch)
    sw.isChecked = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                sw.isChecked = false
                showPermissionDialog("DND Permission", "Grant DND access to use this feature.") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, true) }
                notifySettingsChanged()
            }
        } else {
            prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, false) }
            notifySettingsChanged()
        }
    }
}

internal fun SettingsActivity.setupGrayscaleMode() {
    val sw = findViewById<SwitchCompat>(R.id.grayscale_mode_switch)
    sw.isChecked = prefs.getBoolean(Constants.Prefs.GRAYSCALE_MODE_ENABLED, false)
    sw.setOnCheckedChangeListener { _, isChecked ->
        try {
            Settings.Secure.putInt(contentResolver, "accessibility_display_daltonizer_enabled", if (isChecked) 1 else 0)
            Settings.Secure.putInt(contentResolver, "accessibility_display_daltonizer", if (isChecked) 0 else -1)
            prefs.edit { putBoolean(Constants.Prefs.GRAYSCALE_MODE_ENABLED, isChecked) }
        } catch (e: Exception) {
            e.printStackTrace()
            sw.isChecked = !isChecked
            showProtectedPermissionDialog()
        }
    }
}

internal fun SettingsActivity.showProtectedPermissionDialog() {
    AlertDialog.Builder(this, R.style.CustomDialogTheme)
        .setTitle(getString(R.string.dlg_permission_required))
        .setMessage(getString(R.string.dlg_this_feature_requires_the_write_secure_settings) +
                "Please run this command via ADB:\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        .setPositiveButton(getString(R.string.dlg_copy_command)) { _, _ ->
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, this.getString(R.string.toast_command_copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton(getString(R.string.support_thank_you_close), null)
        .show()
}

internal fun SettingsActivity.showWalkDetectionAppPicker(onAppSelected: () -> Unit) {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = packageManager.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                ?: packageName
            packageName to label
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }

    if (apps.isEmpty()) {
        Toast.makeText(this, this.getString(R.string.toast_no_launchable_apps_found), Toast.LENGTH_SHORT).show()
        return
    }

    AlertDialog.Builder(this, R.style.CustomDialogTheme)
        .setTitle(getString(R.string.choose_app))
        .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
            prefs.edit { putString(Constants.Prefs.WALK_DETECT_CUSTOM_APP_PACKAGE, apps[which].first) }
            onAppSelected()
        }
        .setNegativeButton(getString(R.string.cancel_button), null)
        .show()
}


internal fun SettingsActivity.setupLockScreen() {
    val sw = findViewById<SwitchCompat>(R.id.lockscreen_switch)
    val content = findViewById<SwitchCompat>(R.id.lockscreen_content_switch)
    sw.isChecked = prefs.getBoolean(Constants.Prefs.LOCKSCREEN_ENABLED, false) && Settings.canDrawOverlays(this)
    content.isChecked = prefs.getBoolean(Constants.Prefs.LOCKSCREEN_NOTIF_CONTENT, false)
    sw.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !Settings.canDrawOverlays(this)) {
            sw.isChecked = false
            showPermissionDialog(getString(R.string.set_overlay_title), getString(R.string.set_overlay_message)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            }
        } else {
            prefs.edit { putBoolean(Constants.Prefs.LOCKSCREEN_ENABLED, isChecked) }
            com.guruswarupa.launch.managers.ServiceManager(this, prefs).updateLockScreenService()
        }
    }
    content.setOnCheckedChangeListener { _, isChecked ->
        prefs.edit { putBoolean(Constants.Prefs.LOCKSCREEN_NOTIF_CONTENT, isChecked) }
    }
}
