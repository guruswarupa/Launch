package com.guruswarupa.launch.managers

import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.TimeUtils
import java.util.concurrent.Executor

import com.guruswarupa.launch.managers.AppUsageStatsManager





class UsageStatsRefreshManager(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val usageStatsManager: AppUsageStatsManager
) {



    private fun safeExecute(task: Runnable): Boolean =
        TimeUtils.safeExecuteOn(
            isActivityAlive = { !(activity.isFinishing || activity.isDestroyed) },
            executor = backgroundExecutor
        ) { task.run() }




    fun updateUsageInBackground() {
        if (!usageStatsManager.hasUsageStatsPermission()) {
            activity.runOnUiThread {
                val usageTextView = activity.findViewById<TextView>(R.id.daily_usage_time)
                usageTextView?.text = "Usage: Tap to setup"
            }
            return
        }

        safeExecute {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val totalUsage = usageStatsManager.getTotalUsageForPeriod(startTime, endTime)
            val formattedUsage = usageStatsManager.formatUsageTime(totalUsage)

            activity.runOnUiThread {
                val usageTextView = activity.findViewById<TextView>(R.id.daily_usage_time)
                usageTextView?.text = "Usage: $formattedUsage"
            }
        }
    }
}
