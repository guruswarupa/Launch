package com.guruswarupa.launch.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt






class ShakeDetector(
    context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


    private var shakeThreshold = 25.0f
    private val shakeTimeWindow = 1000L
    private val minTimeBetweenShakes = 150L

    private var lastShakeTime = 0L
    private var shakeCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var resetShakeCountRunnable: Runnable? = null

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdateTime = 0L

    private var isListening = false




    fun start() {
        if (isListening) return

        accelerometer?.let {

            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        }
    }




    fun updateSensitivity(sensitivity: Int) {



        shakeThreshold = 45f - (sensitivity.coerceIn(1, 10) - 1) * 3.33f
    }




    fun stop() {
        if (!isListening) return

        sensorManager.unregisterListener(this)
        isListening = false
        shakeCount = 0
        resetShakeCountRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return


        if (GestureCoordinator.isInCooldown()) {
            shakeCount = 0
            return
        }

        val currentTime = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (lastUpdateTime == 0L) {
            lastX = x
            lastY = y
            lastZ = z
            lastUpdateTime = currentTime
            return
        }

        val timeDelta = currentTime - lastUpdateTime

        if (timeDelta < 20) return

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)


        val acceleration = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

        if (acceleration > shakeThreshold) {
            val timeSinceLastShake = currentTime - lastShakeTime


            if (timeSinceLastShake > minTimeBetweenShakes) {
                shakeCount++
                lastShakeTime = currentTime


                resetShakeCountRunnable?.let { handler.removeCallbacks(it) }

                if (shakeCount >= 3) {

                    onShakeDetected()
                    shakeCount = 0
                } else {

                    val runnable = Runnable {
                        shakeCount = 0
                    }
                    resetShakeCountRunnable = runnable
                    handler.postDelayed(runnable, shakeTimeWindow)
                }
            }
        }

        lastX = x
        lastY = y
        lastZ = z
        lastUpdateTime = currentTime
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun cleanup() {
        stop()
    }
}
