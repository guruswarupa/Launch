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

internal fun SettingsActivity.setupSupportSection() {
    val h = findViewById<LinearLayout>(R.id.support_header)
    val c = findViewById<LinearLayout>(R.id.support_content)
    val a = findViewById<TextView>(R.id.support_arrow)
    setupSectionToggle(h, c, a)
    findViewById<View>(R.id.feedback_button).setOnClickListener { sendFeedback() }
    updateSupporterBadge()
    supportProducts.forEach { (productId, buttonId) ->
        findViewById<Button>(buttonId).apply {
            text = getString(R.string.support_button_loading)
            isEnabled = false
            setOnClickListener { launchSupportPurchase(productId) }
        }
    }
    findViewById<TextView>(R.id.github_links_text).apply {
        movementMethod = LinkMovementMethod.getInstance()
        text = Html.fromHtml(getString(R.string.github_project_links), Html.FROM_HTML_MODE_COMPACT)
    }
    initializeBilling()
}

internal fun SettingsActivity.initializeBilling() {
    if (billingClient?.isReady == true) {
        querySupportProductDetails()
        queryExistingSupportPurchases()
        return
    }
    if (billingClient == null) {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }
    setSupportButtonsLoading()
    billingClient?.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                querySupportProductDetails()
                queryExistingSupportPurchases()
            } else {
                handleBillingError(billingResult, showToast = false)
            }
        }

        override fun onBillingServiceDisconnected() {
            setSupportButtonsUnavailable(getString(R.string.support_billing_unavailable))
        }
    })
}

internal fun SettingsActivity.querySupportProductDetails() {
    val billing = billingClient ?: return
    val params = QueryProductDetailsParams.newBuilder()
        .setProductList(
            supportProducts.keys.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
        )
        .build()
    billing.queryProductDetailsAsync(params) { billingResult, queryResult ->
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            handleBillingError(billingResult, showToast = false)
            return@queryProductDetailsAsync
        }
        runOnUiThread {
            supportProductDetails.clear()
            queryResult.productDetailsList.forEach { details ->
                supportProductDetails[details.productId] = details
            }
            updateSupportButtons()
        }
    }
}

internal fun SettingsActivity.updateSupportButtons() {
    supportProducts.forEach { (productId, buttonId) ->
        val button = findViewById<Button>(buttonId)
        val offer = supportProductDetails[productId]?.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (offer != null) {
            button.text = getString(R.string.support_button_buy, offer.formattedPrice)
            button.isEnabled = true
            button.alpha = 1f
        } else {
            button.text = getString(R.string.support_button_unavailable)
            button.isEnabled = false
            button.alpha = 0.6f
        }
    }
}

internal fun SettingsActivity.setSupportButtonsLoading() {
    supportProducts.values.forEach { buttonId ->
        findViewById<Button>(buttonId).apply {
            text = getString(R.string.support_button_loading)
            isEnabled = false
            alpha = 0.7f
        }
    }
}

internal fun SettingsActivity.setSupportButtonsUnavailable(label: String) {
    supportProducts.values.forEach { buttonId ->
        findViewById<Button>(buttonId).apply {
            text = label
            isEnabled = false
            alpha = 0.6f
        }
    }
}

internal fun SettingsActivity.launchSupportPurchase(productId: String) {
    val billing = billingClient
    if (billing == null || !billing.isReady) {
        initializeBilling()
        Toast.makeText(this, getString(R.string.support_billing_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val details = supportProductDetails[productId]
    val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
    val offerToken = offer?.offerToken
    if (details == null || offer == null || offerToken.isNullOrBlank()) {
        Toast.makeText(this, getString(R.string.support_option_not_ready), Toast.LENGTH_SHORT).show()
        return
    }
    val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(details)
        .setOfferToken(offerToken)
        .build()
    val billingResult = billing.launchBillingFlow(
        this,
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
    )
    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        handleBillingError(billingResult)
    }
}

internal fun SettingsActivity.queryExistingSupportPurchases() {
    val billing = billingClient ?: return
    billing.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
    ) { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases
                .filter { purchase -> purchase.products.any(supportProducts::containsKey) }
                .forEach { purchase -> handlePurchase(purchase, false) }
        }
    }
}

internal fun SettingsActivity.handlePurchase(purchase: Purchase, notifyUser: Boolean) {
    when (purchase.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
            billingClient?.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    prefs.edit {
                        putBoolean(Constants.Prefs.SUPPORTER_BADGE_EARNED, true)
                        putBoolean(Constants.Prefs.DONATION_PROMPT_SHOWN, true)
                    }
                    runOnUiThread {
                        updateSupporterBadge()
                        if (notifyUser) {
                            showSupportThankYouDialog()
                        }
                    }
                } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    handleBillingError(result, showToast = notifyUser)
                }
            }
        }
        Purchase.PurchaseState.PENDING -> {
            if (notifyUser) {
                Toast.makeText(this, getString(R.string.support_purchase_pending), Toast.LENGTH_SHORT).show()
            }
            updatePendingSupportButtons(purchase)
        }
        else -> Unit
    }
}

internal fun SettingsActivity.showSupportThankYouDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_support_thank_you, null)
    AlertDialog.Builder(this, R.style.CustomDialogTheme)
        .setView(dialogView)
        .setPositiveButton(getString(R.string.support_thank_you_close), null)
        .show()
}

internal fun SettingsActivity.updateSupporterBadge() {
    findViewById<TextView>(R.id.support_badge)?.visibility =
        if (prefs.getBoolean(Constants.Prefs.SUPPORTER_BADGE_EARNED, false)) View.VISIBLE else View.GONE
}

internal fun SettingsActivity.updatePendingSupportButtons(purchase: Purchase) {
    purchase.products.forEach { productId ->
        val buttonId = supportProducts[productId] ?: return@forEach
        findViewById<Button>(buttonId).apply {
            text = getString(R.string.support_button_pending)
            isEnabled = false
            alpha = 0.7f
        }
    }
}

internal fun SettingsActivity.handleBillingError(billingResult: BillingResult, showToast: Boolean = true) {
    val messageRes = when (billingResult.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> R.string.support_billing_unavailable
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> R.string.support_network_error
        BillingClient.BillingResponseCode.USER_CANCELED -> R.string.support_purchase_canceled
        else -> R.string.support_purchase_failed
    }
    runOnUiThread {
        if (messageRes == R.string.support_billing_unavailable || messageRes == R.string.support_network_error) {
            setSupportButtonsUnavailable(getString(R.string.support_button_unavailable))
        }
        if (showToast) {
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }
}
