package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

class AppLockSettingsActivity : AppCompatActivity() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var enableAppLockSwitch: SwitchCompat
    private lateinit var fingerprintSwitch: SwitchCompat
    private lateinit var fingerprintLayout: View
    private lateinit var changePinButton: Button
    private lateinit var resetAppLockButton: Button

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
                Toast.makeText(this, this.getString(R.string.toast_set_pin_first), Toast.LENGTH_SHORT).show()
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
                .setTitle(getString(R.string.dlg_reset_vault))
                .setMessage(getString(R.string.dlg_this_will_wipe_the_pin_and_unlock_everything_con))
                .setPositiveButton(getString(R.string.reset)) { _, _ ->
                    appLockManager.resetAppLock { success: Boolean ->
                        if (success) {
                            enableAppLockSwitch.isChecked = false
                            fingerprintSwitch.isChecked = false
                            updatePinButtonText()
                            recreateAppsList()
                        }
                    }
                }.setNegativeButton(getString(R.string.cancel_button), null).show()
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

                appLockManager.verifyPin { success: Boolean ->
                    if (success) onSuccess() else recreateAppsList()
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
            val scheduleButton: ImageView = v.findViewById(R.id.lock_schedule_button)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_app_lock, p, false))
        override fun getItemCount() = apps.size
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val app = apps[p]
            h.icon.setImageDrawable(app.icon)
            h.name.text = app.appName
            val isLocked = manager.getLockedApps().contains(app.packageName)
            h.sw.setOnCheckedChangeListener(null)
            h.sw.isChecked = isLocked
            h.scheduleButton.visibility = if (isLocked) View.VISIBLE else View.GONE
            h.scheduleButton.setOnClickListener {
                requestPinAuth {
                    showScheduleDialog(h.scheduleButton.context, app.packageName)
                }
            }
            h.sw.setOnCheckedChangeListener { _, isChecked ->
                requestPinAuth {
                    if (isChecked) manager.lockApp(app.packageName) else manager.unlockApp(app.packageName)
                    h.scheduleButton.visibility = if (isChecked) View.VISIBLE else View.GONE
                }
                if (!manager.isPinSet()) h.sw.isChecked = false
            }
        }

        private fun showScheduleDialog(context: android.content.Context, packageName: String) {
            val existing = manager.getAppLockSchedule(packageName)
            val dayLabels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val dayValues = intArrayOf(
                java.util.Calendar.SUNDAY, java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
                java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY,
                java.util.Calendar.SATURDAY
            )
            val checkedDays = BooleanArray(7) { existing?.selectedDays?.contains(dayValues[it]) == true }

            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(R.string.app_lock_schedule_title)
                .setMultiChoiceItems(dayLabels, checkedDays) { _, which, isChecked ->
                    checkedDays[which] = isChecked
                }
                .setPositiveButton(R.string.app_lock_schedule_pick_times) { _, _ ->
                    val selectedDays = dayValues.filterIndexed { index, _ -> checkedDays[index] }.toSet()
                    pickStartTime(context, packageName, selectedDays, existing)
                }
                .setNeutralButton(R.string.app_lock_schedule_remove) { _, _ ->
                    manager.setAppLockSchedule(packageName, null)
                }
                .setNegativeButton(R.string.cancel_button, null)
                .show()
        }

        private fun pickStartTime(context: android.content.Context, packageName: String, selectedDays: Set<Int>, existing: com.guruswarupa.launch.models.AppLockSchedule?) {
            val existingStart = existing?.startTime?.split(":")
            val startHour = existingStart?.getOrNull(0)?.toIntOrNull() ?: 9
            val startMinute = existingStart?.getOrNull(1)?.toIntOrNull() ?: 0

            TimePickerDialog(context, { _, h1, m1 ->
                val startTime = String.format(java.util.Locale.US, "%02d:%02d", h1, m1)
                val existingEnd = existing?.endTime?.split(":")
                val endHour = existingEnd?.getOrNull(0)?.toIntOrNull() ?: 17
                val endMinute = existingEnd?.getOrNull(1)?.toIntOrNull() ?: 0

                TimePickerDialog(context, { _, h2, m2 ->
                    val endTime = String.format(java.util.Locale.US, "%02d:%02d", h2, m2)
                    manager.setAppLockSchedule(
                        packageName,
                        com.guruswarupa.launch.models.AppLockSchedule(selectedDays, startTime, endTime)
                    )
                }, endHour, endMinute, true).show()
            }, startHour, startMinute, true).show()
        }
    }
}
