package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.document.BinaryConverters
import com.guruswarupa.launch.ui.views.ZoomImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoViewerActivity : VaultBaseActivity() {

    private lateinit var root: View
    private lateinit var zoom: ZoomImageView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var loading: View
    private lateinit var errorText: TextView
    private lateinit var rotateAction: View

    private var file: File? = null
    private var displayName: String = ""
    private var deleteOnExit = false
    private var chromeVisible = true
    private var bitmap: Bitmap? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var animated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_photo_viewer)

        root = findViewById(R.id.photo_root)
        zoom = findViewById(R.id.photo_zoom)
        topBar = findViewById(R.id.photo_top)
        bottomBar = findViewById(R.id.photo_bottom)
        loading = findViewById(R.id.photo_loading)
        errorText = findViewById(R.id.photo_error)
        rotateAction = findViewById(R.id.photo_action_rotate)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(topBar.paddingLeft, bars.top + dp(8), topBar.paddingRight, topBar.paddingBottom)
            bottomBar.setPadding(bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, bars.bottom + dp(12))
            insets
        }

        findViewById<ImageView>(R.id.photo_back).setOnClickListener { finish() }
        findViewById<View>(R.id.photo_action_share).setOnClickListener { share() }
        findViewById<View>(R.id.photo_action_info).setOnClickListener { showDetails() }
        findViewById<View>(R.id.photo_action_open).setOnClickListener { openWith() }
        rotateAction.setOnClickListener { rotate() }

        zoom.onSingleTap = { toggleChrome() }
        zoom.onDismissProgress = { progress ->
            root.background = ColorDrawable(Color.argb((255 * (1f - progress * 0.85f)).toInt(), 0, 0, 0))
            val chromeAlpha = 1f - progress * 2f
            topBar.alpha = chromeAlpha.coerceIn(0f, 1f) * (if (chromeVisible) 1f else 0f)
            bottomBar.alpha = topBar.alpha
        }
        zoom.onDismiss = {
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
        zoom.onZoomChanged = { zoomed ->
            if (zoomed && chromeVisible) toggleChrome()
        }

        val viaAlias = intent.component?.className != PhotoViewerActivity::class.java.name
        val data = intent.data
        if (viaAlias || data != null && intent.getStringExtra(EXTRA_PATH) == null) {
            if (data == null) {
                showError()
                return
            }
            displayName = queryDisplayName(data)
            deleteOnExit = true
            lifecycleScope.launch {
                val copied = withContext(Dispatchers.IO) { copyToCache(data, displayName) }
                if (copied == null) {
                    showError()
                } else {
                    startWithFile(copied)
                }
            }
            return
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        deleteOnExit = intent.getBooleanExtra(EXTRA_DELETE, false)
        val target = path?.let { File(it) }
        if (target == null || !target.exists()) {
            showError()
            return
        }
        if (displayName.isEmpty()) displayName = target.name
        startWithFile(target)
    }

    private fun startWithFile(target: File) {
        file = target
        findViewById<TextView>(R.id.photo_title).text = displayName
        findViewById<TextView>(R.id.photo_subtitle).text = subtitleFor(target)
        loadImage(target)
    }

    private fun queryDisplayName(uri: android.net.Uri): String {
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (_: Exception) {
        }
        val last = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        return last.ifBlank { "image" }
    }

    private fun copyToCache(uri: android.net.Uri, name: String): File? {
        return try {
            val dir = File(cacheDir, "doc_viewer").apply { mkdirs() }
            val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "image" }
            val withExt = if (safe.contains('.')) safe else "$safe.${MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri)) ?: "jpg"}"
            val dest = File(dir, withExt)
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: return null
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun subtitleFor(target: File): String {
        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(target.lastModified()))
        return "$date  ·  ${BinaryConverters.formatSize(target.length())}"
    }

    private fun showError() {
        findViewById<View>(R.id.photo_loading).visibility = View.GONE
        findViewById<TextView>(R.id.photo_error).apply {
            visibility = View.VISIBLE
            text = getString(R.string.media_unable_to_open_image)
        }
    }

    private fun loadImage(target: File) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { decode(target) }
            loading.visibility = View.GONE
            if (result == null) {
                showError()
                return@launch
            }
            sourceWidth = result.width
            sourceHeight = result.height
            animated = result.drawable is AnimatedImageDrawable
            bitmap = result.bitmap
            rotateAction.visibility = if (animated) View.GONE else View.VISIBLE
            zoom.setImageDrawable(result.drawable)
            (result.drawable as? AnimatedImageDrawable)?.start()
        }
    }

    private class Decoded(val drawable: Drawable, val bitmap: Bitmap?, val width: Int, val height: Int)

    private fun decode(target: File): Decoded? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            var width = bounds.outWidth
            var height = bounds.outHeight
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                var decodedWidth = 0
                var decodedHeight = 0
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(target)) { decoder, info, _ ->
                    decodedWidth = info.size.width
                    decodedHeight = info.size.height
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_DECODE_SIDE) {
                        val sample = (longest / MAX_DECODE_SIDE) + 1
                        decoder.setTargetSampleSize(sample)
                    }
                    decoder.isMutableRequired = false
                }
                if (width <= 0) width = decodedWidth
                if (height <= 0) height = decodedHeight
                val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                Decoded(drawable, bmp, width, height)
            } else {
                val options = BitmapFactory.Options()
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                options.inSampleSize = if (longest > MAX_DECODE_SIDE) Integer.highestOneBit(longest / MAX_DECODE_SIDE) * 2 else 1
                var decoded = BitmapFactory.decodeFile(target.absolutePath, options) ?: return null
                val orientation = try {
                    ExifInterface(target.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    decoded = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                }
                Decoded(android.graphics.drawable.BitmapDrawable(resources, decoded), decoded, width, height)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun rotate() {
        val current = bitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = try {
            Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        } catch (_: Throwable) {
            return
        }
        bitmap = rotated
        zoom.setImageBitmap(rotated)
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val alpha = if (chromeVisible) 1f else 0f
        topBar.animate().alpha(alpha).setDuration(180).start()
        bottomBar.animate().alpha(alpha).setDuration(180).start()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        topBar.isClickable = chromeVisible
        bottomBar.isClickable = chromeVisible
    }

    private fun mimeOf(target: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(target.extension.lowercase()) ?: "image/*"

    private fun uriFor(target: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", target)

    private fun share() {
        val target = file ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(target)
                putExtra(Intent.EXTRA_STREAM, uriFor(target))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_document_to_share, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWith() {
        val target = file ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(target), mimeOf(target))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDetails() {
        val target = file ?: return
        val rows = ArrayList<Pair<String, String>>()
        rows.add("Name" to displayName)
        rows.add("Size" to BinaryConverters.formatSize(target.length()))
        if (sourceWidth > 0 && sourceHeight > 0) {
            val megapixels = sourceWidth.toLong() * sourceHeight / 1_000_000.0
            rows.add("Resolution" to "$sourceWidth × $sourceHeight  (${String.format(Locale.US, "%.1f", megapixels)} MP)")
        }
        rows.add("Type" to mimeOf(target))
        rows.add("Modified" to SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(target.lastModified())))
        try {
            val exif = ExifInterface(target.absolutePath)
            val taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            if (!taken.isNullOrBlank()) rows.add("Taken" to taken)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty()
            if (make.isNotBlank() || model.isNotBlank()) rows.add("Camera" to "$make $model".trim())
            val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            val parts = ArrayList<String>()
            aperture?.let { parts.add("f/$it") }
            exposure?.toDoubleOrNull()?.let { parts.add(if (it < 1 && it > 0) "1/${Math.round(1 / it)}s" else "${it}s") }
            iso?.let { parts.add("ISO $it") }
            focal?.let { f ->
                val value = f.split('/').let { p -> if (p.size == 2) (p[0].toDoubleOrNull() ?: 0.0) / (p[1].toDoubleOrNull() ?: 1.0) else f.toDoubleOrNull() }
                if (value != null && value > 0) parts.add("${Math.round(value)}mm")
            }
            if (parts.isNotEmpty()) rows.add("Exposure" to parts.joinToString("  ·  "))
        } catch (_: Exception) {
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
            setBackgroundColor(Color.parseColor("#1C1C1E"))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.media_details_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })
        rows.forEach { (label, value) ->
            container.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 12f
                setPadding(0, dp(10), 0, 0)
            })
            container.addView(TextView(this).apply {
                text = value
                setTextColor(Color.WHITE)
                textSize = 15f
                setTextIsSelectable(true)
            })
        }
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(container)
        sheet.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (deleteOnExit) {
            try {
                val target = file
                if (target != null && target.parentFile?.name == "doc_viewer") target.delete()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "photo_path"
        const val EXTRA_NAME = "photo_name"
        const val EXTRA_DELETE = "photo_delete_on_exit"
        private const val MAX_DECODE_SIDE = 4096
    }
}
