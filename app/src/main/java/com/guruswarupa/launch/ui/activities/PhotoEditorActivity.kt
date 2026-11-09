package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.guruswarupa.launch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Crop, rotate, flip, adjust and filter a photo, then save a copy or replace the original. */
class PhotoEditorActivity : AppCompatActivity() {

    private enum class Filter(val labelRes: Int, val matrix: () -> ColorMatrix?) {
        NONE(R.string.ed_filter_none, { null }),
        MONO(R.string.ed_filter_mono, { ColorMatrix().apply { setSaturation(0f) } }),
        SEPIA(R.string.ed_filter_sepia, { ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)) }),
        VIVID(R.string.ed_filter_vivid, { ColorMatrix().apply { setSaturation(1.45f); postConcat(contrast(1.12f)) } }),
        COOL(R.string.ed_filter_cool, { ColorMatrix(floatArrayOf(0.92f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 4f, 0f, 0f, 1.12f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)) }),
        WARM(R.string.ed_filter_warm, { ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 8f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 0.86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)) }),
        VINTAGE(R.string.ed_filter_vintage, { ColorMatrix().apply { setSaturation(0.7f); postConcat(ColorMatrix(floatArrayOf(1.08f, 0.04f, 0f, 0f, 14f, 0f, 1f, 0f, 0f, 6f, 0f, 0.04f, 0.82f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))); postConcat(contrast(0.92f)) } });
    }

    private lateinit var p: FilesPalette
    private lateinit var canvasView: EditorCanvas
    private lateinit var panel: FrameLayout
    private lateinit var tabs: LinearLayout
    private var source: File? = null
    private var original: Bitmap? = null        // downsampled for editing
    private var rotation = 0
    private var flipH = false
    private var flipV = false
    private var brightness = 0
    private var contrast = 0
    private var saturation = 0
    private var warmth = 0
    private var filter = Filter.NONE
    private var tab = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        p = FilesPalette(this)
        val path = intent.getStringExtra(EXTRA_PATH)
        source = path?.let { File(it) }?.takeIf { it.exists() }
        if (source == null) { finish(); return }
        setContentView(buildUi())
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { decode(source!!, 2400) }
            if (bmp == null) { Toast.makeText(this@PhotoEditorActivity, R.string.media_unable_to_open_image, Toast.LENGTH_LONG).show(); finish(); return@launch }
            original = bmp
            refreshBase()
        }
    }

    private fun txt(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun pill(label: String, onClick: () -> Unit) = txt(label, 13.5f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(16), dp(9), dp(16), dp(9))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0x33FFFFFF); cornerRadius = dp(18).toFloat() }
        setOnClickListener { onClick() }
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0B0D.toInt()) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(14), dp(6)) }
        bar.addView(txt("✕", 20f).apply { gravity = Gravity.CENTER; setOnClickListener { confirmDiscard() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(txt(getString(R.string.ed_title), 18f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(pill(getString(R.string.ed_reset)) { resetAll() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        bar.addView(txt(getString(R.string.ed_save), 15f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(p.accent); cornerRadius = dp(22).toFloat() }
            setOnClickListener { saveChoice() }
        })
        column.addView(bar)
        canvasView = EditorCanvas(this)
        column.addView(canvasView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        panel = FrameLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        column.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, dp(116)))
        tabs = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(10), dp(10)) }
        listOf(R.string.ed_crop, R.string.ed_adjust, R.string.ed_filters).forEachIndexed { i, label ->
            tabs.addView(txt(getString(label), 14f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); tag = i
                setOnClickListener { selectTab(i) }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        column.addView(tabs)
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        selectTab(0)
        return root
    }

    private fun selectTab(i: Int) {
        tab = i
        for (k in 0 until tabs.childCount) {
            val t = tabs.getChildAt(k) as TextView
            val on = k == i
            t.setTextColor(if (on) p.onAccent else Color.WHITE)
            t.background = android.graphics.drawable.GradientDrawable().apply { setColor(if (on) p.accent else 0x22FFFFFF); cornerRadius = dp(20).toFloat() }
        }
        canvasView.cropping = i == 0
        panel.removeAllViews()
        panel.addView(when (i) { 0 -> cropPanel(); 1 -> adjustPanel(); else -> filterPanel() }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun scroller(content: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; addView(content)
    }

    private fun cropPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        val ratios = LinearLayout(this)
        listOf(R.string.ed_free to 0f, R.string.ed_original_ratio to -1f, 0 to 1f, 0 to 4f / 3, 0 to 3f / 4, 0 to 16f / 9, 0 to 9f / 16).forEach { (res, r) ->
            val label = when { res != 0 -> getString(res); r == 1f -> "1:1"; r == 4f / 3 -> "4:3"; r == 3f / 4 -> "3:4"; r == 16f / 9 -> "16:9"; else -> "9:16" }
            ratios.addView(pill(label) { canvasView.setAspect(r) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        col.addView(scroller(ratios), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val tools = LinearLayout(this).apply { setPadding(0, dp(10), 0, 0) }
        listOf(
            getString(R.string.ed_rotate_left) to { rotation = (rotation + 270) % 360; refreshBase() },
            getString(R.string.ed_rotate_right) to { rotation = (rotation + 90) % 360; refreshBase() },
            getString(R.string.ed_flip_h) to { flipH = !flipH; refreshBase() },
            getString(R.string.ed_flip_v) to { flipV = !flipV; refreshBase() }
        ).forEach { (label, action) -> tools.addView(pill(label) { action() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }) }
        col.addView(scroller(tools), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return col
    }

    private fun slider(label: Int, value: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(txt(getString(label), 12.5f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(dp(84), WRAP_CONTENT))
        val bar = SeekBar(this).apply {
            max = 200; progress = value + 100
            progressTintList = android.content.res.ColorStateList.valueOf(p.accent); thumbTintList = android.content.res.ColorStateList.valueOf(p.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) { onChange(v - 100); canvasView.invalidate() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(bar, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    private fun adjustPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0) }
        // Two rows of two sliders keep every control reachable in a short panel.
        val a = LinearLayout(this); val b = LinearLayout(this)
        a.addView(slider(R.string.ed_brightness, brightness) { brightness = it; pushFilter() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        a.addView(slider(R.string.ed_contrast, contrast) { contrast = it; pushFilter() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        b.addView(slider(R.string.ed_saturation, saturation) { saturation = it; pushFilter() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        b.addView(slider(R.string.ed_warmth, warmth) { warmth = it; pushFilter() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(a); col.addView(b)
        return col
    }

    private fun filterPanel(): View {
        val row = LinearLayout(this).apply { setPadding(dp(12), dp(8), dp(12), 0) }
        Filter.values().forEach { f ->
            row.addView(txt(getString(f.labelRes), 13.5f, if (f == filter) p.onAccent else Color.WHITE, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(18), dp(12), dp(18), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(if (f == filter) p.accent else 0x33FFFFFF); cornerRadius = dp(18).toFloat() }
                setOnClickListener { filter = f; pushFilter(); selectTab(2) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        return scroller(row)
    }

    // ---- editing state ----

    private fun pushFilter() { canvasView.colorFilter = ColorMatrixColorFilter(colorMatrix()); canvasView.invalidate() }

    private fun colorMatrix(): ColorMatrix {
        val m = ColorMatrix()
        filter.matrix()?.let { m.postConcat(it) }
        if (saturation != 0) m.postConcat(ColorMatrix().apply { setSaturation(1f + saturation / 100f) })
        if (contrast != 0) m.postConcat(contrast(1f + contrast / 100f))
        if (brightness != 0) { val b = brightness * 1.6f; m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, b, 0f, 1f, 0f, 0f, b, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f))) }
        if (warmth != 0) { val w = warmth / 100f; m.postConcat(ColorMatrix(floatArrayOf(1f + 0.25f * w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - 0.25f * w, 0f, 0f, 0f, 0f, 0f, 1f, 0f))) }
        return m
    }

    private fun transform(src: Bitmap): Bitmap {
        if (rotation == 0 && !flipH && !flipV) return src
        val m = Matrix().apply { postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f, src.width / 2f, src.height / 2f); postRotate(rotation.toFloat(), src.width / 2f, src.height / 2f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun refreshBase() {
        val src = original ?: return
        canvasView.setBitmap(transform(src))
        pushFilter()
    }

    private fun resetAll() {
        rotation = 0; flipH = false; flipV = false; brightness = 0; contrast = 0; saturation = 0; warmth = 0; filter = Filter.NONE
        canvasView.resetCrop(); refreshBase(); selectTab(tab)
    }

    private fun changed() = rotation != 0 || flipH || flipV || brightness != 0 || contrast != 0 || saturation != 0 || warmth != 0 || filter != Filter.NONE || canvasView.cropChanged()

    private fun confirmDiscard() {
        if (!changed()) { finish(); return }
        android.app.AlertDialog.Builder(this).setMessage(R.string.ed_discard).setPositiveButton(R.string.ed_discard_yes) { _, _ -> finish() }.setNegativeButton(R.string.cancel_button, null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { confirmDiscard() }

    // ---- saving ----

    private fun saveChoice() {
        if (!changed()) { Toast.makeText(this, R.string.ed_nothing, Toast.LENGTH_SHORT).show(); return }
        val sheet = com.guruswarupa.launch.ui.activities.FilesDialogs(this, p)
        sheet.sheet(null, listOf(
            SheetItem(R.drawable.ic_f_copy, getString(R.string.ed_save_copy)) { save(overwrite = false) },
            SheetItem(R.drawable.ic_f_rename, getString(R.string.ed_overwrite)) { save(overwrite = true) }
        ))
    }

    private fun save(overwrite: Boolean) {
        val target = source ?: return
        val crop = canvasView.cropFraction()
        val matrix = colorMatrix()
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    val full = decode(target, 4096) ?: return@withContext null
                    var bmp = transform(full)
                    val x = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                    val y = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                    val w = ((crop.right - crop.left) * bmp.width).toInt().coerceIn(1, bmp.width - x)
                    val h = ((crop.bottom - crop.top) * bmp.height).toInt().coerceIn(1, bmp.height - y)
                    if (x != 0 || y != 0 || w != bmp.width || h != bmp.height) bmp = Bitmap.createBitmap(bmp, x, y, w, h)
                    val result = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    Canvas(result).drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
                    val png = target.extension.equals("png", true)
                    val dest = if (overwrite) target else uniqueCopy(target, if (png) "png" else "jpg")
                    dest.outputStream().use { result.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
                    if (!png) copyExif(target, dest)
                    MediaScannerConnection.scanFile(this@PhotoEditorActivity, arrayOf(dest.absolutePath), null, null)
                    dest
                } catch (_: Throwable) { null }
            }
            if (out == null) { Toast.makeText(this@PhotoEditorActivity, R.string.ed_save_failed, Toast.LENGTH_LONG).show(); return@launch }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PATH, out.absolutePath))
            Toast.makeText(this@PhotoEditorActivity, R.string.ed_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun uniqueCopy(target: File, ext: String): File {
        val base = target.nameWithoutExtension.removeSuffix("_edited")
        var f = File(target.parentFile, "${base}_edited.$ext")
        var n = 2
        while (f.exists()) { f = File(target.parentFile, "${base}_edited_$n.$ext"); n++ }
        return f
    }

    /** Keeps the date, camera and place of the original; the pixels are already rotated so orientation resets. */
    private fun copyExif(from: File, to: File) {
        try {
            val a = ExifInterface(from.absolutePath); val b = ExifInterface(to.absolutePath)
            listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH
            ).forEach { t -> a.getAttribute(t)?.let { b.setAttribute(t, it) } }
            b.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            b.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun decode(f: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = try {
            when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
        } catch (_: Exception) { 0f }
        if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        return bmp
    }

    // ---- the picture and its crop frame ----

    private inner class EditorCanvas(context: android.content.Context) : View(context) {
        var colorFilter: ColorMatrixColorFilter? = null
        var cropping = true
            set(v) { field = v; invalidate() }
        private var bmp: Bitmap? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dim = Paint().apply { color = 0xAA000000.toInt() }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat() }
        private val image = RectF()
        private val crop = RectF(0f, 0f, 1f, 1f)   // fractions of the image
        private var aspect = 0f                      // width / height of the crop, 0 = free
        private var drag = 0                         // bit mask: 1 left 2 top 4 right 8 bottom, 16 move
        private var lastX = 0f
        private var lastY = 0f

        fun setBitmap(b: Bitmap) { val sameShape = bmp?.let { it.width == b.width && it.height == b.height } == true; bmp = b; if (!sameShape) crop.set(0f, 0f, 1f, 1f); layoutImage(); invalidate() }
        fun resetCrop() { crop.set(0f, 0f, 1f, 1f); aspect = 0f; invalidate() }
        fun cropChanged() = crop.left > 0.001f || crop.top > 0.001f || crop.right < 0.999f || crop.bottom < 0.999f
        fun cropFraction() = RectF(crop)

        fun setAspect(r: Float) {
            val b = bmp ?: return
            aspect = if (r < 0f) b.width.toFloat() / b.height else r
            if (aspect > 0f) {
                // Largest centred frame of that shape.
                val imgAspect = b.width.toFloat() / b.height
                val wFrac: Float; val hFrac: Float
                if (aspect > imgAspect) { wFrac = 1f; hFrac = imgAspect / aspect } else { hFrac = 1f; wFrac = aspect / imgAspect }
                crop.set((1f - wFrac) / 2f, (1f - hFrac) / 2f, (1f + wFrac) / 2f, (1f + hFrac) / 2f)
            }
            invalidate()
        }

        private fun layoutImage() {
            val b = bmp ?: return
            if (width == 0 || height == 0) return
            val pad = dp(16)
            val scale = min((width - pad * 2f) / b.width, (height - pad * 2f) / b.height)
            val w = b.width * scale; val h = b.height * scale
            image.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); layoutImage() }

        private fun frame() = RectF(image.left + crop.left * image.width(), image.top + crop.top * image.height(), image.left + crop.right * image.width(), image.top + crop.bottom * image.height())

        override fun onDraw(c: Canvas) {
            val b = bmp ?: return
            paint.colorFilter = colorFilter
            c.drawBitmap(b, null, image, paint)
            if (!cropping) return
            val f = frame()
            c.drawRect(image.left, image.top, image.right, f.top, dim); c.drawRect(image.left, f.bottom, image.right, image.bottom, dim)
            c.drawRect(image.left, f.top, f.left, f.bottom, dim); c.drawRect(f.right, f.top, image.right, f.bottom, dim)
            c.drawRect(f, line)
            for (i in 1..2) { c.drawLine(f.left + f.width() * i / 3, f.top, f.left + f.width() * i / 3, f.bottom, line); c.drawLine(f.left, f.top + f.height() * i / 3, f.right, f.top + f.height() * i / 3, line) }
            val s = dp(18).toFloat()
            c.drawLine(f.left, f.top, f.left + s, f.top, handle); c.drawLine(f.left, f.top, f.left, f.top + s, handle)
            c.drawLine(f.right, f.top, f.right - s, f.top, handle); c.drawLine(f.right, f.top, f.right, f.top + s, handle)
            c.drawLine(f.left, f.bottom, f.left + s, f.bottom, handle); c.drawLine(f.left, f.bottom, f.left, f.bottom - s, handle)
            c.drawLine(f.right, f.bottom, f.right - s, f.bottom, handle); c.drawLine(f.right, f.bottom, f.right, f.bottom - s, handle)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!cropping || bmp == null) return false
            val f = frame(); val slop = dp(28)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    var m = 0
                    if (abs(e.x - f.left) < slop) m = m or 1
                    if (abs(e.x - f.right) < slop) m = m or 4
                    if (abs(e.y - f.top) < slop) m = m or 2
                    if (abs(e.y - f.bottom) < slop) m = m or 8
                    if (m == 0 && f.contains(e.x, e.y)) m = 16
                    drag = m; lastX = e.x; lastY = e.y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return m != 0
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.x - lastX) / image.width(); val dy = (e.y - lastY) / image.height()
                    lastX = e.x; lastY = e.y
                    val minSize = 0.08f
                    if (drag == 16) {
                        val w = crop.width(); val h = crop.height()
                        val l = (crop.left + dx).coerceIn(0f, 1f - w); val t = (crop.top + dy).coerceIn(0f, 1f - h)
                        crop.set(l, t, l + w, t + h)
                    } else if (drag != 0) {
                        if (drag and 1 != 0) crop.left = (crop.left + dx).coerceIn(0f, crop.right - minSize)
                        if (drag and 4 != 0) crop.right = (crop.right + dx).coerceIn(crop.left + minSize, 1f)
                        if (drag and 2 != 0) crop.top = (crop.top + dy).coerceIn(0f, crop.bottom - minSize)
                        if (drag and 8 != 0) crop.bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, 1f)
                        if (aspect > 0f) enforceAspect()
                    }
                    invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { drag = 0; return true }
            }
            return true
        }

        private fun enforceAspect() {
            val b = bmp ?: return
            val imgAspect = b.width.toFloat() / b.height
            // Keep the shape by adjusting height from width (or the reverse when a horizontal edge moved).
            val horizontalEdge = drag and (2 or 8) != 0 && drag and (1 or 4) == 0
            if (horizontalEdge) {
                var w = crop.height() * aspect / imgAspect
                w = min(w, 1f)
                val cx = (crop.left + crop.right) / 2f
                crop.left = (cx - w / 2f).coerceAtLeast(0f); crop.right = (cx + w / 2f).coerceAtMost(1f)
            } else {
                var h = crop.width() * imgAspect / aspect
                h = min(h, 1f)
                if (drag and 2 != 0) crop.top = (crop.bottom - h).coerceAtLeast(0f) else crop.bottom = (crop.top + h).coerceAtMost(1f)
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "editor_path"
        const val EXTRA_RESULT_PATH = "editor_result_path"
        private fun contrast(c: Float): ColorMatrix { val t = (1f - c) * 127.5f; return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)) }
    }
}
