package com.guruswarupa.launch.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.views.CalendarEventsCalendarView
import java.text.SimpleDateFormat
import java.util.*
import com.guruswarupa.launch.ui.theme.ThemeManager

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val allDay: Boolean,
    val calendarId: Long,
    val isFestival: Boolean = false
)

class CalendarEventsWidget(
    private val context: Context,
    private val container: LinearLayout,
    @Suppress("UNUSED_PARAMETER") private val sharedPreferences: android.content.SharedPreferences
) : InitializableWidget {

    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var permissionButton: Button
    @Suppress("unused")
    private lateinit var widgetContainer: LinearLayout
    private lateinit var widgetView: View
    private lateinit var viewToggleButton: Button
    private lateinit var listViewContainer: View
    private lateinit var calendarViewContainer: ViewGroup

    private val events: MutableList<CalendarEvent> = mutableListOf()
    private val allEvents: MutableList<CalendarEvent> = mutableListOf()
    private lateinit var adapter: CalendarEventAdapter
    private var isCalendarView = false
    private var calendarView: CalendarEventsCalendarView? = null

    companion object {
        const val REQUEST_CODE_CALENDAR_PERMISSION = 106
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && hasCalendarPermission()) {
                updateEvents()
                handler.postDelayed(this, 300000)
            }
        }
    }

    override fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.calendar_events_widget, container, false)
        container.addView(widgetView)

        eventsRecyclerView = widgetView.findViewById(R.id.calendar_events_recycler_view)
        emptyState = widgetView.findViewById(R.id.calendar_empty_state)
        permissionButton = widgetView.findViewById(R.id.request_calendar_permission_button)
        widgetContainer = widgetView.findViewById(R.id.calendar_widget_container)
        viewToggleButton = widgetView.findViewById(R.id.calendar_view_toggle_button)
        listViewContainer = widgetView.findViewById(R.id.calendar_list_view_container)
        calendarViewContainer = widgetView.findViewById(R.id.calendar_view_container)

        adapter = CalendarEventAdapter(events) { _ ->
            openCalendarApp()
        }
        eventsRecyclerView.layoutManager = LinearLayoutManager(context)
        eventsRecyclerView.adapter = adapter

        permissionButton.setOnClickListener {
            requestCalendarPermission()
        }

        viewToggleButton.setOnClickListener {
            toggleView()
        }


        initializeCalendarView()



        if (hasCalendarPermission()) {
            setupWithPermission()
        } else {
            setupWithoutPermission()
        }

        isInitialized = true
    }

    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.calendar_events_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = CalendarEventsCalendarView(calendarViewLayout, allEvents) { date, dayEvents ->
            showDayEventDetails(date, dayEvents)
        }
    }

    private fun showDayEventDetails(date: String, dayEvents: List<CalendarEvent>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        val parsedDate = try {
            dateFormat.parse(date)
        } catch (_: Exception) {
            null
        }

        val displayDate = parsedDate?.let { displayFormat.format(it) } ?: date




        val uniqueEvents = dayEvents.distinctBy { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }

            "${event.title.lowercase().trim()}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_${cal.get(Calendar.DAY_OF_MONTH)}"
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.calendar_day_events_dialog, null)
        val dateText = dialogView.findViewById<TextView>(R.id.day_date_text)
        val eventsList = dialogView.findViewById<RecyclerView>(R.id.day_events_list)
        val emptyStateView = dialogView.findViewById<View>(R.id.day_empty_state)

        dateText.text = displayDate

        if (uniqueEvents.isEmpty()) {
            eventsList.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            eventsList.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE

            eventsList.layoutManager = LinearLayoutManager(context)
            eventsList.adapter = CalendarEventAdapter(uniqueEvents) { _ ->
                openCalendarApp()
            }
        }

        android.app.AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.dlg_events))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.support_thank_you_close), null)
            .show()
    }

    private fun toggleView() {
        isCalendarView = !isCalendarView

        if (isCalendarView) {

            listViewContainer.visibility = View.GONE
            calendarViewContainer.visibility = View.VISIBLE
            viewToggleButton.text = context.getString(R.string.calendar_view_list)
            calendarView?.updateEvents(allEvents)
        } else {

            listViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            viewToggleButton.text = context.getString(R.string.calendar_view_calendar)
        }
    }

    private fun setupWithPermission() {
        permissionButton.visibility = View.GONE
        updateEvents()
        handler.post(updateRunnable)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupWithoutPermission() {
        permissionButton.visibility = View.VISIBLE
        emptyState.visibility = View.VISIBLE
        eventsRecyclerView.visibility = View.GONE
        events.clear()
        adapter.notifyDataSetChanged()
    }

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCalendarPermission() {
        if (context is android.app.Activity) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                android.app.AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle(context.getString(R.string.dlg_calendar_permission))
                    .setMessage(context.getString(R.string.dlg_this_permission_allows_the_launcher_to_display_y))
                    .setPositiveButton(context.getString(R.string.dlg_grant_permission)) { _, _ ->

                        if (context is androidx.fragment.app.FragmentActivity) {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                context,
                                arrayOf(Manifest.permission.READ_CALENDAR),
                                REQUEST_CODE_CALENDAR_PERMISSION
                            )
                        }
                    }
                    .setNegativeButton(context.getString(R.string.cancel_button), null)
                    .show()
            }
        } else {

            openSettings()
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_settings), Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateEvents() {
        if (!hasCalendarPermission()) {
            setupWithoutPermission()
            return
        }

        try {

            val allNewEvents = loadAllEvents()
            allEvents.clear()
            allEvents.addAll(allNewEvents)


            val now = System.currentTimeMillis()
            val twoDaysFromNow = now + (2 * 24 * 60 * 60 * 1000)
            val upcomingEvents = allNewEvents.filter { event ->
                event.startTime in now..twoDaysFromNow
            }.sortedBy { it.startTime }.take(5)

            events.clear()
            events.addAll(upcomingEvents)
            adapter.notifyDataSetChanged()


            calendarView?.updateEvents(allEvents)

            if (events.isEmpty() && !isCalendarView) {
                emptyState.visibility = View.VISIBLE
                eventsRecyclerView.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                if (!isCalendarView) {
                    eventsRecyclerView.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.toast_error_loading_calendar_events, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAllEvents(): List<CalendarEvent> {
        val eventsList = mutableListOf<CalendarEvent>()
        val seenEvents = mutableSetOf<String>()


        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        val now = System.currentTimeMillis()

        val oneYearAgo = now - (365 * 24 * 60 * 60 * 1000L)
        val twoYearsFromNow = now + (730 * 24 * 60 * 60 * 1000L)



        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val uri: Uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(oneYearAgo.toString())
            .appendPath(twoYearsFromNow.toString())
            .build()

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calendarIdIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val calendarDisplayNameIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                if (idIndex < 0 || startIndex < 0 || endIndex < 0) {
                    return eventsList
                }

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIndex)
                    val title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "No Title" else "No Title"
                    var startTime = cursor.getLong(startIndex)
                    var endTime = cursor.getLong(endIndex)
                    val location = if (locationIndex >= 0) cursor.getString(locationIndex) else null
                    val allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) == 1
                    val calendarId = if (calendarIdIndex >= 0) cursor.getLong(calendarIdIndex) else 0L
                    val calendarDisplayName = if (calendarDisplayNameIndex >= 0) cursor.getString(calendarDisplayNameIndex) ?: "" else ""
                    // ... (rest of the processing logic)
                    val isFestival = calendarDisplayName.lowercase().let { name ->
                        name.contains("festival") || name.contains("holiday") ||
                        name.contains("holidays") || name.contains("festivals") ||
                        name.contains("religious") || name.contains("indian") ||
                        name.contains("hindu") || name.contains("muslim") ||
                        name.contains("christian") || name.contains("national")
                    }

                    if (allDay) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startTime
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        startTime = cal.timeInMillis

                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        endTime = cal.timeInMillis
                    }

                    val cal = Calendar.getInstance().apply { timeInMillis = startTime }
                    val dateKey = "${title.lowercase().trim()}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_${cal.get(Calendar.DAY_OF_MONTH)}"

                    if (!seenEvents.contains(dateKey)) {
                        seenEvents.add(dateKey)
                        eventsList.add(
                            CalendarEvent(
                                id = eventId,
                                title = title,
                                startTime = startTime,
                                endTime = endTime,
                                location = location,
                                allDay = allDay,
                                calendarId = calendarId,
                                isFestival = isFestival
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            setupWithoutPermission()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return eventsList
    }

    private fun openCalendarApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "time/epoch"
            }
            val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            val resolvedIntent = calendarIntent.resolveActivity(context.packageManager)
            if (resolvedIntent != null) {
                context.startActivity(calendarIntent)
            } else {

                context.startActivity(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_calendar), Toast.LENGTH_SHORT).show()
        }
    }

    fun onResume() {
        if (isInitialized && hasCalendarPermission()) {
            updateEvents()
            handler.post(updateRunnable)
        }
    }

    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }

    fun onPermissionGranted() {
        if (isInitialized) {
            setupWithPermission()
        }
    }

    fun refresh() {
        if (isInitialized) {
            if (hasCalendarPermission()) {
                updateEvents()
                if (isCalendarView) {
                    calendarView?.updateEvents(allEvents)
                }
            } else {
                setupWithoutPermission()
            }
        }
    }

    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }
}

