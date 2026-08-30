package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.managers.NoiseDecibelManager
import com.guruswarupa.launch.ui.theme.ThemeManager
import java.text.DecimalFormat

class NoiseDecibelWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) : InitializableWidget {

    private lateinit var noiseManager: NoiseDecibelManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private lateinit var decibelText: TextView
    private lateinit var noiseLevelText: TextView
    private lateinit var decibelIndicator: View
    private lateinit var toggleButton: Button
    private lateinit var permissionButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var noMicrophoneText: TextView
    private lateinit var noPermissionText: TextView
    private lateinit var widgetView: View

    private val df = DecimalFormat("#.#")

    companion object {
        private const val PREF_NOISE_ENABLED = "noise_decibel_enabled"
    }

    private val updateRunnable = object : Runnable {
        override fun run() {

            if (isInitialized && !noiseManager.isRecording()) {
                val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
                if (isEnabled && hasMicrophonePermission() && noiseManager.hasMicrophone()) {
                    noiseManager.startRecording()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_noise_decibel, container, false)
        container.addView(widgetView)

        decibelText = widgetView.findViewById(R.id.decibel_text)
        noiseLevelText = widgetView.findViewById(R.id.noise_level_text)
        decibelIndicator = widgetView.findViewById(R.id.decibel_indicator)
        toggleButton = widgetView.findViewById(R.id.toggle_noise_button)
        permissionButton = widgetView.findViewById(R.id.request_microphone_permission_button)
        widgetContainer = widgetView.findViewById(R.id.noise_container)
        noMicrophoneText = widgetView.findViewById(R.id.no_microphone_text)
        noPermissionText = widgetView.findViewById(R.id.no_permission_text)

        noiseManager = NoiseDecibelManager(context)

        noiseManager.setOnDecibelChangedListener { decibel ->
            handler.post {
                updateDecibelDisplay(decibel)
            }
        }

        toggleButton.setOnClickListener {
            toggleNoiseAnalyzer()
        }

        permissionButton.setOnClickListener {
            requestMicrophonePermission()
        }


        if (!sharedPreferences.contains(PREF_NOISE_ENABLED)) {
            sharedPreferences.edit { putBoolean(PREF_NOISE_ENABLED, false) }
        }

        val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
        updateUI(isEnabled)

        isInitialized = true
    }

    private fun toggleNoiseAnalyzer() {
        val currentState = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_NOISE_ENABLED, newState) }
        updateUI(newState)
    }

    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            widgetContainer.visibility = View.VISIBLE
            toggleButton.setText(R.string.noise_button_disable)


            if (!hasMicrophonePermission()) {
                setupWithoutPermission()
                return
            }


            if (noiseManager.hasMicrophone()) {
                setupWithMicrophone()
            } else {
                setupWithoutMicrophone()
            }
        } else {
            widgetContainer.visibility = View.GONE
            toggleButton.setText(R.string.noise_button_enable)
            handler.removeCallbacks(updateRunnable)
            noiseManager.stopRecording()
        }
    }

    private fun setupWithMicrophone() {
        noMicrophoneText.visibility = View.GONE
        noPermissionText.visibility = View.GONE
        permissionButton.visibility = View.GONE
        decibelText.visibility = View.VISIBLE
        noiseLevelText.visibility = View.VISIBLE
        decibelIndicator.visibility = View.VISIBLE

        if (noiseManager.startRecording()) {
            handler.post(updateRunnable)
        } else {
            setupWithoutMicrophone()
        }
    }

    private fun setupWithoutMicrophone() {
        noMicrophoneText.visibility = View.VISIBLE
        noPermissionText.visibility = View.GONE
        permissionButton.visibility = View.GONE
        decibelText.visibility = View.GONE
        noiseLevelText.visibility = View.GONE
        decibelIndicator.visibility = View.GONE
        noiseManager.stopRecording()
    }

    private fun setupWithoutPermission() {
        noMicrophoneText.visibility = View.GONE
        noPermissionText.visibility = View.VISIBLE
        permissionButton.visibility = View.VISIBLE
        decibelText.visibility = View.GONE
        noiseLevelText.visibility = View.GONE
        decibelIndicator.visibility = View.GONE
        noiseManager.stopRecording()
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        if (context is androidx.fragment.app.FragmentActivity) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                android.app.AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle(context.getString(R.string.dlg_microphone_permission))
                    .setMessage(context.getString(R.string.dlg_this_permission_allows_the_launcher_to_measure_a))
                    .setPositiveButton(context.getString(R.string.dlg_grant_permission)) { _, _ ->

                        androidx.core.app.ActivityCompat.requestPermissions(
                            context,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            PermissionManager.VOICE_SEARCH_REQUEST
                        )
                    }
                    .setNegativeButton(context.getString(R.string.cancel_button), null)
                    .show()
            }
        } else {

            openSettings()
        }
    }

    private fun openSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDecibelDisplay(decibel: Double) {
        decibelText.text = context.getString(R.string.decibel_format, df.format(decibel))


        val levelRes = when {
            decibel < 30 -> R.string.noise_level_quiet
            decibel < 50 -> R.string.noise_level_moderate
            decibel < 70 -> R.string.noise_level_loud
            else -> R.string.noise_level_very_loud
        }

        val color = when {
            decibel < 30 -> ThemeManager.color(context, R.attr.appHighlight)
            decibel < 50 -> ThemeManager.color(context, R.attr.appAccent)
            decibel < 70 -> ThemeManager.color(context, R.attr.appError)
            else -> ContextCompat.getColor(context, R.color.nord12) // most severe step, no themed attr yet
        }

        noiseLevelText.setText(levelRes)
        noiseLevelText.setTextColor(color)


        val indicatorWidth = (decibel / 120.0).coerceIn(0.0, 1.0)
        val parentWidth = decibelIndicator.parent as? View
        parentWidth?.let { parent ->
            val layoutParams = decibelIndicator.layoutParams
            layoutParams.width = (parent.width * indicatorWidth).toInt()
            decibelIndicator.layoutParams = layoutParams


            decibelIndicator.setBackgroundColor(color)
        }
    }

    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
            if (isEnabled && hasMicrophonePermission() && noiseManager.hasMicrophone()) {
                if (!noiseManager.isRecording()) {
                    noiseManager.startRecording()
                    handler.post(updateRunnable)
                }
            }
        }
    }

    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            noiseManager.stopRecording()
        }
    }

    fun onPermissionGranted() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
            if (isEnabled) {
                updateUI(true)
            }
        }
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            noiseManager.stopRecording()
            noiseManager.cleanup()
        }
    }
}
