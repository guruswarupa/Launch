package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

class TemperatureManager(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var temperatureSensor: Sensor? = null
    private var isListening = false

    private var currentTemperature: Float = 0f
    private var onTemperatureChanged: ((Float) -> Unit)? = null

    companion object {
        private const val TAG = "TemperatureManager"
    }

    init {
        initializeSensor()
    }

    private fun initializeSensor() {

        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)


        if (temperatureSensor == null) {
            @Suppress("DEPRECATION")
            temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
        }

        if (temperatureSensor == null) {
            Log.w(TAG, "Temperature sensor not available on this device")
        }
    }

    fun hasTemperatureSensor(): Boolean {
        return temperatureSensor != null
    }

    fun setOnTemperatureChangedListener(listener: (Float) -> Unit) {
        onTemperatureChanged = listener
    }

    fun startTracking() {
        if (!hasTemperatureSensor()) {
            Log.w(TAG, "Cannot start tracking: temperature sensor not available")
            return
        }

        if (isListening) {
            return
        }

        val success = sensorManager.registerListener(
            this,
            temperatureSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        if (success) {
            isListening = true
        } else {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        isListening = false
    }




    fun cleanup() {
        stopTracking()
        onTemperatureChanged = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        @Suppress("DEPRECATION")
        val temperature = when (event.sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> event.values[0]
            Sensor.TYPE_TEMPERATURE -> event.values[0]
            else -> return
        }

        if (abs(temperature - currentTemperature) > 0.1f) {
            currentTemperature = temperature
            onTemperatureChanged?.invoke(currentTemperature)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    fun getCurrentTemperature(): Float {
        return currentTemperature
    }

    fun getTemperatureInFahrenheit(): Float {
        return (currentTemperature * 9f / 5f) + 32f
    }

    fun getTemperatureStatus(): String {
        return when {
            currentTemperature < 0 -> "Freezing"
            currentTemperature < 10 -> "Cold"
            currentTemperature < 20 -> "Cool"
            currentTemperature < 30 -> "Warm"
            currentTemperature < 40 -> "Hot"
            else -> "Very Hot"
        }
    }
}
