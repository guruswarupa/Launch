package com.guruswarupa.launch.widgets

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.guruswarupa.launch.managers.WidgetConfigurationManager

class WidgetVisibilityManager(
    private val activity: Activity,
    private val widgetConfigurationManager: WidgetConfigurationManager
) {
    companion object {
        private const val TAG = "WidgetVisibilityManager"
    }

    private val widgetViewCache = mutableMapOf<String, View>()

    fun clearCache() {
        widgetViewCache.clear()
    }

    fun update(
        yearProgressWidget: YearProgressWidget? = null,
        githubContributionWidget: GithubContributionWidget? = null
    ) {

        widgetConfigurationManager.forceRefresh()

        val widgets = widgetConfigurationManager.getWidgetOrder()
        val widgetMap = widgets.associateBy { it.id }

        val hasEnabledWidgets = widgets.any { it.enabled }
        val emptyState = activity.findViewById<View>(com.guruswarupa.launch.R.id.widgets_empty_state)
        emptyState?.visibility = if (hasEnabledWidgets) View.GONE else View.VISIBLE


        val failedWidgets = mutableListOf<String>()

        failedWidgets.addAll(updateSimpleWidgetContainers(widgetMap))

        yearProgressWidget?.setGlobalVisibility(widgetMap["year_progress_widget_container"]?.enabled == true)
        githubContributionWidget?.setGlobalVisibility(widgetMap["github_contributions_widget_container"]?.enabled == true)

        failedWidgets.addAll(reorderWidgetsInLayout(widgets, widgetMap))

        if (failedWidgets.isNotEmpty()) {
            Log.w(TAG, "Failed to update visibility for widgets: ${failedWidgets.joinToString()}. Scheduling retry.")
            scheduleRetry(yearProgressWidget, githubContributionWidget)
        }
    }

    private fun updateSimpleWidgetContainers(widgetMap: Map<String, WidgetConfigurationManager.WidgetInfo>): List<String> {
        val failedWidgets = mutableListOf<String>()
        val simpleWidgets = listOf(
            "media_controller_widget_container" to com.guruswarupa.launch.R.id.media_controller_widget_container,
            "calendar_events_widget_container" to com.guruswarupa.launch.R.id.calendar_events_widget_container,
            "countdown_widget_container" to com.guruswarupa.launch.R.id.countdown_widget_container,
            "dns_widget_container" to com.guruswarupa.launch.R.id.dns_widget_container,
            "note_widget_container" to com.guruswarupa.launch.R.id.note_widget_container,
            "battery_health_widget_container" to com.guruswarupa.launch.R.id.battery_health_widget_container,
            "physical_activity_widget_container" to com.guruswarupa.launch.R.id.physical_activity_widget_container,
            "compass_widget_container" to com.guruswarupa.launch.R.id.compass_widget_container,
            "pressure_widget_container" to com.guruswarupa.launch.R.id.pressure_widget_container,
            "temperature_widget_container" to com.guruswarupa.launch.R.id.temperature_widget_container,
            "weather_forecast_widget_container" to com.guruswarupa.launch.R.id.weather_forecast_widget_container,
            "noise_decibel_widget_container" to com.guruswarupa.launch.R.id.noise_decibel_widget_container,
            "finance_widget" to com.guruswarupa.launch.R.id.finance_widget,
            "network_stats_widget_container" to com.guruswarupa.launch.R.id.network_stats_widget_container,
            "device_info_widget_container" to com.guruswarupa.launch.R.id.device_info_widget_container,
            "weekly_usage_widget" to com.guruswarupa.launch.R.id.weekly_usage_widget,
            "github_contributions_widget_container" to com.guruswarupa.launch.R.id.github_contributions_widget_container,
            "habit_tracker_widget_container" to com.guruswarupa.launch.R.id.habit_tracker_widget_container
        )

        simpleWidgets.forEach { (widgetId, _) ->
            val view = getWidgetViewById(widgetId)
            if (view != null) {
                view.visibility = if (widgetMap[widgetId]?.enabled == true) View.VISIBLE else View.GONE
            } else if (widgetMap.containsKey(widgetId)) {
                Log.w(TAG, "Widget container not found: $widgetId")
                failedWidgets.add(widgetId)
            }
        }


        listOf(
            "workout_widget_container",
            "calculator_widget_container",
            "todo_recycler_view"
        ).forEach { widgetId ->
            val view = getWidgetViewById(widgetId)
            if (view != null) {
                view.visibility = if (widgetMap[widgetId]?.enabled == true) View.VISIBLE else View.GONE
            } else if (widgetMap.containsKey(widgetId)) {
                Log.w(TAG, "Widget view not found: $widgetId")
                failedWidgets.add(widgetId)
            }
        }

        return failedWidgets
    }

    private fun reorderWidgetsInLayout(
        widgets: List<WidgetConfigurationManager.WidgetInfo>,
        widgetMap: Map<String, WidgetConfigurationManager.WidgetInfo>
    ): List<String> {
        val failedWidgets = mutableListOf<String>()
        val contentLayout = activity.findViewById<LinearLayout>(com.guruswarupa.launch.R.id.drawer_content_layout)
        contentLayout?.let { layout ->
            // Pre-cache: find any system widgets currently in layout before we clear it
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                val tag = child.tag
                if (tag is Int) {
                    widgetViewCache["system_widget_$tag"] = child
                }
            }

            // Collect all widget views first
            val viewMap = mutableMapOf<String, View>()
            widgets.forEach { widget ->
                val view = if (widget.isSystemWidget) {
                    widgetViewCache[widget.id] ?: run {
                        val widgetIdNum = widget.id.removePrefix("system_widget_").toIntOrNull()
                        if (widgetIdNum != null) layout.findViewWithTag<View>(widgetIdNum) else null
                    }
                } else {
                    getWidgetViewById(widget.id)
                }

                if (view != null) {
                    viewMap[widget.id] = view
                    widgetViewCache[widget.id] = view
                } else if (!widget.isSystemWidget) {
                    Log.w(TAG, "Widget view not found for reordering: ${widget.id}")
                    failedWidgets.add(widget.id)
                }
            }

            // Identify non-widget views (like empty state, spacers, etc.)
            val nonWidgetViews = mutableListOf<View>()
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (!viewMap.values.contains(child)) {
                    nonWidgetViews.add(child)
                }
            }

            // Temporarily disable layout transitions to prevent visual glitches
            val animateLayoutChanges = layout.layoutTransition
            layout.layoutTransition = null

            // Remove all views
            layout.removeAllViews()

            // Add non-widget views first (empty state, etc.)
            val hasEnabledWidgets = widgets.any { it.enabled }
            nonWidgetViews.forEach { 
                // Don't add empty state if we have enabled widgets
                val isEmptyState = it.id == com.guruswarupa.launch.R.id.widgets_empty_state
                if (!(isEmptyState && hasEnabledWidgets)) {
                    layout.addView(it) 
                }
            }

            // Add widget views in order, only if enabled
            widgets.forEach { widget ->
                viewMap[widget.id]?.let { view ->
                    // Only add to layout if enabled
                    if (widget.enabled) {
                        view.visibility = View.VISIBLE
                        if (view.parent == null) {
                            layout.addView(view)
                        }
                    } else {
                        // Remove from parent if disabled to prevent empty backgrounds
                        view.visibility = View.GONE
                        if (view.parent != null) {
                            (view.parent as? ViewGroup)?.removeView(view)
                        }
                    }
                }
            }

            // Restore layout transition
            layout.layoutTransition = animateLayoutChanges
        } ?: run {
            Log.e(TAG, "drawer_content_layout not found!")
        }

        return failedWidgets
    }

    private fun getWidgetViewById(widgetId: String): View? {
        val contentLayout = activity.findViewById<LinearLayout>(com.guruswarupa.launch.R.id.drawer_content_layout) ?: return null

        val view = when (widgetId) {
            "media_controller_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.media_controller_widget_container)
            "calendar_events_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.calendar_events_widget_container)
            "countdown_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.countdown_widget_container)
            "dns_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.dns_widget_container)
            "note_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.note_widget_container)
            "battery_health_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.battery_health_widget_container)
            "physical_activity_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.physical_activity_widget_container)
            "compass_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.compass_widget_container)
            "pressure_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.pressure_widget_container)
            "temperature_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.temperature_widget_container)
            "weather_forecast_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.weather_forecast_widget_container)
            "noise_decibel_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.noise_decibel_widget_container)
            "workout_widget_container" -> activity.findViewById<View>(com.guruswarupa.launch.R.id.workout_widget_main_container)
            "calculator_widget_container" -> activity.findViewById<View>(com.guruswarupa.launch.R.id.calculator_widget_main_container)
            "todo_recycler_view" -> activity.findViewById<View>(com.guruswarupa.launch.R.id.todo_widget_main_container)
            "finance_widget" -> activity.findViewById(com.guruswarupa.launch.R.id.finance_widget)
            "weekly_usage_widget" -> activity.findViewById(com.guruswarupa.launch.R.id.weekly_usage_widget)
            "github_contributions_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.github_contributions_widget_container)
            "network_stats_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.network_stats_widget_container)
            "device_info_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.device_info_widget_container)
            "year_progress_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.year_progress_widget_container)
            "habit_tracker_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.habit_tracker_widget_container)
            else -> {
                Log.w(TAG, "Unknown widget ID: $widgetId")
                null
            }
        }

        // If the view is already a direct child, return it
        if (view != null && view.parent == contentLayout) {
            return view
        }

        // If the view is not a direct child but we found it, search up the tree
        if (view != null) {
            var current: View? = view
            while (current?.parent != null) {
                if (current.parent == contentLayout) {
                    return current
                }
                current = current.parent as? View
            }
        }

        // If not found in hierarchy, check our cache (might be detached)
        return widgetViewCache[widgetId]
    }

    private fun scheduleRetry(
        yearProgressWidget: YearProgressWidget? = null,
        githubContributionWidget: GithubContributionWidget? = null
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                update(yearProgressWidget, githubContributionWidget)
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed: ${e.message}", e)
            }
        }, 500)
    }
}
