package com.guruswarupa.launch.managers

import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import java.util.concurrent.Executor

import com.guruswarupa.launch.managers.AppUsageStatsManager





class UsageStatsRefreshManager(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val usageStatsManager: AppUsageStatsManager
) {



    private fun safeExecute(task: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        try {
            backgroundExecutor.execute(task)
            return true
        } catch (e: Exception) {
            Log.w("UsageStatsRefreshManager", "Task rejected by executor", e)
            return false
        }
    }




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
