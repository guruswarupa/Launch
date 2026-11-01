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

internal fun SettingsActivity.selectedModelId(): String =
    prefs.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_MODEL_ID, null) ?: AssistantModel.DEFAULT.id

internal fun SettingsActivity.isWebSourceSelected(): Boolean =
    prefs.getString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_ON_DEVICE) ==
        Constants.Prefs.AI_ASSISTANT_SOURCE_WEB

internal fun SettingsActivity.selectedWebProviderId(): String? =
    prefs.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, null)

internal fun SettingsActivity.setupAiAssistant() {
    val sw = findViewById<SwitchCompat>(R.id.ai_assistant_switch)
    val statusText = findViewById<TextView>(R.id.ai_assistant_status_text)
    val licenseText = findViewById<TextView>(R.id.ai_assistant_license_text)

    licenseText.text = getString(R.string.ai_assistant_license_notice)

    sw.isChecked = prefs.getBoolean(Constants.Prefs.AI_ASSISTANT_ENABLED, false)
    sw.setOnCheckedChangeListener { _, isChecked ->
        prefs.edit { putBoolean(Constants.Prefs.AI_ASSISTANT_ENABLED, isChecked) }
        notifySettingsChanged()
    }

    val modelListLabel = findViewById<View>(R.id.ai_assistant_choose_model_label)
    val modelList = findViewById<LinearLayout>(R.id.ai_assistant_model_list)
    val capability = deviceCapability.check()
    if (capability is DeviceCapabilityResult.Unsupported) {
        modelListLabel.isVisible = false
        modelList.isVisible = false
        statusText.isVisible = true
        statusText.text = when (capability.reason) {
            DeviceCapabilityResult.Reason.LOW_RAM -> getString(R.string.ai_assistant_unsupported_ram)
            DeviceCapabilityResult.Reason.UNSUPPORTED_ABI -> getString(R.string.ai_assistant_unsupported_abi)
            DeviceCapabilityResult.Reason.LOW_STORAGE -> getString(R.string.ai_assistant_unsupported_storage)
        }
    } else {
        statusText.isVisible = false
        setupOnDeviceModelList(modelList)
    }

    setupWebProviderList()
}

internal fun SettingsActivity.setupOnDeviceModelList(modelList: LinearLayout) {
    modelList.removeAllViews()
    aiModelRows.clear()
    AssistantModel.ALL.forEach { model ->
        val rowView = layoutInflater.inflate(R.layout.item_ai_model_row, modelList, false)
        val row = SettingsActivity.ModelRowViews(
            root = rowView,
            name = rowView.findViewById(R.id.model_row_name),
            badge = rowView.findViewById(R.id.model_row_selected_badge),
            description = rowView.findViewById(R.id.model_row_description),
            status = rowView.findViewById(R.id.model_row_status),
            progress = rowView.findViewById(R.id.model_row_progress),
            actionButton = rowView.findViewById(R.id.model_row_action_button)
        )
        row.name.text = model.displayName
        row.description.text = model.description
        aiModelRows[model.id] = row
        modelList.addView(rowView)

        row.root.setOnClickListener {
            if (modelDownloadManager.currentState(model) == ModelState.READY) {
                selectOnDeviceModel(model.id)
            }
        }
        row.actionButton.setOnClickListener {
            when (modelDownloadManager.currentState(model)) {
                ModelState.NOT_DOWNLOADED, ModelState.FAILED -> {
                    if (isAnyAiModelDownloading()) return@setOnClickListener
                    if (modelDownloadManager.startDownload(model)) {
                        refreshAllAiModelRows()
                        startAiAssistantPolling()
                    }
                }
                ModelState.READY -> {
                    if (!isWebSourceSelected() && model.id == selectedModelId()) {
                        modelDownloadManager.deleteModel(model)
                        refreshAllAiModelRows()
                        notifySettingsChanged()
                    } else {
                        selectOnDeviceModel(model.id)
                    }
                }
                ModelState.DOWNLOADING, ModelState.VERIFYING -> {}
            }
        }
    }

    refreshAllAiModelRows()
    if (isAnyAiModelDownloading()) {
        startAiAssistantPolling()
    }
}

internal fun SettingsActivity.selectOnDeviceModel(modelId: String) {
    prefs.edit {
        putString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_ON_DEVICE)
        putString(Constants.Prefs.AI_ASSISTANT_SELECTED_MODEL_ID, modelId)
    }
    refreshAllAiModelRows()
    refreshAllWebProviderRows()
    notifySettingsChanged()
}

internal fun SettingsActivity.selectWebProvider(providerId: String) {
    prefs.edit {
        putString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_WEB)
        putString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, providerId)
    }
    refreshAllAiModelRows()
    refreshAllWebProviderRows()
    notifySettingsChanged()
}

