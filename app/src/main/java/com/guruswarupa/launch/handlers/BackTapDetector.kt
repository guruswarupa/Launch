package com.guruswarupa.launch.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.guruswarupa.launch.handlers.GestureCoordinator
import kotlin.math.abs
import kotlin.math.sqrt





class BackTapDetector(
    context: Context,
    private val onBackTap: (Int) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var tapThreshold = 6.0f
    private var maxTapThreshold = 18.0f

    private val tapTimeWindow = 450L
    private val minTimeBetweenTaps = 220L

    private var lastTapTime = 0L
    private var tapCount = 0

    private var lastUpdateTime = 0L
    private var isListening = false
    private var isUsingLinearAcc = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION


    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    fun start() {
        if (isListening) return

        sensor?.let {

            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
        }
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        tapCount = 0
        isListening = false
    }

    fun updateSensitivity(sensitivity: Int) {



        val baseMin = if (isUsingLinearAcc) 4.0f else 12.0f
        val baseMax = if (isUsingLinearAcc) 25.0f else 40.0f



        tapThreshold = baseMax - (sensitivity.coerceIn(1, 10) - 1) * ((baseMax - baseMin) / 9f)




        maxTapThreshold = tapThreshold * 2.2f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return


        if (GestureCoordinator.isInCooldown()) {
            tapCount = 0
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 10) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration: Float
        val zAcceleration: Float

        if (isUsingLinearAcc) {
            acceleration = sqrt(x*x + y*y + z*z)
            zAcceleration = abs(z)
        } else {
            val dx = x - lastX
            val dy = y - lastY
            val dz = z - lastZ
            acceleration = sqrt(dx*dx + dy*dy + dz*dz)
            zAcceleration = abs(dz)
        }

        lastX = x
        lastY = y
        lastZ = z



        val isMostlyZ = zAcceleration > (acceleration * 0.6f)


        if (acceleration > tapThreshold && acceleration < maxTapThreshold && isMostlyZ) {
            val now = System.currentTimeMillis()

            if (now - lastTapTime > minTimeBetweenTaps) {
                if (now - lastTapTime > tapTimeWindow) {
                    tapCount = 1
                } else {
                    tapCount++
                }

                if (tapCount >= 2) {
                    onBackTap(2)
                    tapCount = 0

                    lastTapTime = now + 300
                } else {
                    lastTapTime = now
                }
            }
        } else if (acceleration >= maxTapThreshold) {

            tapCount = 0
        }

        lastUpdateTime = currentTime
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
