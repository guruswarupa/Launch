package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class Workspace(
    val id: String,
    val name: String,
    val appPackageNames: Set<String>
)

class WorkspaceManager @Inject constructor(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val WORKSPACES_KEY = "workspaces"
        private const val ACTIVE_WORKSPACE_KEY = "active_workspace_id"
    }

    fun createWorkspace(name: String, appPackageNames: Set<String>): Workspace {
        val workspaceId = "workspace_${System.currentTimeMillis()}"
        val workspace = Workspace(workspaceId, name, appPackageNames)
        saveWorkspace(workspace)
        return workspace
    }

    fun updateWorkspace(workspaceId: String, name: String, appPackageNames: Set<String>) {
        val workspace = Workspace(workspaceId, name, appPackageNames)
        saveWorkspace(workspace)
    }

    fun deleteWorkspace(workspaceId: String) {
        val workspaces = getAllWorkspaces().toMutableList()
        workspaces.removeAll { it.id == workspaceId }
        saveAllWorkspaces(workspaces)

        if (getActiveWorkspaceId() == workspaceId) {
            setActiveWorkspaceId(null)
        }
    }

    fun getAllWorkspaces(): List<Workspace> {
        val workspacesJson = sharedPreferences.getString(WORKSPACES_KEY, "[]") ?: "[]"
        val userWorkspaces = try {
            val jsonArray = JSONArray(workspacesJson)
            (0 until jsonArray.length()).mapNotNull { index ->
                val obj = jsonArray.getJSONObject(index)
                Workspace(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    appPackageNames = obj.getJSONArray("apps").let { appsArray ->
                        (0 until appsArray.length()).map { appsArray.getString(it) }.toSet()
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        return userWorkspaces
    }

    fun getWorkspace(workspaceId: String): Workspace? {
        return getAllWorkspaces().find { it.id == workspaceId }
    }

    private fun saveWorkspace(workspace: Workspace) {
        val workspaces = getAllWorkspaces().toMutableList()
        val existingIndex = workspaces.indexOfFirst { it.id == workspace.id }

        if (existingIndex >= 0) {
            workspaces[existingIndex] = workspace
        } else {
            workspaces.add(workspace)
        }

        saveAllWorkspaces(workspaces)
    }

    private fun saveAllWorkspaces(workspaces: List<Workspace>) {
        val jsonArray = JSONArray()
        workspaces.forEach { workspace ->
            val obj = JSONObject().apply {
                put("id", workspace.id)
                put("name", workspace.name)
                val appsArray = JSONArray()
                workspace.appPackageNames.forEach { appsArray.put(it) }
                put("apps", appsArray)
            }
            jsonArray.put(obj)
        }
        sharedPreferences.edit { putString(WORKSPACES_KEY, jsonArray.toString()) }
    }

    fun setActiveWorkspaceId(workspaceId: String?) {
        sharedPreferences.edit {
            if (workspaceId != null) {
                putString(ACTIVE_WORKSPACE_KEY, workspaceId)
            } else {
                remove(ACTIVE_WORKSPACE_KEY)
            }
        }
    }

    fun getActiveWorkspaceId(): String? {
        return sharedPreferences.getString(ACTIVE_WORKSPACE_KEY, null)
    }

    fun getActiveWorkspace(): Workspace? {
        val activeId = getActiveWorkspaceId()
        return if (activeId != null) getWorkspace(activeId) else null
    }

    fun isWorkspaceModeActive(): Boolean {
        return getActiveWorkspaceId() != null
    }

    fun isAppInActiveWorkspace(packageName: String): Boolean {
        val activeWorkspace = getActiveWorkspace()
        return activeWorkspace?.appPackageNames?.contains(packageName) ?: true
    }

    fun shouldShowApp(packageName: String, workProfileManager: WorkProfileManager): Boolean {
        val activeWorkspace = getActiveWorkspace()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()

        return when {

            activeWorkspace != null && !activeWorkspace.appPackageNames.contains(packageName) -> false

            isWorkProfileEnabled -> true

            else -> true
        }
    }

    fun getAppsInWorkspaces(excludeWorkspaceId: String? = null): Set<String> {
        val allWorkspaces = getAllWorkspaces()
        val appsInWorkspaces = mutableSetOf<String>()

        allWorkspaces.forEach { workspace ->
            if (workspace.id != excludeWorkspaceId) {
                appsInWorkspaces.addAll(workspace.appPackageNames)
            }
        }

        return appsInWorkspaces
    }
}
