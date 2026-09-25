package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.UserManager
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOrderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        const val FOLDER_PACKAGE = "com.guruswarupa.launch.FOLDER"
        const val FOLDER_TOKEN_PREFIX = "folder:"
    }

    private val mainUserSerial = (context.getSystemService(Context.USER_SERVICE) as UserManager)
        .getSerialNumberForUser(Process.myUserHandle()).toInt()

    fun keyOf(app: ResolveInfo): String {
        val base = "${app.activityInfo.packageName}|${app.activityInfo.name}"
        return if (app.preferredOrder != mainUserSerial) "$base|profile${app.preferredOrder}" else base
    }

    fun isFolderEntry(app: ResolveInfo): Boolean = app.activityInfo.packageName == FOLDER_PACKAGE

    fun folderIdOf(app: ResolveInfo): String = app.nonLocalizedLabel?.toString().orEmpty()

    fun createFolderInfo(folder: AppFolder): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = FOLDER_PACKAGE
        ri.activityInfo.name = folder.name
        ri.activityInfo.applicationInfo = ApplicationInfo().apply { packageName = FOLDER_PACKAGE }
        ri.nonLocalizedLabel = folder.id

        val isFocusMode = sharedPreferences.getBoolean("focus_mode_enabled", false)
        ri.preferredOrder = (folder.appKeys.joinToString(",") + "|focus=$isFocusMode").hashCode()
        return ri
    }

    fun applyOrder(
        apps: List<ResolveInfo>,
        appListManager: AppListManager,
        prefKey: String,
        folders: List<AppFolder> = emptyList(),
        includeUnlisted: Boolean = true
    ): List<ResolveInfo> {
        val alphabetical = compareBy<ResolveInfo> {
            appListManager.getSortKey(appListManager.getDisplayLabel(it).lowercase())
        }

        val folderByAppKey = HashMap<String, AppFolder>()
        folders.forEach { folder -> folder.appKeys.forEach { key -> folderByAppKey[key] = folder } }

        val byKey = apps.associateBy { keyOf(it) }
        val ordered = mutableListOf<ResolveInfo>()
        val emittedFolderIds = HashSet<String>()
        val usedAppKeys = HashSet<String>()

        fun emitApp(key: String) {
            val app = byKey[key] ?: return
            if (!usedAppKeys.add(key)) return
            val folder = folderByAppKey[key]
            if (folder != null) {
                if (emittedFolderIds.add(folder.id)) ordered.add(createFolderInfo(folder))
            } else {
                ordered.add(app)
            }
        }

        loadOrder(prefKey).forEach { token ->
            if (token.startsWith(FOLDER_TOKEN_PREFIX)) {
                val folderId = token.removePrefix(FOLDER_TOKEN_PREFIX)
                val folder = folders.find { it.id == folderId } ?: return@forEach
                val hasPresentApp = folder.appKeys.any { it in byKey }
                if (hasPresentApp && emittedFolderIds.add(folder.id)) {
                    ordered.add(createFolderInfo(folder))
                    usedAppKeys.addAll(folder.appKeys)
                }
            } else {
                emitApp(token)
            }
        }

        if (includeUnlisted) {
            apps.filter { keyOf(it) !in usedAppKeys }
                .sortedWith(alphabetical)
                .forEach { emitApp(keyOf(it)) }

            folders.forEach { folder ->
                val hasPresentApp = folder.appKeys.any { it in byKey }
                if (hasPresentApp && emittedFolderIds.add(folder.id)) ordered.add(createFolderInfo(folder))
            }
        }

        return ordered
    }

    fun saveOrder(entries: List<ResolveInfo>, prefKey: String) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            if (isFolderEntry(entry)) {
                jsonArray.put("$FOLDER_TOKEN_PREFIX${folderIdOf(entry)}")
            } else {
                jsonArray.put(keyOf(entry))
            }
        }
        sharedPreferences.edit { putString(prefKey, jsonArray.toString()) }
    }

    fun loadOrderTokens(prefKey: String): List<String> = loadOrder(prefKey)

    fun removeTokens(prefKey: String, keysToRemove: Set<String>) {
        if (keysToRemove.isEmpty()) return
        val remaining = loadOrder(prefKey).filterNot { it in keysToRemove }
        val jsonArray = JSONArray()
        remaining.forEach { jsonArray.put(it) }
        sharedPreferences.edit { putString(prefKey, jsonArray.toString()) }
    }

    private fun loadOrder(prefKey: String): List<String> {
        val json = sharedPreferences.getString(prefKey, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
