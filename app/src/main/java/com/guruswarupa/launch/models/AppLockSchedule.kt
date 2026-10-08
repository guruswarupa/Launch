package com.guruswarupa.launch.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Calendar

@JsonClass(generateAdapter = true)
data class AppLockSchedule(
    @Json(name = "selectedDays") val selectedDays: Set<Int> = emptySet(),
    @Json(name = "startTime") val startTime: String,
    @Json(name = "endTime") val endTime: String
) {
    fun appliesToday(): Boolean {
        if (selectedDays.isEmpty()) return true
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return selectedDays.contains(today)
    }

    fun isWithinTimeRange(nowMinutes: Int): Boolean {
        val start = parseMinutes(startTime) ?: return true
        val end = parseMinutes(endTime) ?: return true
        return if (start <= end) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    private fun parseMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }
}
