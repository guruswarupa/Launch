package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AppFolder(
    val id: String,
    var name: String,
    val appKeys: MutableList<String>
)

/**
 * Persists named folders of apps for the stock layout, under a caller-supplied SharedPreferences
 * key (home page vs drawer folders are independent). An app is referenced by its
 * "packageName|activityName" key, matching [AppOrderManager]'s key format.
 */
@Singleton
class FolderManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun getFolders(prefKey: String): List<AppFolder> {
        val json = sharedPreferences.getString(prefKey, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = obj.optString("name", "")
                val apps = obj.optJSONArray("apps")
                val appKeys = mutableListOf<String>()
                if (apps != null) {
                    for (j in 0 until apps.length()) appKeys.add(apps.getString(j))
                }
                if (appKeys.isEmpty()) null else AppFolder(id, name, appKeys)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFolders(prefKey: String, folders: List<AppFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            if (folder.appKeys.isNotEmpty()) {
                val obj = JSONObject()
                obj.put("id", folder.id)
                obj.put("name", folder.name)
                obj.put("apps", JSONArray(folder.appKeys))
                array.put(obj)
            }
        }
        sharedPreferences.edit { putString(prefKey, array.toString()) }
    }

    fun createFolder(prefKey: String, name: String, appKeys: List<String>): AppFolder {
        val folders = getFolders(prefKey).toMutableList()
        val folder = AppFolder(id = UUID.randomUUID().toString(), name = name, appKeys = appKeys.distinct().toMutableList())
        folders.add(folder)
        saveFolders(prefKey, folders)
        return folder
    }

    fun renameFolder(prefKey: String, folderId: String, newName: String) {
        val folders = getFolders(prefKey)
        val updated = folders.map { if (it.id == folderId) it.copy(name = newName) else it }
        saveFolders(prefKey, updated)
    }

    fun addAppToFolder(prefKey: String, folderId: String, appKey: String) {
        val folders = getFolders(prefKey)
        val updated = folders.map { folder ->
            if (folder.id == folderId && appKey !in folder.appKeys) {
                folder.copy(appKeys = (folder.appKeys + appKey).toMutableList())
            } else folder
        }
        saveFolders(prefKey, updated)
    }

    /** Removes [appKey] from the folder; the folder is dropped entirely once it has fewer than 2 apps left. */
    fun removeAppFromFolder(prefKey: String, folderId: String, appKey: String) {
        val folders = getFolders(prefKey)
        val updated = folders.mapNotNull { folder ->
            if (folder.id != folderId) return@mapNotNull folder
            val remaining = folder.appKeys.filter { it != appKey }
            if (remaining.size < 2) null else folder.copy(appKeys = remaining.toMutableList())
        }
        saveFolders(prefKey, updated)
    }

    fun deleteFolder(prefKey: String, folderId: String) {
        val folders = getFolders(prefKey).filter { it.id != folderId }
        saveFolders(prefKey, folders)
    }

    fun findFolder(prefKey: String, folderId: String): AppFolder? =
        getFolders(prefKey).find { it.id == folderId }
}
