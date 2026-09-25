package com.guruswarupa.launch.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.guruswarupa.launch.receivers.WorkProfileProvisioningReceiver
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class WorkProfileManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "WorkProfileManager"
        const val WORK_PROFILE_ENABLED_KEY = "work_profile_enabled"
        const val REQUEST_CODE_CREATE_WORK_PROFILE = 2000
        const val WORK_PROFILE_WORKSPACE_ID = "work_profile_workspace"
    }

    private val provisioningReceiverComponent = ComponentName(
        context,
        WorkProfileProvisioningReceiver::class.java
    )

    fun isWorkProfileSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    fun hasActualWorkProfile(): Boolean {
        return getWorkProfileUserHandle() != null
    }

    fun isWorkProfileEnabled(): Boolean {
        return sharedPreferences.getBoolean(WORK_PROFILE_ENABLED_KEY, false)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun createWorkProfile(activity: androidx.activity.ComponentActivity) {
        if (!isWorkProfileSupported()) {
            return
        }

        try {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, provisioningReceiverComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, "")
            }

            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CODE_CREATE_WORK_PROFILE)
        } catch (e: Exception) {
        }
    }

    fun setWorkProfileEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(WORK_PROFILE_ENABLED_KEY, enabled) }
    }

    fun syncWorkProfileEnabledState(): Boolean {
        val enabled = isWorkProfileAvailableAndEnabled()
        setWorkProfileEnabled(enabled)
        return enabled
    }

    fun isWorkProfileAvailableAndEnabled(): Boolean {
        val userHandle = getWorkProfileUserHandle() ?: return false
        return !isWorkProfileQuietModeEnabled(userHandle)
    }

    fun setWorkProfileQuietMode(enabled: Boolean): Boolean {
        val userHandle = getWorkProfileUserHandle() ?: return false
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val success = userManager.requestQuietModeEnabled(!enabled, userHandle)
            if (success) {
                setWorkProfileEnabled(enabled)
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    fun deleteWorkProfile() {
        setWorkProfileEnabled(false)
    }

    private fun getWorkProfileUserHandle(): UserHandle? {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val myUserHandle = Process.myUserHandle()
            launcherApps.profiles.firstOrNull { it != myUserHandle }
        } catch (e: Exception) {
            null
        }
    }

    private fun isWorkProfileQuietModeEnabled(userHandle: UserHandle): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            userManager.isQuietModeEnabled(userHandle)
        } catch (e: Exception) {
            true
        }
    }
}
