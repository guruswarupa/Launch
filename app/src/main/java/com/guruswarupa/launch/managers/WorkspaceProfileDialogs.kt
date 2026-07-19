package com.guruswarupa.launch.managers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.WorkspaceConfigActivity
import com.guruswarupa.launch.utils.DialogStyler

class WorkspaceProfileDialogs(
    private val context: Context,
    private val activity: MainActivity,
    private val workspaceManager: WorkspaceManager,
    private val workProfileManager: WorkProfileManager,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun updateWorkspaceIcon()
        fun updateWorkProfileIcon()
        fun updateDockVisibility()
        fun refreshAppsForWorkspace()
        fun scrollToTop()
        fun showWorkspaceSettings()
    }

    fun showWorkspaceSelector() {
        val workspaces = workspaceManager.getAllWorkspaces()
        if (workspaces.isEmpty()) {
            Toast.makeText(context, "No workspaces available. create one.", Toast.LENGTH_SHORT).show()
            callbacks.showWorkspaceSettings()
            return
        }

        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        val workspaceNames = workspaces.map { it.name }.toMutableList()
        if (isWorkspaceActive) workspaceNames.add("Turn Off")

        val itemsArray = workspaceNames.toTypedArray()
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(if (isWorkspaceActive) "Switch Workspace" else "Select Workspace")
            .setItems(itemsArray) { _, which ->
                if (isWorkspaceActive && which == itemsArray.size - 1) {
                    workspaceManager.setActiveWorkspaceId(null)
                } else {
                    val selectedWorkspace = workspaces[which]
                    workspaceManager.setActiveWorkspaceId(selectedWorkspace.id)
                }
                callbacks.updateWorkspaceIcon()
                callbacks.refreshAppsForWorkspace()
                callbacks.scrollToTop()
            }
            .setNegativeButton("Cancel", null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    fun showWorkProfileManagementDialog() {
        if (!workProfileManager.hasActualWorkProfile()) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle("Work Profile")
                .setMessage("Create a work profile to separate your work apps from personal apps. Work profiles keep your data isolated and secure.")
                .setPositiveButton("Create Work Profile") { _, _ ->
                    startWorkProfileCreation()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            showManageWorkProfileDialog()
        }
    }

    private fun showManageWorkProfileDialog() {
        val options = arrayOf("Open Work Profile Settings")

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Work Profile")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWorkProfileSettings()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun startWorkProfileCreation() {
        if (!workProfileManager.isWorkProfileSupported()) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle("Not Supported")
                .setMessage("Work profiles are not supported on this device. This feature requires Android 9.0 (Pie) or higher and device support for managed profiles.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                workProfileManager.createWorkProfile(activity)
            } else {
                Toast.makeText(context, "Work profiles require Android 9.0 or higher", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create work profile: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openWorkProfileSettings() {
        val packageManager = context.packageManager
        val intents = listOf(
            Intent("android.settings.MANAGED_PROFILE_SETTINGS"),
            Intent("android.settings.SYNC_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        ).map { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setPackage("com.android.settings")
            intent
        }

        val targetIntent = intents.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        }

        if (targetIntent != null) {
            try {
                context.startActivity(targetIntent)
                return
            } catch (e: Exception) {
                Log.e("AppDockManager", "Failed to open work profile settings", e)
            }
        }

        Toast.makeText(context, "Unable to open work profile settings", Toast.LENGTH_SHORT).show()
    }

    fun showCreateWorkProfileDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Create Work Profile")
            .setMessage("No work profile was found. Create one to keep work apps separate from your personal apps.")
            .setPositiveButton("Create") { _, _ ->
                startWorkProfileCreation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
