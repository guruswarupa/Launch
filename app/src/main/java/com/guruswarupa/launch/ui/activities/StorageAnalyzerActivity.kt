package com.guruswarupa.launch.ui.activities

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import java.io.File
import java.text.NumberFormat
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

internal class SNode(val name: String, val file: File?, var size: Long, val isDir: Boolean, var cat: Int) {
    var children: List<SNode> = emptyList()
    var parent: SNode? = null
    var fileCount = 0
    var synthetic = false
    var catSizes = LongArray(7)
}

internal object StorageCats {
    val names = arrayOf("Images", "Videos", "Audio", "Documents", "Apps", "Archives", "Other files")
    val colors = intArrayOf(0xFF26A69A.toInt(), 0xFFEF5350.toInt(), 0xFFAB47BC.toInt(), 0xFF42A5F5.toInt(), 0xFFFFA726.toInt(), 0xFF8D6E63.toInt(), 0xFF90A4AE.toInt())
    val system = 0xFF5C6BC0.toInt()

    fun of(f: File): Int {
        val e = FilesOps.ext(f)
        return when {
            e in FilesOps.imageExt -> 0
            e in FilesOps.videoExt -> 1
            e in FilesOps.audioExt -> 2
            e in FilesOps.docExt -> 3
            e == "apk" || e == "xapk" || e == "obb" -> 4
            e in FilesOps.archiveExt -> 5
            else -> 6
        }
    }
}

private class Scanner(private val cancelled: () -> Boolean, private val onProgress: (Long, Long) -> Unit) {
    val totals = LongArray(7)
    val largest = PriorityQueue<SNode>(compareBy { it.size })
    var files = 0L
    var bytes = 0L
    private var lastReport = 0L

    fun scan(dir: File, label: String? = null): SNode {
        val node = SNode(label ?: dir.name, dir, 0, true, 6)
        val cats = LongArray(7)
        val dirs = ArrayList<SNode>()
        val fileNodes = ArrayList<SNode>()
        val kids = dir.listFiles()
        if (kids != null) {
            for (k in kids) {
                if (cancelled()) break
                if (k.isDirectory) {
                    if (runCatching { java.nio.file.Files.isSymbolicLink(k.toPath()) }.getOrDefault(false)) continue
                    val d = scan(k)
                    dirs.add(d)
                    node.size += d.size
                    node.fileCount += d.fileCount
                    for (i in 0..6) cats[i] += d.catSizes[i]
                } else {
                    val len = k.length()
                    val c = StorageCats.of(k)
                    val fn = SNode(k.name, k, len, false, c)
                    fn.catSizes = LongArray(7).also { it[c] = len }
                    fileNodes.add(fn)
                    node.size += len
                    node.fileCount++
                    cats[c] += len
                    totals[c] += len
                    bytes += len
                    files++
                    if (largest.size < 120) largest.add(fn) else if (len > (largest.peek()?.size ?: 0L)) { largest.poll(); largest.add(fn) }
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 250) { lastReport = now; onProgress(files, bytes) }
                }
            }
        }
        fileNodes.sortByDescending { it.size }
        val keep = fileNodes.take(40)
        val rest = fileNodes.drop(40)
        val list = ArrayList<SNode>(dirs)
        list.addAll(keep)
        if (rest.isNotEmpty()) {
            val restSize = rest.sumOf { it.size }
            val restCats = LongArray(7)
            rest.forEach { restCats[it.cat] += it.size }
            val other = SNode("${rest.size} smaller files", null, restSize, false, restCats.indices.maxByOrNull { restCats[it] } ?: 6)
            other.synthetic = true
            other.catSizes = restCats
            list.add(other)
        }
        list.sortByDescending { it.size }
        list.forEach { it.parent = node }
        node.children = list
        node.catSizes = cats
        node.cat = cats.indices.maxByOrNull { cats[it] } ?: 6
        return node
    }
}

