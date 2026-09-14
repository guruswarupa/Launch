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

/**
 * Persists a user-defined drag-to-reorder app order under a caller-supplied SharedPreferences
 * key, as a JSON array of tokens - either "packageName|activityName" for a standalone app, or
 * "folder:<id>" for an [AppFolder] slot. Used for both the stock home page (favorites order)
 * and the stock app drawer (all-apps order) - each under its own key.
 *
 * Folders are represented in the returned/saved lists as synthetic [ResolveInfo] entries (see
 * [FOLDER_PACKAGE]), the same trick [AppListManager] already uses for separators - so
 * [com.guruswarupa.launch.AppAdapter] never needs a second item type.
 */
@Singleton
class AppOrderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        const val FOLDER_PACKAGE = "com.guruswarupa.launch.FOLDER"
        const val FOLDER_TOKEN_PREFIX = "folder:"
    }

    // Work-profile apps share the exact same packageName/activityName as their personal
    // counterpart (it's literally the same APK, cloned into a different user) - a plain
    // "packageName|activityName" key would collide between the two, so a work app can end up
    // silently merged into - or dropped by - whatever a personal app of the same key was doing
    // in applyOrder()'s used-keys/folder-membership bookkeeping. Personal apps keep the
    // unsuffixed key (preserving every existing saved order/folder), work apps get a distinct
    // one.
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
        // AppAdapter's DiffUtil callback compares preferredOrder to decide whether to rebind an
        // item; folders otherwise look identical (same packageName/name/id) before and after a
        // membership change, so without this the icon preview and the contents dialog would go
        // stale until something else forced a rebind. Real apps use preferredOrder for the user
        // serial number, which folder placeholders never need.
        // Focus mode is folded into the hash too: toggling it doesn't touch folder.appKeys at
        // all (the blocked set lives elsewhere), but it does change which members
        // resolveFolderApps() will actually show - without this, a folder's cached preview
        // composite would keep showing blocked apps' icons until something else forced a rebind.
        val isFocusMode = sharedPreferences.getBoolean("focus_mode_enabled", false)
        ri.preferredOrder = (folder.appKeys.joinToString(",") + "|focus=$isFocusMode").hashCode()
        return ri
    }

    /**
     * Returns [apps] arranged per the saved order under [prefKey], with [folders] collapsed into
     * single synthetic entries. Apps not yet in the saved order (new installs) are appended
     * alphabetically, unless [includeUnlisted] is false - for a slot like the Stock dock, where
     * membership must be entirely explicit (dragged in) and nothing should show up just because
     * it exists. Apps/folders from the saved order that no longer exist (or, for [includeUnlisted]
     * = false, aren't in the saved order at all) are dropped.
     */
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

    /** Persists the given top-level entries (apps and/or folder placeholders) as the new order. */
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

    /** The raw saved token list under [prefKey] (app keys and/or "folder:<id>" tokens), unresolved. */
    fun loadOrderTokens(prefKey: String): List<String> = loadOrder(prefKey)

    /**
     * Drops [keysToRemove] from [prefKey]'s saved order, if present. Used when an app that
     * already has its own standalone slot is folded into a folder from elsewhere (e.g. dragging
     * a drawer folder onto Home that shares a member with something already pinned there) - left
     * alone, that stale token would still be sitting in the saved order, and [applyOrder] would
     * silently swap the folder into that app's old slot the next time it replays the order
     * (see [FOLDER_TOKEN_PREFIX] handling), which reads as the folder shoving the existing app
     * out and taking its exact spot instead of just being added like any other new entry.
     */
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
