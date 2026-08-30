package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import java.util.Collections

class ControlCenterConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShortcutConfigAdapter
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }


    private lateinit var tabCustomize: Button
    private lateinit var tabHandle: Button
    private lateinit var shortcutsCard: View
    private lateinit var triggerCard: View


    private lateinit var triggerPositionSpinner: Spinner
    private lateinit var triggerLockSwitch: SwitchCompat
    private lateinit var triggerAlphaSeekbar: SeekBar
    private lateinit var triggerHeightSeekbar: SeekBar
    private lateinit var triggerWidthSeekbar: SeekBar
    private lateinit var wallpaperBackground: ImageView
    private lateinit var settingsOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_control_center_config)

        wallpaperBackground = findViewById(R.id.wallpaper_background)
        settingsOverlay = findViewById(R.id.settings_overlay)
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        saveButton = findViewById(R.id.btn_save)
        cancelButton = findViewById(R.id.btn_cancel)
        tabCustomize = findViewById(R.id.tab_customize)
        tabHandle = findViewById(R.id.tab_handle)
        shortcutsCard = findViewById(R.id.shortcuts_card)
        triggerCard = findViewById(R.id.trigger_card)

        applyThemeAndWallpaper()


        tabCustomize.setOnClickListener { switchTab(0) }
        tabHandle.setOnClickListener { switchTab(1) }


        setupShortcutList()


        setupTriggerSettings()


        switchTab(0)

        saveButton.setOnClickListener {
            saveShortcutSettings()
            sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply {
                setPackage(packageName)
            })
            Toast.makeText(this, getString(R.string.control_center_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        cancelButton.setOnClickListener { finish() }
    }

    private fun getLabel(id: String): String = when(id) {
        "wifi" -> "Wi-Fi Panel"; "bluetooth" -> "Bluetooth"; "airplane" -> "Airplane Mode"
        "torch" -> "Flashlight"; "data" -> "Cellular Data"; "rotation" -> "Rotation Lock"
        "sound" -> "Audio Profile"; "dnd" -> "Focus Mode"; "location" -> "GPS Location"
        "qr_scan" -> "Scan QR"; "camera" -> "Capture"; "screenshot" -> "Screen Snap"
        "record" -> "Video Capture"; "lock" -> "Security Lock"; "power" -> "System Power"
        "hotspot" -> "Tethering"; "screen_timeout" -> "Display Sleep"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    private fun getIcon(id: String): Int = when(id) {
        "wifi" -> R.drawable.ic_wifi_stat; "bluetooth" -> android.R.drawable.stat_sys_data_bluetooth; "airplane" -> R.drawable.ic_airplane_stat
        "torch" -> R.drawable.ic_torch_stat; "data" -> R.drawable.ic_mobile_data_stat; "rotation" -> R.drawable.ic_rotation_stat
        "sound" -> R.drawable.ic_volume_up_stat; "dnd" -> R.drawable.ic_focus_mode_icon; "location" -> android.R.drawable.ic_menu_mylocation
        "qr_scan" -> R.drawable.ic_qr_scan_stat; "camera" -> android.R.drawable.ic_menu_camera; "screenshot" -> R.drawable.ic_screenshot_stat
        "record" -> android.R.drawable.ic_menu_slideshow; "lock" -> android.R.drawable.ic_lock_idle_lock; "power" -> android.R.drawable.ic_lock_power_off
        "hotspot" -> R.drawable.ic_wifi_stat; "screen_timeout" -> R.drawable.ic_settings_icon
        else -> android.R.drawable.ic_menu_help
    }

    private fun setupShortcutList() {
        recyclerView = findViewById(R.id.rv_shortcuts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val saved = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, ScreenLockAccessibilityService.DEFAULT_SHORTCUTS)
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val allShortcuts = ScreenLockAccessibilityService.DEFAULT_SHORTCUTS.split(",")

        val items = mutableListOf<ShortcutItem>()
        for (id in saved) if (allShortcuts.contains(id)) items.add(ShortcutItem(id, getLabel(id), getIcon(id), true))
        for (id in allShortcuts) if (!saved.contains(id)) items.add(ShortcutItem(id, getLabel(id), getIcon(id), false))

        adapter = ShortcutConfigAdapter(items)
        recyclerView.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                val from = v.adapterPosition; val to = t.adapterPosition
                Collections.swap(items, from, to); adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(v: RecyclerView.ViewHolder, d: Int) {}
        }).attachToRecyclerView(recyclerView)
    }

    private fun switchTab(index: Int) {
        if (index == 0) {

            shortcutsCard.visibility = View.VISIBLE
            triggerCard.visibility = View.GONE
            tabCustomize.setTextColor(ThemeManager.color(this, R.attr.appTextPrimary))
            tabHandle.setTextColor(Color.parseColor("#80FFFFFF"))
        } else {

            shortcutsCard.visibility = View.GONE
            triggerCard.visibility = View.VISIBLE
            tabCustomize.setTextColor(Color.parseColor("#80FFFFFF"))
            tabHandle.setTextColor(ThemeManager.color(this, R.attr.appTextPrimary))
        }
    }

    private fun setupTriggerSettings() {
        triggerPositionSpinner = findViewById(R.id.trigger_position_spinner)
        triggerLockSwitch = findViewById(R.id.trigger_lock_switch)
        triggerAlphaSeekbar = findViewById(R.id.trigger_alpha_seekbar)
        triggerHeightSeekbar = findViewById(R.id.trigger_height_seekbar)
        triggerWidthSeekbar = findViewById(R.id.trigger_width_seekbar)


        val positions = arrayOf(
            getString(R.string.control_center_trigger_position_left),
            getString(R.string.control_center_trigger_position_right)
        )
        val values = arrayOf("start", "end")
        triggerPositionSpinner.adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, positions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        triggerPositionSpinner.setSelection(values.indexOf(prefs.getString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, "end")).coerceAtLeast(0))

        triggerPositionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, values[position]) }
                notifySettingsChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        triggerLockSwitch.isChecked = prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, false)
        triggerLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, isChecked) }
            notifySettingsChanged()
        }


        val currentAlpha = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_ALPHA, 80).coerceIn(20, 100)
        triggerAlphaSeekbar.progress = currentAlpha - 20
        triggerAlphaSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_ALPHA, progress + 20) }
            notifySettingsChanged()
        })


        val currentHeight = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_HEIGHT_DP, 72).coerceIn(40, 112)
        triggerHeightSeekbar.progress = currentHeight - 40
        triggerHeightSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_HEIGHT_DP, progress + 40) }
            notifySettingsChanged()
        })


        val currentWidth = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_WIDTH_DP, 18).coerceIn(12, 36)
        triggerWidthSeekbar.progress = currentWidth - 12
        triggerWidthSeekbar.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            prefs.edit { putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_WIDTH_DP, progress + 12) }
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

    private fun saveShortcutSettings() {
        val items = (adapter as ShortcutConfigAdapter).items
        val res = items.filter { it.isEnabled }.joinToString(",") { it.id }
        prefs.edit().putString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, res).apply()
    }

    private fun applyThemeAndWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)

        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        settingsOverlay.setBackgroundColor(Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase)))

        titleText.setText(R.string.control_center_config_title)
        subtitleText.setText(R.string.control_center_config_subtitle)
        saveButton.setBackgroundResource(R.drawable.settings_card_background)
        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    data class ShortcutItem(val id: String, val label: String, val icon: Int, var isEnabled: Boolean)

    inner class ShortcutConfigAdapter(val items: List<ShortcutItem>) : RecyclerView.Adapter<ShortcutConfigAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_config_shortcut, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = items[pos]
            h.label.text = item.label
            h.icon.setImageResource(item.icon)
            h.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                ThemeManager.color(h.icon.context, R.attr.appIconTint)
            )
            h.cb.isChecked = item.isEnabled
            h.cb.setOnCheckedChangeListener { _, checked -> item.isEnabled = checked }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.iv_shortcut_icon)
            val label: TextView = v.findViewById(R.id.tv_shortcut_label)
            val cb: CheckBox = v.findViewById(R.id.cb_shortcut_visible)
        }
    }
}
