package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import java.io.File
import java.text.DecimalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.guruswarupa.launch.ui.theme.ThemeManager

class EncryptedVaultActivity : VaultBaseActivity() {
    private lateinit var vaultManager: EncryptedFolderManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: VaultAdapter
    private var fileToDecrypt: File? = null

    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { encryptAndMoveFile(it) }
    }

    private val createNoteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFiles()
        }
    }

    private val decryptResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                fileToDecrypt?.let { file ->
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    vaultManager.decryptToOutputStream(file.name, outputStream)
                                } ?: throw IllegalStateException("Unable to open destination")
                            }
                            Toast.makeText(this@EncryptedVaultActivity, this@EncryptedVaultActivity.getString(R.string.toast_file_decrypted_and_saved), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@EncryptedVaultActivity, this@EncryptedVaultActivity.getString(R.string.toast_decryption_failed, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private val exportVaultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        if (vaultManager.exportVault(outputStream)) {
                            Toast.makeText(this, this.getString(R.string.toast_vault_exported_successfully), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, this.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, this.getString(R.string.toast_export_failed_2, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importVaultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { inputStream ->
                    if (vaultManager.importVault(inputStream)) {
                        Toast.makeText(this, this.getString(R.string.toast_vault_imported_successfully_please_unlock_to_see), Toast.LENGTH_SHORT).show()
                        loadFiles()
                    } else {
                        Toast.makeText(this, this.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, this.getString(R.string.toast_import_failed_2, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_encrypted_vault)

        makeSystemBarsTransparent()
        setupWallpaper()
        applyBackgroundTranslucency()

        vaultManager = EncryptedFolderManager(this)
        recyclerView = findViewById(R.id.vault_recycler_view)
        emptyStateText = findViewById(R.id.empty_state_text)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        adapter = VaultAdapter(vaultManager, emptyList(),
            onFileClick = { file -> openFile(file) },
            onFileLongClick = { file -> showFileOptions(file) }
        )
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.settings_button).setOnClickListener {
            showVaultSettings()
        }

        findViewById<FloatingActionButton>(R.id.add_file_fab).setOnClickListener {
            showAddFileOptions()
        }
    }

    override fun onStart() {
        super.onStart()
        checkVaultState()
    }

    private fun checkVaultState() {
        if (!vaultManager.isVaultSetup()) {
            showSetupDialog()
        } else if (!vaultManager.isUnlocked()) {
            showUnlockDialog()
        } else {
            loadFiles()
        }
    }

    private fun showSetupDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter New Vault Password"
            DialogStyler.styleInput(this@EncryptedVaultActivity, this)
        }
        val horizontalInset = dp(20)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_setup_vault))
            .setMessage(getString(R.string.dlg_set_a_password_to_encrypt_your_vault_remember_it))
            .setView(input, horizontalInset, 0, horizontalInset, 0)
            .setPositiveButton(getString(R.string.dlg_set_password)) { _, _ ->
                val password = input.text.toString()
                if (password.length >= 4) {
                    showConfirmPasswordDialog(password)
                } else {
                    Toast.makeText(this, this.getString(R.string.toast_password_must_be_at_least_4_characters), Toast.LENGTH_SHORT).show()
                    showSetupDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button)) { _, _ -> finish() }
            .setNeutralButton(getString(R.string.dlg_import_existng)) { _, _ -> importVaultLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            .setCancelable(false)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showConfirmPasswordDialog(password: String) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Confirm Vault Password"
            DialogStyler.styleInput(this@EncryptedVaultActivity, this)
        }
        val horizontalInset = dp(20)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_confirm_password))
            .setMessage(getString(R.string.dlg_please_re_enter_your_password_to_confirm))
            .setView(input, horizontalInset, 0, horizontalInset, 0)
            .setPositiveButton(getString(R.string.app_lock_action_confirm)) { _, _ ->
                val confirmedPassword = input.text.toString()
                if (password == confirmedPassword) {
                    if (vaultManager.setupVault(password)) {
                        loadFiles()
                    } else {
                        Toast.makeText(this, this.getString(R.string.toast_setup_failed), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, this.getString(R.string.toast_passwords_do_not_match_start_over), Toast.LENGTH_SHORT).show()
                    showSetupDialog()
                }
            }
            .setNegativeButton(getString(R.string.back_button)) { _, _ -> showSetupDialog() }
            .setCancelable(false)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showUnlockDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Vault Password"
            DialogStyler.styleInput(this@EncryptedVaultActivity, this)
        }
        val horizontalInset = dp(20)
        val topInset = dp(12)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_vault_locked))
            .setView(input, horizontalInset, topInset, horizontalInset, 0)
            .setPositiveButton(getString(R.string.app_lock_action_unlock)) { _, _ ->
                val password = input.text.toString()
                if (vaultManager.unlock(password)) {
                    loadFiles()
                } else {
                    Toast.makeText(this, this.getString(R.string.toast_incorrect_password), Toast.LENGTH_SHORT).show()
                    showUnlockDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button)) { _, _ -> finish() }
            .setCancelable(false)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun makeSystemBarsTransparent() {
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        val overlay = findViewById<View>(R.id.settings_overlay)
        overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.settings_overlay))

        WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
        applyWallpaperBlur(wallpaperImageView)
    }

    private fun applyWallpaperBlur(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageView.setRenderEffect(null)
        }
    }

    private fun loadFiles() {
        if (!vaultManager.isUnlocked()) {
            showUnlockDialog()
            return
        }
        val files = vaultManager.getEncryptedFiles()
        adapter.updateFiles(files)
        updateEmptyState(files.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun encryptAndMoveFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "encrypted_file_${System.currentTimeMillis()}"
        try {
            vaultManager.encryptFile(uri, fileName)
            var deleted = false
            if (uri.scheme == "file") {
                uri.path?.let { deleted = File(it).delete() }
            } else if (uri.scheme == "content") {
                try {
                    if (DocumentsContract.isDocumentUri(this, uri)) {
                        deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                    }
                } catch (e: Exception) {}
            }
            loadFiles()
            Toast.makeText(this, if (deleted) getString(R.string.toast_file_moved_to_vault) else getString(R.string.toast_encrypted_delete_manually), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_encryption_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun openFile(file: File) {
        try {
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            
            if (mimeType.startsWith("text/") || file.name.endsWith(".txt")) {
                val intent = Intent(this, NoteEditorActivity::class.java).apply {
                    putExtra("FILE_NAME", file.name)
                }
                createNoteLauncher.launch(intent)
            } else if (DocumentViewerActivity.isSupported(file.name)) {
                // Open in built-in document viewer
                val intent = DocumentViewerActivity.createVaultIntent(this, file.name)
                startActivity(intent)
            } else {
                lifecycleScope.launch {
                    try {
                        val tempFile = withContext(Dispatchers.IO) {
                            vaultManager.decryptToCache(file.name)
                        }
                        val uri = FileProvider.getUriForFile(
                            this@EncryptedVaultActivity,
                            "${packageName}.fileprovider",
                            tempFile
                        )
                        val actualMimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, actualMimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(Intent.createChooser(intent, "Open with..."))
                    } catch (e: Exception) {
                        Toast.makeText(this@EncryptedVaultActivity, this@EncryptedVaultActivity.getString(R.string.toast_failed_to_open_file, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_open_file, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileOptions(file: File) {
        val options = arrayOf("Open", "Decrypt & Save", "Delete")
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile(file)
                    1 -> decryptFile(file)
                    2 -> deleteFile(file)
                }
            }.create()
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun decryptFile(file: File) {
        fileToDecrypt = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        decryptResultLauncher.launch(intent)
    }

    private fun deleteFile(file: File) {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_delete_file))
            .setMessage(getString(R.string.dlg_delete_from_vault, file.name))
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                if (vaultManager.deleteFile(file.name)) {
                    loadFiles()
                    Toast.makeText(this, this.getString(R.string.toast_file_deleted), Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(getString(R.string.cancel_button), null).create()
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }
    
    private fun showAddFileOptions() {
        val options = arrayOf("Add File", "Create Note")
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_add_to_vault))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFile.launch(arrayOf("*/*"))
                    1 -> {
                        val intent = Intent(this, NoteEditorActivity::class.java)
                        createNoteLauncher.launch(intent)
                    }
                }
            }.create()
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showVaultSettings() {
        val options = arrayOf("Export Vault", "Import Vault", "Change Password", "Auto-Lock Settings")
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_vault_settings))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/zip"
                            putExtra(Intent.EXTRA_TITLE, "vault_backup_${System.currentTimeMillis()}.zip")
                        }
                        exportVaultLauncher.launch(intent)
                    }
                    1 -> importVaultLauncher.launch(arrayOf("application/zip", "*/*"))
                    2 -> showSetupDialog()
                    3 -> showAutoLockDialog()
                }
            }.create()
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showAutoLockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vault_settings, null)
        val timeoutSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.timeout_switch)
        val recoveryBtn = dialogView.findViewById<Button>(R.id.view_recovery_phrase_button)

        recoveryBtn.visibility = View.GONE

        timeoutSwitch.isChecked = prefs.getBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, false)

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_auto_lock_settings))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_button)) { _, _ ->
                prefs.edit()
                    .putBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, timeoutSwitch.isChecked)
                    .putInt(Constants.Prefs.VAULT_TIMEOUT_DURATION, 1)
                    .apply()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        vaultManager.clearCache()
    }

    class VaultAdapter(
        private val vaultManager: EncryptedFolderManager,
        private var files: List<File>,
        private val onFileClick: (File) -> Unit,
        private val onFileLongClick: (File) -> Unit
    ) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.file_thumbnail)
            val name: TextView = view.findViewById(R.id.file_name)
            val info: TextView = view.findViewById(R.id.file_info)
            val typeOverlay: ImageView = view.findViewById(R.id.file_type_overlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.name.text = file.name
            holder.info.text = formatFileSize(file.length())


            holder.thumbnail.setImageDrawable(null)

            val bitmap = vaultManager.getThumbnail(file.name)
            if (bitmap != null && !bitmap.isRecycled) {
                holder.thumbnail.setImageBitmap(bitmap)
                holder.thumbnail.imageTintList = null
                holder.thumbnail.alpha = 1.0f
            } else {
                val extension = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
                when {
                    mimeType.startsWith("text/") || file.name.endsWith(".txt") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_note)
                        holder.thumbnail.imageTintList = android.content.res.ColorStateList.valueOf(ThemeManager.color(holder.itemView.context, R.attr.appTextPrimary))
                        holder.thumbnail.alpha = 1.0f
                    }
                    extension == "pdf" || mimeType.contains("pdf") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_pdf)
                        holder.thumbnail.imageTintList = android.content.res.ColorStateList.valueOf(ThemeManager.color(holder.itemView.context, R.attr.appError))
                        holder.thumbnail.alpha = 1.0f
                    }
                    else -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_file)
                        holder.thumbnail.imageTintList = android.content.res.ColorStateList.valueOf(ThemeManager.color(holder.itemView.context, R.attr.appTextPrimary))
                        holder.thumbnail.alpha = 0.3f
                    }
                }
            }
            holder.itemView.setOnClickListener { onFileClick(file) }
            holder.itemView.setOnLongClickListener { onFileLongClick(file); true }
        }

        override fun getItemCount() = files.size
        fun updateFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }
        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
                .coerceIn(0, units.size - 1)
            return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}
