package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.guruswarupa.launch.managers.DeviceInfoManager
import com.guruswarupa.launch.R

class DeviceInfoWidget(
    private val context: Context,
    private val container: LinearLayout
) : InitializableWidget {
    private var isInitialized = false
    private var isDestroyed = false
    private lateinit var widgetView: View
    private lateinit var cpuModelText: TextView
    private lateinit var cpuTempText: TextView
    private lateinit var ramUsageText: TextView
    private lateinit var ramProgressBar: ProgressBar
    private lateinit var storageUsageText: TextView
    private lateinit var storageProgressBar: ProgressBar

    private lateinit var androidVersionText: TextView
    private lateinit var uptimeText: TextView
    private lateinit var hardwareText: TextView
    private lateinit var kernelText: TextView

    private val deviceInfoManager = DeviceInfoManager(context)
    private val handler = Handler(Looper.getMainLooper())


    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && !isDestroyed) {
                updateDisplay()
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_device_info, container, false)
        container.addView(widgetView)

        cpuModelText = widgetView.findViewById(R.id.cpu_model_text)
        cpuTempText = widgetView.findViewById(R.id.cpu_temp_text)
        ramUsageText = widgetView.findViewById(R.id.ram_usage_text)
        ramProgressBar = widgetView.findViewById(R.id.ram_progress_bar)
        storageUsageText = widgetView.findViewById(R.id.storage_usage_text)
        storageProgressBar = widgetView.findViewById(R.id.storage_progress_bar)

        androidVersionText = widgetView.findViewById(R.id.android_version_text)
        uptimeText = widgetView.findViewById(R.id.uptime_text)
        hardwareText = widgetView.findViewById(R.id.hardware_text)
        kernelText = widgetView.findViewById(R.id.kernel_text)


        cpuModelText.text = deviceInfoManager.getCpuModel()
        androidVersionText.text = deviceInfoManager.getAndroidVersion()
        hardwareText.text = deviceInfoManager.getHardwareInfo()
        kernelText.text = deviceInfoManager.getKernelVersion()

        updateDisplay()


        handler.post(updateRunnable)

        isInitialized = true
    }

    private fun updateDisplay() {
        uptimeText.text = deviceInfoManager.getUptime()


        val (usedRam, totalRam) = deviceInfoManager.getRamUsage()
        val usedRamGb = deviceInfoManager.formatBytes(usedRam)
        val totalRamGb = deviceInfoManager.formatBytes(totalRam)
        ramUsageText.text = context.getString(R.string.ram_usage_format, usedRamGb, totalRamGb)

        if (totalRam > 0) {
            ramProgressBar.progress = ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt()
        }


        val (usedStorage, totalStorage) = deviceInfoManager.getStorageUsage()
        val usedStorageGb = deviceInfoManager.formatBytes(usedStorage)
        val totalStorageGb = deviceInfoManager.formatBytes(totalStorage)
        storageUsageText.text = context.getString(R.string.storage_usage_format, usedStorageGb, totalStorageGb)

        if (totalStorage > 0) {
            storageProgressBar.progress = ((usedStorage.toDouble() / totalStorage.toDouble()) * 100).toInt()
        }


        val temp = deviceInfoManager.getCpuTemperature()
        if (temp > 0) {
            cpuTempText.text = context.getString(R.string.cpu_temp_format, temp)
        } else {
            cpuTempText.text = "--"
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
        isDestroyed = true
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacksAndMessages(null)
    }
}
