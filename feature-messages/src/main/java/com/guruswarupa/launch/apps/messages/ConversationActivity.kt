package com.guruswarupa.launch.apps.messages

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.messages.R
import com.guruswarupa.launch.apps.phone.PhoneContacts
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import java.util.Calendar
import java.util.Date

/** One conversation, or a blank new message when no number is given. */
class ConversationActivity : AppCompatActivity() {

    private sealed class Row {
        class Day(val label: String) : Row()
        class Bubble(val m: Message) : Row()
    }

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var title: TextView
    private lateinit var toBox: LinearLayout
    private lateinit var toEdit: EditText
    private lateinit var suggestions: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendButton: ImageView
    private lateinit var banner: LinearLayout
    private var address: String = ""
    private var threadId = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) { override fun onChange(selfChange: Boolean) { reload() } }
    private var picks: List<PhoneContacts.Entry> = emptyList()
    private val pending = ArrayList<PickedMedia>()
    private lateinit var strip: LinearLayout
    private lateinit var stripScroll: HorizontalScrollView
    private lateinit var emojiPanel: android.widget.ScrollView
    private val thumbs = object : android.util.LruCache<Long, android.graphics.Bitmap>(24) {}

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { addMedia(it.orEmpty()) }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> addMedia(listOf(u)) } }
    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> addMedia(listOf(u)) } }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateBanner() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

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
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        threadId = intent.getLongExtra(EXTRA_THREAD, 0L)
        // sms:/smsto: links from other apps
        intent.data?.takeIf { it.scheme in listOf("sms", "smsto", "mms", "mmsto") }?.let { uri ->
            if (address.isEmpty()) address = Uri.decode(uri.schemeSpecificPart.substringBefore('?'))
        }
        val prefill = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        setContentView(buildUi())
        prefill?.let { input.setText(it) }
        applyAddress()
        val need = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) permissions.launch(need.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        try { contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onResume() {
        super.onResume()
        updateBanner()
        reload()
        if (address.isNotEmpty()) Notifier.cancel(this, address)
    }

    private fun granted(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        title = txt(getString(R.string.msg_new), 18f, bold = true).apply { setPadding(dp(6), 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone); setColorFilter(0xFF2E9E4F.toInt()); contentDescription = getString(R.string.phone_call)
            val pad = dp(11); setPadding(pad, pad, pad, pad)
            setOnClickListener { if (address.isNotEmpty()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)))) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        column.addView(bar)

        // Recipient field for a new message
        toBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        toEdit = EditText(this).apply {
            hint = getString(R.string.msg_to_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 16f; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            background = dialogs.rounded(withAlpha(p.text, 24), 18); setPadding(dp(16), dp(11), dp(16), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { suggest(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        toBox.addView(toEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(4)) })
        suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toBox.addView(suggestions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), 0, dp(14), 0) })
        column.addView(toBox)

        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = BubbleAdapter(); setPadding(dp(10), dp(6), dp(10), dp(6)); clipToPadding = false
        }
        column.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        banner = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = dialogs.rounded(withAlpha(p.accent, 36), 16); setPadding(dp(14), dp(10), dp(10), dp(10)); visibility = View.GONE
        }
        banner.addView(txt(getString(R.string.msg_need_default), 12.5f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        banner.addView(txt(getString(R.string.msg_set_default), 13f, p.onAccent, true).apply {
            background = dialogs.rounded(p.accent, 18); setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { try { roleRequest.launch(SmsRepo.defaultRequestIntent(this@ConversationActivity)) } catch (_: Exception) {} }
        })
        column.addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) })

        stripScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        strip = LinearLayout(this).apply { setPadding(dp(12), dp(4), dp(12), dp(4)) }
        stripScroll.addView(strip)
        column.addView(stripScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val composer = LinearLayout(this).apply { gravity = Gravity.BOTTOM; setPadding(dp(6), dp(4), dp(10), dp(10)) }
        composer.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(p.accent); contentDescription = getString(R.string.msg_attach)
            val pd = dp(11); setPadding(pd, pd, pd, pd); setOnClickListener { attachMenu() }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        composer.addView(txt("😊", 22f).apply {
            gravity = Gravity.CENTER; contentDescription = getString(R.string.msg_emoji); setOnClickListener { toggleEmoji() }
        }, LinearLayout.LayoutParams(dp(42), dp(46)))
        input = EditText(this).apply {
            hint = getString(R.string.msg_type_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 5
            background = dialogs.rounded(withAlpha(p.text, 24), 22); setPadding(dp(18), dp(11), dp(18), dp(11))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        input.setOnClickListener { emojiPanel.visibility = View.GONE }
        sendButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_right); setColorFilter(p.onAccent); contentDescription = getString(R.string.msg_send)
            val pd = dp(12); setPadding(pd, pd, pd, pd); background = dialogs.rounded(p.accent, 24)
            setOnClickListener { send() }
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        column.addView(composer)

        emojiPanel = android.widget.ScrollView(this).apply { visibility = View.GONE; setBackgroundColor(withAlpha(p.text, 18)) }
        val emojiGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        EMOJIS.chunked(8).forEach { rowEmojis ->
            val row = LinearLayout(this)
            rowEmojis.forEach { e -> row.addView(txt(e, 26f).apply { gravity = Gravity.CENTER; setOnClickListener { insertEmoji(e) } }, LinearLayout.LayoutParams(0, dp(46), 1f)) }
            repeat(8 - rowEmojis.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(46), 1f)) }
            emojiGrid.addView(row)
        }
        emojiPanel.addView(emojiGrid)
        column.addView(emojiPanel, LinearLayout.LayoutParams(MATCH_PARENT, dp(230)))

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, b.top, 0, maxOf(b.bottom, ime.bottom))
            insets
        }
        return frame
    }

    private fun applyAddress() {
        if (address.isEmpty()) {
            toBox.visibility = View.VISIBLE
            title.text = getString(R.string.msg_new)
        } else {
            toBox.visibility = View.GONE
            title.text = SmsRepo.title(this, address)
            if (threadId == 0L) Thread { val t = SmsRepo.threadFor(this, address); runOnUiThread { threadId = t; reload() } }.start()
        }
    }

    private fun suggest(q: String) {
        suggestions.removeAllViews()
        val query = q.trim()
        if (query.isEmpty() || !granted(Manifest.permission.READ_CONTACTS)) return
        Thread {
            val found = PhoneContacts.search(this, query).take(5)
            runOnUiThread {
                if (toEdit.text.toString().trim() != query) return@runOnUiThread
                picks = found
                suggestions.removeAllViews()
                found.forEach { e ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { chooseRecipient(e.number) } }
                    row.addView(txt(e.name, 15f, bold = true))
                    row.addView(txt(e.number, 12.5f, p.text2))
                    suggestions.addView(row)
                }
                if (found.isEmpty() && query.any { it.isDigit() } && query.count { it.isDigit() } >= 3) {
                    suggestions.addView(txt(getString(R.string.msg_send_to_number, query), 14f, p.accent, true).apply {
                        setPadding(dp(8), dp(10), dp(8), dp(10)); setOnClickListener { chooseRecipient(query) }
                    })
                }
            }
        }.start()
    }

    private fun chooseRecipient(number: String) {
        address = number
        suggestions.removeAllViews()
        applyAddress()
        reload()
        input.requestFocus()
    }

    private fun updateBanner() {
        if (!::banner.isInitialized) return
        banner.visibility = if (SmsRepo.isDefault(this)) View.GONE else View.VISIBLE
    }

    private fun reload() {
        if (!::list.isInitialized) return
        if (threadId == 0L || !granted(Manifest.permission.READ_SMS)) { (list.adapter as BubbleAdapter).submit(emptyList()); return }
        Thread {
            val msgs = SmsRepo.messages(this, threadId)
            SmsRepo.markRead(this, threadId)
            val rows = ArrayList<Row>()
            var lastDay = -1
            val cal = Calendar.getInstance()
            msgs.forEach { m ->
                cal.timeInMillis = m.date
                val day = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
                if (day != lastDay) { rows.add(Row.Day(dayLabel(m.date))); lastDay = day }
                rows.add(Row.Bubble(m))
            }
            runOnUiThread { (list.adapter as BubbleAdapter).submit(rows); if (rows.isNotEmpty()) list.scrollToPosition(rows.size - 1) }
        }.start()
    }

    private fun dayLabel(date: Long): String {
        val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = date }
        val same = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        return when {
            same && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> getString(R.string.msg_today)
            same && now.get(Calendar.DAY_OF_YEAR) - 1 == then.get(Calendar.DAY_OF_YEAR) -> getString(R.string.msg_yesterday)
            else -> DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), if (same) "EEEMMMd" else "yMMMd"), date).toString()
        }
    }

    private fun send() {
        val body = input.text.toString().trim()
        if (body.isEmpty() && pending.isEmpty()) return
        if (address.isEmpty()) { toEdit.error = getString(R.string.msg_to_hint); return }
        if (!SmsRepo.isDefault(this)) { Toast.makeText(this, R.string.msg_need_default, Toast.LENGTH_LONG).show(); return }
        if (!granted(Manifest.permission.SEND_SMS)) { permissions.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)); return }
        if (pending.isNotEmpty()) { sendPicture(body); return }
        input.setText("")
        Thread {
            val ok = SmsSender.send(this, address, body)
            val t = if (threadId == 0L) SmsRepo.threadFor(this, address) else threadId
            runOnUiThread {
                if (!ok) Toast.makeText(this, R.string.msg_send_failed, Toast.LENGTH_SHORT).show()
                threadId = t; reload()
            }
        }.start()
    }

    private fun bubbleOptions(m: Message) {
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_f_copy, getString(R.string.files_copy)) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", m.body))
            Toast.makeText(this, R.string.calc_copied, Toast.LENGTH_SHORT).show()
        })
        if (m.failed && !m.mms) items.add(SheetItem(R.drawable.ic_arrow_right, getString(R.string.msg_retry)) {
            Thread { SmsRepo.deleteMessage(this, m.id); SmsSender.send(this, address, m.body); runOnUiThread { reload() } }.start()
        })
        if (SmsRepo.isDefault(this)) items.add(SheetItem(R.drawable.ic_f_delete, getString(R.string.files_delete), danger = true) {
            Thread { if (m.mms) MmsStore.delete(this, m.id) else SmsRepo.deleteMessage(this, m.id); runOnUiThread { reload() } }.start()
        })
        dialogs.sheet(null, items)
    }

    // ---- attachments and emoji ----

    private fun attachMenu() {
        dialogs.sheet(getString(R.string.msg_attach), listOf(
            SheetItem(R.drawable.ic_image, getString(R.string.msg_photo)) { pickImages.launch("image/*") },
            SheetItem(R.drawable.ic_f_video, getString(R.string.msg_video)) { pickVideo.launch("video/*") },
            SheetItem(R.drawable.ic_f_music, getString(R.string.msg_audio)) { pickAudio.launch("audio/*") }
        ))
    }

    private fun nameOf(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null } ?: (uri.lastPathSegment ?: "file")

    private fun addMedia(uris: List<Uri>) {
        uris.forEach { u ->
            if (pending.size >= 6) return@forEach
            pending.add(PickedMedia(u, contentResolver.getType(u) ?: "application/octet-stream", nameOf(u)))
        }
        renderStrip()
    }

    private fun renderStrip() {
        strip.removeAllViews()
        stripScroll.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        pending.forEach { m ->
            val cell = FrameLayout(this).apply { background = dialogs.rounded(withAlpha(p.text, 28), 14) }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND; background = dialogs.rounded(withAlpha(p.text, 28), 14) }
            when {
                m.mime.startsWith("image/") -> Thread {
                    val bmp = decode(m.uri, 160)
                    runOnUiThread { if (bmp != null) image.setImageBitmap(bmp) }
                }.start()
                m.mime.startsWith("video/") -> { image.setImageResource(R.drawable.ic_f_video); image.setColorFilter(p.accent); image.scaleType = ImageView.ScaleType.CENTER; image.setPadding(dp(18), dp(18), dp(18), dp(18)) }
                else -> { image.setImageResource(R.drawable.ic_f_music); image.setColorFilter(p.accent); image.scaleType = ImageView.ScaleType.CENTER; image.setPadding(dp(18), dp(18), dp(18), dp(18)) }
            }
            cell.addView(image, FrameLayout.LayoutParams(dp(64), dp(64)))
            cell.addView(txt("✕", 12f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = dialogs.rounded(0xAA000000.toInt(), 10)
                setOnClickListener { pending.remove(m); renderStrip() }
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END))
            strip.addView(cell, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(8) })
        }
    }

    private fun decode(uri: Uri, target: Int): android.graphics.Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (_: Exception) { null }

    private fun sendPicture(text: String) {
        if (!granted(Manifest.permission.SEND_SMS)) { permissions.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)); return }
        val media = ArrayList(pending)
        pending.clear(); renderStrip(); input.setText("")
        Toast.makeText(this, R.string.msg_sending, Toast.LENGTH_SHORT).show()
        Thread {
            var message: String? = null
            val ok = try { MmsSender.send(this, address, text, media) } catch (e: MmsTooLarge) { message = getString(R.string.msg_too_large, e.limitKb); false } catch (_: Exception) { false }
            val t = if (threadId == 0L) SmsRepo.threadFor(this, address) else threadId
            runOnUiThread {
                if (!ok) Toast.makeText(this, message ?: getString(R.string.msg_send_failed), Toast.LENGTH_LONG).show()
                threadId = t; reload()
            }
        }.start()
    }

    private fun toggleEmoji() {
        val show = emojiPanel.visibility != View.VISIBLE
        emojiPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (show) (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun insertEmoji(e: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        input.text.insert(start, e)
    }

    private fun mediaView(att: Attachment): View {
        val frame = FrameLayout(this)
        if (att.isAudio) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(6), dp(10), dp(6)) }
            row.addView(txt("▶", 18f, p.accent, true).apply { gravity = Gravity.CENTER; background = dialogs.rounded(withAlpha(Color.WHITE, 60), 18) }, LinearLayout.LayoutParams(dp(36), dp(36)))
            row.addView(txt(att.name ?: getString(R.string.msg_audio), 13.5f).apply { setPadding(dp(10), 0, 0, 0); maxLines = 1 })
            frame.addView(row)
        } else {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = dialogs.rounded(withAlpha(Color.BLACK, 60), 14)
            }
            frame.addView(image, FrameLayout.LayoutParams(dp(220), dp(160)))
            val cached = thumbs.get(att.partId)
            if (cached != null) image.setImageBitmap(cached) else Thread {
                val bmp = if (att.isImage) decode(att.uri, 480) else try {
                    val r = android.media.MediaMetadataRetriever(); r.setDataSource(this, att.uri); val f = r.getFrameAtTime(0); r.release(); f
                } catch (_: Exception) { null }
                if (bmp != null) { thumbs.put(att.partId, bmp); runOnUiThread { image.setImageBitmap(bmp) } }
            }.start()
            if (att.isVideo) frame.addView(txt("▶", 30f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        frame.setOnClickListener { openAttachment(att) }
        return frame
    }

    private fun openAttachment(att: Attachment) {
        Thread {
            val dir = java.io.File(cacheDir, "mms_view").apply { mkdirs() }
            val file = java.io.File(dir, "${att.partId}.${MmsStore.extensionFor(att.type)}")
            try {
                if (!file.exists()) contentResolver.openInputStream(att.uri)?.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
            } catch (_: Exception) { return@Thread }
            runOnUiThread {
                try {
                    // The photo and video viewers live in the main app module, so they are started by name.
                    val target = if (att.isImage) "com.guruswarupa.launch.ui.activities.PhotoViewerActivity" else "com.guruswarupa.launch.ui.activities.VideoPlayerActivity"
                    val pathKey = if (att.isImage) "photo_path" else "video_path"
                    val nameKey = if (att.isImage) "photo_name" else "video_name"
                    startActivity(Intent().setClassName(packageName, target).putExtra(pathKey, file.absolutePath).putExtra(nameKey, if (att.isImage) file.name else (att.name ?: file.name)))
                } catch (_: Exception) {}
            }
        }.start()
    }

    private inner class BubbleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<Row> = emptyList()
        fun submit(l: List<Row>) { rows = l; notifyDataSetChanged() }
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = FrameLayout(parent.context).apply { layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT) }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val host = holder.itemView as FrameLayout
            host.removeAllViews()
            when (val r = rows[position]) {
                is Row.Day -> host.addView(txt(r.label, 12f, p.text2, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6)) }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                is Row.Bubble -> {
                    val m = r.m
                    val col = LinearLayout(this@ConversationActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = dialogs.rounded(if (m.outgoing) p.accent else p.card, 20)
                        setPadding(dp(14), dp(9), dp(14), dp(7))
                        setOnLongClickListener { bubbleOptions(m); true }
                    }
                    val fg = if (m.outgoing) p.onAccent else p.text
                    m.attachments.forEach { att -> col.addView(mediaView(att), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(4) }) }
                    if (m.body.isNotBlank() || m.attachments.isEmpty()) col.addView(txt(m.body, 15.5f, fg).apply { setTextIsSelectable(false); maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt() })
                    val status = when { m.failed -> getString(R.string.msg_not_sent); m.sending -> getString(R.string.msg_sending); else -> "" }
                    col.addView(txt(DateFormat.getTimeFormat(this@ConversationActivity).format(Date(m.date)) + if (status.isNotEmpty()) "  ·  $status" else "", 10.5f, if (m.failed) 0xFFE57373.toInt() else withAlpha(fg, 170)).apply { gravity = Gravity.END; setPadding(0, dp(3), 0, 0) })
                    host.addView(col, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, if (m.outgoing) Gravity.END else Gravity.START).apply { setMargins(0, dp(3), 0, dp(3)) })
                }
            }
        }
    }

    companion object {
        private val EMOJIS = ("😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🥳😏😒😞😔😟😕🙁😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀👽🤖🎃" +
            "👋🤚🖐✋🖖👌🤏✌🤞🤟🤘🤙👈👉👆👇☝👍👎✊👊🤛🤜👏🙌👐🤲🤝🙏💪🧠👀👁👅👄" +
            "❤🧡💛💚💙💜🖤🤍🤎💔❣💕💞💓💗💖💘💝🔥✨⭐🌟💫🎉🎊🎈🎁🏆🥇⚽🏀🏏🎮🎵🎶📷📱💻☕🍕🍔🍟🍿🍰🍫🍺🥂🌹🌸🌞🌙⛅🌈🚗✈🏠").let { s ->
            val out = ArrayList<String>(); var i = 0
            while (i < s.length) { val cp = s.codePointAt(i); var j = i + Character.charCount(cp)
                while (j < s.length && (s[j] == '\uFE0F' || s[j] == '\u200D')) { j++ }
                out.add(s.substring(i, j)); i = j }
            out
        }
        const val EXTRA_THREAD = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }
}
