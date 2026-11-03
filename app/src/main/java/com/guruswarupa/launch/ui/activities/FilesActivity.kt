package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.guruswarupa.launch.R
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

open class FilesActivity : AppCompatActivity() {

    protected open val startCategory: String? = null

    companion object {
        const val EXTRA_OPEN_PATH = "open_path"
    }

    private enum class Sort(val label: String) { NAME("Name"), DATE("Date modified"), SIZE("Size"), TYPE("Type") }

    private class Cat(val key: String, val label: String, val icon: Int, val color: Int)

    private sealed class Row {
        object Storage : Row()
        data class Header(val text: String) : Row()
        data class Category(val cat: Cat) : Row()
        data class Entry(val file: File, val sub: String? = null, val home: Boolean = false) : Row()
    }

    private val root: File = Environment.getExternalStorageDirectory()
    private var current: File? = null
    private var category: String? = null
    private var sort = Sort.NAME
    private var ascending = true
    private var gridSetting: Boolean? = null
    private var showHidden = false
    private var query = ""
    private var searching = false
    private var clipboard: List<File> = emptyList()
    private var clipboardMove = false
    private val selected = LinkedHashSet<File>()
    private var rows: List<Row> = emptyList()
    private var trashMap: Map<String, FilesTrash.Item> = emptyMap()
    private val trashButtons = ArrayList<View>()
    private val normalSelButtons = ArrayList<View>()
    private var searchRows: List<Row>? = null
    private var loadToken = 0
    private var searchToken = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var thumbs: FilesThumbs
    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var backButton: ImageView
    private lateinit var normalBar: View
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var searchRow: View
    private lateinit var searchBox: EditText
    private lateinit var crumbScroll: HorizontalScrollView
    private lateinit var crumbs: LinearLayout
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var list: RecyclerView
    private lateinit var emptyBox: View
    private lateinit var emptyText: TextView
    private lateinit var permissionBox: View
    private lateinit var pasteBar: View
    private lateinit var pasteText: TextView
    private lateinit var fab: View
    private lateinit var toast: TextView
    private lateinit var viewToggle: ImageView
    private lateinit var adapter: RowAdapter
    private lateinit var layoutManager: GridLayoutManager

