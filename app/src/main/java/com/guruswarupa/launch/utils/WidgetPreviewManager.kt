package com.guruswarupa.launch.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import androidx.collection.LruCache
import com.guruswarupa.launch.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WidgetPreviewManager(private val context: Context) {

    companion object {
        private const val PREVIEW_WIDTH_DP = 200
        private const val PREVIEW_HEIGHT_DP = 200
        private const val CACHE_SIZE = 20
    }

    private val previewCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    fun generatePreview(
        widgetId: String,
        widgetName: String,
        callback: (Bitmap?) -> Unit
    ) {
        val cachedPreview = previewCache.get(widgetId)
        if (cachedPreview != null) {
            callback(cachedPreview)
            return
        }

        backgroundExecutor.execute {
            try {
                val preview = createWidgetPreview(widgetId, widgetName)
                if (preview != null) {
                    previewCache.put(widgetId, preview)
                }

                (context as? android.app.Activity)?.runOnUiThread {
                    callback(preview)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    callback(null)
                }
            }
        }
    }

    private fun createWidgetPreview(widgetId: String, widgetName: String): Bitmap? {
        if (widgetId.startsWith("system_widget_") || widgetId.startsWith("provider_")) {
            return getSystemWidgetPreview(widgetId)
        }

        return when (widgetId) {
            "calculator_widget_container" -> createCalculatorPreview()
            "compass_widget_container" -> createCompassPreview()
            "calendar_events_widget_container" -> createCalendarPreview()
            "countdown_widget_container" -> createCountdownPreview()
            "physical_activity_widget_container" -> createPhysicalActivityPreview()
            "pressure_widget_container" -> createPressurePreview()
            "temperature_widget_container" -> createTemperaturePreview()
            "weather_forecast_widget_container" -> createWeatherForecastPreview()
            "noise_decibel_widget_container" -> createNoiseDecibelPreview()
            "workout_widget_container" -> createWorkoutPreview()
            "todo_recycler_view" -> createTodoPreview()
            "finance_widget" -> createFinancePreview()
            "weekly_usage_widget" -> createWeeklyUsagePreview()
            "network_stats_widget_container" -> createNetworkStatsPreview()
            "device_info_widget_container" -> createDeviceInfoPreview()
            "year_progress_widget_container" -> createYearProgressPreview()
            "github_contributions_widget_container" -> createGithubContributionsPreview()
            "dns_widget_container" -> createDnsPreview()
            "note_widget_container" -> createNotePreview()
            "battery_health_widget_container" -> createBatteryHealthPreview()
            else -> createDefaultPreview(widgetName)
        }
    }

    private fun getSystemWidgetPreview(widgetId: String): Bitmap? {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val provider = if (widgetId.startsWith("system_widget_")) {
            val appWidgetId = widgetId.removePrefix("system_widget_").toIntOrNull() ?: return null
            appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
        } else {
            val parts = widgetId.split("_")
            if (parts.size >= 3) {
                ComponentName(parts[1], parts[2])
            } else null
        } ?: return null

        val info = appWidgetManager.installedProviders.find { it.provider == provider } ?: return null

        val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)

        return if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else if (drawable != null) {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } else {
            null
        }
    }

    private fun createCalculatorPreview(): Bitmap? = inflateAndCapture(R.layout.widget_calculator_preview)
    private fun createCompassPreview(): Bitmap? = inflateAndCapture(R.layout.widget_compass_preview)
    private fun createCalendarPreview(): Bitmap? = inflateAndCapture(R.layout.widget_calendar_preview)
    private fun createCountdownPreview(): Bitmap? = inflateAndCapture(R.layout.widget_countdown_preview)
    private fun createPhysicalActivityPreview(): Bitmap? = inflateAndCapture(R.layout.widget_physical_activity_preview)
    private fun createPressurePreview(): Bitmap? = inflateAndCapture(R.layout.widget_pressure_preview)
    private fun createTemperaturePreview(): Bitmap? = inflateAndCapture(R.layout.widget_temperature_preview)
    private fun createWeatherForecastPreview(): Bitmap? = inflateAndCapture(R.layout.widget_weather_forecast_preview)
    private fun createNoiseDecibelPreview(): Bitmap? = inflateAndCapture(R.layout.widget_noise_decibel_preview)
    private fun createWorkoutPreview(): Bitmap? = inflateAndCapture(R.layout.widget_workout_preview)
    private fun createTodoPreview(): Bitmap? = inflateAndCapture(R.layout.widget_todo_preview)
    private fun createFinancePreview(): Bitmap? = inflateAndCapture(R.layout.widget_finance_preview)
    private fun createWeeklyUsagePreview(): Bitmap? = inflateAndCapture(R.layout.widget_weekly_usage_preview)
    private fun createNetworkStatsPreview(): Bitmap? = inflateAndCapture(R.layout.widget_network_stats_preview)
    private fun createDeviceInfoPreview(): Bitmap? = inflateAndCapture(R.layout.widget_device_info_preview)
    private fun createYearProgressPreview(): Bitmap? = inflateAndCapture(R.layout.widget_year_progress)
    private fun createGithubContributionsPreview(): Bitmap? = inflateAndCapture(R.layout.widget_github_contributions)
    private fun createDnsPreview(): Bitmap? = inflateAndCapture(R.layout.widget_dns_preview)
    private fun createNotePreview(): Bitmap? = inflateAndCapture(R.layout.widget_note_preview)
    private fun createBatteryHealthPreview(): Bitmap? = inflateAndCapture(R.layout.widget_battery_health_preview)

    private fun inflateAndCapture(layoutResId: Int): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(layoutResId, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createDefaultPreview(widgetName: String): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_default_preview, null)

            val nameView = previewView.findViewById<android.widget.TextView>(R.id.preview_widget_name)
            nameView.text = widgetName

            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun measureAndLayoutView(view: View) {
        val width = dpToPx(PREVIEW_WIDTH_DP)
        val height = dpToPx(PREVIEW_HEIGHT_DP)

        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)

        view.measure(widthMeasureSpec, heightMeasureSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun viewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun clearCache() {
        previewCache.evictAll()
    }

    fun cleanup() {
        backgroundExecutor.shutdown()
    }
}
