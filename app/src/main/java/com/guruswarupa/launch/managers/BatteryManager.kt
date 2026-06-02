package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.TextView
import java.util.Locale

data class BatteryHealthInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingSpeed: Int,
    val voltage: Int,
    val temperature: Float,
    val health: String,
    val timeRemaining: String?,
    val designCapacity: Int,
    val currentFullCapacity: Int,
    val isFull: Boolean
)

class BatteryManager(private val context: Context) {

    fun updateBatteryInfo(batteryText: TextView) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            if (scale > 0) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val chargingText = if (isCharging) " ⚡" else ""
                batteryText.text = String.format(Locale.getDefault(), "Battery: %d%%%s", batteryPct, chargingText)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBatteryHealthInfo(): BatteryHealthInfo {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        var percentage = 85
        var isCharging = false
        var chargingSpeed = 0
        var voltage = 0
        var temperature = 30.0f
        var health = "Good"
        var timeRemaining: String? = null
        var isFull = false

        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val healthStatus = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)

            if (scale > 0) {
                percentage = (level * 100 / scale.toFloat()).toInt()
            }

            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            
            isFull = status == BatteryManager.BATTERY_STATUS_FULL || percentage == 100

            if (isCharging) {
                chargingSpeed = estimateChargingSpeed(batteryIntent)
            }

            voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3700)
            temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) / 10.0f

            health = when (healthStatus) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unknown"
                else -> "Good"
            }

            if (isCharging) {
                timeRemaining = estimateChargingTime(percentage)
            } else {
                timeRemaining = estimateDischargeTime(percentage)
            }
        }

        val designCapacity = getBatteryDesignCapacity()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val currentCapacityMicroAh = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentFullCapacity = Math.abs(currentCapacityMicroAh / 1000)

        return BatteryHealthInfo(
            percentage = percentage,
            isCharging = isCharging,
            chargingSpeed = chargingSpeed,
            voltage = voltage,
            temperature = temperature,
            health = health,
            timeRemaining = timeRemaining,
            designCapacity = designCapacity,
            currentFullCapacity = currentFullCapacity,
            isFull = isFull
        )
    }

    private fun getBatteryDesignCapacity(): Int {
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        return try {
            val mPowerProfile = Class.forName(powerProfileClass).getConstructor(Context::class.java).newInstance(context)
            val batteryCapacity = Class.forName(powerProfileClass).getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
            batteryCapacity.toInt()
        } catch (_: Exception) {
            4000
        }
    }

    private fun estimateChargingSpeed(batteryIntent: android.content.Intent): Int {
        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3700)
        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> {
                when {
                    voltage > 4200 -> 3000
                    voltage > 4000 -> 2000
                    else -> 1000
                }
            }
            BatteryManager.BATTERY_PLUGGED_USB -> {
                when {
                    voltage > 4200 -> 1500
                    voltage > 4000 -> 1000
                    else -> 500
                }
            }
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> {
                when {
                    voltage > 4200 -> 1500
                    else -> 800
                }
            }
            else -> 0
        }
    }

    private fun estimateChargingTime(currentPercentage: Int): String {
        val averageChargingRate = 15
        val remainingToFull = 100 - currentPercentage
        val minutes = (remainingToFull * 60) / averageChargingRate
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun estimateDischargeTime(currentPercentage: Int): String {
        val averageDrainRate = 8
        val hours = currentPercentage / averageDrainRate
        val mins = ((currentPercentage % averageDrainRate) * 60) / averageDrainRate
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }
}
