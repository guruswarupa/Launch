package com.guruswarupa.launch.managers

import android.app.ActivityManager
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.guruswarupa.launch.managers.AppUsageStatsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.RejectedExecutionException

class AppTimerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_timer_prefs", Context.MODE_PRIVATE)
    private val dailyUsagePrefs: SharedPreferences = context.getSharedPreferences("daily_usage_prefs", Context.MODE_PRIVATE)
    private var currentTimer: CountDownTimer? = null
    private var currentPackageName: String? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentDialog: AlertDialog? = null
    private val usageStatsManager = AppUsageStatsManager(context)

    companion object {
        const val TIMER_1_MIN = 60000L
        const val TIMER_5_MIN = 300000L
        const val TIMER_10_MIN = 600000L
        const val NO_TIMER = 0L

        const val PREF_DAILY_LIMIT_PREFIX = "daily_limit_"
        const val PREF_SESSION_TIMER_ENABLED_PREFIX = "session_timer_enabled_"
        private const val PREF_TIMER_ENABLED_PREFIX = "timer_enabled_"
    }

    fun isTimerEnabled(packageName: String): Boolean {
        return dailyUsagePrefs.getBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", false)
    }

    fun setTimerEnabled(packageName: String, enabled: Boolean) {
        dailyUsagePrefs.edit { putBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", enabled) }
    }

    fun getAppsWithTimers(): Set<String> {
        val apps = mutableSetOf<String>()
        val allPrefs = dailyUsagePrefs.all
        for (key in allPrefs.keys) {
            if (key.startsWith(PREF_TIMER_ENABLED_PREFIX) && dailyUsagePrefs.getBoolean(key, false)) {
                val packageName = key.removePrefix(PREF_TIMER_ENABLED_PREFIX)
                apps.add(packageName)
            }
        }
        return apps
    }

    fun getTodayUsageMap(): Map<String, Long> {
        return usageStatsManager.getUsageMapForToday()
    }

    private fun runOnBackgroundThread(task: () -> Unit) {
        if (backgroundExecutor.isShutdown || backgroundExecutor.isTerminated) {
            return
        }

        try {
            backgroundExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
        }
    }

    fun isAppOverDailyLimit(packageName: String): Boolean {
        val limit = getDailyLimit(packageName)
        if (limit == NO_TIMER) return false

        val usage = usageStatsManager.getAppUsageTime(packageName)
        return usage >= limit
    }

    fun getDailyLimit(packageName: String): Long {
        return prefs.getLong(PREF_DAILY_LIMIT_PREFIX + packageName, NO_TIMER)
    }

    fun setDailyLimit(packageName: String, limit: Long) {
        prefs.edit { putLong(PREF_DAILY_LIMIT_PREFIX + packageName, limit) }
        setTimerEnabled(packageName, limit > NO_TIMER)
        AppUsageMonitor.syncMonitoring(context)
        usageStatsManager.invalidateCache()
    }

    fun isSessionTimerEnabled(packageName: String): Boolean {
        return prefs.getBoolean(PREF_SESSION_TIMER_ENABLED_PREFIX + packageName, false)
    }

    fun setSessionTimerEnabled(packageName: String, enabled: Boolean) {
        prefs.edit { putBoolean(PREF_SESSION_TIMER_ENABLED_PREFIX + packageName, enabled) }
    }

    fun applyGrayscaleIfOverLimit(packageName: String, imageView: ImageView) {
        if (isAppOverDailyLimit(packageName)) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(matrix)
            imageView.colorFilter = filter
            imageView.alpha = 0.5f
        } else {

            imageView.colorFilter = null
            imageView.clearColorFilter()
            imageView.alpha = 1.0f

            imageView.drawable?.clearColorFilter()
        }
    }

    fun showDailyLimitDialog(appName: String, packageName: String, onLimitSet: () -> Unit) {
        if (currentDialog?.isShowing == true) {
            return
        }

        val options = arrayOf("15 minutes", "30 minutes", "1 hour", "2 hours", "Custom", "No limit")
        val currentLimit = getDailyLimit(packageName)
        val currentLimitStr = if (currentLimit > 0) " (Current: ${usageStatsManager.formatUsageTime(currentLimit)})" else ""

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Daily limit for $appName$currentLimitStr")
            .setItems(options) { _, which ->
                currentDialog = null
                when (which) {
                    0 -> { setDailyLimit(packageName, 15 * 60000L); onLimitSet() }
                    1 -> { setDailyLimit(packageName, 30 * 60000L); onLimitSet() }
                    2 -> { setDailyLimit(packageName, 60 * 60000L); onLimitSet() }
                    3 -> { setDailyLimit(packageName, 120 * 60000L); onLimitSet() }
                    4 -> showCustomDailyLimitDialog(packageName, onLimitSet)
                    5 -> { setDailyLimit(packageName, NO_TIMER); onLimitSet() }
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                currentDialog = null
                d.dismiss()
            }
            .setOnDismissListener {
                currentDialog = null
            }
            .show()

        currentDialog = dialog
        fixDialogItemsTextColor(dialog)
    }

    private fun showCustomDailyLimitDialog(packageName: String, onLimitSet: () -> Unit) {
        val input = EditText(context)
        input.hint = "Enter minutes"
        DialogStyler.styleInput(context, input)

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Daily Limit")
            .setMessage("Enter time in minutes:")
            .setDialogInputView(context, input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutesString = input.text.toString()
                    if (minutesString.isNotEmpty()) {
                        val minutes = minutesString.toInt()
                        if (minutes > 0) {
                            setDailyLimit(packageName, minutes * 60000L)
                            onLimitSet()
                        } else {
                            Toast.makeText(context, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun showTimerDialog(appName: String, onTimerSet: (Long) -> Unit) {

        if (currentDialog?.isShowing == true) {
            return
        }

        val options = arrayOf("1 minute", "5 minutes", "10 minutes", "Custom", "No timer")

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set timer for $appName")
            .setItems(options) { _, which ->
                currentDialog = null
                when (which) {
                    0 -> onTimerSet(TIMER_1_MIN)
                    1 -> onTimerSet(TIMER_5_MIN)
                    2 -> onTimerSet(TIMER_10_MIN)
                    3 -> showCustomTimerDialog(onTimerSet)
                    4 -> onTimerSet(NO_TIMER)
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                currentDialog = null
                d.dismiss()
            }
            .setOnDismissListener {
                currentDialog = null
            }
            .show()

        currentDialog = dialog


        fixDialogItemsTextColor(dialog)
    }

    private fun fixDialogItemsTextColor(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            if (listView != null) {

                listView.post {
                    try {

                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(textColor)
                            } else if (itemView is ViewGroup) {

                                findTextViewsAndSetColor(itemView, textColor)
                            }
                        }
                    } catch (_: Exception) {

                    }
                }


                listView.doOnLayout {
                    try {
                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(textColor)
                            } else if (itemView is ViewGroup) {

                                findTextViewsAndSetColor(itemView, textColor)
                            }
                        }
                    } catch (_: Exception) {

                    }
                }
            }
        } catch (_: Exception) {

        }
    }

    private fun findTextViewsAndSetColor(viewGroup: ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(color)
            } else if (child is ViewGroup) {
                findTextViewsAndSetColor(child, color)
            }
        }
    }

    private fun showCustomTimerDialog(onTimerSet: (Long) -> Unit) {
        val input = EditText(context)
        input.hint = "Enter minutes"
        DialogStyler.styleInput(context, input)

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Timer")
            .setMessage("Enter time in minutes:")
            .setDialogInputView(context, input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutesString = input.text.toString()
                    if (minutesString.isNotEmpty()) {
                        val minutes = minutesString.toInt()
                        if (minutes > 0) {
                            onTimerSet(minutes * 60000L)
                        } else {
                            Toast.makeText(context, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun startTimer(packageName: String, duration: Long) {
        if (duration == NO_TIMER) {

            launchApp(packageName)
            return
        }

        currentPackageName = packageName
        prefs.edit { putLong("timer_remaining_$packageName", duration) }

        currentTimer?.cancel()
        currentTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {

                runOnBackgroundThread {
                    prefs.edit { putLong("timer_remaining_$packageName", millisUntilFinished) }
                }
            }

            override fun onFinish() {

                runOnBackgroundThread {
                    prefs.edit { remove("timer_remaining_$packageName") }
                }


                mainHandler.post {
                    Toast.makeText(context, "Time's up! Closing app and returning to launcher", Toast.LENGTH_SHORT).show()
                    returnToLauncher(packageName)
                    currentTimer = null
                    currentPackageName = null
                }
            }
        }.start()


        launchApp(packageName)
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    fun returnToLauncher(packageName: String) {
        try {

            val launcherPackageName = context.packageName


            try {
                val launcherIntent = context.packageManager.getLaunchIntentForPackage(launcherPackageName)
                if (launcherIntent != null) {
                    launcherIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                          Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                                          Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                          Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(launcherIntent)


                    CoroutineScope(Dispatchers.Main + NonCancellable).launch {
                        delay(500)
                        forceCloseApp(packageName)
                    }
                    return
                }
            } catch (_: Exception) {

            }


            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                          Intent.FLAG_ACTIVITY_CLEAR_TASK or
                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                          Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)


            CoroutineScope(Dispatchers.Main + NonCancellable).launch {
                delay(500)
                forceCloseApp(packageName)
            }
        } catch (_: Exception) {

            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)


                CoroutineScope(Dispatchers.Main + NonCancellable).launch {
                    delay(500)
                    forceCloseApp(packageName)
                }
            } catch (_: Exception) {

            }
        }
    }

    private fun forceCloseApp(packageName: String) {

        runOnBackgroundThread {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runOnBackgroundThread

                // Best-effort background-process cleanup only. A third-party launcher cannot
                // force-stop a foreground app; the real enforcement is returning the user to
                // the home screen via returnToLauncher().
                activityManager.killBackgroundProcesses(packageName)
            } catch (_: Exception) {
            }
        }
    }

    fun cancelTimer() {
        currentTimer?.cancel()
        currentTimer = null
        currentPackageName?.let { packageName ->

            runOnBackgroundThread {
                prefs.edit { remove("timer_remaining_$packageName") }
            }
        }
        currentPackageName = null
    }

    fun cleanup() {

        currentTimer?.cancel()
        currentTimer = null
        currentDialog?.dismiss()
        currentDialog = null

        if (!backgroundExecutor.isShutdown) {
            backgroundExecutor.shutdown()
            try {
                if (!backgroundExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    backgroundExecutor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                backgroundExecutor.shutdownNow()
            }
        }
    }
}
