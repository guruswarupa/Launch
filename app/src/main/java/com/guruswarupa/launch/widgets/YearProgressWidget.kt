package com.guruswarupa.launch.widgets

import android.content.Context
import com.guruswarupa.launch.R

class YearProgressWidget(
    private val context: Context,
    private val container: android.widget.LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) : InitializableWidget {

    private lateinit var yearProgressView: YearProgressView
    private lateinit var progressStatsText: android.widget.TextView
    private lateinit var daysRemainingText: android.widget.TextView
    private lateinit var yearProgressContainer: android.widget.LinearLayout
    private lateinit var widgetView: android.view.View

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isInitialized = false

    override fun initialize() {
        if (isInitialized) return


        val inflater = android.view.LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_year_progress, container, false)
        container.addView(widgetView)


        yearProgressView = widgetView.findViewById(R.id.year_progress_view)
        progressStatsText = widgetView.findViewById(R.id.progress_stats_text)
        daysRemainingText = widgetView.findViewById(R.id.days_remaining_text)
        yearProgressContainer = widgetView.findViewById(R.id.year_progress_container)


        updateProgressInfo()


        startPeriodicUpdates()

        isInitialized = true
    }

    private fun updateProgressInfo() {
        val progressInfo = yearProgressView.getProgressInfo()
        progressStatsText.text = context.getString(
            R.string.year_progress_stats_format,
            progressInfo.daysCompleted,
            progressInfo.totalDays,
            progressInfo.percentage
        )
        daysRemainingText.text = context.getString(
            R.string.year_progress_days_remaining_format,
            progressInfo.daysRemaining
        )
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                yearProgressView.refresh()
                updateProgressInfo()
            }
            handler.postDelayed(this, 3600000)
        }
    }

    private fun startPeriodicUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    fun onResume() {
        if (isInitialized) {
            yearProgressView.refresh()
            updateProgressInfo()
        }
    }

    fun onPause() {
        handler.removeCallbacks(updateRunnable)
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }

    fun setGlobalVisibility(visible: Boolean) {
        if (visible) {
            widgetView.visibility = android.view.View.VISIBLE

            yearProgressContainer.visibility = android.view.View.VISIBLE
            yearProgressView.refresh()
            updateProgressInfo()
        } else {
            widgetView.visibility = android.view.View.GONE
        }
    }
}