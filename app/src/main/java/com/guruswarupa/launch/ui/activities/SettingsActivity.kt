package com.guruswarupa.launch.ui.activities

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
import com.guruswarupa.launch.utils.IconPackManager
import com.guruswarupa.launch.utils.AppLanguage
import com.guruwarupa.launch.ui.activities.SettingsBackupHelper
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
        private const val STATE_WIDGETS_SECTION_EXPANDED = "state_widgets_section_expanded"
        private const val STATE_NEWS_SECTION_EXPANDED = "state_news_section_expanded"
        private const val STATE_SYSTEM_MONITOR_SECTION_EXPANDED = "state_system_monitor_section_expanded"
    }

    @javax.inject.Inject
    lateinit var deviceCapability: DeviceCapability

    @javax.inject.Inject
    lateinit var modelDownloadManager: ModelDownloadManager

    private val aiAssistantPollHandler = Handler(Looper.getMainLooper())
    private var aiAssistantPolling = false

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    private class SettingsHandler(activity: SettingsActivity) : Handler(Looper.getMainLooper()) {
        private val weakActivity = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            val activity = weakActivity.get() ?: return
        }
    }

    private val handler = SettingsHandler(this)
    private var selectedThemeId: String = "stardust"
    private var selectedThemeCategory: String? = null
    private var hasUnsavedThemeChanges = false
    private var widgetsSectionExpanded = false
    private var newsSectionExpanded = false
    private var systemMonitorSectionExpanded = false
    private var billingClient: BillingClient? = null
    private val supportProducts = linkedMapOf(
        "support_49" to R.id.support_49,
        "support_99" to R.id.support_99,
        "support_199" to R.id.support_199,
        "support_299" to R.id.support_299
    )
    private val supportProductDetails = LinkedHashMap<String, ProductDetails>()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { exportSettingsToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { importSettingsFromFile(it) }
    }

    private val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setupWallpaper(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setupMaintenanceSection()
        setupSupportSection()
        setupVersionInfo()
        openSupportSectionIfRequested()
    }

    override fun onResume() {
        super.onResume()
        applyOrientationPreference()
        if (billingClient?.isReady == true) {
            queryExistingSupportPurchases()
        }
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

    private fun triggerWallpaperPicker(themeId: String) {
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

    private fun setupVersionInfo() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            findViewById<TextView>(R.id.version_text)?.text = "v$version"
        } catch (_: Exception) {
            findViewById<TextView>(R.id.version_text)?.text = "v1.0"
        }
    }

    private fun setupWallpaper(demoThemeId: String?) {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        if (demoThemeId == null) {
            WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
        } else {
            WallpaperDisplayHelper.applyThemeWallpaper(wallpaperImageView, demoThemeId)
        }
    }

    private fun setupThemeSection() {
        setupThemePaletteRow()
        setupThemeAccentGrid()
        setupOpaqueSurfacesSwitch()
    }

    private fun setupOpaqueSurfacesSwitch() {
        val sw = findViewById<SwitchCompat>(R.id.opaque_surfaces_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, isChecked) }
            notifySettingsChanged()
            recreate()
        }
    }

    private fun setupThemePaletteRow() {
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

    private fun setupThemeAccentGrid() {
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
                // The "palette" sentinel: an outline-only ring meaning "use the palette's own
                // native accent" rather than a solid swatch color.
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

    private fun setupAppearanceSection() {
        val displayHeader = findViewById<LinearLayout>(R.id.display_style_header)
        val displayContent = findViewById<LinearLayout>(R.id.display_style_content)
        val displayArrow = findViewById<TextView>(R.id.display_style_arrow)
        setupSectionToggle(displayHeader, displayContent, displayArrow)

        val gridBtn = findViewById<Button>(R.id.grid_option)
        val listBtn = findViewById<Button>(R.id.list_option)
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

        updateDisplayStyleButtons(gridBtn, listBtn, selectedStyle)
        gridSection.isVisible = selectedStyle == Constants.Prefs.VIEW_PREFERENCE_GRID

        listBtn.setOnClickListener {
            selectedStyle = Constants.Prefs.VIEW_PREFERENCE_LIST
            updateDisplayStyleButtons(gridBtn, listBtn, Constants.Prefs.VIEW_PREFERENCE_LIST)
            gridSection.isVisible = false
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST) }
            notifySettingsChanged()
        }


        val showAppNameInSection = findViewById<LinearLayout>(R.id.show_app_name_in_grid_section)
        val showAppNameSwitch = findViewById<SwitchCompat>(R.id.show_app_name_in_grid_switch)
        showAppNameInSection.isVisible = selectedStyle == Constants.Prefs.VIEW_PREFERENCE_GRID

        val showAppNamesInGrid = prefs.getBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)
        showAppNameSwitch.isChecked = showAppNamesInGrid

        showAppNameSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, isChecked) }
            notifySettingsChanged()
        }

        val hideAppIconInListSection = findViewById<LinearLayout>(R.id.hide_app_icon_in_list_section)
        val hideAppIconInListSwitch = findViewById<SwitchCompat>(R.id.hide_app_icon_in_list_switch)
        hideAppIconInListSection.isVisible = selectedStyle == Constants.Prefs.VIEW_PREFERENCE_LIST

        val hideAppIconInList = prefs.getBoolean(Constants.Prefs.HIDE_APP_ICON_IN_LIST, false)
        hideAppIconInListSwitch.isChecked = hideAppIconInList

        hideAppIconInListSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.HIDE_APP_ICON_IN_LIST, isChecked) }
            notifySettingsChanged()
        }


        gridBtn.setOnClickListener {
            updateDisplayStyleButtons(gridBtn, listBtn, Constants.Prefs.VIEW_PREFERENCE_GRID)
            gridSection.isVisible = true
            showAppNameInSection.isVisible = true
            hideAppIconInListSection.isVisible = false
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_GRID) }
            notifySettingsChanged()
        }

        listBtn.setOnClickListener {
            updateDisplayStyleButtons(gridBtn, listBtn, Constants.Prefs.VIEW_PREFERENCE_LIST)
            gridSection.isVisible = false
            showAppNameInSection.isVisible = false
            hideAppIconInListSection.isVisible = true
            prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST) }
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

    private fun setupLanguageSpinner() {
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

    private fun setupThemeSelection() {
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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupActionsSection() {
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
        setupGrayscaleMode()

        findViewById<View>(R.id.config_control_center_button).setOnClickListener {
            startActivity(Intent(this, ControlCenterConfigActivity::class.java))
        }
        findViewById<View>(R.id.config_edge_panel_button).setOnClickListener {
            startActivity(Intent(this, EdgePanelConfigActivity::class.java))
        }
    }

    private fun setupDockSettings() {
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

    private fun setupNewsFeedSection() {
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

    private fun setupAppTimerSection() {
        val tHeader = findViewById<LinearLayout>(R.id.app_timer_header)
        val tContent = findViewById<LinearLayout>(R.id.app_timer_content)
        val tArrow = findViewById<TextView>(R.id.app_timer_arrow)
        setupSectionToggle(tHeader, tContent, tArrow)

        findViewById<View>(R.id.manage_app_timers_button).setOnClickListener {
            startActivity(Intent(this, AppTimerManagementActivity::class.java))
        }
    }

    private fun setupSecuritySection() {
        val h = findViewById<LinearLayout>(R.id.app_lock_header)
        val c = findViewById<LinearLayout>(R.id.app_lock_content)
        val a = findViewById<TextView>(R.id.app_lock_arrow)
        setupSectionToggle(h, c, a)

        findViewById<View>(R.id.app_lock_button).setOnClickListener { startActivity(Intent(this, AppLockSettingsActivity::class.java)) }
        findViewById<View>(R.id.hidden_apps_button).setOnClickListener { startActivity(Intent(this, HiddenAppsSettingsActivity::class.java)) }
        findViewById<View>(R.id.privacy_dashboard_button).setOnClickListener { startActivity(Intent(this, PrivacyDashboardActivity::class.java)) }
    }

    private fun setupSystemMonitorSection() {
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
    }

    private fun setupWebAppsSection() {
        val h = findViewById<LinearLayout>(R.id.web_apps_header)
        val c = findViewById<LinearLayout>(R.id.web_apps_content)
        val a = findViewById<TextView>(R.id.web_apps_arrow)
        setupSectionToggle(h, c, a)
        findViewById<View>(R.id.web_apps_button).setOnClickListener { startActivity(Intent(this, WebAppSettingsActivity::class.java)) }
    }

    private fun setupMaintenanceSection() {
        val bHeader = findViewById<LinearLayout>(R.id.backup_restore_header)
        val bContent = findViewById<LinearLayout>(R.id.backup_restore_content)
        val a = findViewById<TextView>(R.id.backup_restore_arrow)
        setupSectionToggle(bHeader, bContent, a)

        findViewById<View>(R.id.export_settings_button).setOnClickListener { exportSettings() }
        findViewById<View>(R.id.import_settings_button).setOnClickListener { importSettings() }

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

    private fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_open_app_info), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSupportSection() {
        val h = findViewById<LinearLayout>(R.id.support_header)
        val c = findViewById<LinearLayout>(R.id.support_content)
        val a = findViewById<TextView>(R.id.support_arrow)
        setupSectionToggle(h, c, a)
        findViewById<View>(R.id.feedback_button).setOnClickListener { sendFeedback() }
        updateSupporterBadge()
        supportProducts.forEach { (productId, buttonId) ->
            findViewById<Button>(buttonId).apply {
                text = getString(R.string.support_button_loading)
                isEnabled = false
                setOnClickListener { launchSupportPurchase(productId) }
            }
        }
        findViewById<TextView>(R.id.github_links_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = Html.fromHtml(getString(R.string.github_project_links), Html.FROM_HTML_MODE_COMPACT)
        }
        initializeBilling()
    }

    private fun openSupportSectionIfRequested() {
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

    private fun initializeBilling() {
        if (billingClient?.isReady == true) {
            querySupportProductDetails()
            queryExistingSupportPurchases()
            return
        }
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .enableAutoServiceReconnection()
                .build()
        }
        setSupportButtonsLoading()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySupportProductDetails()
                    queryExistingSupportPurchases()
                } else {
                    handleBillingError(billingResult, showToast = false)
                }
            }

            override fun onBillingServiceDisconnected() {
                setSupportButtonsUnavailable(getString(R.string.support_billing_unavailable))
            }
        })
    }

    private fun querySupportProductDetails() {
        val billing = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                supportProducts.keys.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()
        billing.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                handleBillingError(billingResult, showToast = false)
                return@queryProductDetailsAsync
            }
            runOnUiThread {
                supportProductDetails.clear()
                queryResult.productDetailsList.forEach { details ->
                    supportProductDetails[details.productId] = details
                }
                updateSupportButtons()
            }
        }
    }

    private fun updateSupportButtons() {
        supportProducts.forEach { (productId, buttonId) ->
            val button = findViewById<Button>(buttonId)
            val offer = supportProductDetails[productId]?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            if (offer != null) {
                button.text = getString(R.string.support_button_buy, offer.formattedPrice)
                button.isEnabled = true
                button.alpha = 1f
            } else {
                button.text = getString(R.string.support_button_unavailable)
                button.isEnabled = false
                button.alpha = 0.6f
            }
        }
    }

    private fun setSupportButtonsLoading() {
        supportProducts.values.forEach { buttonId ->
            findViewById<Button>(buttonId).apply {
                text = getString(R.string.support_button_loading)
                isEnabled = false
                alpha = 0.7f
            }
        }
    }

    private fun setSupportButtonsUnavailable(label: String) {
        supportProducts.values.forEach { buttonId ->
            findViewById<Button>(buttonId).apply {
                text = label
                isEnabled = false
                alpha = 0.6f
            }
        }
    }

    private fun launchSupportPurchase(productId: String) {
        val billing = billingClient
        if (billing == null || !billing.isReady) {
            initializeBilling()
            Toast.makeText(this, getString(R.string.support_billing_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val details = supportProductDetails[productId]
        val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
        val offerToken = offer?.offerToken
        if (details == null || offer == null || offerToken.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.support_option_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val billingResult = billing.launchBillingFlow(
            this,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()
        )
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            handleBillingError(billingResult)
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

    private fun queryExistingSupportPurchases() {
        val billing = billingClient ?: return
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases
                    .filter { purchase -> purchase.products.any(supportProducts::containsKey) }
                    .forEach { purchase -> handlePurchase(purchase, false) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase, notifyUser: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                billingClient?.consumeAsync(
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result, _ ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        prefs.edit {
                            putBoolean(Constants.Prefs.SUPPORTER_BADGE_EARNED, true)
                            putBoolean(Constants.Prefs.DONATION_PROMPT_SHOWN, true)
                        }
                        runOnUiThread {
                            updateSupporterBadge()
                            if (notifyUser) {
                                showSupportThankYouDialog()
                            }
                        }
                    } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        handleBillingError(result, showToast = notifyUser)
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> {
                if (notifyUser) {
                    Toast.makeText(this, getString(R.string.support_purchase_pending), Toast.LENGTH_SHORT).show()
                }
                updatePendingSupportButtons(purchase)
            }
            else -> Unit
        }
    }

    private fun showSupportThankYouDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_support_thank_you, null)
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.support_thank_you_close), null)
            .show()
    }

    private fun updateSupporterBadge() {
        findViewById<TextView>(R.id.support_badge)?.visibility =
            if (prefs.getBoolean(Constants.Prefs.SUPPORTER_BADGE_EARNED, false)) View.VISIBLE else View.GONE
    }

    private fun updatePendingSupportButtons(purchase: Purchase) {
        purchase.products.forEach { productId ->
            val buttonId = supportProducts[productId] ?: return@forEach
            findViewById<Button>(buttonId).apply {
                text = getString(R.string.support_button_pending)
                isEnabled = false
                alpha = 0.7f
            }
        }
    }

    private fun handleBillingError(billingResult: BillingResult, showToast: Boolean = true) {
        val messageRes = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> R.string.support_billing_unavailable
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> R.string.support_network_error
            BillingClient.BillingResponseCode.USER_CANCELED -> R.string.support_purchase_canceled
            else -> R.string.support_purchase_failed
        }
        runOnUiThread {
            if (messageRes == R.string.support_billing_unavailable || messageRes == R.string.support_network_error) {
                setSupportButtonsUnavailable(getString(R.string.support_button_unavailable))
            }
            if (showToast) {
                Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSectionToggle(header: View, content: View, arrow: TextView, onToggle: ((Boolean) -> Unit)? = null) {
        header.setOnClickListener {
            val visible = content.visibility == View.VISIBLE
            val expanded = !visible
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(250).start()
            onToggle?.invoke(expanded)
        }
    }

    private fun applySectionExpandedState(content: View, arrow: TextView, isExpanded: Boolean) {
        content.visibility = if (isExpanded) View.VISIBLE else View.GONE
        arrow.rotation = if (isExpanded) 180f else 0f
    }

    private fun updateDisplayStyleButtons(grid: Button, list: Button, style: String) {
        val isGrid = style == "grid"
        grid.alpha = if (isGrid) 1.0f else 0.4f
        grid.setBackgroundResource(if (isGrid) R.drawable.settings_card_background else 0)
        list.alpha = if (!isGrid) 1.0f else 0.4f
        list.setBackgroundResource(if (!isGrid) R.drawable.settings_card_background else 0)
    }

    private fun setupAccessibilityShortcut() {
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

    private fun setupEdgePanel() {
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

    private fun setupBackTap() {
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

    private fun setupShakeTorch() {
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

    private fun setupWalkDetection() {
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

    private fun setupExtraDimmer() {
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
                    if (sw.isChecked) ScreenDimmerService.updateDimLevel(this@SettingsActivity, p)
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupNightMode() {
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
                    if (sw.isChecked) NightModeService.updateIntensity(this@SettingsActivity, p)
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupFlipToDnd() {
        val sw = findViewById<SwitchCompat>(R.id.flip_dnd_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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

    private fun setupGrayscaleMode() {
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

    private fun showProtectedPermissionDialog() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_permission_required))
            .setMessage(getString(R.string.dlg_this_feature_requires_the_write_secure_settings) +
                    "Please run this command via ADB:\n\n" +
                    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            .setPositiveButton(getString(R.string.dlg_copy_command)) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, this.getString(R.string.toast_command_copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.support_thank_you_close), null)
            .show()
    }

    private fun setupSearchEngine() {
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

    private fun setupSmartSuggestions() {
        val sw = findViewById<SwitchCompat>(R.id.smart_suggestions_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.SMART_SUGGESTIONS_ENABLED, true)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SMART_SUGGESTIONS_ENABLED, isChecked) }
            notifySettingsChanged()
        }
    }

    private fun formatSize(bytes: Long): String = "${bytes / (1024 * 1024)} MB"

    private class ModelRowViews(
        val root: View,
        val name: TextView,
        val badge: View,
        val description: TextView,
        val status: TextView,
        val progress: ProgressBar,
        val actionButton: Button
    )

    private val aiModelRows = LinkedHashMap<String, ModelRowViews>()
    private val aiWebProviderRows = LinkedHashMap<String, ModelRowViews>()

    private fun selectedModelId(): String =
        prefs.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_MODEL_ID, null) ?: AssistantModel.DEFAULT.id

    private fun isWebSourceSelected(): Boolean =
        prefs.getString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_ON_DEVICE) ==
            Constants.Prefs.AI_ASSISTANT_SOURCE_WEB

    private fun selectedWebProviderId(): String? =
        prefs.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, null)

    private fun setupAiAssistant() {
        val sw = findViewById<SwitchCompat>(R.id.ai_assistant_switch)
        val statusText = findViewById<TextView>(R.id.ai_assistant_status_text)
        val licenseText = findViewById<TextView>(R.id.ai_assistant_license_text)

        licenseText.text = getString(R.string.ai_assistant_license_notice)

        sw.isChecked = prefs.getBoolean(Constants.Prefs.AI_ASSISTANT_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.AI_ASSISTANT_ENABLED, isChecked) }
            notifySettingsChanged()
        }

        // Web providers need no local model, so they're offered on every device — only the
        // on-device model list is gated on capability.
        val modelListLabel = findViewById<View>(R.id.ai_assistant_choose_model_label)
        val modelList = findViewById<LinearLayout>(R.id.ai_assistant_model_list)
        val capability = deviceCapability.check()
        if (capability is DeviceCapabilityResult.Unsupported) {
            modelListLabel.isVisible = false
            modelList.isVisible = false
            statusText.isVisible = true
            statusText.text = when (capability.reason) {
                DeviceCapabilityResult.Reason.LOW_RAM -> getString(R.string.ai_assistant_unsupported_ram)
                DeviceCapabilityResult.Reason.UNSUPPORTED_ABI -> getString(R.string.ai_assistant_unsupported_abi)
                DeviceCapabilityResult.Reason.LOW_STORAGE -> getString(R.string.ai_assistant_unsupported_storage)
            }
        } else {
            statusText.isVisible = false
            setupOnDeviceModelList(modelList)
        }

        setupWebProviderList()
    }

    private fun setupOnDeviceModelList(modelList: LinearLayout) {
        modelList.removeAllViews()
        aiModelRows.clear()
        AssistantModel.ALL.forEach { model ->
            val rowView = layoutInflater.inflate(R.layout.item_ai_model_row, modelList, false)
            val row = ModelRowViews(
                root = rowView,
                name = rowView.findViewById(R.id.model_row_name),
                badge = rowView.findViewById(R.id.model_row_selected_badge),
                description = rowView.findViewById(R.id.model_row_description),
                status = rowView.findViewById(R.id.model_row_status),
                progress = rowView.findViewById(R.id.model_row_progress),
                actionButton = rowView.findViewById(R.id.model_row_action_button)
            )
            row.name.text = model.displayName
            row.description.text = model.description
            aiModelRows[model.id] = row
            modelList.addView(rowView)

            row.root.setOnClickListener {
                if (modelDownloadManager.currentState(model) == ModelState.READY) {
                    selectOnDeviceModel(model.id)
                }
            }
            row.actionButton.setOnClickListener {
                when (modelDownloadManager.currentState(model)) {
                    ModelState.NOT_DOWNLOADED, ModelState.FAILED -> {
                        if (isAnyAiModelDownloading()) return@setOnClickListener
                        if (modelDownloadManager.startDownload(model)) {
                            refreshAllAiModelRows()
                            startAiAssistantPolling()
                        }
                    }
                    ModelState.READY -> {
                        if (!isWebSourceSelected() && model.id == selectedModelId()) {
                            modelDownloadManager.deleteModel(model)
                            refreshAllAiModelRows()
                            notifySettingsChanged()
                        } else {
                            selectOnDeviceModel(model.id)
                        }
                    }
                    ModelState.DOWNLOADING, ModelState.VERIFYING -> {}
                }
            }
        }

        refreshAllAiModelRows()
        if (isAnyAiModelDownloading()) {
            startAiAssistantPolling()
        }
    }

    private fun selectOnDeviceModel(modelId: String) {
        prefs.edit {
            putString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_ON_DEVICE)
            putString(Constants.Prefs.AI_ASSISTANT_SELECTED_MODEL_ID, modelId)
        }
        refreshAllAiModelRows()
        refreshAllWebProviderRows()
        notifySettingsChanged()
    }

    private fun selectWebProvider(providerId: String) {
        prefs.edit {
            putString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_WEB)
            putString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, providerId)
        }
        refreshAllAiModelRows()
        refreshAllWebProviderRows()
        notifySettingsChanged()
    }

    private fun setupWebProviderList() {
        val providerList = findViewById<LinearLayout>(R.id.ai_assistant_web_provider_list)
        providerList.removeAllViews()
        aiWebProviderRows.clear()

        WebAiProvider.ALL.forEach { provider ->
            val rowView = layoutInflater.inflate(R.layout.item_ai_model_row, providerList, false)
            val row = ModelRowViews(
                root = rowView,
                name = rowView.findViewById(R.id.model_row_name),
                badge = rowView.findViewById(R.id.model_row_selected_badge),
                description = rowView.findViewById(R.id.model_row_description),
                status = rowView.findViewById(R.id.model_row_status),
                progress = rowView.findViewById(R.id.model_row_progress),
                actionButton = rowView.findViewById(R.id.model_row_action_button)
            )
            row.name.text = provider.displayName
            row.description.text = getString(R.string.ai_assistant_web_provider_description, provider.displayName)
            row.progress.isVisible = false
            row.status.isVisible = false
            row.actionButton.text = getString(R.string.ai_assistant_use_web_provider)
            aiWebProviderRows[provider.id] = row
            providerList.addView(rowView)

            val select = { selectWebProvider(provider.id) }
            row.root.setOnClickListener { select() }
            row.actionButton.setOnClickListener { select() }
        }

        refreshAllWebProviderRows()
    }

    private fun refreshAllWebProviderRows() {
        val isWeb = isWebSourceSelected()
        val selectedId = selectedWebProviderId()
        WebAiProvider.ALL.forEach { provider ->
            val row = aiWebProviderRows[provider.id] ?: return@forEach
            val isSelected = isWeb && provider.id == selectedId
            row.badge.isVisible = isSelected
            row.actionButton.isVisible = !isSelected
        }
    }

    private fun isAnyAiModelDownloading(): Boolean =
        AssistantModel.ALL.any { modelDownloadManager.currentState(it) == ModelState.DOWNLOADING }

    private fun refreshAllAiModelRows() {
        val selectedId = selectedModelId()
        val anyDownloading = isAnyAiModelDownloading()
        val onDeviceActive = !isWebSourceSelected()

        AssistantModel.ALL.forEach { model ->
            val row = aiModelRows[model.id] ?: return@forEach
            val isSelected = onDeviceActive && model.id == selectedId

            when (val state = modelDownloadManager.currentState(model)) {
                ModelState.NOT_DOWNLOADED, ModelState.FAILED -> {
                    row.badge.isVisible = false
                    row.status.text = if (state == ModelState.FAILED) {
                        getString(R.string.ai_assistant_failed)
                    } else {
                        getString(R.string.ai_assistant_wifi_notice, formatSize(model.expectedSizeBytes))
                    }
                    row.progress.isVisible = false
                    row.actionButton.isVisible = true
                    row.actionButton.isEnabled = !anyDownloading
                    row.actionButton.text = getString(R.string.ai_assistant_download, formatSize(model.expectedSizeBytes))
                }
                ModelState.DOWNLOADING -> {
                    row.badge.isVisible = false
                    val percent = ((modelDownloadManager.progress(model)?.fraction ?: 0f) * 100).toInt()
                    row.status.text = getString(R.string.ai_assistant_downloading, percent)
                    row.progress.isVisible = true
                    row.progress.progress = percent
                    row.actionButton.isVisible = true
                    row.actionButton.isEnabled = false
                    row.actionButton.text = getString(R.string.ai_assistant_downloading, percent)
                }
                ModelState.VERIFYING -> {
                    row.badge.isVisible = false
                    row.status.text = getString(R.string.ai_assistant_verifying)
                    row.progress.isVisible = true
                    row.progress.progress = 100
                    row.actionButton.isVisible = true
                    row.actionButton.isEnabled = false
                    row.actionButton.text = getString(R.string.ai_assistant_verifying)
                }
                ModelState.READY -> {
                    row.badge.isVisible = isSelected
                    row.status.text = getString(R.string.ai_assistant_ready)
                    row.progress.isVisible = false
                    row.actionButton.isVisible = true
                    row.actionButton.isEnabled = true
                    row.actionButton.text = if (isSelected) {
                        getString(R.string.ai_assistant_delete_model)
                    } else {
                        getString(R.string.ai_assistant_use_model)
                    }
                }
            }
        }
    }

    private fun startAiAssistantPolling() {
        if (aiAssistantPolling) return
        aiAssistantPolling = true
        val poll = object : Runnable {
            override fun run() {
                val stillActive = AssistantModel.ALL
                    .map { modelDownloadManager.pollAndReconcile(it) }
                    .any { it == ModelState.DOWNLOADING || it == ModelState.VERIFYING }
                refreshAllAiModelRows()
                if (stillActive) {
                    aiAssistantPollHandler.postDelayed(this, 1000)
                } else {
                    aiAssistantPolling = false
                }
            }
        }
        aiAssistantPollHandler.post(poll)
    }

    private fun setupTypographySettings() {
        val seek = findViewById<SeekBar>(R.id.typography_size_seekbar)
        val valT = findViewById<TextView>(R.id.typography_size_value_text)
        val styS = findViewById<Spinner>(R.id.typography_style_spinner)
        val intS = findViewById<Spinner>(R.id.typography_intensity_spinner)
        val colS = findViewById<Spinner>(R.id.typography_color_spinner)

        val scale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100).coerceIn(80, 140)


        val baseStyV = mutableListOf("default", "serif", "monospace", "condensed", "rounded", "casual", "cursive")
        val baseStyL = mutableListOf("Modern Sans", "Classic Serif", "Dev Mono", "Clean Condensed", "Soft Rounded", "Casual Hand", "Creative Script")


        DownloadableFontManager.getFontOptions().forEach { font ->
            if (DownloadableFontManager.isDownloaded(this, font.styleKey)) {
                baseStyV.add(font.styleKey)
                baseStyL.add(font.displayName)
            }
        }

        seek.max = 60
        seek.progress = scale - 80
        valT.text = "$scale%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = "${p + 80}%"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, p + 80) }
                    TypographyManager.applyToActivity(this@SettingsActivity)
                    notifySettingsChanged()
                }
            } override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        styS.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, baseStyL.toTypedArray()).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val currentStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val selectedIndex = baseStyV.indexOf(currentStyle)
        if (selectedIndex >= 0) {
            styS.setSelection(selectedIndex)
        } else {

            prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") }
            styS.setSelection(0)
            TypographyManager.applyToActivity(this)
            notifySettingsChanged()
        }

        styS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, baseStyV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            } override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val intV = arrayOf("light", "regular", "bold", "extra_bold")
        val intL = arrayOf("Light weight", "Regular weight", "Bold weight", "Heavy weight")
        intS.adapter =  ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, intL).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        intS.setSelection(intV.indexOf(prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular")).coerceAtLeast(0))
        intS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, intV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            } override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val colL = arrayOf(
            "Pure White",
            "Electric Purple",
            "Neon Pink",
            "Solar Gold",
            "Emerald Mist",
            "Arctic Frost",
            "Midnight Teal",
            "Cyan Accent",
            "Nord Mint",
            "Lavender",
            "Orange Glow",

            "Sky Blue",
            "Soft Coral",
            "Lime Glow",
            "Ice Blue",
            "Rose Pink",
            "Bright Amber",
            "Mint Green",
            "Violet Glow",
            "Aqua Light",
            "Peach Light"
        )

        val colV = arrayOf(
            Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT,
            "#FF7C3AED",
            "#FFEC4899",
            "#FFF59E0B",
            "#FF10B981",
            "#FF93C5FD",
            "#FF0F766E",
            "#FF03DAC5",
            "#FF8FBCBB",
            "#FFB48EAD",
            "#FFD08770",

            "#FF60A5FA",
            "#FFF87171",
            "#FFA3E635",
            "#FFBAE6FD",
            "#FFF472B6",
            "#FFFBBF24",
            "#FF6EE7B7",
            "#FFA78BFA",
            "#FF67E8F9",
            "#FFFDA4AF"
        )
        colS.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, colL, colV).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        colS.setSelection(colV.indexOf(prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)).coerceAtLeast(0))
        colS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, colV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            } override fun onNothingSelected(p: AdapterView<*>) {}
        }
        setupDownloadableFontsSection()
    }

    private fun setupDownloadableFontsSection() {
        val h = findViewById<View>(R.id.downloadable_fonts_header)
        val a = findViewById<ImageView>(R.id.downloadable_fonts_arrow)
        val c = findViewById<LinearLayout>(R.id.downloadable_fonts_container)
        h.setOnClickListener {
            val vis = c.isVisible
            c.visibility = if (vis) View.GONE else View.VISIBLE
            a.animate().rotation(if (vis) 0f else 90f).start()
            if (!vis) updateDownloadableFontsList(c)
        }
    }

    private fun updateDownloadableFontsList(cont: LinearLayout) {
        cont.removeAllViews()
        DownloadableFontManager.getFontOptions().forEach { opt ->
            val view = layoutInflater.inflate(R.layout.item_downloadable_font_option, cont, false)
            val lbl = view.findViewById<TextView>(R.id.downloadable_font_label)
            val btn = view.findViewById<Button>(R.id.downloadable_font_button)
            lbl.text = opt.displayName
            val inst = DownloadableFontManager.isDownloaded(this, opt.styleKey)
            btn.text = if (inst) "Remove" else "Install"
            btn.setTextColor(if (inst) Color.RED else Color.GREEN)
            btn.setOnClickListener {
                btn.isEnabled = false
                if (inst) {
                    DownloadableFontManager.uninstallFont(this, opt.styleKey)
                    if (prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") == opt.styleKey) {
                        prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") }
                    }

                    setupTypographySettings()
                    updateDownloadableFontsList(cont)
                } else {
                    btn.text = getString(R.string.lbl_fetching)
                    DownloadableFontManager.requestFont(this, opt.styleKey) {
                        handler.post {

                            setupTypographySettings()
                            updateDownloadableFontsList(cont)
                        }
                    }
                }
            }
            cont.addView(view)
        }
    }

    private fun setupIconPackSettings() {
        val header = findViewById<LinearLayout>(R.id.icon_pack_header)
        val content = findViewById<LinearLayout>(R.id.icon_pack_content)
        val arrow = findViewById<TextView>(R.id.icon_pack_arrow)
        val toggleSwitch = findViewById<SwitchCompat>(R.id.icon_pack_toggle_switch)
        val listContainer = findViewById<LinearLayout>(R.id.icon_pack_list_container)
        val emptyState = findViewById<TextView>(R.id.icon_pack_empty_state)
        val browseButton = findViewById<Button>(R.id.browse_icon_packs_button)
        
        setupSectionToggle(header, content, arrow)
        
        val iconPacks = IconPackManager.getInstalledIconPacks(this)
        val selectedPack = IconPackManager.getSelectedIconPack(prefs)
        val isEnabled = IconPackManager.isIconPackEnabled(prefs)
        
        toggleSwitch.isChecked = isEnabled
        
        fun applySwitchColors(isEnabled: Boolean) {
            val color = if (isEnabled) ThemeManager.color(this@SettingsActivity, R.attr.appAccent) else ThemeManager.color(this@SettingsActivity, R.attr.appTextPrimary)
            toggleSwitch.thumbTintList = ColorStateList.valueOf(color)
            toggleSwitch.trackTintList = ColorStateList.valueOf(color)
        }
        
        applySwitchColors(isEnabled)
        listContainer.visibility = if (isEnabled && iconPacks.isNotEmpty()) View.VISIBLE else View.GONE
        emptyState.visibility = if (isEnabled && iconPacks.isEmpty()) View.VISIBLE else View.GONE
        
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            applySwitchColors(isChecked)
            if (isChecked) {
                IconPackManager.setIconPack(prefs, iconPacks.firstOrNull()?.packageName)
            } else {
                IconPackManager.clearIconPack(prefs)
            }
            listContainer.visibility = if (isChecked && iconPacks.isNotEmpty()) View.VISIBLE else View.GONE
            emptyState.visibility = if (isChecked && iconPacks.isEmpty()) View.VISIBLE else View.GONE
            notifySettingsChanged()
        }
        
        if (iconPacks.isNotEmpty()) {
            listContainer.removeAllViews()
            iconPacks.forEach { pack ->
                val view = layoutInflater.inflate(R.layout.item_icon_pack, listContainer, false)
                val icon = view.findViewById<ImageView>(R.id.icon_pack_icon)
                val name = view.findViewById<TextView>(R.id.icon_pack_name)
                val status = view.findViewById<TextView>(R.id.icon_pack_status)
                val radio = view.findViewById<RadioButton>(R.id.icon_pack_radio)
                
                icon.setImageDrawable(pack.icon)
                name.text = pack.name
                status.text = pack.packageName
                radio.isChecked = selectedPack == pack.packageName
                
                view.setOnClickListener {
                    IconPackManager.setIconPack(prefs, pack.packageName)
                    toggleSwitch.isChecked = true
                    listContainer.forEach { child ->
                        child.findViewById<RadioButton>(R.id.icon_pack_radio)?.isChecked = false
                    }
                    radio.isChecked = true
                    notifySettingsChanged()
                    Toast.makeText(this, this.getString(R.string.toast_icon_pack_applied_icons_will_update_when_you_ret, pack.name), Toast.LENGTH_LONG).show()
                }
                
                listContainer.addView(view)
            }
        }
        
        browseButton.setOnClickListener {
            IconPackManager.browseIconPacks(this)
        }
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(packageName) })
    }

    private fun hasAccessibilityServicePermission(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(ComponentName(this, ScreenLockAccessibilityService::class.java).flattenToString())
    }

    private fun showPermissionDialog(t: String, m: String, onP: () -> Unit) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme).setTitle(t).setMessage(m).setPositiveButton(getString(R.string.settings)) { _, _ -> onP() }.setNegativeButton(getString(R.string.cancel_button), null).show()
    }

    private fun showWalkDetectionAppPicker(onAppSelected: () -> Unit) {
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

    private fun chooseWallpaper() { wallpaperLauncher.launch(Intent(Intent.ACTION_SET_WALLPAPER)) }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<ScrollView>(R.id.settings_scroll_view).setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun applyOrientationPreference() {
        requestedOrientation = if (prefs.getBoolean(Constants.Prefs.LANDSCAPE_ORIENTATION_ENABLED, false)) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun restartLauncher() {
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

    private fun clearCache() {
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

    private fun clearData() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.factory_reset_launcher))
            .setMessage(getString(R.string.dlg_warning_this_will_permanently_delete_all_your_se))
            .setPositiveButton(getString(R.string.dlg_reset_everything)) { _, _ ->
                (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
            }
            .setNegativeButton(getString(R.string.dlg_keep_data), null)
            .show()
    }

    private fun sendFeedback() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri(); putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "Launch Feedback") }, "Feedback")) } catch (_: Exception) {}
    }

    private fun exportSettings() { exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "launch_backup.zip") }) }
    private fun importSettings() { importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }) }

    private fun exportSettingsToFile(uri: Uri) {
        SettingsBackupHelper.exportToUri(this, prefs, uri)
    }

    private fun importSettingsFromFile(uri: Uri) {
        SettingsBackupHelper.importFromUri(this, prefs, uri) { restartLauncher() }
    }

    private fun showUnsavedChangesDialog(onConfirm: () -> Unit) {
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
    private val itemColors: Array<String>? = null
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

    private fun applyThemeColor(view: View, position: Int) {
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
