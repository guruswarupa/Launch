package com.guruswarupa.launch.core

import android.content.SharedPreferences
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

class SystemBarManager(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val sharedPreferences: SharedPreferences
) {
    fun makeSystemBarsTransparent() {
        updateSystemBars(false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let { controller ->
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun updateSystemBars(isFullyTransparent: Boolean) {
        val scrimColor = if (isFullyTransparent) Color.TRANSPARENT else currentTranslucencyScrimColor()
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrimColor),
            navigationBarStyle = SystemBarStyle.dark(scrimColor)
        )
    }

    private fun currentTranslucencyScrimColor(): Int {
        val translucency = sharedPreferences.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(activity, R.attr.appScrim)
        return Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
    }

    fun removeBlurEffect() {
    }
}
