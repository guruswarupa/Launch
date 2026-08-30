package com.guruswarupa.launch.ai.prediction

import java.util.Calendar

/**
 * Pure scoring logic for on-device app/contact suggestions. No Android framework
 * dependencies, so this is directly unit-testable.
 */
object SuggestionRanker {

    const val CONTEXT_BUCKETS = 8

    private const val FREQUENCY_WEIGHT = 0.45
    private const val RECENCY_WEIGHT = 0.35
    private const val CONTEXT_WEIGHT = 0.20
    private const val RECENCY_HALF_LIFE_HOURS = 72.0 // 3 days

    /**
     * Time-of-day (Night/Morning/Afternoon/Evening) crossed with weekday-vs-weekend,
     * giving 8 context buckets used to learn "this app is usually opened at this time".
     */
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

    /**
     * @param launchCount total recorded launches for this key, 0 if never seen before.
     * @param lastLaunchMillis epoch millis of the most recent launch, ignored if [launchCount] is 0.
     * @param contextCounts per-bucket launch counts, size [CONTEXT_BUCKETS].
     * @param usagePriorNormalized 0..1 cold-start signal (e.g. today's foreground usage,
     *   normalized against the max across candidates) used when there is no launch history yet.
     */
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
