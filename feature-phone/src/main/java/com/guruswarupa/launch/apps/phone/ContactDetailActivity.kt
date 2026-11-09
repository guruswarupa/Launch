package com.guruswarupa.launch.apps.phone

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha

/** Launch's own contact page: numbers, emails, address and notes, with call and message buttons. */
class ContactDetailActivity : AppCompatActivity() {

    private class Line(val value: String, val label: String)

    private class Detail(
        var name: String = "", var photo: String? = null, var starred: Boolean = false, var lookup: String? = null,
        var company: String = "", var nickname: String = "", var notes: String = "", var birthday: String = "",
        val phones: MutableList<Line> = ArrayList(), val emails: MutableList<Line> = ArrayList(), val addresses: MutableList<Line> = ArrayList()
    )

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private var contactId = -1L
    private lateinit var frame: FrameLayout
    private var detail: Detail? = null
    private var pending: String? = null

    private var afterWritePermission: (() -> Unit)? = null
    private val writePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterWritePermission
        afterWritePermission = null
        if (granted) action?.invoke() else Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show()
    }

    /** Runs [action] once contacts can be changed, asking for permission first if needed. */
    private fun withWrite(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) action()
        else { afterWritePermission = action; writePermission.launch(Manifest.permission.WRITE_CONTACTS) }
    }

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { place(it, granted) }
        pending = null
    }

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
        contactId = intent.getLongExtra(EXTRA_ID, -1L)
        if (contactId < 0 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            contactId = ContactIntents.contactId(contentResolver, intent.data)
        frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        Thread {
            val d = load()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (d == null) { Toast.makeText(this, R.string.phone_contact_missing, Toast.LENGTH_SHORT).show(); finish() }
                else {
                    detail = d
                    frame.removeAllViews()
                    frame.addView(build(d), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
            }
        }.start()
    }

    private fun load(): Detail? {
        if (contactId < 0 || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val d = Detail()
        try {
            contentResolver.query(
                ContentUriId(), arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI, ContactsContract.Contacts.STARRED, ContactsContract.Contacts.LOOKUP_KEY),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) { d.name = c.getString(0).orEmpty(); d.photo = c.getString(1); d.starred = c.getInt(2) == 1; d.lookup = c.getString(3) } else return null }
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI, null, "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(contactId.toString()), null
            )?.use { c ->
                val mime = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val d1 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                val d2 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
                val d3 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA3)
                while (c.moveToNext()) {
                    val v = c.getString(d1).orEmpty()
                    if (v.isBlank()) continue
                    when (c.getString(mime)) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if (d.phones.none { android.telephony.PhoneNumberUtils.compare(it.value, v) })
                            d.phones.add(Line(v, CommonDataKinds.Phone.getTypeLabel(resources, c.getInt(d2), c.getString(d3)).toString()))
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> d.emails.add(Line(v, CommonDataKinds.Email.getTypeLabel(resources, c.getInt(d2), c.getString(d3)).toString()))
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> d.addresses.add(Line(v, CommonDataKinds.StructuredPostal.getTypeLabel(resources, c.getInt(d2), c.getString(d3)).toString()))
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> d.company = listOf(v, c.getString(c.getColumnIndexOrThrow(ContactsContract.Data.DATA4)).orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> d.nickname = v
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> d.notes = v
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> if (c.getInt(d2) == CommonDataKinds.Event.TYPE_BIRTHDAY) d.birthday = v
                    }
                }
            }
        } catch (_: Exception) { return null }
        return d
    }

    @Suppress("FunctionName")
    private fun ContentUriId(): Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())

    private fun build(d: Detail): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(28)) }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_star_icon); setColorFilter(if (d.starred) 0xFFFFC107.toInt() else p.text2)
            contentDescription = getString(if (d.starred) R.string.contacts_favorite_remove else R.string.contacts_favorite_add)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { toggleFavorite(d) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_rename); setColorFilter(p.text); contentDescription = getString(R.string.contacts_edit)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { startActivity(Intent(this@ContactDetailActivity, ContactEditActivity::class.java).putExtra(ContactEditActivity.EXTRA_ID, contactId)) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_more); setColorFilter(p.text); contentDescription = getString(R.string.files_delete)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { moreMenu(d) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        column.addView(bar)

        // Header
        val color = intArrayOf(0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF3949AB.toInt())[(d.name.hashCode() and 0x7fffffff) % 7]
        val avatar = FrameLayout(this).apply { background = dialogs.rounded(color, 48) }
        avatar.addView(txt(d.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", 38f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        d.photo?.let { u ->
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND; background = dialogs.rounded(color, 48) }
            try { img.setImageURI(Uri.parse(u)); if (img.drawable != null) avatar.addView(img, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) } catch (_: Exception) {}
        }
        column.addView(avatar, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4) })
        column.addView(txt(d.name, 24f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) })
        val sub = listOf(d.nickname.takeIf { it.isNotBlank() }?.let { "\"$it\"" }, d.company.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
        if (sub.isNotBlank()) column.addView(txt(sub, 14f, p.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
        if (d.starred) column.addView(txt("★", 18f, 0xFFFFC107.toInt()).apply { gravity = Gravity.CENTER })

        // Quick actions
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6)) }
        fun action(icon: Int, label: String, tint: Int, enabled: Boolean, onClick: () -> Unit) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.35f; setOnClickListener { if (enabled) onClick() } }
            cell.addView(ImageView(this).apply {
                setImageResource(icon); setColorFilter(tint)
                val pd = dp(15); setPadding(pd, pd, pd, pd); background = dialogs.rounded(withAlpha(tint, 40), 28)
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            cell.addView(txt(label, 12f, p.text2, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            actions.addView(cell, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val first = d.phones.firstOrNull()?.value
        action(R.drawable.ic_phone, getString(R.string.phone_call), 0xFF2E9E4F.toInt(), first != null) { call(first!!) }
        action(R.drawable.ic_message, getString(R.string.phone_message), p.accent, first != null) { sms(first!!) }
        action(R.drawable.ic_f_share, getString(R.string.phone_email), 0xFFEF6C00.toInt(), d.emails.isNotEmpty()) { mail(d.emails.first().value) }
        column.addView(actions)

        // Numbers
        if (d.phones.isNotEmpty()) {
            val card = card(getString(R.string.phone_numbers))
            d.phones.forEachIndexed { i, line ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)); setOnClickListener { call(line.value) }; setOnLongClickListener { copy(line.value); true } }
                val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                t.addView(txt(line.value, 16.5f, bold = true))
                t.addView(txt(line.label, 12.5f, p.text2))
                row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_message); setColorFilter(p.accent); val pd = dp(11); setPadding(pd, pd, pd, pd); setOnClickListener { sms(line.value) } }, LinearLayout.LayoutParams(dp(46), dp(46)))
                row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_phone); setColorFilter(0xFF2E9E4F.toInt()); val pd = dp(11); setPadding(pd, pd, pd, pd); setOnClickListener { call(line.value) } }, LinearLayout.LayoutParams(dp(46), dp(46)))
                card.addView(row)
            }
            column.addView(card, cardLp())
        }
        if (d.emails.isNotEmpty()) {
            val card = card(getString(R.string.phone_email))
            d.emails.forEach { line ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)); setOnClickListener { mail(line.value) }; setOnLongClickListener { copy(line.value); true } }
                row.addView(txt(line.value, 16f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                row.addView(txt(line.label, 12.5f, p.text2))
                card.addView(row)
            }
            column.addView(card, cardLp())
        }
        if (d.addresses.isNotEmpty()) {
            val card = card(getString(R.string.phone_addresses))
            d.addresses.forEach { line ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(line.value)))) } catch (_: Exception) {} } }
                row.addView(txt(line.value, 15f))
                row.addView(txt(line.label, 12.5f, p.text2))
                card.addView(row)
            }
            column.addView(card, cardLp())
        }
        if (d.birthday.isNotBlank()) {
            val card = card(getString(R.string.phone_birthday))
            card.addView(txt(d.birthday, 16f).apply { setPadding(0, dp(8), 0, dp(8)) })
            column.addView(card, cardLp())
        }
        if (d.notes.isNotBlank()) {
            val card = card(getString(R.string.phone_notes))
            card.addView(txt(d.notes, 15f).apply { setPadding(0, dp(8), 0, dp(8)) })
            column.addView(card, cardLp())
        }
        return ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(column) }
    }

    private fun toggleFavorite(d: Detail) = withWrite {
        Thread {
            val ok = ContactStore.setStarred(contentResolver, contactId, !d.starred)
            runOnUiThread {
                if (ok) reload() else Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun moreMenu(d: Detail) {
        dialogs.sheet(d.name, listOf(
            SheetItem(R.drawable.ic_f_delete, getString(R.string.files_delete), danger = true) {
                dialogs.confirm(d.name, getString(R.string.contacts_delete_message), getString(R.string.files_delete), true) { withWrite {
                    Thread {
                        val ok = ContactStore.delete(contentResolver, contactId)
                        runOnUiThread { if (ok) finish() else Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show() }
                    }.start()
                } }
            }
        ))
    }

    private fun cardLp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }

    private fun card(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = dialogs.rounded(p.card, 22); setPadding(dp(18), dp(14), dp(10), dp(8))
        addView(txt(title, 12.5f, p.accent, true))
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("contact", text))
        Toast.makeText(this, R.string.calc_copied, Toast.LENGTH_SHORT).show()
    }

    private fun sms(number: String) { try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))) } catch (_: Exception) {} }
    private fun mail(address: String) { try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))) } catch (_: Exception) {} }

    private fun call(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) place(number, true)
        else { pending = number; callPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    private fun place(number: String, direct: Boolean) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        try { startActivity(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
        catch (_: Exception) { try { startActivity(Intent(Intent.ACTION_DIAL, uri)) } catch (_: Exception) {} }
    }

    companion object { const val EXTRA_ID = "contact_id" }
}
