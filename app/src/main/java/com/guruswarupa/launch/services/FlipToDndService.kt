package com.guruswarupa.launch.services

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.guruswarupa.launch.managers.ServiceNotificationManager

class FlipToDndService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    private var isFaceDown = false
    private var isProximityNear = false

    private lateinit var notificationManager: NotificationManager
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "FlipToDndService"
        private const val SERVICE_NAME = "Flip to DND"

        fun startService(context: Context) {
            val intent = Intent(context, FlipToDndService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FlipToDndService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            sharedPreferences = getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)

            registerSensors()
        } catch (e: Exception) {
        }
    }

    private fun startForegroundServiceStatus() {
        val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        try {
            startForegroundServiceStatus()
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val z = event.values[2]

                isFaceDown = z < -8.0
                updateDndState()
            }
            Sensor.TYPE_PROXIMITY -> {

                isProximityNear = event.values[0] < (proximitySensor?.maximumRange ?: 5f)
                updateDndState()
            }
        }
    }

    private fun updateDndState() {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val isFocusModeActive = sharedPreferences.getBoolean("focus_mode_enabled", false)
        if (isFocusModeActive) return

        if (isFaceDown && isProximityNear) {
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } else {
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(this)

            val isFocusModeActive = sharedPreferences.getBoolean("focus_mode_enabled", false)
            if (!isFocusModeActive && notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
        } catch (e: Exception) {
        }
    }
}
