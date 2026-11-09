package com.guruswarupa.launch.apps.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalAccount(val id: Long, val name: String, val color: Int, val writable: Boolean)

data class CalEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?
) {
    val startDate: LocalDate
        get() = if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate()

    val endDate: LocalDate
        get() {
            val last = (end - 1).coerceAtLeast(begin)
            return if (allDay) Instant.ofEpochMilli(last).atZone(ZoneOffset.UTC).toLocalDate()
            else Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    fun occursOn(day: LocalDate) = !day.isBefore(startDate) && !day.isAfter(endDate)
}

object CalendarRepo {

    fun calendars(cr: ContentResolver): List<CalAccount> {
        val out = ArrayList<CalAccount>()
        try {
            cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                "${CalendarContract.Calendars.VISIBLE} = 1", null, null
            )?.use { c ->
                while (c.moveToNext()) out.add(CalAccount(c.getLong(0), c.getString(1) ?: "", c.getInt(2), c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
            }
        } catch (_: SecurityException) {}
        return out
    }

    fun events(cr: ContentResolver, from: Long, to: Long): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, from)
            ContentUris.appendId(builder, to)
            cr.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION
                ),
                null, null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(CalEvent(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4), c.getInt(5) == 1, c.getInt(6), c.getString(7)))
                }
            }
        } catch (_: SecurityException) {}
        return out
    }

    /** Everything the editor can change. [id] == null means a new event. */
    data class Draft(
        val id: Long?, val calendarId: Long, val title: String, val location: String, val description: String,
        val startDate: LocalDate, val startMin: Int, val endDate: LocalDate, val endMin: Int,
        val allDay: Boolean, val rrule: String?, val reminderMin: Int?
    )

    fun load(cr: ContentResolver, eventId: Long): Draft? {
        try {
            cr.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(
                    CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY, CalendarContract.Events.RRULE, CalendarContract.Events.DURATION,
                    CalendarContract.Events.EVENT_TIMEZONE
                ), null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val allDay = c.getInt(6) == 1
                val begin = c.getLong(4)
                var end = if (c.isNull(5)) 0L else c.getLong(5)
                if (end == 0L) end = begin + durationMillis(c.getString(8), allDay)
                val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
                val s = Instant.ofEpochMilli(begin).atZone(zone)
                // All-day DTEND is exclusive; show the last day in the editor.
                val e = Instant.ofEpochMilli(if (allDay) (end - 1).coerceAtLeast(begin) else end).atZone(zone)
                var reminder: Int? = null
                cr.query(
                    CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES),
                    "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null
                )?.use { r -> if (r.moveToFirst()) reminder = r.getInt(0) }
                return Draft(
                    eventId, c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                    s.toLocalDate(), s.hour * 60 + s.minute, e.toLocalDate(), e.hour * 60 + e.minute,
                    allDay, c.getString(7), reminder
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun durationMillis(d: String?, allDay: Boolean): Long {
        if (d == null) return if (allDay) 86_400_000L else 3_600_000L
        return try { java.time.Duration.parse(if (d.endsWith("W")) "P" + (d.removePrefix("P").removeSuffix("W").toLong() * 7) + "D" else d).toMillis() }
        catch (_: Exception) { Regex("P(\\d+)S").matchEntire(d)?.groupValues?.get(1)?.toLong()?.times(1000) ?: 3_600_000L }
    }

    fun save(cr: ContentResolver, d: Draft): Boolean {
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, d.calendarId)
            put(CalendarContract.Events.TITLE, d.title)
            put(CalendarContract.Events.EVENT_LOCATION, d.location.ifBlank { null })
            put(CalendarContract.Events.DESCRIPTION, d.description.ifBlank { null })
            val start: Long
            val end: Long
            if (d.allDay) {
                start = d.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                end = maxOf(d.endDate, d.startDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val zone = ZoneId.systemDefault()
                start = LocalDateTime.of(d.startDate, java.time.LocalTime.of(d.startMin / 60, d.startMin % 60)).atZone(zone).toInstant().toEpochMilli()
                var e = LocalDateTime.of(d.endDate, java.time.LocalTime.of(d.endMin / 60, d.endMin % 60)).atZone(zone).toInstant().toEpochMilli()
                if (e <= start) e = start + 3_600_000L
                end = e
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }
            put(CalendarContract.Events.DTSTART, start)
            if (d.rrule != null) {
                // Recurring events carry a DURATION instead of a DTEND.
                put(CalendarContract.Events.RRULE, d.rrule)
                put(CalendarContract.Events.DTEND, null as Long?)
                val secs = (end - start) / 1000
                put(CalendarContract.Events.DURATION, if (d.allDay) "P${secs / 86400}D" else "P${secs}S")
            } else {
                put(CalendarContract.Events.RRULE, null as String?)
                put(CalendarContract.Events.DURATION, null as String?)
                put(CalendarContract.Events.DTEND, end)
            }
        }
        return try {
            val id: Long
            if (d.id == null) {
                id = cr.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull() ?: return false
            } else {
                cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, d.id), v, null, null)
                id = d.id
                cr.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
            }
            if (d.reminderMin != null) {
                val r = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, d.reminderMin)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                cr.insert(CalendarContract.Reminders.CONTENT_URI, r)
            }
            true
        } catch (e: Exception) { false }
    }

    fun search(cr: ContentResolver, q: String): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        val like = "%" + q.replace("%", "").replace("_", "") + "%"
        try {
            cr.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.CALENDAR_COLOR, CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.DELETED} = 0 AND (${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?)",
                arrayOf(like, like, like), "${CalendarContract.Events.DTSTART} DESC LIMIT 60"
            )?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(3)
                    val end = if (c.isNull(4) || c.getLong(4) == 0L) begin + 3_600_000L else c.getLong(4)
                    out.add(CalEvent(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), begin, end, c.getInt(5) == 1, c.getInt(6), c.getString(7)))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun calendarName(cr: ContentResolver, id: Long): String? = try {
        cr.query(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id), arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    fun delete(cr: ContentResolver, eventId: Long): Boolean = try {
        cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
    } catch (e: Exception) { false }
}
