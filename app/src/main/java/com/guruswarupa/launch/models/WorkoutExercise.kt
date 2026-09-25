package com.guruswarupa.launch.models

import java.util.Locale

enum class ExerciseType {
    REPS,
    TIME
}

data class WorkoutExercise(
    val id: String,
    val name: String,
    val type: ExerciseType = ExerciseType.REPS,
    var todayCount: Int = 0,
    var totalCount: Int = 0,
    var bestDay: Int = 0,
    var lastWorkoutDate: String? = null,
    var workoutDates: Set<String> = emptySet(),
    var dailyCounts: Map<String, Int> = emptyMap()
) {
    fun toJson(): String {
        val datesStr = workoutDates.joinToString(",")
        val typeStr = if (type == ExerciseType.TIME) "TIME" else "REPS"
        val dailyCountsStr = dailyCounts.entries.joinToString(";") { "${it.key}:${it.value}" }
        return "$id|$name|$typeStr|$todayCount|$totalCount|$bestDay|${lastWorkoutDate ?: ""}|$datesStr|$dailyCountsStr"
    }

    fun increment(amount: Int = 1) {
        val today = getCurrentDate()
        todayCount += amount
        totalCount += amount
        if (todayCount > bestDay) {
            bestDay = todayCount
        }
        lastWorkoutDate = today
        workoutDates = workoutDates + today

        val currentDayCount = dailyCounts[today] ?: 0
        dailyCounts = dailyCounts + (today to (currentDayCount + amount))
    }

    fun getCountForDate(date: String): Int {
        return dailyCounts[date] ?: 0
    }

    fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
            else -> String.format(Locale.getDefault(), "%ds", secs)
        }
    }

    fun getDisplayValue(): String {
        return if (type == ExerciseType.TIME) {
            formatTime(todayCount)
        } else {
            todayCount.toString()
        }
    }

    fun getTotalDisplayValue(): String {
        return if (type == ExerciseType.TIME) {
            formatTime(totalCount)
        } else {
            totalCount.toString()
        }
    }

    fun getBestDisplayValue(): String {
        return if (type == ExerciseType.TIME) {
            formatTime(bestDay)
        } else {
            bestDay.toString()
        }
    }

    fun resetToday() {
        val today = getCurrentDate()
        val todayCountValue = todayCount

        totalCount -= todayCountValue

        dailyCounts = dailyCounts - today

        if (todayCountValue == bestDay && todayCountValue > 0) {
            bestDay = if (dailyCounts.isNotEmpty()) {
                dailyCounts.values.maxOrNull() ?: 0
            } else {
                0
            }
        }

        todayCount = 0
    }

    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return String.format(Locale.getDefault(), "%d-%d-%d", year, month, day)
    }

    companion object {
        private val PRESET_REPS_EXERCISES = listOf(
            "Push-ups", "Sit-ups", "Squats", "Burpees",
            "Jumping Jacks", "Lunges", "Mountain Climbers", "Crunches", "Leg Raises"
        )

        private val PRESET_TIME_EXERCISES = listOf(
            "Plank", "Wall Sit", "Hollow Hold", "Side Plank", "Dead Hang"
        )

        fun getPresets(type: ExerciseType): List<String> {
            return if (type == ExerciseType.TIME) {
                PRESET_TIME_EXERCISES
            } else {
                PRESET_REPS_EXERCISES
            }
        }

        fun fromJson(json: String): WorkoutExercise? {
            val parts = json.split("|")
            if (parts.size >= 3) {

                val typeStr = parts.getOrNull(2) ?: "REPS"
                val exerciseType = if (typeStr == "TIME") ExerciseType.TIME else ExerciseType.REPS

                val todayCountIndex = if (typeStr == "TIME" || typeStr == "REPS") 3 else 2
                val totalCountIndex = todayCountIndex + 1
                val bestDayIndex = totalCountIndex + 1
                val lastDateIndex = bestDayIndex + 1
                val datesIndex = lastDateIndex + 1
                val dailyCountsIndex = datesIndex + 1

                val datesStr = parts.getOrNull(datesIndex)?.takeIf { it.isNotEmpty() } ?: ""
                val workoutDates = if (datesStr.isNotEmpty()) {
                    datesStr.split(",").toSet()
                } else {
                    emptySet()
                }

                val dailyCountsStr = parts.getOrNull(dailyCountsIndex)?.takeIf { it.isNotEmpty() } ?: ""
                val dailyCounts = if (dailyCountsStr.isNotEmpty()) {
                    dailyCountsStr.split(";").associate { entry ->
                        val keyValue = entry.split(":")
                        if (keyValue.size == 2) {
                            keyValue[0] to (keyValue[1].toIntOrNull() ?: 0)
                        } else {
                            "" to 0
                        }
                    }.filterKeys { it.isNotEmpty() }
                } else {
                    emptyMap()
                }

                return WorkoutExercise(
                    id = parts[0],
                    name = parts[1],
                    type = exerciseType,
                    todayCount = parts.getOrNull(todayCountIndex)?.toIntOrNull() ?: 0,
                    totalCount = parts.getOrNull(totalCountIndex)?.toIntOrNull() ?: 0,
                    bestDay = parts.getOrNull(bestDayIndex)?.toIntOrNull() ?: 0,
                    lastWorkoutDate = parts.getOrNull(lastDateIndex)?.takeIf { it.isNotEmpty() },
                    workoutDates = workoutDates,
                    dailyCounts = dailyCounts
                )
            }
            return null
        }
    }
}
