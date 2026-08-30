package com.guruswarupa.launch.ai.llm

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.guruswarupa.launch.di.BackgroundExecutor
import com.guruswarupa.launch.models.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, VERIFYING, READY, FAILED }

data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads [AssistantModel] over Wi-Fi/unmetered only, verifies it byte-for-byte and by
 * SHA-256 before marking it ready, and never touches the network again after that — the
 * assistant runs fully offline from then on. No account, no key, nothing to configure.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    @BackgroundExecutor private val backgroundExecutor: ExecutorService
) {
    private val downloadManager: DownloadManager
        get() = context.getSystemService<DownloadManager>()
            ?: throw IllegalStateException("DownloadManager unavailable")

    private val modelsDir: File by lazy {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "ai_models").apply { mkdirs() }
    }

    fun modelFile(): File = File(modelsDir, AssistantModel.FILE_NAME)

    /** Cheap, prefs-only read — safe to call often (e.g. to decide whether to show the assistant UI). */
    fun currentState(): ModelState {
        val raw = rawState()
        return if (raw == ModelState.READY && !modelFile().exists()) ModelState.NOT_DOWNLOADED else raw
    }

    fun startDownload(): Boolean {
        if (currentState() == ModelState.DOWNLOADING) return false
        modelFile().delete()

        val request = DownloadManager.Request(Uri.parse(AssistantModel.DOWNLOAD_URL))
            .setTitle(AssistantModel.DISPLAY_NAME)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(modelFile()))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)

        val id = downloadManager.enqueue(request)
        sharedPreferences.edit {
            putLong(Constants.Prefs.AI_ASSISTANT_DOWNLOAD_ID, id)
            putString(Constants.Prefs.AI_ASSISTANT_MODEL_STATE, ModelState.DOWNLOADING.name)
        }
        return true
    }

    /** Progress for the currently tracked download, or null if none is in flight. */
    fun progress(): DownloadProgress? {
        val id = downloadId() ?: return null
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(downloaded, if (total > 0) total else AssistantModel.EXPECTED_SIZE_BYTES)
        }
    }

    /**
     * Call periodically (e.g. every second) while a download is in flight, and once on
     * app/screen start to reconcile a download that finished or failed while the process
     * wasn't running. Kicks off checksum verification in the background on success.
     */
    fun pollAndReconcile(): ModelState {
        if (rawState() != ModelState.DOWNLOADING) return currentState()
        val id = downloadId() ?: return markFailed()

        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return markFailed()
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    setState(ModelState.VERIFYING)
                    backgroundExecutor.execute { verifyAndFinalize() }
                    ModelState.VERIFYING
                }
                DownloadManager.STATUS_FAILED -> markFailed()
                else -> ModelState.DOWNLOADING
            }
        }
    }

    fun deleteModel() {
        downloadId()?.let { downloadManager.remove(it) }
        modelFile().delete()
        sharedPreferences.edit {
            remove(Constants.Prefs.AI_ASSISTANT_DOWNLOAD_ID)
            putString(Constants.Prefs.AI_ASSISTANT_MODEL_STATE, ModelState.NOT_DOWNLOADED.name)
        }
    }

    private fun verifyAndFinalize() {
        val file = modelFile()
        if (!file.exists() || file.length() != AssistantModel.EXPECTED_SIZE_BYTES) {
            file.delete()
            markFailed()
            return
        }
        if (!sha256Of(file).equals(AssistantModel.EXPECTED_SHA256, ignoreCase = true)) {
            file.delete()
            markFailed()
            return
        }
        setState(ModelState.READY)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadId(): Long? =
        sharedPreferences.getLong(Constants.Prefs.AI_ASSISTANT_DOWNLOAD_ID, -1L).takeIf { it != -1L }

    private fun rawState(): ModelState =
        sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_MODEL_STATE, null)
            ?.let { runCatching { ModelState.valueOf(it) }.getOrNull() }
            ?: ModelState.NOT_DOWNLOADED

    private fun setState(state: ModelState) {
        sharedPreferences.edit { putString(Constants.Prefs.AI_ASSISTANT_MODEL_STATE, state.name) }
    }

    private fun markFailed(): ModelState {
        setState(ModelState.FAILED)
        return ModelState.FAILED
    }
}
