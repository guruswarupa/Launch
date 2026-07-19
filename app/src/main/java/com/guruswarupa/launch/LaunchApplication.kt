package com.guruswarupa.launch

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import dagger.hilt.android.HiltAndroidApp
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class LaunchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {
                if (activity is ComponentActivity) {
                    activity.enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let { controller ->
                        controller.isAppearanceLightStatusBars = false
                        controller.isAppearanceLightNavigationBars = false
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                    activity.window.decorView.post {
                        TypographyManager.applyToActivity(activity)
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                TypographyManager.applyToActivity(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        setupCrashHandler()
        initServices()
    }

    private fun initServices() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)

        val isDimmerEnabled = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        if (isDimmerEnabled && Settings.canDrawOverlays(this)) {
            val dimLevel = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 50)
            startServiceWithDelay(ScreenDimmerService::class.java) {
                ScreenDimmerService.startService(this, dimLevel)
            }
        }

        val isNightModeEnabled = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        if (isNightModeEnabled && Settings.canDrawOverlays(this)) {
            val intensity = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
            startServiceWithDelay(NightModeService::class.java) {
                NightModeService.startService(this, intensity)
            }
        }
    }

    private fun startServiceWithDelay(serviceClass: Class<*>, startAction: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAction()
            }, 500)
        } else {
            startAction()
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                sendCrashReport(throwable)
            } catch (e: Exception) {
                Log.e("LaunchApplication", "Failed to send crash report", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun sendCrashReport(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val deviceInfo = """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}
            App Version: ${getAppVersion()}
        """.trimIndent()

        val emailBody = "The app has crashed with the following exception:\n\n$deviceInfo\n\nStack Trace:\n$stackTrace"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Launch App Crash Report: ${throwable.javaClass.simpleName}")
            putExtra(Intent.EXTRA_TEXT, emailBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(packageManager) != null) {
            val mainLooper = android.os.Looper.getMainLooper()
            if (Looper.myLooper() == mainLooper) {
                startActivity(intent)
            } else {
                android.os.Handler(mainLooper).post {
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            "${pInfo.versionName} ($versionCode)"
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
