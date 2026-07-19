package com.guruswarupa.launch.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TodoItem(
    @Json(name = "id") val id: Long = System.nanoTime(),
    val text: String,
    var isChecked: Boolean,
    @Json(name = "isRecurring") val isRecurring: Boolean = false,
    @Json(name = "lastCompletedDate") var lastCompletedDate: String? = null,
    @Json(name = "selectedDays") val selectedDays: Set<Int> = emptySet(),
    @Json(name = "priority") val priority: Priority = Priority.MEDIUM,
    @Json(name = "category") val category: String = "General",
    @Json(name = "dueTime") val dueTime: String? = null,
    @Json(name = "recurrenceInterval") val recurrenceInterval: Int? = null,
    @Json(name = "intervalStartTime") val intervalStartTime: String? = null
) {
    enum class Priority(val displayName: String, val color: String) {
        @Json(name = "HIGH") HIGH("High", "#FF5722"),
        @Json(name = "MEDIUM") MEDIUM("Medium", "#FF9800"),
        @Json(name = "LOW") LOW("Low", "#4CAF50")
    }

    fun isIntervalBased(): Boolean = recurrenceInterval != null && recurrenceInterval > 0
    fun isDayBased(): Boolean = selectedDays.isNotEmpty()
}
