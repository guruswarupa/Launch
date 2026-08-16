package com.guruswarupa.launch.ui.activities

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.DeviceInfoManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.managers.BatteryManager as AppBatteryManager
import com.guruswarupa.launch.ui.views.UsageGraphView
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import java.util.Locale

class SystemMonitorActivity : AppCompatActivity() {

    private lateinit var deviceInfoManager: DeviceInfoManager
    private lateinit var appBatteryManager: AppBatteryManager

    private class SystemMonitorHandler(activity: SystemMonitorActivity) : Handler(Looper.getMainLooper()) {
        private val weakActivity = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            val activity = weakActivity.get() ?: return
        }
    }

    private val handler = SystemMonitorHandler(this)
    private var updateRunnable: Runnable? = null
    private val prefs by lazy { getSharedPreferences("system_monitor_prefs", MODE_PRIVATE) }

    private lateinit var cpuGraph: UsageGraphView
    private lateinit var gpuGraph: UsageGraphView
    private lateinit var cpuCoresContainer: ChipGroup
    private lateinit var gpuCoresContainer: ChipGroup

    companion object {
        private const val KEY_LAST_CALCULATED_HEALTH = "last_calculated_health"
        private const val KEY_LAST_FULL_CAPACITY = "last_full_capacity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContentView(R.layout.activity_system_monitor)
        TypographyManager.applyToView(findViewById(android.R.id.content))
        applyContentInsets()

        deviceInfoManager = DeviceInfoManager(this)
        appBatteryManager = AppBatteryManager(this)

        cpuGraph = findViewById(R.id.cpu_graph)
        gpuGraph = findViewById(R.id.gpu_graph)
        cpuCoresContainer = findViewById(R.id.cpu_cores_container)
        gpuCoresContainer = findViewById(R.id.gpu_cores_container)

        setupWallpaper()
        setupHardwareInfo()
        setupNetworkInfo()
        setupSensorsAndCameraInfo()

        startRealtimeUpdates()
    }

    override fun onBackPressed() {
        finish()
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
    }

    private fun startRealtimeUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                updatePerformanceInfo()
                updateBatteryInfo()
                handler.postDelayed(this, 1000)
            }
        }
        updateRunnable = runnable
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updatePerformanceInfo() {
        val cpuStatus = findViewById<TextView>(R.id.cpu_status)
        val cpuTempText = findViewById<TextView>(R.id.cpu_temp)
        val gpuStatus = findViewById<TextView>(R.id.gpu_status)
        val gpuTempText = findViewById<TextView>(R.id.gpu_temp)

        val cpuUsage = deviceInfoManager.getCpuUsage()
        val cpuModel = deviceInfoManager.getCpuModel()
        cpuStatus.text = String.format(Locale.getDefault(), "CPU: %s (%d%%)", cpuModel, cpuUsage)
        cpuGraph.addDataPoint(cpuUsage.toFloat())
        
        val cTemp = deviceInfoManager.getCpuTemperature()
        if (cTemp > 0) {
            cpuTempText.text = String.format(Locale.getDefault(), "CPU Temp: %.1f°C", cTemp)
        } else if (cTemp == -2f) {
            cpuTempText.text = getString(R.string.lbl_cpu_temp_sensor_blocked)
        } else {
            cpuTempText.text = getString(R.string.lbl_cpu_temp_restricted)
        }

        updateCpuCores(deviceInfoManager.getCpuCoreUsages(), deviceInfoManager.getCpuCoreFrequencies())

        val gpuUsage = deviceInfoManager.getGpuUsage()
        val gpuModel = deviceInfoManager.getGpuModel()
        
        gpuStatus.text = String.format(Locale.getDefault(), "GPU: %s - %d%%", gpuModel, gpuUsage)
        gpuGraph.addDataPoint(gpuUsage.toFloat())
        gpuCoresContainer.visibility = View.GONE
        
        val gTemp = deviceInfoManager.getGpuTemperature()
        if (gTemp > 0) {
            gpuTempText.text = String.format(Locale.getDefault(), "GPU Temp: %.1f°C", gTemp)
        } else {
            gpuTempText.text = getString(R.string.lbl_gpu_temp_restricted)
        }
    }

    private fun updateCpuCores(coreUsages: List<Int>, coreFreqs: List<String>) {
        if (cpuCoresContainer.childCount != coreUsages.size) {
            cpuCoresContainer.removeAllViews()
            repeat(coreUsages.size) {
                val chip = Chip(this).apply {
                    setChipBackgroundColorResource(R.color.card_background)
                    setTextColor(Color.WHITE)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                    isClickable = false
                    isCheckable = false
                }
                cpuCoresContainer.addView(chip)
            }
        }

        for (i in coreUsages.indices) {
            val chip = cpuCoresContainer.getChildAt(i) as? Chip ?: continue
            val usage = coreUsages[i]
            val freq = if (i < coreFreqs.size) coreFreqs[i] else "N/A"
            chip.text = String.format(Locale.getDefault(), "C%d: %d%% (%s)", i, usage, freq)
            
            // Visual indicator of load
            when {
                usage > 80 -> chip.setChipStrokeColorResource(R.color.red)
                usage > 50 -> chip.setChipStrokeColorResource(R.color.nord13) // yellow/orange
                else -> chip.setChipStrokeColorResource(R.color.nord8) // cyan/blue
            }
            chip.chipStrokeWidth = 2f
        }
    }

    private fun updateBatteryInfo() {
        val batteryInfo = appBatteryManager.getBatteryHealthInfo()

        val healthText = findViewById<TextView>(R.id.battery_health)
        val capacityText = findViewById<TextView>(R.id.battery_capacity)
        val calculatedHealthText = findViewById<TextView>(R.id.battery_calculated_health)
        val voltageText = findViewById<TextView>(R.id.battery_voltage)
        val batteryTempText = findViewById<TextView>(R.id.battery_temp)
        val chargingSpeedText = findViewById<TextView>(R.id.battery_charging_speed)
        val timeRemainingText = findViewById<TextView>(R.id.battery_time_remaining)

        healthText.text = String.format(Locale.getDefault(), "Battery: %d%% (%s)", batteryInfo.percentage, batteryInfo.health)

        voltageText.text = String.format(Locale.getDefault(), "Voltage: %d mV", batteryInfo.voltage)
        batteryTempText.text = String.format(Locale.getDefault(), "Temperature: %.1f°C", batteryInfo.temperature)

        if (batteryInfo.isCharging) {
            chargingSpeedText.text = String.format(Locale.getDefault(), "Charging Speed: %d mA", batteryInfo.chargingSpeed)
            timeRemainingText.text = String.format(Locale.getDefault(), "Time to Full: %s", batteryInfo.timeRemaining ?: "--")
        } else {
            chargingSpeedText.text = getString(R.string.lbl_status_discharging)
            timeRemainingText.text = String.format(Locale.getDefault(), "Time Remaining: %s", batteryInfo.timeRemaining ?: "--")
        }

        val designCapacity = batteryInfo.designCapacity
        val currentChargeCounter = batteryInfo.liveCapacity

        if (batteryInfo.isFull && designCapacity > 0 && currentChargeCounter > 0) {
            val healthPct = (currentChargeCounter.toFloat() / designCapacity.toFloat() * 100).toInt().coerceAtMost(100)
            prefs.edit().putInt(KEY_LAST_CALCULATED_HEALTH, healthPct).putInt(KEY_LAST_FULL_CAPACITY, currentChargeCounter).apply()

            capacityText.text = String.format(Locale.getDefault(), "Design: %dmAh | Full: %dmAh | Charge: %dmAh", designCapacity, currentChargeCounter, currentChargeCounter)
            calculatedHealthText.text = String.format(Locale.getDefault(), "Calculated Health: %d%% (Updated at 100%%)", healthPct)
        } else {
            val lastFullCapacity = prefs.getInt(KEY_LAST_FULL_CAPACITY, -1)
            val lastHealth = prefs.getInt(KEY_LAST_CALCULATED_HEALTH, -1)

            if (lastFullCapacity != -1) {
                capacityText.text = String.format(Locale.getDefault(), "Design: %dmAh | Full: %dmAh | Charge: %dmAh", designCapacity, lastFullCapacity, currentChargeCounter)
            } else {
                capacityText.text = String.format(Locale.getDefault(), "Design: %dmAh | Full: Calibrating... | Charge: %dmAh", designCapacity, currentChargeCounter)
            }

            if (lastHealth != -1) {
                calculatedHealthText.text = String.format(Locale.getDefault(), "Calculated Health: %d%% (Last full charge)", lastHealth)
            } else {
                calculatedHealthText.text = getString(R.string.lbl_calculated_health_waiting_for_100_charge_to_cali)
            }
        }
    }

    private fun setupHardwareInfo() {
        val container = findViewById<LinearLayout>(R.id.hardware_info_container)
        container.removeAllViews()

        addInfoItem(container, "Processor", deviceInfoManager.getCpuModel())
        addInfoItem(container, "Android Version", deviceInfoManager.getAndroidVersion())
        addInfoItem(container, "Kernel", deviceInfoManager.getKernelVersion())
        
        val ram = deviceInfoManager.getRamUsage()
        addInfoItem(container, "Memory (RAM)", String.format(Locale.getDefault(), "%sGB / %sGB used (%s)", deviceInfoManager.formatBytes(ram.first), deviceInfoManager.formatBytes(ram.second), deviceInfoManager.getRamType()))
        
        val storage = deviceInfoManager.getStorageUsage()
        addInfoItem(container, "Storage", String.format(Locale.getDefault(), "%sGB / %sGB used (%s)", deviceInfoManager.formatBytes(storage.first), deviceInfoManager.formatBytes(storage.second), deviceInfoManager.getStorageType()))
        
        val displayMetrics = resources.displayMetrics
        addInfoItem(container, "Display", String.format(Locale.getDefault(), "%dx%d (%d dpi)", displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi))

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        addInfoItem(container, "Low RAM Device", activityManager.isLowRamDevice.toString())
    }

    private fun setupNetworkInfo() {
        val container = findViewById<LinearLayout>(R.id.network_info_container)
        container.removeAllViews()
        
        val networkInfo = deviceInfoManager.getDetailedNetworkInfo()
        if (networkInfo.isNotEmpty()) {
            for ((label, value) in networkInfo) {
                addInfoItem(container, label, value)
            }
        } else {
            addInfoItem(container, "Network", "Disconnected")
        }
    }

    private fun setupSensorsAndCameraInfo() {
        val container = findViewById<LinearLayout>(R.id.sensors_info_container)
        container.removeAllViews()
        
        // Cameras Detailed
        val cameraInfo = deviceInfoManager.getDetailedCameraInfo()
        addInfoItem(container, "Camera Modules", "${cameraInfo.size} detected")
        addVerticalSpacer(container, 8)
        
        for (cam in cameraInfo) {
            val label = "Camera ${cam["ID"]} (${cam["Facing"]})"
            val value = "Res: ${cam["Resolution"]} | Aperture: ${cam["Aperture"]} | Focal: ${cam["Focal Length"]}"
            addInfoItem(container, label, value)
        }

        addVerticalSpacer(container, 16)

        // Sensors
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        
        addInfoItem(container, "Total Sensors", String.format(Locale.getDefault(), "%d available", sensors.size))
        addVerticalSpacer(container, 8)
        
        for (s in sensors) {
            addInfoItem(container, s.name, String.format(Locale.getDefault(), "Vendor: %s | Power: %.2fmA", s.vendor, s.power))
        }
    }

    private fun addVerticalSpacer(container: LinearLayout, dp: Int) {
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp.toPx()
        )
        container.addView(spacer)
    }

    private fun addInfoItem(container: LinearLayout, label: String, value: String) {
        val view = layoutInflater.inflate(R.layout.item_system_info, container, false)
        view.findViewById<TextView>(R.id.info_label).text = label
        view.findViewById<TextView>(R.id.info_value).text = value
        container.addView(view)
    }

    private fun applyContentInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
