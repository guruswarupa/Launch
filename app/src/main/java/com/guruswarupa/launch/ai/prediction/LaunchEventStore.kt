package com.guruswarupa.launch.ai.prediction

import android.content.Context
import com.guruswarupa.launch.di.BackgroundExecutor
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class LaunchEventStat(
    val launchCount: Int,
    val lastLaunchMillis: Long,
    val contextCounts: List<Int>
)

@Singleton
class LaunchEventStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackgroundExecutor private val backgroundExecutor: ExecutorService
) {
    data class Stats(
        val launchCount: Int,
        val lastLaunchMillis: Long,
        val contextCounts: IntArray
    )

    companion object {
        private val moshi = Moshi.Builder().build()
        private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, LaunchEventStat::class.java)
    }

    private val storeFile: File = File(context.filesDir, "launch_stats.json")
    private val statsLock = Any()
    private val stats = mutableMapOf<String, Stats>()

    val generation = AtomicInteger(0)

    @Volatile
    private var loaded = false

    fun recordEvent(key: String, nowMillis: Long = System.currentTimeMillis()) {
        backgroundExecutor.execute {
            loadIfNeeded()
            val bucket = SuggestionRanker.timeBucketOf(nowMillis)
            synchronized(statsLock) {
                val existing = stats[key]
                val contextCounts = existing?.contextCounts?.copyOf() ?: IntArray(SuggestionRanker.CONTEXT_BUCKETS)
                contextCounts[bucket] = contextCounts[bucket] + 1
                stats[key] = Stats(
                    launchCount = (existing?.launchCount ?: 0) + 1,
                    lastLaunchMillis = nowMillis,
                    contextCounts = contextCounts
                )
            }
            generation.incrementAndGet()
            persist()
        }
    }

    fun snapshot(): Map<String, Stats> {
        loadIfNeeded()
        synchronized(statsLock) {
            return HashMap(stats)
        }
    }

    private fun loadIfNeeded() {
        if (loaded) return
        synchronized(statsLock) {
            if (loaded) return
            try {
                if (storeFile.exists()) {
                    val json = FileInputStream(storeFile).use { it.bufferedReader().readText() }
                    val adapter = moshi.adapter<Map<String, LaunchEventStat>>(mapType)
                    adapter.fromJson(json)?.forEach { (key, dto) ->
                        stats[key] = Stats(
                            launchCount = dto.launchCount,
                            lastLaunchMillis = dto.lastLaunchMillis,
                            contextCounts = dto.contextCounts.toIntArray().copyOf(SuggestionRanker.CONTEXT_BUCKETS)
                        )
                    }
                }
            } catch (_: Exception) {

            }
            loaded = true
        }
    }

    private fun persist() {

        synchronized(statsLock) {
            try {
                val snapshotToSave = stats.mapValues { (_, s) -> LaunchEventStat(s.launchCount, s.lastLaunchMillis, s.contextCounts.toList()) }
                val adapter = moshi.adapter<Map<String, LaunchEventStat>>(mapType)
                val json = adapter.toJson(snapshotToSave)
                FileOutputStream(storeFile).use { it.bufferedWriter().write(json) }
            } catch (_: Exception) {
            }
        }
    }
}