    private val cats by lazy {
        listOf(
            Cat("images", "Images", R.drawable.ic_f_image, 0xFF26A69A.toInt()),
            Cat("videos", "Videos", R.drawable.ic_f_video, 0xFFEF5350.toInt()),
            Cat("audio", "Audio", R.drawable.ic_f_music, 0xFFAB47BC.toInt()),
            Cat("documents", "Docs", R.drawable.ic_f_doc, 0xFF42A5F5.toInt()),
            Cat("downloads", "Downloads", R.drawable.ic_f_download, 0xFF66BB6A.toInt()),
            Cat("apks", "Installers", R.drawable.ic_f_apk, 0xFFFFA726.toInt()),
            Cat("archives", "Archives", R.drawable.ic_f_archive, 0xFF8D6E63.toInt()),
            Cat("recent", "Recent", R.drawable.ic_f_recent, 0xFF78909C.toInt()),
            Cat("trash", "Recycle bin", R.drawable.ic_f_delete, 0xFF8E8E93.toInt())
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        p = FilesPalette(this)
        dialogs = FilesDialogs(this, p)
        thumbs = FilesThumbs(this)
        prefs = getSharedPreferences("files_prefs", Context.MODE_PRIVATE)
        sort = Sort.values().getOrElse(prefs.getInt("sort", 0)) { Sort.NAME }
        ascending = prefs.getBoolean("asc", true)
        gridSetting = if (prefs.contains("grid")) prefs.getBoolean("grid", false) else null
        showHidden = prefs.getBoolean("hidden", false)
        category = startCategory
        intent.getStringExtra(EXTRA_OPEN_PATH)?.let { path ->
            val f = File(path)
            current = if (f.isDirectory) f else f.parentFile
            category = null
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = p.isLight
            isAppearanceLightNavigationBars = p.isLight
        }
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!goBack()) finish() }
        })
    }

    override fun onResume() {
        super.onResume()
        if (hasAccess()) {
            Thread { FilesTrash.purgeExpired(root) }.start()
            permissionBox.visibility = View.GONE
            refreshLayout.visibility = View.VISIBLE
            refresh()
        } else {
            permissionBox.visibility = View.VISIBLE
            refreshLayout.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        searchToken++
        loadToken++
        super.onDestroy()
    }

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onResume()
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun iconBtn(res: Int, desc: String, onClick: (View) -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(p.text)
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        contentDescription = desc
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        val out = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)
        setBackgroundResource(out.resourceId)
        setOnClickListener(onClick)
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val headerBox = FrameLayout(this)
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        backButton = iconBtn(R.drawable.ic_f_back, "Back") { if (!goBack()) finish() }
        bar.addView(backButton)
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        titleView = text("My Files", 22f, p.text, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
        subtitleView = text("", 12.5f, p.text2).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
        titles.addView(titleView); titles.addView(subtitleView)
        bar.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(iconBtn(R.drawable.ic_f_search, "Search") { toggleSearch() })
        viewToggle = iconBtn(R.drawable.ic_f_view_grid, "Layout") { val next = !useGrid(); gridSetting = next; prefs.edit().putBoolean("grid", next).apply(); render() }
        bar.addView(viewToggle)
        bar.addView(iconBtn(R.drawable.ic_f_more, "More") { showMoreSheet() })
        normalBar = bar
        headerBox.addView(bar)

        val sel = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = dialogs.rounded(withAlpha(p.accent, 70), 0)
            visibility = View.GONE
        }
        sel.addView(iconBtn(R.drawable.ic_f_close, "Cancel selection") { clearSelection() })
        selectionCount = text("", 18f, p.text, true)
        sel.addView(selectionCount, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        sel.addView(iconBtn(R.drawable.ic_f_select_all, "Select all") { selectAll() })
        val restoreBtn = iconBtn(R.drawable.ic_f_recent, "Restore") { restoreItems(selected.toList()) }
        trashButtons.add(restoreBtn); sel.addView(restoreBtn)
        val shareBtn = iconBtn(R.drawable.ic_f_share, "Share") { shareFiles(selected.toList()) }
        val copyBtn = iconBtn(R.drawable.ic_f_copy, "Copy") { setClipboard(selected.toList(), false) }
        val moveBtn = iconBtn(R.drawable.ic_f_move, "Move") { setClipboard(selected.toList(), true) }
        normalSelButtons.addAll(listOf(shareBtn, copyBtn, moveBtn))
        sel.addView(shareBtn); sel.addView(copyBtn); sel.addView(moveBtn)
        sel.addView(iconBtn(R.drawable.ic_f_delete, "Delete") { if (category == "trash") confirmDeleteForever(selected.toList()) else confirmDelete(selected.toList()) })
        sel.addView(iconBtn(R.drawable.ic_f_more, "More") { showSelectionSheet() })
        selectionBar = sel
        headerBox.addView(sel)
        column.addView(headerBox)

        searchBox = EditText(this).apply {
            hint = "Search files and folders"
            setHintTextColor(p.text2)
            setTextColor(p.text)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            background = dialogs.rounded(p.card, 24)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onQueryChanged(s?.toString().orEmpty().trim()) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        searchRow = FrameLayout(this).apply {
            setPadding(dp(14), dp(2), dp(14), dp(6))
            addView(searchBox, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            visibility = View.GONE
        }
        column.addView(searchRow)

        crumbs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), dp(6)) }
        crumbScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(crumbs)
            visibility = View.GONE
        }
        column.addView(crumbScroll)

        val body = FrameLayout(this)
        adapter = RowAdapter()
        layoutManager = GridLayoutManager(this, 12)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = adapter.spanAt(position)
        }
        list = RecyclerView(this).apply {
            layoutManager = this@FilesActivity.layoutManager
            adapter = this@FilesActivity.adapter
            clipToPadding = false
            setPadding(dp(10), dp(2), dp(10), dp(120))
            itemAnimator = null
            setHasFixedSize(false)
        }
        refreshLayout = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(p.accent)
            setProgressBackgroundColorSchemeColor(p.cardStrong)
            setOnRefreshListener { refresh(); postDelayed({ isRefreshing = false }, 500) }
            addView(list, MATCH_PARENT, MATCH_PARENT)
        }
        body.addView(refreshLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        emptyText = text("Nothing here", 15f, p.text2).apply { gravity = Gravity.CENTER }
        emptyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ImageView(this@FilesActivity).apply {
                setImageResource(R.drawable.ic_f_folder); setColorFilter(withAlpha(p.text, 90))
            }, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(emptyText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })
            visibility = View.GONE
            isClickable = false
        }
        body.addView(emptyBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        permissionBox = buildPermissionBox()
        body.addView(permissionBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val overlay = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(16), dp(16), dp(16), dp(20))
            tag = "overlay"
        }
        pasteText = text("", 14f, p.onAccent, true)
        val pasteInner = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = dialogs.rounded(p.accent, 28)
            elevation = dp(6).toFloat()
            setPadding(dp(18), 0, dp(6), 0)
            addView(pasteText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(ImageView(this@FilesActivity).apply {
                setImageResource(R.drawable.ic_f_close); setColorFilter(p.onAccent)
                val pd = dp(12); setPadding(pd, pd, pd, pd)
                setOnClickListener { clipboard = emptyList(); render() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) })
            setOnClickListener { paste() }
        }
        pasteBar = pasteInner
        overlay.addView(pasteBar, LinearLayout.LayoutParams(WRAP_CONTENT, dp(52)).apply { marginEnd = dp(12) })
        fab = ImageView(this).apply {
            setImageResource(R.drawable.ic_f_newfolder)
            setColorFilter(p.onAccent)
            val pd = dp(16); setPadding(pd, pd, pd, pd)
            background = dialogs.rounded(p.accent, 28)
            elevation = dp(6).toFloat()
            setOnClickListener { showCreateSheet() }
        }
        overlay.addView(fab, LinearLayout.LayoutParams(dp(56), dp(56)))
        body.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        toast = text("", 14f, p.text, false).apply {
            background = dialogs.rounded(p.dialog, 22, withAlpha(p.text, 40), 1)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            alpha = 0f
            visibility = View.GONE
            elevation = dp(10).toFloat()
        }
        body.addView(toast, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(96) })

        column.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, 0)
            list.setPadding(dp(10), dp(2), dp(10), b.bottom + dp(120))
            overlay.setPadding(dp(16), dp(16), dp(16), b.bottom + dp(20))
            (toast.layoutParams as FrameLayout.LayoutParams).bottomMargin = b.bottom + dp(96)
            insets
        }
        return frame
    }

    private fun buildPermissionBox(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(28), dp(28), dp(28))
        val card = LinearLayout(this@FilesActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = dialogs.rounded(p.cardStrong, 28, withAlpha(p.text, 35), 1)
            setPadding(dp(26), dp(30), dp(26), dp(26))
        }
        card.addView(ImageView(this@FilesActivity).apply {
            setImageResource(R.drawable.ic_f_folder); setColorFilter(0xFFFFC107.toInt())
            background = dialogs.rounded(withAlpha(0xFFFFC107.toInt(), 45), 24)
            val pd = dp(18); setPadding(pd, pd, pd, pd)
        }, LinearLayout.LayoutParams(dp(84), dp(84)))
        card.addView(text("Access your files", 21f, p.text, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(8)) })
        card.addView(text("Launch Files needs All files access so it can browse, copy, move and organise everything on your device. Nothing ever leaves your phone.", 14f, p.text2).apply { gravity = Gravity.CENTER })
        card.addView(text("Allow access", 16f, p.onAccent, true).apply {
            gravity = Gravity.CENTER
            background = dialogs.rounded(p.accent, 26)
            setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener { requestAccess() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(22) })
        addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        visibility = View.GONE
    }

    private fun notify(message: String) {
        toast.text = message
        toast.visibility = View.VISIBLE
        toast.animate().cancel()
        toast.animate().alpha(1f).setDuration(150).withEndAction {
            toast.animate().alpha(0f).setStartDelay(2200).setDuration(250).withEndAction { toast.visibility = View.GONE }.start()
        }.start()
    }

    private fun toggleSearch() {
        searching = !searching
        searchRow.visibility = if (searching) View.VISIBLE else View.GONE
        if (searching) {
            searchBox.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(searchBox, 0)
        } else {
            searchBox.setText("")
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    private fun onQueryChanged(q: String) {
        query = q
        val token = ++searchToken
        if (q.isEmpty()) { searchRows = null; render(); return }
        handler.removeCallbacksAndMessages("search")
        handler.postAtTime({
            subtitleView.text = "Searching…"
            val scope = current ?: root
            Thread {
                val found = FilesOps.search(scope, q, 400) { token != searchToken }
                val sortedFound = sorted(found)
                runOnUiThread {
                    if (token == searchToken) {
                        searchRows = sortedFound.map { Row.Entry(it, it.parentFile?.absolutePath?.removePrefix(root.absolutePath)?.ifEmpty { "/" }) }
                        subtitleView.text = "${sortedFound.size} results"
                        render()
                    }
                }
            }.start()
        }, "search", android.os.SystemClock.uptimeMillis() + 280)
    }

    private fun goBack(): Boolean {
        if (selected.isNotEmpty()) { clearSelection(); return true }
        if (searching) { toggleSearch(); return true }
        val c = current
        when {
            category != null && startCategory != null -> return false
            category != null -> { category = null; current = null }
            c != null && c.absolutePath != root.absolutePath -> current = c.parentFile
            c != null -> current = null
            else -> return false
        }
        refresh()
        return true
    }

    private fun favorites(): List<File> =
        (prefs.getStringSet("fav", emptySet()) ?: emptySet()).map { File(it) }.filter { it.exists() }.sortedBy { it.name.lowercase(Locale.ROOT) }

    private fun toggleFavorite(f: File) {
        val set = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        val added = if (set.contains(f.absolutePath)) { set.remove(f.absolutePath); false } else { set.add(f.absolutePath); true }
        prefs.edit().putStringSet("fav", set).apply()
        notify(if (added) "Added to favorites" else "Removed from favorites")
        if (current == null && category == null) refresh()
    }

    private fun refresh() {
        val token = ++loadToken
        val cat = category
        val dir = current
        searchRows = null
        if (query.isNotEmpty()) onQueryChanged(query)
        backButton.visibility = if (cat == null && dir == null) View.INVISIBLE else View.VISIBLE
        if (cat == null && dir == null) {
            val home = ArrayList<Row>()
            home.add(Row.Storage)
            cats.forEach { home.add(Row.Category(it)) }
            val favs = favorites()
            if (favs.isNotEmpty()) {
                home.add(Row.Header("Favorites"))
                favs.forEach { home.add(Row.Entry(it, home = true)) }
            }
            home.add(Row.Header("Quick access"))
            listOf("Download", "DCIM", "Pictures", "Documents", "Music", "Movies").map { File(root, it) }.filter { it.isDirectory }
                .forEach { home.add(Row.Entry(it, home = true)) }
            home.add(Row.Entry(root, home = true))
            rows = home
            titleView.text = "My Files"
            subtitleView.text = "Internal storage"
            render(); return
        }
        if (dir != null) {
            titleView.text = if (dir.absolutePath == root.absolutePath) "Internal storage" else dir.name
            subtitleView.text = "Loading…"
            Thread {
                val files = (dir.listFiles() ?: emptyArray()).toList()
                val ordered = sorted(files)
                runOnUiThread {
                    if (token == loadToken) {
                        rows = ordered.map { Row.Entry(it) }
                        val folders = ordered.count { it.isDirectory }
                        subtitleView.text = "$folders folders · ${ordered.size - folders} files"
                        render()
                    }
                }
            }.start()
        } else {
            val c = cats.first { it.key == cat }
            titleView.text = c.label
            subtitleView.text = "Searching…"
            rows = emptyList()
            render()
            if (cat == "trash") {
                Thread {
                    FilesTrash.purgeExpired(root)
                    val found = FilesTrash.items(root)
                    runOnUiThread {
                        if (token == loadToken) {
                            trashMap = found.associateBy { it.data.absolutePath }
                            rows = if (found.isEmpty()) emptyList() else listOf<Row>(Row.Header("Deleted items are removed automatically after ${FilesTrash.RETENTION_DAYS} days")) +
                                found.map { Row.Entry(it.data, "${it.daysLeft} days left") }
                            subtitleView.text = "${found.size} items"
                            render()
                        }
                    }
                }.start()
                return
            }
            Thread {
                val found = FilesOps.scanCategory(root, cat!!) { token != loadToken }
                runOnUiThread {
                    if (token == loadToken) {
                        rows = found.map { Row.Entry(it, it.parentFile?.name) }
                        subtitleView.text = "${found.size} items"
                        render()
                    }
                }
            }.start()
        }
    }

    private fun sorted(files: List<File>): List<File> {
        val visible = if (showHidden) files else files.filter { !it.name.startsWith(".") }
        val cmp: Comparator<File> = when (sort) {
            Sort.NAME -> compareBy { it.name.lowercase(Locale.ROOT) }
            Sort.DATE -> compareBy { it.lastModified() }
            Sort.SIZE -> compareBy { if (it.isDirectory) 0L else it.length() }
            Sort.TYPE -> compareBy<File> { it.extension.lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) }
        }
        val ordered = visible.sortedWith(if (ascending) cmp else cmp.reversed())
        return ordered.filter { it.isDirectory } + ordered.filter { !it.isDirectory }
    }

    private fun useGrid(): Boolean = (gridSetting ?: (category == "images" || category == "videos")) && searchRows == null && !(current == null && category == null)

    private fun render() {
        val shown = searchRows ?: rows
        adapter.submit(shown, useGrid())
        val atHome = current == null && category == null
        viewToggle.visibility = if (atHome) View.GONE else View.VISIBLE
        viewToggle.setImageResource(if (useGrid()) R.drawable.ic_f_view_list else R.drawable.ic_f_view_grid)
        emptyBox.visibility = if (shown.isEmpty() && hasAccess() && !atHome && !subtitleView.text.toString().endsWith("…")) View.VISIBLE else View.GONE
        emptyText.text = if (query.isNotEmpty()) "No matches for \"$query\"" else if (category == "trash") "Recycle bin is empty" else "This folder is empty"
        pasteBar.visibility = if (clipboard.isNotEmpty() && current != null) View.VISIBLE else View.GONE
        pasteText.text = (if (clipboardMove) "Move " else "Copy ") + "${clipboard.size} here"
        fab.visibility = if (current != null && selected.isEmpty() && searchRows == null) View.VISIBLE else View.GONE
        val sel = selected.isNotEmpty()
        selectionBar.visibility = if (sel) View.VISIBLE else View.GONE
        normalBar.visibility = if (sel) View.INVISIBLE else View.VISIBLE
        selectionCount.text = "${selected.size} selected"
        val inTrash = category == "trash"
        trashButtons.forEach { it.visibility = if (inTrash) View.VISIBLE else View.GONE }
        normalSelButtons.forEach { it.visibility = if (inTrash) View.GONE else View.VISIBLE }
        updateCrumbs()
    }

    private fun updateCrumbs() {
        val dir = current
        if (dir == null || searchRows != null) { crumbScroll.visibility = View.GONE; return }
        crumbScroll.visibility = View.VISIBLE
        crumbs.removeAllViews()
        val chain = ArrayList<File>()
        var f: File? = dir
        while (f != null && f.absolutePath.startsWith(root.absolutePath)) { chain.add(0, f); if (f.absolutePath == root.absolutePath) break; f = f.parentFile }
        chain.forEachIndexed { i, seg ->
            val last = i == chain.lastIndex
            if (i > 0) crumbs.addView(text("›", 16f, p.text2).apply { setPadding(dp(4), 0, dp(4), 0) })
            crumbs.addView(text(if (seg.absolutePath == root.absolutePath) "Internal storage" else seg.name, 13.5f, if (last) p.onAccent else p.text, last).apply {
                background = dialogs.rounded(if (last) p.accent else p.card, 16)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { if (!last) { current = seg; category = null; refresh() } }
            })
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun clearSelection() { selected.clear(); render() }

    private fun selectAll() {
        val entries = (searchRows ?: rows).filterIsInstance<Row.Entry>()
        if (selected.size == entries.size) selected.clear() else entries.forEach { selected.add(it.file) }
        render()
    }

    private fun toggle(f: File) {
        if (!selected.remove(f)) selected.add(f)
        render()
    }

    private fun setClipboard(files: List<File>, move: Boolean) {
        if (files.isEmpty()) return
        clipboard = files; clipboardMove = move
        selected.clear()
        notify(if (current == null) "Open a folder and tap Paste" else "${files.size} item(s) ready to ${if (move) "move" else "copy"}")
        if (current == null && category == null) current = root
        refresh()
    }

    private fun runTask(title: String, files: List<File>, work: (FilesOps.Progress) -> String?) {
        var handle: ProgressHandle? = null
        handle = dialogs.progress(title) {}
        val h = handle
        Thread {
            val total = FilesOps.countFiles(files)
            val prog = FilesOps.Progress(h, total)
            val message = try { work(prog) } catch (e: Exception) { "Failed: ${e.message ?: "unknown error"}" }
            runOnUiThread {
                h.dismiss()
                selected.clear()
                refresh()
                message?.let { notify(it) }
            }
        }.start()
    }

    private fun paste() {
        val dir = current ?: return
        val items = clipboard
        val move = clipboardMove
        runTask(if (move) "Moving" else "Copying", items) { prog ->
            var failed = 0
            for (src in items) {
                if (prog.cancelled) break
                val target = FilesOps.uniqueTarget(dir, src.name)
                if (src.isDirectory && (target.absolutePath + "/").startsWith(src.absolutePath + "/")) { failed++; continue }
                try {
                    if (move && src.renameTo(target)) { prog.step(src.name); continue }
                    FilesOps.copyTree(src, target, prog)
                    if (move && !prog.cancelled) src.deleteRecursively()
                } catch (e: Exception) { failed++ }
            }
            clipboard = emptyList()
            if (failed > 0) "$failed item(s) failed" else "Done"
        }
    }

    private fun confirmDelete(files: List<File>) {
        if (files.isEmpty()) return
        val msg = if (files.size == 1) "\"${files[0].name}\"" else "${files.size} items"
        dialogs.confirm("Move to recycle bin?", "$msg will be kept for ${FilesTrash.RETENTION_DAYS} days before being deleted for good. You can restore ${if (files.size == 1) "it" else "them"} any time.", "Move to bin", true) {
            runTask("Moving to recycle bin", files) { prog ->
                var failed = 0
                files.forEach { if (prog.cancelled) return@forEach; if (!FilesTrash.moveToTrash(root, it)) failed++; prog.step(it.name) }
                if (failed > 0) "$failed item(s) couldn't be moved" else "Moved ${files.size} item(s) to recycle bin"
            }
        }
    }

    private fun confirmDeleteForever(files: List<File>) {
        if (files.isEmpty()) return
        val msg = if (files.size == 1) "\"${files[0].name}\" will be permanently deleted." else "${files.size} items will be permanently deleted."
        dialogs.confirm("Delete permanently?", "$msg This can't be undone.", "Delete", true) {
            runTask("Deleting", files) { prog ->
                files.forEach { f ->
                    if (category == "trash") trashMap[f.absolutePath]?.folder?.let { folder -> FilesOps.deleteTree(folder, prog) } else FilesOps.deleteTree(f, prog)
                }
                "Deleted ${files.size} item(s)"
            }
        }
    }

    private fun restoreItems(files: List<File>) {
        val items = files.mapNotNull { trashMap[it.absolutePath] }
        if (items.isEmpty()) return
        var ok = 0
        Thread {
            items.forEach { if (FilesTrash.restore(root, it)) ok++ }
            runOnUiThread {
                selected.clear()
                refresh()
                notify(if (ok == items.size) "Restored $ok item(s)" else "Restored $ok of ${items.size}")
            }
        }.start()
    }

    private fun emptyTrash() {
        dialogs.confirm("Empty recycle bin?", "Everything in the recycle bin will be permanently deleted.", "Empty", true) {
            Thread {
                FilesTrash.empty(root)
                runOnUiThread { refresh(); notify("Recycle bin emptied") }
            }.start()
        }
    }

    private fun rename(f: File) {
        dialogs.input("Rename", "Name", f.name, "Rename") { name ->
            if (name.contains('/')) { notify("Name can't contain /"); return@input }
            val target = File(f.parentFile, name)
            when {
                target.exists() && target != f -> notify("\"$name\" already exists")
                !f.renameTo(target) -> notify("Couldn't rename")
                else -> { selected.clear(); refresh(); notify("Renamed") }
            }
        }
    }

    private fun createFolder() {
        val dir = current ?: return
        dialogs.input("New folder", "Folder name", "", "Create") { name ->
            if (name.contains('/')) { notify("Name can't contain /"); return@input }
            val t = File(dir, name)
            if (t.exists()) notify("\"$name\" already exists") else if (t.mkdirs()) { refresh(); notify("Folder created") } else notify("Couldn't create folder")
        }
    }

    private fun createTextFile() {
        val dir = current ?: return
        dialogs.input("New text file", "File name", "note.txt", "Create") { name ->
            if (name.contains('/')) { notify("Name can't contain /"); return@input }
            val t = File(dir, name)
            if (t.exists()) notify("\"$name\" already exists") else if (runCatching { t.createNewFile() }.getOrDefault(false)) { refresh(); notify("File created") } else notify("Couldn't create file")
        }
    }

    private fun compress(files: List<File>) {
        val dir = current ?: files.firstOrNull()?.parentFile ?: return
        val baseName = if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name } else "Archive"
        dialogs.input("Compress", "Archive name", "$baseName.zip", "Compress") { raw ->
            val name = if (raw.endsWith(".zip", true)) raw else "$raw.zip"
            val out = FilesOps.uniqueTarget(dir, name)
            runTask("Compressing", files) { prog ->
                FilesOps.zip(files, out, prog)
                if (prog.cancelled) { out.delete(); "Cancelled" } else "Created ${out.name}"
            }
        }
    }

    private fun extract(zip: File) {
        val dir = zip.parentFile ?: return
        val dest = FilesOps.uniqueTarget(dir, zip.nameWithoutExtension)
        val count = FilesOps.zipEntryCount(zip)
        var handle: ProgressHandle? = null
        handle = dialogs.progress("Extracting") {}
        val h = handle
        Thread {
            val prog = FilesOps.Progress(h, count)
            val msg = try { FilesOps.unzip(zip, dest, prog); if (prog.cancelled) "Cancelled" else "Extracted to ${dest.name}" } catch (e: Exception) { "Couldn't extract this archive" }
            runOnUiThread { h.dismiss(); refresh(); notify(msg) }
        }.start()
    }

    private fun shareFiles(files: List<File>) {
        val regular = files.filter { it.isFile }
        if (regular.isEmpty()) { notify("Folders can't be shared"); return }
        val uris = ArrayList(regular.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]); type = FilesOps.mime(regular[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); type = "*/*" }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Share"))
        selected.clear(); render()
    }

    private fun openWith(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, FilesOps.mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(i, "Open with")) } catch (e: Exception) { notify("No app can open this file") }
    }

    private fun open(f: File) {
        val e = FilesOps.ext(f)
        val i: Intent = when {
            e in FilesOps.imageExt && e != "svg" -> {
                val sib = siblingPaths { FilesOps.ext(it) in FilesOps.imageExt && FilesOps.ext(it) != "svg" }
                Intent(this, PhotoViewerActivity::class.java)
                    .putExtra(PhotoViewerActivity.EXTRA_PATH, f.absolutePath).putExtra(PhotoViewerActivity.EXTRA_NAME, f.name)
                    .putExtra(PhotoViewerActivity.EXTRA_SIBLINGS, sib).putExtra(PhotoViewerActivity.EXTRA_INDEX, sib.indexOf(f.absolutePath).coerceAtLeast(0))
            }
            e in FilesOps.videoExt || e in FilesOps.audioExt -> {
                val sib = siblingPaths { FilesOps.ext(it) in FilesOps.videoExt || FilesOps.ext(it) in FilesOps.audioExt }
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_PATH, f.absolutePath).putExtra(VideoPlayerActivity.EXTRA_NAME, f.name)
                    .putExtra(VideoPlayerActivity.EXTRA_SIBLINGS, sib).putExtra(VideoPlayerActivity.EXTRA_INDEX, sib.indexOf(f.absolutePath).coerceAtLeast(0))
            }
            e in FilesOps.docExt -> Intent(this, DocumentViewerActivity::class.java)
                .putExtra("file_uri", Uri.fromFile(f).toString()).putExtra("file_name", f.name)
            e == "zip" -> { extractPrompt(f); return }
            else -> {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, FilesOps.mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try { startActivity(i) } catch (ex: Exception) { notify("No app can open this file") }
    }

    private fun siblingPaths(match: (File) -> Boolean): Array<String> =
        (searchRows ?: rows).filterIsInstance<Row.Entry>().map { it.file }.filter { it.isFile && match(it) }.map { it.absolutePath }.toTypedArray()

    private fun extractPrompt(zip: File) {
        dialogs.confirm("Extract archive?", "Extract \"${zip.name}\" into a new folder here?", "Extract") { extract(zip) }
    }

    private fun details(files: List<File>) {
        if (files.size == 1) {
            val f = files[0]
            val (icon, color) = iconFor(f)
            val rows = ArrayList<Pair<String, String>>()
            rows.add("Type" to FilesOps.typeName(f))
            rows.add("Location" to (f.parentFile?.absolutePath ?: "/"))
            rows.add("Size" to if (f.isDirectory) "Calculating…" else "${FilesOps.formatSize(f.length())} (${f.length()} bytes)")
            if (f.isDirectory) rows.add("Contains" to "Calculating…")
            rows.add("Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified())))
            rows.add("Permissions" to (if (f.canRead()) "Read" else "") + (if (f.canWrite()) " · Write" else "") + (if (f.isHidden) " · Hidden" else ""))
            if (FilesOps.ext(f) in FilesOps.imageExt && f.isFile) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
                if (o.outWidth > 0) rows.add("Dimensions" to "${o.outWidth} × ${o.outHeight}")
            }
            val values = dialogs.details(f.name, icon, color, rows)
            if (f.isDirectory) {
                Thread {
                    val size = FilesOps.sizeOf(f)
                    var folders = 0; var fileCount = 0
                    f.walkTopDown().drop(1).forEach { if (it.isDirectory) folders++ else fileCount++ }
                    runOnUiThread {
                        values[2].text = FilesOps.formatSize(size)
                        values[3].text = "$fileCount files, $folders folders"
                    }
                }.start()
            }
        } else {
            val values = dialogs.details("${files.size} items", R.drawable.ic_f_file, 0xFF90A4AE.toInt(), listOf(
                "Selected" to "${files.count { it.isDirectory }} folders, ${files.count { it.isFile }} files",
                "Total size" to "Calculating…"
            ))
            Thread {
                val total = files.sumOf { FilesOps.sizeOf(it) }
                runOnUiThread { values[1].text = FilesOps.formatSize(total) }
            }.start()
        }
    }

    private fun showSortSheet() {
        val items = ArrayList<SheetItem>()
        Sort.values().forEach { s ->
            items.add(SheetItem(R.drawable.ic_f_sort, s.label, checked = sort == s) {
                sort = s; prefs.edit().putInt("sort", s.ordinal).apply(); refresh()
            })
        }
        items.add(SheetItem(R.drawable.ic_f_sort, if (ascending) "Order: Ascending" else "Order: Descending") {
            ascending = !ascending; prefs.edit().putBoolean("asc", ascending).apply(); refresh()
        })
        dialogs.sheet("Sort by", items)
    }

    private fun showMoreSheet() {
        val items = ArrayList<SheetItem>()
        if (category == "trash") {
            items.add(SheetItem(R.drawable.ic_f_recent, "Restore all") { restoreItems(trashMap.values.map { it.data }) })
            items.add(SheetItem(R.drawable.ic_f_delete, "Empty recycle bin", danger = true) { emptyTrash() })
            items.add(SheetItem(R.drawable.ic_f_select_all, "Select all") { selectAll() })
            dialogs.sheet(null, items)
            return
        }
        if (current != null) {
            items.add(SheetItem(R.drawable.ic_f_newfolder, "New folder") { createFolder() })
            items.add(SheetItem(R.drawable.ic_f_doc, "New text file") { createTextFile() })
        }
        if (current == null && category == null) items.add(SheetItem(R.drawable.ic_f_info, "Storage analyzer") { startActivity(Intent(this, StorageAnalyzerActivity::class.java)) })
        items.add(SheetItem(R.drawable.ic_f_sort, "Sort by…") { showSortSheet() })
        items.add(SheetItem(R.drawable.ic_f_select_all, "Select all") { selectAll() })
        items.add(SheetItem(R.drawable.ic_f_view_list, if (showHidden) "Hide hidden files" else "Show hidden files", checked = showHidden) {
            showHidden = !showHidden; prefs.edit().putBoolean("hidden", showHidden).apply(); refresh()
        })
        items.add(SheetItem(R.drawable.ic_f_recent, "Refresh") { refresh() })
        dialogs.sheet(null, items)
    }

    private fun showCreateSheet() {
        dialogs.sheet("Create", listOf(
            SheetItem(R.drawable.ic_f_newfolder, "New folder") { createFolder() },
            SheetItem(R.drawable.ic_f_doc, "New text file") { createTextFile() }
        ))
    }

    private fun showSelectionSheet() {
        val files = selected.toList()
        if (files.isEmpty()) return
        val items = ArrayList<SheetItem>()
        if (category == "trash") {
            items.add(SheetItem(R.drawable.ic_f_recent, "Restore") { restoreItems(files) })
            items.add(SheetItem(R.drawable.ic_f_delete, "Delete permanently", danger = true) { confirmDeleteForever(files) })
            dialogs.sheet("${files.size} selected", items)
            return
        }
        if (files.size == 1) items.add(SheetItem(R.drawable.ic_f_rename, "Rename") { rename(files[0]) })
        items.add(SheetItem(R.drawable.ic_f_archive, "Compress to ZIP") { compress(files) })
        items.add(SheetItem(R.drawable.ic_f_info, "Details") { details(files) })
        items.add(SheetItem(R.drawable.ic_f_delete, "Move to recycle bin", danger = true) { confirmDelete(files) })
        dialogs.sheet("${files.size} selected", items)
    }

    private fun showItemSheet(f: File) {
        val items = ArrayList<SheetItem>()
        if (category == "trash") {
            val item = trashMap[f.absolutePath]
            items.add(SheetItem(R.drawable.ic_f_recent, "Restore") { restoreItems(listOf(f)) })
            items.add(SheetItem(R.drawable.ic_f_delete, "Delete permanently", danger = true) { confirmDeleteForever(listOf(f)) })
            items.add(SheetItem(R.drawable.ic_f_info, "Details") { details(listOf(f)) })
            dialogs.sheet(f.name + (item?.let { "  ·  from ${it.original.parentFile?.name ?: ""}" } ?: ""), items)
            return
        }
        if (f.isFile) {
            items.add(SheetItem(R.drawable.ic_f_file, "Open") { open(f) })
            items.add(SheetItem(R.drawable.ic_f_file, "Open with…") { openWith(f) })
            items.add(SheetItem(R.drawable.ic_f_share, "Share") { shareFiles(listOf(f)) })
        } else {
            val fav = favorites().any { it.absolutePath == f.absolutePath }
            items.add(SheetItem(R.drawable.ic_f_folder, if (fav) "Remove from favorites" else "Add to favorites") { toggleFavorite(f) })
        }
        items.add(SheetItem(R.drawable.ic_f_copy, "Copy") { setClipboard(listOf(f), false) })
        items.add(SheetItem(R.drawable.ic_f_move, "Move") { setClipboard(listOf(f), true) })
        items.add(SheetItem(R.drawable.ic_f_rename, "Rename") { rename(f) })
        if (FilesOps.ext(f) == "zip") items.add(SheetItem(R.drawable.ic_f_archive, "Extract here") { extract(f) })
        items.add(SheetItem(R.drawable.ic_f_archive, "Compress to ZIP") { compress(listOf(f)) })
        items.add(SheetItem(R.drawable.ic_f_info, "Details") { details(listOf(f)) })
        items.add(SheetItem(R.drawable.ic_f_delete, "Move to recycle bin", danger = true) { confirmDelete(listOf(f)) })
        dialogs.sheet(f.name, items)
    }

    private fun iconFor(f: File): Pair<Int, Int> {
        if (f.isDirectory) return R.drawable.ic_f_folder to 0xFFFFC107.toInt()
        val e = FilesOps.ext(f)
        return when {
            e in FilesOps.imageExt -> R.drawable.ic_f_image to 0xFF26A69A.toInt()
            e in FilesOps.videoExt -> R.drawable.ic_f_video to 0xFFEF5350.toInt()
            e in FilesOps.audioExt -> R.drawable.ic_f_music to 0xFFAB47BC.toInt()
            e in FilesOps.docExt -> R.drawable.ic_f_doc to 0xFF42A5F5.toInt()
            e == "apk" -> R.drawable.ic_f_apk to 0xFFFFA726.toInt()
            e in FilesOps.archiveExt -> R.drawable.ic_f_archive to 0xFF8D6E63.toInt()
            else -> R.drawable.ic_f_file to 0xFF90A4AE.toInt()
        }
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        private var data: List<Row> = emptyList()
        private var gridMode = false

        fun submit(r: List<Row>, grid: Boolean) { data = r; gridMode = grid; notifyDataSetChanged() }

        fun spanAt(position: Int): Int = when (val r = data.getOrNull(position)) {
            is Row.Category -> 3
            is Row.Entry -> if (gridMode && !r.home) 4 else 12
            else -> 12
        }

        override fun getItemViewType(position: Int): Int = when (val r = data[position]) {
            is Row.Storage -> 0
            is Row.Header -> 1
            is Row.Category -> 2
            is Row.Entry -> if (gridMode && !r.home) 4 else 3
        }

        inner class VH(val box: ViewGroup, val thumb: ImageView?, val name: TextView?, val meta: TextView?, val check: TextView?, val more: ImageView?, val extra: View? = null) : RecyclerView.ViewHolder(box)

        private fun ripple(v: View) {
            val out = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            v.foreground = androidx.core.content.ContextCompat.getDrawable(this@FilesActivity, out.resourceId)
        }

        private fun checkView() = TextView(this@FilesActivity).apply {
            gravity = Gravity.CENTER; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = this@FilesActivity
            when (viewType) {
                0 -> {
                    val box = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        background = dialogs.rounded(p.card, 22, withAlpha(p.text, 28), 1)
                        setPadding(dp(18), dp(16), dp(18), dp(16))
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(10)) }
                    }
                    val titleRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                    titleRow.addView(text("Internal storage", 16f, p.text, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    titleRow.addView(text("Analyze", 13f, p.onAccent, true).apply {
                        background = dialogs.rounded(p.accent, 16)
                        setPadding(dp(14), dp(6), dp(14), dp(6))
                        setOnClickListener { startActivity(Intent(ctx, StorageAnalyzerActivity::class.java)) }
                    })
                    val meta = text("", 13f, p.text2).apply { setPadding(0, dp(2), 0, dp(10)) }
                    val barBg = FrameLayout(ctx).apply { background = dialogs.rounded(withAlpha(p.text, 35), 5) }
                    val barFill = View(ctx).apply { background = dialogs.rounded(p.accent, 5) }
                    barBg.addView(barFill, FrameLayout.LayoutParams(0, MATCH_PARENT))
                    box.addView(titleRow); box.addView(meta); box.addView(barBg, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
                    return VH(box, null, null, meta, null, null, barFill)
                }
                1 -> {
                    val t = text("", 13f, p.text2, true).apply {
                        setPadding(dp(8), dp(14), dp(8), dp(6)); isAllCaps = true; letterSpacing = 0.08f
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    }
                    return VH(FrameLayout(ctx).apply { layoutParams = t.layoutParams; addView(t) }, null, t, null, null, null)
                }
                2 -> {
                    val box = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(2), dp(8), dp(2), dp(8))
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    }
                    val icon = ImageView(ctx).apply { val pd = dp(14); setPadding(pd, pd, pd, pd) }
                    box.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
                    val name = text("", 12.5f, p.text).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(6), 0, 0) }
                    box.addView(name, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                    return VH(box, icon, name, null, null, null)
                }
                3 -> {
                    val box = LinearLayout(ctx).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(8), dp(4), dp(8))
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    }
                    val thumb = ImageView(ctx).apply { clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND }
                    box.addView(thumb, LinearLayout.LayoutParams(dp(48), dp(48)))
                    val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                    val name = text("", 15.5f, p.text).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
                    val meta = text("", 12.5f, p.text2).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
                    texts.addView(name); texts.addView(meta)
                    box.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14); marginEnd = dp(6) })
                    val check = checkView()
                    box.addView(check, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
                    val more = ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_f_more); setColorFilter(p.text2)
                        val pd = dp(10); setPadding(pd, pd, pd, pd)
                    }
                    box.addView(more, LinearLayout.LayoutParams(dp(42), dp(42)))
                    return VH(box, thumb, name, meta, check, more)
                }
                else -> {
                    val box = FrameLayout(ctx).apply {
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    }
                    val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                    val cell = (resources.displayMetrics.widthPixels - dp(20)) / 3 - dp(8)
                    val thumb = ImageView(ctx).apply { clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND }
                    col.addView(thumb, LinearLayout.LayoutParams(cell, cell))
                    val name = text("", 12.5f, p.text).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE; gravity = Gravity.CENTER; setPadding(dp(2), dp(5), dp(2), 0) }
                    col.addView(name, LinearLayout.LayoutParams(cell, WRAP_CONTENT))
                    box.addView(col, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
                    val check = checkView()
                    box.addView(check, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(10), 0) })
                    return VH(box, thumb, name, null, check, null)
                }
            }
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            when (val r = data[position]) {
                is Row.Storage -> {
                    val used = root.totalSpace - root.usableSpace
                    h.meta?.text = "${FilesOps.formatSize(used)} used of ${FilesOps.formatSize(root.totalSpace)} · ${FilesOps.formatSize(root.usableSpace)} free"
                    val frac = if (root.totalSpace > 0) used.toFloat() / root.totalSpace else 0f
                    h.box.post {
                        val w = (h.box.width - dp(36))
                        h.extra?.layoutParams = (h.extra?.layoutParams as FrameLayout.LayoutParams).apply { width = (w * frac).toInt() }
                    }
                    h.box.setOnClickListener { current = root; refresh() }
                }
                is Row.Header -> h.name?.text = r.text
                is Row.Category -> {
                    h.name?.text = r.cat.label
                    h.thumb?.setImageResource(r.cat.icon)
                    h.thumb?.setColorFilter(r.cat.color)
                    h.thumb?.background = dialogs.rounded(withAlpha(r.cat.color, 55), 18)
                    h.box.setOnClickListener { category = r.cat.key; current = null; if (searching) toggleSearch(); refresh() }
                }
                is Row.Entry -> bindEntry(h, r)
            }
        }

        private fun bindEntry(h: VH, r: Row.Entry) {
            val f = r.file
            val (res, color) = iconFor(f)
            val selecting = selected.isNotEmpty()
            val isSel = selected.contains(f)
            val homeRoot = r.home && f.absolutePath == root.absolutePath
            h.name?.text = if (homeRoot) "Internal storage" else f.name
            val grid = gridMode && !r.home
            if (!grid) {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(f.lastModified()))
                h.meta?.text = when {
                    r.home -> f.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" }
                    r.sub != null -> r.sub + " · " + (if (f.isFile) FilesOps.formatSize(f.length()) else "Folder")
                    f.isDirectory -> date + " · " + (f.list()?.size ?: 0) + " items"
                    else -> date + " · " + FilesOps.formatSize(f.length())
                }
            }
            val thumb = h.thumb!!
            thumb.setColorFilter(color)
            thumb.setImageResource(res)
            thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            val pd = if (grid) dp(26) else dp(11)
            thumb.setPadding(pd, pd, pd, pd)
            thumb.background = dialogs.rounded(withAlpha(color, 45), if (grid) 16 else 14)
            thumb.tag = null
            if (thumbs.hasThumb(f)) {
                val size = if (grid) dp(220) else dp(120)
                thumbs.load(f, thumb, size) { ok ->
                    if (ok) {
                        thumb.clearColorFilter(); thumb.setPadding(0, 0, 0, 0); thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            }
            h.check?.let { c ->
                c.visibility = if (selecting) View.VISIBLE else View.GONE
                c.text = if (isSel) "✓" else ""
                c.setTextColor(p.onAccent)
                c.background = if (isSel) dialogs.rounded(p.accent, 12) else dialogs.rounded(withAlpha(p.text, 25), 12, withAlpha(p.text, 140), 2)
            }
            h.more?.visibility = if (selecting || r.home) View.GONE else View.VISIBLE
            h.more?.setOnClickListener { showItemSheet(f) }
            h.box.background = if (isSel) dialogs.rounded(withAlpha(p.accent, 60), 16) else null
            h.box.setOnClickListener {
                when {
                    selecting -> toggle(f)
                    category == "trash" -> showItemSheet(f)
                    f.isDirectory -> { current = f; category = null; if (searching) toggleSearch(); refresh() }
                    else -> open(f)
                }
            }
            h.box.setOnLongClickListener {
                if (r.home) showItemSheet(f) else { if (!selecting) toggle(f) else toggle(f) }
                true
            }
        }
    }
}

class GalleryActivity : FilesActivity() {
    override val startCategory = "images"
}

class PlayerActivity : FilesActivity() {
    override val startCategory = "videos"
}
