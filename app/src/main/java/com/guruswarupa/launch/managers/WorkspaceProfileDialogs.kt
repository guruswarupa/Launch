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
            Toast.makeText(context, context.getString(R.string.toast_no_workspaces_available_create_one), Toast.LENGTH_SHORT).show()
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
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    fun showWorkProfileManagementDialog() {
        if (!workProfileManager.hasActualWorkProfile()) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(context.getString(R.string.dlg_work_profile))
                .setMessage(context.getString(R.string.dlg_create_a_work_profile_to_separate_your_work_apps))
                .setPositiveButton(context.getString(R.string.dlg_create_work_profile)) { _, _ ->
                    startWorkProfileCreation()
                }
                .setNegativeButton(context.getString(R.string.cancel_button), null)
                .show()
        } else {
            showManageWorkProfileDialog()
        }
    }

    private fun showManageWorkProfileDialog() {
        val options = arrayOf("Open Work Profile Settings")

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_work_profile))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWorkProfileSettings()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .show()
    }

    fun startWorkProfileCreation() {
        if (!workProfileManager.isWorkProfileSupported()) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(context.getString(R.string.dlg_not_supported))
                .setMessage(context.getString(R.string.dlg_work_profiles_are_not_supported_on_this_device_t))
                .setPositiveButton(context.getString(R.string.dlg_ok), null)
                .show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                workProfileManager.createWorkProfile(activity)
            } else {
                Toast.makeText(context, context.getString(R.string.toast_work_profiles_require_android_9_0_or_higher), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_failed_to_create_work_profile, e.message), Toast.LENGTH_LONG).show()
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

        Toast.makeText(context, context.getString(R.string.toast_unable_to_open_work_profile_settings), Toast.LENGTH_SHORT).show()
    }

    fun showCreateWorkProfileDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_create_work_profile))
            .setMessage(context.getString(R.string.dlg_no_work_profile_was_found_create_one_to_keep_wor))
            .setPositiveButton(context.getString(R.string.dlg_create)) { _, _ ->
                startWorkProfileCreation()
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .show()
    }
}
