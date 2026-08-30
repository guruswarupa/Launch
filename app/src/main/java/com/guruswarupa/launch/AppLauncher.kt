package com.guruswarupa.launch

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.ai.prediction.LaunchEventStore
import com.guruswarupa.launch.managers.AppLockManager

class AppLauncher(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val appLockManager: AppLockManager,
    private val launchEventStore: LaunchEventStore,
) {

    fun launchApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null && intent.resolveActivity(packageManager) != null) {
                activity.startActivity(intent)
                launchEventStore.recordEvent(packageName)
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_app_is_not_installed, appName), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_error_opening_app, appName), Toast.LENGTH_SHORT).show()
        }
    }


    fun launchAppWithLockCheck(packageName: String, appName: String) {
        if (appLockManager.isAppLocked(packageName)) {
            appLockManager.verifyPin { isAuthenticated ->
                if (isAuthenticated) {
                    launchApp(packageName, appName)
                }
            }
        } else {
            launchApp(packageName, appName)
        }
    }
}
