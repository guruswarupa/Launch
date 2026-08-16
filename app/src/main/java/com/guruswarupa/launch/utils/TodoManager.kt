package com.guruswarupa.launch.utils

import android.content.SharedPreferences
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.TodoItem
import com.guruswarupa.launch.ui.adapters.TodoAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Calendar
import java.util.Locale




class TodoManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val todoRecyclerView: RecyclerView,
    private val addTodoButton: ImageButton,
    private val todoAlarmManager: TodoAlarmManager
) {
    private val todoItems = mutableListOf<TodoItem>()
    private lateinit var todoAdapter: TodoAdapter

    fun initialize() {
        todoRecyclerView.layoutManager = LinearLayoutManager(activity)

        todoRecyclerView.itemAnimator = null

        todoAdapter = TodoAdapter(todoItems, { todoItem ->
            removeTodoItem(todoItem)
        }, {

            saveTodoItems()
            rescheduleTodoAlarms()
        })
        todoRecyclerView.adapter = todoAdapter

        addTodoButton.setOnClickListener {
            showAddTodoDialog()
        }

        loadTodoItems()
        rescheduleTodoAlarms()
    }




    fun onThemeChanged() {
        if (::todoAdapter.isInitialized) {
            todoAdapter.notifyItemRangeChanged(0, todoItems.size)
        }
    }

    fun showAddTodoDialog() {
        val dialogBuilder = android.app.AlertDialog.Builder(activity, R.style.CustomDialogTheme)
        dialogBuilder.setTitle(activity.getString(R.string.dlg_add_todo_item))


        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_todo, null)
        val taskInput = dialogView.findViewById<EditText>(R.id.task_input)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.category_spinner)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.priority_spinner)
        val enableTimeCheckbox = dialogView.findViewById<CheckBox>(R.id.enable_time_checkbox)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker)
        val recurringCheckbox = dialogView.findViewById<CheckBox>(R.id.recurring_checkbox)
        val recurrenceTypeGroup = dialogView.findViewById<RadioGroup>(R.id.recurrence_type_group)
        val recurrenceDays = dialogView.findViewById<RadioButton>(R.id.recurrence_days)
        val recurrenceIntervalRadio = dialogView.findViewById<RadioButton>(R.id.recurrence_interval)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.days_selection_container)
        val intervalContainer = dialogView.findViewById<LinearLayout>(R.id.interval_selection_container)
        val intervalSpinner = dialogView.findViewById<Spinner>(R.id.interval_spinner)
        val intervalStartTimePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.interval_start_time_picker)


        val dayCheckboxes = listOf<CheckBox>(
            dialogView.findViewById(R.id.checkbox_sunday),
            dialogView.findViewById(R.id.checkbox_monday),
            dialogView.findViewById(R.id.checkbox_tuesday),
            dialogView.findViewById(R.id.checkbox_wednesday),
            dialogView.findViewById(R.id.checkbox_thursday),
            dialogView.findViewById(R.id.checkbox_friday),
            dialogView.findViewById(R.id.checkbox_saturday)
        )


        val categories = arrayOf("General", "Work", "Personal", "Health", "Shopping", "Study")
        categorySpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, categories)


        val priorities = TodoItem.Priority.entries.map { it.displayName }.toTypedArray()
        prioritySpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, priorities)
        prioritySpinner.setSelection(1)


        val intervals = arrayOf(
            "30 minutes",
            "1 hour",
            "2 hours",
            "3 hours",
            "4 hours",
            "6 hours",
            "8 hours",
            "12 hours"
        )
        val intervalValues = arrayOf(30, 60, 120, 180, 240, 360, 480, 720)
        intervalSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, intervals)


        enableTimeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            timePicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }


        recurringCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                recurrenceTypeGroup.visibility = View.VISIBLE
                if (recurrenceDays.isChecked) {
                    daysContainer.visibility = View.VISIBLE
                    intervalContainer.visibility = View.GONE
                } else {
                    daysContainer.visibility = View.GONE
                    intervalContainer.visibility = View.VISIBLE
                }
            } else {
                recurrenceTypeGroup.visibility = View.GONE
                daysContainer.visibility = View.GONE
                intervalContainer.visibility = View.GONE
            }
        }


        recurrenceTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.recurrence_days) {
                daysContainer.visibility = View.VISIBLE
                intervalContainer.visibility = View.GONE
            } else {
                daysContainer.visibility = View.GONE
                intervalContainer.visibility = View.VISIBLE
            }
        }

        dialogBuilder.setView(dialogView)

        dialogBuilder.setPositiveButton(activity.getString(R.string.dlg_add_task)) { _, _ ->
            val todoText = taskInput.text.toString().trim()
            if (todoText.isNotEmpty()) {
                val isRecurring = recurringCheckbox.isChecked
                val selectedDays = if (isRecurring && recurrenceDays.isChecked) {
                    dayCheckboxes.mapIndexedNotNull { index, checkbox ->
                        if (checkbox.isChecked) index + 1 else null
                    }.toSet()
                } else {
                    emptySet()
                }

                val recurrenceInterval = if (isRecurring && recurrenceIntervalRadio.isChecked) {
                    intervalValues[intervalSpinner.selectedItemPosition]
                } else {
                    null
                }

                val intervalStartTime = if (isRecurring && recurrenceIntervalRadio.isChecked && recurrenceInterval != null) {
                    val hour = intervalStartTimePicker.hour
                    val minute = intervalStartTimePicker.minute
                    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                } else {
                    null
                }

                val category = categories[categorySpinner.selectedItemPosition]
                val priority = TodoItem.Priority.entries[prioritySpinner.selectedItemPosition]
                val dueTime = if (enableTimeCheckbox.isChecked) {
                    val hour = timePicker.hour
                    val minute = timePicker.minute
                    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                } else {
                    null
                }

                addTodoItem(todoText, isRecurring, selectedDays, priority, category, dueTime, recurrenceInterval, intervalStartTime)
            }
        }

        dialogBuilder.setNegativeButton(activity.getString(R.string.cancel_button)) { dialog, _ ->
            dialog.cancel()
        }

        val dialog = dialogBuilder.create()
        dialog.show()


        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: android.app.AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(activity, R.color.text)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }

    private fun addTodoItem(
        text: String,
        isRecurring: Boolean,
        selectedDays: Set<Int> = emptySet(),
        priority: TodoItem.Priority = TodoItem.Priority.MEDIUM,
        category: String = "General",
        dueTime: String? = null,
        recurrenceInterval: Int? = null,
        intervalStartTime: String? = null
    ) {
        val newTodo = TodoItem(
            text = text,
            isChecked = false,
            isRecurring = isRecurring,
            lastCompletedDate = null,
            selectedDays = selectedDays,
            priority = priority,
            category = category,
            dueTime = dueTime,
            recurrenceInterval = recurrenceInterval,
            intervalStartTime = intervalStartTime
        )
        val index = todoItems.size
        todoItems.add(newTodo)
        todoAdapter.notifyItemInserted(index)
        saveTodoItems()


        if (dueTime != null || (newTodo.isIntervalBased() && newTodo.intervalStartTime != null)) {
            val requestCode = todoAlarmManager.getRequestCode(newTodo, index)
            todoAlarmManager.scheduleAlarm(newTodo, requestCode)
        }
    }

    private fun removeTodoItem(todoItem: TodoItem) {
        val index = todoItems.indexOf(todoItem)
        if (index != -1) {

            if (todoItem.dueTime != null) {
                val requestCode = todoAlarmManager.getRequestCode(todoItem, index)
                todoAlarmManager.cancelAlarm(todoItem, requestCode)
            }
            todoItems.removeAt(index)
            todoAdapter.notifyItemRemoved(index)
            saveTodoItems()
        }
    }

    fun loadTodoItems() {
        val jsonString = sharedPreferences.getString("todo_items_json", null)
        
        if (jsonString != null) {
            try {
                val moshi = Moshi.Builder().build()
                val type = Types.newParameterizedType(List::class.java, TodoItem::class.java)
                val jsonAdapter = moshi.adapter<List<TodoItem>>(type)
                val loadedItems = jsonAdapter.fromJson(jsonString)
                
                if (loadedItems != null) {
                    todoItems.clear()
                    todoItems.addAll(loadedItems)
                    todoAdapter.notifyItemRangeInserted(0, todoItems.size)
                    checkRecurringTasks()
                    rescheduleTodoAlarms()
                    return
                }
            } catch (e: Exception) {
                // Fallback to old format for backward compatibility
            }
        }
        
        // Try loading from old format
        loadTodoItemsFromLegacyFormat()
    }

    private fun loadTodoItemsFromLegacyFormat() {
        val todoString = sharedPreferences.getString("todo_items", "") ?: ""
        if (todoString.isEmpty()) return
        
        val todoArray = todoString.split("|")
        val legacyItems = mutableListOf<TodoItem>()
        
        for (itemString in todoArray) {
            if (itemString.isNotEmpty()) {
                val parts = itemString.split(":")
                if (parts.size >= 7) {
                    val text = parts[0]
                    val isChecked = parts[1].toBoolean()
                    val isRecurring = parts[2].toBoolean()
                    val lastCompletedDate = parts[3].ifEmpty { null }
                    val selectedDays = if (parts[4].isNotEmpty()) {
                        parts[4].split(",").mapNotNull { it.toIntOrNull() }.toSet()
                    } else emptySet()
                    val priority = try {
                        TodoItem.Priority.valueOf(parts[5])
                    } catch (_: Exception) {
                        TodoItem.Priority.MEDIUM
                    }
                    val category = parts[6]
                    val dueTime = if (parts.size > 7 && parts[7].isNotEmpty()) parts[7] else null
                    val recurrenceInterval = if (parts.size > 8 && parts[8].isNotEmpty()) {
                        parts[8].toIntOrNull()
                    } else null
                    val intervalStartTime = if (parts.size > 9 && parts[9].isNotEmpty()) {
                        parts[9]
                    } else null
                    
                    legacyItems.add(
                        TodoItem(
                            text = text,
                            isChecked = isChecked,
                            isRecurring = isRecurring,
                            lastCompletedDate = lastCompletedDate,
                            selectedDays = selectedDays,
                            priority = priority,
                            category = category,
                            dueTime = dueTime,
                            recurrenceInterval = recurrenceInterval,
                            intervalStartTime = intervalStartTime
                        )
                    )
                } else if (parts.size >= 3) {
                    val text = parts[0]
                    val isChecked = parts[1].toBoolean()
                    val isRecurring = parts[2].toBoolean()
                    val lastCompletedDate = if (parts.size > 3) parts[3] else null
                    legacyItems.add(TodoItem(id = 0L, text = text, isChecked = isChecked, isRecurring = isRecurring, lastCompletedDate = lastCompletedDate))
                } else if (parts.size == 2) {
                    val text = parts[0]
                    val isChecked = parts[1].toBoolean()
                    legacyItems.add(TodoItem(id = 0L, text = text, isChecked = isChecked))
                }
            }
        }
        
        if (legacyItems.isNotEmpty()) {
            todoItems.clear()
            todoItems.addAll(legacyItems)
            todoAdapter.notifyItemRangeInserted(0, todoItems.size)
            checkRecurringTasks()
            rescheduleTodoAlarms()
            // Migrate to new format
            saveTodoItems()
        }
    }

    fun saveTodoItems() {
        try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, TodoItem::class.java)
            val jsonAdapter = moshi.adapter<List<TodoItem>>(type)
            val jsonString = jsonAdapter.toJson(todoItems)
            sharedPreferences.edit { putString("todo_items_json", jsonString) }
        } catch (e: Exception) {
            // Fallback to old format if JSON serialization fails
            val todoString = todoItems.joinToString("|") {
                val selectedDaysString = it.selectedDays.joinToString(",")
                "${it.text}:${it.isChecked}:${it.isRecurring}:${it.lastCompletedDate ?: ""}:${selectedDaysString}:${it.priority.name}:${it.category}:${it.dueTime ?: ""}:${it.recurrenceInterval ?: ""}:${it.intervalStartTime ?: ""}"
            }
            sharedPreferences.edit { putString("todo_items", todoString) }
        }
    }

    private fun checkRecurringTasks() {
        val currentDate = getCurrentDateString()
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val currentTimeMillis = System.currentTimeMillis()

        val itemsToRemove = mutableListOf<TodoItem>()

        for (todoItem in todoItems) {
            if (todoItem.isRecurring) {
                if (todoItem.isIntervalBased() && todoItem.recurrenceInterval != null) {

                    val lastCompletedDate = todoItem.lastCompletedDate
                    if (lastCompletedDate != null) {
                        try {

                            val lastCompletedMillis = lastCompletedDate.toLongOrNull()
                            if (lastCompletedMillis != null) {
                                val elapsedMinutes = (currentTimeMillis - lastCompletedMillis) / (1000 * 60)
                                if (elapsedMinutes >= todoItem.recurrenceInterval) {

                                    todoItem.isChecked = false
                                    todoItem.lastCompletedDate = null
                                }
                            }
                        } catch (_: Exception) {

                            if (todoItem.lastCompletedDate != currentDate) {
                                todoItem.isChecked = false
                                todoItem.lastCompletedDate = null
                            }
                        }
                    } else if (todoItem.isChecked) {

                        todoItem.lastCompletedDate = currentTimeMillis.toString()
                    }
                } else if (todoItem.isDayBased() && todoItem.lastCompletedDate != currentDate) {

                    if (todoItem.selectedDays.contains(currentDayOfWeek)) {
                        todoItem.isChecked = false
                    }
                } else if (todoItem.lastCompletedDate != currentDate) {

                    todoItem.isChecked = false
                }
            } else if (todoItem.dueTime != null) {

                val dueTimeParts = todoItem.dueTime.split(":")
                if (dueTimeParts.size == 2) {
                    try {
                        val dueHour = dueTimeParts[0].toInt()
                        val dueMinute = dueTimeParts[1].toInt()
                        val dueTimeInMinutes = dueHour * 60 + dueMinute


                        if (currentTimeInMinutes > dueTimeInMinutes) {
                            itemsToRemove.add(todoItem)
                        }
                    } catch (_: NumberFormatException) {

                    }
                }
            }
        }


        for (item in itemsToRemove) {
            val index = todoItems.indexOf(item)
            if (index != -1) {
                todoItems.removeAt(index)
                todoAdapter.notifyItemRemoved(index)
            }
        }

        if (itemsToRemove.isNotEmpty()) {
            saveTodoItems()
        }
    }

    fun rescheduleTodoAlarms() {
        todoAlarmManager.rescheduleAllAlarms(todoItems)
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }
}
