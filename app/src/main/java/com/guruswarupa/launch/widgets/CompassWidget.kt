package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.guruswarupa.launch.managers.CompassManager
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.views.CompassView

class CompassWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) : InitializableWidget {

    private lateinit var compassManager: CompassManager
    private lateinit var compassView: CompassView
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private lateinit var directionText: TextView
    private lateinit var azimuthText: TextView
    private lateinit var noSensorText: TextView
    private lateinit var toggleButton: Button
    private lateinit var compassContainer: LinearLayout
    private lateinit var widgetView: View

    companion object {
        private const val PREF_COMPASS_ENABLED = "compass_enabled"
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && compassManager.hasRequiredSensors()) {
                updateDisplay()
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_compass, container, false)
        container.addView(widgetView)

        directionText = widgetView.findViewById(R.id.direction_text)
        azimuthText = widgetView.findViewById(R.id.azimuth_text)
        noSensorText = widgetView.findViewById(R.id.no_sensor_text)
        compassView = widgetView.findViewById(R.id.compass_view)
        toggleButton = widgetView.findViewById(R.id.toggle_compass_button)
        compassContainer = widgetView.findViewById(R.id.compass_container)

        compassManager = CompassManager(context)

        compassManager.setOnDirectionChangedListener { azimuth ->
            val directionName = compassManager.getDirectionName(azimuth)
            val accuracy = compassManager.getAccuracy()
            handler.post {
                updateCompassDisplay(azimuth, directionName, accuracy)
            }
        }

        compassManager.setOnAccuracyChangedListener { accuracy ->
            val azimuth = compassManager.getCurrentDirection()
            val directionName = compassManager.getDirectionName(azimuth)
            handler.post {
                updateCompassDisplay(azimuth, directionName, accuracy)
            }
        }

        toggleButton.setOnClickListener {
            toggleCompass()
        }

        val isEnabled = sharedPreferences.getBoolean(PREF_COMPASS_ENABLED, false)
        updateUI(isEnabled)

        isInitialized = true
    }

    private fun toggleCompass() {
        val currentState = sharedPreferences.getBoolean(PREF_COMPASS_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_COMPASS_ENABLED, newState) }
        updateUI(newState)
    }

    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {

            compassContainer.visibility = View.VISIBLE
            toggleButton.text = context.getString(R.string.compass_disable)

            if (compassManager.hasRequiredSensors()) {
                setupWithSensors()
            } else {
                setupWithoutSensors()
            }
        } else {

            compassContainer.visibility = View.GONE
            toggleButton.text = context.getString(R.string.compass_enable)

            handler.removeCallbacks(updateRunnable)
            compassManager.stopTracking()
        }
    }

    private fun setupWithSensors() {
        noSensorText.visibility = View.GONE
        compassView.visibility = View.VISIBLE
        directionText.visibility = View.VISIBLE
        azimuthText.visibility = View.VISIBLE

        compassManager.startTracking()

        handler.post(updateRunnable)

        updateDisplay()
    }

    private fun setupWithoutSensors() {
        noSensorText.visibility = View.VISIBLE
        compassView.visibility = View.GONE
        directionText.visibility = View.GONE
        azimuthText.visibility = View.GONE

        noSensorText.text = context.getString(R.string.compass_no_sensors)
    }

    private fun updateDisplay() {
        val azimuth = compassManager.getCurrentDirection()
        val directionName = compassManager.getDirectionName(azimuth)
        val accuracy = compassManager.getAccuracy()
        updateCompassDisplay(azimuth, directionName, accuracy)
    }

    private fun updateCompassDisplay(azimuth: Float, directionName: String, accuracy: Int) {
        directionText.text = directionName
        azimuthText.text = context.getString(R.string.compass_azimuth_format, azimuth.toInt())
        compassView.setDirection(azimuth, directionName, accuracy)
    }

    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_COMPASS_ENABLED, false)
            if (isEnabled && compassManager.hasRequiredSensors()) {
                compassManager.startTracking()
                handler.post(updateRunnable)
                updateDisplay()
            }
        }
    }

    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            compassManager.stopTracking()
        }
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            compassManager.cleanup()
        }
    }
}
