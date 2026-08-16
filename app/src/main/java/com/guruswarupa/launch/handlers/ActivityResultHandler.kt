package com.guruswarupa.launch.handlers

import com.guruswarupa.launch.R
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.managers.WorkProfileManager
import com.guruswarupa.launch.utils.VoiceCommandHandler

class ActivityResultHandler(
    private val activity: FragmentActivity,
    private val searchBox: AutoCompleteTextView,
    private var voiceCommandHandler: VoiceCommandHandler?,
    private val shareManager: ShareManager,
    private val widgetManager: WidgetManager,
    private val wallpaperManagerHelper: WallpaperManagerHelper?,
    private val onBlockBackGestures: () -> Unit
) {
    fun setVoiceCommandHandler(handler: VoiceCommandHandler?) {
        voiceCommandHandler = handler
    }
    companion object {
        const val WALLPAPER_REQUEST_CODE = 456
        const val REQUEST_PICK_WIDGET = 800
        const val REQUEST_CONFIGURE_WIDGET = 801
        const val REQUEST_WIDGET_CONFIGURATION = 802
        const val REQUEST_BIND_WIDGET = 805
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && resultCode == Activity.RESULT_OK) {
            handleVoiceSearchResult(data)
        } else if (requestCode == ShareManager.FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            shareManager.handleFilePickerResult(data?.data)
        } else if (requestCode == REQUEST_PICK_WIDGET && resultCode == Activity.RESULT_OK) {
            onBlockBackGestures()
            widgetManager.handleWidgetPicked(activity, data)
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET && resultCode == Activity.RESULT_OK) {
            onBlockBackGestures()
            val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            widgetManager.handleWidgetConfigured(appWidgetId?.takeIf { it != -1 })
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            widgetManager.handleWidgetConfigurationCanceled(
                data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    ?.takeIf { it != -1 }
            )
        } else if (requestCode == REQUEST_BIND_WIDGET && resultCode == Activity.RESULT_OK) {
            onBlockBackGestures()
            widgetManager.handleBindRequestResult(activity, approved = true)
        } else if (requestCode == REQUEST_BIND_WIDGET) {
            widgetManager.handleBindRequestResult(activity, approved = false)
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            wallpaperManagerHelper?.let {
                it.clearCache()
                it.setWallpaperBackground(forceReload = true)
            }
        } else if (requestCode == WorkProfileManager.REQUEST_CODE_CREATE_WORK_PROFILE) {
            handleWorkProfileResult(resultCode)
        }
    }

    private fun handleWorkProfileResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(activity, activity.getString(R.string.toast_work_profile_setup_complete), Toast.LENGTH_LONG).show()
            if (activity is MainActivity) {
                val prefs = activity.getSharedPreferences("com.guruswarupa.launch.PREFS", Activity.MODE_PRIVATE)
                prefs.edit().putBoolean("work_profile_enabled", true).apply()
                activity.refreshAppsForWorkspace()

                activity.appDockManager.updateDockIcons()
            }
        } else {
            Toast.makeText(activity, activity.getString(R.string.toast_work_profile_setup_failed_or_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVoiceSearchResult(data: Intent?) {
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        results?.get(0)?.let { result ->
            searchBox.setText(result)
            searchBox.setSelection(result.length)
            voiceCommandHandler?.handleCommand(result)
        }
    }
}
