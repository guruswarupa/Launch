package com.guruswarupa.launch.ai.llm

import android.content.Context
import android.content.SharedPreferences
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.guruswarupa.launch.models.Constants
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

@Singleton
class OnDeviceAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val modelDownloadManager: ModelDownloadManager
) {
    companion object {
        private const val MAX_TOKENS = 512
    }

    private val lock = Mutex()
    private var engine: LlmInference? = null
    private var loadedModelId: String? = null

    fun selectedModel(): AssistantModelInfo =
        AssistantModel.byId(sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_MODEL_ID, null))

    val isModelReady: Boolean get() = modelDownloadManager.currentState(selectedModel()) == ModelState.READY

    suspend fun ask(prompt: String): AssistantResult = withContext(Dispatchers.IO) {
        val model = selectedModel()
        if (modelDownloadManager.currentState(model) != ModelState.READY) {
            return@withContext AssistantResult.Error("Model not downloaded")
        }

        lock.withLock {
            try {
                if (loadedModelId != null && loadedModelId != model.id) {
                    releaseLocked()
                }
                val activeEngine = engine ?: createEngine(model).also {
                    engine = it
                    loadedModelId = model.id
                }
                AssistantResult.Success(activeEngine.generateResponse(prompt))
            } catch (e: Exception) {

                releaseLocked()
                AssistantResult.Error(e.message ?: "The on-device assistant failed to respond")
            }
        }
    }

    fun release() {

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
        loadedModelId = null
    }

    @Suppress("DEPRECATION")
    private fun createEngine(model: AssistantModelInfo): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelDownloadManager.modelFile(model).absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options)
    }
}
