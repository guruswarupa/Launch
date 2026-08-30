package com.guruswarupa.launch.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

/**
 * The AI chat page in the home-screen pager (see ScreenPagerManager.Page.AI_CHAT) — a
 * dedicated conversational screen, distinct from the one-shot "Ask AI" search result.
 *
 * Two mutually exclusive sources, chosen in Settings:
 *  - On-device (see [AssistantResult]/OnDeviceAssistant): the custom chat UI below, offline.
 *  - Web (see [WebAiProvider]): the provider's real site loaded full-screen in a WebView —
 *    needed for hosted assistants like ChatGPT/Claude that have no on-device equivalent.
 *    Deliberately not domain-locked the way WebAppActivity locks user-added web apps: these
 *    are curated, trusted, provider-controlled URLs, and login flows for them routinely
 *    redirect across domains (Google/Microsoft SSO), which a domain lock would break.
 *
 * Chat history is kept in memory only for this MainActivity instance; nothing is persisted.
 */
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
    private val contentContainer: View = rootView.findViewById(R.id.ai_chat_content_container)

    private val webView: WebView = rootView.findViewById(R.id.ai_chat_webview)
    private val webViewFullscreenContainer: FrameLayout = rootView.findViewById(R.id.ai_chat_webview_fullscreen_container)
    private val webViewProgress: ProgressBar = rootView.findViewById(R.id.ai_chat_webview_progress)

    private val adapter = AiChatAdapter()
    private var isWaitingForResponse = false

    private var webViewInitialized = false
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

        updateTypography()
        refreshAvailability()
        // Deliberately not calling refreshSource() here: it would eagerly start loading a
        // web provider's full site on every cold launcher start even when this page is never
        // visited. onPageShown()/onActivityResume() call it lazily once this page is actually
        // the visible one (including the initial restore-to-this-page case).
    }

    /** Called whenever this page becomes the visible pager page. */
    fun onPageShown() {
        refreshAvailability()
        refreshSource()
        if (webViewInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    /** Called whenever the pager scrolls to a different page — stops this page's back-press/WebView activity from interfering elsewhere. */
    fun onPageHidden() {
        if (webViewInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        updateBackCallbackEnabled()
    }

    fun onActivityResume() {
        if (isCurrentPage()) {
            // Catches switching source/model (in Settings) and returning here without the
            // pager itself changing page, which is the only other trigger for these refreshes.
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
        if (webViewInitialized) {
            releaseWebView()
        }
    }

    /** Applies the user's configured font scale/style/intensity/color — same mechanism RssFeedPage uses — so this page matches the News/Widgets pages instead of looking themed differently. */
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

    /** Switches between the on-device chat UI and the web provider's WebView, and loads the selected provider's URL if it changed. Safe to call repeatedly — a no-op unless something actually changed. */
    private fun refreshSource() {
        val provider = WebAiProvider.byId(
            activity.sharedPreferences.getString(Constants.Prefs.AI_ASSISTANT_SELECTED_WEB_PROVIDER_ID, null)
        )

        if (isWebSourceSelected() && provider != null) {
            contentContainer.isVisible = false
            webView.isVisible = true
            ensureWebViewInitialized()
            if (loadedWebProviderId != provider.id) {
                loadedWebProviderId = provider.id
                webView.loadUrl(provider.url)
            }
        } else {
            webView.isVisible = false
            webViewProgress.isVisible = false
            contentContainer.isVisible = true
        }
        updateBackCallbackEnabled()
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
                // Non-web scheme (mailto:, intent:, market:, etc.) — hand off to the system, since the WebView can't render it.
                return try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateBackCallbackEnabled()
            }
        }
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

    /** Pushes the input bar above the on-screen keyboard by shrinking the content column with bottom padding equal to the IME's height, instead of relying on windowSoftInputMode (which would resize every page in the shared pager, not just this one). */
    private fun setupKeyboardAvoidance() {
        val initialBottomPadding = contentContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                initialBottomPadding + imeBottom
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
