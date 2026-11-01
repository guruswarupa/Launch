package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ai.llm.AssistantModel
import com.guruswarupa.launch.ai.llm.DeviceCapability
import com.guruswarupa.launch.ai.llm.DeviceCapabilityResult
import com.guruswarupa.launch.ai.llm.ModelDownloadManager
import com.guruswarupa.launch.ai.llm.ModelState
import com.guruswarupa.launch.ai.llm.WebAiProvider
import dagger.hilt.android.AndroidEntryPoint
import com.guruswarupa.launch.managers.DownloadableFontManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.ThemeOption
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.services.WalkDetectionService
import com.guruswarupa.launch.ui.views.SafeHorizontalScrollView
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.IconPackManager
import com.guruswarupa.launch.utils.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.guruswarupa.launch.ui.theme.AccentSwatch
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.ui.theme.ThemePalette

internal fun SettingsActivity.exportSettings() { exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "launch_backup.zip") }) }

internal fun SettingsActivity.importSettings() { importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }) }

internal fun SettingsActivity.exportSettingsToFile(uri: Uri) {
    val density = resources.displayMetrics.density
    val pad = (20 * density).toInt()
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, (8 * density).toInt(), pad, 0)
    }
    val passInput = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = getString(R.string.backup_passphrase_optional)
        DialogStyler.styleInput(this@exportSettingsToFile, this)
    }
    val confirmInput = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = getString(R.string.backup_passphrase_confirm)
        DialogStyler.styleInput(this@exportSettingsToFile, this)
    }
    val vaultAvailable = com.guruswarupa.launch.managers.EncryptedFolderManager(this).isVaultSetup()
    val vaultCheck = CheckBox(this).apply {
        text = getString(R.string.backup_include_vault)
        isChecked = vaultAvailable
        visibility = if (vaultAvailable) View.VISIBLE else View.GONE
        setTextColor(com.guruswarupa.launch.ui.theme.ThemeManager.color(this@exportSettingsToFile, R.attr.appTextPrimary))
    }
    container.addView(passInput)
    container.addView(confirmInput)
    container.addView(vaultCheck)

    val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        .setTitle(R.string.backup_options_title)
        .setMessage(R.string.backup_options_message)
        .setView(container)
        .setPositiveButton(R.string.save_button, null)
        .setNegativeButton(R.string.cancel_button, null)
        .create()
    dialog.show()
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val pass = passInput.text.toString()
        if (pass != confirmInput.text.toString()) {
            Toast.makeText(this, getString(R.string.backup_passphrase_mismatch), Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        if (pass.isNotEmpty() && pass.length < 6) {
            Toast.makeText(this, getString(R.string.backup_passphrase_too_short), Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        dialog.dismiss()
        Toast.makeText(this, getString(R.string.backup_working), Toast.LENGTH_SHORT).show()
        val includeVault = vaultAvailable && vaultCheck.isChecked
        com.guruswarupa.launch.core.BackgroundWork.execute {
            val ok = SettingsBackupHelper.exportToUri(this, prefs, uri, pass.ifEmpty { null }, includeVault)
            runOnUiThread {
                Toast.makeText(this, getString(if (ok) R.string.toast_saved else R.string.toast_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun SettingsActivity.importSettingsFromFile(uri: Uri) {
    com.guruswarupa.launch.core.BackgroundWork.execute {
        val encrypted = com.guruswarupa.launch.core.BackupCrypto.isEncrypted(this, uri)
        runOnUiThread {
            if (encrypted) promptPassphraseAndImport(uri) else runImport(uri, null)
        }
    }
}

internal fun SettingsActivity.promptPassphraseAndImport(uri: Uri) {
    val density = resources.displayMetrics.density
    val input = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = getString(R.string.backup_passphrase_enter)
        DialogStyler.styleInput(this@promptPassphraseAndImport, this)
    }
    val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        .setTitle(R.string.backup_encrypted_title)
        .setView(android.widget.FrameLayout(this).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(input)
        })
        .setPositiveButton(R.string.import_all_data) { _, _ -> runImport(uri, input.text.toString()) }
        .setNegativeButton(R.string.cancel_button, null)
        .create()
    dialog.show()
}

internal fun SettingsActivity.runImport(uri: Uri, passphrase: String?) {
    Toast.makeText(this, getString(R.string.backup_working), Toast.LENGTH_SHORT).show()
    com.guruswarupa.launch.core.BackgroundWork.execute {
        val result = SettingsBackupHelper.importFromUri(this, prefs, uri, passphrase)
        runOnUiThread {
            when (result) {
                SettingsBackupHelper.ImportResult.SUCCESS -> restartLauncher()
                SettingsBackupHelper.ImportResult.WRONG_PASSPHRASE -> {
                    Toast.makeText(this, getString(R.string.backup_wrong_passphrase), Toast.LENGTH_LONG).show()
                    if (passphrase != null) promptPassphraseAndImport(uri)
                }
                SettingsBackupHelper.ImportResult.FAILED ->
                    Toast.makeText(this, getString(R.string.toast_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
