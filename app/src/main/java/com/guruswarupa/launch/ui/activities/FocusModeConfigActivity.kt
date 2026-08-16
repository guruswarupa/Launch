package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
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
    private lateinit var appsContainer: LinearLayout
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
        appsContainer = findViewById(R.id.apps_container)
        saveButton = findViewById(R.id.save_focus_config)
        cancelButton = findViewById(R.id.cancel_focus_config)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null


        applyThemeAndWallpaper()

        loadApps()

        adapter = FocusModeAppAdapter(appList, focusModeManager)
        recyclerView.adapter = adapter

        saveButton.setOnClickListener {

            focusModeManager.updateAllowedApps(adapter.getSelectedApps())

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

    private fun applyThemeAndWallpaper() {

        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)

        applyBackgroundTranslucency()

        appsContainer.setBackgroundResource(R.drawable.widget_background)

        val textColor = Color.WHITE
        val subTextColor = Color.parseColor("#B0B0B0")

        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        saveButton.setTextColor(textColor)
        cancelButton.setTextColor(textColor)

        saveButton.setBackgroundResource(R.drawable.settings_card_background)
        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = (packageManager.queryIntentActivities(intent, 0) + webAppManager.getResolveInfos())
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { AppDisplayHelper.getLabel(it, packageManager).lowercase() }

        appList = apps.toMutableList()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = Color.argb(alpha, 0, 0, 0)
        themeOverlay.setBackgroundColor(color)
    }
}
