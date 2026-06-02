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
    private lateinit var currentFullCapacityText: TextView
    private lateinit var calculatedHealthPercentageText: TextView

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
        currentFullCapacityText = widgetView.findViewById(R.id.current_full_capacity_text)
        calculatedHealthPercentageText = widgetView.findViewById(R.id.calculated_health_percentage_text)

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
            chargingSpeedText.setTextColor(context.getColor(R.color.nord10))
        } else {
            chargingSpeedText.text = context.getString(R.string.status_not_charging)
            chargingSpeedText.setTextColor(context.getColor(R.color.widget_text_secondary))
        }

        voltageText.text = String.format(Locale.getDefault(), "%d mV", batteryInfo.voltage)

        temperatureText.text = String.format(Locale.getDefault(), "%.1f°C", batteryInfo.temperature)

        healthStatusText.text = batteryInfo.health
        healthStatusText.setTextColor(
            when (batteryInfo.health) {
                "Good" -> context.getColor(R.color.nord14)
                "Overheat", "Dead", "Over Voltage" -> context.getColor(R.color.nord13)
                else -> context.getColor(R.color.widget_text)
            }
        )

        designCapacityText.text = String.format(Locale.getDefault(), "%d mAh", batteryInfo.designCapacity)
        
        // Health Calculation and Persistence (Calibrated at 100%)
        val KEY_LAST_CALCULATED_HEALTH = "last_calculated_health"
        val KEY_LAST_FULL_CAPACITY = "last_full_capacity"
        
        val liveChargeCounter = batteryInfo.currentFullCapacity // This is actually the live charge mAh from BatteryManager

        if (batteryInfo.isFull && batteryInfo.designCapacity > 0 && liveChargeCounter > 0) {
            val healthPct = (liveChargeCounter.toFloat() / batteryInfo.designCapacity.toFloat() * 100).toInt().coerceAtMost(100)
            prefs.edit().putInt(KEY_LAST_CALCULATED_HEALTH, healthPct).putInt(KEY_LAST_FULL_CAPACITY, liveChargeCounter).apply()
            
            calculatedHealthPercentageText.text = String.format(Locale.getDefault(), "%d%%", healthPct)
            currentFullCapacityText.text = String.format(Locale.getDefault(), "%d mAh", liveChargeCounter)
        } else {
            val lastHealth = prefs.getInt(KEY_LAST_CALCULATED_HEALTH, -1)
            val lastFullCapacity = prefs.getInt(KEY_LAST_FULL_CAPACITY, -1)
            
            if (lastHealth != -1) {
                calculatedHealthPercentageText.text = String.format(Locale.getDefault(), "%d%%", lastHealth)
            } else {
                calculatedHealthPercentageText.text = "--"
            }
            
            if (lastFullCapacity != -1) {
                currentFullCapacityText.text = String.format(Locale.getDefault(), "%d mAh", lastFullCapacity)
            } else {
                currentFullCapacityText.text = "Calibrating..."
            }
        }
    }

    fun onResume() {
        if (isInitialized) {
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
