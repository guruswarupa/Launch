package com.guruswarupa.launch.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageManager(private val context: Context) {

    fun getSecurePrefs(fileName: String): SharedPreferences {
        return try {
            createEncryptedPrefs(fileName)
        } catch (_: Exception) {

            deletePrefsFile(fileName)
            createEncryptedPrefs(fileName)
        }
    }

    private fun createEncryptedPrefs(fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deletePrefsFile(fileName: String) {
        try {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()
            val prefsDir = context.filesDir.parentFile?.resolve("shared_prefs")
            prefsDir?.resolve("$fileName.xml")?.delete()
        } catch (_: Exception) {

        }
    }

    companion object {
        const val GITHUB_PREFS = "secure_github_prefs"
        const val FINANCE_PREFS = "secure_finance_prefs"
    }
}
