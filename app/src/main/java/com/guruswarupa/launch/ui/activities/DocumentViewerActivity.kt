package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import android.widget.PopupMenu
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.ui.document.ConvertedDocument
import com.guruswarupa.launch.ui.document.DocumentConverter
import com.guruswarupa.launch.ui.document.UnsupportedLegacyFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class DocumentViewerActivity : VaultBaseActivity() {

    private lateinit var documentTitle: TextView
    private lateinit var documentPageInfo: TextView
    private lateinit var pdfReader: com.guruswarupa.launch.ui.views.PdfReaderView
    private var pdfIndex: com.guruswarupa.launch.ui.document.PdfTextIndex? = null
    private var pdfMatches: List<com.guruswarupa.launch.ui.document.PdfTextIndex.Match> = emptyList()
    private var pdfMatchIndex = 0
    private var pdfSearchToken = 0
    private var pdfIndexStarted = false
    private var pdfFile: File? = null
    private var pdfCurrentQuery = ""
    private lateinit var documentWebView: WebView
    private lateinit var loadingContainer: View
    private lateinit var loadingText: TextView
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var openExternalButton: TextView
    private lateinit var pdfNavBar: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var btnPrevPage: ImageView
    private lateinit var btnNextPage: ImageView
    private lateinit var shareButton: ImageView

    private var vaultManager: EncryptedFolderManager? = null
    private var tempFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var totalPages = 0
    private var currentPageIndex = 0


    private var isVaultFile = false
    private var vaultFileName: String? = null
    private var externalUri: Uri? = null

    private var currentDoc: ConvertedDocument? = null
    private var showingAlternate = false
    private var pendingScrollY = 0
    private lateinit var mediaContainer: FrameLayout
    private lateinit var mediaVideo: VideoView
    private lateinit var mediaAudioLabel: TextView
    private lateinit var findButton: ImageView
    private lateinit var optionsButton: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView
    private val viewPrefs by lazy { getSharedPreferences("document_viewer_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_document_viewer)

        initViews()

        vaultFileName = intent.getStringExtra(EXTRA_VAULT_FILE_NAME)
        val uriString = intent.getStringExtra(EXTRA_FILE_URI)

        val vfn = vaultFileName
        if (!vfn.isNullOrEmpty()) {
            isVaultFile = true
            val manager = EncryptedFolderManager(this)
            vaultManager = manager
            if (!manager.isUnlocked()) {
                Toast.makeText(this, this.getString(R.string.toast_vault_is_locked), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            documentTitle.text = vfn
            loadVaultDocument(vfn)
        } else if (uriString != null) {
            val uri = Uri.parse(uriString)
            externalUri = uri
            val displayName = intent.getStringExtra(EXTRA_FILE_NAME) ?: getFileNameFromUri(uri) ?: "Document"
            documentTitle.text = displayName
            loadExternalDocument(uri, displayName)
        } else if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            val uri = intent.data ?: return
            externalUri = uri
            val displayName = getFileNameFromUri(uri) ?: "Document"
            documentTitle.text = displayName
            loadExternalDocument(uri, displayName)
        } else {
            showError("No document provided", showOpenWithButton = false)
        }

        setupClickListeners()
    }

    private fun initViews() {
        documentTitle = findViewById(R.id.document_title)
        documentPageInfo = findViewById(R.id.document_page_info)
        pdfReader = findViewById(R.id.pdf_reader)
        documentWebView = findViewById(R.id.document_webview)
        loadingContainer = findViewById(R.id.loading_container)
        loadingText = findViewById(R.id.loading_text)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_text)
        openExternalButton = findViewById(R.id.open_external_button)
        pdfNavBar = findViewById(R.id.pdf_nav_bar)
        pageIndicator = findViewById(R.id.page_indicator)
        btnPrevPage = findViewById(R.id.btn_prev_page)
        btnNextPage = findViewById(R.id.btn_next_page)
        shareButton = findViewById(R.id.share_button)
        mediaContainer = findViewById(R.id.media_container)
        mediaVideo = findViewById(R.id.media_video)
        mediaAudioLabel = findViewById(R.id.media_audio_label)
        findButton = findViewById(R.id.find_button)
        optionsButton = findViewById(R.id.view_options_button)
        findBar = findViewById(R.id.find_bar)
        findInput = findViewById(R.id.find_input)
        findCount = findViewById(R.id.find_count)
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }

        shareButton.setOnClickListener { shareDocument() }
        findButton.setOnClickListener { toggleFindBar() }
        optionsButton.setOnClickListener { showViewOptions(it) }
        findInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString().orEmpty()
                if (pdfReader.visibility == View.VISIBLE) { onPdfQuery(query); return }
                if (query.isEmpty()) {
                    documentWebView.clearMatches()
                    findCount.text = ""
                } else {
                    documentWebView.findAllAsync(query)
                }
            }
        })
        findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pdfReader.visibility == View.VISIBLE) stepPdfMatch(1) else documentWebView.findNext(true)
                true
            } else false
        }
        findViewById<TextView>(R.id.find_next).setOnClickListener { if (pdfReader.visibility == View.VISIBLE) stepPdfMatch(1) else documentWebView.findNext(true) }
        findViewById<TextView>(R.id.find_prev).setOnClickListener { if (pdfReader.visibility == View.VISIBLE) stepPdfMatch(-1) else documentWebView.findNext(false) }
        findViewById<TextView>(R.id.find_close).setOnClickListener { toggleFindBar(forceClose = true) }
        documentWebView.setFindListener { active, total, _ ->
            findCount.text = if (total == 0) getString(R.string.doc_no_matches) else "${active + 1}/$total"
        }

        openExternalButton.setOnClickListener { openWithExternalApp() }

        btnPrevPage.setOnClickListener { pdfReader.scrollToPage(pdfReader.currentPage() - 1) }
        btnNextPage.setOnClickListener { pdfReader.scrollToPage(pdfReader.currentPage() + 1) }
        pageIndicator.setOnClickListener { promptGoToPage() }
    }

    private fun promptGoToPage() {
        if (totalPages <= 1) return
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 - $totalPages"
            setText((pdfReader.currentPage() + 1).toString())
            selectAll()
        }
        val wrap = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(wrap)
            .setPositiveButton("Go") { _, _ ->
                input.text.toString().toIntOrNull()?.let { pdfReader.scrollToPage(it.coerceIn(1, totalPages) - 1) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensurePdfIndex() {
        val file = pdfFile ?: return
        if (pdfIndex == null) pdfIndex = com.guruswarupa.launch.ui.document.PdfTextIndex(this, file)
        val index = pdfIndex ?: return
        if (pdfIndexStarted) return
        pdfIndexStarted = true
        Thread {
            index.buildIndex({ isDestroyed }) { done, total ->
                runOnUiThread {
                    if (pdfReader.visibility != View.VISIBLE) return@runOnUiThread
                    if (pdfCurrentQuery.isNotEmpty() && (done == total || done % 12 == 0)) runPdfSearch(keepPosition = true)
                    else if (pdfCurrentQuery.isNotEmpty() && pdfMatches.isEmpty()) findCount.text = "Indexing $done/$total"
                }
            }
        }.start()
    }

    private fun onPdfQuery(query: String) {
        pdfCurrentQuery = query.trim()
        ensurePdfIndex()
        if (pdfCurrentQuery.isEmpty()) {
            pdfMatches = emptyList()
            pdfReader.clearHighlights()
            findCount.text = ""
            return
        }
        runPdfSearch(keepPosition = false)
    }

    private fun runPdfSearch(keepPosition: Boolean) {
        val index = pdfIndex ?: return
        val token = ++pdfSearchToken
        val query = pdfCurrentQuery
        val startPage = pdfReader.currentPage()
        val previous = pdfMatches.getOrNull(pdfMatchIndex)
        Thread {
            val found = index.search(query) { token != pdfSearchToken }
            if (token != pdfSearchToken) return@Thread
            runOnUiThread {
                if (token != pdfSearchToken) return@runOnUiThread
                pdfMatches = found
                if (found.isEmpty()) {
                    pdfReader.clearHighlights()
                    findCount.text = if (index.finished) getString(R.string.doc_no_matches) else "Indexing ${index.indexed}/${index.pageCount}"
                    if (index.finished && index.totalChars == 0L) findCount.text = "No text in this PDF"
                    return@runOnUiThread
                }
                pdfMatchIndex = if (keepPosition && previous != null) found.indexOfFirst { it.page == previous.page && it.start == previous.start }.takeIf { it >= 0 } ?: 0
                else found.indexOfFirst { it.page >= startPage }.takeIf { it >= 0 } ?: 0
                showPdfMatch(true)
            }
        }.start()
    }

    private fun stepPdfMatch(dir: Int) {
        if (pdfMatches.isEmpty()) return
        pdfMatchIndex = (pdfMatchIndex + dir + pdfMatches.size) % pdfMatches.size
        showPdfMatch(true)
    }

    private fun showPdfMatch(scroll: Boolean) {
        val index = pdfIndex ?: return
        val matches = pdfMatches
        if (matches.isEmpty()) return
        val current = matches[pdfMatchIndex]
        val suffix = if (index.finished) "" else "+"
        findCount.text = "${pdfMatchIndex + 1}/${matches.size}$suffix"
        val token = pdfSearchToken
        Thread {
            val list = ArrayList<com.guruswarupa.launch.ui.views.PdfReaderView.Highlight>()
            var focus: android.graphics.RectF? = null
            val lo = (current.page - 2).coerceAtLeast(0)
            val hi = current.page + 2
            for (m in matches) {
                if (m.page < lo) continue
                if (m.page > hi) break
                val isCurrent = m === current
                index.rectsFor(m).forEach { r ->
                    list.add(com.guruswarupa.launch.ui.views.PdfReaderView.Highlight(m.page, r, isCurrent))
                    if (isCurrent && focus == null) focus = r
                }
            }
            runOnUiThread {
                if (token != pdfSearchToken) return@runOnUiThread
                pdfReader.setHighlights(list)
                val f = focus
                if (scroll) {
                    if (f != null) pdfReader.scrollToRect(current.page, f) else pdfReader.scrollToPage(current.page)
                }
            }
        }.start()
    }

    private fun loadVaultDocument(fileName: String) {
        showLoading("Decrypting document…")
        lifecycleScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    vaultManager?.decryptToCache(fileName)
                } ?: throw IllegalStateException("Vault manager not initialized")
                tempFile = decryptedFile
                openDocument(decryptedFile, fileName)
            } catch (e: Exception) {
                showError("Unable to decrypt file. Use the button below to open in another app.", showOpenWithButton = true)
            }
        }
    }

    private fun loadExternalDocument(uri: Uri, displayName: String) {
        showLoading("Loading document…")
        lifecycleScope.launch {
            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    copyUriToCache(uri, displayName)
                }
                tempFile = cachedFile
                openDocument(cachedFile, displayName)
            } catch (e: Exception) {
                showError("Unable to load document. Use the button below to open in another app.", showOpenWithButton = true)
            }
        }
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File {
        val cacheDir = File(cacheDir, "doc_viewer")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(cacheDir, sanitizedFileName)

        contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input).use { bufferedInput ->
                BufferedOutputStream(FileOutputStream(destFile)).use { bufferedOutput ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                        bufferedOutput.write(buffer, 0, bytesRead)
                    }
                    bufferedOutput.flush()
                }
            }
        } ?: throw IllegalStateException("Cannot open file")

        return destFile
    }

    private fun openDocument(file: File, fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""

        if (extension == "pdf" || mimeType == "application/pdf") {
            openPdf(file)
            return
        }

        val photoExtensions = com.guruswarupa.launch.ui.document.BinaryConverters.IMAGE_EXTENSIONS - "svg"
        if (extension in photoExtensions) {
            startActivity(Intent(this, PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_PATH, file.absolutePath)
                putExtra(PhotoViewerActivity.EXTRA_NAME, fileName)
                putExtra(PhotoViewerActivity.EXTRA_DELETE, !isVaultFile)
            })
            tempFile = null
            finish()
            return
        }
        if (extension in com.guruswarupa.launch.ui.document.BinaryConverters.MEDIA_EXTENSIONS) {
            startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_PATH, file.absolutePath)
                putExtra(VideoPlayerActivity.EXTRA_NAME, fileName)
                putExtra(VideoPlayerActivity.EXTRA_DELETE, !isVaultFile)
            })
            tempFile = null
            finish()
            return
        }

        showLoading("Preparing document…")
        lifecycleScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { DocumentConverter.convert(file, fileName) }
                currentDoc = doc
                showingAlternate = false
                hideLoading()
                displayDocument(doc)
            } catch (e: UnsupportedLegacyFormatException) {
                showError(e.message ?: "Unsupported document format.", showOpenWithButton = true)
            } catch (e: OutOfMemoryError) {
                showError(getString(R.string.doc_too_large), showOpenWithButton = true)
            } catch (e: Exception) {
                showError("Unable to display this document. Use the button below to open it in another app.", showOpenWithButton = true)
            }
        }
    }

    private fun openMedia(file: File, fileName: String, isAudio: Boolean) {
        hideLoading()
        pdfReader.visibility = View.GONE
        documentWebView.visibility = View.GONE
        errorContainer.visibility = View.GONE
        findButton.visibility = View.GONE
        optionsButton.visibility = View.GONE
        mediaContainer.visibility = View.VISIBLE
        mediaAudioLabel.visibility = if (isAudio) View.VISIBLE else View.GONE
        mediaAudioLabel.text = "\u266A\n\n$fileName"
        val controller = MediaController(this)
        controller.setAnchorView(mediaContainer)
        mediaVideo.setMediaController(controller)
        mediaVideo.setOnPreparedListener { player ->
            player.isLooping = false
            mediaVideo.start()
        }
        mediaVideo.setOnErrorListener { _, _, _ ->
            mediaContainer.visibility = View.GONE
            showError("This media format can't be played here. Use the button below to open it in another app.", showOpenWithButton = true)
            true
        }
        mediaVideo.setVideoURI(Uri.fromFile(file))
    }

    override fun onPause() {
        super.onPause()
        if (::mediaVideo.isInitialized && mediaVideo.isPlaying) mediaVideo.pause()
    }

    private fun applyViewSettings(html: String): String {
        val theme = viewPrefs.getString("theme", "light") ?: "light"
        val wrap = viewPrefs.getBoolean("wrap", false)
        return html
            .replaceFirst("data-theme=\"light\"", "data-theme=\"$theme\"")
            .replaceFirst("data-wrap=\"off\"", "data-wrap=\"${if (wrap) "on" else "off"}\"")
    }

    private fun displayDocument(doc: ConvertedDocument, keepScroll: Boolean = false) {
        pendingScrollY = if (keepScroll) documentWebView.scrollY else 0
        val html = if (showingAlternate && doc.alternateHtml != null) doc.alternateHtml else doc.html
        findButton.visibility = View.VISIBLE
        optionsButton.visibility = View.VISIBLE
        showWebViewContent(applyViewSettings(html))
    }

    private fun toggleFindBar(forceClose: Boolean = false) {
        val show = !forceClose && findBar.visibility != View.VISIBLE
        findBar.visibility = if (show) View.VISIBLE else View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        if (show) {
            findInput.requestFocus()
            imm?.showSoftInput(findInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            documentWebView.clearMatches()
            pdfSearchToken++
            pdfMatches = emptyList()
            pdfCurrentQuery = ""
            pdfReader.clearHighlights()
            findInput.setText("")
            findCount.text = ""
            imm?.hideSoftInputFromWindow(findInput.windowToken, 0)
        }
    }

    private fun showViewOptions(anchor: View) {
        val doc = currentDoc ?: return
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.doc_text_larger)
        menu.menu.add(0, 2, 1, R.string.doc_text_smaller)
        val theme = viewPrefs.getString("theme", "light") ?: "light"
        if (theme != "light") menu.menu.add(0, 3, 2, R.string.doc_theme_light)
        if (theme != "dark") menu.menu.add(0, 4, 3, R.string.doc_theme_dark)
        if (theme != "sepia") menu.menu.add(0, 5, 4, R.string.doc_theme_sepia)
        if (doc.kind == ConvertedDocument.Kind.CODE || doc.kind == ConvertedDocument.Kind.TEXT || doc.alternateHtml != null) {
            val wrapItem = menu.menu.add(0, 6, 5, R.string.doc_word_wrap)
            wrapItem.isCheckable = true
            wrapItem.isChecked = viewPrefs.getBoolean("wrap", false)
        }
        if (doc.alternateHtml != null) {
            menu.menu.add(0, 7, 6, if (showingAlternate) R.string.doc_view_rendered else R.string.doc_view_source)
        }
        menu.menu.add(0, 8, 7, R.string.open_with_another_app)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> changeTextZoom(10)
                2 -> changeTextZoom(-10)
                3 -> setTheme("light")
                4 -> setTheme("dark")
                5 -> setTheme("sepia")
                6 -> {
                    viewPrefs.edit().putBoolean("wrap", !viewPrefs.getBoolean("wrap", false)).apply()
                    displayDocument(doc, keepScroll = true)
                }
                7 -> {
                    showingAlternate = !showingAlternate
                    displayDocument(doc)
                }
                8 -> openWithExternalApp()
            }
            true
        }
        menu.show()
    }

    private fun setTheme(theme: String) {
        viewPrefs.edit().putString("theme", theme).apply()
        currentDoc?.let { displayDocument(it, keepScroll = true) }
    }

    private fun changeTextZoom(delta: Int) {
        val zoom = (viewPrefs.getInt("text_zoom", 100) + delta).coerceIn(70, 240)
        viewPrefs.edit().putInt("text_zoom", zoom).apply()
        documentWebView.settings.textZoom = zoom
    }

    private fun openPdf(file: File) {
        lifecycleScope.launch {
            try {
                pdfIndex?.close()
                pdfIndex = null
                pdfIndexStarted = false
                pdfRenderer?.close()
                fileDescriptor?.close()

                val sizes = withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor = fd
                    val renderer = PdfRenderer(fd)
                    pdfRenderer = renderer
                    totalPages = renderer.pageCount
                    val widths = FloatArray(totalPages)
                    val heights = FloatArray(totalPages)
                    var lastW = 0f
                    var lastH = 0f
                    for (i in 0 until totalPages) {
                        renderer.openPage(i).use { page ->
                            widths[i] = page.width.toFloat()
                            heights[i] = page.height.toFloat()
                        }
                    }
                    Pair(widths, heights)
                }

                hideLoading()
                val renderer = pdfRenderer
                if (renderer == null) {
                    showError("Failed to initialize PDF renderer")
                    return@launch
                }
                pdfReader.visibility = View.VISIBLE
                pdfNavBar.visibility = View.VISIBLE
                pdfReader.setDocument(renderer, sizes.first, sizes.second)
                pdfReader.onPageChanged = { page ->
                    if (page != currentPageIndex) {
                        currentPageIndex = page
                        updatePageIndicator(page + 1)
                    }
                }

                documentPageInfo.visibility = View.VISIBLE
                documentPageInfo.text = resources.getQuantityString(R.plurals.document_page_count, totalPages, totalPages)
                pdfFile = file
                currentPageIndex = 0
                updatePageIndicator(1)
            } catch (e: OutOfMemoryError) {
                showError("PDF is too large to preview. Use the button below to open in another app.", showOpenWithButton = true)
            } catch (e: Exception) {
                showError("Failed to render PDF: ${e.message}")
            }
        }
    }

    private fun updatePageIndicator(currentPage: Int) {
        pageIndicator.text = getString(R.string.document_page_indicator, currentPage, totalPages)
        btnPrevPage.alpha = if (currentPage <= 1) 0.3f else 1.0f
        btnNextPage.alpha = if (currentPage >= totalPages) 0.3f else 1.0f
    }

    private fun showWebViewContent(html: String) {
        documentWebView.visibility = View.VISIBLE
        documentWebView.setBackgroundColor(Color.TRANSPARENT)
        documentWebView.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = viewPrefs.getInt("text_zoom", 100)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
            }
        }
        documentWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                val scheme = url.scheme.orEmpty().lowercase()
                if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (pendingScrollY > 0) {
                    val target = pendingScrollY
                    pendingScrollY = 0
                    view?.postDelayed({ view.scrollTo(0, target) }, 60)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                @Suppress("DEPRECATION")
                super.onReceivedError(view, errorCode, description, failingUrl)
                showError("Unable to display document. Use the button below to open in another app.", showOpenWithButton = true)
            }
        }
        documentWebView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
    }

    private fun showLoading(message: String) {

        pdfReader.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        errorContainer.visibility = View.GONE
        mediaContainer.visibility = View.GONE

                documentWebView.clearHistory()
        documentWebView.clearCache(true)
        documentWebView.loadUrl("about:blank")

        loadingText.text = message
        loadingContainer.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingContainer.visibility = View.GONE
    }

    private fun showError(message: String, showOpenWithButton: Boolean = false) {
        loadingContainer.visibility = View.GONE
        pdfReader.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        mediaContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorText.text = message
        openExternalButton.visibility = if (showOpenWithButton) View.VISIBLE else View.GONE
    }

    private fun shareDocument() {
        val file = tempFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, this.getString(R.string.toast_no_document_to_share), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share document"))
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_could_not_share, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWithExternalApp() {
        val file = tempFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, this.getString(R.string.toast_no_document_available_to_open_externally), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with…"))
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_no_app_available_to_open_this_file_type), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            mediaVideo.stopPlayback()
        } catch (_: Exception) {
        }
        pdfSearchToken++
        pdfIndex?.close()
        pdfReader.release()
        pdfRenderer = null

        try {
            fileDescriptor?.close()
        } catch (e: Exception) {
        }

        try {
            documentWebView.clearHistory()
            documentWebView.clearCache(true)
            documentWebView.loadUrl("about:blank")
        } catch (e: Exception) {
        } finally {

            try {
                val parentView = documentWebView.parent as? android.view.ViewGroup
                if (parentView != null) {
                    parentView.removeView(documentWebView)
                }
            } catch (e: Exception) {
            }
        }

        try {
            documentWebView.destroy()
        } catch (e: Exception) {
        }

        tempFile?.let { file ->
            try {
                val parentDir = file.parentFile
                if (parentDir?.name == "doc_viewer") {
                    file.delete()
                }
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_VAULT_FILE_NAME = "vault_file_name"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_FILE_NAME = "file_name"

        fun isSupported(fileName: String): Boolean = true

        fun createVaultIntent(context: Context, fileName: String): Intent {

            return Intent().apply {
                setClassName(context, "com.guruswarupa.launch.ui.activities.VaultDocumentViewerActivity")
                putExtra(EXTRA_VAULT_FILE_NAME, fileName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
        }

        fun createFileIntent(context: Context, uri: Uri, fileName: String? = null): Intent {
            return Intent(context, DocumentViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, uri.toString())
                if (fileName != null) putExtra(EXTRA_FILE_NAME, fileName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}