internal fun SettingsActivity.setupWebProviderList() {
    val providerList = findViewById<LinearLayout>(R.id.ai_assistant_web_provider_list)
    providerList.removeAllViews()
    aiWebProviderRows.clear()

    WebAiProvider.ALL.forEach { provider ->
        val rowView = layoutInflater.inflate(R.layout.item_ai_model_row, providerList, false)
        val row = SettingsActivity.ModelRowViews(
            root = rowView,
            name = rowView.findViewById(R.id.model_row_name),
            badge = rowView.findViewById(R.id.model_row_selected_badge),
            description = rowView.findViewById(R.id.model_row_description),
            status = rowView.findViewById(R.id.model_row_status),
            progress = rowView.findViewById(R.id.model_row_progress),
            actionButton = rowView.findViewById(R.id.model_row_action_button)
        )
        row.name.text = provider.displayName
        row.description.text = getString(R.string.ai_assistant_web_provider_description, provider.displayName)
        row.progress.isVisible = false
        row.status.isVisible = false
        row.actionButton.text = getString(R.string.ai_assistant_use_web_provider)
        aiWebProviderRows[provider.id] = row
        providerList.addView(rowView)

        val select = { selectWebProvider(provider.id) }
        row.root.setOnClickListener { select() }
        row.actionButton.setOnClickListener { select() }
    }

    refreshAllWebProviderRows()
}

internal fun SettingsActivity.refreshAllWebProviderRows() {
    val isWeb = isWebSourceSelected()
    val selectedId = selectedWebProviderId()
    WebAiProvider.ALL.forEach { provider ->
        val row = aiWebProviderRows[provider.id] ?: return@forEach
        val isSelected = isWeb && provider.id == selectedId
        row.badge.isVisible = isSelected
        row.actionButton.isVisible = !isSelected
    }
}

internal fun SettingsActivity.isAnyAiModelDownloading(): Boolean =
    AssistantModel.ALL.any { modelDownloadManager.currentState(it) == ModelState.DOWNLOADING }

internal fun SettingsActivity.refreshAllAiModelRows() {
    val selectedId = selectedModelId()
    val anyDownloading = isAnyAiModelDownloading()
    val onDeviceActive = !isWebSourceSelected()

    AssistantModel.ALL.forEach { model ->
        val row = aiModelRows[model.id] ?: return@forEach
        val isSelected = onDeviceActive && model.id == selectedId

        when (val state = modelDownloadManager.currentState(model)) {
            ModelState.NOT_DOWNLOADED, ModelState.FAILED -> {
                row.badge.isVisible = false
                row.status.text = if (state == ModelState.FAILED) {
                    getString(R.string.ai_assistant_failed)
                } else {
                    getString(R.string.ai_assistant_wifi_notice, formatSize(model.expectedSizeBytes))
                }
                row.progress.isVisible = false
                row.actionButton.isVisible = true
                row.actionButton.isEnabled = !anyDownloading
                row.actionButton.text = getString(R.string.ai_assistant_download, formatSize(model.expectedSizeBytes))
            }
            ModelState.DOWNLOADING -> {
                row.badge.isVisible = false
                val percent = ((modelDownloadManager.progress(model)?.fraction ?: 0f) * 100).toInt()
                row.status.text = getString(R.string.ai_assistant_downloading, percent)
                row.progress.isVisible = true
                row.progress.progress = percent
                row.actionButton.isVisible = true
                row.actionButton.isEnabled = false
                row.actionButton.text = getString(R.string.ai_assistant_downloading, percent)
            }
            ModelState.VERIFYING -> {
                row.badge.isVisible = false
                row.status.text = getString(R.string.ai_assistant_verifying)
                row.progress.isVisible = true
                row.progress.progress = 100
                row.actionButton.isVisible = true
                row.actionButton.isEnabled = false
                row.actionButton.text = getString(R.string.ai_assistant_verifying)
            }
            ModelState.READY -> {
                row.badge.isVisible = isSelected
                row.status.text = getString(R.string.ai_assistant_ready)
                row.progress.isVisible = false
                row.actionButton.isVisible = true
                row.actionButton.isEnabled = true
                row.actionButton.text = if (isSelected) {
                    getString(R.string.ai_assistant_delete_model)
                } else {
                    getString(R.string.ai_assistant_use_model)
                }
            }
        }
    }
}

internal fun SettingsActivity.startAiAssistantPolling() {
    if (aiAssistantPolling) return
    aiAssistantPolling = true
    val poll = object : Runnable {
        override fun run() {
            val stillActive = AssistantModel.ALL
                .map { modelDownloadManager.pollAndReconcile(it) }
                .any { it == ModelState.DOWNLOADING || it == ModelState.VERIFYING }
            refreshAllAiModelRows()
            if (stillActive) {
                aiAssistantPollHandler.postDelayed(this, 1000)
            } else {
                aiAssistantPolling = false
            }
        }
    }
    aiAssistantPollHandler.post(poll)
}
