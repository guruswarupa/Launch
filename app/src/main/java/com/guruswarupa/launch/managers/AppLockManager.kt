package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.core.SecureStorageManager
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLockManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        SecureStorageManager(context).getSecurePrefs("app_lock_prefs")
    }

    companion object {
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_PIN_SALT = "pin_salt"
        private const val PREF_LOCKED_APPS = "locked_apps"
        private const val PREF_IS_APP_LOCK_ENABLED = "is_app_lock_enabled"
        private const val PREF_LAST_AUTH_TIME = "last_auth_time"
        private const val PREF_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val AUTH_TIMEOUT = 1 * 60 * 1000L
        private const val SALT_LENGTH_BYTES = 16
        private const val PIN_HASH_ITERATIONS = 310000
        private const val PIN_HASH_KEY_LENGTH = 256
    }

    private fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PIN_HASH_ITERATIONS, PIN_HASH_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String): String {
        val existingSalt = sharedPreferences.getString(PREF_PIN_SALT, null)
        return if (existingSalt != null) {
            hashPinWithSalt(pin, existingSalt)
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    private fun saveNewPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        sharedPreferences.edit {
            putString(PREF_PIN_SALT, salt)
            putString(PREF_PIN_HASH, hash)
        }
    }


    fun setupPin(callback: (Boolean) -> Unit) {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.app_lock_hint_enter_pin_range)
            DialogStyler.styleInput(context, this)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(R.string.app_lock_title_set_pin)
            .setMessage(R.string.app_lock_message_choose_pin)
            .setDialogInputView(context, pinInput)
            .setPositiveButton(R.string.app_lock_action_set_pin) { _, _ ->
                val pin = pinInput.text.toString()
                if (pin.length in 4..6 && pin.all { it.isDigit() }) {
                    val confirmInput = EditText(context).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        hint = context.getString(R.string.app_lock_hint_confirm_pin)
                        DialogStyler.styleInput(context, this)
                    }

                    AlertDialog.Builder(context, R.style.CustomDialogTheme)
                        .setTitle(R.string.app_lock_title_confirm_pin)
                        .setDialogInputView(context, confirmInput)
                        .setPositiveButton(R.string.app_lock_action_confirm) { _, _ ->
                            val confirmPin = confirmInput.text.toString()
                            if (pin == confirmPin) {
                                saveNewPin(pin)
                                sharedPreferences.edit {
                                    putBoolean(PREF_IS_APP_LOCK_ENABLED, true)
                                }
                                Toast.makeText(context, R.string.app_lock_pin_set_success, Toast.LENGTH_SHORT).show()
                                callback(true)
                            } else {
                                Toast.makeText(context, R.string.app_lock_pins_do_not_match, Toast.LENGTH_SHORT).show()
                                callback(false)
                            }
                        }
                        .setNegativeButton(R.string.cancel_button) { _, _ -> callback(false) }
                        .show()
                } else {
                    Toast.makeText(context, R.string.app_lock_pin_length_error, Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ -> callback(false) }
            .show()
    }


    fun verifyPin(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {
            callback(true)
            return
        }


        val lastAuthTime = sharedPreferences.getLong(PREF_LAST_AUTH_TIME, 0)
        if (System.currentTimeMillis() - lastAuthTime < AUTH_TIMEOUT) {
            callback(true)
            return
        }


        if (isFingerprintEnabled()) {
            showBiometricPrompt(callback)
        } else {
            showPinPrompt(callback)
        }
    }

    private fun showBiometricPrompt(callback: (Boolean) -> Unit) {

        val activity = context as? FragmentActivity
        if (activity == null) {
            Toast.makeText(context, R.string.app_lock_biometric_unavailable_context, Toast.LENGTH_SHORT).show()
            showPinPrompt(callback)
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_biometric_title))
            .setDescription(context.getString(R.string.app_lock_biometric_description))
            .setNegativeButtonText(context.getString(R.string.app_lock_action_use_pin))
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    sharedPreferences.edit {
                        putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                    }
                    callback(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context, R.string.app_lock_biometric_failed, Toast.LENGTH_SHORT).show()

                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {

                        showPinPrompt(callback)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.app_lock_biometric_error, errString),
                            Toast.LENGTH_SHORT
                        ).show()
                        callback(false)
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showPinPrompt(callback: (Boolean) -> Unit) {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.app_lock_hint_enter_pin)
            DialogStyler.styleInput(context, this)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(R.string.app_lock_title_enter_pin)
            .setMessage(R.string.app_lock_message_unlock)
            .setDialogInputView(context, pinInput)
            .setPositiveButton(R.string.app_lock_action_unlock) { _, _ ->
                val enteredPin = pinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(enteredPin) == storedPinHash) {

                    sharedPreferences.edit {
                        putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                    }
                    callback(true)
                } else {
                    Toast.makeText(context, R.string.app_lock_incorrect_pin, Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ -> callback(false) }
            .setCancelable(false)
            .show()
    }


    fun isPinSet(): Boolean {
        return sharedPreferences.getString(PREF_PIN_HASH, null)?.isNotEmpty() == true
    }


    fun isFingerprintAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }


    fun isFingerprintEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_FINGERPRINT_ENABLED, false) && isFingerprintAvailable()
    }


    fun setFingerprintEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_FINGERPRINT_ENABLED, enabled) }
    }


    fun isAppLockEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_APP_LOCK_ENABLED, false) && isPinSet()
    }


    fun setAppLockEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_IS_APP_LOCK_ENABLED, enabled) }
    }


    fun lockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.add(packageName)
        sharedPreferences.edit { putStringSet(PREF_LOCKED_APPS, lockedApps) }
    }


    fun unlockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.remove(packageName)
        sharedPreferences.edit { putStringSet(PREF_LOCKED_APPS, lockedApps) }
    }


    fun getLockedApps(): Set<String> {
        return sharedPreferences.getStringSet(PREF_LOCKED_APPS, emptySet()) ?: emptySet()
    }


    fun isAppLocked(packageName: String): Boolean {
        return isAppLockEnabled() && getLockedApps().contains(packageName)
    }


    fun changePin(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {
            setupPin(callback)
            return
        }

        val oldPinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.app_lock_hint_enter_current_pin)
            DialogStyler.styleInput(context, this)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(R.string.app_lock_title_change_pin)
            .setMessage(R.string.app_lock_message_enter_current_pin)
            .setDialogInputView(context, oldPinInput)
            .setPositiveButton(R.string.app_lock_action_continue) { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(oldPin) == storedPinHash) {
                    setupPin(callback)
                } else {
                    Toast.makeText(context, R.string.app_lock_incorrect_current_pin, Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ -> callback(false) }
            .show()
    }


    fun resetAppLock(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {

            resetAppLockData()
            callback(true)
            return
        }

        val oldPinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.app_lock_hint_enter_current_pin)
            DialogStyler.styleInput(context, this)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(R.string.app_lock_title_reset)
            .setMessage(R.string.app_lock_message_reset)
            .setDialogInputView(context, oldPinInput)
            .setPositiveButton(R.string.app_lock_action_continue) { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(oldPin) == storedPinHash) {

                    resetAppLockData()
                    Toast.makeText(context, R.string.app_lock_reset_success, Toast.LENGTH_SHORT).show()
                    callback(true)
                } else {
                    Toast.makeText(context, R.string.app_lock_incorrect_current_pin, Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ -> callback(false) }
            .show()
    }

    private fun resetAppLockData() {
        sharedPreferences.edit {
            remove(PREF_PIN_HASH)
            remove(PREF_PIN_SALT)
            remove(PREF_LOCKED_APPS)
            remove(PREF_IS_APP_LOCK_ENABLED)
            remove(PREF_LAST_AUTH_TIME)
            remove(PREF_FINGERPRINT_ENABLED)
        }
    }


    fun clearAuthTimeout() {
        sharedPreferences.edit { remove(PREF_LAST_AUTH_TIME) }
    }
}
