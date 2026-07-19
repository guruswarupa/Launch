package com.guruswarupa.launch

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.managers.AppLockManager

class AppLauncher(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val appLockManager: AppLockManager,
) {

    fun launchApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null && intent.resolveActivity(packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "$appName app is not installed.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(activity, "Error opening $appName app.", Toast.LENGTH_SHORT).show()
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
