package com.guruswarupa.launch.ai.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AssistantResult {
    data class Success(val text: String) : AssistantResult
    data class Error(val message: String) : AssistantResult
}

/**
 * Lazily loads [AssistantModel] into memory on first question and holds it resident for
 * follow-up questions. A ~500MB model sitting in memory is exactly the kind of thing that
 * gets a home-screen launcher's process killed to reclaim RAM, so [release] must be called
 * whenever the launcher backgrounds or the system asks for memory back — see
 * MainActivity's onStop/onTrimMemory.
 *
 * Deprecation note: [LlmInference] (tasks-genai) is in MediaPipe's maintenance-only mode in
 * favor of LiteRT-LM, but LiteRT-LM has no `.litertlm` build of our chosen ungated model
 * (see [AssistantModel]) — only `.task`, which tasks-genai consumes directly. Swap this file
 * if/when that changes; nothing outside `ai/llm/` depends on which runtime is used.
 */
@Singleton
class OnDeviceAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) {
    companion object {
        private const val MAX_TOKENS = 512
    }

    private val lock = Mutex()
    private var engine: LlmInference? = null

    val isModelReady: Boolean get() = modelDownloadManager.currentState() == ModelState.READY

    suspend fun ask(prompt: String): AssistantResult = withContext(Dispatchers.IO) {
        if (!isModelReady) return@withContext AssistantResult.Error("Model not downloaded")

        lock.withLock {
            try {
                val activeEngine = engine ?: createEngine().also { engine = it }
                AssistantResult.Success(activeEngine.generateResponse(prompt))
            } catch (e: Exception) {
                // A corrupt load or an engine wedged by a previous failure is not worth
                // retrying with the same instance — drop it and let the next ask() reload.
                releaseLocked()
                AssistantResult.Error(e.message ?: "The on-device assistant failed to respond")
            }
        }
    }

    fun release() {
        // Mutex.withLock requires a suspend context; tryLock covers the sync call sites
        // (onStop/onTrimMemory) and simply skips if a generation is genuinely in flight —
        // that ask() call will still finish and its own catch block will not re-populate
        // a stale engine since it always reads through the `engine` field.
        if (lock.tryLock()) {
            try {
                releaseLocked()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun releaseLocked() {
        engine?.close()
        engine = null
    }

    @Suppress("DEPRECATION")
    private fun createEngine(): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelDownloadManager.modelFile().absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options)
    }
}
