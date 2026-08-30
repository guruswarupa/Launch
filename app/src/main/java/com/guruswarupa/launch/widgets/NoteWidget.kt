package com.guruswarupa.launch.widgets

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.guruswarupa.launch.ui.theme.ThemeManager

data class NoteItem(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(updatedAt))
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): NoteItem {
            return NoteItem(
                id = json.getString("id"),
                title = json.getString("title"),
                content = json.getString("content"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt")
            )
        }
    }
}

class NoteWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: SharedPreferences
) : InitializableWidget {

    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var addButton: View
    private lateinit var widgetContainer: LinearLayout
    private lateinit var widgetView: View

    private val notes: MutableList<NoteItem> = mutableListOf()
    private lateinit var adapter: NoteAdapter

    private var isInitialized = false

    companion object {
        private const val PREFS_NOTES_KEY = "note_widget_items"
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_note, container, false)
        container.addView(widgetView)

        notesRecyclerView = widgetView.findViewById(R.id.notes_recycler_view)
        emptyState = widgetView.findViewById(R.id.note_empty_state)
        addButton = widgetView.findViewById(R.id.add_note_button)
        widgetContainer = widgetView.findViewById(R.id.note_widget_container)

        adapter = NoteAdapter(notes,
            onNoteClick = { note ->
                showEditNoteDialog(note)
            },
            onNoteLongPress = { note ->
                showNoteOptionsDialog(note)
            }
        )
        notesRecyclerView.layoutManager = LinearLayoutManager(context)
        notesRecyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val note = notes[position]
                    deleteNote(note)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(notesRecyclerView)

        addButton.setOnClickListener {
            showAddNoteDialog()
        }

        loadNotes()
        updateUI()

        isInitialized = true
    }

    private fun loadNotes() {
        try {
            val notesJson = sharedPreferences.getString(PREFS_NOTES_KEY, null)
            if (notesJson != null) {
                val jsonArray = JSONArray(notesJson)
                notes.clear()
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    notes.add(NoteItem.fromJson(json))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveNotes() {
        try {
            val jsonArray = JSONArray()
            notes.forEach { note ->
                jsonArray.put(note.toJson())
            }
            sharedPreferences.edit {
                putString(PREFS_NOTES_KEY, jsonArray.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNote(note: NoteItem) {
        notes.add(note)
        saveNotes()
        updateUI()
    }

    fun updateNote(note: NoteItem) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index != -1) {
            notes[index] = note
            saveNotes()
            updateUI()
        }
    }

    fun deleteNote(note: NoteItem) {
        notes.removeAll { it.id == note.id }
        saveNotes()
        updateUI()
    }

    fun showNoteOptionsDialog(note: NoteItem) {
        val options = mutableListOf("Edit", "Delete")

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(note.title.ifEmpty { context.getString(R.string.untitled_note) })
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit" -> showEditNoteDialog(note)
                    "Delete" -> {
                        AlertDialog.Builder(context, R.style.CustomDialogTheme)
                            .setTitle(context.getString(R.string.dlg_delete_note))
                            .setMessage(context.getString(R.string.dlg_delete_note_confirm, note.title.ifEmpty { context.getString(R.string.untitled_note) }))
                            .setPositiveButton(context.getString(R.string.delete_button)) { _, _ ->
                                deleteNote(note)
                            }
                            .setNegativeButton(context.getString(R.string.cancel_button), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(context.getString(R.string.cancel_button), null)
            .show()

        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ThemeManager.color(context, R.attr.appTextPrimary)
            val listView = dialog.listView
            listView?.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun updateUI() {
        adapter.notifyDataSetChanged()

        if (notes.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            notesRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            notesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddNoteDialog() {
        val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
        builder.setTitle(context.getString(R.string.new_note))

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_note_edit, null)
        val titleEditText = dialogView.findViewById<EditText>(R.id.note_title_edit_text)
        val contentEditText = dialogView.findViewById<EditText>(R.id.note_content_edit_text)

        builder.setView(dialogView)

        builder.setPositiveButton(context.getString(R.string.save_button)) { _, _ ->
            val title = titleEditText.text.toString().trim()
            val content = contentEditText.text.toString().trim()

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.toast_please_enter_a_title_or_content), Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val now = System.currentTimeMillis()
            val note = NoteItem(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                createdAt = now,
                updatedAt = now
            )
            addNote(note)
        }

        builder.setNegativeButton(context.getString(R.string.cancel_button), null)
        builder.show()
    }

    private fun showEditNoteDialog(note: NoteItem) {
        val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
        builder.setTitle(context.getString(R.string.dlg_edit_note))

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_note_edit, null)
        val titleEditText = dialogView.findViewById<EditText>(R.id.note_title_edit_text)
        val contentEditText = dialogView.findViewById<EditText>(R.id.note_content_edit_text)

        titleEditText.setText(note.title)
        contentEditText.setText(note.content)

        builder.setView(dialogView)

        builder.setPositiveButton(context.getString(R.string.save_button)) { _, _ ->
            val title = titleEditText.text.toString().trim()
            val content = contentEditText.text.toString().trim()

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.toast_please_enter_a_title_or_content), Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val updatedNote = note.copy(
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis()
            )
            updateNote(updatedNote)
        }

        builder.setNegativeButton(context.getString(R.string.cancel_button), null)
        builder.show()
    }

    fun onResume() {

    }

    fun onPause() {

    }

    fun cleanup() {

    }

    fun exportNotesToJson(): String {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            jsonArray.put(note.toJson())
        }
        return jsonArray.toString(2)
    }

    fun importNotesFromJson(jsonString: String): Int {
        try {
            val jsonArray = JSONArray(jsonString)
            var importedCount = 0

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val note = NoteItem.fromJson(json)

                val existingIndex = notes.indexOfFirst { it.id == note.id }
                if (existingIndex == -1) {
                    notes.add(note)
                    importedCount++
                } else {
                    notes[existingIndex] = note
                    importedCount++
                }
            }

            saveNotes()
            updateUI()

            return importedCount
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
}

class NoteAdapter(
    private val notes: List<NoteItem>,
    private val onNoteClick: (NoteItem) -> Unit,
    private val onNoteLongPress: (NoteItem) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.note_title)
        val contentTextView: TextView = itemView.findViewById(R.id.note_content)
        val dateTextView: TextView = itemView.findViewById(R.id.note_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.titleTextView.text = note.title.ifEmpty { holder.itemView.context.getString(R.string.untitled_note) }
        holder.contentTextView.text = note.content.take(100) + if (note.content.length > 100) "..." else ""
        holder.dateTextView.text = note.getFormattedDate()

        holder.itemView.setOnClickListener {
            onNoteClick(note)
        }

        holder.itemView.setOnLongClickListener {
            onNoteLongPress(note)
            true
        }
    }

    override fun getItemCount() = notes.size
}
