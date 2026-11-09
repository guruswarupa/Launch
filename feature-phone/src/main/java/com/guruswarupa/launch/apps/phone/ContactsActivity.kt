package com.guruswarupa.launch.apps.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.withAlpha

/** Contacts app: favorites first, then everyone A to Z, with search and a button to add a contact. */
class ContactsActivity : AppCompatActivity() {

    private sealed class Item {
        class Header(val text: String) : Item()
        class Entry(val person: Person, val inFavorites: Boolean) : Item()
    }

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var list: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var permBox: LinearLayout
    private lateinit var fab: ImageView
    private lateinit var empty: TextView
    private var people: List<Person> = emptyList()
    private val pickMode by lazy { intent.action == Intent.ACTION_PICK || intent.action == Intent.ACTION_GET_CONTENT }
    private val pickType by lazy { intent.type.orEmpty() }
    private var asked = false

    private val readPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { load() }
    private var afterWrite: (() -> Unit)? = null
    private val writePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val a = afterWrite; afterWrite = null
        if (granted) a?.invoke() else Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show()
    }

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

    override fun onResume() {
        super.onResume()
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
        bar.addView(txt(getString(if (pickMode) R.string.contacts_choose else R.string.app_contacts), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
        column.addView(bar)

        searchBox = EditText(this).apply {
            hint = getString(R.string.phone_search_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 15f; setSingleLine()
            background = dialogs.rounded(withAlpha(p.text, 24), 22); setPadding(dp(18), dp(11), dp(18), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { render() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        column.addView(searchBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })

        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)); visibility = View.GONE }
        permBox.addView(txt(getString(R.string.phone_permission_message), 14.5f, p.text2).apply { gravity = Gravity.CENTER })
        permBox.addView(txt(getString(R.string.files_allow_access), 16f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 26); setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener {
                if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                else { asked = true; readPermission.launch(Manifest.permission.READ_CONTACTS) }
            }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(18) })
        column.addView(permBox)

        val content = FrameLayout(this)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = Adapter(); setPadding(dp(10), 0, dp(10), dp(100)); clipToPadding = false }
        content.addView(list, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        empty = txt(getString(R.string.contacts_empty), 14.5f, p.text2).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        content.addView(empty, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        fab = ImageView(this).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(p.onAccent)
            val pd = dp(16); setPadding(pd, pd, pd, pd); background = dialogs.rounded(p.accent, 28); elevation = dp(6).toFloat()
            contentDescription = getString(R.string.contacts_new)
            setOnClickListener { startActivity(Intent(this@ContactsActivity, ContactEditActivity::class.java)) }
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

    private fun load() {
        if (!::list.isInitialized) return
        val ok = granted(Manifest.permission.READ_CONTACTS)
        permBox.visibility = if (ok) View.GONE else View.VISIBLE
        searchBox.visibility = if (ok) View.VISIBLE else View.GONE
        fab.visibility = if (ok) View.VISIBLE else View.GONE
        if (!ok) { people = emptyList(); render(); return }
        Thread {
            val result = ContactStore.load(contentResolver)
            runOnUiThread { people = result; render() }
        }.start()
    }

    private fun render() {
        val q = searchBox.text.toString().trim().lowercase()
        val digits = q.filter { it.isDigit() }
        val shown = if (q.isEmpty()) people else people.filter { it.name.lowercase().contains(q) || (digits.isNotEmpty() && it.numbers.any { n -> n.filter { c -> c.isDigit() }.contains(digits) }) }
        val items = ArrayList<Item>()
        if (q.isEmpty()) {
            val favs = shown.filter { it.starred }
            if (favs.isNotEmpty()) { items.add(Item.Header("★  " + getString(R.string.phone_favorites))); favs.forEach { items.add(Item.Entry(it, true)) } }
            var last = ""
            shown.forEach { person ->
                val letter = person.name.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"
                if (letter != last) { items.add(Item.Header(letter)); last = letter }
                items.add(Item.Entry(person, false))
            }
        } else shown.forEach { items.add(Item.Entry(it, false)) }
        (list.adapter as Adapter).submit(items)
        empty.visibility = if (granted(Manifest.permission.READ_CONTACTS) && items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Another app asked for a contact, number or email: send back what was chosen. */
    private fun pick(person: Person) {
        fun finishWith(uri: Uri) {
            setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            finish()
        }
        Thread {
            val rows: List<Pair<Long, String>>
            val base: Uri?
            when {
                pickType.contains("phone_v2") -> { rows = ContactIntents.phoneRows(contentResolver, person.id); base = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI }
                pickType.contains("email_v2") -> { rows = ContactIntents.emailRows(contentResolver, person.id); base = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI }
                pickType.contains("postal-address") -> { rows = ContactIntents.postalRows(contentResolver, person.id); base = android.provider.ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI }
                else -> { rows = emptyList(); base = null }
            }
            runOnUiThread {
                if (base == null) {
                    val lookup = person.lookup
                    finishWith(if (lookup != null) android.provider.ContactsContract.Contacts.getLookupUri(person.id, lookup) else android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, person.id))
                } else if (rows.isEmpty()) Toast.makeText(this, R.string.contacts_nothing_to_pick, Toast.LENGTH_SHORT).show()
                else if (rows.size == 1) finishWith(android.content.ContentUris.withAppendedId(base, rows[0].first))
                else dialogs.sheet(person.name, rows.map { (id, label) -> com.guruswarupa.launch.ui.activities.SheetItem(R.drawable.ic_person, label) { finishWith(android.content.ContentUris.withAppendedId(base, id)) } })
            }
        }.start()
    }

    private fun toggleStar(person: Person) {
        val action = {
            Thread {
                val ok = ContactStore.setStarred(contentResolver, person.id, !person.starred)
                runOnUiThread { if (ok) load() }
            }.start()
        }
        if (granted(Manifest.permission.WRITE_CONTACTS)) action() else { afterWrite = action; writePermission.launch(Manifest.permission.WRITE_CONTACTS) }
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

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<Item> = emptyList()
        fun submit(l: List<Item>) { items = l; notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position] is Item.Header) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), if (viewType == 0) dp(10) else dp(7), dp(4), if (viewType == 0) dp(2) else dp(7))
            }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = holder.itemView as LinearLayout
            row.removeAllViews(); row.setOnClickListener(null)
            when (val item = items[position]) {
                is Item.Header -> row.addView(txt(item.text, 13f, p.accent, true).apply { setPadding(dp(6), 0, 0, 0) })
                is Item.Entry -> {
                    val person = item.person
                    row.addView(avatar(person, 44), LinearLayout.LayoutParams(dp(44), dp(44)))
                    val t = LinearLayout(this@ContactsActivity).apply { orientation = LinearLayout.VERTICAL }
                    t.addView(txt(person.name, 16f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    t.addView(txt(person.numbers.first() + if (person.numbers.size > 1) "  +${person.numbers.size - 1}" else "", 12.5f, p.text2).apply { maxLines = 1 })
                    row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
                    row.addView(ImageView(this@ContactsActivity).apply {
                        setImageResource(R.drawable.ic_star_icon); setColorFilter(if (person.starred) 0xFFFFC107.toInt() else withAlpha(p.text2, 110))
                        contentDescription = getString(if (person.starred) R.string.contacts_favorite_remove else R.string.contacts_favorite_add)
                        val pd = dp(11); setPadding(pd, pd, pd, pd); setOnClickListener { toggleStar(person) }
                    }, LinearLayout.LayoutParams(dp(46), dp(46)))
                    row.setOnClickListener { if (pickMode) pick(person) else startActivity(Intent(this@ContactsActivity, ContactDetailActivity::class.java).putExtra(ContactDetailActivity.EXTRA_ID, person.id)) }
                }
            }
        }
    }
}
