package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WorkspaceManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

/** Full-screen replacement for the old "enter workspace name" [android.app.AlertDialog], used for both creating a new workspace and renaming an existing one. */
class WorkspaceNameActivity : AppCompatActivity() {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var nameInput: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private var workspaceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_workspace_name)
        applyContentInsets()

        workspaceManager = WorkspaceManager(prefs)
        workspaceId = intent.getStringExtra(EXTRA_WORKSPACE_ID)

        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        titleText = findViewById(R.id.title_text)
        nameInput = findViewById(R.id.workspace_name_input)
        saveButton = findViewById(R.id.save_workspace_name)
        cancelButton = findViewById(R.id.cancel_workspace_name)

        val isRename = workspaceId != null
        titleText.text = if (isRename) getString(R.string.dlg_rename_workspace) else getString(R.string.create_workspace)
        nameInput.setText(intent.getStringExtra(EXTRA_WORKSPACE_NAME).orEmpty())

        applyThemeAndWallpaper()

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_please_enter_a_workspace_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = workspaceId
            if (id != null) {
                val existingApps = workspaceManager.getWorkspace(id)?.appPackageNames ?: emptySet()
                workspaceManager.updateWorkspace(id, name, existingApps)
                Toast.makeText(this, getString(R.string.toast_workspace_renamed), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                startActivity(
                    Intent(this, WorkspaceAppPickerActivity::class.java)
                        .putExtra(WorkspaceAppPickerActivity.EXTRA_WORKSPACE_NAME, name)
                )
                finish()
            }
        }

        cancelButton.setOnClickListener {
            finish()
        }
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
        const val EXTRA_WORKSPACE_ID = "workspace_id"
        const val EXTRA_WORKSPACE_NAME = "workspace_name"
    }
}
