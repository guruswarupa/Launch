package com.guruswarupa.launch.managers

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.managers.AppTimerManager
import com.guruswarupa.launch.R

class AppUsageMonitor : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private lateinit var appTimerManager: AppTimerManager
    private lateinit var usageStatsManager: UsageStatsManager

    companion object {
        private const val SERVICE_NAME = "App Usage Monitor"
        private const val POLLING_INTERVAL_MS = 3000L

        fun shouldRun(context: Context): Boolean {
            val usageManager = AppUsageStatsManager(context)
            if (!usageManager.hasUsageStatsPermission()) {
                return false
            }
            return AppTimerManager(context).getAppsWithTimers().isNotEmpty()
        }

        fun syncMonitoring(context: Context) {
            val intent = Intent(context, AppUsageMonitor::class.java)
            if (shouldRun(context)) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appTimerManager = AppTimerManager(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ServiceNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitoringRunnable != null) return

        val runnable = object : Runnable {
            override fun run() {
                checkForegroundAppUsage()

                handler.postDelayed(this, POLLING_INTERVAL_MS)
            }
        }
        monitoringRunnable = runnable
        handler.post(runnable)
    }

    private fun checkForegroundAppUsage() {
        val currentApp = getForegroundApp()


        if (currentApp != null && currentApp != packageName &&
            !currentApp.startsWith("com.android.systemui")) {

            if (appTimerManager.isAppOverDailyLimit(currentApp)) {
                handler.post {
                    Toast.makeText(this@AppUsageMonitor, R.string.daily_usage_limit_reached, Toast.LENGTH_LONG).show()
                    appTimerManager.returnToLauncher(currentApp)
                }
            }
        }
    }

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 60 * 1000, time)

        if (stats != null) {
            var lastApp: String? = null
            var lastTime = 0L
            for (usageStats in stats) {
                if (usageStats.lastTimeUsed > lastTime) {
                    lastTime = usageStats.lastTimeUsed
                    lastApp = usageStats.packageName
                }
            }
            return lastApp
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
