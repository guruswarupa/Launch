package com.guruswarupa.launch.apps.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

data class AlarmItem(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var days: Int,
    var label: String,
    var enabled: Boolean
)

object AlarmTimes {
    const val ALL_DAYS = 0b1111111

    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun next(now: ZonedDateTime, hour: Int, minute: Int, days: Int): ZonedDateTime {
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (days == 0) return candidate
        repeat(8) {
            if (days and dayBit(candidate.dayOfWeek) != 0) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}

object AlarmStore {
    private const val PREFS = "clock_alarms"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<AlarmItem> {
        val out = ArrayList<AlarmItem>()
        try {
            val arr = JSONArray(prefs(c).getString("alarms", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(AlarmItem(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.optString("l"), o.optBoolean("e", true)))
            }
        } catch (_: Exception) {}
        return out
    }

    fun save(c: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("d", it.days).put("l", it.label).put("e", it.enabled))
        }
        prefs(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun nextId(list: List<AlarmItem>): Int = (list.maxOfOrNull { it.id } ?: 0) + 1

    fun find(c: Context, id: Int): AlarmItem? = load(c).firstOrNull { it.id == id }

    fun timerEnd(c: Context): Long = prefs(c).getLong("timer_end", 0L)
    fun timerTotal(c: Context): Long = prefs(c).getLong("timer_total", 0L)
    fun timerPaused(c: Context): Long = prefs(c).getLong("timer_paused_left", 0L)
    fun saveTimer(c: Context, end: Long, total: Long, pausedLeft: Long) {
        prefs(c).edit().putLong("timer_end", end).putLong("timer_total", total).putLong("timer_paused_left", pausedLeft).apply()
    }
}

object AlarmScheduler {
    const val ACTION_ALARM = "com.guruswarupa.launch.clock.ALARM"
    const val ACTION_SNOOZE_FIRE = "com.guruswarupa.launch.clock.SNOOZE_FIRE"
    const val ACTION_TIMER = "com.guruswarupa.launch.clock.TIMER"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_LABEL = "alarm_label"
    const val SNOOZE_MINUTES = 10
    private const val TIMER_CODE = 700_000
    private const val SNOOZE_CODE = 800_000

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(c: Context, action: String, code: Int, id: Int, label: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).setData(Uri.parse("launchclock://$action/$code"))
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 1, Intent(c, ClockActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setClock(c: Context, at: Long, pi: PendingIntent) {
        try {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(c)), pi)
        } catch (e: SecurityException) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun schedule(c: Context, a: AlarmItem) {
        cancel(c, a.id)
        if (!a.enabled) return
        val next = AlarmTimes.next(ZonedDateTime.now(ZoneId.systemDefault()), a.hour, a.minute, a.days)
        setClock(c, next.toInstant().toEpochMilli(), pending(c, ACTION_ALARM, a.id, a.id, a.label))
    }

    fun cancel(c: Context, id: Int) {
        am(c).cancel(pending(c, ACTION_ALARM, id, id, ""))
    }

    fun snooze(c: Context, label: String) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setClock(c, at, pending(c, ACTION_SNOOZE_FIRE, SNOOZE_CODE, 0, label))
    }

    fun scheduleTimer(c: Context, endAt: Long) {
        setClock(c, endAt, pending(c, ACTION_TIMER, TIMER_CODE, 0, ""))
    }

    fun cancelTimer(c: Context) {
        am(c).cancel(pending(c, ACTION_TIMER, TIMER_CODE, 0, ""))
    }

    fun nextTrigger(c: Context): Long? = am(c).nextAlarmClock?.triggerTime

    fun rescheduleAll(c: Context) {
        AlarmStore.load(c).forEach { schedule(c, it) }
        val end = AlarmStore.timerEnd(c)
        if (end > System.currentTimeMillis()) scheduleTimer(c, end)
    }
}
