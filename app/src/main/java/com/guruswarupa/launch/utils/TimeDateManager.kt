package com.guruswarupa.launch.utils

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeDateManager(
    private val timeTextView: TextView,
    private val dateTextView: TextView,
    private val rightDrawerTime: TextView? = null,
    private val rightDrawerDate: TextView? = null,
    private var use24HourFormat: Boolean = false
) {
    private val handler = Handler(Looper.getMainLooper())
    private var timeFormat = createMainTimeFormat(use24HourFormat)
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    private var artisticTimeFormat = createDrawerTimeFormat(use24HourFormat)
    private val artisticDateFormat = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateDate()
            handler.postDelayed(this, 1000)
        }
    }

    private val powerSaverUpdateRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateDate()
            handler.postDelayed(this, 30000)
        }
    }

    fun startUpdates(isPowerSaver: Boolean = false) {
        stopUpdates()
        if (isPowerSaver) {
            handler.post(powerSaverUpdateRunnable)
        } else {
            handler.post(updateRunnable)
        }
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(powerSaverUpdateRunnable)
    }

    fun destroy() {
        stopUpdates()
        handler.removeCallbacksAndMessages(null)
    }

    fun setUse24HourFormat(enabled: Boolean) {
        if (use24HourFormat == enabled) return
        use24HourFormat = enabled
        timeFormat = createMainTimeFormat(enabled)
        artisticTimeFormat = createDrawerTimeFormat(enabled)
        updateTime()
    }

    fun updateTime() {
        val now = Date()
        val currentTime = timeFormat.format(now)
        timeTextView.text = currentTime

        rightDrawerTime?.text = artisticTimeFormat.format(now)
    }

    fun updateDate() {
        val now = Date()
        val currentTime = dateFormat.format(now)
        dateTextView.text = currentTime

        rightDrawerDate?.text = artisticDateFormat.format(now).uppercase(Locale.getDefault())
    }

    private fun createMainTimeFormat(use24Hour: Boolean): SimpleDateFormat {
        val pattern = if (use24Hour) "HH:mm:ss" else "hh:mm:ss"
        return SimpleDateFormat(pattern, Locale.getDefault())
    }

    private fun createDrawerTimeFormat(use24Hour: Boolean): SimpleDateFormat {
        val pattern = if (use24Hour) "HH:mm" else "hh:mm"
        return SimpleDateFormat(pattern, Locale.getDefault())
    }
}
