package com.guruswarupa.launch.apps.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.apps.common.ContactNames
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import java.util.Calendar
import java.util.Date

object DialerRole {
    fun isDefault(ctx: Context): Boolean = try {
        ctx.getSystemService(TelecomManager::class.java).defaultDialerPackage == ctx.packageName
    } catch (_: Exception) { false }

    fun requestIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
        else Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
}

internal class CallEntry(val ids: List<Long>, val number: String, val name: String?, val type: Int, val date: Long, val count: Int, val duration: Long)

internal object CallLogRepo {
    fun load(ctx: Context, limit: Int = 400): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val number = c.getString(1).orEmpty(); val type = c.getInt(3)
                    val last = out.lastOrNull()
                    // Fold back-to-back calls with the same person and outcome into one row.
                    if (last != null && last.number == number && last.type == type) {
                        out[out.size - 1] = CallEntry(last.ids + id, number, last.name, type, last.date, last.count + 1, last.duration)
                    } else out.add(CallEntry(listOf(id), number, c.getString(2)?.takeIf { it.isNotBlank() }, type, c.getLong(4), 1, c.getLong(5)))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun delete(ctx: Context, ids: List<Long>) {
        try { ids.chunked(50).forEach { chunk -> ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${chunk.joinToString(",")})", null) } } catch (_: Exception) {}
    }
}

/** The Recents tab: call history with call-back, message, add-to-contacts and delete. */
internal class RecentsView(
    context: Context,
    private val p: FilesPalette,
    private val dialogs: FilesDialogs,
    private val onCall: (String) -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onSetDefault: () -> Unit
) : LinearLayout(context) {

    private val list = RecyclerView(context)
    private val banner = LinearLayout(context)
    private val perm = LinearLayout(context)
    private val empty = TextView(context)
    private var items: List<CallEntry> = emptyList()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(context).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        orientation = VERTICAL
        banner.apply {
            orientation = VERTICAL; background = dialogs.rounded(withAlpha(p.accent, 36), 18); setPadding(dp(16), dp(12), dp(16), dp(12)); visibility = GONE
            addView(txt(context.getString(R.string.phone_default_banner), 13.5f))
            addView(txt(context.getString(R.string.phone_set_default), 14f, p.onAccent, true).apply {
                gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 20); setPadding(dp(18), dp(9), dp(18), dp(9)); setOnClickListener { onSetDefault() }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        addView(banner, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })
        perm.apply {
            orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)); visibility = GONE
            addView(txt(context.getString(R.string.phone_recents_permission), 14.5f, p.text2).apply { gravity = Gravity.CENTER })
            addView(txt(context.getString(R.string.files_allow_access), 16f, p.onAccent, true).apply {
                gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 26); setPadding(dp(30), dp(13), dp(30), dp(13)); setOnClickListener { onRequestPermission() }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(18) })
        }
        addView(perm)
        empty.apply { text = context.getString(R.string.phone_no_recents); textSize = 14.5f; setTextColor(p.text2); gravity = Gravity.CENTER; setPadding(dp(32), dp(40), dp(32), 0); visibility = GONE }
        addView(empty)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = Adapter()
        list.setPadding(dp(10), 0, dp(10), dp(10)); list.clipToPadding = false
        addView(list, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        perm.visibility = if (ok) GONE else VISIBLE
        banner.visibility = if (ok && !DialerRole.isDefault(context)) VISIBLE else GONE
        if (!ok) { items = emptyList(); (list.adapter as Adapter).notifyDataSetChanged(); empty.visibility = GONE; return }
        Thread {
            val result = CallLogRepo.load(context)
            result.forEach { ContactNames.lookup(context, it.number) }
            list.post { items = result; (list.adapter as Adapter).notifyDataSetChanged(); empty.visibility = if (result.isEmpty()) VISIBLE else GONE }
        }.start()
    }

    private fun whenText(date: Long): String {
        val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = date }
        val time = DateFormat.getTimeFormat(context).format(Date(date))
        return when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> time
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - 1 == then.get(Calendar.DAY_OF_YEAR) -> context.getString(R.string.msg_yesterday) + ", " + time
            else -> DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMd"), date).toString() + ", " + time
        }
    }

    private fun typeInfo(type: Int): Triple<String, Int, Int> = when (type) {
        CallLog.Calls.INCOMING_TYPE -> Triple("↙", 0xFF2E9E4F.toInt(), R.string.phone_incoming)
        CallLog.Calls.OUTGOING_TYPE -> Triple("↗", p.accent, R.string.phone_outgoing)
        CallLog.Calls.MISSED_TYPE -> Triple("↙", 0xFFE53935.toInt(), R.string.phone_missed)
        CallLog.Calls.REJECTED_TYPE -> Triple("✕", 0xFFE53935.toInt(), R.string.phone_declined)
        CallLog.Calls.BLOCKED_TYPE -> Triple("⦸", 0xFFE53935.toInt(), R.string.phone_blocked)
        else -> Triple("↙", p.text2, R.string.phone_incoming)
    }

    private fun options(e: CallEntry) {
        val title = e.name ?: ContactNames.lookup(context, e.number) ?: e.number.ifBlank { context.getString(R.string.phone_unknown_caller) }
        val items = ArrayList<SheetItem>()
        if (e.number.isNotBlank()) {
            items.add(SheetItem(R.drawable.ic_phone, context.getString(R.string.phone_call_number, e.number)) { onCall(e.number) })
            items.add(SheetItem(R.drawable.ic_message, context.getString(R.string.phone_message)) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(e.number)))) })
            if (ContactNames.lookup(context, e.number) == null) items.add(SheetItem(R.drawable.ic_person, context.getString(R.string.phone_add_contact)) {
                context.startActivity(Intent(context, ContactEditActivity::class.java).putExtra(ContactEditActivity.EXTRA_NUMBER, e.number))
            })
            else items.add(SheetItem(R.drawable.ic_person, context.getString(R.string.phone_view_contact)) {
                val id = ContactIntents.phoneToContact(context.contentResolver, e.number)
                if (id >= 0) context.startActivity(Intent(context, ContactDetailActivity::class.java).putExtra(ContactDetailActivity.EXTRA_ID, id))
            })
            items.add(SheetItem(R.drawable.ic_f_copy, context.getString(R.string.files_copy)) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("number", e.number))
                Toast.makeText(context, R.string.calc_copied, Toast.LENGTH_SHORT).show()
            })
        }
        items.add(SheetItem(R.drawable.ic_f_delete, context.getString(R.string.files_delete), danger = true) {
            Thread { CallLogRepo.delete(context, e.ids); list.post { refresh() } }.start()
        })
        dialogs.sheet(title, items)
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply { layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(4), dp(8)) }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val e = items[position]
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            val (glyph, color, label) = typeInfo(e.type)
            val name = e.name ?: ContactNames.lookup(context, e.number) ?: e.number.ifBlank { context.getString(R.string.phone_unknown_caller) }
            row.addView(txt(glyph, 20f, color, true).apply { gravity = Gravity.CENTER; background = dialogs.rounded(withAlpha(color, 40), 22) }, LayoutParams(dp(44), dp(44)))
            val t = LinearLayout(context).apply { orientation = VERTICAL }
            t.addView(txt(name + if (e.count > 1) "  (${e.count})" else "", 16f, if (e.type == CallLog.Calls.MISSED_TYPE) 0xFFE53935.toInt() else p.text, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            t.addView(txt(context.getString(label) + " · " + whenText(e.date), 12.5f, p.text2).apply { maxLines = 1 })
            row.addView(t, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
            if (e.number.isNotBlank()) row.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_phone); setColorFilter(0xFF2E9E4F.toInt()); val pd = dp(11); setPadding(pd, pd, pd, pd)
                setOnClickListener { onCall(e.number) }
            }, LayoutParams(dp(46), dp(46)))
            row.setOnClickListener { options(e) }
            row.setOnLongClickListener { options(e); true }
        }
    }
}