class CalendarEventAdapter(
    private val events: List<CalendarEvent>,
    private val onEventClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.event_title)
        val timeText: TextView = itemView.findViewById(R.id.event_time)
        val locationText: TextView = itemView.findViewById(R.id.event_location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_event_item, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.titleText.text = event.title


        val titleColor = if (event.isFestival) {
            ThemeManager.color(holder.itemView.context, R.attr.appError)
        } else {
            ThemeManager.color(holder.itemView.context, R.attr.appAccentMuted)
        }
        holder.titleText.setTextColor(titleColor)

        val timeFormat = if (event.allDay) {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        } else {
            SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        }

        val startTime = Date(event.startTime)
        val timeString = timeFormat.format(startTime)


        val now = Calendar.getInstance()
        val eventDate = Calendar.getInstance().apply { time = startTime }

        val timeDisplay = when {
            isSameDay(now, eventDate) -> {
                if (event.allDay) {
                    "Today (All Day)"
                } else {
                    val timeOnly = SimpleDateFormat("h:mm a", Locale.getDefault()).format(startTime)
                    "Today at $timeOnly"
                }
            }
            isTomorrow(now, eventDate) -> {
                if (event.allDay) {
                    "Tomorrow (All Day)"
                } else {
                    val timeOnly = SimpleDateFormat("h:mm a", Locale.getDefault()).format(startTime)
                    "Tomorrow at $timeOnly"
                }
            }
            else -> timeString
        }

        holder.timeText.text = timeDisplay

        if (!event.location.isNullOrEmpty()) {
            holder.locationText.text = event.location
            holder.locationText.visibility = View.VISIBLE
        } else {
            holder.locationText.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onEventClick(event)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(cal1: Calendar, cal2: Calendar): Boolean {
        val tomorrow = cal1.clone() as Calendar
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        return tomorrow.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                tomorrow.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount() = events.size
}
