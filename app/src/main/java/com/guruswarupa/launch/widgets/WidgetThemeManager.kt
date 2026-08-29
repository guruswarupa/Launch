package com.guruswarupa.launch.widgets

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.managers.TypographyManager

class WidgetThemeManager(
    private val activity: Activity
) {
    fun apply(
        searchBox: EditText? = null,
        searchContainer: View? = null,
        voiceSearchButton: ImageButton? = null,
        searchTypeButton: ImageButton? = null,
        appDockManager: AppDockManager? = null
    ) {

        val widgetBackground = R.drawable.widget_background
        val emptyStateBackground = R.drawable.drawer_widgets_empty_state_bg


        activity.findViewById<View>(R.id.top_widget_container)?.setBackgroundResource(widgetBackground)


        activity.findViewById<View>(R.id.widget_settings_header)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.widget_config_button)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.widgets_empty_state)?.setBackgroundResource(emptyStateBackground)
        activity.findViewById<View>(R.id.rss_header)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.rss_refresh_button)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.rss_manage_button)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.rss_empty_state)?.setBackgroundResource(emptyStateBackground)

        TypographyManager.applyToActivity(activity)

        searchBox?.let { sb ->
            val searchBg = R.drawable.search_box_transparent_bg
            val textColor = android.graphics.Color.WHITE
            val hintColor = android.graphics.Color.WHITE


            searchContainer?.let { sc ->
                sc.setBackgroundResource(searchBg)
                sb.background = null
            } ?: run {
                sb.setBackgroundResource(searchBg)
            }

            sb.setTextColor(textColor)
            sb.setHintTextColor(hintColor)
            TypographyManager.applyToView(sb)


            val iconColor = android.graphics.Color.WHITE
            sb.compoundDrawablesRelative[0]?.setTint(iconColor)
            voiceSearchButton?.setColorFilter(iconColor)
            searchTypeButton?.setColorFilter(iconColor)
        }


        activity.findViewById<ImageView>(R.id.weather_icon)?.setColorFilter(android.graphics.Color.WHITE)


        appDockManager?.updateDockIcons()
    }




    fun checkAndUpdateThemeIfNeeded(
        todoManager: TodoManager? = null,
        appDockManager: AppDockManager? = null,
        searchBox: EditText? = null,
        searchContainer: View? = null,
        voiceSearchButton: ImageButton? = null,
        searchTypeButton: ImageButton? = null
    ) {

        apply(
            searchBox = searchBox,
            searchContainer = searchContainer,
            voiceSearchButton = voiceSearchButton,
            searchTypeButton = searchTypeButton,
            appDockManager = appDockManager
        )

        todoManager?.onThemeChanged()
    }
}
