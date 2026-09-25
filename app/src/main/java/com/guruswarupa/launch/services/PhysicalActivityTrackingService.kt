package com.guruswarupa.launch.services

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.app.Service
import com.guruswarupa.launch.managers.PhysicalActivityManager
import com.guruswarupa.launch.managers.ServiceNotificationManager

class PhysicalActivityTrackingService : Service() {

    private var activityManager: PhysicalActivityManager? = null

    companion object {
        private const val TAG = "PhysicalActivityService"
        private const val SERVICE_NAME = "Activity Tracking"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
        }

        activityManager = PhysicalActivityManager(this)
        activityManager?.initializeAsync(autoStartTracking = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
        }

        activityManager?.let { manager ->
            if (manager.hasActivityRecognitionPermission()) {
                manager.startTracking()
            } else {
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activityManager?.stopTracking()
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
