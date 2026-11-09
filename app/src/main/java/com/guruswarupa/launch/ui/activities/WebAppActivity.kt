package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.guruswarupa.launch.services.WebMediaBridge
import com.guruswarupa.launch.services.WebMediaService
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppAdBlocker
import com.guruswarupa.launch.managers.WebAppIconFetcher

class WebAppActivity : AppCompatActivity() {
    @Volatile
    private var currentPageUrl: String? = null

    companion object {
        const val EXTRA_WEB_APP_NAME = "web_app_name"
        const val EXTRA_WEB_APP_URL = "web_app_url"
        const val EXTRA_BLOCK_REDIRECTS = "block_redirects"
        const val EXTRA_VIEW_MODE = "view_mode"
        private const val TAG = "WebAppActivity"
        private const val PLAY_SCRIPT = "(function(){document.querySelectorAll('video,audio').forEach(function(m){ if(m.paused && !m.ended && m.currentTime>0){ try{ m.play(); }catch(e){} } });})()"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** Desktop was asked for, or the mode is "auto" and the site is one that only keeps playing on its desktop version. */
        private fun resolveDesktop(mode: String, url: String): Boolean = when (mode) { "desktop" -> true; "mobile" -> false; else -> isYouTube(url) }

        private fun isYouTube(url: String): Boolean {
            val host = try { Uri.parse(url).host.orEmpty().lowercase() } catch (_: Exception) { "" }
            return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
        }

        /** m.youtube.com always serves the phone layout, so send it to the main site. */
        private fun desktopYouTubeUrl(url: String): String {
            val uri = Uri.parse(url)
            return if (uri.host.equals("m.youtube.com", ignoreCase = true)) uri.buildUpon().authority("www.youtube.com").build().toString() else url
        }

        private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "intent")

