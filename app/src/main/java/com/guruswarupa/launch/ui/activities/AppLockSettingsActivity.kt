package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppLockManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class AppLockSettingsActivity : ComponentActivity() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var enableAppLockSwitch: SwitchCompat
    private lateinit var fingerprintSwitch: SwitchCompat
    private lateinit var fingerprintLayout: View
    private lateinit var changePinButton: Button
    private lateinit var resetAppLockButton: Button
    private var isPinVerifiedForThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)
        applyContentInsets()

        appLockManager = AppLockManager(this)
        setupViews()
        setupExpandableSections()
        setupListeners()
        recreateAppsList()
    }

    private fun setupViews() {
        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))
        enableAppLockSwitch = findViewById(R.id.enable_app_lock_switch)
        fingerprintSwitch = findViewById(R.id.fingerprint_switch)
        fingerprintLayout = findViewById(R.id.fingerprint_layout)
        changePinButton = findViewById(R.id.change_pin_button)
        resetAppLockButton = findViewById(R.id.reset_app_lock_button)
        appsRecyclerView = findViewById(R.id.apps_recycler_view)
        appsRecyclerView.layoutManager = LinearLayoutManager(this)

        enableAppLockSwitch.isChecked = appLockManager.isAppLockEnabled()
        updatePinButtonText()

        if (appLockManager.isFingerprintAvailable()) {
            fingerprintLayout.isVisible = true
            fingerprintSwitch.isChecked = appLockManager.isFingerprintEnabled()
        } else {
            fingerprintLayout.isVisible = false
        }
    }

    private fun updatePinButtonText() {
        changePinButton.text = if (appLockManager.isPinSet()) getString(R.string.change_access_pin) else getString(R.string.set_access_pin)
    }

    private fun setupListeners() {
        enableAppLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !appLockManager.isPinSet()) {
                appLockManager.setupPin { success: Boolean ->
                    if (!success) enableAppLockSwitch.isChecked = false
                    else updatePinButtonText()
                }
            } else if (!isChecked && appLockManager.isPinSet()) {
                appLockManager.verifyPin { auth: Boolean ->
                    if (auth) appLockManager.setAppLockEnabled(false)
                    else enableAppLockSwitch.isChecked = true
                }
            } else appLockManager.setAppLockEnabled(isChecked)
        }

        fingerprintSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (appLockManager.isPinSet()) appLockManager.setFingerprintEnabled(isChecked)
            else {
                fingerprintSwitch.isChecked = false
                Toast.makeText(this, "Set PIN first", Toast.LENGTH_SHORT).show()
            }
        }

        changePinButton.setOnClickListener {
            if (appLockManager.isPinSet()) {
                appLockManager.changePin { success: Boolean ->
                    if (success) updatePinButtonText()
                }
            } else {
                appLockManager.setupPin { success: Boolean ->
                    if (success) {
                        updatePinButtonText()
                        enableAppLockSwitch.isChecked = true
                    }
                }
            }
        }

        resetAppLockButton.setOnClickListener {
            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Reset Vault")
                .setMessage("This will wipe the PIN and unlock everything. Continue?")
                .setPositiveButton("Reset") { _, _ ->
                    appLockManager.resetAppLock { success: Boolean ->
                        if (success) {
                            enableAppLockSwitch.isChecked = false
                            fingerprintSwitch.isChecked = false
                            updatePinButtonText()
                            recreateAppsList()
                        }
                    }
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun setupExpandableSections() {
        setupSectionToggle(findViewById(R.id.app_lock_settings_header), findViewById(R.id.app_lock_settings_content), findViewById(R.id.app_lock_settings_arrow))
        setupSectionToggle(findViewById(R.id.apps_list_header), appsRecyclerView, findViewById(R.id.apps_list_arrow))
    }

    private fun setupSectionToggle(header: View, content: View, arrow: TextView) {
        header.setOnClickListener {
            val vis = content.isVisible
            content.isVisible = !vis
            arrow.animate().rotation(if (vis) 0f else 180f).setDuration(250).start()
        }
    }

    private fun recreateAppsList() {
        appsRecyclerView.adapter = AppLockAdapter(
            getInstalledApps(), appLockManager,
            requestPinAuth = { onSuccess ->
                if (isPinVerifiedForThisSession) onSuccess()
                else appLockManager.verifyPin { success: Boolean ->
                    if (success) { isPinVerifiedForThisSession = true; onSuccess() }
                    else recreateAppsList()
                }
            }
        )
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                   else pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.appName }
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

    data class AppInfo(val packageName: String, val appName: String, val icon: android.graphics.drawable.Drawable)

    class AppLockAdapter(private val apps: List<AppInfo>, private val manager: AppLockManager, private val requestPinAuth: (onSuccess: () -> Unit) -> Unit) : RecyclerView.Adapter<AppLockAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val sw: SwitchCompat = v.findViewById(R.id.lock_switch)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_app_lock, p, false))
        override fun getItemCount() = apps.size
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val app = apps[p]
            h.icon.setImageDrawable(app.icon)
            h.name.text = app.appName
            h.sw.setOnCheckedChangeListener(null)
            h.sw.isChecked = manager.isAppLocked(app.packageName)
            h.sw.setOnCheckedChangeListener { _, isChecked ->
                requestPinAuth {
                    if (isChecked) manager.lockApp(app.packageName) else manager.unlockApp(app.packageName)
                }
                if (!manager.isPinSet()) h.sw.isChecked = false
            }
        }
    }
}
