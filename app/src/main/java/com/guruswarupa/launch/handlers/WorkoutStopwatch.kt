package com.guruswarupa.launch.handlers

import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.guruswarupa.launch.R
import java.util.Locale

class WorkoutStopwatch(
    private val timeDisplay: TextView,
    private val startStopButton: Button,
    private val resetButton: Button,
    private val onTimeRecorded: (Int) -> Unit
) {
    private var isRunning = false
    private var startTime = 0L
    private var elapsedTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    init {
        startStopButton.setOnClickListener {
            if (isRunning) {
                stop()
            } else {
                start()
            }
        }

        resetButton.setOnClickListener {
            reset()
        }

        updateDisplay()
    }

    private fun start() {
        if (!isRunning) {
            startTime = System.currentTimeMillis() - elapsedTime
            isRunning = true
            startStopButton.text = startStopButton.context.getString(R.string.workout_stop)
            resetButton.isEnabled = false

            val runnable = object : Runnable {
                override fun run() {
                    if (isRunning) {
                        elapsedTime = System.currentTimeMillis() - startTime
                        updateDisplay()
                        handler.postDelayed(this, 100)
                    }
                }
            }
            updateRunnable = runnable
            handler.post(runnable)
        }
    }

    private fun stop() {
        if (isRunning) {
            isRunning = false
            startStopButton.text = startStopButton.context.getString(R.string.workout_start)
            resetButton.isEnabled = true
            updateRunnable?.let { handler.removeCallbacks(it) }


            val seconds = (elapsedTime / 1000).toInt()
            if (seconds > 0) {
                onTimeRecorded(seconds)
            }
        }
    }

    private fun reset() {
        isRunning = false
        elapsedTime = 0L
        startTime = 0L
        startStopButton.text = startStopButton.context.getString(R.string.workout_start)
        resetButton.isEnabled = true
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateDisplay()
    }

    private fun updateDisplay() {
        val totalSeconds = (elapsedTime / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        timeDisplay.text = when {
            hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            else -> String.format(Locale.getDefault(), "0:%02d", seconds)
        }
    }

    fun cleanup() {
        updateRunnable?.let { handler.removeCallbacks(it) }
    }
}
