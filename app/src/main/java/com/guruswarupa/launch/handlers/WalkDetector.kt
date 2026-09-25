package com.guruswarupa.launch.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

class WalkDetector(
    context: Context,
    private val onWalkingDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepTimestamps = mutableListOf<Long>()

    var stepThreshold = 10
    var timeWindowMs = 15_000L
    var cooldownMs = 5 * 60_000L

    private var lastTriggerTime = 0L
    private var isListening = false

    companion object {
        private const val TAG = "WalkDetector"
    }

    fun start() {
        if (isListening) return
        if (stepDetector == null) {
            return
        }

        sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL)
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        stepTimestamps.clear()
    }

    fun cleanup() {
        stop()
    }

    fun updateSettings(threshold: Int, windowSeconds: Int, cooldownMinutes: Int) {
        stepThreshold = threshold.coerceAtLeast(1)
        timeWindowMs = windowSeconds.coerceAtLeast(1) * 1000L
        cooldownMs = cooldownMinutes.coerceAtLeast(0) * 60_000L
        pruneOldSteps(SystemClock.elapsedRealtime())
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        if (event.values.isEmpty() || event.values[0] != 1.0f) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < cooldownMs) return

        stepTimestamps.add(now)
        pruneOldSteps(now)

        if (stepTimestamps.size >= stepThreshold) {
            lastTriggerTime = now
            stepTimestamps.clear()
            onWalkingDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun pruneOldSteps(now: Long) {
        stepTimestamps.removeAll { now - it > timeWindowMs }
    }
}
