package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.ui.adapters.PdfPageAdapterLazy
import com.guruswarupa.launch.ui.document.DocumentContentExtractor
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
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

    private lateinit var wallpaperBackground: ImageView
    private lateinit var wallpaperOverlay: View
    private lateinit var documentTitle: TextView
    private lateinit var documentPageInfo: TextView
    private lateinit var pdfRecyclerView: RecyclerView
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


    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector


    private var isVaultFile = false
    private var vaultFileName: String? = null
    private var externalUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_document_viewer)

        initViews()
        setupWallpaper()
        setupZoomAndPan()


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
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        wallpaperOverlay = findViewById(R.id.document_viewer_overlay)
        documentTitle = findViewById(R.id.document_title)
        documentPageInfo = findViewById(R.id.document_page_info)
        pdfRecyclerView = findViewById(R.id.pdf_recycler_view)
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
    }

    private fun setupWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground)
        wallpaperOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.note_editor_overlay))
    }

    private fun setupZoomAndPan() {

        pdfRecyclerView.post {
            pdfRecyclerView.pivotX = 0f
            pdfRecyclerView.pivotY = 0f
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentScale = scaleFactor
                val requestedScale = currentScale * detector.scaleFactor
                val nextScale = requestedScale.coerceIn(1.0f, 6.0f)
                val relativeScale = nextScale / currentScale

                val focusX = detector.focusX
                val focusY = detector.focusY


                pdfRecyclerView.translationX =
                    pdfRecyclerView.translationX * relativeScale + focusX * (1 - relativeScale)
                pdfRecyclerView.translationY =
                    pdfRecyclerView.translationY * relativeScale + focusY * (1 - relativeScale)

                scaleFactor = nextScale
                pdfRecyclerView.scaleX = scaleFactor
                pdfRecyclerView.scaleY = scaleFactor

                applyBounds()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > 1.0f) {
                    scaleFactor = 1.0f
                    pdfRecyclerView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(300)
                        .start()
                } else {
                    val targetScale = 2.5f
                    val relativeScale = targetScale / scaleFactor
                    val targetX = pdfRecyclerView.translationX * relativeScale + e.x * (1 - relativeScale)
                    val targetY = pdfRecyclerView.translationY * relativeScale + e.y * (1 - relativeScale)

                    scaleFactor = targetScale
                    pdfRecyclerView.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .translationX(targetX)
                        .translationY(targetY)
                        .setDuration(300)
                        .start()
                }
                return true
            }
        })

        pdfRecyclerView.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            val isPinching = scaleGestureDetector.isInProgress
            val isMultiTouch = event.pointerCount > 1
            val shouldHandleZoomGesture = scaleFactor > 1.0f || isPinching || isMultiTouch

            if (shouldHandleZoomGesture) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.0f)
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        val (focusX, focusY) = getTouchFocus(event)
                        lastTouchX = focusX
                        lastTouchY = focusY
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPinching) {

                            val (focusX, focusY) = getTouchFocus(event)
                            lastTouchX = focusX
                            lastTouchY = focusY
                            return@setOnTouchListener true
                        } else if (isMultiTouch && scaleFactor > 1.0f) {
                            val (focusX, focusY) = getTouchFocus(event)
                            val dx = focusX - lastTouchX
                            val dy = focusY - lastTouchY
                            pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                            pdfRecyclerView.translationX += dx
                            pdfRecyclerView.translationY += dy
                            applyBounds()
                            lastTouchX = focusX
                            lastTouchY = focusY
                            return@setOnTouchListener true
                        } else {
                            if (scaleFactor <= 1.0f) return@setOnTouchListener false

                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY
                            pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                            pdfRecyclerView.translationX += dx
                            pdfRecyclerView.translationY += dy
                            applyBounds()
                            lastTouchX = event.x
                            lastTouchY = event.y
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.0f)
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun getTouchFocus(event: MotionEvent): Pair<Float, Float> {
        var sumX = 0f
        var sumY = 0f
        val count = event.pointerCount.coerceAtLeast(1)
        for (i in 0 until count) {
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        return Pair(sumX / count, sumY / count)
    }

    private fun applyBounds() {
        if (scaleFactor <= 1.0f) {
            pdfRecyclerView.translationX = 0f
            pdfRecyclerView.translationY = 0f
            return
        }

        val width = pdfRecyclerView.width.toFloat()
        val height = pdfRecyclerView.height.toFloat()
        val minX = width - (width * scaleFactor)
        val minY = height - (height * scaleFactor)

        pdfRecyclerView.translationX = max(minX, min(pdfRecyclerView.translationX, 0f))
        pdfRecyclerView.translationY = max(minY, min(pdfRecyclerView.translationY, 0f))
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }

        shareButton.setOnClickListener { shareDocument() }

        openExternalButton.setOnClickListener { openWithExternalApp() }

        btnPrevPage.setOnClickListener {
            val layoutManager = pdfRecyclerView.layoutManager as? LinearLayoutManager ?: return@setOnClickListener
            val currentPos = getCurrentPageIndex(layoutManager)
            if (currentPos > 0) {
                val target = currentPos - 1
                currentPageIndex = target
                updatePageIndicator(currentPageIndex + 1)
                pdfRecyclerView.smoothScrollToPosition(target)
            }
        }

        btnNextPage.setOnClickListener {
            val layoutManager = pdfRecyclerView.layoutManager as? LinearLayoutManager ?: return@setOnClickListener
            val currentPos = getCurrentPageIndex(layoutManager)
            if (currentPos < totalPages - 1) {
                val target = currentPos + 1
                currentPageIndex = target
                updatePageIndicator(currentPageIndex + 1)
                pdfRecyclerView.smoothScrollToPosition(target)
            }
        }
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
        
        // Sanitize filename to prevent path traversal
        val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(cacheDir, sanitizedFileName)
        
        contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input).use { bufferedInput ->
                BufferedOutputStream(FileOutputStream(destFile)).use { bufferedOutput ->
                    val buffer = ByteArray(8192) // 8KB buffer
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

        when {
            extension == "pdf" || mimeType == "application/pdf" -> {
                openPdf(file)
            }
            extension in listOf("txt", "md", "json", "xml", "csv", "log", "html", "htm", "css", "js", "kt", "java", "py", "c", "cpp", "h", "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bat", "ps1", "rb", "go", "rs", "swift", "dart", "ts", "tsx", "jsx", "vue", "sql") -> {
                openTextFile(file)
            }
            mimeType.startsWith("text/") -> {
                openTextFile(file)
            }
            else -> {

                try {
                    val content = file.readText(Charsets.UTF_8)
                    if (content.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
                        showError("Unsupported document format. Use the button below to open in another app.", showOpenWithButton = true)
                    } else {
                        openTextFile(file)
                    }
                } catch (e: Exception) {
                    showError("Unsupported document format. Use the button below to open in another app.", showOpenWithButton = true)
                }
            }
        }
    }



    private fun openPdf(file: File) {
        lifecycleScope.launch {
            try {

                pdfRenderer?.close()
                fileDescriptor?.close()

                withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor = fd
                    val renderer = PdfRenderer(fd)
                    pdfRenderer = renderer
                    totalPages = renderer.pageCount
                }

                hideLoading()
                pdfRecyclerView.visibility = View.VISIBLE
                pdfNavBar.visibility = View.VISIBLE

                documentPageInfo.visibility = View.VISIBLE
                documentPageInfo.text = resources.getQuantityString(R.plurals.document_page_count, totalPages, totalPages)

                val layoutManager = LinearLayoutManager(this@DocumentViewerActivity)
                pdfRecyclerView.layoutManager = layoutManager
                val renderer = pdfRenderer
                if (renderer != null) {
                    pdfRecyclerView.adapter = PdfPageAdapterLazy(renderer, totalPages)
                } else {
                    showError("Failed to initialize PDF renderer")
                    return@launch
                }

                currentPageIndex = 0
                updatePageIndicator(currentPageIndex + 1)

                pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val page = getCurrentPageIndex(layoutManager)
                        if (page != currentPageIndex) {
                            currentPageIndex = page
                            updatePageIndicator(currentPageIndex + 1)
                        }
                    }
                })

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

    private fun getCurrentPageIndex(layoutManager: LinearLayoutManager): Int {
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return 0

        val viewportTop = pdfRecyclerView.paddingTop
        val viewportBottom = pdfRecyclerView.height - pdfRecyclerView.paddingBottom
        var bestIndex = first
        var bestVisibleHeight = -1
        var bestDistanceToCenter = Int.MAX_VALUE
        val centerY = (viewportTop + viewportBottom) / 2

        for (pos in first..last) {
            val child = layoutManager.findViewByPosition(pos) ?: continue
            val visibleTop = max(child.top, viewportTop)
            val visibleBottom = min(child.bottom, viewportBottom)
            val visibleHeight = visibleBottom - visibleTop
            if (visibleHeight <= 0) continue

            val childCenterY = (child.top + child.bottom) / 2
            val distanceToCenter = kotlin.math.abs(childCenterY - centerY)

            if (visibleHeight > bestVisibleHeight ||
                (visibleHeight == bestVisibleHeight && distanceToCenter < bestDistanceToCenter) ||
                (visibleHeight == bestVisibleHeight && distanceToCenter == bestDistanceToCenter && pos > bestIndex)
            ) {
                bestVisibleHeight = visibleHeight
                bestDistanceToCenter = distanceToCenter
                bestIndex = pos
            }
        }

        return bestIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    }



    private fun openTextFile(file: File) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    file.readText(Charsets.UTF_8)
                }

                val extension = file.extension.lowercase()
                val html = DocumentContentExtractor.buildTextFileHtml(content, extension)

                hideLoading()
                showWebViewContent(html)
            } catch (e: Exception) {
                showError("Unable to read file. Use the button below to open in another app.", showOpenWithButton = true)
            }
        }
    }



    private fun showWebViewContent(html: String) {

        documentWebView.clearHistory()
        documentWebView.clearCache(true)
        documentWebView.loadUrl("about:blank")

        documentWebView.visibility = View.VISIBLE
        documentWebView.setBackgroundColor(Color.TRANSPARENT)
        documentWebView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
            }
        }
        documentWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                @Suppress("DEPRECATION")
                super.onReceivedError(view, errorCode, description, failingUrl)
                showError("Unable to display document. Use the button below to open in another app.", showOpenWithButton = true)
            }
        }
        documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }



    private fun showLoading(message: String) {

        pdfRecyclerView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        errorContainer.visibility = View.GONE


        pdfRecyclerView.adapter = null
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
        pdfRecyclerView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
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

        // Close PDF resources separately to handle individual failures
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            android.util.Log.w("DocumentViewer", "Error closing PDF renderer", e)
        }
        
        try {
            fileDescriptor?.close()
        } catch (e: Exception) {
            android.util.Log.w("DocumentViewer", "Error closing file descriptor", e)
        }

        // WebView cleanup with proper error handling
        try {
            documentWebView.clearHistory()
            documentWebView.clearCache(true)
            documentWebView.loadUrl("about:blank")
        } catch (e: Exception) {
            android.util.Log.w("DocumentViewer", "Error during WebView cleanup operations", e)
        } finally {
            // Always try to remove and destroy WebView
            try {
                val parentView = documentWebView.parent as? android.view.ViewGroup
                if (parentView != null) {
                    parentView.removeView(documentWebView)
                }
            } catch (e: Exception) {
                android.util.Log.w("DocumentViewer", "WebView already removed from parent", e)
            }
        }
        
        // Ensure destroy is always called
        try {
            documentWebView.destroy()
        } catch (e: Exception) {
            android.util.Log.w("DocumentViewer", "Error destroying WebView", e)
        }

        // Clean up temp file
        tempFile?.let { file ->
            try {
                val parentDir = file.parentFile
                if (parentDir?.name == "doc_viewer") {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.w("DocumentViewer", "Error deleting temp file", e)
            }
        }
    }

    companion object {
        const val EXTRA_VAULT_FILE_NAME = "vault_file_name"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_FILE_NAME = "file_name"


        val SUPPORTED_EXTENSIONS = setOf(
            "pdf",
            "txt", "md", "json", "xml", "csv", "log", "html", "htm",
            "css", "js", "kt", "java", "py", "c", "cpp", "h",
            "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bat",
            "rb", "go", "rs", "swift", "dart", "ts", "tsx", "jsx",
            "vue", "sql", "ps1"
        )

        fun isSupported(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }

        fun createVaultIntent(context: Context, fileName: String): Intent {
            return Intent(context, DocumentViewerActivity::class.java).apply {
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
