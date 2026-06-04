package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.adapters.AppPrivacyInfo
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.ui.adapters.PrivacyDashboardAdapter
import java.util.concurrent.Executors

class PrivacyDashboardActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PrivacyDashboardAdapter
    private lateinit var searchBox: EditText


    private lateinit var statTotalValue: TextView
    private lateinit var statCriticalValue: TextView
    private lateinit var statSideloadedValue: TextView
    private lateinit var chipGroup: ChipGroup

    private var allApps = listOf<AppPrivacyInfo>()
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    private val sensitivePermissions = listOfNotNull(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_SMS,
        Manifest.permission.CALL_PHONE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_privacy_dashboard)
        applyBackgroundTranslucency()
        applyContentInsets()

        setupTheme()

        recyclerView = findViewById(R.id.privacy_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PrivacyDashboardAdapter(emptyList())
        recyclerView.adapter = adapter


        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator?.apply {
            addDuration = 0
            moveDuration = 0
        }

        searchBox = findViewById(R.id.search_box)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        statTotalValue = findViewById(R.id.stat_total_value)
        statCriticalValue = findViewById(R.id.stat_critical_value)
        statSideloadedValue = findViewById(R.id.stat_sideloaded_value)
        chipGroup = findViewById(R.id.filter_chip_group)

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            applyFilters()
        }

        loadApps()
    }

    override fun onBackPressed() {
        finish()
    }

    private fun setupTheme() {

        applyBackgroundTranslucency()
        setupWallpaper()
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
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

    private fun loadApps() {
        executor.execute {
            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val appPrivacyList = packages.mapNotNull { packageInfo ->
                val granted = mutableListOf<String>()
                packageInfo.requestedPermissions?.forEachIndexed { index, permission ->
                    if (permission in sensitivePermissions) {
                        val flag = packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                        if ((flag and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            granted.add(permission)
                        }
                    }
                }

                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null

                if (granted.isNotEmpty() || (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        packageInfo.packageName
                    }

                    val severity = when {
                        granted.any { it in listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_SMS, Manifest.permission.ACCESS_FINE_LOCATION) } -> 2
                        granted.isNotEmpty() -> 1
                        else -> 0
                    }


                    @Suppress("DEPRECATION")
                    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            pm.getInstallSourceInfo(packageInfo.packageName).installingPackageName
                        } catch (_: Exception) { null }
                    } else {
                        pm.getInstallerPackageName(packageInfo.packageName)
                    }

                    val isSideloaded = installer == null ||
                        installer.isEmpty() ||
                        installer in listOf("com.android.packageinstaller", "com.google.android.packageinstaller")

                    AppPrivacyInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        grantedPermissions = granted,
                        severity = severity,
                        isSideloaded = isSideloaded
                    )
                } else {
                    null
                }
            }.sortedWith(compareByDescending<AppPrivacyInfo> { it.severity }
                .thenByDescending { it.grantedPermissions.size }
                .thenBy { it.appName.lowercase() })

            val totalApps = appPrivacyList.size
            val criticalApps = appPrivacyList.count { it.severity == 2 }
            val sideloadedApps = appPrivacyList.count { it.isSideloaded }

            runOnUiThread {
                allApps = appPrivacyList
                statTotalValue.text = totalApps.toString()
                statCriticalValue.text = criticalApps.toString()
                statSideloadedValue.text = sideloadedApps.toString()
                adapter.updateData(allApps)
            }
        }
    }

    private fun applyFilters() {
        val query = searchBox.text.toString().trim()
        val checkedChipId = chipGroup.checkedChipId

        val filtered = allApps.filter { app ->
            val matchesQuery = query.isEmpty() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)

            val matchesChip = when (checkedChipId) {
                R.id.chip_camera -> app.grantedPermissions.contains(Manifest.permission.CAMERA)
                R.id.chip_microphone -> app.grantedPermissions.contains(Manifest.permission.RECORD_AUDIO)
                R.id.chip_location -> app.grantedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                                     app.grantedPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                R.id.chip_sideloaded -> app.isSideloaded
                else -> true
            }

            matchesQuery && matchesChip
        }
        adapter.updateData(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
