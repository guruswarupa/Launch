package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.AppDisplayHelper
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.ui.adapters.FocusModeAppAdapter
import com.guruswarupa.launch.ui.theme.ThemeManager
import java.util.concurrent.Executors

class FocusModeConfigActivity : AppCompatActivity() {

    private lateinit var focusModeManager: FocusModeManager
    private lateinit var webAppManager: WebAppManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FocusModeAppAdapter
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var blockedCountText: TextView
    private lateinit var searchBox: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_focus_mode_config)

        focusModeManager = FocusModeManager(this, getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE))
        webAppManager = WebAppManager(prefs)


        recyclerView = findViewById(R.id.focus_mode_app_list)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        applyBackgroundTranslucency()
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        blockedCountText = findViewById(R.id.blocked_count_text)
        searchBox = findViewById(R.id.app_search_box)
        saveButton = findViewById(R.id.save_focus_config)
        cancelButton = findViewById(R.id.cancel_focus_config)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null

        applyThemeAndWallpaper()

        // Querying every launchable activity and resolving all their labels is real work - do
        // it off the main thread, adapter construction included (it only touches data, no
        // views), so the screen appears instantly instead of blocking onCreate.
        backgroundExecutor.execute {
            val apps = queryLaunchableApps()
            val newAdapter = FocusModeAppAdapter(
                appList = apps,
                packageManager = packageManager,
                focusModeManager = focusModeManager,
                iconExecutor = backgroundExecutor,
                onSelectionChanged = { updateBlockedCount(it) }
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                appList = apps.toMutableList()
                adapter = newAdapter
                recyclerView.adapter = adapter
                updateBlockedCount(adapter.blockedCount())
            }
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (::adapter.isInitialized) adapter.filter(s?.toString().orEmpty())
            }
        })

        saveButton.setOnClickListener {
            if (::adapter.isInitialized) {
                focusModeManager.updateBlockedApps(adapter.getBlockedApps())
            }

            Toast.makeText(this, this.getString(R.string.toast_focus_mode_configuration_saved), Toast.LENGTH_SHORT).show()


            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)

            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun updateBlockedCount(count: Int) {
        blockedCountText.text = resources.getQuantityString(R.plurals.focus_mode_blocked_count, count, count)
    }

    private fun applyThemeAndWallpaper() {

        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)

        applyBackgroundTranslucency()

        val textColor = ThemeManager.color(this, R.attr.appTextPrimary)
        val subTextColor = ThemeManager.color(this, R.attr.appTextSecondary)

        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        cancelButton.setTextColor(textColor)

        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    /** Runs on [backgroundExecutor] - must not touch any view. */
    private fun queryLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return (packageManager.queryIntentActivities(intent, 0) + webAppManager.getResolveInfos())
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { AppDisplayHelper.getLabel(it, packageManager).lowercase() }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        themeOverlay.setBackgroundColor(color)
    }
}
