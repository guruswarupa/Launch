package com.guruswarupa.launch.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class ScreenOffAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {

    }

    override fun onDisabled(context: Context, intent: Intent) {

    }
}
