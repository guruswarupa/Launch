package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.HiddenAppManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class HiddenAppsSettingsActivity : ComponentActivity() {

    private lateinit var hiddenAppManager: HiddenAppManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private var hiddenAppsList = mutableListOf<ResolveInfo>()

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_apps_settings)
        applyContentInsets()
        applyBackgroundTranslucency()

        val prefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        hiddenAppManager = HiddenAppManager(prefs)

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))
        appsRecyclerView = findViewById(R.id.hidden_apps_recycler_view)
        emptyStateLayout = findViewById(R.id.empty_state_layout)
        appsRecyclerView.layoutManager = LinearLayoutManager(this)

        recreateAppsList()
    }

    private fun recreateAppsList() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps = pm.queryIntentActivities(mainIntent, 0)
        val hiddenPackageNames = hiddenAppManager.getHiddenApps()

        hiddenAppsList = allApps.filter {
            it.activityInfo.packageName != "com.guruswarupa.launch" &&
            hiddenPackageNames.contains(it.activityInfo.packageName)
        }.sortedBy { it.loadLabel(pm).toString().lowercase() }.toMutableList()

        if (hiddenAppsList.isEmpty()) {
            appsRecyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            appsRecyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            appsRecyclerView.adapter = HiddenAppsAdapter(
                apps = hiddenAppsList,
                onOpen = { app -> openHiddenApp(app) },
                onUnhide = { pkg ->
                    hiddenAppManager.unhideApp(pkg)
                    Toast.makeText(this, getString(R.string.application_restored), Toast.LENGTH_SHORT).show()
                    recreateAppsList()
                    setResult(RESULT_OK)
                }
            )
        }
    }

    private fun openHiddenApp(app: ResolveInfo) {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.activityInfo.packageName, app.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        runCatching { startActivity(launchIntent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.unable_to_open_app), Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = Color.argb(alpha, 0, 0, 0)
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }

    private fun applyContentInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private class HiddenAppsAdapter(
        private val apps: List<ResolveInfo>,
        private val onOpen: (ResolveInfo) -> Unit,
        private val onUnhide: (String) -> Unit
    ) : RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val btn: Button = v.findViewById(R.id.unhide_button)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_hidden_app, p, false))
        override fun getItemCount() = apps.size
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val app = apps[p]
            val pm = h.itemView.context.packageManager
            h.icon.setImageDrawable(app.loadIcon(pm))
            h.name.text = app.loadLabel(pm)
            h.itemView.setOnClickListener { onOpen(app) }
            h.btn.setOnClickListener { onUnhide(app.activityInfo.packageName) }
        }
    }
}
