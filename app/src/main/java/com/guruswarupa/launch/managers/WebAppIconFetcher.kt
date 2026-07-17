package com.guruswarupa.launch.managers

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.drawable.toDrawable
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

object WebAppIconFetcher {
    private val memoryCache = ConcurrentHashMap<String, Drawable>()
    private val pendingCallbacks = ConcurrentHashMap<String, MutableList<(Drawable?) -> Unit>>()
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun clearCache() {
        memoryCache.clear()
    }

    fun shutdown() {
        if (!executor.isShutdown) {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                executor.shutdownNow()
            }
        }
        memoryCache.clear()
        pendingCallbacks.clear()
    }

    fun loadIcon(
        context: Context,
        siteUrl: String,
        onResult: (Drawable?) -> Unit
    ) {
        memoryCache[siteUrl]?.let {

            if (!(it is android.graphics.drawable.BitmapDrawable && it.bitmap.isRecycled)) {
                onResult(it)
            } else {
                memoryCache.remove(siteUrl)
                loadIcon(context, siteUrl, onResult)
            }
            return
        }

        if (executor.isShutdown || executor.isTerminated) {
            onResult(null)
            return
        }

        val callbacks = pendingCallbacks.compute(siteUrl) { _, existing ->
            (existing ?: mutableListOf()).also { it.add(onResult) }
        } ?: mutableListOf(onResult)

        if (callbacks.size > 1) {
            return
        }

        try {
            executor.execute {
                val drawable = try {
                    val loaded = loadFromDisk(context, siteUrl) ?: fetchAndCache(context, siteUrl)
                    loaded?.let { memoryCache[siteUrl] = it }
                    loaded
                } catch (_: Exception) {
                    null
                }

                val waitingCallbacks = pendingCallbacks.remove(siteUrl).orEmpty()
                try {
                    mainHandler.post {
                        waitingCallbacks.forEach { callback -> callback(drawable) }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: RejectedExecutionException) {
            pendingCallbacks.remove(siteUrl)?.forEach { callback -> callback(null) }
            onResult(null)
        }
    }

    private fun fetchAndCache(context: Context, siteUrl: String): Drawable? {
        val iconBytes = resolveIconBytes(siteUrl) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
            ?: fallbackFaviconUrl(siteUrl)?.takeIf { it != siteUrl }?.let(::fetchBytes)?.let { fallbackBytes ->
                BitmapFactory.decodeByteArray(fallbackBytes, 0, fallbackBytes.size)?.also {
                    runCatching {
                        getIconFile(context, siteUrl).parentFile?.mkdirs()
                        getIconFile(context, siteUrl).writeBytes(fallbackBytes)
                    }
                }
            }
            ?: return null
        val file = getIconFile(context, siteUrl)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(iconBytes)
        }
        return bitmap.toDrawable(context.resources)
    }

    private fun loadFromDisk(context: Context, siteUrl: String): Drawable? {
        val file = getIconFile(context, siteUrl)
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bitmap.toDrawable(context.resources)
    }

    private fun resolveIconBytes(siteUrl: String): ByteArray? {
        val html = fetchText(siteUrl)
        val resolvedIconUrl = html?.let { extractIconUrl(siteUrl, it) } ?: fallbackFaviconUrl(siteUrl)
        return resolvedIconUrl?.let(::fetchBytes)
    }

    private fun fetchText(targetUrl: String): String? {
        val connection = (URL(targetUrl).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Launch WebApp Icon Fetcher")
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBytes(targetUrl: String): ByteArray? {
        val connection = (URL(targetUrl).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Launch WebApp Icon Fetcher")
            connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun extractIconUrl(siteUrl: String, html: String): String? {
        val matches = LINK_TAG_REGEX.findAll(html)
            .mapNotNull { match ->
                val tag = match.value
                val rel = REL_REGEX.find(tag)?.groupValues?.getOrNull(2)?.lowercase(Locale.US).orEmpty()
                val href = HREF_REGEX.find(tag)?.groupValues?.getOrNull(2).orEmpty()
                if (href.isBlank() || !rel.contains("icon")) return@mapNotNull null
                rel to href
            }
            .toList()

        val preferredHref = matches.firstOrNull { it.first.contains("apple-touch-icon") }?.second
            ?: matches.firstOrNull { it.first.contains("shortcut icon") }?.second
            ?: matches.firstOrNull()?.second

        return preferredHref?.let { resolveUrl(siteUrl, it) } ?: fallbackFaviconUrl(siteUrl)
    }

    private fun fallbackFaviconUrl(siteUrl: String): String? {
        return runCatching {
            val uri = URI(siteUrl)
            URI(uri.scheme ?: "https", uri.authority, "/favicon.ico", null, null).toString()
        }.getOrNull()
    }

    private fun resolveUrl(baseUrl: String, href: String): String? {
        return runCatching { URI(baseUrl).resolve(href).toString() }.getOrNull()
    }

    private fun getIconFile(context: Context, siteUrl: String): File {
        return File(File(context.filesDir, "web_app_icons"), "${hash(siteUrl)}.bin")
    }

    private fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val LINK_TAG_REGEX = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val REL_REGEX = Regex("rel\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
    private val HREF_REGEX = Regex("href\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
}
