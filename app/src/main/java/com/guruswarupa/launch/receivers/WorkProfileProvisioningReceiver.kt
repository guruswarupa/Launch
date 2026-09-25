package com.guruswarupa.launch.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class WorkProfileProvisioningReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "WorkProfileReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, WorkProfileProvisioningReceiver::class.java)

            dpm.setProfileEnabled(adminComponent)

            dpm.setProfileName(adminComponent, "Work Profile")

        } catch (e: Exception) {
        }
    }
}
