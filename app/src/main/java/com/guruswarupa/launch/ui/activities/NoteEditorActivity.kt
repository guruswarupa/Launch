package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import java.io.File


class NoteEditorActivity : VaultBaseActivity() {
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var vaultManager: EncryptedFolderManager
    private lateinit var wallpaperBackground: ImageView
    private lateinit var wallpaperOverlay: View
    private lateinit var headerTitle: TextView
    private var isEditing = false
    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        wallpaperOverlay = findViewById(R.id.note_editor_overlay)
        headerTitle = findViewById(R.id.title_text)
        setupWallpaper()

        vaultManager = EncryptedFolderManager(this)



        if (!vaultManager.isUnlocked()) {
            Toast.makeText(this, this.getString(R.string.toast_vault_is_locked), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        noteTitle = findViewById(R.id.note_title)
        noteContent = findViewById(R.id.note_content)

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.save_button).setOnClickListener {
            saveNote()
        }

        fileName = intent.getStringExtra("FILE_NAME")
        isEditing = !fileName.isNullOrEmpty()
        updateHeaderTitle()

        if (isEditing) {
            headerTitle.setOnClickListener { showRenameDialog() }
            loadExistingNote()
        } else {
            headerTitle.setOnClickListener(null)
        }
    }

    private fun setupWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground)

        val overlayColorRes = R.color.note_editor_overlay
        wallpaperOverlay.setBackgroundColor(ContextCompat.getColor(this, overlayColorRes))
    }

    private fun updateHeaderTitle() {
        headerTitle.text = fileName ?: "New Note"
    }

    private fun showRenameDialog() {
        val currentName = fileName ?: return
        val editText = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            DialogStyler.styleInput(this@NoteEditorActivity, this)
        }

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dlg_rename_file))
            .setView(editText, dp(20), dp(12), dp(20), 0)
            .setPositiveButton(getString(R.string.dlg_rename)) { _, _ ->
                val candidate = editText.text.toString()
                val sanitized = sanitizeFileName(candidate)
                if (sanitized.isBlank()) {
                    Toast.makeText(this, this.getString(R.string.toast_file_name_cannot_be_empty), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (sanitized == currentName) return@setPositiveButton

                val renamed = vaultManager.renameFile(currentName, sanitized)
                if (renamed) {
                    fileName = sanitized
                    updateHeaderTitle()
                    Toast.makeText(this, this.getString(R.string.toast_renamed_to, sanitized), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, this.getString(R.string.toast_unable_to_rename_file), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun sanitizeFileName(input: String): String {
        var name = input.trim().replace(Regex("[/\\\\]"), "_")
        if (name.isEmpty()) return ""
        if (!name.contains('.')) {
            name += ".txt"
        }
        return name
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadExistingNote() {
        val fileNameToLoad = fileName
        if (fileNameToLoad.isNullOrBlank()) {
            Toast.makeText(this, this.getString(R.string.toast_invalid_note_file), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        var tempFile: File? = null
        try {
            tempFile = vaultManager.decryptToCache(fileNameToLoad)
            val contentStr = tempFile.readText(Charsets.UTF_8)

            val parts = contentStr.split("\n\n", limit = 2)
            if (parts.size >= 2) {
                noteTitle.setText(parts[0])
                noteContent.setText(parts[1])
            } else {
                noteTitle.setText("")
                noteContent.setText(contentStr)
            }
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_load_note, e.message), Toast.LENGTH_SHORT).show()
        } finally {
            tempFile?.delete()
        }
    }

    private fun saveNote() {
        val title = noteTitle.text.toString().trim()
        val content = noteContent.text.toString()

        if (content.isEmpty()) {
            Toast.makeText(this, this.getString(R.string.toast_cannot_save_empty_note), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val noteData = if (title.isNotEmpty()) "$title\n\n$content" else "Untitled Note\n\n$content"
            val fileNameToUse = if (isEditing) {
                val existingFileName = fileName
                if (existingFileName.isNullOrBlank()) {
                    Toast.makeText(this, this.getString(R.string.toast_invalid_note_file), Toast.LENGTH_SHORT).show()
                    return
                }
                existingFileName
            } else {
                "note_${System.currentTimeMillis()}.txt"
            }

            val tempFile = File.createTempFile("note_", ".txt", cacheDir)
            try {
                tempFile.writeText(noteData, Charsets.UTF_8)
                vaultManager.encryptFile(Uri.fromFile(tempFile), fileNameToUse)
            } finally {
                tempFile.delete()
            }

            Toast.makeText(this, this.getString(R.string.toast_note_saved), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_save_note, e.message), Toast.LENGTH_LONG).show()
        }
    }
}
