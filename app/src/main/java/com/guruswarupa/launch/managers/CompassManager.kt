package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var isListening = false

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var currentAzimuth: Float = 0f
    private var onDirectionChanged: ((Float) -> Unit)? = null
    private var onAccuracyChangedListener: ((Int) -> Unit)? = null
    private var currentAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

    companion object {
        private const val TAG = "CompassManager"
    }

    init {
        initializeSensors()
    }

    private fun initializeSensors() {
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometerSensor == null || magnetometerSensor == null) {
            Log.w(TAG, "Required sensors not available for compass")
        }
    }

    fun hasRequiredSensors(): Boolean {
        return accelerometerSensor != null && magnetometerSensor != null
    }

    fun setOnDirectionChangedListener(listener: (Float) -> Unit) {
        onDirectionChanged = listener
    }

    fun setOnAccuracyChangedListener(listener: (Int) -> Unit) {
        onAccuracyChangedListener = listener
    }

    fun startTracking() {
        if (!hasRequiredSensors()) {
            Log.w(TAG, "Cannot start tracking: sensors not available")
            return
        }

        if (isListening) {
            return
        }

        val success1 = sensorManager.registerListener(
            this,
            accelerometerSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        val success2 = sensorManager.registerListener(
            this,
            magnetometerSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        if (success1 && success2) {
            isListening = true
        } else {
            // Partial registration: a listener may already be active, so unregister
            // the whole listener to avoid leaking a sensor registration.
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    fun stopTracking() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }




    fun cleanup() {
        stopTracking()
        onDirectionChanged = null
        onAccuracyChangedListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }

        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            currentAccuracy = accuracy
            onAccuracyChangedListener?.invoke(accuracy)
        }
    }

    private fun updateOrientationAngles() {

        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )


        SensorManager.getOrientation(rotationMatrix, orientationAngles)



        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val normalizedAzimuth = ((azimuth + 360) % 360)

        if (abs(normalizedAzimuth - currentAzimuth) > 1f) {
            currentAzimuth = normalizedAzimuth
            onDirectionChanged?.invoke(currentAzimuth)
        }
    }

    fun getCurrentDirection(): Float {
        return currentAzimuth
    }

    fun getAccuracy(): Int {
        return currentAccuracy
    }

    fun getDirectionName(azimuth: Float): String {
        val a = (azimuth + 22.5f) % 360f

        return when (a) {
            in 0f..45f -> "N"
            in 45f..90f -> "NE"
            in 90f..135f -> "E"
            in 135f..180f -> "SE"
            in 180f..225f -> "S"
            in 225f..270f -> "SW"
            in 270f..315f -> "W"
            in 315f..360f -> "NW"
            else -> "N"
        }
    }
}
