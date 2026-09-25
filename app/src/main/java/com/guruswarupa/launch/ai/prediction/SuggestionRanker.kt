package com.guruswarupa.launch.ai.prediction

import java.util.Calendar

object SuggestionRanker {

    const val CONTEXT_BUCKETS = 8

    private const val FREQUENCY_WEIGHT = 0.45
    private const val RECENCY_WEIGHT = 0.35
    private const val CONTEXT_WEIGHT = 0.20
    private const val RECENCY_HALF_LIFE_HOURS = 72.0

    fun timeBucketOf(nowMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 0 until 6 -> 0
            in 6 until 12 -> 1
            in 12 until 18 -> 2
            else -> 3
        }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        return timeOfDay * 2 + if (isWeekend) 1 else 0
    }

    fun score(
        launchCount: Int,
        lastLaunchMillis: Long,
        contextCounts: IntArray,
        nowMillis: Long,
        currentBucket: Int,
        maxLaunchCount: Int,
        usagePriorNormalized: Double = 0.0
    ): Double {
        if (launchCount <= 0) {
            return FREQUENCY_WEIGHT * usagePriorNormalized.coerceIn(0.0, 1.0)
        }

        val frequency = if (maxLaunchCount > 0) launchCount.toDouble() / maxLaunchCount else 0.0

        val hoursSinceLastLaunch = (nowMillis - lastLaunchMillis).coerceAtLeast(0L) / 3_600_000.0
        val recency = Math.pow(0.5, hoursSinceLastLaunch / RECENCY_HALF_LIFE_HOURS)

        val totalContextCount = contextCounts.sum()
        val contextAffinity = if (totalContextCount > 0) {
            contextCounts[currentBucket].toDouble() / totalContextCount
        } else 0.0

        return FREQUENCY_WEIGHT * frequency + RECENCY_WEIGHT * recency + CONTEXT_WEIGHT * contextAffinity
    }
}
