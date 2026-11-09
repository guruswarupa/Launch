package com.guruswarupa.launch.apps.phone

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

/** Creates a new contact or edits an existing one. Saves straight to the phone's contacts. */
class ContactEditActivity : AppCompatActivity() {

    private class Row(var type: Int, val field: EditText, val typeView: TextView)

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var root: FrameLayout
    private lateinit var firstEdit: EditText
    private lateinit var lastEdit: EditText
    private lateinit var companyEdit: EditText
    private lateinit var notesEdit: EditText
    private lateinit var phoneBox: LinearLayout
    private lateinit var emailBox: LinearLayout
    private lateinit var accountBox: LinearLayout
    private val phones = ArrayList<Row>()
    private val emails = ArrayList<Row>()
    private var rawId: Long? = null
    private var accounts: List<Pair<String?, String?>> = emptyList()
    private var account: Pair<String?, String?>? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) start() else { Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_LONG).show(); finish() }
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
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, b.top, 0, maxOf(b.bottom, ime.bottom))
            insets
        }
        val need = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) start() else permissions.launch(need.toTypedArray())
    }

    private fun start() {
        buildUi()
        var id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0 && intent.action == Intent.ACTION_EDIT) id = ContactIntents.contactId(contentResolver, intent.data)
        val prefill = ContactIntents.prefill(intent)
        Thread {
            accounts = ContactStore.accounts(contentResolver)
            val raw = if (id >= 0) ContactStore.firstRawId(contentResolver, id) else null
            val draft = raw?.let { ContactStore.load(contentResolver, it) }
            runOnUiThread {
                account = accounts.firstOrNull { it.second == "com.google" } ?: accounts.firstOrNull { it.first != null || it.second != null }
                if (draft != null) fill(draft) else {
                    addRow(phones, phoneBox, CommonDataKinds.Phone.TYPE_MOBILE, prefill.second.orEmpty(), true)
                    prefill.third?.let { addRow(emails, emailBox, CommonDataKinds.Email.TYPE_HOME, it, false) }
                    prefill.first?.let { firstEdit.setText(it) }
                    intent.getStringExtra(ContactsContract.Intents.Insert.COMPANY)?.let { companyEdit.setText(it) }
                    intent.getStringExtra(ContactsContract.Intents.Insert.NOTES)?.let { notesEdit.setText(it) }
                }
                renderAccounts(draft == null)
            }
        }.start()
    }

    private fun field(hint: Int, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) = EditText(this).apply {
        this.hint = getString(hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 16f; inputType = type; setText(value)
        background = dialogs.rounded(withAlpha(p.text, 24), 14); setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(12), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_close); setColorFilter(p.text); contentDescription = getString(R.string.files_cancel)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val editing = intent.getLongExtra(EXTRA_ID, -1L) >= 0 || intent.action == Intent.ACTION_EDIT
        bar.addView(txt(getString(if (editing) R.string.contacts_edit else R.string.contacts_new), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(txt(getString(R.string.clock_save), 15f, p.onAccent, true).apply {
            background = dialogs.rounded(p.accent, 22); setPadding(dp(20), dp(10), dp(20), dp(10)); setOnClickListener { save() }
        })
        column.addView(bar)

        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(28)) }
        firstEdit = field(R.string.contacts_first_name)
        lastEdit = field(R.string.contacts_last_name)
        companyEdit = field(R.string.contacts_company)
        form.addView(firstEdit)
        form.addView(lastEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        form.addView(companyEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        form.addView(txt(getString(R.string.phone_numbers), 12.5f, p.accent, true).apply { setPadding(dp(4), dp(18), 0, dp(6)) })
        phoneBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(phoneBox)
        form.addView(addButton(R.string.contacts_add_phone) { addRow(phones, phoneBox, CommonDataKinds.Phone.TYPE_MOBILE, "", true) })

        form.addView(txt(getString(R.string.phone_email), 12.5f, p.accent, true).apply { setPadding(dp(4), dp(18), 0, dp(6)) })
        emailBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(emailBox)
        form.addView(addButton(R.string.contacts_add_email) { addRow(emails, emailBox, CommonDataKinds.Email.TYPE_HOME, "", false) })

        notesEdit = field(R.string.contacts_notes_hint, type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply { minLines = 3; gravity = Gravity.TOP }
        form.addView(notesEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(18) })

        accountBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(accountBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        column.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; addView(form) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun addButton(label: Int, onClick: () -> Unit) = txt(getString(label), 14f, p.accent, true).apply {
        setPadding(dp(6), dp(10), dp(6), dp(6)); setOnClickListener { onClick() }
    }

    private fun typeLabel(isPhone: Boolean, type: Int): String =
        (if (isPhone) CommonDataKinds.Phone.getTypeLabel(resources, type, null) else CommonDataKinds.Email.getTypeLabel(resources, type, null)).toString()

    private fun addRow(list: MutableList<Row>, box: LinearLayout, type: Int, value: String, isPhone: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val typeView = txt(typeLabel(isPhone, type), 13f, p.accent, true).apply {
            gravity = Gravity.CENTER; minWidth = dp(74); setPadding(dp(10), dp(10), dp(10), dp(10)); background = dialogs.rounded(withAlpha(p.accent, 36), 14)
        }
        val edit = field(if (isPhone) R.string.phone_numbers else R.string.phone_email, value,
            if (isPhone) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val entry = Row(type, edit, typeView)
        typeView.setOnClickListener {
            val options = if (isPhone) listOf(2, 1, 3, 7) else listOf(1, 2, 3)
            dialogs.sheet(null, options.map { t -> SheetItem(R.drawable.ic_phone, typeLabel(isPhone, t), checked = t == entry.type) { entry.type = t; typeView.text = typeLabel(isPhone, t) } })
        }
        row.addView(typeView)
        row.addView(edit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_close); setColorFilter(p.text2); val pd = dp(11); setPadding(pd, pd, pd, pd)
            setOnClickListener { list.remove(entry); box.removeView(row) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        list.add(entry)
        box.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun fill(d: ContactDraft) {
        rawId = d.rawId
        firstEdit.setText(d.first); lastEdit.setText(d.last); companyEdit.setText(d.company); notesEdit.setText(d.notes)
        d.phones.forEach { addRow(phones, phoneBox, it.first, it.second, true) }
        if (d.phones.isEmpty()) addRow(phones, phoneBox, CommonDataKinds.Phone.TYPE_MOBILE, "", true)
        d.emails.forEach { addRow(emails, emailBox, it.first, it.second, false) }
    }

    private fun accountName(a: Pair<String?, String?>?): String {
        val name = a?.first?.takeIf { it.isNotBlank() }
        val type = a?.second.orEmpty()
        // Accounts created by the phone itself have technical names; show those as "Phone".
        return if (name == null || type.startsWith("vnd.") || name == type) getString(R.string.contacts_account_phone) else name
    }

    private fun renderAccounts(isNew: Boolean) {
        accountBox.removeAllViews()
        if (!isNew || accounts.size < 2) return
        accountBox.addView(txt(getString(R.string.contacts_save_to), 12.5f, p.accent, true).apply { setPadding(dp(4), 0, 0, dp(6)) })
        val chips = LinearLayout(this)
        accounts.forEach { a ->
            chips.addView(txt(accountName(a).take(28), 13f, if (a == account) p.onAccent else p.text, true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = dialogs.rounded(if (a == account) p.accent else withAlpha(p.text, 24), 16)
                setOnClickListener { account = a; renderAccounts(true) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        accountBox.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
    }

    private fun save() {
        val first = firstEdit.text.toString(); val last = lastEdit.text.toString()
        val ph = phones.map { it.type to it.field.text.toString() }.filter { it.second.isNotBlank() }
        val em = emails.map { it.type to it.field.text.toString() }.filter { it.second.isNotBlank() }
        if (first.isBlank() && last.isBlank() && ph.isEmpty() && em.isEmpty()) { Toast.makeText(this, R.string.contacts_need_info, Toast.LENGTH_SHORT).show(); return }
        val draft = ContactDraft(rawId, first, last, companyEdit.text.toString(), notesEdit.text.toString(), ph, em, if (rawId == null) account else null)
        Thread {
            val ok = ContactStore.save(contentResolver, draft)
            runOnUiThread {
                if (ok) { setResult(RESULT_OK); finish() } else Toast.makeText(this, R.string.contacts_save_failed, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    companion object {
        const val EXTRA_ID = "contact_id"
        const val EXTRA_NUMBER = "contact_number"
        const val EXTRA_NAME = "contact_name"
    }
}
