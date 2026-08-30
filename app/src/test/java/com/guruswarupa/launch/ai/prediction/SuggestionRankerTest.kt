package com.guruswarupa.launch.ai.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SuggestionRankerTest {

    private fun millisAt(hour: Int, dayOfWeek: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    @Test
    fun `never-launched key with no usage prior scores zero`() {
        val score = SuggestionRanker.score(
            launchCount = 0,
            lastLaunchMillis = 0L,
            contextCounts = IntArray(SuggestionRanker.CONTEXT_BUCKETS),
            nowMillis = System.currentTimeMillis(),
            currentBucket = 0,
            maxLaunchCount = 10
        )
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `never-launched key falls back to usage prior`() {
        val score = SuggestionRanker.score(
            launchCount = 0,
            lastLaunchMillis = 0L,
            contextCounts = IntArray(SuggestionRanker.CONTEXT_BUCKETS),
            nowMillis = System.currentTimeMillis(),
            currentBucket = 0,
            maxLaunchCount = 10,
            usagePriorNormalized = 1.0
        )
        assertTrue("cold-start prior should contribute a positive score", score > 0.0)
    }

    @Test
    fun `more frequently launched key scores higher, all else equal`() {
        val now = System.currentTimeMillis()
        val contextCounts = IntArray(SuggestionRanker.CONTEXT_BUCKETS)
        val frequent = SuggestionRanker.score(
            launchCount = 8, lastLaunchMillis = now, contextCounts = contextCounts,
            nowMillis = now, currentBucket = 0, maxLaunchCount = 10
        )
        val rare = SuggestionRanker.score(
            launchCount = 1, lastLaunchMillis = now, contextCounts = contextCounts,
            nowMillis = now, currentBucket = 0, maxLaunchCount = 10
        )
        assertTrue(frequent > rare)
    }

    @Test
    fun `recently launched key scores higher than a stale one, all else equal`() {
        val now = System.currentTimeMillis()
        val contextCounts = IntArray(SuggestionRanker.CONTEXT_BUCKETS)
        val recent = SuggestionRanker.score(
            launchCount = 5, lastLaunchMillis = now, contextCounts = contextCounts,
            nowMillis = now, currentBucket = 0, maxLaunchCount = 10
        )
        val stale = SuggestionRanker.score(
            launchCount = 5, lastLaunchMillis = now - 30L * 24 * 60 * 60 * 1000, contextCounts = contextCounts,
            nowMillis = now, currentBucket = 0, maxLaunchCount = 10
        )
        assertTrue(recent > stale)
    }

    @Test
    fun `key habitually opened in the current time bucket scores higher than one that never was`() {
        val now = System.currentTimeMillis()
        val morningPerson = IntArray(SuggestionRanker.CONTEXT_BUCKETS).apply { this[1] = 10 }
        val neverInBucket = IntArray(SuggestionRanker.CONTEXT_BUCKETS).apply { this[3] = 10 }

        val morningScore = SuggestionRanker.score(
            launchCount = 10, lastLaunchMillis = now, contextCounts = morningPerson,
            nowMillis = now, currentBucket = 1, maxLaunchCount = 10
        )
        val eveningAppAtMorningTime = SuggestionRanker.score(
            launchCount = 10, lastLaunchMillis = now, contextCounts = neverInBucket,
            nowMillis = now, currentBucket = 1, maxLaunchCount = 10
        )
        assertTrue(morningScore > eveningAppAtMorningTime)
    }

    @Test
    fun `time bucket split is night, morning, afternoon, evening crossed with weekday-weekend`() {
        // bucket = timeOfDay * 2 + (1 if weekend else 0)
        assertEquals(0, SuggestionRanker.timeBucketOf(millisAt(3, Calendar.MONDAY)))   // night, weekday
        assertEquals(2, SuggestionRanker.timeBucketOf(millisAt(9, Calendar.MONDAY)))   // morning, weekday
        assertEquals(4, SuggestionRanker.timeBucketOf(millisAt(15, Calendar.MONDAY)))  // afternoon, weekday
        assertEquals(6, SuggestionRanker.timeBucketOf(millisAt(21, Calendar.MONDAY)))  // evening, weekday

        // Weekend flips the low bit of the same time-of-day bucket.
        assertEquals(1, SuggestionRanker.timeBucketOf(millisAt(3, Calendar.SATURDAY))) // night, weekend
        assertEquals(3, SuggestionRanker.timeBucketOf(millisAt(9, Calendar.SUNDAY)))   // morning, weekend
    }
}
