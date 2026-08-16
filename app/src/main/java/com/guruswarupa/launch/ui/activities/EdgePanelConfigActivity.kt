package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.adapters.WorkspacesAppsAdapter
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class EdgePanelConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var appsContainer: View
    private lateinit var handleContainer: View
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var positionSpinner: Spinner
    private lateinit var lockSwitch: SwitchCompat
    private lateinit var showRecentSwitch: SwitchCompat
    private lateinit var alphaSeekbar: SeekBar
    private lateinit var heightSeekbar: SeekBar
    private lateinit var widthSeekbar: SeekBar


    private lateinit var tabApps: Button
    private lateinit var tabHandle: Button

    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    private val selectedApps = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_edge_panel_config)

        recyclerView = findViewById(R.id.edge_panel_app_list)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        handleContainer = findViewById(R.id.handle_container)
        appsContainer = findViewById(R.id.apps_container)
        saveButton = findViewById(R.id.save_edge_panel_config)
        cancelButton = findViewById(R.id.cancel_edge_panel_config)
        positionSpinner = findViewById(R.id.edge_panel_position_spinner)
        lockSwitch = findViewById(R.id.edge_panel_lock_switch)
        showRecentSwitch = findViewById(R.id.edge_panel_show_recent_switch)
        alphaSeekbar = findViewById(R.id.edge_panel_alpha_seekbar)
        heightSeekbar = findViewById(R.id.edge_panel_height_seekbar)
        widthSeekbar = findViewById(R.id.edge_panel_width_seekbar)
        tabApps = findViewById(R.id.tab_apps)
        tabHandle = findViewById(R.id.tab_handle)

        applyThemeAndWallpaper()
        loadSavedSelection()
        setupHandleControls()
        setupRecyclerView()


        tabApps.setOnClickListener { switchTab(0) }
        tabHandle.setOnClickListener { switchTab(1) }


        switchTab(0)

        saveButton.setOnClickListener {
            prefs.edit {
                putString(Constants.Prefs.EDGE_PANEL_PINNED_APPS, selectedApps.joinToString(","))
            }
            sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply {
                setPackage(packageName)
            })
            Toast.makeText(this, getString(R.string.edge_panel_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        cancelButton.setOnClickListener { finish() }
    }

    private fun switchTab(index: Int) {
        if (index == 0) {

            appsContainer.visibility = View.VISIBLE
            handleContainer.visibility = View.GONE
            tabApps.setTextColor(Color.WHITE)
            tabHandle.setTextColor(Color.parseColor("#80FFFFFF"))
        } else {

            appsContainer.visibility = View.GONE
            handleContainer.visibility = View.VISIBLE
            tabApps.setTextColor(Color.parseColor("#80FFFFFF"))
            tabHandle.setTextColor(Color.WHITE)
        }
    }

    private fun applyThemeAndWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)

        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        themeOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))

        titleText.setText(R.string.edge_panel_config_title)
        subtitleText.setText(R.string.edge_panel_config_subtitle)
        saveButton.setBackgroundResource(R.drawable.settings_card_background)
        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    private fun loadSavedSelection() {
        val savedPackages = prefs.getString(Constants.Prefs.EDGE_PANEL_PINNED_APPS, null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        selectedApps.addAll(savedPackages)
    }

    private fun setupRecyclerView() {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps: List<ResolveInfo> = packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = WorkspacesAppsAdapter(apps, selectedApps) { packageName, isChecked ->
            if (isChecked) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
        }
    }

    private fun setupHandleControls() {
        val positions = arrayOf(
            getString(R.string.edge_panel_position_right),
            getString(R.string.edge_panel_position_left)
        )
        val values = arrayOf("end", "start")
        positionSpinner.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, positions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        positionSpinner.setSelection(values.indexOf(prefs.getString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, "end")).coerceAtLeast(0))
        positionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.EDGE_PANEL_HANDLE_SIDE, values[position]) }
                notifySettingsChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        lockSwitch.isChecked = prefs.getBoolean(Constants.Prefs.EDGE_PANEL_HANDLE_LOCKED, false)
        lockSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.EDGE_PANEL_HANDLE_LOCKED, isChecked) }
            notifySettingsChanged()
        }

        showRecentSwitch.isChecked = prefs.getBoolean(Constants.Prefs.EDGE_PANEL_SHOW_RECENT, true)
        showRecentSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.EDGE_PANEL_SHOW_RECENT, isChecked) }
            notifySettingsChanged()
        }

        val currentAlpha = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_ALPHA, 80).coerceIn(20, 100)
        alphaSeekbar.progress = currentAlpha - 20
        alphaSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.EDGE_PANEL_HANDLE_ALPHA, progress + 20) }
            notifySettingsChanged()
        })

        val currentHeight = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_HEIGHT_DP, 72).coerceIn(40, 112)
        heightSeekbar.progress = currentHeight - 40
        heightSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.EDGE_PANEL_HANDLE_HEIGHT_DP, progress + 40) }
            notifySettingsChanged()
        })

        val currentWidth = prefs.getInt(Constants.Prefs.EDGE_PANEL_HANDLE_WIDTH_DP, 18).coerceIn(12, 36)
        widthSeekbar.progress = currentWidth - 12
        widthSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.EDGE_PANEL_HANDLE_WIDTH_DP, progress + 12) }
            notifySettingsChanged()
        })
    }

    private fun simpleSeekBarListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply {
            setPackage(packageName)
        })
    }
}
