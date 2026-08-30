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
 * Downloads any [AssistantModelInfo] from [AssistantModel.ALL] over Wi-Fi/unmetered only,
 * verifies it byte-for-byte and by SHA-256 before marking it ready, and never touches the
 * network again after that — the assistant runs fully offline from then on. No account, no
 * key, nothing to configure. Every model in the catalog tracks its own download state
 * independently, so switching the selected model doesn't lose progress on another one.
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

    fun modelFile(model: AssistantModelInfo): File = File(modelsDir, model.fileName)

    /** Cheap, prefs-only read — safe to call often (e.g. to decide whether to show the assistant UI). */
    fun currentState(model: AssistantModelInfo): ModelState {
        val raw = rawState(model)
        return if (raw == ModelState.READY && !modelFile(model).exists()) ModelState.NOT_DOWNLOADED else raw
    }

    fun startDownload(model: AssistantModelInfo): Boolean {
        if (currentState(model) == ModelState.DOWNLOADING) return false
        modelFile(model).delete()

        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle(model.displayName)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(modelFile(model)))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)

        val id = downloadManager.enqueue(request)
        sharedPreferences.edit {
            putLong(downloadIdKey(model), id)
            putString(stateKey(model), ModelState.DOWNLOADING.name)
        }
        return true
    }

    /** Progress for [model]'s currently tracked download, or null if none is in flight. */
    fun progress(model: AssistantModelInfo): DownloadProgress? {
        val id = downloadId(model) ?: return null
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(downloaded, if (total > 0) total else model.expectedSizeBytes)
        }
    }

    /**
     * Call periodically (e.g. every second) while [model]'s download is in flight, and once
     * on app/screen start to reconcile a download that finished or failed while the process
     * wasn't running. Kicks off checksum verification in the background on success.
     */
    fun pollAndReconcile(model: AssistantModelInfo): ModelState {
        if (rawState(model) != ModelState.DOWNLOADING) return currentState(model)
        val id = downloadId(model) ?: return markFailed(model)

        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return markFailed(model)
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    setState(model, ModelState.VERIFYING)
                    backgroundExecutor.execute { verifyAndFinalize(model) }
                    ModelState.VERIFYING
                }
                DownloadManager.STATUS_FAILED -> markFailed(model)
                else -> ModelState.DOWNLOADING
            }
        }
    }

    fun deleteModel(model: AssistantModelInfo) {
        downloadId(model)?.let { downloadManager.remove(it) }
        modelFile(model).delete()
        sharedPreferences.edit {
            remove(downloadIdKey(model))
            putString(stateKey(model), ModelState.NOT_DOWNLOADED.name)
        }
    }

    private fun verifyAndFinalize(model: AssistantModelInfo) {
        val file = modelFile(model)
        if (!file.exists() || file.length() != model.expectedSizeBytes) {
            file.delete()
            markFailed(model)
            return
        }
        if (!sha256Of(file).equals(model.expectedSha256, ignoreCase = true)) {
            file.delete()
            markFailed(model)
            return
        }
        setState(model, ModelState.READY)
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

    private fun stateKey(model: AssistantModelInfo) = Constants.Prefs.AI_ASSISTANT_MODEL_STATE_PREFIX + model.id
    private fun downloadIdKey(model: AssistantModelInfo) = Constants.Prefs.AI_ASSISTANT_DOWNLOAD_ID_PREFIX + model.id

    private fun downloadId(model: AssistantModelInfo): Long? =
        sharedPreferences.getLong(downloadIdKey(model), -1L).takeIf { it != -1L }

    private fun rawState(model: AssistantModelInfo): ModelState =
        sharedPreferences.getString(stateKey(model), null)
            ?.let { runCatching { ModelState.valueOf(it) }.getOrNull() }
            ?: ModelState.NOT_DOWNLOADED

    private fun setState(model: AssistantModelInfo, state: ModelState) {
        sharedPreferences.edit { putString(stateKey(model), state.name) }
    }

    private fun markFailed(model: AssistantModelInfo): ModelState {
        setState(model, ModelState.FAILED)
        return ModelState.FAILED
    }
}
