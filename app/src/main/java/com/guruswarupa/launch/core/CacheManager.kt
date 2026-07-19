package com.guruswarupa.launch.core

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import com.guruswarupa.launch.di.BackgroundExecutor
import com.guruswarupa.launch.di.ResourceLoader
import com.guruswarupa.launch.models.AppMetadata
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackgroundExecutor private val backgroundExecutor: ExecutorService,
    @ResourceLoader private val resourceLoader: ExecutorService
) {
    companion object {
        private const val CACHE_DURATION = 300000L
        private val moshi = Moshi.Builder().build()
        private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, AppMetadata::class.java)
    }

    private val appListCacheFile: File = File(context.cacheDir, "app_list_cache.dat")
    private val appListCacheTimeFile: File = File(context.cacheDir, "app_list_cache_time.txt")
    private val appMetadataCacheFile: File = File(context.cacheDir, "app_metadata_cache.dat")
    private val appListVersionFile: File = File(context.cacheDir, "app_list_version.txt")
    private val packageManager: PackageManager = context.packageManager
    
    private val iconCacheDir: File = File(context.cacheDir, "icon_cache").apply { mkdirs() }
    private val iconCacheVersionFile: File = File(iconCacheDir, "icon_cache_version.txt")

    private val appMetadataCache = ConcurrentHashMap<String, AppMetadata>()
    private var cachedAppListVersion: String? = null
    @Volatile
    private var metadataLoaded = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getAppListVersion(): String {
        return try {
            val packages = packageManager.getInstalledPackages(0)

            var maxUpdateTime = 0L
            for (pkg in packages) {
                if (pkg.lastUpdateTime > maxUpdateTime) {
                    maxUpdateTime = pkg.lastUpdateTime
                }
            }
            "${packages.size}_$maxUpdateTime"
        } catch (_: Exception) {
            System.currentTimeMillis().toString()
        }
    }

    fun isVersionCurrent(): Boolean {
        val currentVersion = getAppListVersion()
        val cachedVersion = try {
            if (appListVersionFile.exists()) {
                appListVersionFile.readText().trim()
            } else null
        } catch (_: Exception) {
            null
        }
        return currentVersion == cachedVersion
    }

    fun isCacheValid(): Boolean {
        if (!appListCacheFile.exists()) return false

        val cacheAge = try {
            val cachedTime = appListCacheTimeFile.readText().toLongOrNull() ?: 0L
            System.currentTimeMillis() - cachedTime
        } catch (_: Exception) {
            Long.MAX_VALUE
        }

        return cacheAge < CACHE_DURATION
    }

    fun loadAppListFromCache(): List<ResolveInfo> {
        return try {
            if (!appListCacheFile.exists()) return emptyList()

            val cacheData = appListCacheFile.readText().lines().filter { it.isNotBlank() }
            if (cacheData.isEmpty()) return emptyList()


            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

            val allAppsMap = mutableMapOf<String, ResolveInfo>()

            for (user in launcherApps.profiles) {
                val serial = userManager.getSerialNumberForUser(user).toInt()
                val apps = launcherApps.getActivityList(null, user)
                for (app in apps) {
                    val resolveInfo = ResolveInfo()
                    resolveInfo.activityInfo = android.content.pm.ActivityInfo().apply {
                        packageName = app.componentName.packageName
                        name = app.componentName.className
                        applicationInfo = app.applicationInfo
                    }
                    resolveInfo.preferredOrder = serial

                    val key = "${resolveInfo.activityInfo.packageName}|${resolveInfo.activityInfo.name}|$serial"
                    allAppsMap[key] = resolveInfo
                }
            }

            val apps = mutableListOf<ResolveInfo>()
            for (line in cacheData) {

                val key = if (line.count { it == '|' } == 1) {

                    var found: ResolveInfo? = null
                    val mainUserSerial = userManager.getSerialNumberForUser(android.os.Process.myUserHandle()).toInt()


                    found = allAppsMap["$line|$mainUserSerial"]


                    if (found == null) {
                        found = allAppsMap.values.find { "${it.activityInfo.packageName}|${it.activityInfo.name}" == line }
                    }

                    if (found != null) {
                        "${found.activityInfo.packageName}|${found.activityInfo.name}|${found.preferredOrder}"
                    } else line
                } else line

                allAppsMap[key]?.let { apps.add(it) }
            }

            if (apps.isEmpty() && allAppsMap.isNotEmpty()) {
                return allAppsMap.values.toList()
            }

            apps
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAppListToCache(apps: List<ResolveInfo>) {
        backgroundExecutor.execute {
            try {
                val cacheData = apps.map {
                    "${it.activityInfo.packageName}|${it.activityInfo.name}|${it.preferredOrder}"
                }
                appListCacheFile.writeText(cacheData.joinToString("\n"))
                appListCacheTimeFile.writeText(System.currentTimeMillis().toString())

                val version = getAppListVersion()
                appListVersionFile.writeText(version)
                cachedAppListVersion = version
            } catch (_: Exception) {
            }
        }
    }

    fun loadAppMetadataFromCache(): Map<String, AppMetadata> {
        return try {
            if (!appMetadataCacheFile.exists()) return emptyMap()

            val json = FileInputStream(appMetadataCacheFile).use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            val adapter = moshi.adapter<Map<String, AppMetadata>>(mapType)
            val metadata = adapter.fromJson(json) ?: emptyMap()

            appMetadataCache.clear()
            appMetadataCache.putAll(metadata)
            metadataLoaded = true

            metadata
        } catch (_: Exception) {
            metadataLoaded = true
            emptyMap()
        }
    }

    fun loadAppMetadataFromCacheAsync(onLoaded: (() -> Unit)? = null) {
        if (metadataLoaded) {
            onLoaded?.invoke()
            return
        }
        backgroundExecutor.execute {
            loadAppMetadataFromCache()
            onLoaded?.let { callback ->
                mainHandler.post { callback() }
            }
        }
    }

    fun saveAppMetadataToCache(metadata: Map<String, AppMetadata>) {
        backgroundExecutor.execute {
            try {
                val adapter = moshi.adapter<Map<String, AppMetadata>>(mapType)
                val json = adapter.toJson(metadata)

                FileOutputStream(appMetadataCacheFile).use { outputStream ->
                    outputStream.bufferedWriter().write(json)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun preloadAppMetadata(apps: List<ResolveInfo>) {
        resourceLoader.execute {
            try {
                val metadata = mutableMapOf<String, AppMetadata>()
                val currentTime = System.currentTimeMillis()

                apps.forEach { app ->
                    val packageName = app.activityInfo.packageName
                    val cacheKey = "${packageName}|${app.preferredOrder}"
                    try {
                        val cached = appMetadataCache[cacheKey]
                        if (cached != null && (currentTime - cached.lastUpdated) < CACHE_DURATION) {
                            metadata[cacheKey] = cached
                        } else {
                            val label = app.loadLabel(packageManager).toString()
                            metadata[cacheKey] = AppMetadata(
                                packageName = packageName,
                                activityName = app.activityInfo.name,
                                label = label,
                                lastUpdated = currentTime
                            )
                        }
                    } catch (_: Exception) {
                    }
                }

                appMetadataCache.putAll(metadata)
                saveAppMetadataToCache(appMetadataCache)
            } catch (_: Exception) {
            }
        }
    }

    fun clearCache() {
        try {
            if (appListCacheFile.exists()) appListCacheFile.delete()
            if (appListCacheTimeFile.exists()) appListCacheTimeFile.delete()
            if (appMetadataCacheFile.exists()) appMetadataCacheFile.delete()
            if (appListVersionFile.exists()) appListVersionFile.delete()
            appMetadataCache.clear()
            cachedAppListVersion = null
            clearIconCache()
        } catch (_: Exception) {
        }
    }

    fun getMetadataCache(): Map<String, AppMetadata> = appMetadataCache.toMap()

    fun updateMetadataCache(cacheKey: String, metadata: AppMetadata) {
        appMetadataCache[cacheKey] = metadata
    }

    fun removeMetadata(packageName: String) {
        val prefix = "$packageName|"
        appMetadataCache.keys.removeIf { it == packageName || it.startsWith(prefix) }
    }
    
    fun getIconCacheKey(iconStyle: String, iconSize: Int): String {
        return "${iconStyle}_${iconSize}"
    }
    
    fun getIconCacheVersion(): String {
        return try {
            if (iconCacheVersionFile.exists()) {
                iconCacheVersionFile.readText().trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
    
    fun setIconCacheVersion(version: String) {
        backgroundExecutor.execute {
            try {
                iconCacheVersionFile.writeText(version)
            } catch (_: Exception) {
            }
        }
    }
    
    fun getCachedIcon(cacheKey: String, version: String = ""): android.graphics.drawable.Drawable? {
        return try {
            val iconFile = File(iconCacheDir, "${version}_${cacheKey.replace("/", "_")}.png")
            if (!iconFile.exists()) return null
            
            val bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
            if (bitmap != null) {
                android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    
    fun cacheIcon(cacheKey: String, drawable: android.graphics.drawable.Drawable, version: String = "") {
        backgroundExecutor.execute {
            try {
                val iconFile = File(iconCacheDir, "${version}_${cacheKey.replace("/", "_")}.png")
                
                val bitmap = when (drawable) {
                    is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                    else -> {
                        val width = drawable.intrinsicWidth.coerceAtLeast(1)
                        val height = drawable.intrinsicHeight.coerceAtLeast(1)
                        val tempBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(tempBitmap)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        tempBitmap
                    }
                }
                
                val outputStream = java.io.FileOutputStream(iconFile)
                outputStream.use { os ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                }
            } catch (_: Exception) {
            }
        }
    }
    
    fun clearIconCache() {
        backgroundExecutor.execute {
            try {
                iconCacheDir.listFiles()?.forEach { it.delete() }
                iconCacheVersionFile.delete()
            } catch (_: Exception) {
            }
        }
    }
    
    fun isIconCacheValid(currentIconStyle: String, currentIconSize: Int): Boolean {
        val currentVersion = getIconCacheKey(currentIconStyle, currentIconSize)
        val cachedVersion = getIconCacheVersion()
        return currentVersion == cachedVersion && iconCacheDir.exists() && iconCacheDir.listFiles()?.isNotEmpty() == true
    }
}
