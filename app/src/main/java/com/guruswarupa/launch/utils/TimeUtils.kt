package com.guruswarupa.launch.utils

import java.util.Calendar

object TimeUtils {

    fun formatDuration(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0m"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun startOfToday(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun endOfToday(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    fun safeExecuteOn(
        isActivityAlive: () -> Boolean,
        executor: java.util.concurrent.Executor,
        checkShutdown: Boolean = false,
        task: () -> Unit
    ): Boolean {
        if (!isActivityAlive()) return false
        if (checkShutdown && (executor as? java.util.concurrent.ExecutorService)?.isShutdown == true) {
            return false
        }
        return try {
            executor.execute(task)
            true
        } catch (e: Exception) {
            false
        }
    }
}
