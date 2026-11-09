package com.guruswarupa.launch.apps.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import java.util.Locale

/**
 * Keypad, favorites and contacts. Calls go through the phone's own call screen, so this is a front end
 * rather than a replacement phone app, and it does not read the call history.
 */
class PhoneActivity : AppCompatActivity() {

    private sealed class Item {
        class Header(val letter: String) : Item()
        class Entry(val person: Person) : Item()
    }

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var keypad: LinearLayout
    private lateinit var numberView: TextView
    private lateinit var actionsRow: LinearLayout
    private lateinit var suggestions: LinearLayout
    private lateinit var favoritesBox: FrameLayout
    private lateinit var recents: RecentsView
    private lateinit var contactsBox: LinearLayout
    private lateinit var favList: RecyclerView
    private lateinit var conList: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var favEmpty: TextView
    private lateinit var permBox: LinearLayout

    private val typed = StringBuilder()
    private var people: List<Person> = emptyList()
    private var tab = 0
    private var pendingCall: String? = null
    private val prefs by lazy { getSharedPreferences("phone_app", MODE_PRIVATE) }

    private val callLogPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (::recents.isInitialized) recents.refresh() }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (::recents.isInitialized) recents.refresh() }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { loadContacts() }
    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCall ?: return@registerForActivityResult
        pendingCall = null
        place(number, granted)
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
        tab = intent.getIntExtra("tab", 0)
        savedInstanceState?.let { typed.append(it.getString("typed").orEmpty()); tab = it.getInt("tab", 0) }
        // Dialer links (tel:) land here with the number already filled in.
        intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let { if (typed.isEmpty()) typed.append(Uri.decode(it).filter { c -> c.isDigit() || c in "+*#" }) }
        setContentView(buildUi())
        selectTab(tab)
        loadContacts()
        updateNumber()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("typed", typed.toString()); outState.putInt("tab", tab)
    }

    // ---------------- UI ----------------

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(12), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(txt(getString(R.string.app_phone), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
        column.addView(bar)

        content = FrameLayout(this)
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        buildKeypad()
        buildFavorites()
        buildContacts()
        recents = RecentsView(this, p, dialogs, { call(it) }, { callLogPermission.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)) }, { try { roleRequest.launch(DialerRole.requestIntent(this)) } catch (_: Exception) {} })
        content.addView(keypad, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(recents, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(favoritesBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(contactsBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        nav = LinearLayout(this).apply { setPadding(dp(14), dp(6), dp(14), dp(10)); gravity = Gravity.CENTER }
        listOf(R.string.phone_keypad to R.drawable.ic_phone, R.string.phone_recents to R.drawable.ic_f_recent, R.string.phone_favorites to R.drawable.ic_star_icon, R.string.phone_contacts to R.drawable.ic_person).forEachIndexed { i, (label, icon) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)); tag = i
                setOnClickListener { selectTab(i) }
            }
            cell.addView(ImageView(this).apply { setImageResource(icon) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            cell.addView(txt(getString(label), 11.5f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            nav.addView(cell, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        column.addView(nav)

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        return frame
    }

    private fun selectTab(i: Int) {
        tab = i
        keypad.visibility = if (i == 0) View.VISIBLE else View.GONE
        recents.visibility = if (i == 1) View.VISIBLE else View.GONE
        favoritesBox.visibility = if (i == 2) View.VISIBLE else View.GONE
        contactsBox.visibility = if (i == 3) View.VISIBLE else View.GONE
        if (i == 1) recents.refresh()
        for (k in 0 until nav.childCount) {
            val cell = nav.getChildAt(k) as LinearLayout
            val on = k == i
            cell.background = if (on) dialogs.rounded(withAlpha(p.accent, 45), 18) else null
            val color = if (on) p.accent else p.text2
            (cell.getChildAt(0) as ImageView).setColorFilter(color)
            (cell.getChildAt(1) as TextView).setTextColor(color)
        }
        refreshLists()
    }

    // ---------------- keypad ----------------

    private val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")

    private fun buildKeypad() {
        keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), dp(6)) }
        suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keypad.addView(suggestions, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        numberView = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(p.text); maxLines = 1
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 22, 40, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnLongClickListener { paste(); true }
        }
        keypad.addView(numberView, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))

        actionsRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun chip(label: Int, onClick: () -> Unit) = txt(getString(label), 13.5f, p.accent, true).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7)); background = dialogs.rounded(withAlpha(p.accent, 40), 16)
            setOnClickListener { onClick() }
        }
        actionsRow.addView(chip(R.string.phone_add_contact) {
            startActivity(Intent(this, ContactEditActivity::class.java).putExtra(ContactEditActivity.EXTRA_NUMBER, typed.toString()))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        actionsRow.addView(chip(R.string.phone_message) {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(typed.toString()))))
        })
        keypad.addView(actionsRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
        rows.forEach { r ->
            val row = LinearLayout(this)
            r.forEach { k -> row.addView(keyCell(k), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)) }
            grid.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        keypad.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 3.4f))

        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4)) }
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone); setColorFilter(Color.WHITE)
            val pd = dp(20); setPadding(pd, pd, pd, pd)
            background = dialogs.rounded(0xFF2E9E4F.toInt(), 40)
            contentDescription = getString(R.string.phone_call)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CONFIRM); onCallPressed() }
        }, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottom.addView(FrameLayout(this).apply {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_f_close); setColorFilter(p.text2)
                val pd = dp(14); setPadding(pd, pd, pd, pd)
                contentDescription = getString(R.string.phone_delete)
                setOnClickListener { if (typed.isNotEmpty()) { typed.setLength(typed.length - 1); updateNumber() } }
                setOnLongClickListener { typed.clear(); updateNumber(); true }
            }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        keypad.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, dp(88)))
    }

    private fun keyCell(k: String): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isClickable = true
            val ripple = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); type(k) }
        }
        cell.addView(txt(k, 30f, p.text).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER })
        letters[k]?.let { cell.addView(txt(if (k == "0") "+" else it, 10.5f, p.text2, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f }) }
        if (k == "1") cell.setOnLongClickListener { cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); voicemail(); true }
        if (k == "0") cell.setOnLongClickListener { cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); type("+"); true }
        return cell
    }

    private fun voicemail() {
        val uri = Uri.fromParts("voicemail", "", null)
        try { startActivity(Intent(if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) } catch (_: Exception) {}
    }

    private fun type(k: String) {
        if (typed.length >= 30) return
        typed.append(k)
        updateNumber()
    }

    private fun paste() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().filter { it.isDigit() || it in "+*#" }
        if (t.isNotEmpty()) { typed.clear(); typed.append(t.take(30)); updateNumber() }
    }

    private fun updateNumber() {
        val raw = typed.toString()
        val shown = if (raw.isEmpty()) "" else try {
            PhoneNumberUtils.formatNumber(raw, Locale.getDefault().country) ?: raw
        } catch (_: Exception) { raw }
        numberView.text = shown
        actionsRow.visibility = if (raw.isEmpty()) View.INVISIBLE else View.VISIBLE
        renderSuggestions()
    }

    private fun renderSuggestions() {
        suggestions.removeAllViews()
        suggestions.gravity = Gravity.BOTTOM
        val q = typed.toString()
        if (q.isEmpty() || people.isEmpty()) return
        val digits = q.filter { it.isDigit() }
        val matches = people.filter { person ->
            digits.isNotEmpty() && (person.t9.startsWith(digits) || person.words.any { it.startsWith(digits) } ||
                person.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) })
        }.take(4)
        matches.reversed().forEach { person ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener { typed.clear(); typed.append(person.numbers.first().filter { c -> c.isDigit() || c == '+' }); updateNumber() }
            }
            row.addView(avatar(person, 38), LinearLayout.LayoutParams(dp(38), dp(38)))
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(txt(person.name, 15f, bold = true).apply { maxLines = 1 })
            t.addView(txt(person.numbers.first(), 12.5f, p.text2).apply { maxLines = 1 })
            row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            suggestions.addView(row)
        }
    }

    // ---------------- calling ----------------

    private fun onCallPressed() {
        var number = typed.toString()
        if (number.isEmpty()) {
            val last = prefs.getString("last", null)
            if (last != null) { typed.append(last); updateNumber() }
            return
        }
        call(number)
    }

    private fun call(number: String) {
        prefs.edit().putString("last", number).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) place(number, true)
        else { pendingCall = number; callPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    /** Calls directly when allowed, otherwise hands the number to the phone's dialer. */
    private fun place(number: String, direct: Boolean) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        try {
            startActivity(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_DIAL, uri)) } catch (_: Exception) {}
        }
    }

    // ---------------- contacts ----------------

    private fun hasContacts() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun loadContacts() {
        if (!::favList.isInitialized) return
        if (!hasContacts()) { people = emptyList(); refreshLists(); return }
        Thread {
            val result = ContactStore.load(contentResolver)
            runOnUiThread { people = result; refreshLists(); renderSuggestions() }
        }.start()
    }

    private fun buildItems(source: List<Person>): List<Item> {
        val out = ArrayList<Item>()
        var last = ""
        source.forEach { person ->
            val letter = person.name.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"
            if (letter != last) { out.add(Item.Header(letter)); last = letter }
            out.add(Item.Entry(person))
        }
        return out
    }

    private fun refreshLists() {
        if (!::favList.isInitialized) return
        val granted = hasContacts()
        permBox.visibility = if (granted) View.GONE else View.VISIBLE
        val favs = people.filter { it.starred }
        (favList.adapter as ContactAdapter).submit(favs.map { Item.Entry(it) })
        favEmpty.visibility = if (granted && favs.isEmpty() && tab == 2) View.VISIBLE else View.GONE
        favEmpty.text = getString(R.string.phone_no_favorites)
        filterContacts(searchBox.text.toString())
    }

    private fun filterContacts(q: String) {
        val query = q.trim().lowercase()
        val list = if (query.isEmpty()) people else people.filter { it.name.lowercase().contains(query) || it.numbers.any { n -> n.filter { c -> c.isDigit() }.contains(query.filter { c -> c.isDigit() }.ifEmpty { "\u0000" }) } }
        (conList.adapter as ContactAdapter).submit(if (query.isEmpty()) buildItems(list) else list.map { Item.Entry(it) })
    }

    private fun buildFavorites() {
        favoritesBox = FrameLayout(this)
        favList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = ContactAdapter(); setPadding(dp(10), dp(4), dp(10), dp(10)); clipToPadding = false }
        favoritesBox.addView(favList, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        favEmpty = txt("", 14f, p.text2).apply { gravity = Gravity.CENTER; setPadding(dp(32), 0, dp(32), 0); visibility = View.GONE }
        favoritesBox.addView(favEmpty, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun buildContacts() {
        contactsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchBox = EditText(this).apply {
            hint = getString(R.string.phone_search_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 15f; setSingleLine()
            background = dialogs.rounded(withAlpha(p.text, 24), 22); setPadding(dp(18), dp(11), dp(18), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { filterContacts(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        contactsBox.addView(searchBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })
        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)); visibility = View.GONE }
        permBox.addView(txt(getString(R.string.phone_permission_message), 14.5f, p.text2).apply { gravity = Gravity.CENTER })
        permBox.addView(txt(getString(R.string.files_allow_access), 16f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 26); setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) || !prefs.getBoolean("asked_contacts", false)) {
                    prefs.edit().putBoolean("asked_contacts", true).apply(); contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                } else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(18) })
        contactsBox.addView(permBox)
        conList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = ContactAdapter(); setPadding(dp(10), 0, dp(10), dp(10)); clipToPadding = false }
        contactsBox.addView(conList, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun avatar(person: Person, size: Int): View {
        val palette = intArrayOf(0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF3949AB.toInt())
        val color = palette[(person.name.hashCode() and 0x7fffffff) % palette.size]
        val frame = FrameLayout(this).apply { background = dialogs.rounded(color, size / 2) }
        frame.addView(txt(person.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", size * 0.42f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        person.photo?.let { uri ->
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND; background = dialogs.rounded(color, size / 2) }
            try { img.setImageURI(Uri.parse(uri)); if (img.drawable != null) frame.addView(img, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) } catch (_: Exception) {}
        }
        return frame
    }

    private fun showPerson(person: Person) {
        startActivity(Intent(this, ContactDetailActivity::class.java).putExtra(ContactDetailActivity.EXTRA_ID, person.id))
    }

    private inner class ContactAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<Item> = emptyList()

        fun submit(list: List<Item>) { items = list; notifyDataSetChanged() }

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position] is Item.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), if (viewType == 0) dp(10) else dp(8), dp(8), if (viewType == 0) dp(2) else dp(8))
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            when (val item = items[position]) {
                is Item.Header -> {
                    row.setOnClickListener(null)
                    row.addView(txt(item.letter, 13f, p.accent, true).apply { setPadding(dp(6), 0, 0, 0) })
                }
                is Item.Entry -> {
                    val person = item.person
                    row.addView(avatar(person, 44), LinearLayout.LayoutParams(dp(44), dp(44)))
                    val t = LinearLayout(this@PhoneActivity).apply { orientation = LinearLayout.VERTICAL }
                    t.addView(txt(person.name, 16f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    t.addView(txt(person.numbers.first() + if (person.numbers.size > 1) "  +${person.numbers.size - 1}" else "", 12.5f, p.text2).apply { maxLines = 1 })
                    row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
                    row.addView(ImageView(this@PhoneActivity).apply {
                        setImageResource(R.drawable.ic_phone); setColorFilter(0xFF2E9E4F.toInt())
                        val pd = dp(10); setPadding(pd, pd, pd, pd)
                        setOnClickListener { call(person.numbers.first()) }
                    }, LinearLayout.LayoutParams(dp(44), dp(44)))
                    row.setOnClickListener { showPerson(person) }
                }
            }
        }
    }
}
