package com.guruswarupa.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.os.UserManager
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.edit

class AppClickHandler(
    private val activity: MainActivity,
    private val context: Context,
    private val searchBox: AutoCompleteTextView,
    private val userManager: UserManager,
    private val mainUserSerial: Int,
    private val labelResolver: (String, ResolveInfo) -> String
) {
    private sealed interface LaunchResolution {
        data class IntentLaunch(val intent: Intent) : LaunchResolution
        data object AlreadyLaunched : LaunchResolution
        data object Failed : LaunchResolution
    }

    private val prefs = context.getSharedPreferences(com.guruswarupa.launch.models.Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val clickDebounceDelay = 500L

    fun handleAppClick(
        holder: AppAdapter.ViewHolder,
        appInfo: ResolveInfo,
        packageName: String,
        serial: Int
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - holder.lastClickTime < clickDebounceDelay) return
        holder.lastClickTime = currentTime

        if (activity.appTimerManager.isAppOverDailyLimit(packageName)) {
            Toast.makeText(activity, "Daily limit reached for ${labelResolver(packageName, appInfo)}", Toast.LENGTH_SHORT).show()
            return
        }

        when (val launchResolution = resolveLaunchIntent(appInfo, packageName, serial)) {
            LaunchResolution.AlreadyLaunched -> {
                recordUsage(packageName)
                return
            }
            LaunchResolution.Failed -> {
                Toast.makeText(activity, activity.getString(R.string.cannot_launch_app), Toast.LENGTH_SHORT).show()
                return
            }
            is LaunchResolution.IntentLaunch -> {
                recordUsage(packageName)
                val appName = labelResolver(packageName, appInfo)
                if (activity.appTimerManager.isSessionTimerEnabled(packageName)) {
                    activity.appTimerManager.showTimerDialog(appName) { timerDuration ->
                        launchWithOptionalLock(packageName, launchResolution.intent) {
                            activity.appTimerManager.startTimer(packageName, timerDuration)
                        }
                    }
                } else {
                    launchWithOptionalLock(packageName, launchResolution.intent)
                }
            }
        }
    }

    private fun recordUsage(packageName: String) {
        val currentCount = prefs.getInt("usage_$packageName", 0)
        prefs.edit { putInt("usage_$packageName", currentCount + 1) }
    }

    private fun resolveLaunchIntent(appInfo: ResolveInfo, packageName: String, serial: Int): LaunchResolution {
        val activityInfo = appInfo.activityInfo ?: return LaunchResolution.Failed
        val activityName = activityInfo.name
        if (packageName == activity.packageName) {
            return LaunchResolution.IntentLaunch(Intent().apply {
                component = ComponentName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        if (serial != mainUserSerial) {
            val userHandle = userManager.getUserForSerialNumber(serial.toLong())
            if (userHandle != null) {
                val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                if (launcherApps.isActivityEnabled(ComponentName(packageName, activityName), userHandle)) {
                    launcherApps.startMainActivity(ComponentName(packageName, activityName), userHandle, null, null)
                    clearSearch()
                    return LaunchResolution.AlreadyLaunched
                }
                return LaunchResolution.Failed
            }
            // No user handle for this serial: do not fall through to the main profile.
            return LaunchResolution.Failed
        }

        if (appInfo.activityInfo.enabled.not()) {
            return LaunchResolution.Failed
        }

        val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null && intent.resolveActivity(activity.packageManager) != null) {
            return LaunchResolution.IntentLaunch(intent)
        }
        return LaunchResolution.Failed
    }

    private fun launchWithOptionalLock(packageName: String, intent: Intent, afterLaunch: (() -> Unit)? = null) {
        if (activity.appLockManager.isAppLocked(packageName)) {
            activity.appLockManager.verifyPin { isAuthenticated ->
                if (isAuthenticated) {
                    activity.startActivity(intent)
                    afterLaunch?.invoke()
                    clearSearch()
                }
            }
            return
        }

        activity.startActivity(intent)
        afterLaunch?.invoke()
        clearSearch()
    }

    private fun clearSearch() {
        activity.runOnUiThread {
            searchBox.text.clear()
            activity.appSearchManager.filterAppsAndContacts("")
        }
    }
}
