package com.guruswarupa.launch.ai.prediction

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionEngine @Inject constructor(
    private val launchEventStore: LaunchEventStore
) {
    data class Ranked(val key: String, val score: Double)

    fun rank(
        candidateKeys: Collection<String>,
        usagePrior: Map<String, Long> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<Ranked> {
        if (candidateKeys.isEmpty()) return emptyList()

        val snapshot = launchEventStore.snapshot()
        val maxLaunchCount = snapshot.values.maxOfOrNull { it.launchCount } ?: 0
        val maxUsage = usagePrior.values.maxOrNull()?.takeIf { it > 0 }
        val bucket = SuggestionRanker.timeBucketOf(nowMillis)

        val scored = candidateKeys.mapIndexed { index, key ->
            val entry = snapshot[key]
            val usagePriorNormalized = maxUsage?.let { (usagePrior[key] ?: 0L).toDouble() / it } ?: 0.0
            val score = SuggestionRanker.score(
                launchCount = entry?.launchCount ?: 0,
                lastLaunchMillis = entry?.lastLaunchMillis ?: 0L,
                contextCounts = entry?.contextCounts ?: IntArray(SuggestionRanker.CONTEXT_BUCKETS),
                nowMillis = nowMillis,
                currentBucket = bucket,
                maxLaunchCount = maxLaunchCount,
                usagePriorNormalized = usagePriorNormalized
            )
            index to Ranked(key, score)
        }

        return scored.sortedWith(compareByDescending<Pair<Int, Ranked>> { it.second.score }.thenBy { it.first })
            .map { it.second }
    }

    fun topKeys(candidateKeys: Collection<String>, usagePrior: Map<String, Long> = emptyMap(), limit: Int): List<String> =
        rank(candidateKeys, usagePrior).take(limit).map { it.key }

    fun generation(): Int = launchEventStore.generation.get()
}
