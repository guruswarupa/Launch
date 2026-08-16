package com.guruswarupa.launch.widgets

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.ActivityData
import com.guruswarupa.launch.managers.PhysicalActivityManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.PhysicalActivityTrackingService
import com.guruswarupa.launch.ui.views.HourlyStepsChartView
import com.guruswarupa.launch.ui.views.PhysicalActivityCalendarView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class PhysicalActivityWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) : InitializableWidget {
    companion object {
        private const val PREF_PHYSICAL_ACTIVITY_TRACKING_ENABLED = "physical_activity_tracking_enabled"
    }

    private lateinit var activityManager: PhysicalActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private lateinit var stepsText: TextView
    private lateinit var distanceText: TextView
    private lateinit var walkingTimeText: TextView
    private lateinit var permissionButton: Button
    private lateinit var viewToggleButton: Button
    private lateinit var statsViewContainer: LinearLayout
    private lateinit var calendarViewContainer: FrameLayout

    private var isCalendarView = false
    private var calendarView: PhysicalActivityCalendarView? = null
    private lateinit var widgetView: View

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && activityManager.hasActivityRecognitionPermission()) {
                updateDisplay()
            }
            handler.postDelayed(this, 10000)
        }
    }

    override fun initialize() {
        if (isInitialized) return


        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_physical_activity, container, false)
        container.addView(widgetView)


        stepsText = widgetView.findViewById(R.id.steps_text)
        distanceText = widgetView.findViewById(R.id.distance_text)
        walkingTimeText = widgetView.findViewById(R.id.walking_time_text)
        permissionButton = widgetView.findViewById(R.id.request_permission_button)
        viewToggleButton = widgetView.findViewById(R.id.view_toggle_button)
        statsViewContainer = widgetView.findViewById(R.id.stats_view_container)
        calendarViewContainer = widgetView.findViewById(R.id.calendar_view_container)




        activityManager = PhysicalActivityManager(context)
        activityManager.initializeAsync(autoStartTracking = false) {

            if (activityManager.hasActivityRecognitionPermission()) {
                setupWithPermission()
            } else {
                setupWithoutPermission()
            }
        }


        viewToggleButton.setOnClickListener {
            toggleView()
        }

        initializeCalendarView()

        isInitialized = true
    }

    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.physical_activity_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = PhysicalActivityCalendarView(
            calendarViewLayout,
            activityManager
        ) { date, activityData ->
            showDayActivityDetails(date, activityData)
        }
    }

    private fun showDayActivityDetails(date: String, activityData: ActivityData) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        val parsedDate = try {
            dateFormat.parse(date)
        } catch (_: Exception) {
            null
        }

        val displayDate = parsedDate?.let { displayFormat.format(it) } ?: date


        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_hourly_activity, null)


        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val totalStepsText = dialogView.findViewById<TextView>(R.id.total_steps_text)
        val totalDistanceText = dialogView.findViewById<TextView>(R.id.total_distance_text)
        val totalWalkingTimeText = dialogView.findViewById<TextView>(R.id.total_walking_time_text)
        val hourlyChart = dialogView.findViewById<HourlyStepsChartView>(R.id.hourly_chart)
        val hourlyStatsContainer = dialogView.findViewById<LinearLayout>(R.id.hourly_stats_container)


        titleText.text = displayDate


        val df = DecimalFormat("#.##")
        val stepsFormatted = String.format(Locale.getDefault(), "%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)
        totalStepsText.text = stepsFormatted
        totalDistanceText.text = context.getString(R.string.distance_km_format, distanceFormatted)
        totalWalkingTimeText.text = formatWalkingTime(activityData.walkingMinutes)


        val hourlyData = activityManager.getHourlyActivityForDate(date)
        hourlyChart.setHourlyData(hourlyData)


        hourlyStatsContainer.removeAllViews()
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)

        hourlyData.forEach { hourly ->
            if (hourly.steps > 0 || hourly.walkingMinutes > 0) {
                val hourView = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_2,
                    hourlyStatsContainer,
                    false
                )
                val text1 = hourView.findViewById<TextView>(android.R.id.text1)
                val text2 = hourView.findViewById<TextView>(android.R.id.text2)

                val hourLabel = when {
                    hourly.hour == 0 -> "12:00 AM"
                    hourly.hour < 12 -> "${hourly.hour}:00 AM"
                    hourly.hour == 12 -> "12:00 PM"
                    else -> "${hourly.hour - 12}:00 PM"
                }

                text1.text = hourLabel
                text1.setTextColor(textColor)
                text1.textSize = 14f

                val distanceFormattedHourly = df.format(hourly.distanceKm)
                text2.text = context.getString(
                    R.string.steps_distance_time_format,
                    hourly.steps,
                    distanceFormattedHourly,
                    formatWalkingTime(hourly.walkingMinutes)
                )
                text2.setTextColor(secondaryTextColor)
                text2.textSize = 12f

                hourlyStatsContainer.addView(hourView)
            }
        }


        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.support_thank_you_close), null)
            .show()
    }

    private fun toggleView() {
        isCalendarView = !isCalendarView

        if (isCalendarView) {

            statsViewContainer.visibility = View.GONE
            calendarViewContainer.visibility = View.VISIBLE
            viewToggleButton.text = context.getString(R.string.stats)
            calendarView?.refreshData()
        } else {

            statsViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            viewToggleButton.text = context.getString(R.string.calendar)
        }
    }

    private fun setupWithPermission() {
        permissionButton.visibility = View.GONE
        viewToggleButton.visibility = View.VISIBLE


        startTrackingService()

        handler.removeCallbacks(initialDisplayRunnable)
        handler.postDelayed(initialDisplayRunnable, 1000)


        handler.postDelayed(updateRunnable, 5000)
    }

    private val initialDisplayRunnable = Runnable { updateDisplay() }

    private fun startTrackingService() {
        sharedPreferences.edit().putBoolean(PREF_PHYSICAL_ACTIVITY_TRACKING_ENABLED, true).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(context, PhysicalActivityTrackingService::class.java)
            context.startForegroundService(intent)
        } else {
            val intent = Intent(context, PhysicalActivityTrackingService::class.java)
            context.startService(intent)
        }
    }

    private fun stopTrackingService() {
        val intent = Intent(context, PhysicalActivityTrackingService::class.java)
        context.stopService(intent)
    }

    private fun setupWithoutPermission() {
        permissionButton.visibility = View.VISIBLE
        viewToggleButton.visibility = View.GONE

        statsViewContainer.visibility = View.VISIBLE
        calendarViewContainer.visibility = View.GONE


        stepsText.text = context.getString(R.string.zero_steps)
        distanceText.text = context.getString(R.string.zero_distance)
        walkingTimeText.text = context.getString(R.string.zero_walking_time)

        permissionButton.setOnClickListener {
            requestPermission()
        }


        val permissionDenied = sharedPreferences.getBoolean("activity_recognition_permission_denied", false)
        if (permissionDenied) {
            permissionButton.text = context.getString(R.string.enable_in_settings)
            permissionButton.setOnClickListener {
                openSettings()
            }
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle(context.getString(R.string.dlg_physical_activity_permission))
                    .setMessage(context.getString(R.string.dlg_this_permission_allows_the_launcher_to_track_you))
                    .setPositiveButton(context.getString(R.string.dlg_grant_permission)) { _, _ ->

                        if (context is FragmentActivity) {
                            ActivityCompat.requestPermissions(
                                context,
                                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                                Constants.RequestCodes.ACTIVITY_RECOGNITION_PERMISSION
                            )
                        }
                    }
                    .setNegativeButton(context.getString(R.string.cancel_button), null)
                    .show()
            }
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {

        }
    }

    fun onPermissionGranted() {
        setupWithPermission()
    }

    fun updateDisplay() {
        if (!isInitialized) return

        val activityData = activityManager.getTodayActivity()


        val df = DecimalFormat("#.##")
        val stepsFormatted = String.format(Locale.getDefault(), "%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)

        stepsText.text = context.getString(R.string.steps_format, stepsFormatted)
        distanceText.text = context.getString(R.string.distance_km_format, distanceFormatted)
        walkingTimeText.text = formatWalkingTime(activityData.walkingMinutes)


        if (isCalendarView) {
            calendarView?.refreshData()
        }
    }

    fun onResume() {
        if (isInitialized && activityManager.hasActivityRecognitionPermission()) {
            updateDisplay()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
    }

    fun onPause() {
        handler.removeCallbacks(updateRunnable)
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(initialDisplayRunnable)
        handler.removeCallbacksAndMessages(null)
        if (sharedPreferences.getBoolean(PREF_PHYSICAL_ACTIVITY_TRACKING_ENABLED, false)) {
            stopTrackingService()
        }
    }

    private fun formatWalkingTime(totalMinutes: Int): String {
        val safeMinutes = totalMinutes.coerceAtLeast(0)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.walking_time_hours_minutes_format, hours.toString(), minutes.toString())
        } else {
            context.getString(R.string.walking_time_minutes_format, minutes.toString())
        }
    }
}
