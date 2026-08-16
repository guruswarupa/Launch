package com.guruswarupa.launch.handlers

import com.guruswarupa.launch.R
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.core.PermissionManager





class VoiceSearchManager(
    private val activity: FragmentActivity,
    private val packageManager: android.content.pm.PackageManager
) {
    fun startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PermissionManager.VOICE_SEARCH_REQUEST
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, PermissionManager.VOICE_SEARCH_REQUEST)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, activity.getString(R.string.toast_voice_recognition_not_supported_on_this_device), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, activity.getString(R.string.toast_voice_recognition_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceSearchWithLauncher(launcher: ActivityResultLauncher<Intent>) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PermissionManager.VOICE_SEARCH_REQUEST
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                launcher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, activity.getString(R.string.toast_voice_recognition_not_supported_on_this_device), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, activity.getString(R.string.toast_voice_recognition_not_available), Toast.LENGTH_SHORT).show()
        }
    }





    fun triggerSystemAssistant() {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(activity, activity.getString(R.string.toast_could_not_launch_system_assistant), Toast.LENGTH_SHORT).show()
            }
        } else {

            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (searchIntent.resolveActivity(packageManager) != null) {
                activity.startActivity(searchIntent)
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_no_voice_assistant_found_on_this_device), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
