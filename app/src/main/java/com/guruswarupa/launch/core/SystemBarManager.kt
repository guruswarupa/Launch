package com.guruswarupa.launch.core

import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class SystemBarManager(private val activity: androidx.fragment.app.FragmentActivity) {
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
        val scrimColor = if (isFullyTransparent) Color.TRANSPARENT else Color.argb(0x66, 0, 0, 0)
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrimColor),
            navigationBarStyle = SystemBarStyle.dark(scrimColor)
        )
    }

    fun removeBlurEffect() {
    }
}