        /**
         * Each web app runs in its own task. The address is part of the intent, so opening the same web app again
         * (or tapping its playback notification) returns to the one that's already running.
         */
        fun launchIntent(context: android.content.Context, name: String, url: String, blockRedirects: Boolean = true, viewMode: String = "auto"): Intent =
            Intent(context, WebAppActivity::class.java).apply {
                data = Uri.parse("launch-web://app/" + url.hashCode().toUInt())
                putExtra(EXTRA_WEB_APP_NAME, name)
                putExtra(EXTRA_WEB_APP_URL, url)
                putExtra(EXTRA_BLOCK_REDIRECTS, blockRedirects)
                putExtra(EXTRA_VIEW_MODE, viewMode)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var addressView: TextView
    private lateinit var fullscreenContainer: FrameLayout

    private var allowedDomain: String? = null
    private fun wantsDesktop(url: String) = resolveDesktop(intent.getStringExtra(EXTRA_VIEW_MODE) ?: "auto", url)
    private var blockRedirects: Boolean = true
    private var trustedDomains = mutableSetOf<String>()
    private var subResourceErrorCount = 0

    private var customView: View? = null
    private var mediaPlaying = false
    private var videoPlaying = false
    private var resumeWanted = false
    private var resumeTries = 0
    private val resumeLoop = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || inForeground || !resumeWanted || isFinishing) return
            // Pages sometimes pause themselves when you leave. If it was playing a moment ago, press play again.
            // A few polite retries only. If the page keeps pausing itself, fighting it just makes the video stutter.
            if (resumeTries++ >= 3) { resumeWanted = false; return }
            webView.evaluateJavascript(PLAY_SCRIPT, null)
            window.decorView.postDelayed(this, 2500)
        }
    }
    private var videoWidth = 16
    private var videoHeight = 9
    private var inForeground = true
    private var mediaServiceRunning = false
    private var originalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
            }
            fileUploadCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    /** The page tells us when a video or audio element starts or stops, so we know whether to keep playing in the background. */
    private inner class MediaBridge {
        @android.webkit.JavascriptInterface
        fun state(playing: Boolean, video: Boolean, width: Int, height: Int) {
            runOnUiThread { videoPlaying = playing && video; if (video && width > 0 && height > 0) { videoWidth = width; videoHeight = height }; onMediaState(playing) }
        }
    }

    private val mediaScript = """
        (function(){ if (window.__launchMedia) return; window.__launchMedia = true;
          // Sites like YouTube pause themselves when the page is "hidden". Keep reporting it as visible so playback carries on.
          try {
            Object.defineProperty(Document.prototype, 'hidden', { configurable: true, get: function(){ return false; } });
            Object.defineProperty(Document.prototype, 'webkitHidden', { configurable: true, get: function(){ return false; } });
            Object.defineProperty(Document.prototype, 'visibilityState', { configurable: true, get: function(){ return 'visible'; } });
            Object.defineProperty(Document.prototype, 'webkitVisibilityState', { configurable: true, get: function(){ return 'visible'; } });
            ['visibilitychange','webkitvisibilitychange','pagehide','blur','freeze'].forEach(function(t){
              window.addEventListener(t, function(e){ e.stopImmediatePropagation(); }, true);
              document.addEventListener(t, function(e){ e.stopImmediatePropagation(); }, true);
            });
          } catch(e) {}
          function report(){ var p = false, v = false, w = 0, h = 0;
            document.querySelectorAll('video,audio').forEach(function(m){ if(!m.paused && !m.ended && m.readyState > 2){ p = true;
              if (m.tagName === 'VIDEO' && m.videoWidth > 0) { v = true; w = m.videoWidth; h = m.videoHeight; } } });
            try { LaunchMedia.state(p, v, w, h); } catch(e) {} }
          ['play','playing','pause','ended','emptied','waiting'].forEach(function(t){ document.addEventListener(t, report, true); });
          window.__launchPip = function(on){
            var id = '__launchPipStyle';
            function clear(){
              var el = document.getElementById(id); if (el) el.remove();
              document.querySelectorAll('[data-launch-pip],[data-launch-pip-up]').forEach(function(n){ n.removeAttribute('data-launch-pip'); n.removeAttribute('data-launch-pip-up'); });
              if (window.__launchPipTimer) { clearInterval(window.__launchPipTimer); window.__launchPipTimer = null; }
            }
            function apply(){
              var v = document.querySelector('video[data-launch-pip]');
              if (!v) { document.querySelectorAll('video').forEach(function(x){ if(!x.paused && !x.ended && (!v || x.videoWidth > v.videoWidth)) v = x; }); }
              if (!v) return;
              v.setAttribute('data-launch-pip','1');
              // A fixed video only fills the screen if no parent traps it (transforms, clipping, containment), so release them all.
              var n = v.parentElement;
              while (n && n !== document.documentElement) { n.setAttribute('data-launch-pip-up','1'); n = n.parentElement; }
              if (!document.getElementById(id)) {
                var st = document.createElement('style'); st.id = id;
                st.textContent = 'html,body{background:#000!important;overflow:hidden!important;margin:0!important} ' +
                  '[data-launch-pip-up]{transform:none!important;contain:none!important;filter:none!important;perspective:none!important;will-change:auto!important;clip-path:none!important;overflow:visible!important;} ' +
                  'video[data-launch-pip]{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;width:100vw!important;height:100vh!important;' +
                  'max-width:none!important;max-height:none!important;object-fit:contain!important;z-index:2147483647!important;background:#000!important;transform:none!important;margin:0!important;padding:0!important}';
                document.documentElement.appendChild(st);
              }
            }
            if (!on) { clear(); return; }
            clear(); apply();
            // The window finishes resizing a moment after the switch, and the page may re-render, so keep it applied while it's small.
            window.__launchPipTimer = setInterval(apply, 500);
          };
          report(); setInterval(report, 3000);
        })();
    """.trimIndent()

    private fun onMediaState(playing: Boolean) {
        if (!playing && resumeWanted && !inForeground) { mediaPlaying = false; return }
        mediaPlaying = playing
        updatePipParams()
        if (!inForeground && !isInPictureInPictureMode) {
            if (playing) startMediaService() else { stopMediaService(); pauseWebView() }
        }
    }

    private fun pipSupported() = android.os.Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): android.app.PictureInPictureParams {
        val w = videoWidth.coerceAtLeast(1); val h = videoHeight.coerceAtLeast(1)
        // Android only accepts ratios between 1:2.39 and 2.39:1.
        val ratio = (w.toFloat() / h).coerceIn(1f / 2.39f, 2.39f)
        val builder = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational((ratio * 1000).toInt(), 1000))
        if (android.os.Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(videoPlaying)
        return builder.build()
    }

    private fun updatePipParams() {
        if (!pipSupported()) return
        try { setPictureInPictureParams(pipParams()) } catch (_: Exception) {}
    }

    /** Shrinks the web app into a small floating window, like a mini player, while a video is playing. */
    private fun enterPip() {
        if (!pipSupported() || isInPictureInPictureMode) return
        try { enterPictureInPictureMode(pipParams()) } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (videoPlaying && mediaPlaying && !isFinishing) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        progressBar.visibility = if (isInPictureInPictureMode) View.GONE else progressBar.visibility
        if (::webView.isInitialized) webView.evaluateJavascript("window.__launchPip && window.__launchPip($isInPictureInPictureMode)", null)
        if (isInPictureInPictureMode) return
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            // The mini window was closed: stop what was playing and leave.
            if (::webView.isInitialized) webView.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();})", null)
            mediaPlaying = false
            finish()
        } else applyImmersion(customView != null)
    }

    private fun startMediaService() {
        if (mediaServiceRunning) return
        val open = android.app.PendingIntent.getActivity(
            this, 7, launchIntent(this, intent.getStringExtra(EXTRA_WEB_APP_NAME).orEmpty(), intent.getStringExtra(EXTRA_WEB_APP_URL).orEmpty(), blockRedirects, intent.getStringExtra(EXTRA_VIEW_MODE) ?: "auto"),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, WebMediaService::class.java).putExtra(WebMediaService.EXTRA_TITLE, intent.getStringExtra(EXTRA_WEB_APP_NAME).orEmpty()).putExtra(WebMediaService.EXTRA_OPEN, open)
            )
            mediaServiceRunning = true
        } catch (e: Exception) {
            android.util.Log.w("WebMedia", "could not start media service", e)
        }
    }

    private fun stopMediaService() {
        if (!mediaServiceRunning) return
        try { startService(Intent(this, WebMediaService::class.java).setAction(WebMediaService.ACTION_STOP)) } catch (_: Exception) {}
        mediaServiceRunning = false
    }

    private fun pauseWebView() {
        if (!::webView.isInitialized) return
        webView.onPause()
        webView.pauseTimers()
    }

    private fun controlMedia(command: String) {
        when (command) {
            "close" -> { runOnUiThread { resumeWanted = false; mediaPlaying = false; mediaServiceRunning = false; finish() } }
            "toggle" -> runOnUiThread {
                resumeWanted = false
                webView.evaluateJavascript(
                    "(function(){var m=[].slice.call(document.querySelectorAll('video,audio'));var playing=m.some(function(x){return !x.paused&&!x.ended});" +
                        "m.forEach(function(x){ if(playing) x.pause(); else if(x.currentTime>0 && !x.ended) x.play(); });})();", null
                )
            }
        }
    }

    /** The page draws edge to edge with the status bar hidden. Full screen (video) hides every system bar. */
    private fun applyImmersion(full: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (full) controller.hide(WindowInsetsCompat.Type.systemBars()) else {
            // The status bar stays hidden (swipe down from the top edge to peek at it); the navigation bar is kept.
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersion(customView != null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val appName = intent.getStringExtra(EXTRA_WEB_APP_NAME).orEmpty()
        val url = intent.getStringExtra(EXTRA_WEB_APP_URL).orEmpty()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_web_app)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        applyImmersion(false)

        if (appName.isBlank() || url.isBlank()) {
            finish()
            return
        }

        allowedDomain = extractDomain(url)

        blockRedirects = intent.getBooleanExtra(EXTRA_BLOCK_REDIRECTS, true)

        title = appName

        WebAppIconFetcher.loadIcon(this, url) { drawable ->
            if (drawable != null) {
                try {
                    val bitmap = createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1)
                    ).applyCanvas {
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(this)
                    }

                    @Suppress("DEPRECATION")
                    val taskDescription = ActivityManager.TaskDescription(
                        appName,
                        bitmap,
                        Color.TRANSPARENT
                    )
                    setTaskDescription(taskDescription)
                } catch (_: Exception) {
                }
            }
        }

        titleView = findViewById<TextView>(R.id.web_app_title).apply { text = appName }
        addressView = findViewById<TextView>(R.id.web_app_address).apply {
            text = url
            setOnLongClickListener {
                if (trustedDomains.isNotEmpty()) {
                    android.app.AlertDialog.Builder(this@WebAppActivity, R.style.CustomDialogTheme)
                        .setTitle(getString(R.string.dlg_clear_trusted_domains))
                        .setMessage(getString(R.string.dlg_clear_trusted_domain_s_for_this_session, trustedDomains.size))
                        .setPositiveButton(getString(R.string.weather_condition_clear)) { _, _ ->
                            trustedDomains.clear()
                            Toast.makeText(this@WebAppActivity, this@WebAppActivity.getString(R.string.toast_trusted_domains_cleared), Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(getString(R.string.cancel_button), null)
                        .show()
                    true
                } else {
                    false
                }
            }
        }
        progressBar = findViewById(R.id.web_app_progress)
        webView = findViewById(R.id.web_app_webview)
        fullscreenContainer = findViewById(R.id.web_app_fullscreen_container)

        findViewById<ImageButton>(R.id.web_app_back_button).setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        findViewById<ImageButton>(R.id.web_app_refresh_button).setOnClickListener {
            webView.reload()
        }
        findViewById<ImageButton>(R.id.web_app_browser_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, (webView.url ?: url).toUri()))
        }

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
            if (wantsDesktop(url)) {
                // YouTube's phone site pauses when you leave; the desktop site keeps playing, so ask for that one.
                userAgentString = DESKTOP_USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }

        webView.addJavascriptInterface(MediaBridge(), "LaunchMedia")
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        WebMediaBridge.controller = ::controlMedia

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
                originalOrientation = requestedOrientation
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                applyImmersion(true)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                val isVideo = acceptTypes.any { it.startsWith("video/") }
                val isImage = acceptTypes.any { it.startsWith("image/") || it == "*/*" }
                try {
                    val mimeType = when {
                        isVideo -> "video/*"
                        isImage -> "image/*"
                        else -> "*/*"
                    }
                    mediaPickerLauncher.launch(mimeType)
                    return true
                } catch (_: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
            }
        }

        val root = findViewById<View>(R.id.web_app_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            // Nothing is padded at the top or sides, so the page fills the screen behind the status bar.
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, if (customView != null) 0 else bottom)
            insets
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentPageUrl = url ?: currentPageUrl
                view?.evaluateJavascript(WebAppAdBlocker.cosmeticScriptFor(currentPageUrl), null)
                view?.evaluateJavascript(mediaScript, null)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (WebAppAdBlocker.shouldBlockRequest(request?.url, currentPageUrl)) {
                    return WebAppAdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                if (WebAppAdBlocker.shouldBlockRequest(url?.toUri(), currentPageUrl)) {
                    return WebAppAdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val targetUrl = targetUri.toString()

                if (WebAppAdBlocker.shouldBlock(targetUri)) {
                    return true
                }

                val scheme = targetUri.scheme.orEmpty().lowercase()

                if (scheme != "http" && scheme != "https") {
                    if (scheme in BLOCKED_SCHEMES) {
                        return true
                    }

                    val shouldOpen = try {
                        android.app.AlertDialog.Builder(this@WebAppActivity, R.style.CustomDialogTheme)
                            .setTitle(getString(R.string.dlg_open_external_app))
                            .setMessage(getString(R.string.dlg_this_will_open_an_external_app, scheme))
                            .setPositiveButton(getString(R.string.dlg_open)) { _, _ ->
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, targetUrl.toUri()))
                                } catch (e: Exception) {
                                    Toast.makeText(this@WebAppActivity, this@WebAppActivity.getString(R.string.toast_no_app_available), Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(getString(R.string.cancel_button), null)
                            .show()
                        false
                    } catch (e: Exception) {
                        false
                    }
                    return shouldOpen
                }

                val targetDomain = extractDomain(targetUrl)
                val isSameDomain = targetDomain != null && isDomainAllowed(targetDomain)

                if (!isSameDomain) {
                    val targetDomainSafe = targetDomain ?: "unknown domain"

                    if (targetDomainSafe in trustedDomains) {
                        return false
                    }

                    if (blockRedirects) {
                        Toast.makeText(this@WebAppActivity, this@WebAppActivity.getString(R.string.toast_navigation_blocked_redirecting_to_external_domai), Toast.LENGTH_LONG).show()
                        return true
                    }

                    val shouldProceed = try {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_security_warning, null)
                        val trustCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.trust_domain_checkbox)

                        android.app.AlertDialog.Builder(this@WebAppActivity, R.style.CustomDialogTheme)
                            .setTitle(getString(R.string.dlg_security_warning))
                            .setView(dialogView)
                            .setMessage(getString(R.string.dlg_you_are_about_to_navigate_to_an_external_domain, targetDomainSafe, allowedDomain))
                            .setPositiveButton(getString(R.string.app_lock_action_continue)) { _, _ ->
                                if (trustCheckbox.isChecked) {
                                    trustedDomains.add(targetDomainSafe)
                                }
                            }
                            .setNegativeButton(getString(R.string.cancel_button)) { _, _ ->
                            }
                            .show()
                        false
                    } catch (e: Exception) {

                        android.app.AlertDialog.Builder(this@WebAppActivity, R.style.CustomDialogTheme)
                            .setTitle(getString(R.string.dlg_security_warning))
                            .setMessage(getString(R.string.dlg_you_are_about_to_navigate_to_an_external_domain, targetDomainSafe, allowedDomain))
                            .setPositiveButton(getString(R.string.app_lock_action_continue)) { _, _ ->
                                trustedDomains.add(targetDomainSafe)
                            }
                            .setNegativeButton(getString(R.string.cancel_button)) { _, _ ->
                            }
                            .show()
                        false
                    }
                    return shouldProceed
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentPageUrl = url
                view?.evaluateJavascript(WebAppAdBlocker.cosmeticScriptFor(url), null)
                view?.evaluateJavascript(mediaScript, null)
                addressView.text = url ?: intent.getStringExtra(EXTRA_WEB_APP_URL).orEmpty()

                subResourceErrorCount = 0

                val currentDomain = extractDomain(url ?: "")
                val isExternalTrusted = currentDomain != null && currentDomain != allowedDomain && currentDomain in trustedDomains
                if (isExternalTrusted) {
                    addressView.alpha = 0.7f
                } else {
                    addressView.alpha = 1.0f
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                val url = request?.url?.toString() ?: "unknown"
                val isMainFrame = request?.isForMainFrame == true

                if (isMainFrame) {
                    Toast.makeText(this@WebAppActivity, R.string.web_app_load_failed, Toast.LENGTH_SHORT).show()
                } else {

                    subResourceErrorCount++

                    if (subResourceErrorCount == 5) {
                        Toast.makeText(this@WebAppActivity, this@WebAppActivity.getString(R.string.toast_resource_loading_errors_occurred_some_page_conte, subResourceErrorCount), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        currentPageUrl = url
        webView.loadUrl(if (wantsDesktop(url) && isYouTube(url)) desktopYouTubeUrl(url) else url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen()
                    return
                }
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        if (!::webView.isInitialized) return
        resumeWanted = mediaPlaying
        resumeTries = 0
        if (resumeWanted) { window.decorView.removeCallbacks(resumeLoop); window.decorView.postDelayed(resumeLoop, 700) }
        // Keep running while a video or sound is playing so it carries on in the background.
        if (isFinishing) { pauseWebView(); return }
        if (isInPictureInPictureMode) return
        if (videoPlaying && pipSupported()) {
            // A video is playing: it is probably about to shrink into the mini window. If it doesn't, carry on in the background.
            window.decorView.postDelayed({
                if (!inForeground && !isInPictureInPictureMode && !isFinishing) { if (mediaPlaying) startMediaService() else pauseWebView() }
            }, 400)
            return
        }
        if (mediaPlaying) { startMediaService(); return }
        // Not sure yet (the page may not have reported): ask it right now before pausing.
        webView.evaluateJavascript(
            "(function(){var p=false;document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused&&!m.ended&&m.readyState>2)p=true;});return p;})()"
        ) { result ->
            if (!inForeground) {
                if (result == "true") { mediaPlaying = true; startMediaService() } else pauseWebView()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        resumeWanted = false
        window.decorView.removeCallbacks(resumeLoop)
        stopMediaService()
        if (::webView.isInitialized) {
            webView.resumeTimers()
            webView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        // Android marks the page hidden when the screen goes away; wake it back up while something is playing.
        if (::webView.isInitialized && mediaPlaying && !isFinishing) {
            webView.onResume(); webView.resumeTimers()
            window.decorView.postDelayed({ if (::webView.isInitialized && mediaPlaying && !inForeground) { webView.onResume(); webView.resumeTimers() } }, 500)
        }
        if (::webView.isInitialized && !mediaPlaying && !isInPictureInPictureMode) {
            webView.stopLoading()
        }
        if (isFinishing) {
            releaseWebView()
        }
    }

    override fun onDestroy() {
        stopMediaService()
        if (WebMediaBridge.controller != null) WebMediaBridge.controller = null
        exitFullscreen()

        trustedDomains.clear()
        subResourceErrorCount = 0

        releaseWebView()
        fileUploadCallback = null
        super.onDestroy()
    }

    private fun exitFullscreen() {
        customView?.let {
            fullscreenContainer.removeView(it)
            customView = null
        }
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        fullscreenContainer.visibility = View.GONE
        if (::webView.isInitialized) webView.visibility = View.VISIBLE
        requestedOrientation = originalOrientation
        applyImmersion(false)
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.host
        } catch (e: Exception) {
            null
        }
    }

    private fun isDomainAllowed(domain: String): Boolean {
        val allowed = allowedDomain ?: return false

        if (domain in trustedDomains) return true

        if (domain == allowed) return true

        val domainWithoutWww = domain.removePrefix("www.")
        val allowedWithoutWww = allowed.removePrefix("www.")

        if (domainWithoutWww == allowedWithoutWww) return true

        if (domainWithoutWww.endsWith(".$allowedWithoutWww")) return true

        return false
    }

    private fun releaseWebView() {
        if (!::webView.isInitialized) {
            return
        }

        try {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null

            webView.apply {
                try {
                    stopLoading()
                    onPause()
                    pauseTimers()
                    loadUrl("about:blank")
                    clearHistory()
                    clearCache(false)
                } catch (e: Exception) {
                } finally {

                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    removeAllViews()

                    val parentView = parent as? ViewGroup
                    if (parentView != null) {
                        try {
                            parentView.removeView(this)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {

            try {
                webView.destroy()
            } catch (e: Exception) {
            }
        }
    }
}
