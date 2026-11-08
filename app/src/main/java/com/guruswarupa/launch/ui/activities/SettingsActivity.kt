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

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), PurchasesUpdatedListener {
    companion object {
        const val EXTRA_OPEN_SUPPORT_SECTION = "open_support_section"
        const val EXTRA_OPEN_AI_SECTION = "open_ai_section"
        private const val STATE_WIDGETS_SECTION_EXPANDED = "state_widgets_section_expanded"
        private const val STATE_NEWS_SECTION_EXPANDED = "state_news_section_expanded"
        private const val STATE_SYSTEM_MONITOR_SECTION_EXPANDED = "state_system_monitor_section_expanded"
    }

    @javax.inject.Inject
    lateinit var deviceCapability: DeviceCapability

    @javax.inject.Inject
    lateinit var modelDownloadManager: ModelDownloadManager

    internal val aiAssistantPollHandler = Handler(Looper.getMainLooper())
    internal var aiAssistantPolling = false

    internal val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    internal class SettingsHandler(activity: SettingsActivity) : Handler(Looper.getMainLooper()) {
        private val weakActivity = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            val activity = weakActivity.get() ?: return
        }
    }

    internal val handler = SettingsHandler(this)
    internal var selectedThemeId: String = "stardust"
    internal var selectedThemeCategory: String? = null
    internal var hasUnsavedThemeChanges = false
    internal var widgetsSectionExpanded = false
    internal var newsSectionExpanded = false
    internal var systemMonitorSectionExpanded = false
    internal var billingClient: BillingClient? = null
    internal val supportProducts = linkedMapOf(
        "support_49" to R.id.support_49,
        "support_99" to R.id.support_99,
        "support_199" to R.id.support_199,
        "support_299" to R.id.support_299
    )
    internal val supportProductDetails = LinkedHashMap<String, ProductDetails>()

    internal val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { exportSettingsToFile(it) }
    }

    internal val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { importSettingsFromFile(it) }
    }

    internal val importThemeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null && ThemeShareHelper.importThemeFromUri(this, prefs, uri)) {
                Toast.makeText(this, getString(R.string.toast_theme_imported), Toast.LENGTH_SHORT).show()
                restartLauncher()
            } else if (uri != null) {
                Toast.makeText(this, getString(R.string.toast_theme_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setupWallpaper(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.guruswarupa.launch.managers.KidsModeManager(this).isActive()) {
            Toast.makeText(this, getString(R.string.kids_mode_settings_blocked), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        applyOrientationPreference()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_settings)
        applyContentInsets()
        applyBackgroundTranslucency()
        widgetsSectionExpanded = savedInstanceState?.getBoolean(STATE_WIDGETS_SECTION_EXPANDED, false) ?: false
        newsSectionExpanded = savedInstanceState?.getBoolean(STATE_NEWS_SECTION_EXPANDED, false) ?: false
        systemMonitorSectionExpanded = savedInstanceState?.getBoolean(STATE_SYSTEM_MONITOR_SECTION_EXPANDED, false) ?: false

        selectedThemeId = prefs.getString(Constants.Prefs.SELECTED_THEME, "stardust") ?: "stardust"

        setupWallpaper(null)

        setupThemeSection()
        setupAppearanceSection()
        setupActionsSection()
        setupDockSettings()
        setupNewsFeedSection()
        setupAppTimerSection()
        setupSecuritySection()
        setupWebAppsSection()
        setupSystemMonitorSection()
        setupFileTransferSection()
        setupMaintenanceSection()
        setupSupportSection()
        setupVersionInfo()
        openSupportSectionIfRequested()
        openAiSectionIfRequested()
    }

    override fun onResume() {
        super.onResume()
        applyOrientationPreference()
        if (billingClient?.isReady == true) {
            queryExistingSupportPurchases()
        }
        findViewById<SwitchCompat>(R.id.notification_badges_switch)?.isChecked =
            com.guruswarupa.launch.core.PermissionManager(this, prefs).isNotificationListenerServiceEnabled()
        findViewById<SwitchCompat>(R.id.kids_mode_switch)?.isChecked =
            com.guruswarupa.launch.managers.KidsModeManager(this).isActive()
    }

    @SuppressLint("QueryPermissionsNeeded")
    internal fun showSwipeRightAppPicker(onChanged: () -> Unit) {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val labels = (listOf(getString(R.string.stock_swipe_right_app_none)) + resolved.map { it.loadLabel(pm).toString() }).toTypedArray()
        val packageNames = listOf("") + resolved.map { it.activityInfo.packageName }
        val current = prefs.getString(Constants.Prefs.STOCK_SWIPE_RIGHT_APP, null).orEmpty()
        val checkedIndex = packageNames.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.stock_swipe_right_app_choose)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                prefs.edit { putString(Constants.Prefs.STOCK_SWIPE_RIGHT_APP, packageNames[which]) }
                notifySettingsChanged()
                onChanged()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    @SuppressLint("QueryPermissionsNeeded")
    internal fun showKidsModeAppPicker(
        kidsModeManager: com.guruswarupa.launch.managers.KidsModeManager,
        onResult: ((saved: Boolean) -> Unit)? = null
    ) {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val labels = resolved.map { it.loadLabel(pm).toString() }.toTypedArray()
        val packageNames = resolved.map { it.activityInfo.packageName }
        val currentlyAllowed = kidsModeManager.getAllowedApps()
        val checked = BooleanArray(packageNames.size) { packageNames[it] in currentlyAllowed }
        var saved = false

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.kids_mode_choose_apps)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save_button) { _, _ ->
                kidsModeManager.setAllowedApps(packageNames.filterIndexed { index, _ -> checked[index] }.toSet())
                saved = true
            }
            .setNegativeButton(R.string.cancel_button, null)
            .setOnDismissListener { onResult?.invoke(saved) }
            .show()
    }

    override fun onDestroy() {
        billingClient?.endConnection()
        billingClient = null
        aiAssistantPollHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WIDGETS_SECTION_EXPANDED, widgetsSectionExpanded)
        outState.putBoolean(STATE_NEWS_SECTION_EXPANDED, newsSectionExpanded)
        outState.putBoolean(STATE_SYSTEM_MONITOR_SECTION_EXPANDED, systemMonitorSectionExpanded)
    }

    internal fun triggerWallpaperPicker(themeId: String) {
        val theme = ThemeOption.PREDEFINED_THEMES.find { it.id == themeId } ?: return

        Toast.makeText(this, this.getString(R.string.toast_preparing_wallpaper_picker), Toast.LENGTH_SHORT).show()

        Glide.with(this as android.app.Activity)
            .asFile()
            .load(theme.wallpaperUrl)
            .into(object : CustomTarget<File>() {
                override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                    try {
                        val wallpaperFile = File(cacheDir, "theme_wallpaper.jpg")
                        resource.copyTo(wallpaperFile, overwrite = true)

                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this@SettingsActivity,
                            "$packageName.fileprovider",
                            wallpaperFile
                        )

                        val wm = WallpaperManager.getInstance(this@SettingsActivity)
                        try {

                            val intent = wm.getCropAndSetWallpaperIntent(uri)
                            startActivity(intent)
                        } catch (_: Exception) {

                            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                addCategory(Intent.CATEGORY_DEFAULT)
                                setDataAndType(uri, "image/*")
                                putExtra("mimeType", "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, "Set System Wallpaper"))
                        }

                        Toast.makeText(this@SettingsActivity, this@SettingsActivity.getString(R.string.toast_use_the_system_dialog_to_set_your_wallpaper), Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, this@SettingsActivity.getString(R.string.toast_failed_to_prepare_wallpaper, e.message), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    internal fun setupVersionInfo() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            findViewById<TextView>(R.id.version_text)?.text = "v$version"
        } catch (_: Exception) {
            findViewById<TextView>(R.id.version_text)?.text = "v1.0"
        }
    }

    internal fun setupWallpaper(demoThemeId: String?) {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        if (demoThemeId == null) {
            WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
        } else {
            WallpaperDisplayHelper.applyThemeWallpaper(wallpaperImageView, demoThemeId)
        }
    }

    internal fun setupThemeSection() {
        setupThemePaletteRow()
        setupThemeAccentGrid()
        setupOpaqueSurfacesSwitch()
    }

    internal fun setupOpaqueSurfacesSwitch() {
        val sw = findViewById<SwitchCompat>(R.id.opaque_surfaces_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, isChecked) }
            notifySettingsChanged()
            recreate()
        }
    }

    internal fun setupThemePaletteRow() {
        val row = findViewById<LinearLayout>(R.id.theme_palette_row)
        row.removeAllViews()
        val currentId = prefs.getString(Constants.Prefs.COLOR_THEME, null)
        ThemePalette.ALL.forEach { palette ->
            val card = layoutInflater.inflate(R.layout.item_theme_palette_card, row, false)
            card.findViewById<TextView>(R.id.palette_name).setText(palette.nameRes)
            card.findViewById<View>(R.id.palette_swatch_bg).setBackgroundColor(palette.previewBackground)
            card.findViewById<View>(R.id.palette_swatch_surface).background =
                GradientDrawable().apply {
                    setColor(palette.previewSurface)
                    cornerRadius = 8.dpToPx().toFloat()
                }
            card.findViewById<View>(R.id.palette_swatch_accent).background =
                GradientDrawable().apply {
                    setColor(palette.previewAccent)
                    shape = GradientDrawable.OVAL
                }
            card.findViewById<ImageView>(R.id.palette_check).isVisible =
                palette.id == (currentId ?: ThemePalette.NORD.id)
            card.setOnClickListener {
                if (palette.id == (prefs.getString(Constants.Prefs.COLOR_THEME, null) ?: ThemePalette.NORD.id)) return@setOnClickListener
                prefs.edit { putString(Constants.Prefs.COLOR_THEME, palette.id) }
                notifySettingsChanged()
                recreate()
            }
            row.addView(card)
        }
    }

    internal fun setupThemeAccentGrid() {
        val grid = findViewById<LinearLayout>(R.id.theme_accent_grid)
        grid.removeAllViews()
        val currentId = prefs.getString(Constants.Prefs.COLOR_ACCENT, null) ?: AccentSwatch.PALETTE.id
        AccentSwatch.ALL.forEach { accent ->
            val swatch = layoutInflater.inflate(R.layout.item_accent_swatch, grid, false)
            val fill = swatch.findViewById<View>(R.id.accent_swatch_fill)
            if (accent.previewColor != null) {
                fill.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent.previewColor)
                }
            } else {

                fill.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(2.dpToPx(), ThemeManager.color(this@SettingsActivity, R.attr.appOutline))
                }
            }
            swatch.findViewById<ImageView>(R.id.accent_check).isVisible = accent.id == currentId
            swatch.contentDescription = if (accent.id == AccentSwatch.PALETTE.id) {
                getString(R.string.accent_color)
            } else {
                accent.id
            }
            swatch.setOnClickListener {
                if (accent.id == (prefs.getString(Constants.Prefs.COLOR_ACCENT, null) ?: AccentSwatch.PALETTE.id)) return@setOnClickListener
                prefs.edit { putString(Constants.Prefs.COLOR_ACCENT, accent.id) }
                notifySettingsChanged()
                recreate()
            }
            grid.addView(swatch)
        }
    }

    internal fun setupAppearanceSection() {
        val displayHeader = findViewById<LinearLayout>(R.id.display_style_header)
        val displayContent = findViewById<LinearLayout>(R.id.display_style_content)
        val displayArrow = findViewById<TextView>(R.id.display_style_arrow)
        setupSectionToggle(displayHeader, displayContent, displayArrow)

        val gridBtn = findViewById<Button>(R.id.grid_option)
        val listBtn = findViewById<Button>(R.id.list_option)
        val stockBtn = findViewById<Button>(R.id.stock_option)
        val gridSection = findViewById<LinearLayout>(R.id.grid_columns_section)
        val gridValue = findViewById<TextView>(R.id.grid_columns_value)
        val gridSeek = findViewById<SeekBar>(R.id.grid_columns_seekbar)

        val minCols = resources.getInteger(R.integer.grid_columns_min)
        val maxCols = resources.getInteger(R.integer.grid_columns_max)
        var selectedCols = prefs.getInt(Constants.Prefs.GRID_COLUMNS, resources.getInteger(R.integer.app_grid_columns))
            .coerceIn(minCols, maxCols)

        var selectedStyle = prefs.getString(
            Constants.Prefs.VIEW_PREFERENCE,
            Constants.Prefs.VIEW_PREFERENCE_LIST
        ) ?: Constants.Prefs.VIEW_PREFERENCE_LIST

        gridSeek.max = maxCols - minCols
        gridSeek.progress = selectedCols - minCols
        gridValue.text = getString(R.string.apps_per_row_format, selectedCols)

        gridSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                selectedCols = minCols + p
                gridValue.text = getString(R.string.apps_per_row_format, selectedCols)
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.GRID_COLUMNS, selectedCols) }
                    notifySettingsChanged()
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val showAppNameInSection = findViewById<LinearLayout>(R.id.show_app_name_in_grid_section)
        val showAppNameSwitch = findViewById<SwitchCompat>(R.id.show_app_name_in_grid_switch)
        val hideAppIconInListSection = findViewById<LinearLayout>(R.id.hide_app_icon_in_list_section)
        val hideAppIconInListSwitch = findViewById<SwitchCompat>(R.id.hide_app_icon_in_list_switch)
        val notificationBadgesSwitch = findViewById<SwitchCompat>(R.id.notification_badges_switch)
        val kidsModeSwitch = findViewById<SwitchCompat>(R.id.kids_mode_switch)
        val kidsModeChooseAppsButton = findViewById<TextView>(R.id.kids_mode_choose_apps_button)
        val stockDrawerEnabledSection = findViewById<LinearLayout>(R.id.stock_drawer_enabled_section)
        val stockDrawerEnabledSwitch = findViewById<SwitchCompat>(R.id.stock_drawer_enabled_switch)
        val autoOrganizeFoldersButton = findViewById<TextView>(R.id.auto_organize_folders_button)
        autoOrganizeFoldersButton.setOnClickListener {
            prefs.edit { putBoolean(Constants.Prefs.AUTO_ORGANIZE_FOLDERS_PENDING, true) }
            notifySettingsChanged()
            Toast.makeText(this, getString(R.string.auto_organize_folders_done), Toast.LENGTH_SHORT).show()
        }
        val swipeRightAppButton = findViewById<TextView>(R.id.stock_swipe_right_app_button)
        fun refreshSwipeRightAppLabel() {
            val pkg = prefs.getString(Constants.Prefs.STOCK_SWIPE_RIGHT_APP, null).orEmpty()
            val label = if (pkg.isEmpty()) {
                getString(R.string.stock_swipe_right_app_none)
            } else {
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    getString(R.string.stock_swipe_right_app_none)
                }
            }
            swipeRightAppButton.text = getString(R.string.stock_swipe_right_app_format, label)
        }
        refreshSwipeRightAppLabel()
        swipeRightAppButton.setOnClickListener {
            showSwipeRightAppPicker { refreshSwipeRightAppLabel() }
        }
        val stockHotseatSection = findViewById<LinearLayout>(R.id.stock_hotseat_section)
        val stockHotseatValue = findViewById<TextView>(R.id.stock_hotseat_value)
        val stockHotseatSeek = findViewById<SeekBar>(R.id.stock_hotseat_seekbar)

        fun applyDisplayStyle(style: String) {
            selectedStyle = style
            updateDisplayStyleButtons(gridBtn, listBtn, stockBtn, style)
            val isGridLike = style == Constants.Prefs.VIEW_PREFERENCE_GRID || style == Constants.Prefs.VIEW_PREFERENCE_STOCK
            val isStock = style == Constants.Prefs.VIEW_PREFERENCE_STOCK
            gridSection.isVisible = isGridLike
            showAppNameInSection.isVisible = isGridLike
            hideAppIconInListSection.isVisible = style == Constants.Prefs.VIEW_PREFERENCE_LIST
            stockDrawerEnabledSection.isVisible = isStock
            stockHotseatSection.isVisible = isStock
            autoOrganizeFoldersButton.isVisible = isStock
            swipeRightAppButton.isVisible = isStock
        }

        applyDisplayStyle(selectedStyle)

        stockDrawerEnabledSwitch.isChecked = prefs.getBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, true)
        stockDrawerEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.STOCK_DRAWER_ENABLED, isChecked) }
            notifySettingsChanged()
        }

        val minHotseat = 3
        val maxHotseat = 5
        var selectedHotseatCount = prefs.getInt(Constants.Prefs.STOCK_HOTSEAT_COUNT, 4).coerceIn(minHotseat, maxHotseat)
        stockHotseatSeek.max = maxHotseat - minHotseat
        stockHotseatSeek.progress = selectedHotseatCount - minHotseat
        stockHotseatValue.text = getString(R.string.stock_hotseat_apps_format, selectedHotseatCount)
        stockHotseatSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                selectedHotseatCount = minHotseat + p
                stockHotseatValue.text = getString(R.string.stock_hotseat_apps_format, selectedHotseatCount)
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.STOCK_HOTSEAT_COUNT, selectedHotseatCount) }
                    notifySettingsChanged()
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val showAppNamesInGrid = prefs.getBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)
        showAppNameSwitch.isChecked = showAppNamesInGrid

        showAppNameSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, isChecked) }
            notifySettingsChanged()
        }

        val hideAppIconInList = prefs.getBoolean(Constants.Prefs.HIDE_APP_ICON_IN_LIST, false)
        hideAppIconInListSwitch.isChecked = hideAppIconInList

        hideAppIconInListSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.HIDE_APP_ICON_IN_LIST, isChecked) }
            notifySettingsChanged()
        }

        val notificationPermissionManager = com.guruswarupa.launch.core.PermissionManager(this, prefs)
        notificationBadgesSwitch.isChecked = notificationPermissionManager.isNotificationListenerServiceEnabled()
        notificationBadgesSwitch.setOnClickListener {
            notificationBadgesSwitch.isChecked = notificationPermissionManager.isNotificationListenerServiceEnabled()
            notificationPermissionManager.requestNotificationListenerPermission()
        }

        val kidsModeManager = com.guruswarupa.launch.managers.KidsModeManager(this)
        val kidsModeAppLockManager = com.guruswarupa.launch.managers.AppLockManager(this)
        kidsModeSwitch.isChecked = kidsModeManager.isActive()
        kidsModeChooseAppsButton.setOnClickListener { showKidsModeAppPicker(kidsModeManager) }
        kidsModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                kidsModeManager.setActive(false)
                return@setOnCheckedChangeListener
            }
            if (!kidsModeAppLockManager.isPinSet()) {
                Toast.makeText(this, getString(R.string.kids_mode_pin_required), Toast.LENGTH_LONG).show()
                kidsModeSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            val activate = {
                kidsModeManager.setActive(true)
                finish()
            }
            if (kidsModeManager.getAllowedApps().isEmpty()) {
                showKidsModeAppPicker(kidsModeManager) { saved ->
                    if (saved && kidsModeManager.getAllowedApps().isNotEmpty()) {
                        activate()
                    } else {
                        kidsModeSwitch.isChecked = false
                    }
                }
            } else {
                activate()
            }
        }

        gridBtn.setOnClickListener {
            applyDisplayStyle(Constants.Prefs.VIEW_PREFERENCE_GRID)
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_GRID) }
            notifySettingsChanged()
        }

        listBtn.setOnClickListener {
            applyDisplayStyle(Constants.Prefs.VIEW_PREFERENCE_LIST)
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST) }
            notifySettingsChanged()
        }

        stockBtn.setOnClickListener {
            applyDisplayStyle(Constants.Prefs.VIEW_PREFERENCE_STOCK)
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_STOCK) }
            notifySettingsChanged()
        }

        val iconSpinner = findViewById<Spinner>(R.id.icon_style_spinner)
        val iconSeek = findViewById<SeekBar>(R.id.icon_size_seekbar)
        val iconVal = findViewById<TextView>(R.id.icon_size_value)
        val shapes = arrayOf("Round", "Squircle", "Squared", "Teardrop", "Vortex", "Overlay")
        val values = arrayOf("round", "squircle", "squared", "teardrop", "vortex", "overlay")
        iconSpinner.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, shapes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        iconSpinner.setSelection(values.indexOf(prefs.getString(Constants.Prefs.ICON_STYLE, "squircle")).coerceAtLeast(0))
        iconSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.ICON_STYLE, values[pos]) }
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val currentSize = prefs.getInt(Constants.Prefs.ICON_SIZE, 40)
        iconSeek.max = 60
        iconSeek.progress = currentSize - 36
        iconVal.text = "${currentSize}dp"
        iconSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val size = p + 36
                iconVal.text = "${size}dp"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.ICON_SIZE, size) }
                    notifySettingsChanged()
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val homePageSpinner = findViewById<Spinner>(R.id.default_home_page_spinner)
        val rssEnabled = prefs.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)
        val widgetsEnabled = prefs.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
        val pageEntries = buildList {
            add(getString(R.string.home_page_wallpaper_left) to "wallpaper")
            add(getString(R.string.home_page_home_center) to "center")
            if (widgetsEnabled) add(getString(R.string.home_page_widgets_right) to "widgets")
            if (rssEnabled) add(getString(R.string.home_page_news_far_right) to "rss")
        }
        val pages = pageEntries.map { it.first }.toTypedArray()
        homePageSpinner.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, pages).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val defaultPageTarget = prefs.getString(Constants.Prefs.DEFAULT_HOME_PAGE_TARGET, null)
        val selectedIndex = pageEntries.indexOfFirst { it.second == defaultPageTarget }.takeIf { it >= 0 }
            ?: pageEntries.indexOfFirst { it.second == "center" }.takeIf { it >= 0 }
            ?: 0
        homePageSpinner.setSelection(selectedIndex)
        homePageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit {
                    putString(Constants.Prefs.DEFAULT_HOME_PAGE_TARGET, pageEntries[pos].second)
                    putInt(Constants.Prefs.DEFAULT_HOME_PAGE_INDEX, pos)
                }
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        setupLanguageSpinner()

        val wallHeader = findViewById<LinearLayout>(R.id.wallpaper_header)
        val wallContent = findViewById<LinearLayout>(R.id.wallpaper_content)
        val wallArrow = findViewById<TextView>(R.id.wallpaper_arrow)
        setupSectionToggle(wallHeader, wallContent, wallArrow)
        findViewById<View>(R.id.change_wallpaper_button).setOnClickListener { chooseWallpaper() }

        setupThemeSelection()

        val typoHeader = findViewById<LinearLayout>(R.id.typography_header)
        val typoContent = findViewById<LinearLayout>(R.id.typography_content)
        val typoArrow = findViewById<TextView>(R.id.typography_arrow)
        setupSectionToggle(typoHeader, typoContent, typoArrow)
        setupTypographySettings()
        setupIconPackSettings()

        findViewById<SwitchCompat>(R.id.clock_24_hour_switch).apply {
            fun applyClockSwitchColors(isEnabled: Boolean) {
                val color = if (isEnabled) ThemeManager.color(this@SettingsActivity, R.attr.appAccent) else ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary)
                thumbTintList = ColorStateList.valueOf(color)
                trackTintList = ColorStateList.valueOf(color)
            }

            isChecked = prefs.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)
            applyClockSwitchColors(isChecked)
            setOnCheckedChangeListener { _, isChecked ->
                applyClockSwitchColors(isChecked)
                prefs.edit { putBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, isChecked) }
                notifySettingsChanged()
            }
        }

        findViewById<SwitchCompat>(R.id.show_fast_scroller_switch).apply {
            fun applyFastScrollerSwitchColors(isEnabled: Boolean) {
                val color = if (isEnabled) ThemeManager.color(this@SettingsActivity, R.attr.appAccent) else ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary)
                thumbTintList = ColorStateList.valueOf(color)
                trackTintList = ColorStateList.valueOf(color)
            }

            isChecked = !prefs.getBoolean(Constants.Prefs.SHOW_FAST_SCROLLER, true)
            applyFastScrollerSwitchColors(isChecked)
            setOnCheckedChangeListener { _, isChecked ->
                applyFastScrollerSwitchColors(isChecked)
                prefs.edit { putBoolean(Constants.Prefs.SHOW_FAST_SCROLLER, !isChecked) }
                notifySettingsChanged()
            }
        }

        findViewById<SwitchCompat>(R.id.landscape_orientation_switch).apply {
            fun applyLandscapeSwitchColors(isEnabled: Boolean) {
                val color = if (isEnabled) ThemeManager.color(this@SettingsActivity, R.attr.appAccent) else ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary)
                thumbTintList = ColorStateList.valueOf(color)
                trackTintList = ColorStateList.valueOf(color)
            }

            isChecked = prefs.getBoolean(Constants.Prefs.LANDSCAPE_ORIENTATION_ENABLED, false)
            applyLandscapeSwitchColors(isChecked)
            setOnCheckedChangeListener { _, isChecked ->
                applyLandscapeSwitchColors(isChecked)
                prefs.edit { putBoolean(Constants.Prefs.LANDSCAPE_ORIENTATION_ENABLED, isChecked) }
                applyOrientationPreference()
                notifySettingsChanged()
            }
        }

        findViewById<SwitchCompat>(R.id.minimal_mode_switch).apply {
            fun applyMinimalModeSwitchColors(isEnabled: Boolean) {
                val color = if (isEnabled) ThemeManager.color(this@SettingsActivity, R.attr.appAccent) else ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary)
                thumbTintList = ColorStateList.valueOf(color)
                trackTintList = ColorStateList.valueOf(color)
            }

            isChecked = prefs.getBoolean(Constants.Prefs.MINIMAL_MODE_ENABLED, false)
            applyMinimalModeSwitchColors(isChecked)
            setOnCheckedChangeListener { _, isChecked ->
                applyMinimalModeSwitchColors(isChecked)
                prefs.edit { putBoolean(Constants.Prefs.MINIMAL_MODE_ENABLED, isChecked) }
                setupWallpaper(null)
                notifySettingsChanged()
            }
        }

        val translucencySeek = findViewById<SeekBar>(R.id.background_translucency_seekbar)
        val translucencyValue = findViewById<TextView>(R.id.background_translucency_value)
        val currentTranslucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)

        translucencySeek.max = 100
        translucencySeek.progress = currentTranslucency
        translucencyValue.text = "$currentTranslucency%"

        translucencySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                translucencyValue.text = "$p%"

                val alpha = (p * 255 / 100).coerceIn(0, 255)
                val scrimBase = ThemeManager.color(this@SettingsActivity, R.attr.appScrim)
                val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
                findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)

                if (f) {
                    prefs.edit { putInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, p) }
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    internal fun setupLanguageSpinner() {
        val languageSpinner = findViewById<Spinner>(R.id.language_spinner)
        val labels = AppLanguage.options.map { option ->
            if (option.tag.isEmpty()) getString(R.string.language_system_default) else option.autonym
        }.toTypedArray()
        languageSpinner.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.setSelection(AppLanguage.indexOf(AppLanguage.current()))
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val tag = AppLanguage.options[pos].tag
                if (tag != AppLanguage.current()) {
                    AppLanguage.apply(tag)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    internal fun setupThemeSelection() {
        val container = findViewById<LinearLayout>(R.id.theme_options_container)
        val applyBtn = findViewById<MaterialButton>(R.id.apply_theme_button)
        container.removeAllViews()

        if (selectedThemeCategory == null) {

            val categories = ThemeOption.PREDEFINED_THEMES.map { it.category }.distinct()

            val scrollContainer = SafeHorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            scrollContainer.addView(row)
            container.addView(scrollContainer)

            categories.forEach { category ->
                val categoryView = layoutInflater.inflate(R.layout.item_theme_option, row, false)
                val card = categoryView.findViewById<MaterialCardView>(R.id.theme_card)
                val name = categoryView.findViewById<TextView>(R.id.theme_name)
                val preview = categoryView.findViewById<ImageView>(R.id.theme_preview_image)

                name.text = category

                val isSelected = ThemeOption.PREDEFINED_THEMES.find { it.id == selectedThemeId }?.category == category

                if (isSelected) {
                    card.strokeColor = ThemeManager.color(this, R.attr.appAccent)
                    card.strokeWidth = 2.dpToPx()
                } else {
                    card.strokeColor = Color.TRANSPARENT
                    card.strokeWidth = 0
                }

                val firstThemeInCategory = ThemeOption.PREDEFINED_THEMES.find { it.category == category }
                if (firstThemeInCategory != null) {
                    WallpaperDisplayHelper.applyThemePreview(preview, firstThemeInCategory.id)
                }

                card.setOnClickListener {
                    selectedThemeCategory = category
                    setupThemeSelection()
                }
                row.addView(categoryView)
            }
            applyBtn.isVisible = false
        } else {
            val category = selectedThemeCategory ?: return
            val contentRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val backBtn = ImageButton(this).apply {
                setImageResource(R.drawable.ic_arrow_right)
                rotation = 180f
                setBackgroundResource(android.R.color.transparent)
                setColorFilter(ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary))
                setPadding(0, 0, 8, 0)
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    selectedThemeCategory = null
                    setupThemeSelection()
                }
            }
            contentRow.addView(backBtn)

            val scrollContainer = SafeHorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            scrollContainer.addView(row)
            contentRow.addView(scrollContainer)
            container.addView(contentRow)

            val themes = ThemeOption.PREDEFINED_THEMES.filter { it.category == category }

            themes.forEach { theme ->
                val themeView = layoutInflater.inflate(R.layout.item_theme_option, row, false)
                val card = themeView.findViewById<MaterialCardView>(R.id.theme_card)
                val name = themeView.findViewById<TextView>(R.id.theme_name)
                val preview = themeView.findViewById<ImageView>(R.id.theme_preview_image)

                name.text = theme.name
                WallpaperDisplayHelper.applyThemePreview(preview, theme.id)

                if (selectedThemeId == theme.id) {
                    card.strokeColor = ThemeManager.color(this, R.attr.appAccent)
                    card.strokeWidth = 2.dpToPx()
                } else {
                    card.strokeColor = Color.TRANSPARENT
                    card.strokeWidth = 0
                }

                card.setOnClickListener {
                    selectedThemeId = theme.id
                    prefs.edit { putString(Constants.Prefs.SELECTED_THEME, theme.id) }
                    setupThemeSelection()
                    setupWallpaper(theme.id)
                    hasUnsavedThemeChanges = true
                    notifySettingsChanged()
                }
                row.addView(themeView)
            }

            applyBtn.isVisible = themes.any { it.id == selectedThemeId }
        }

        applyBtn.setOnClickListener {
            hasUnsavedThemeChanges = false
            triggerWallpaperPicker(selectedThemeId)
        }
    }

    internal fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    internal fun setupActionsSection() {
        val wHeader = findViewById<LinearLayout>(R.id.widgets_settings_header)
        val wContent = findViewById<LinearLayout>(R.id.widgets_settings_content)
        val wArrow = findViewById<TextView>(R.id.widgets_settings_arrow)
        setupSectionToggle(wHeader, wContent, wArrow) { isExpanded ->
            widgetsSectionExpanded = isExpanded
        }
        applySectionExpandedState(wContent, wArrow, widgetsSectionExpanded)
        val widgetsEnabledSwitch = findViewById<SwitchCompat>(R.id.widgets_page_enabled_switch)
        val topWidgetEnabledSwitch = findViewById<SwitchCompat>(R.id.top_widget_enabled_switch)
        val wallpaperLyricsEnabledSwitch = findViewById<SwitchCompat>(R.id.wallpaper_lyrics_enabled_switch)
        val configureWidgetsButton = findViewById<View>(R.id.configure_widgets_button)
        fun applyWidgetsSwitchColors(isEnabled: Boolean) {
            val color = if (isEnabled) ThemeManager.color(this, R.attr.appAccent) else ThemeManager.color(this, R.attr.appTextPrimary)
            widgetsEnabledSwitch.thumbTintList = ColorStateList.valueOf(color)
            widgetsEnabledSwitch.trackTintList = ColorStateList.valueOf(color)
            configureWidgetsButton.alpha = if (isEnabled) 1f else 0.6f
            configureWidgetsButton.isEnabled = isEnabled
        }
        fun applyTopWidgetSwitchColors(isEnabled: Boolean) {
            val color = if (isEnabled) ThemeManager.color(this, R.attr.appAccent) else ThemeManager.color(this, R.attr.appTextPrimary)
            topWidgetEnabledSwitch.thumbTintList = ColorStateList.valueOf(color)
            topWidgetEnabledSwitch.trackTintList = ColorStateList.valueOf(color)
        }
        fun applyWallpaperLyricsSwitchColors(isEnabled: Boolean) {
            val color = if (isEnabled) ThemeManager.color(this, R.attr.appAccent) else ThemeManager.color(this, R.attr.appTextPrimary)
            wallpaperLyricsEnabledSwitch.thumbTintList = ColorStateList.valueOf(color)
            wallpaperLyricsEnabledSwitch.trackTintList = ColorStateList.valueOf(color)
        }

        widgetsEnabledSwitch.isChecked = prefs.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
        topWidgetEnabledSwitch.isChecked = prefs.getBoolean(Constants.Prefs.TOP_WIDGET_ENABLED, true)
        wallpaperLyricsEnabledSwitch.isChecked = prefs.getBoolean(Constants.Prefs.WALLPAPER_LYRICS_ENABLED, true)
        applyWidgetsSwitchColors(widgetsEnabledSwitch.isChecked)
        applyTopWidgetSwitchColors(topWidgetEnabledSwitch.isChecked)
        applyWallpaperLyricsSwitchColors(wallpaperLyricsEnabledSwitch.isChecked)
        widgetsEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            applyWidgetsSwitchColors(isChecked)
            prefs.edit { putBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, isChecked) }
            notifySettingsChanged()
            recreate()
        }
        topWidgetEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            applyTopWidgetSwitchColors(isChecked)
            prefs.edit { putBoolean(Constants.Prefs.TOP_WIDGET_ENABLED, isChecked) }
            notifySettingsChanged()
        }
        wallpaperLyricsEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            applyWallpaperLyricsSwitchColors(isChecked)
            prefs.edit { putBoolean(Constants.Prefs.WALLPAPER_LYRICS_ENABLED, isChecked) }
            notifySettingsChanged()
        }

        configureWidgetsButton.setOnClickListener {
            startActivity(Intent(this, WidgetConfigurationActivity::class.java))
        }

        val sHeader = findViewById<LinearLayout>(R.id.search_engine_header)
        val sContent = findViewById<LinearLayout>(R.id.search_engine_content)
        val sArrow = findViewById<TextView>(R.id.search_engine_arrow)
        setupSectionToggle(sHeader, sContent, sArrow)
        setupSearchEngine()
        setupSmartSuggestions()

        val aiHeader = findViewById<LinearLayout>(R.id.ai_assistant_header)
        val aiContent = findViewById<LinearLayout>(R.id.ai_assistant_content)
        val aiArrow = findViewById<TextView>(R.id.ai_assistant_arrow)
        setupSectionToggle(aiHeader, aiContent, aiArrow)
        setupAiAssistant()

        val qHeader = findViewById<LinearLayout>(R.id.quick_actions_header)
        val qContent = findViewById<LinearLayout>(R.id.quick_actions_content)
        val qArrow = findViewById<TextView>(R.id.quick_actions_arrow)
        setupSectionToggle(qHeader, qContent, qArrow)

        setupAccessibilityShortcut()
        setupEdgePanel()
        setupBackTap()
        setupShakeTorch()
        setupWalkDetection()
        setupExtraDimmer()
        setupNightMode()
        setupFlipToDnd()
        setupLockScreen()
        setupTourEntry()
        setupGrayscaleMode()

        findViewById<View>(R.id.config_control_center_button).setOnClickListener {
            startActivity(Intent(this, ControlCenterConfigActivity::class.java))
        }
        findViewById<View>(R.id.config_edge_panel_button).setOnClickListener {
            startActivity(Intent(this, EdgePanelConfigActivity::class.java))
        }
    }

    internal fun setupDockSettings() {
        val header = findViewById<LinearLayout>(R.id.dock_settings_header)
        val content = findViewById<LinearLayout>(R.id.dock_settings_content)
        val arrow = findViewById<TextView>(R.id.dock_settings_arrow)
        setupSectionToggle(header, content, arrow)

        val hideWorkProfileSwitch = findViewById<SwitchCompat>(R.id.dock_hide_work_profile_switch)
        val hideFocusModeSwitch = findViewById<SwitchCompat>(R.id.dock_hide_focus_mode_switch)
        val hideWorkspacesSwitch = findViewById<SwitchCompat>(R.id.dock_hide_workspaces_switch)

        fun applySwitchColors(isEnabled: Boolean, switch: SwitchCompat) {
            val color = if (isEnabled) ThemeManager.color(this, R.attr.appAccent) else ThemeManager.color(this, R.attr.appTextPrimary)
            switch.thumbTintList = ColorStateList.valueOf(color)
            switch.trackTintList = ColorStateList.valueOf(color)
        }

        hideWorkProfileSwitch.isChecked = prefs.getBoolean(Constants.Prefs.DOCK_HIDE_WORK_PROFILE, false)
        hideFocusModeSwitch.isChecked = prefs.getBoolean(Constants.Prefs.DOCK_HIDE_FOCUS_MODE, false)
        hideWorkspacesSwitch.isChecked = prefs.getBoolean(Constants.Prefs.DOCK_HIDE_WORKSPACES, false)

        applySwitchColors(hideWorkProfileSwitch.isChecked, hideWorkProfileSwitch)
        applySwitchColors(hideFocusModeSwitch.isChecked, hideFocusModeSwitch)
        applySwitchColors(hideWorkspacesSwitch.isChecked, hideWorkspacesSwitch)

        hideWorkProfileSwitch.setOnCheckedChangeListener { _, isChecked ->
            applySwitchColors(isChecked, hideWorkProfileSwitch)
            prefs.edit { putBoolean(Constants.Prefs.DOCK_HIDE_WORK_PROFILE, isChecked) }
            notifySettingsChanged()
        }

        hideFocusModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            applySwitchColors(isChecked, hideFocusModeSwitch)
            prefs.edit { putBoolean(Constants.Prefs.DOCK_HIDE_FOCUS_MODE, isChecked) }
            notifySettingsChanged()
        }

        hideWorkspacesSwitch.setOnCheckedChangeListener { _, isChecked ->
            applySwitchColors(isChecked, hideWorkspacesSwitch)
            prefs.edit { putBoolean(Constants.Prefs.DOCK_HIDE_WORKSPACES, isChecked) }
            notifySettingsChanged()
        }
    }

    internal fun setupNewsFeedSection() {
        val header = findViewById<LinearLayout>(R.id.news_feed_header)
        val content = findViewById<LinearLayout>(R.id.news_feed_content)
        val arrow = findViewById<TextView>(R.id.news_feed_arrow)
        setupSectionToggle(header, content, arrow) { isExpanded ->
            newsSectionExpanded = isExpanded
        }
        applySectionExpandedState(content, arrow, newsSectionExpanded)

        val enabledSwitch = findViewById<SwitchCompat>(R.id.news_feed_enabled_switch)
        val manageButton = findViewById<View>(R.id.manage_news_feeds_button)

        val disabledColor = ThemeManager.color(this, R.attr.appTextPrimary)
        val enabledColor = ThemeManager.color(this, R.attr.appAccent)
        fun applyNewsFeedSwitchColors(isEnabled: Boolean) {
            val color = if (isEnabled) enabledColor else disabledColor
            enabledSwitch.thumbTintList = ColorStateList.valueOf(color)
            enabledSwitch.trackTintList = ColorStateList.valueOf(color)
            manageButton.alpha = if (isEnabled) 1f else 0.6f
            manageButton.isEnabled = isEnabled
        }

        enabledSwitch.isChecked = prefs.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)
        applyNewsFeedSwitchColors(enabledSwitch.isChecked)
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            applyNewsFeedSwitchColors(isChecked)
            prefs.edit { putBoolean(Constants.Prefs.RSS_PAGE_ENABLED, isChecked) }
            notifySettingsChanged()
            recreate()
        }

        manageButton.setOnClickListener {
            startActivity(Intent(this, RssFeedSettingsActivity::class.java))
        }
    }

    internal fun setupAppTimerSection() {
        val tHeader = findViewById<LinearLayout>(R.id.app_timer_header)
        val tContent = findViewById<LinearLayout>(R.id.app_timer_content)
        val tArrow = findViewById<TextView>(R.id.app_timer_arrow)
        setupSectionToggle(tHeader, tContent, tArrow)

        findViewById<View>(R.id.manage_app_timers_button).setOnClickListener {
            startActivity(Intent(this, AppTimerManagementActivity::class.java))
        }
    }

    internal fun setupSecuritySection() {
        val h = findViewById<LinearLayout>(R.id.app_lock_header)
        val c = findViewById<LinearLayout>(R.id.app_lock_content)
        val a = findViewById<TextView>(R.id.app_lock_arrow)
        setupSectionToggle(h, c, a)

        findViewById<View>(R.id.app_lock_button).setOnClickListener { startActivity(Intent(this, AppLockSettingsActivity::class.java)) }
        findViewById<View>(R.id.launch_apps_button).setOnClickListener { startActivity(Intent(this, LaunchAppsActivity::class.java)) }
        findViewById<View>(R.id.bloat_button).setOnClickListener { startActivity(Intent(this, BloatwareActivity::class.java)) }
        findViewById<View>(R.id.hidden_apps_button).setOnClickListener { startActivity(Intent(this, HiddenAppsSettingsActivity::class.java)) }
        findViewById<View>(R.id.privacy_dashboard_button).setOnClickListener { startActivity(Intent(this, PrivacyDashboardActivity::class.java)) }
    }

    internal fun setupFileTransferSection() {
        setupSectionToggle(
            findViewById<View>(R.id.file_transfer_header),
            findViewById<View>(R.id.file_transfer_content),
            findViewById<TextView>(R.id.file_transfer_arrow),
        )
        findViewById<View>(R.id.file_transfer_button).setOnClickListener {
            startActivity(Intent(this, com.guruswarupa.launch.transfer.ui.TransferActivity::class.java))
        }
    }

    internal fun setupSystemMonitorSection() {
        val header = findViewById<View>(R.id.system_monitor_header)
        val content = findViewById<View>(R.id.system_monitor_content)
        val arrow = findViewById<TextView>(R.id.system_monitor_arrow)
        val button = findViewById<Button>(R.id.system_monitor_button)

        setupSectionToggle(header, content, arrow) { expanded ->
            systemMonitorSectionExpanded = expanded
        }
        applySectionExpandedState(content, arrow, systemMonitorSectionExpanded)

        button.setOnClickListener {
            startActivity(Intent(this, SystemMonitorActivity::class.java))
        }
        findViewById<Button>(R.id.usage_recap_button).setOnClickListener {
            startActivity(Intent(this, UsageRecapActivity::class.java))
        }
    }

    internal fun setupWebAppsSection() {
        val h = findViewById<LinearLayout>(R.id.web_apps_header)
        val c = findViewById<LinearLayout>(R.id.web_apps_content)
        val a = findViewById<TextView>(R.id.web_apps_arrow)
        setupSectionToggle(h, c, a)
        findViewById<View>(R.id.web_apps_button).setOnClickListener { startActivity(Intent(this, WebAppSettingsActivity::class.java)) }
    }

    internal fun setupMaintenanceSection() {
        val bHeader = findViewById<LinearLayout>(R.id.backup_restore_header)
        val bContent = findViewById<LinearLayout>(R.id.backup_restore_content)
        val a = findViewById<TextView>(R.id.backup_restore_arrow)
        setupSectionToggle(bHeader, bContent, a)

        findViewById<View>(R.id.export_settings_button).setOnClickListener { exportSettings() }
        findViewById<View>(R.id.import_settings_button).setOnClickListener { importSettings() }
        findViewById<View>(R.id.share_theme_button).setOnClickListener {
            val shareIntent = ThemeShareHelper.createShareIntent(this, prefs)
            if (shareIntent != null) {
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_theme)))
            }
        }
        findViewById<View>(R.id.import_theme_button).setOnClickListener {
            importThemeLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            })
        }


        val lHeader = findViewById<LinearLayout>(R.id.launcher_header)
        val lContent = findViewById<LinearLayout>(R.id.launcher_content)
        val lArrow = findViewById<TextView>(R.id.launcher_arrow)
        setupSectionToggle(lHeader, lContent, lArrow)

        findViewById<View>(R.id.restart_launcher_button).setOnClickListener { restartLauncher() }
        findViewById<View>(R.id.clear_cache_button).setOnClickListener { clearCache() }
        findViewById<View>(R.id.clear_data_button).setOnClickListener { clearData() }

        val pHeader = findViewById<LinearLayout>(R.id.permissions_header)
        val pContent = findViewById<LinearLayout>(R.id.permissions_content)
        val pArrow = findViewById<TextView>(R.id.permissions_arrow)
        setupSectionToggle(pHeader, pContent, pArrow)

        findViewById<View>(R.id.check_permissions_button).setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        findViewById<View>(R.id.app_info_button).setOnClickListener { openAppInfo() }
    }

    internal fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_open_app_info), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun openSupportSectionIfRequested() {
        if (!intent.getBooleanExtra(EXTRA_OPEN_SUPPORT_SECTION, false)) {
            return
        }
        val supportHeader = findViewById<LinearLayout>(R.id.support_header)
        val supportContent = findViewById<LinearLayout>(R.id.support_content)
        val scrollView = findViewById<ScrollView>(R.id.settings_scroll_view)
        val supportCard = supportHeader.parent?.parent as? View ?: supportHeader
        intent.removeExtra(EXTRA_OPEN_SUPPORT_SECTION)
        supportHeader.post {
            if (supportContent.visibility != View.VISIBLE) {
                supportHeader.performClick()
            }
            scrollView.postDelayed({
                supportCard.requestFocus()
                scrollView.smoothScrollTo(0, supportCard.top)
            }, 180)
        }
    }

    internal fun openAiSectionIfRequested() {
        if (!intent.getBooleanExtra(EXTRA_OPEN_AI_SECTION, false)) {
            return
        }
        val aiHeader = findViewById<LinearLayout>(R.id.ai_assistant_header)
        val aiContent = findViewById<LinearLayout>(R.id.ai_assistant_content)
        val scrollView = findViewById<ScrollView>(R.id.settings_scroll_view)
        val aiCard = aiHeader.parent?.parent as? View ?: aiHeader
        intent.removeExtra(EXTRA_OPEN_AI_SECTION)
        aiHeader.post {
            if (aiContent.visibility != View.VISIBLE) {
                aiHeader.performClick()
            }
            scrollView.postDelayed({
                aiCard.requestFocus()
                scrollView.smoothScrollTo(0, aiCard.top)
            }, 180)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { handlePurchase(it, true) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Toast.makeText(this, getString(R.string.support_purchase_canceled), Toast.LENGTH_SHORT).show()
            }
            else -> handleBillingError(billingResult)
        }
    }

    internal fun setupSectionToggle(header: View, content: View, arrow: TextView, onToggle: ((Boolean) -> Unit)? = null) {
        header.setOnClickListener {
            val visible = content.visibility == View.VISIBLE
            val expanded = !visible
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(250).start()
            onToggle?.invoke(expanded)
        }
    }

    internal fun applySectionExpandedState(content: View, arrow: TextView, isExpanded: Boolean) {
        content.visibility = if (isExpanded) View.VISIBLE else View.GONE
        arrow.rotation = if (isExpanded) 180f else 0f
    }

    internal fun updateDisplayStyleButtons(grid: Button, list: Button, stock: Button, style: String) {
        fun apply(button: Button, isSelected: Boolean) {
            button.alpha = if (isSelected) 1.0f else 0.4f
            button.setBackgroundResource(if (isSelected) R.drawable.settings_card_background else 0)
        }
        apply(grid, style == Constants.Prefs.VIEW_PREFERENCE_GRID)
        apply(list, style == Constants.Prefs.VIEW_PREFERENCE_LIST)
        apply(stock, style == Constants.Prefs.VIEW_PREFERENCE_STOCK)
    }

    internal fun setupSearchEngine() {
        val s = findViewById<Spinner>(R.id.search_engine_spinner)
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Ecosia", "Brave", "Startpage", "Yahoo", "Qwant")
        s.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, engines).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        s.setSelection(engines.indexOf(prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")).coerceAtLeast(0))
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.SEARCH_ENGINE, engines[pos]) }
                notifySettingsChanged()
            } override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    internal fun setupSmartSuggestions() {
        val sw = findViewById<SwitchCompat>(R.id.smart_suggestions_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.SMART_SUGGESTIONS_ENABLED, true)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SMART_SUGGESTIONS_ENABLED, isChecked) }
            notifySettingsChanged()
        }
    }

    internal fun formatSize(bytes: Long): String = "${bytes / (1024 * 1024)} MB"

    internal class ModelRowViews(
        val root: View,
        val name: TextView,
        val badge: View,
        val description: TextView,
        val status: TextView,
        val progress: ProgressBar,
        val actionButton: Button
    )

    internal val aiModelRows = LinkedHashMap<String, ModelRowViews>()
    internal val aiWebProviderRows = LinkedHashMap<String, ModelRowViews>()

    internal fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(packageName) })
    }

    internal fun hasAccessibilityServicePermission(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(ComponentName(this, ScreenLockAccessibilityService::class.java).flattenToString())
    }

    internal fun showPermissionDialog(t: String, m: String, onP: () -> Unit) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme).setTitle(t).setMessage(m).setPositiveButton(getString(R.string.settings)) { _, _ -> onP() }.setNegativeButton(getString(R.string.cancel_button), null).show()
    }

    internal fun chooseWallpaper() { wallpaperLauncher.launch(Intent(Intent.ACTION_SET_WALLPAPER)) }

    internal fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }

    internal fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<ScrollView>(R.id.settings_scroll_view).setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    internal fun applyOrientationPreference() {
        requestedOrientation = if (prefs.getBoolean(Constants.Prefs.LANDSCAPE_ORIENTATION_ENABLED, false)) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    internal fun restartLauncher() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.restart_launcher))
            .setMessage(getString(R.string.dlg_are_you_sure_you_want_to_restart_the_launcher))
            .setPositiveButton(getString(R.string.dlg_restart)) { _, _ ->
                packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                    intent.component?.let { component ->
                        startActivity(Intent.makeRestartActivityTask(component))
                        finish()
                    }
                }
            }
            .setNegativeButton(getString(R.string.dlg_later), null)
            .show()
    }

    internal fun clearCache() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.clear_temporary_cache))
            .setMessage(getString(R.string.dlg_this_will_remove_temporary_app_data_and_cached_i))
            .setPositiveButton(getString(R.string.weather_condition_clear)) { _, _ ->
                cacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
                Toast.makeText(this, this.getString(R.string.toast_cache_cleared_successfully), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    internal fun clearData() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.factory_reset_launcher))
            .setMessage(getString(R.string.dlg_warning_this_will_permanently_delete_all_your_se))
            .setPositiveButton(getString(R.string.dlg_reset_everything)) { _, _ ->
                (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
            }
            .setNegativeButton(getString(R.string.dlg_keep_data), null)
            .show()
    }

    internal fun sendFeedback() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri(); putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "Launch Feedback") }, "Feedback")) } catch (_: Exception) {}
    }

    internal fun showUnsavedChangesDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_unsaved_changes))
            .setMessage(getString(R.string.dlg_you_have_selected_a_new_theme_but_haven_t_applie))
            .setPositiveButton(getString(R.string.dlg_leave_without_applying)) { _, _ -> onConfirm() }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    override fun onBackPressed() {
        if (hasUnsavedThemeChanges) {
            showUnsavedChangesDialog {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

class ThemedArrayAdapter(
    context: Context,
    resource: Int,
    objects: Array<String>,
    internal val itemColors: Array<String>? = null
) : ArrayAdapter<String>(context, resource, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        applyThemeColor(view, position)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        applyThemeColor(view, position)
        return view
    }

    internal fun applyThemeColor(view: View, position: Int) {
        if (view is TextView) {
            val color = if (itemColors != null && position < itemColors.size) {
                val colorStr = itemColors[position]
                if (colorStr == Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT) {

                    ThemeManager.color(context, R.attr.appTextPrimary)
                } else {
                    try { Color.parseColor(colorStr) } catch (_: Exception) { ThemeManager.color(context, R.attr.appTextPrimary) }
                }
            } else {
                TypographyManager.getConfiguredFontColor(context) ?: ThemeManager.color(context, R.attr.appTextPrimary)
            }
            view.setTextColor(color)
        }
    }
}
