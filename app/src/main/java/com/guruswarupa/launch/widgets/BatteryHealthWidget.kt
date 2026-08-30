package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.BatteryManager
import java.util.Locale
import com.guruswarupa.launch.ui.theme.ThemeManager

class BatteryHealthWidget(
    private val context: Context,
    private val container: LinearLayout
) : InitializableWidget {
    private var isInitialized = false
    private lateinit var widgetView: View

    private lateinit var batteryPercentageText: TextView
    private lateinit var timeRemainingText: TextView
    private lateinit var chargingSpeedText: TextView
    private lateinit var voltageText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var healthStatusText: TextView
    private lateinit var designCapacityText: TextView
    private lateinit var liveCapacityText: TextView
    private lateinit var currentFullCapacityText: TextView
    private lateinit var calculatedHealthPercentageText: TextView
    private lateinit var healthPredictionIndicator: TextView

    private val batteryManager = BatteryManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("system_monitor_prefs", Context.MODE_PRIVATE) }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateDisplay()
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_battery_health, container, false)
        container.addView(widgetView)

        batteryPercentageText = widgetView.findViewById(R.id.battery_percentage_text)
        timeRemainingText = widgetView.findViewById(R.id.time_remaining_text)
        chargingSpeedText = widgetView.findViewById(R.id.charging_speed_text)
        voltageText = widgetView.findViewById(R.id.voltage_text)
        temperatureText = widgetView.findViewById(R.id.temperature_text)
        healthStatusText = widgetView.findViewById(R.id.health_status_text)
        designCapacityText = widgetView.findViewById(R.id.design_capacity_text)
        liveCapacityText = widgetView.findViewById(R.id.live_capacity_text)
        currentFullCapacityText = widgetView.findViewById(R.id.current_full_capacity_text)
        calculatedHealthPercentageText = widgetView.findViewById(R.id.calculated_health_percentage_text)
        healthPredictionIndicator = widgetView.findViewById(R.id.health_prediction_indicator)

        updateDisplay()

        handler.post(updateRunnable)

        isInitialized = true
    }

    private fun updateDisplay() {
        val batteryInfo = batteryManager.getBatteryHealthInfo()

        batteryPercentageText.text = String.format(Locale.getDefault(), "%d%%", batteryInfo.percentage)

        timeRemainingText.text = batteryInfo.timeRemaining ?: "--"

        if (batteryInfo.isCharging) {
            chargingSpeedText.text = String.format(Locale.getDefault(), "%d mA", batteryInfo.chargingSpeed)
            chargingSpeedText.setTextColor(ThemeManager.color(context, R.attr.appAccentSecondary))
        } else {
            chargingSpeedText.text = context.getString(R.string.status_not_charging)
            chargingSpeedText.setTextColor(ThemeManager.color(context, R.attr.appTextPrimary))
        }

        voltageText.text = String.format(Locale.getDefault(), "%d mV", batteryInfo.voltage)

        temperatureText.text = String.format(Locale.getDefault(), "%.1f°C", batteryInfo.temperature)

        healthStatusText.text = batteryInfo.health
        healthStatusText.setTextColor(
            when (batteryInfo.health) {
                "Good" -> ThemeManager.color(context, R.attr.appSuccess)
                "Overheat", "Dead", "Over Voltage" -> ThemeManager.color(context, R.attr.appWarning)
                else -> ThemeManager.color(context, R.attr.appTextPrimary)
            }
        )

        designCapacityText.text = String.format(Locale.getDefault(), "%d mAh", batteryInfo.designCapacity)
        liveCapacityText.text = String.format(Locale.getDefault(), "%d mAh", batteryInfo.liveCapacity)
        
        // Health Calculation based on current percentage and design capacity
        // Predict full capacity and health percentage instead of requiring full charge calibration
        val liveChargeCounter = batteryInfo.liveCapacity
        val currentPercentage = batteryInfo.percentage
        val designCapacity = batteryInfo.designCapacity
        val isFull = batteryInfo.isFull
        
        val KEY_LAST_CALCULATED_HEALTH = "last_calculated_health"
        val KEY_LAST_FULL_CAPACITY = "last_full_capacity"
        
        if (isFull && designCapacity > 0 && liveChargeCounter > 0) {
            // Accurate health calculation when battery is at 100% (most reliable method)
            val healthPct = (liveChargeCounter.toFloat() / designCapacity.toFloat() * 100).toInt().coerceAtMost(100)
            prefs.edit().putInt(KEY_LAST_CALCULATED_HEALTH, healthPct).putInt(KEY_LAST_FULL_CAPACITY, liveChargeCounter).apply()
            
            calculatedHealthPercentageText.text = String.format(Locale.getDefault(), "%d%%", healthPct)
            currentFullCapacityText.text = String.format(Locale.getDefault(), "%d mAh", liveChargeCounter)
            
            // Hide prediction indicator since this is the accurate calculation at 100%
            healthPredictionIndicator.visibility = View.GONE
        } else if (designCapacity > 0 && liveChargeCounter > 0 && currentPercentage > 0) {
            // Predict what the full capacity will be when fully charged (estimate)
            val predictedFullCapacity = (liveChargeCounter.toFloat() / currentPercentage.toFloat() * 100).toInt()
            
            // Calculate health percentage based on predicted full capacity vs design capacity
            val healthPct = (predictedFullCapacity.toFloat() / designCapacity.toFloat() * 100).toInt().coerceAtMost(100)
            
            // Store the prediction for persistence
            prefs.edit().putInt(KEY_LAST_CALCULATED_HEALTH, healthPct).putInt(KEY_LAST_FULL_CAPACITY, predictedFullCapacity).apply()
            
            calculatedHealthPercentageText.text = String.format(Locale.getDefault(), "%d%%", healthPct)
            currentFullCapacityText.text = String.format(Locale.getDefault(), "%d mAh", predictedFullCapacity)
            
            // Show prediction indicator since this is an estimate, not measured at 100%
            healthPredictionIndicator.visibility = View.VISIBLE
        } else {
            // Fallback to stored values if current calculation is not possible
            val lastHealth = prefs.getInt(KEY_LAST_CALCULATED_HEALTH, -1)
            val lastFullCapacity = prefs.getInt(KEY_LAST_FULL_CAPACITY, -1)
            
            if (lastHealth != -1) {
                calculatedHealthPercentageText.text = String.format(Locale.getDefault(), "%d%%", lastHealth)
                // Hide prediction indicator since stored values may be from accurate 100% calculation
                healthPredictionIndicator.visibility = View.GONE
            } else {
                calculatedHealthPercentageText.text = "--"
                healthPredictionIndicator.visibility = View.GONE
            }
            
            if (lastFullCapacity != -1) {
                currentFullCapacityText.text = String.format(Locale.getDefault(), "%d mAh", lastFullCapacity)
            } else {
                currentFullCapacityText.text = "--"
            }
        }
    }

    fun onResume() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
    }

    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }
}
