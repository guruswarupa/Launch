package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.NetworkStatsManager

class NetworkStatsWidget(
    private val context: Context,
    private val container: LinearLayout
) : InitializableWidget {
    private var isInitialized = false
    private lateinit var widgetView: View
    private lateinit var downloadText: TextView
    private lateinit var uploadText: TextView
    private lateinit var pingText: TextView
    private lateinit var jitterText: TextView
    private lateinit var statusText: TextView
    private lateinit var runButton: Button
    private lateinit var wifiIpText: TextView

    private val networkStatsManager = NetworkStatsManager()
    private val handler = Handler(Looper.getMainLooper())


    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateDataUsage()
                handler.postDelayed(this, 10000)
            }
        }
    }

    private lateinit var mobileUsageText: TextView
    private lateinit var wifiUsageText: TextView

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_network_stats, container, false)
        container.addView(widgetView)

        downloadText = widgetView.findViewById(R.id.download_speed_text)
        uploadText = widgetView.findViewById(R.id.upload_speed_text)
        pingText = widgetView.findViewById(R.id.ping_text)
        jitterText = widgetView.findViewById(R.id.jitter_text)
        mobileUsageText = widgetView.findViewById(R.id.mobile_usage_text)
        wifiUsageText = widgetView.findViewById(R.id.wifi_usage_text)
        statusText = widgetView.findViewById(R.id.network_status_text)
        runButton = widgetView.findViewById(R.id.run_speedtest_button)
        wifiIpText = widgetView.findViewById(R.id.wifi_ip_text)

        runButton.setOnClickListener {
            startSpeedTest()
        }

        updateDataUsage()
        isInitialized = true


        handler.post(updateRunnable)
    }

    private fun updateDataUsage() {
        val (mobile, wifi) = networkStatsManager.getNetworkUsage()
        mobileUsageText.text = networkStatsManager.formatDataUsage(mobile)
        wifiUsageText.text = networkStatsManager.formatDataUsage(wifi)
        wifiIpText.text = networkStatsManager.getWifiIpAddress()
    }

    private fun startSpeedTest() {
        runButton.isEnabled = false
        runButton.setText(R.string.status_running)
        statusText.setText(R.string.status_initializing)
        updateDataUsage()

        networkStatsManager.runSpeedTest(
            callback = { result ->
                handler.post {
                    downloadText.text = context.getString(R.string.mbps_format, result.downloadSpeedMbps)
                    uploadText.text = context.getString(R.string.mbps_format, result.uploadSpeedMbps)
                    pingText.text = context.getString(R.string.ms_format, result.pingMs)
                    jitterText.text = context.getString(R.string.ms_format, result.jitterMs)

                    statusText.setText(R.string.status_completed)
                    runButton.isEnabled = true
                    runButton.setText(R.string.button_run_speedtest)
                }
            },
            onProgress = { message ->
                handler.post {
                    statusText.text = message
                }
            },
            onError = { error ->
                handler.post {
                    statusText.text = error
                    runButton.isEnabled = true
                    runButton.setText(R.string.button_run_speedtest)
                }
            }
        )
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
        handler.removeCallbacks(updateRunnable)
    }
}
