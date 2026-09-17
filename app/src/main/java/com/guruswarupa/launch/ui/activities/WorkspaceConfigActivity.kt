package com.guruswarupa.launch.ui.activities

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.managers.WorkspaceManager
import java.util.concurrent.Executors
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.Workspace
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import android.graphics.Color
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

class WorkspaceConfigActivity : AppCompatActivity() {
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var workspaceList: ListView
    private lateinit var createWorkspaceButton: Button
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var workspacesContainer: LinearLayout

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_workspace_config)

        workspaceManager = WorkspaceManager(prefs)

        workspaceList = findViewById(R.id.workspace_list)
        createWorkspaceButton = findViewById(R.id.create_workspace_button)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        applyBackgroundTranslucency()
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        workspacesContainer = findViewById(R.id.workspaces_container)


        applyThemeAndWallpaper()

        createWorkspaceButton.setOnClickListener {
            startActivity(Intent(this, WorkspaceNameActivity::class.java))
        }

        loadWorkspaces()
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        themeOverlay.setBackgroundColor(color)
    }

    private fun applyThemeAndWallpaper() {

        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)

        applyBackgroundTranslucency()

        workspacesContainer.setBackgroundResource(R.drawable.widget_background)

        val textColor = ThemeManager.color(this, R.attr.appTextPrimary)
        val subTextColor = ThemeManager.color(this, R.attr.appTextSecondary)

        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        createWorkspaceButton.setTextColor(textColor)

        createWorkspaceButton.setBackgroundResource(R.drawable.settings_card_background)
    }

    override fun onResume() {
        super.onResume()
        loadWorkspaces()
        applyThemeAndWallpaper()
    }

    private fun loadWorkspaces() {
        val workspaces = workspaceManager.getAllWorkspaces()


        val allWorkspaces = workspaces

        val workspaceNames = allWorkspaces.map {
            val appCount = it.appPackageNames.size
            "${it.name} ($appCount apps)"
        }.toTypedArray()


        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, workspaceNames) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                text.setTextColor(ThemeManager.color(view.context, R.attr.appTextPrimary))
                return view
            }
        }
        workspaceList.adapter = adapter

        workspaceList.setOnItemClickListener { _, _, position, _ ->
            val workspace = workspaces[position]
            showWorkspaceEditor(workspace)
        }

        workspaceList.setOnItemLongClickListener { _, _, position, _ ->
            val workspace = workspaces[position]
            showDeleteWorkspaceDialog(workspace)
            true
        }
    }

    private fun showWorkspaceEditor(workspace: Workspace) {
        val options = arrayOf("Edit Apps", "Rename", "Activate")

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(workspace.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, WorkspaceAppPickerActivity::class.java)
                            .putExtra(WorkspaceAppPickerActivity.EXTRA_WORKSPACE_NAME, workspace.name)
                            .putExtra(WorkspaceAppPickerActivity.EXTRA_WORKSPACE_ID, workspace.id)
                    )
                    1 -> startActivity(
                        Intent(this, WorkspaceNameActivity::class.java)
                            .putExtra(WorkspaceNameActivity.EXTRA_WORKSPACE_ID, workspace.id)
                            .putExtra(WorkspaceNameActivity.EXTRA_WORKSPACE_NAME, workspace.name)
                    )
                    2 -> {
                        workspaceManager.setActiveWorkspaceId(workspace.id)
                        Toast.makeText(this, this.getString(R.string.toast_workspace_activated, workspace.name), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun showDeleteWorkspaceDialog(workspace: Workspace) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_delete_workspace))
            .setMessage(getString(R.string.dlg_are_you_sure_you_want_to_delete, workspace.name))
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                workspaceManager.deleteWorkspace(workspace.id)
                Toast.makeText(this, this.getString(R.string.toast_workspace_deleted), Toast.LENGTH_SHORT).show()
                loadWorkspaces()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
