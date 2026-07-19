package com.guruswarupa.launch.ui.adapters

import android.graphics.Paint
import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.models.TodoItem
import java.util.Calendar
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.TypographyManager

class TodoAdapter(
    private val todoItems: MutableList<TodoItem>,
    private val onDeleteClick: (TodoItem) -> Unit,
    private val onTaskStateChanged: () -> Unit = {}
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val todoCheckBox: CheckBox = view.findViewById(R.id.todo_checkbox)
        val todoText: TextView = view.findViewById(R.id.todo_text)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_todo_button)
        val priorityIndicator: View = view.findViewById(R.id.priority_indicator)
        val categoryText: TextView = view.findViewById(R.id.category_text)
        val dueTimeText: TextView = view.findViewById(R.id.due_time_text)
        val intervalText: TextView = view.findViewById(R.id.interval_text)
        val daysContainer: LinearLayout = view.findViewById(R.id.days_container)

        val dayViews = listOf<TextView>(
            view.findViewById(R.id.day_sun),
            view.findViewById(R.id.day_mon),
            view.findViewById(R.id.day_tue),
            view.findViewById(R.id.day_wed),
            view.findViewById(R.id.day_thu),
            view.findViewById(R.id.day_fri),
            view.findViewById(R.id.day_sat)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.todo_item, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todoItem = todoItems[position]
        val context = holder.itemView.context
        
        TypographyManager.applyToView(holder.itemView)

        holder.todoCheckBox.setOnCheckedChangeListener(null)
        holder.todoCheckBox.isChecked = todoItem.isChecked


        val priorityDrawable = when (todoItem.priority) {
            TodoItem.Priority.HIGH -> ContextCompat.getDrawable(context, R.drawable.priority_high)
            TodoItem.Priority.MEDIUM -> ContextCompat.getDrawable(context, R.drawable.priority_medium)
            TodoItem.Priority.LOW -> ContextCompat.getDrawable(context, R.drawable.priority_low)
        }
        holder.priorityIndicator.background = priorityDrawable


        holder.todoText.text = todoItem.text
        if (todoItem.isChecked) {
            holder.todoText.paintFlags = holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }


        if (todoItem.category.isNotEmpty() && todoItem.category != "General") {
            holder.categoryText.text = todoItem.category
            holder.categoryText.visibility = View.VISIBLE
        } else {
            holder.categoryText.visibility = View.GONE
        }


        if (todoItem.dueTime != null) {
            holder.dueTimeText.text = context.getString(R.string.todo_due_format, todoItem.dueTime)
            holder.dueTimeText.visibility = View.VISIBLE
        } else {
            holder.dueTimeText.visibility = View.GONE
        }


        if (todoItem.isIntervalBased() && todoItem.recurrenceInterval != null) {
            val intervalLabel = when (todoItem.recurrenceInterval) {
                30 -> context.getString(R.string.todo_every_30_min)
                60 -> context.getString(R.string.todo_every_1_hr)
                120 -> context.getString(R.string.todo_every_2_hrs)
                180 -> context.getString(R.string.todo_every_3_hrs)
                240 -> context.getString(R.string.todo_every_4_hrs)
                360 -> context.getString(R.string.todo_every_6_hrs)
                480 -> context.getString(R.string.todo_every_8_hrs)
                720 -> context.getString(R.string.todo_every_12_hrs)
                else -> context.getString(R.string.todo_every_min_format, todoItem.recurrenceInterval)
            }
            val startTimeLabel = if (todoItem.intervalStartTime != null) {
                context.getString(R.string.todo_from_format, todoItem.intervalStartTime)
            } else {
                ""
            }
            holder.intervalText.text = context.getString(R.string.converter_result_format, intervalLabel, startTimeLabel)
            holder.intervalText.visibility = View.VISIBLE
        } else {
            holder.intervalText.visibility = View.GONE
        }


        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {
            holder.daysContainer.visibility = View.VISIBLE

            holder.dayViews.forEachIndexed { index, dayView ->
                val dayOfWeek = index + 1
                if (todoItem.selectedDays.contains(dayOfWeek)) {

                    val selectedBgColor = ContextCompat.getColor(context, R.color.nord8)
                    dayView.setBackgroundColor(selectedBgColor)
                    dayView.setTextColor(ContextCompat.getColor(context, R.color.white))
                } else {

                    val unselectedBgColor = if (isNightMode(context)) {
                        ContextCompat.getColor(context, R.color.nord3)
                    } else {
                        ContextCompat.getColor(context, R.color.nord2)
                    }
                    val unselectedTextColor = ContextCompat.getColor(context, R.color.text_secondary)
                    dayView.setBackgroundColor(unselectedBgColor)
                    dayView.setTextColor(unselectedTextColor)
                }
            }
        } else {
            holder.daysContainer.visibility = View.GONE
        }


        holder.todoCheckBox.setOnCheckedChangeListener { _, isChecked ->
            todoItem.isChecked = isChecked

            if (isChecked && todoItem.isRecurring) {
                if (todoItem.isIntervalBased()) {

                    todoItem.lastCompletedDate = System.currentTimeMillis().toString()
                } else {

                    todoItem.lastCompletedDate = getCurrentDateString()
                }
            } else if (!isChecked && todoItem.isRecurring && todoItem.isIntervalBased()) {

                todoItem.lastCompletedDate = null
            }


            if (isChecked) {
                holder.todoText.paintFlags = holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            onTaskStateChanged()
        }


        holder.deleteButton.setOnClickListener {
            onDeleteClick(todoItem)
        }
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }

    private fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun getItemCount(): Int = todoItems.size
}
