package com.guruswarupa.launch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.adapters.AiChatAdapter
import com.guruswarupa.launch.adapters.ChatMessage
import com.guruswarupa.launch.ai.llm.AssistantResult
import com.guruswarupa.launch.ai.llm.WebAiProvider
import com.guruswarupa.launch.managers.ScreenPagerManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.managers.WebAppAdBlocker
import com.guruswarupa.launch.models.Constants
import kotlinx.coroutines.launch

class AiChatPage(
    private val activity: MainActivity,
    private val rootView: View
) {
    private val recyclerView: RecyclerView = rootView.findViewById(R.id.ai_chat_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.ai_chat_empty_state)
    private val emptyTitle: TextView = rootView.findViewById(R.id.ai_chat_empty_title)
    private val emptyMessage: TextView = rootView.findViewById(R.id.ai_chat_empty_message)
    private val inputField: EditText = rootView.findViewById(R.id.ai_chat_input)
    private val sendButton: ImageButton = rootView.findViewById(R.id.ai_chat_send_button)
    private val clearButton: ImageButton = rootView.findViewById(R.id.ai_chat_clear_button)
    private val settingsButton: ImageButton = rootView.findViewById(R.id.ai_chat_settings_button)
    private val contentContainer: View = rootView.findViewById(R.id.ai_chat_content_container)

    private val webView: WebView = rootView.findViewById(R.id.ai_chat_webview)
    private val webViewFullscreenContainer: FrameLayout = rootView.findViewById(R.id.ai_chat_webview_fullscreen_container)
    private val webViewProgress: ProgressBar = rootView.findViewById(R.id.ai_chat_webview_progress)
    private val webViewErrorContainer: View = rootView.findViewById(R.id.ai_chat_webview_error_container)
    private val webViewRetryButton: Button = rootView.findViewById(R.id.ai_chat_webview_retry_button)

    private val adapter = AiChatAdapter()
    private var isWaitingForResponse = false

    private var webViewInitialized = false
    private var webViewLoadFailed = false
    private var loadedWebProviderId: String? = null
    private var webCustomView: View? = null
    private var webCustomViewCallback: WebChromeClient.CustomViewCallback? = null

    private val webBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (webCustomView != null) {
                exitWebFullscreen()
            } else if (webViewInitialized && webView.canGoBack()) {
                webView.goBack()
            }
        }
    }

    private var networkCallbackRegistered = false
    private val connectivityManager: ConnectivityManager by lazy {
        activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!webViewLoadFailed || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
            activity.runOnUiThread { retryFailedLoad() }
        }
    }

    fun setup() {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        setupKeyboardAvoidance()
        activity.onBackPressedDispatcher.addCallback(activity, webBackCallback)

        sendButton.setOnClickListener { trySend() }
        inputField.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSendAction) {
                trySend()
                true
            } else {
                false
            }
        }

        clearButton.setOnClickListener {
            adapter.clear()
            refreshAvailability()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(activity, com.guruswarupa.launch.ui.activities.SettingsActivity::class.java).apply {
                putExtra(com.guruswarupa.launch.ui.activities.SettingsActivity.EXTRA_OPEN_AI_SECTION, true)
            }
            activity.startActivity(intent)
        }

        webViewRetryButton.setOnClickListener { retryFailedLoad() }
        registerNetworkCallback()

        updateTypography()
        refreshAvailability()

    }

    fun onPageShown() {
        refreshAvailability()
        refreshSource()
        if (webViewInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    fun onPageHidden() {
        if (webViewInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        updateBackCallbackEnabled()
    }

    fun onActivityResume() {
        if (isCurrentPage()) {

            refreshAvailability()
            refreshSource()
            if (webViewInitialized) {
                webView.onResume()
                webView.resumeTimers()
            }
        }
    }

    fun onActivityPause() {
        if (webViewInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    fun onActivityDestroy() {
        unregisterNetworkCallback()
        if (webViewInitialized) {
            releaseWebView()
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        networkCallbackRegistered = false
    }

    private fun retryFailedLoad() {
        if (!webViewLoadFailed) return
        val provider = WebAiProvider.byId(
            activity.sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, null)
        )
        if (provider != null && isWebSourceSelected()) loadProviderUrl(provider.url)
    }

    fun updateTypography() {
        val prefs = activity.sharedPreferences
        val scale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100) / 100f
        val style = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        val color = TypographyManager.getConfiguredFontColor(activity)

        adapter.updateTypography(scale, style, intensity, color)
        TypographyManager.applyToViewTree(contentContainer, scale, style, intensity, color)
    }

    private fun isCurrentPage(): Boolean =
        activity.screenPagerManager.getCurrentPage() == ScreenPagerManager.Page.AI_CHAT

    private fun isWebSourceSelected(): Boolean =
        activity.sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_SOURCE_TYPE, Constants.Prefs.AI_ASSISTANT_SOURCE_ON_DEVICE) ==
            Constants.Prefs.AI_ASSISTANT_SOURCE_WEB

    private fun refreshSource() {
        val provider = WebAiProvider.byId(
            activity.sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, null)
        )

        if (isWebSourceSelected() && provider != null) {
            contentContainer.isVisible = false
            ensureWebViewInitialized()
            if (loadedWebProviderId != provider.id) {
                loadedWebProviderId = provider.id
                loadProviderUrl(provider.url)
            } else {
                webView.isVisible = !webViewLoadFailed
                webViewErrorContainer.isVisible = webViewLoadFailed
            }
        } else {
            webView.isVisible = false
            webViewProgress.isVisible = false
            webViewErrorContainer.isVisible = false
            contentContainer.isVisible = true
        }
        updateBackCallbackEnabled()
    }

    private fun loadProviderUrl(url: String) {
        webViewLoadFailed = false
        webViewErrorContainer.isVisible = false
        webView.isVisible = true
        webView.loadUrl(url)
    }

    private fun updateBackCallbackEnabled() {
        webBackCallback.isEnabled = isCurrentPage() && isWebSourceSelected() &&
            (webCustomView != null || (webViewInitialized && webView.canGoBack()))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebViewInitialized() {
        if (webViewInitialized) return
        webViewInitialized = true

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                webViewProgress.progress = newProgress
                webViewProgress.isVisible = newProgress in 1..99
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || webCustomView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                webCustomView = view
                webCustomViewCallback = callback
                webViewFullscreenContainer.isVisible = true
                webViewFullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
                webView.isVisible = false
                updateBackCallbackEnabled()
            }

            override fun onHideCustomView() = exitWebFullscreen()

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                activity.resultRegistry.onAiChatMediaPicked = { uris ->
                    filePathCallback?.onReceiveValue(uris.toTypedArray())
                }
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                val mimeType = if (acceptTypes.any { it.startsWith("video/") }) "video/*" else "image/*"
                return try {
                    activity.resultRegistry.aiChatMediaPickerLauncher.launch(mimeType)
                    true
                } catch (_: Exception) {
                    activity.resultRegistry.onAiChatMediaPicked = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (WebAppAdBlocker.shouldBlock(request?.url)) return WebAppAdBlocker.createEmptyResponse()
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase().orEmpty()
                if (scheme == "http" || scheme == "https") return false

                return try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != false) showWebViewError()
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showWebViewError()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (webViewLoadFailed) {
                    webView.isVisible = false
                    webViewErrorContainer.isVisible = true
                }
                updateBackCallbackEnabled()
            }
        }
    }

    private fun showWebViewError() {
        webViewLoadFailed = true
        webViewProgress.isVisible = false
        webView.isVisible = false
        webViewErrorContainer.isVisible = true
    }

    private fun exitWebFullscreen() {
        webCustomView?.let {
            webViewFullscreenContainer.removeView(it)
            webCustomView = null
        }
        webCustomViewCallback?.onCustomViewHidden()
        webCustomViewCallback = null
        webViewFullscreenContainer.isVisible = false
        webView.isVisible = true
        updateBackCallbackEnabled()
    }

    private fun releaseWebView() {
        exitWebFullscreen()
        try {
            webView.apply {
                stopLoading()
                onPause()
                pauseTimers()
                loadUrl("about:blank")
                clearHistory()
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        } catch (_: Exception) {
        } finally {
            try {
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun setupKeyboardAvoidance() {
        val initialBottomPadding = contentContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                initialBottomPadding + maxOf(imeBottom, navBarBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun trySend() {
        val text = inputField.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || isWaitingForResponse || !activity.onDeviceAssistant.isModelReady) return

        inputField.text?.clear()
        emptyState.isVisible = false
        adapter.addMessage(ChatMessage(text, isUser = true))
        val thinkingIndex = adapter.addMessage(ChatMessage(activity.getString(R.string.ask_ai_thinking), isUser = false))
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        setWaiting(true)

        activity.lifecycleScope.launch {
            val result = activity.onDeviceAssistant.ask(text)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val answer = when (result) {
                is AssistantResult.Success -> result.text
                is AssistantResult.Error -> activity.getString(R.string.ask_ai_error, result.message)
            }
            adapter.replaceMessageAt(thinkingIndex, ChatMessage(answer, isUser = false))
            recyclerView.scrollToPosition(adapter.itemCount - 1)
            setWaiting(false)
        }
    }

    private fun setWaiting(waiting: Boolean) {
        isWaitingForResponse = waiting
        sendButton.isEnabled = !waiting && activity.onDeviceAssistant.isModelReady
    }

    private fun refreshAvailability() {
        val ready = activity.onDeviceAssistant.isModelReady
        inputField.isEnabled = ready
        sendButton.isEnabled = ready && !isWaitingForResponse

        if (adapter.itemCount == 0) {
            emptyState.isVisible = true
            if (ready) {
                emptyTitle.text = activity.getString(R.string.ai_chat_empty_title)
                emptyMessage.text = activity.getString(R.string.ai_chat_empty_message)
            } else {
                emptyTitle.text = activity.getString(R.string.ai_chat_not_ready_title)
                emptyMessage.text = activity.getString(R.string.ai_chat_not_ready_message)
            }
        } else {
            emptyState.isVisible = false
        }
    }
}
