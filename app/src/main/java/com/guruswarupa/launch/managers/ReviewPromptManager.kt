package com.guruswarupa.launch.managers

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import java.util.concurrent.TimeUnit

class ReviewPromptManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences
) {
    private val reviewPromptIntervalMillis = TimeUnit.DAYS.toMillis(7)
    private val maxPromptCount = 3

    fun recordFirstUseIfNeeded() {
        if (sharedPreferences.getLong(Constants.Prefs.REVIEW_FIRST_USE_AT, 0L) != 0L) {
            return
        }
        val now = System.currentTimeMillis()
        sharedPreferences.edit {
            putLong(Constants.Prefs.REVIEW_FIRST_USE_AT, now)
            putLong(Constants.Prefs.REVIEW_NEXT_PROMPT_AT, now + reviewPromptIntervalMillis)
        }
    }

    fun promptIfEligible(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        if (sharedPreferences.getBoolean(Constants.Prefs.REVIEW_CTA_USED, false)) {
            return false
        }
        val firstUseAt = sharedPreferences.getLong(Constants.Prefs.REVIEW_FIRST_USE_AT, 0L)
        if (firstUseAt == 0L) {
            recordFirstUseIfNeeded()
            return false
        }
        val promptCount = sharedPreferences.getInt(Constants.Prefs.REVIEW_PROMPT_COUNT, 0)
        if (promptCount >= maxPromptCount) {
            return false
        }
        val now = System.currentTimeMillis()
        val nextPromptAt = sharedPreferences.getLong(
            Constants.Prefs.REVIEW_NEXT_PROMPT_AT,
            firstUseAt + reviewPromptIntervalMillis
        )
        if (now < nextPromptAt) {
            return false
        }
        AlertDialog.Builder(activity, com.guruswarupa.launch.R.style.CustomDialogTheme)
            .setTitle(R.string.review_prompt_title)
            .setMessage(R.string.review_prompt_message)
            .setPositiveButton(R.string.review_prompt_positive) { _, _ ->
                sharedPreferences.edit {
                    putBoolean(Constants.Prefs.REVIEW_CTA_USED, true)
                }
                openPlayStoreListing()
            }
            .setNegativeButton(R.string.review_prompt_negative) { _, _ ->
                scheduleNextPrompt()
            }
            .setOnCancelListener {
                scheduleNextPrompt()
            }
            .show()
        return true
    }

    /**
     * Opens this app's Play Store listing directly. Deliberately not using the Play Core
     * In-App Review API here: that API shows a small in-app star-rating card, not the Play
     * Store page, and is quota-limited — it commonly completes "successfully" while silently
     * showing nothing at all, which left this button doing nothing when that happened.
     */
    private fun openPlayStoreListing() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${activity.packageName}")
        )
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")
        )
        val intent = if (marketIntent.resolveActivity(activity.packageManager) != null) {
            marketIntent
        } else {
            webIntent
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.toast_no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleNextPrompt() {
        val now = System.currentTimeMillis()
        val updatedPromptCount = sharedPreferences.getInt(Constants.Prefs.REVIEW_PROMPT_COUNT, 0) + 1
        sharedPreferences.edit {
            putInt(Constants.Prefs.REVIEW_PROMPT_COUNT, updatedPromptCount)
            putLong(Constants.Prefs.REVIEW_NEXT_PROMPT_AT, now + reviewPromptIntervalMillis)
        }
    }
}
