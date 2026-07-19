package com.guruswarupa.launch.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity



class BroadcastReceiverManager(
    private val activity: FragmentActivity,
    @Suppress("UNUSED_PARAMETER") private val sharedPreferences: android.content.SharedPreferences,
    private val onSettingsUpdated: () -> Unit,
    private val onPackageChanged: (String?, Boolean) -> Unit,
    private val onWallpaperChanged: () -> Unit,
    private val onBatteryChanged: () -> Unit,
    private val onActivityRecognitionPermissionGranted: () -> Unit = {},
    private val onDndStateChanged: () -> Unit = {}
) {
    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                activity.runOnUiThread {
                    onSettingsUpdated()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        onPackageChanged(packageName, true)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        onPackageChanged(packageName, false)
                    }
                }
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val packageName = intent.data?.encodedSchemeSpecificPart
                    onPackageChanged(packageName, false)
                }
            }
        }
    }

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                activity.runOnUiThread {
                    onWallpaperChanged()
                }
            }
        }
    }

    private val batteryChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activity.runOnUiThread {
                onBatteryChanged()
            }
        }
    }

    private val activityRecognitionPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.ACTIVITY_RECOGNITION_PERMISSION_GRANTED") {
                activity.runOnUiThread {
                    onActivityRecognitionPermissionGranted()
                }
            }
        }
    }

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                activity.runOnUiThread {
                    onDndStateChanged()
                }
            }
        }
    }

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            activity.runOnUiThread { onPackageChanged(packageName, false) }
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            activity.runOnUiThread { onPackageChanged(packageName, true) }
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            activity.runOnUiThread { onPackageChanged(packageName, false) }
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { activity.runOnUiThread { onPackageChanged(it, false) } }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { activity.runOnUiThread { onPackageChanged(it, true) } }
        }
    }




    fun registerReceivers() {

        val settingsFilter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        registerReceiverCompat(settingsUpdateReceiver, settingsFilter, exported = false)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiverCompat(packageReceiver, packageFilter, exported = true)


        @Suppress("DEPRECATION")
        val wallpaperFilter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiverCompat(wallpaperChangeReceiver, wallpaperFilter, exported = true)


        registerReceiverCompat(batteryChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), exported = true)


        val activityRecognitionFilter = IntentFilter("com.guruswarupa.launch.ACTIVITY_RECOGNITION_PERMISSION_GRANTED")
        registerReceiverCompat(activityRecognitionPermissionReceiver, activityRecognitionFilter, exported = false)


        registerReceiverCompat(dndReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), exported = true)

        val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.registerCallback(launcherAppsCallback)
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter, exported: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
            activity.registerReceiver(receiver, filter, flags)
        } else {
            ContextCompat.registerReceiver(
                activity,
                receiver,
                filter,
                if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }




    fun unregisterReceivers() {
        val receivers = listOf(
            settingsUpdateReceiver,
            packageReceiver,
            wallpaperChangeReceiver,
            batteryChangeReceiver,
            activityRecognitionPermissionReceiver,
            dndReceiver
        )

        try {
            val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.unregisterCallback(launcherAppsCallback)
        } catch (_: Exception) {
        }

        for (receiver in receivers) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {

            }
        }
    }
}
