package com.guruswarupa.launch.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.EncryptedFolderManager

abstract class VaultBaseActivity : AppCompatActivity() {
    companion object {
        private var activeVaultActivities = 0
    }

    private var lastInteractionTime: Long = 0
    private val inactiveTimeoutRunnable = Runnable {

        EncryptedFolderManager(this).lock()
        finish()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var screenOffReceiver: BroadcastReceiver? = null

    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeVaultActivities++
        initializeAutoLock()
    }

    private fun initializeAutoLock() {
        updateLastInteractionTime()

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    EncryptedFolderManager(this@VaultBaseActivity).lock()
                    finish()
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun updateLastInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()

        handler.removeCallbacks(inactiveTimeoutRunnable)

        if (isAutoLockEnabled()) {
            val timeoutDuration = getTimeoutDurationMinutes() * 60 * 1000L
            handler.postDelayed(inactiveTimeoutRunnable, timeoutDuration)
        }
    }

    private fun isAutoLockEnabled(): Boolean {
        return prefs.getBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, false)
    }

    private fun getTimeoutDurationMinutes(): Int {
        return prefs.getInt(Constants.Prefs.VAULT_TIMEOUT_DURATION, 1)
    }

    override fun onResume() {
        super.onResume()
        updateLastInteractionTime()
    }

    override fun onPause() {
        super.onPause()

    }

    override fun onDestroy() {
        super.onDestroy()
        activeVaultActivities--

        if (activeVaultActivities <= 0 && isFinishing) {
            EncryptedFolderManager(this).lock()
        }

        handler.removeCallbacks(inactiveTimeoutRunnable)
        screenOffReceiver?.let { unregisterReceiver(it) }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        updateLastInteractionTime()
    }
}