internal class TreemapView(context: Context) : View(context) {
    var onTap: ((SNode) -> Unit)? = null
    var onLong: ((SNode) -> Unit)? = null
    private var nodes: List<SNode> = emptyList()
    private val rects = ArrayList<Pair<SNode, RectF>>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(3f, 0f, 1f, 0x99000000.toInt()) }
    private val sizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt(); setShadowLayer(3f, 0f, 1f, 0x99000000.toInt()) }
    private val d = resources.displayMetrics.density
    private var total = 1L

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { hit(e.x, e.y)?.let { onTap?.invoke(it) }; return true }
        override fun onLongPress(e: MotionEvent) { hit(e.x, e.y)?.let { onLong?.invoke(it) } }
    })

    init {
        stroke.strokeWidth = 1.2f * d
        namePaint.textSize = 12f * d
        sizePaint.textSize = 10.5f * d
    }

    fun setData(list: List<SNode>) {
        nodes = list
        total = max(1L, list.sumOf { it.size })
        relayout()
        invalidate()
    }

    private fun hit(x: Float, y: Float): SNode? = rects.firstOrNull { it.second.contains(x, y) }?.first

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { relayout() }

    private fun relayout() {
        rects.clear()
        if (width == 0 || height == 0 || nodes.isEmpty()) return
        val shown = ArrayList<SNode>()
        var smallSum = 0L
        var smallCount = 0
        nodes.forEachIndexed { i, n ->
            if (i < 70 && n.size.toDouble() / total >= 0.002) shown.add(n) else { smallSum += n.size; smallCount++ }
        }
        if (smallCount > 0 && smallSum > 0) {
            val o = SNode("$smallCount smaller items", null, smallSum, false, 6)
            o.synthetic = true
            shown.add(o)
        }
        val pad = 2f * d
        squarify(shown, pad, pad, width - 2 * pad, height - 2 * pad)
    }

    private fun squarify(items: List<SNode>, x: Float, y: Float, w: Float, h: Float) {
        val sum = items.sumOf { it.size }.toDouble()
        if (sum <= 0 || w <= 0 || h <= 0) return
        val scale = w.toDouble() * h / sum
        var cx = x; var cy = y; var cw = w; var ch = h
        var i = 0
        while (i < items.size) {
            val side = min(cw, ch).toDouble()
            var rowArea = 0.0
            var best = Double.MAX_VALUE
            var j = i
            var rmin = Double.MAX_VALUE
            var rmax = 0.0
            while (j < items.size) {
                val a = items[j].size * scale
                val na = rowArea + a
                val nmin = min(rmin, a)
                val nmax = max(rmax, a)
                val worst = max(side * side * nmax / (na * na), na * na / (side * side * nmin))
                if (j > i && worst > best) break
                rowArea = na; rmin = nmin; rmax = nmax; best = worst; j++
            }
            val thickness = (rowArea / side).toFloat()
            if (cw >= ch) {
                var yy = cy
                for (k in i until j) {
                    val hh = (items[k].size * scale / thickness).toFloat()
                    rects.add(items[k] to RectF(cx, yy, cx + thickness, yy + hh)); yy += hh
                }
                cx += thickness; cw -= thickness
            } else {
                var xx = cx
                for (k in i until j) {
                    val ww = (items[k].size * scale / thickness).toFloat()
                    rects.add(items[k] to RectF(xx, cy, xx + ww, cy + thickness)); xx += ww
                }
                cy += thickness; ch -= thickness
            }
            i = j
        }
    }

    override fun onDraw(canvas: Canvas) {
        val gap = 1.5f * d
        val r = 6f * d
        for ((n, rect) in rects) {
            val rr = RectF(rect.left + gap, rect.top + gap, rect.right - gap, rect.bottom - gap)
            if (rr.width() < 2 || rr.height() < 2) continue
            val base = StorageCats.colors[n.cat.coerceIn(0, 6)]
            fill.color = if (n.isDir) base else withAlpha(base, 215)
            canvas.drawRoundRect(rr, r, r, fill)
            if (n.isDir) {
                stroke.color = 0x88FFFFFF.toInt()
                canvas.drawRoundRect(rr, r, r, stroke)
            }
            if (rr.width() > 54 * d && rr.height() > 30 * d) {
                val maxW = rr.width() - 14 * d
                val name = TextUtils.ellipsize(n.name, android.text.TextPaint(namePaint), maxW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(name, rr.left + 7 * d, rr.top + 17 * d, namePaint)
                canvas.drawText(FilesOps.formatSize(n.size), rr.left + 7 * d, rr.top + 31 * d, sizePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event) || super.onTouchEvent(event)
}

class StorageAnalyzerActivity : AppCompatActivity() {

    private data class ListRow(
        val title: String,
        val subtitle: String,
        val size: Long,
        val fraction: Float,
        val color: Int,
        val icon: Int,
        val node: SNode?,
        val pkg: String? = null,
        val drawable: Drawable? = null
    )

    private class AppEntry(val label: String, val pkg: String, val icon: Drawable?, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long, val system: Boolean) {
        val total get() = appBytes + dataBytes
        val dataOnly get() = (dataBytes - cacheBytes).coerceAtLeast(0L)
    }

    private val root: File = Environment.getExternalStorageDirectory()
    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private val ui = Handler(Looper.getMainLooper())

    private var tree: SNode? = null
    private var current: SNode? = null
    private var largest: List<SNode> = emptyList()
    private var totals = LongArray(7)
    private var scanToken = 0
    private var tab = 0
    private var apps: List<AppEntry>? = null
    private var appsLoading = false
    private var appSort = 0
    private var appChips: List<TextView> = emptyList()
    private lateinit var appsBar: View
    private lateinit var appsSummary: TextView

    private lateinit var scanBox: View
    private lateinit var scanText: TextView
    private lateinit var resultBox: View
    private lateinit var permissionBox: View
    private lateinit var summaryBar: LinearLayout
    private lateinit var legend: android.widget.GridLayout
    private lateinit var summaryText: TextView
    private lateinit var crumbScroll: HorizontalScrollView
    private lateinit var crumbs: LinearLayout
    private lateinit var treemap: TreemapView
    private lateinit var listView: RecyclerView
    private lateinit var tabViews: List<TextView>
    private lateinit var hint: TextView
    private lateinit var adapter: ListAdapter
    private lateinit var toast: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        p = FilesPalette(this)
        dialogs = FilesDialogs(this, p)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = p.isLight
            isAppearanceLightNavigationBars = p.isLight
        }
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val c = current
                if (tab < 2 && c?.parent != null) { select(c.parent) } else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (tab == 3 && tree != null) { apps = null; refreshTab() }
        if (!hasAccess()) {
            permissionBox.visibility = View.VISIBLE; scanBox.visibility = View.GONE; resultBox.visibility = View.GONE
        } else {
            permissionBox.visibility = View.GONE
            if (tree == null) startScan()
        }
    }

    override fun onDestroy() { scanToken++; super.onDestroy() }

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onResume()
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun iconBtn(res: Int, desc: String, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res); setColorFilter(p.text)
        val pad = dp(10); setPadding(pad, pad, pad, pad)
        contentDescription = desc
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        val out = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)
        setBackgroundResource(out.resourceId)
        setOnClickListener { onClick() }
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(iconBtn(R.drawable.ic_f_back, "Back") { finish() })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        titles.addView(text("Storage analyzer", 21f, p.text, true))
        summaryText = text("", 12.5f, p.text2)
        titles.addView(summaryText)
        bar.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(iconBtn(R.drawable.ic_f_recent, "Rescan") { startScan() })
        column.addView(bar)

        val body = FrameLayout(this)

        scanText = text("Scanning…", 14f, p.text2).apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(14), dp(24), 0) }
        scanBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(android.widget.ProgressBar(this@StorageAnalyzerActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(p.accent)
            }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(text("Analyzing storage", 18f, p.text, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(18), 0, 0) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            scanText.textAlignment = View.TEXT_ALIGNMENT_CENTER
            addView(scanText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        body.addView(scanBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = dialogs.rounded(p.card, 22, withAlpha(p.text, 28), 1)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        summaryBar = LinearLayout(this).apply { background = dialogs.rounded(withAlpha(p.text, 30), 6); clipToOutline = true }
        summary.addView(summaryBar, LinearLayout.LayoutParams(MATCH_PARENT, dp(12)))
        legend = android.widget.GridLayout(this).apply { columnCount = 2; setPadding(0, dp(10), 0, 0) }
        summary.addView(legend)
        result.addView(summary, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(12), dp(4), dp(12), dp(8)) })

        val tabs = LinearLayout(this).apply { setPadding(dp(12), 0, dp(12), dp(8)) }
        val names = listOf("Map", "Folders", "Largest", "Apps")
        tabViews = names.mapIndexed { i, n ->
            text(n, 14f, p.text, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setOnClickListener { tab = i; refreshTab() }
            }.also { tabs.addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = if (i < names.lastIndex) dp(8) else 0 }) }
        }
        result.addView(tabs)

        crumbs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(12), dp(6)) }
        crumbScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(crumbs) }
        result.addView(crumbScroll)

        val appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(6)); visibility = View.GONE }
        appsSummary = text("", 13f, p.text2)
        appsBox.addView(appsSummary)
        val chipRow = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        appChips = listOf("Total", "App", "Data", "Cache").mapIndexed { i, n ->
            text(n, 12.5f, p.text, true).apply {
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener { appSort = i; refreshTab() }
            }.also { chipRow.addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }) }
        }
        appsBox.addView(chipRow)
        appsBar = appsBox
        result.addView(appsBox)

        val content = FrameLayout(this)
        treemap = TreemapView(this).apply {
            onTap = { n -> if (n.isDir && n.children.isNotEmpty()) select(n) else if (!n.synthetic) showActions(n) }
            onLong = { n -> if (!n.synthetic) showActions(n) }
        }
        content.addView(treemap, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { setMargins(dp(10), 0, dp(10), dp(30)) })
        hint = text("Tap a block to open a folder · long-press for actions", 11.5f, p.text2).apply { gravity = Gravity.CENTER }
        content.addView(hint, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(8) })
        adapter = ListAdapter()
        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StorageAnalyzerActivity)
            adapter = this@StorageAnalyzerActivity.adapter
            clipToPadding = false
            setPadding(dp(10), 0, dp(10), dp(24))
            itemAnimator = null
            visibility = View.GONE
        }
        content.addView(listView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        result.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        resultBox = result
        body.addView(result, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        permissionBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(text("All files access needed", 20f, p.text, true).apply { gravity = Gravity.CENTER })
            addView(text("The analyzer reads file sizes on your device. Nothing leaves your phone.", 14f, p.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(20)) })
            addView(text("Allow access", 16f, p.onAccent, true).apply {
                gravity = Gravity.CENTER
                background = dialogs.rounded(p.accent, 26)
                setPadding(dp(30), dp(13), dp(30), dp(13))
                setOnClickListener { requestAccess() }
            })
            visibility = View.GONE
        }
        body.addView(permissionBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        toast = text("", 14f, p.text).apply {
            background = dialogs.rounded(p.dialog, 22, withAlpha(p.text, 40), 1)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            alpha = 0f; visibility = View.GONE; elevation = dp(10).toFloat()
        }
        body.addView(toast, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(60) })

        column.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        return frame
    }

    private fun notify(message: String) {
        toast.text = message
        toast.visibility = View.VISIBLE
        toast.animate().cancel()
        toast.animate().alpha(1f).setDuration(150).withEndAction {
            toast.animate().alpha(0f).setStartDelay(2000).setDuration(250).withEndAction { toast.visibility = View.GONE }.start()
        }.start()
    }

    private fun startScan() {
        val token = ++scanToken
        tree = null
        current = null
        scanBox.visibility = View.VISIBLE
        resultBox.visibility = View.GONE
        summaryText.text = "Scanning…"
        scanText.text = "Starting…"
        Thread {
            val scanner = Scanner({ token != scanToken }) { files, bytes ->
                ui.post { if (token == scanToken) scanText.text = "${NumberFormat.getInstance().format(files)} files · ${FilesOps.formatSize(bytes)}" }
            }
            val result = scanner.scan(root, "Internal storage")
            val top = scanner.largest.sortedByDescending { it.size }
            runOnUiThread {
                if (token != scanToken) return@runOnUiThread
                tree = result
                totals = scanner.totals
                largest = top
                scanBox.visibility = View.GONE
                resultBox.visibility = View.VISIBLE
                buildSummary(result)
                select(result)
            }
        }.start()
    }

    private fun buildSummary(result: SNode) {
        val total = root.totalSpace
        val used = total - root.usableSpace
        val scanned = result.size
        val system = max(0L, used - scanned)
        summaryText.text = "${FilesOps.formatSize(used)} used of ${FilesOps.formatSize(total)} · ${FilesOps.formatSize(root.usableSpace)} free"
        summaryBar.removeAllViews()
        legend.removeAllViews()
        fun seg(weight: Long, color: Int) {
            if (weight <= 0 || total <= 0) return
            summaryBar.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, MATCH_PARENT, weight.toFloat()))
        }
        for (i in 0..6) seg(totals[i], StorageCats.colors[i])
        seg(system, StorageCats.system)
        seg(root.usableSpace, 0)
        fun legendItem(color: Int, name: String, size: Long) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), dp(8), dp(3)) }
            row.addView(View(this).apply { background = dialogs.rounded(color, 5) }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(7) })
            row.addView(text("$name  ", 12.5f, p.text2))
            row.addView(text(FilesOps.formatSize(size), 12.5f, p.text, true))
            legend.addView(row, android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f), android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)).apply { width = 0 })
        }
        for (i in 0..6) if (totals[i] > 0) legendItem(StorageCats.colors[i], StorageCats.names[i], totals[i])
        if (system > 0) legendItem(StorageCats.system, "System & app data", system)
    }

    private fun select(node: SNode?) {
        current = node
        refreshTab()
    }

    private fun refreshTab() {
        tabViews.forEachIndexed { i, t ->
            t.setTextColor(if (i == tab) p.onAccent else p.text)
            t.background = dialogs.rounded(if (i == tab) p.accent else p.card, 18)
        }
        val node = current
        crumbScroll.visibility = if (tab >= 2) View.GONE else View.VISIBLE
        appsBar.visibility = if (tab == 3) View.VISIBLE else View.GONE
        if (node != null && tab < 2) updateCrumbs(node)
        treemap.visibility = if (tab == 0) View.VISIBLE else View.GONE
        hint.visibility = if (tab == 0) View.VISIBLE else View.GONE
        listView.visibility = if (tab == 0) View.GONE else View.VISIBLE
        when (tab) {
            0 -> treemap.setData(node?.children ?: emptyList())
            1 -> {
                val total = max(1L, node?.size ?: 1L)
                adapter.submit((node?.children ?: emptyList()).map { n ->
                    ListRow(n.name, subtitleFor(n), n.size, n.size.toFloat() / total, StorageCats.colors[n.cat.coerceIn(0, 6)],
                        if (n.isDir) R.drawable.ic_f_folder else R.drawable.ic_f_file, n)
                })
            }
            3 -> showApps()
            2 -> {
                val top = largest.firstOrNull()?.size ?: 1L
                adapter.submit(largest.map { n ->
                    ListRow(n.name, n.file?.parentFile?.absolutePath?.removePrefix(root.absolutePath)?.ifEmpty { "/" } ?: "", n.size, n.size.toFloat() / max(1L, top),
                        StorageCats.colors[n.cat.coerceIn(0, 6)], R.drawable.ic_f_file, n)
                })
            }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadApps() {
        if (appsLoading) return
        appsLoading = true
        Thread {
            val pm = packageManager
            val ssm = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val user = android.os.Process.myUserHandle()
            val list = pm.getInstalledApplications(0).mapNotNull { ai ->
                try {
                    val st = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, user)
                    AppEntry(ai.loadLabel(pm).toString(), ai.packageName, ai.loadIcon(pm), st.appBytes, st.dataBytes, st.cacheBytes,
                        ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
                } catch (e: Exception) { null }
            }
            runOnUiThread { apps = list; appsLoading = false; if (tab == 3) refreshTab() }
        }.start()
    }

    private fun showApps() {
        appChips.forEachIndexed { i, c ->
            c.setTextColor(if (i == appSort) p.onAccent else p.text)
            c.background = dialogs.rounded(if (i == appSort) p.accent else p.card, 15)
        }
        if (!hasUsageAccess()) {
            appsSummary.text = "Usage access is needed to read app sizes. Tap here to allow it."
            appsSummary.setTextColor(p.accent)
            appsSummary.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            adapter.submit(emptyList())
            return
        }
        appsSummary.setTextColor(p.text2)
        appsSummary.setOnClickListener(null)
        val list = apps
        if (list == null) {
            appsSummary.text = "Measuring apps…"
            adapter.submit(emptyList())
            loadApps()
            return
        }
        val key: (AppEntry) -> Long = when (appSort) { 1 -> { a -> a.appBytes }; 2 -> { a -> a.dataOnly }; 3 -> { a -> a.cacheBytes }; else -> { a -> a.total } }
        val sorted = list.sortedByDescending(key)
        val top = max(1L, sorted.firstOrNull()?.let(key) ?: 1L)
        appsSummary.text = "${list.size} apps · ${FilesOps.formatSize(list.sumOf { it.total })} total · ${FilesOps.formatSize(list.sumOf { it.cacheBytes })} cache"
        adapter.submit(sorted.map { a ->
            ListRow(a.label + if (a.system) "  ·  system" else "", a.pkg + "\nApp ${FilesOps.formatSize(a.appBytes)}  ·  Data ${FilesOps.formatSize(a.dataOnly)}  ·  Cache ${FilesOps.formatSize(a.cacheBytes)}",
                key(a).let { if (appSort == 0) a.total else it }, key(a).toFloat() / top, p.accent, R.drawable.ic_f_apk, null, a.pkg, a.icon)
        })
    }

    private fun showAppActions(pkg: String) {
        val entry = apps?.firstOrNull { it.pkg == pkg } ?: return
        val items = ArrayList<SheetItem>()
        packageManager.getLaunchIntentForPackage(pkg)?.let { li -> items.add(SheetItem(R.drawable.ic_f_file, "Open app") { startActivity(li) }) }
        items.add(SheetItem(R.drawable.ic_f_info, "App info & storage settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        })
        if (!entry.system && pkg != packageName) items.add(SheetItem(R.drawable.ic_f_delete, "Uninstall", danger = true) {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        })
        dialogs.sheet("${entry.label}  ·  ${FilesOps.formatSize(entry.total)}\n$pkg", items)
    }

    private fun subtitleFor(n: SNode): String = when {
        n.synthetic -> StorageCats.names[n.cat.coerceIn(0, 6)]
        n.isDir -> "${NumberFormat.getInstance().format(n.fileCount)} files"
        else -> StorageCats.names[n.cat.coerceIn(0, 6)]
    }

    private fun updateCrumbs(node: SNode) {
        crumbs.removeAllViews()
        val chain = ArrayList<SNode>()
        var n: SNode? = node
        while (n != null) { chain.add(0, n); n = n.parent }
        chain.forEachIndexed { i, seg ->
            val last = i == chain.lastIndex
            if (i > 0) crumbs.addView(text("›", 16f, p.text2).apply { setPadding(dp(4), 0, dp(4), 0) })
            crumbs.addView(text(seg.name + "  " + FilesOps.formatSize(seg.size), 13f, if (last) p.onAccent else p.text, last).apply {
                background = dialogs.rounded(if (last) p.accent else p.card, 16)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { if (!last) select(seg) }
            })
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun showActions(n: SNode) {
        val f = n.file ?: return
        val items = ArrayList<SheetItem>()
        if (n.isDir && n.children.isNotEmpty()) items.add(SheetItem(R.drawable.ic_f_folder, "Open in map") { select(n) })
        items.add(SheetItem(R.drawable.ic_f_folder, "Show in Files") {
            startActivity(Intent(this, FilesActivity::class.java).putExtra(FilesActivity.EXTRA_OPEN_PATH, f.absolutePath))
        })
        items.add(SheetItem(R.drawable.ic_f_delete, "Move to recycle bin", danger = true) {
            dialogs.confirm("Move to recycle bin?", "\"${n.name}\" (${FilesOps.formatSize(n.size)}) will be kept for ${FilesTrash.RETENTION_DAYS} days before being deleted for good.", "Move to bin", true) {
                Thread {
                    val ok = FilesTrash.moveToTrash(root, f)
                    runOnUiThread {
                        if (ok) { removeNode(n); notify("Moved ${FilesOps.formatSize(n.size)} to recycle bin") } else notify("Couldn't move it")
                    }
                }.start()
            }
        })
        dialogs.sheet("${n.name}  ·  ${FilesOps.formatSize(n.size)}", items)
    }

    private fun removeNode(n: SNode) {
        var a = n.parent
        while (a != null) {
            a.size -= n.size
            a.fileCount -= if (n.isDir) n.fileCount else 1
            a.catSizes[n.cat.coerceIn(0, 6)] = max(0L, a.catSizes[n.cat.coerceIn(0, 6)] - n.size)
            a = a.parent
        }
        n.parent?.let { par -> par.children = par.children.filter { it !== n } }
        largest = largest.filter { it !== n }
        totals[n.cat.coerceIn(0, 6)] = max(0L, totals[n.cat.coerceIn(0, 6)] - n.size)
        tree?.let { buildSummary(it) }
        refreshTab()
    }

    private inner class ListAdapter : RecyclerView.Adapter<ListAdapter.VH>() {
        private var data: List<ListRow> = emptyList()
        fun submit(rows: List<ListRow>) { data = rows; notifyDataSetChanged() }

        inner class VH(val box: LinearLayout, val icon: ImageView, val name: TextView, val meta: TextView, val size: TextView, val bar: View, val barBg: FrameLayout) : RecyclerView.ViewHolder(box)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val box = LinearLayout(this@StorageAnalyzerActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
            val icon = ImageView(this@StorageAnalyzerActivity).apply { val pd = dp(10); setPadding(pd, pd, pd, pd) }
            box.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
            val mid = LinearLayout(this@StorageAnalyzerActivity).apply { orientation = LinearLayout.VERTICAL }
            val name = text("", 15f, p.text).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
            val meta = text("", 12f, p.text2).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
            val barBg = FrameLayout(this@StorageAnalyzerActivity).apply { background = dialogs.rounded(withAlpha(p.text, 28), 3) }
            val bar = View(this@StorageAnalyzerActivity)
            barBg.addView(bar, FrameLayout.LayoutParams(0, MATCH_PARENT))
            mid.addView(name); mid.addView(meta)
            mid.addView(barBg, LinearLayout.LayoutParams(MATCH_PARENT, dp(5)).apply { topMargin = dp(5) })
            box.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12); marginEnd = dp(10) })
            val size = text("", 13.5f, p.text, true)
            box.addView(size)
            return VH(box, icon, name, meta, size, bar, barBg)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = data[position]
            h.name.text = r.title
            h.meta.text = r.subtitle
            h.size.text = FilesOps.formatSize(r.size)
            if (r.drawable != null) {
                h.icon.setImageDrawable(r.drawable)
                h.icon.clearColorFilter()
                h.icon.setPadding(0, 0, 0, 0)
                h.icon.background = null
            } else {
                h.icon.setImageResource(r.icon)
                h.icon.setColorFilter(r.color)
                val pd = dp(10); h.icon.setPadding(pd, pd, pd, pd)
                h.icon.background = dialogs.rounded(withAlpha(r.color, 50), 12)
            }
            h.bar.background = dialogs.rounded(r.color, 3)
            h.barBg.post {
                (h.bar.layoutParams as FrameLayout.LayoutParams).width = (h.barBg.width * r.fraction.coerceIn(0.01f, 1f)).toInt()
                h.bar.requestLayout()
            }
            val node = r.node
            if (r.pkg != null) {
                h.box.setOnClickListener { showAppActions(r.pkg) }
                h.box.setOnLongClickListener { showAppActions(r.pkg); true }
                return
            }
            h.box.setOnClickListener {
                if (node == null) return@setOnClickListener
                if (node.isDir && node.children.isNotEmpty()) { tab = 1; select(node) } else if (!node.synthetic) showActions(node)
            }
            h.box.setOnLongClickListener { if (node != null && !node.synthetic) showActions(node); true }
        }
    }
}
