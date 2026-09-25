package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.managers.WorkspaceManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.adapters.WorkspacesAppsAdapter
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.AppDisplayHelper
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class WorkspaceAppPickerActivity : AppCompatActivity() {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var webAppManager: WebAppManager
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var selectedCountText: TextView
    private lateinit var searchBox: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var adapter: WorkspacesAppsAdapter

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private lateinit var workspaceName: String
    private var workspaceId: String? = null
    private val selectedApps = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_workspace_app_picker)
        applyContentInsets()

        workspaceManager = WorkspaceManager(prefs)
        webAppManager = WebAppManager(prefs)

        workspaceName = intent.getStringExtra(EXTRA_WORKSPACE_NAME).orEmpty()
        workspaceId = intent.getStringExtra(EXTRA_WORKSPACE_ID)

        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        titleText = findViewById(R.id.title_text)
        selectedCountText = findViewById(R.id.selected_count_text)
        searchBox = findViewById(R.id.app_search_box)
        recyclerView = findViewById(R.id.workspace_app_list)
        saveButton = findViewById(R.id.save_workspace_apps)
        cancelButton = findViewById(R.id.cancel_workspace_apps)

        titleText.text = getString(R.string.dlg_select_apps_for, workspaceName)

        applyThemeAndWallpaper()

        val existingId = workspaceId
        if (existingId != null) {
            selectedApps.addAll(workspaceManager.getWorkspace(existingId)?.appPackageNames ?: emptySet())
        }

        val allApps = queryAvailableApps(existingId)
        if (allApps.isEmpty()) {
            val appsInOtherWorkspaces = workspaceManager.getAppsInWorkspaces(existingId)
            val message = if (appsInOtherWorkspaces.isNotEmpty()) {
                R.string.toast_all_apps_are_already_assigned_to_other_workspace
            } else {
                R.string.toast_no_apps_found
            }
            Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WorkspacesAppsAdapter(allApps, packageManager, selectedApps) { packageName, isChecked ->
            if (isChecked) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
            updateSelectedCount()
        }
        recyclerView.adapter = adapter
        updateSelectedCount()

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
        })

        saveButton.setOnClickListener {
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_please_select_at_least_one_app), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = existingId
            if (id != null) {
                workspaceManager.updateWorkspace(id, workspaceName, selectedApps)
                Toast.makeText(this, getString(R.string.toast_workspace_updated), Toast.LENGTH_SHORT).show()
            } else {
                workspaceManager.createWorkspace(workspaceName, selectedApps)
                Toast.makeText(this, getString(R.string.toast_workspace_created_with_apps, selectedApps.size), Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun queryAvailableApps(existingWorkspaceId: String?): List<android.content.pm.ResolveInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherApps = packageManager.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
        val allAppsRaw = (launcherApps + webAppManager.getResolveInfos())
            .distinctBy { it.activityInfo.packageName }

        val appsInOtherWorkspaces = workspaceManager.getAppsInWorkspaces(existingWorkspaceId)

        return allAppsRaw
            .filter { !appsInOtherWorkspaces.contains(it.activityInfo.packageName) }
            .sortedBy { AppDisplayHelper.getLabel(it, packageManager).lowercase() }
    }

    private fun updateSelectedCount() {
        selectedCountText.text = resources.getQuantityString(
            R.plurals.workspace_selected_count,
            selectedApps.size,
            selectedApps.size
        )
    }

    private fun applyContentInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 20.toPx(),
                view.paddingRight,
                systemBars.bottom + 20.toPx()
            )
            insets
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun applyThemeAndWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)
        applyBackgroundTranslucency()

        titleText.setTextColor(ThemeManager.color(this, R.attr.appTextPrimary))
        cancelButton.setTextColor(ThemeManager.color(this, R.attr.appTextPrimary))
        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        themeOverlay.setBackgroundColor(color)
    }

    companion object {
        const val EXTRA_WORKSPACE_NAME = "workspace_name"
        const val EXTRA_WORKSPACE_ID = "workspace_id"
    }
}
