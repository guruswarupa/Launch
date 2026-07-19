package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.core.content.edit
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.WebAppEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class WebAppManager @Inject constructor(private val sharedPreferences: SharedPreferences) {
    companion object {
        const val WEB_APP_PACKAGE_PREFIX = "com.guruswarupa.launch.webapp."

        fun packageNameForId(id: String): String = "$WEB_APP_PACKAGE_PREFIX$id"
        fun isWebAppPackage(packageName: String): Boolean = packageName.startsWith(WEB_APP_PACKAGE_PREFIX)
        fun extractId(packageName: String): String? =
            packageName.takeIf(::isWebAppPackage)?.removePrefix(WEB_APP_PACKAGE_PREFIX)
    }

    fun getWebApps(): List<WebAppEntry> {
        val rawJson = sharedPreferences.getString(Constants.Prefs.WEB_APPS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(rawJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                    val name = item.optString("name").trim()
                    val url = item.optString("url").trim()
                    val blockRedirects = item.optBoolean("blockRedirects", true)
                    if (name.isNotBlank() && url.isNotBlank()) {
                        add(WebAppEntry(id = id, name = name, url = url, blockRedirects = blockRedirects))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getWebApp(packageName: String): WebAppEntry? {
        val id = extractId(packageName) ?: return null
        return getWebApps().firstOrNull { it.id == id }
    }

    fun getResolveInfos(): List<ResolveInfo> = getWebApps().map(::createResolveInfo)

    fun addWebApp(name: String, url: String, blockRedirects: Boolean = true) {
        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url)
        val updated = getWebApps().toMutableList().apply {
            add(WebAppEntry(UUID.randomUUID().toString(), normalizedName, normalizedUrl, blockRedirects))
        }
        saveWebApps(updated)
    }

    fun updateWebApp(id: String, name: String, url: String, blockRedirects: Boolean? = null) {
        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url)
        val updated = getWebApps().map { entry ->
            if (entry.id == id) {
                if (blockRedirects != null) {
                    entry.copy(name = normalizedName, url = normalizedUrl, blockRedirects = blockRedirects)
                } else {
                    entry.copy(name = normalizedName, url = normalizedUrl)
                }
            } else entry
        }
        saveWebApps(updated)
    }

    fun updateWebAppRedirect(id: String, blockRedirects: Boolean) {
        val updated = getWebApps().map { entry ->
            if (entry.id == id) entry.copy(blockRedirects = blockRedirects) else entry
        }
        saveWebApps(updated)
    }

    fun removeWebApp(id: String) {
        saveWebApps(getWebApps().filterNot { it.id == id })
    }

    fun createResolveInfo(entry: WebAppEntry): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = packageNameForId(entry.id)
                name = entry.name
                nonLocalizedLabel = entry.url
                applicationInfo = ApplicationInfo().apply {
                    packageName = this@apply.packageName
                }
            }
        }
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val candidate = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }

        val uri = try {
            android.net.Uri.parse(candidate)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid web app URL: $rawUrl")
        }
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Unsupported web app URL scheme: $scheme")
        }
        if (uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("Web app URL must include a host: $rawUrl")
        }
        return candidate
    }

    private fun saveWebApps(entries: List<WebAppEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("url", entry.url)
                    put("blockRedirects", entry.blockRedirects)
                }
            )
        }
        sharedPreferences.edit {
            putString(Constants.Prefs.WEB_APPS, jsonArray.toString())
        }
    }
}
