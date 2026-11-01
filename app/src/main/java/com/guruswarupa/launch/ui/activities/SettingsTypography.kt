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

internal fun SettingsActivity.setupTypographySettings() {
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
                TypographyManager.applyToActivity(this@setupTypographySettings)
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
            TypographyManager.applyToActivity(this@setupTypographySettings)
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
            TypographyManager.applyToActivity(this@setupTypographySettings)
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
            TypographyManager.applyToActivity(this@setupTypographySettings)
            notifySettingsChanged()
        } override fun onNothingSelected(p: AdapterView<*>) {}
    }
    setupDownloadableFontsSection()
}

internal fun SettingsActivity.setupDownloadableFontsSection() {
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

internal fun SettingsActivity.updateDownloadableFontsList(cont: LinearLayout) {
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

internal fun SettingsActivity.setupIconPackSettings() {
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
        val color = if (isEnabled) ThemeManager.color(this@setupIconPackSettings, R.attr.appAccent) else ThemeManager.color(this@setupIconPackSettings, R.attr.appTextPrimary)
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
