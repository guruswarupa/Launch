package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.ui.theme.ThemeManager

class ControlCenterTriggerConfigActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_center_trigger_config)
        applyContentInsets()
        applyBackgroundTranslucency()
        window.decorView.post { makeSystemBarsTransparent() }

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))

        setupTriggerSettings()
    }

    private fun setupTriggerSettings() {
        val titleText = findViewById<View>(R.id.title_text) as? android.widget.TextView
        titleText?.text = getString(R.string.lbl_control_center_trigger)

        val positionSpinner = findViewById<View>(R.id.control_center_trigger_position_spinner) as Spinner
        val lockSwitch = findViewById<View>(R.id.control_center_trigger_lock_switch) as SwitchCompat
        val alphaSeekbar = findViewById<View>(R.id.control_center_trigger_alpha_seekbar) as SeekBar
        val heightSeekbar = findViewById<View>(R.id.control_center_trigger_height_seekbar) as SeekBar
        val widthSeekbar = findViewById<View>(R.id.control_center_trigger_width_seekbar) as SeekBar


        val positions = arrayOf(getString(R.string.control_center_trigger_position_left), getString(R.string.control_center_trigger_position_right))
        val adapter = ThemedArrayAdapter(this, android.R.layout.simple_spinner_item, positions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        positionSpinner.adapter = adapter

        val savedSide = prefs.getString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, "end") ?: "end"
        positionSpinner.setSelection(if (savedSide == "start") 0 else 1)

        positionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val side = if (position == 0) "start" else "end"
                prefs.edit().putString(Constants.Prefs.CONTROL_CENTER_TRIGGER_SIDE, side).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        lockSwitch.isChecked = prefs.getBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, false)
        lockSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.Prefs.CONTROL_CENTER_TRIGGER_LOCKED, isChecked).apply()
        }


        val savedAlpha = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_ALPHA, 80)
        alphaSeekbar.progress = savedAlpha - 20
        alphaSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = progress + 20
                prefs.edit().putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_ALPHA, alpha).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        val savedHeight = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_HEIGHT_DP, 72)
        heightSeekbar.progress = savedHeight - 40
        heightSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val height = progress + 40
                prefs.edit().putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_HEIGHT_DP, height).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        val savedWidth = prefs.getInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_WIDTH_DP, 18)
        widthSeekbar.progress = savedWidth - 12
        widthSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val width = progress + 12
                prefs.edit().putInt(Constants.Prefs.CONTROL_CENTER_TRIGGER_WIDTH_DP, width).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        findViewById<Button>(R.id.cancel_control_center_trigger_config).setOnClickListener {
            finish()
        }


        findViewById<Button>(R.id.save_control_center_trigger_config).setOnClickListener {

            sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED"))
            finish()
        }
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        findViewById<View>(R.id.theme_overlay)?.setBackgroundColor(color)
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(android.R.id.content).setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun makeSystemBarsTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }
}
