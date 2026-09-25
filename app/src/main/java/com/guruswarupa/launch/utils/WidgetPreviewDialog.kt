package com.guruswarupa.launch.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.R

class WidgetPreviewDialog(
    context: Context,
    private val widgetInfo: WidgetConfigurationManager.WidgetInfo,
    private val previewManager: WidgetPreviewManager,
    private val onEnableClicked: () -> Unit
) : Dialog(context) {

    private lateinit var previewImage: ImageView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var widgetName: TextView
    private lateinit var widgetDescription: TextView
    private lateinit var closeButton: Button
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_widget_preview)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        previewImage = findViewById(R.id.dialog_preview_image)
        loadingProgress = findViewById(R.id.dialog_loading_progress)
        widgetName = findViewById(R.id.dialog_widget_name)
        widgetDescription = findViewById(R.id.dialog_widget_description)
        closeButton = findViewById(R.id.btn_close)
        enableButton = findViewById(R.id.btn_enable)

        widgetName.text = widgetInfo.name
        widgetDescription.text = getWidgetDescription(widgetInfo.id)

        val isEnabled = widgetInfo.enabled
        enableButton.text = if (isEnabled) "Disable" else "Enable"
        enableButton.setBackgroundResource(
            if (isEnabled) R.drawable.dialog_preview_danger_button
            else R.drawable.dialog_preview_primary_button
        )

        loadPreview()

        closeButton.setOnClickListener {
            dismiss()
        }

        enableButton.setOnClickListener {
            onEnableClicked()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.8f).toInt()
        window?.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun loadPreview() {
        loadingProgress.visibility = android.view.View.VISIBLE
        previewImage.setImageDrawable(null)

        previewManager.generatePreview(widgetInfo.id, widgetInfo.name) { bitmap ->
            loadingProgress.visibility = android.view.View.GONE
            if (bitmap != null && !bitmap.isRecycled) {
                previewImage.setImageBitmap(bitmap)
            } else {
                previewImage.setImageResource(R.drawable.ic_widget_placeholder)
            }
        }
    }

    private fun getWidgetDescription(widgetId: String): String {
        return when (widgetId) {
            "calculator_widget_container" -> "Perform calculations and unit conversions"
            "compass_widget_container" -> "Digital compass with direction tracking"
            "calendar_events_widget_container" -> "Upcoming calendar events and reminders"
            "countdown_widget_container" -> "Countdown timers for important events"
            "physical_activity_widget_container" -> "Track steps and physical activity"
            "pressure_widget_container" -> "Atmospheric pressure monitoring"
            "temperature_widget_container" -> "Temperature monitoring and alerts"
            "weather_forecast_widget_container" -> "Multi-day forecasts, hourly breakdowns, and weather alerts"
            "noise_decibel_widget_container" -> "Sound level measurement in decibels"
            "workout_widget_container" -> "Workout tracking and fitness metrics"
            "todo_recycler_view" -> "Task management and to-do lists"
            "finance_widget" -> "Financial tracking and budget monitoring"
            "weekly_usage_widget" -> "Weekly app usage statistics"
            "network_stats_widget_container" -> "Network connection and data usage"
            "device_info_widget_container" -> "Device information and system stats"
            else -> "Additional widget functionality"
        }
    }

    companion object {
        fun show(
            context: Context,
            widgetInfo: WidgetConfigurationManager.WidgetInfo,
            previewManager: WidgetPreviewManager,
            onActionClicked: (Boolean) -> Unit
        ) {
            val dialog = WidgetPreviewDialog(context, widgetInfo, previewManager) {
                onActionClicked(!widgetInfo.enabled)
            }
            dialog.show()
        }
    }
}
