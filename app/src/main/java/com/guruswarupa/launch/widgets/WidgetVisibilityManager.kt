package com.guruswarupa.launch.widgets

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.util.Log
import android.animation.LayoutTransition
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.managers.WidgetConfigurationManager

class WidgetVisibilityManager(
    private val activity: Activity,
    private val widgetConfigurationManager: WidgetConfigurationManager
) {
    companion object {
        private const val TAG = "WidgetVisibilityManager"
    }

    private val widgetViewCache = mutableMapOf<String, View>()
    private var originalLayoutTransition: LayoutTransition? = null

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
                        
                        // Enable drag start
                        val dragStartListener = View.OnLongClickListener {
                            val shadow = View.DragShadowBuilder(view)
                            it.startDragAndDrop(null, shadow, widget.id, 0)
                            true
                        }
                        view.setOnLongClickListener(dragStartListener)
                        
                        // If it's a system widget, also set on the host view to override WidgetManager's listener
                        if (widget.isSystemWidget && view is ViewGroup) {
                            for (i in 0 until view.childCount) {
                                val child = view.getChildAt(i)
                                if (child is AppWidgetHostView) {
                                    child.setOnLongClickListener(dragStartListener)
                                }
                            }
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

            // Setup container drag listener
            layout.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        val draggedId = event.localState as? String
                        val draggedView = viewMap[draggedId]
                        draggedView?.visibility = View.INVISIBLE
                        
                        // Restore previous transitions if any, but don't use them during drag 
                        // to avoid conflicts with manual translations
                        originalLayoutTransition = layout.layoutTransition
                        layout.layoutTransition = null
                        true
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> {
                        val draggedId = event.localState as? String
                        if (draggedId != null) {
                            val draggedView = viewMap[draggedId] ?: return@setOnDragListener true
                            val currentIndex = layout.indexOfChild(draggedView)
                            val targetIndex = findTargetIndex(layout, event.y, draggedView)
                            
                            if (targetIndex != -1) {
                                // Visually shift other widgets using translationY instead of reordering
                                // This prevents AppWidgetHostView from flickering due to re-attachment
                                val draggedHeight = draggedView.height.toFloat()
                                
                                for (i in 0 until layout.childCount) {
                                    val child = layout.getChildAt(i)
                                    if (child == draggedView || child.id == com.guruswarupa.launch.R.id.widgets_empty_state) {
                                        if (child.translationY != 0f) child.translationY = 0f
                                        continue
                                    }
                                    
                                    val childIndex = i
                                    val targetTranslation = when {
                                        // If we're dragging down: items between current and target move UP
                                        currentIndex < targetIndex && childIndex > currentIndex && childIndex < targetIndex -> -draggedHeight
                                        // If we're dragging up: items between target and current move DOWN
                                        currentIndex > targetIndex && childIndex >= targetIndex && childIndex < currentIndex -> draggedHeight
                                        else -> 0f
                                    }
                                    
                                    if (child.translationY != targetTranslation) {
                                        child.animate()
                                            .translationY(targetTranslation)
                                            .setDuration(150)
                                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                                            .start()
                                    }
                                }
                            }
                            
                            // Handle auto-scroll
                            val scrollView = layout.parent as? NestedScrollView
                            scrollView?.let { scroll ->
                                val scrollY = scroll.scrollY
                                val viewportHeight = scroll.height
                                val edgeThreshold = 180
                                val scrollAmount = 30
                                
                                if (event.y < scrollY + edgeThreshold) {
                                    scroll.scrollBy(0, -scrollAmount)
                                } else if (event.y > scrollY + viewportHeight - edgeThreshold) {
                                    scroll.scrollBy(0, scrollAmount)
                                }
                            }
                        }
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        val draggedId = event.localState as? String
                        if (draggedId != null) {
                            val draggedView = viewMap[draggedId]
                            if (draggedView != null) {
                                // Reset all translations before final reorder
                                for (i in 0 until layout.childCount) {
                                    layout.getChildAt(i).translationY = 0f
                                }
                                
                                val targetIndex = findTargetIndex(layout, event.y, draggedView)
                                val currentIndex = layout.indexOfChild(draggedView)
                                if (targetIndex != -1 && targetIndex != currentIndex) {
                                    layout.removeView(draggedView)
                                    // Adjust target index because removal might have shifted it
                                    val adjustedTarget = if (targetIndex > currentIndex) targetIndex - 1 else targetIndex
                                    layout.addView(draggedView, adjustedTarget.coerceIn(0, layout.childCount))
                                }
                            }
                            finalizeOrderFromLayout(layout, viewMap)
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        val draggedId = event.localState as? String
                        val view = viewMap[draggedId]
                        view?.visibility = View.VISIBLE
                        
                        // Clean up all translations
                        for (i in 0 until layout.childCount) {
                            layout.getChildAt(i).translationY = 0f
                        }
                        
                        // Restore original transitions
                        layout.layoutTransition = originalLayoutTransition
                        
                        if (event.result) {
                            showResizeHandleIfSystemWidget(view)
                        } else {
                            update()
                        }
                        true
                    }
                    else -> true
                }
            }

            // Restore layout transition
            layout.layoutTransition = animateLayoutChanges
        } ?: run {
            Log.e(TAG, "drawer_content_layout not found!")
        }

        return failedWidgets
    }

    private fun findTargetIndex(layout: LinearLayout, y: Float, draggedView: View): Int {
        val currentIndex = layout.indexOfChild(draggedView)
        val draggedHeight = draggedView.height
        
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child == draggedView || child.id == com.guruswarupa.launch.R.id.widgets_empty_state) continue
            
            // Determine the "virtual" center of the child as if the draggedView wasn't in the layout
            val childTop = child.y
            val virtualChildCenter = if (currentIndex != -1 && currentIndex < i) {
                childTop - draggedHeight + child.height / 2
            } else {
                childTop + child.height / 2
            }
            
            if (y < virtualChildCenter) {
                return i
            }
        }
        return layout.childCount
    }

    private fun finalizeOrderFromLayout(layout: LinearLayout, viewMap: Map<String, View>) {
        val allWidgets = widgetConfigurationManager.getWidgetOrder().toMutableList()
        val enabledWidgetsInOrder = mutableListOf<String>()
        
        // Reverse mapping of views to widget IDs
        val viewToIdMap = viewMap.entries.associate { it.value to it.key }
        
        // Get the new order of enabled widgets from the layout
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            viewToIdMap[child]?.let { enabledWidgetsInOrder.add(it) }
        }
        
        // Rebuild allWidgets list maintaining the new order for enabled ones
        // and keeping disabled ones at the end (or where they were)
        val disabledWidgets = allWidgets.filter { !it.enabled }
        val newOrder = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
        
        enabledWidgetsInOrder.forEach { id ->
            allWidgets.find { it.id == id }?.let { newOrder.add(it) }
        }
        newOrder.addAll(disabledWidgets)
        
        widgetConfigurationManager.saveWidgetOrder(newOrder)

        // Also sync system widgets back to WidgetManager
        (activity as? MainActivity)?.let { main ->
            val systemWidgetIds = enabledWidgetsInOrder
                .filter { it.startsWith("system_widget_") }
                .mapNotNull { it.removePrefix("system_widget_").toIntOrNull() }
            
            if (systemWidgetIds.isNotEmpty()) {
                main.widgetManager.syncWidgetOrder(systemWidgetIds)
            }
        }

        // Final refresh to ensure all caches and state are perfectly in sync
        update()
    }

    private fun showResizeHandleIfSystemWidget(view: View?) {
        if (view is ViewGroup) {
            // Search for the resize handle in the system widget container
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is ImageView && child.contentDescription == "Resize widget") {
                    child.visibility = View.VISIBLE
                    // Auto-hide after 4 seconds to match WidgetManager behavior
                    child.postDelayed({ child.visibility = View.GONE }, 4000L)
                    break
                }
            }
        }
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
