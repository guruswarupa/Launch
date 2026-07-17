package com.guruswarupa.launch.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageManager(private val context: Context) {

    fun getSecurePrefs(fileName: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails (e.g., on some older devices or during key issues)
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
    }

    companion object {
        const val GITHUB_PREFS = "secure_github_prefs"
        const val FINANCE_PREFS = "secure_finance_prefs"
    }
}
