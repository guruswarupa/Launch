package com.guruswarupa.launch.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ExecutorService

sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class Plain(val text: String) : LyricsResult()
    object NotFound : LyricsResult()
}

/**
 * Fetches synced/plain lyrics for the currently playing track from lrclib.net (free, no API
 * key), caching results (including "not found") as small JSON files under filesDir so a song
 * isn't re-fetched every time it plays. Mirrors RssFeedManager's HttpURLConnection + executor +
 * main-handler-callback pattern.
 */
class LyricsManager(
    private val context: Context,
    private val backgroundExecutor: ExecutorService
) {
    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val NEGATIVE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_CACHED_FILES = 200
        private const val EVICT_COUNT = 50
        private const val DURATION_TOLERANCE_SEC = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheDir: File by lazy { File(context.filesDir, "lyrics").apply { mkdirs() } }

    @Volatile private var requestToken = 0

    /**
     * [callback] runs on the main thread. If a newer [fetch] call is made before this one
     * completes, this one's callback is dropped - guards against rapid track-skipping
     * resurrecting stale lyrics on screen.
     */
    fun fetch(track: NowPlaying, callback: (LyricsResult) -> Unit) {
        val token = ++requestToken
        val artist = track.artist.trim()
        val title = track.title.trim()
        if (artist.isEmpty() || title.isEmpty()) {
            callback(LyricsResult.NotFound)
            return
        }
        val durationSec = (track.durationMs / 1000).toInt()
        val cacheKey = cacheKey(artist, title, durationSec)

        backgroundExecutor.execute {
            val result = readCache(cacheKey) ?: fetchFromNetwork(artist, title, track.album, durationSec)
                .also { writeCache(cacheKey, it) }
            if (token == requestToken) {
                mainHandler.post { callback(result) }
            }
        }
    }

    private fun fetchFromNetwork(artist: String, title: String, album: String?, durationSec: Int): LyricsResult {
        return try {
            requestGet(artist, title, album, durationSec)
                ?: requestSearch(artist, title, durationSec)
                ?: LyricsResult.NotFound
        } catch (_: Exception) {
            LyricsResult.NotFound
        }
    }

    /** Returns null on a 404 (caller should fall back to search), a parsed result otherwise. */
    private fun requestGet(artist: String, title: String, album: String?, durationSec: Int): LyricsResult? {
        val url = buildString {
            append("$BASE_URL/get?artist_name=${encode(artist)}&track_name=${encode(title)}")
            if (!album.isNullOrBlank()) append("&album_name=${encode(album)}")
            if (durationSec > 0) append("&duration=$durationSec")
        }
        val connection = openConnection(url)
        return try {
            when (connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { parseTrackResponse(JSONObject(it.readText())) }
                404 -> null
                else -> LyricsResult.NotFound
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Looser fallback: search by "artist title" and take the closest duration match. */
    private fun requestSearch(artist: String, title: String, durationSec: Int): LyricsResult? {
        val connection = openConnection("$BASE_URL/search?q=${encode("$artist $title")}")
        return try {
            if (connection.responseCode != 200) return null
            val results = connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            var best: JSONObject? = null
            var bestDelta = Int.MAX_VALUE
            for (i in 0 until results.length()) {
                val candidate = results.getJSONObject(i)
                val candidateDuration = candidate.optInt("duration", -1)
                if (durationSec <= 0 || candidateDuration < 0) {
                    if (best == null) best = candidate
                    continue
                }
                val delta = kotlin.math.abs(candidateDuration - durationSec)
                if (delta <= DURATION_TOLERANCE_SEC && delta < bestDelta) {
                    best = candidate
                    bestDelta = delta
                }
            }
            best?.let { parseTrackResponse(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTrackResponse(json: JSONObject): LyricsResult {
        if (json.optBoolean("instrumental", false)) return LyricsResult.NotFound
        json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }?.let { synced ->
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        json.optString("plainLyrics", "").takeIf { it.isNotBlank() }?.let { plain ->
            return LyricsResult.Plain(plain)
        }
        return LyricsResult.NotFound
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "${context.packageName}/lyrics")
            instanceFollowRedirects = true
            connect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun cacheKey(artist: String, title: String, durationSec: Int): String {
        val raw = "${artist.lowercase()}|${title.lowercase()}|$durationSec"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(key: String) = File(cacheDir, "$key.json")

    private fun readCache(key: String): LyricsResult? {
        val file = cacheFile(key)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            if (!json.optBoolean("found", false)) {
                val ts = json.optLong("ts", 0L)
                if (System.currentTimeMillis() - ts > NEGATIVE_CACHE_TTL_MS) return null
                file.setLastModified(System.currentTimeMillis())
                return LyricsResult.NotFound
            }
            file.setLastModified(System.currentTimeMillis())

            val syncedArray = json.optJSONArray("synced")
            if (syncedArray != null && syncedArray.length() > 0) {
                val lines = (0 until syncedArray.length()).map {
                    val line = syncedArray.getJSONObject(it)
                    LyricLine(line.getLong("t"), line.getString("text"))
                }
                return LyricsResult.Synced(lines)
            }
            val plain = json.optString("plain", "")
            if (plain.isNotBlank()) return LyricsResult.Plain(plain)
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(key: String, result: LyricsResult) {
        try {
            val json = JSONObject().put("ts", System.currentTimeMillis())
            when (result) {
                is LyricsResult.Synced -> {
                    json.put("found", true)
                    val array = JSONArray()
                    result.lines.forEach { line ->
                        array.put(JSONObject().put("t", line.timeMs).put("text", line.text))
                    }
                    json.put("synced", array)
                }
                is LyricsResult.Plain -> {
                    json.put("found", true)
                    json.put("plain", result.text)
                }
                LyricsResult.NotFound -> json.put("found", false)
            }
            cacheFile(key).writeText(json.toString())
            evictIfNeeded()
        } catch (_: Exception) {
        }
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= MAX_CACHED_FILES) return
        files.sortedBy { it.lastModified() }.take(EVICT_COUNT).forEach { it.delete() }
    }
}
