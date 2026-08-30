package com.guruswarupa.launch.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.guruswarupa.launch.widgets.CalendarEvent
import java.text.SimpleDateFormat
import java.util.*
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager
class CalendarEventsCalendarView(
    rootView: View,
    private var events: List<CalendarEvent>,
    onDayClick: ((String, List<CalendarEvent>) -> Unit)? = null
) {
    private val context: Context = rootView.context
    private val calendarRecyclerView: RecyclerView = rootView.findViewById(R.id.calendar_events_calendar_recycler_view)
    private val monthYearText: TextView = rootView.findViewById(R.id.calendar_events_month_year_text)
    private val prevMonthButton: View = rootView.findViewById(R.id.calendar_events_prev_month_button)
    private val nextMonthButton: View = rootView.findViewById(R.id.calendar_events_next_month_button)

    private var currentCalendar = Calendar.getInstance()
    private val adapter = CalendarEventsCalendarAdapter(currentCalendar, events, onDayClick)

    init {
        calendarRecyclerView.layoutManager = GridLayoutManager(context, 7)
        calendarRecyclerView.adapter = adapter

        prevMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        nextMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        updateCalendar()
    }

    fun updateEvents(newEvents: List<CalendarEvent>) {
        events = newEvents
        adapter.updateEvents(newEvents)
    }

    private fun updateCalendar() {
        adapter.updateCalendar(currentCalendar)
        updateMonthYearText()
    }

    private fun updateMonthYearText() {
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthYearText.text = monthYearFormat.format(currentCalendar.time)
    }
}

class CalendarEventsCalendarAdapter(
    private var calendar: Calendar,
    private var events: List<CalendarEvent>,
    private val onDayClick: ((String, List<CalendarEvent>) -> Unit)? = null
) : RecyclerView.Adapter<CalendarEventsCalendarAdapter.DayViewHolder>() {

    private val days = mutableListOf<DayItem>()

    init {
        updateCalendar(calendar)
    }

    fun updateCalendar(newCalendar: Calendar) {
        val oldDays = ArrayList(days)
        calendar = newCalendar.clone() as Calendar
        updateDays()
        val diffResult = DiffUtil.calculateDiff(CalendarDiffCallback(oldDays, days))
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateEvents(newEvents: List<CalendarEvent>) {
        val oldDays = ArrayList(days)
        events = newEvents
        updateDays()
        val diffResult = DiffUtil.calculateDiff(CalendarDiffCallback(oldDays, days))
        diffResult.dispatchUpdatesTo(this)
    }

    private class CalendarDiffCallback(
        private val oldList: List<DayItem>,
        private val newList: List<DayItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].dateString == newList[newItemPosition].dateString &&
                   oldList[oldItemPosition].day == newList[newItemPosition].day
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    private fun updateDays() {
        days.clear()


        val firstDayOfMonth = calendar.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)


        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)



        val startOffset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
        repeat(startOffset) {
            days.add(DayItem(day = null, hasEvents = false, isToday = false, dateString = null, events = emptyList()))
        }


        val monthEvents = events.filter { event ->
            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }
            eventCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            eventCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
        }




        val uniqueMonthEvents = monthEvents.distinctBy { event ->
            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }

            "${event.title.lowercase().trim()}_${eventCalendar.get(Calendar.YEAR)}_${eventCalendar.get(Calendar.MONTH)}_${eventCalendar.get(Calendar.DAY_OF_MONTH)}"
        }


        val eventsByDay = uniqueMonthEvents.groupBy { event ->
            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }
            eventCalendar.get(Calendar.DAY_OF_MONTH)
        }


        val currentDate = Calendar.getInstance()
        val today = currentDate.get(Calendar.DAY_OF_MONTH)
        val currentMonth = currentDate.get(Calendar.MONTH)
        val currentYear = currentDate.get(Calendar.YEAR)

        for (day in 1..daysInMonth) {
            val dateStr = formatDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, day)
            val dayEvents = eventsByDay[day] ?: emptyList()
            val hasEvents = dayEvents.isNotEmpty()
            val isToday = day == today &&
                          calendar.get(Calendar.MONTH) == currentMonth &&
                          calendar.get(Calendar.YEAR) == currentYear

            days.add(DayItem(day = day, hasEvents = hasEvents, isToday = isToday, dateString = dateStr, events = dayEvents))
        }
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {

        return String.format(Locale.getDefault(), "%d-%02d-%02d", year, month, day)
    }

    data class DayItem(
        val day: Int?,
        val hasEvents: Boolean = false,
        val isToday: Boolean = false,
        val dateString: String? = null,
        val events: List<CalendarEvent> = emptyList()
    )

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayText: TextView = itemView.findViewById(R.id.calendar_day_text)
        val eventIndicator: View = itemView.findViewById(R.id.event_indicator)
        val festivalIndicator: View = itemView.findViewById(R.id.festival_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_day_item, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayItem = days[position]

        if (dayItem.day == null) {

            holder.dayText.text = ""
            holder.dayText.visibility = View.INVISIBLE
            holder.eventIndicator.visibility = View.GONE
            holder.festivalIndicator.visibility = View.GONE
            holder.itemView.background = null
            holder.itemView.setOnClickListener(null)
        } else {
            holder.dayText.text = dayItem.day.toString()
            holder.dayText.visibility = View.VISIBLE


            val hasFestival = dayItem.events.any { it.isFestival }
            val hasCustomEvent = dayItem.events.any { !it.isFestival }


            if (hasCustomEvent) {
                holder.eventIndicator.visibility = View.VISIBLE
                holder.eventIndicator.setBackgroundColor(
                    ThemeManager.color(holder.itemView.context, R.attr.appAccentMuted)
                )
            } else {
                holder.eventIndicator.visibility = View.GONE
            }


            if (hasFestival) {
                holder.festivalIndicator.visibility = View.VISIBLE
                holder.festivalIndicator.setBackgroundColor(
                    ThemeManager.color(holder.itemView.context, R.attr.appError)
                )
            } else {
                holder.festivalIndicator.visibility = View.GONE
            }


            if (dayItem.isToday) {
                holder.itemView.setBackgroundResource(R.drawable.today_highlight)
            } else {
                holder.itemView.background = null
            }


            if (dayItem.dateString != null) {
                holder.itemView.setOnClickListener {
                    if (dayItem.hasEvents) {
                        onDayClick?.invoke(dayItem.dateString, dayItem.events)
                    }
                }
            } else {
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount() = days.size
}
