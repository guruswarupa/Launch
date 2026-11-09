package com.guruswarupa.launch.apps.messages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.messages.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import java.util.Calendar
import java.util.Date

/** Messages app: the list of conversations. */
class MessagesActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var list: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var banner: LinearLayout
    private lateinit var permBox: LinearLayout
    private lateinit var empty: TextView
    private lateinit var fab: ImageView
    private var all: List<Conversation> = emptyList()
    private var asked = false
    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) { override fun onChange(selfChange: Boolean) { load() } }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load() }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateBanner(); load() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun granted(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

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
        SmsRepo.clearNames()
        updateBanner()
        load()
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(12), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(txt(getString(R.string.app_messages), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
        column.addView(bar)

        banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = dialogs.rounded(withAlpha(p.accent, 36), 18); setPadding(dp(16), dp(12), dp(16), dp(12)); visibility = View.GONE
        }
        banner.addView(txt(getString(R.string.msg_default_banner), 13.5f))
        banner.addView(txt(getString(R.string.msg_set_default), 14f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 20); setPadding(dp(18), dp(9), dp(18), dp(9))
            setOnClickListener { try { roleRequest.launch(SmsRepo.defaultRequestIntent(this@MessagesActivity)) } catch (_: Exception) {} }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        column.addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })

        searchBox = EditText(this).apply {
            hint = getString(R.string.msg_search_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 15f; setSingleLine()
            background = dialogs.rounded(withAlpha(p.text, 24), 22); setPadding(dp(18), dp(11), dp(18), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { render() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        column.addView(searchBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })

        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)); visibility = View.GONE }
        permBox.addView(txt(getString(R.string.msg_permission_message), 14.5f, p.text2).apply { gravity = Gravity.CENTER })
        permBox.addView(txt(getString(R.string.files_allow_access), 16f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 26); setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener {
                if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                else { asked = true; permissions.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)) }
            }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(18) })
        column.addView(permBox)

        val content = FrameLayout(this)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = Adapter(); setPadding(dp(10), 0, dp(10), dp(100)); clipToPadding = false }
        content.addView(list, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        empty = txt(getString(R.string.msg_no_conversations), 14.5f, p.text2).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        content.addView(empty, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        fab = ImageView(this).apply {
            setImageResource(R.drawable.ic_message); setColorFilter(p.onAccent)
            val pd = dp(16); setPadding(pd, pd, pd, pd); background = dialogs.rounded(p.accent, 28); elevation = dp(6).toFloat()
            contentDescription = getString(R.string.msg_new)
            setOnClickListener { startActivity(Intent(this@MessagesActivity, ConversationActivity::class.java)) }
        }
        frame.addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, 0)
            (fab.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, dp(20), b.bottom + dp(20))
            list.setPadding(dp(10), 0, dp(10), b.bottom + dp(100))
            insets
        }
        return frame
    }

    private fun updateBanner() {
        if (!::banner.isInitialized) return
        banner.visibility = if (granted(Manifest.permission.READ_SMS) && !SmsRepo.isDefault(this)) View.VISIBLE else View.GONE
    }

    private fun load() {
        if (!::list.isInitialized) return
        val ok = granted(Manifest.permission.READ_SMS)
        permBox.visibility = if (ok) View.GONE else View.VISIBLE
        searchBox.visibility = if (ok) View.VISIBLE else View.GONE
        fab.visibility = if (ok) View.VISIBLE else View.GONE
        if (!ok) { all = emptyList(); render(); return }
        Thread {
            val result = SmsRepo.conversations(this)
            // Look names up off the main thread so scrolling stays smooth.
            result.forEach { SmsRepo.contactName(this, it.address) }
            runOnUiThread { all = result; render() }
        }.start()
    }

    private fun render() {
        val q = searchBox.text.toString().trim().lowercase()
        val shown = if (q.isEmpty()) all else all.filter { SmsRepo.title(this, it.address).lowercase().contains(q) || it.snippet.lowercase().contains(q) || it.address.contains(q) }
        (list.adapter as Adapter).submit(shown)
        empty.visibility = if (granted(Manifest.permission.READ_SMS) && shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun whenText(date: Long): String {
        val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = date }
        return when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> DateFormat.getTimeFormat(this).format(Date(date))
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMd"), date).toString()
            else -> DateFormat.getDateFormat(this).format(Date(date))
        }
    }

    private fun avatar(name: String, size: Int): View {
        val palette = intArrayOf(0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF3949AB.toInt())
        val frame = FrameLayout(this).apply { background = dialogs.rounded(palette[(name.hashCode() and 0x7fffffff) % palette.size], size / 2) }
        frame.addView(txt(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", size * 0.42f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return frame
    }

    private fun open(c: Conversation) {
        startActivity(Intent(this, ConversationActivity::class.java).putExtra(ConversationActivity.EXTRA_THREAD, c.threadId).putExtra(ConversationActivity.EXTRA_ADDRESS, c.address))
    }

    private fun options(c: Conversation) {
        val name = SmsRepo.title(this, c.address)
        dialogs.sheet(name, listOf(
            SheetItem(R.drawable.ic_phone, getString(R.string.phone_call)) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.address)))) },
            SheetItem(R.drawable.ic_f_delete, getString(R.string.msg_delete_conversation), danger = true) {
                dialogs.confirm(name, getString(R.string.msg_delete_message), getString(R.string.files_delete), true) {
                    Thread { SmsRepo.deleteThread(this, c.threadId); runOnUiThread { load() } }.start()
                }
            }
        ))
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<Conversation> = emptyList()
        fun submit(l: List<Conversation>) { items = l; notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(9), dp(8), dp(9))
            }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val c = items[position]
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            val name = SmsRepo.title(this@MessagesActivity, c.address)
            row.addView(avatar(name, 48), LinearLayout.LayoutParams(dp(48), dp(48)))
            val t = LinearLayout(this@MessagesActivity).apply { orientation = LinearLayout.VERTICAL }
            val unread = c.unread > 0
            t.addView(txt(name, 16f, bold = unread || true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            val prefix = if (c.lastType != Telephony.Sms.MESSAGE_TYPE_INBOX) getString(R.string.msg_you) else ""
            t.addView(txt(prefix + c.snippet.replace('\n', ' '), 13.5f, if (unread) p.text else p.text2, unread).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14); marginEnd = dp(8) })
            val right = LinearLayout(this@MessagesActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            right.addView(txt(whenText(c.date), 12f, if (unread) p.accent else p.text2, unread))
            if (unread) right.addView(txt(c.unread.toString(), 11.5f, p.onAccent, true).apply {
                gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 10); minWidth = dp(20); setPadding(dp(6), dp(1), dp(6), dp(1))
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) })
            row.addView(right)
            row.setOnClickListener { open(c) }
            row.setOnLongClickListener { options(c); true }
        }
    }
}
