package com.guruswarupa.launch.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guruswarupa.launch.models.TodoItem
import java.util.Calendar

class TodoAlarmManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun scheduleExactOrInexact(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }






    fun scheduleAlarm(todoItem: TodoItem, requestCode: Int) {
        if (todoItem.isChecked) {
            return
        }


        if (todoItem.isIntervalBased() && todoItem.intervalStartTime != null && todoItem.recurrenceInterval != null) {
            scheduleIntervalAlarm(todoItem, requestCode)
            return
        }


        if (todoItem.dueTime == null) {
            return
        }

        val (hour, minute) = parseTime(todoItem.dueTime) ?: return

        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {

            todoItem.selectedDays.forEach { dayOfWeek ->
                scheduleRecurringAlarm(todoItem, dayOfWeek, hour, minute, requestCode + dayOfWeek)
            }
        } else {

            scheduleOneTimeAlarm(todoItem, hour, minute, requestCode)
        }
    }




    private fun scheduleIntervalAlarm(todoItem: TodoItem, requestCode: Int) {
        val intervalStartTime = todoItem.intervalStartTime ?: return
        val recurrenceInterval = todoItem.recurrenceInterval ?: return
        val (startHour, startMinute) = parseTime(intervalStartTime) ?: return

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute


        var nextAlarmTimeInMinutes = startTimeInMinutes


        if (currentTimeInMinutes >= startTimeInMinutes) {

            val elapsedSinceStart = currentTimeInMinutes - startTimeInMinutes
            val intervalsPassed = (elapsedSinceStart / recurrenceInterval) + 1
            nextAlarmTimeInMinutes = startTimeInMinutes + (intervalsPassed * recurrenceInterval)
        }


        if (nextAlarmTimeInMinutes >= 24 * 60) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            nextAlarmTimeInMinutes -= 24 * 60
        }

        calendar.set(Calendar.HOUR_OF_DAY, nextAlarmTimeInMinutes / 60)
        calendar.set(Calendar.MINUTE, nextAlarmTimeInMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrInexact(calendar.timeInMillis, pendingIntent)
    }




    private fun scheduleRecurringAlarm(
        todoItem: TodoItem,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)


        var daysUntil = dayOfWeek - currentDayOfWeek
        if (daysUntil < 0) {
            daysUntil += 7
        } else if (daysUntil == 0) {

            val currentTimeInMinutes = currentHour * 60 + currentMinute
            val dueTimeInMinutes = hour * 60 + minute
            if (currentTimeInMinutes >= dueTimeInMinutes) {
                daysUntil = 7
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysUntil)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrInexact(calendar.timeInMillis, pendingIntent)
    }




    private fun scheduleOneTimeAlarm(
        todoItem: TodoItem,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)


        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val dueTimeInMinutes = hour * 60 + minute

        if (currentTimeInMinutes >= dueTimeInMinutes) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrInexact(calendar.timeInMillis, pendingIntent)
    }




    fun cancelAlarm(todoItem: TodoItem, requestCode: Int) {
        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {

            todoItem.selectedDays.forEach { dayOfWeek ->
                cancelAlarmForRequestCode(requestCode + dayOfWeek)
            }
        } else {
            cancelAlarmForRequestCode(requestCode)
        }
    }




    private fun cancelAlarmForRequestCode(requestCode: Int) {
        val intent = Intent(context, TodoAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }




    fun cancelAllAlarms(todoItems: List<TodoItem>) {
        todoItems.forEachIndexed { index, todoItem ->
            val requestCode = getRequestCode(todoItem, index)
            cancelAlarm(todoItem, requestCode)
        }
    }




    fun rescheduleAllAlarms(todoItems: List<TodoItem>) {
        cancelAllAlarms(todoItems)
        todoItems.forEachIndexed { index, todoItem ->
            if (!todoItem.isChecked) {

                if (todoItem.dueTime != null || (todoItem.isIntervalBased() && todoItem.intervalStartTime != null)) {
                    val requestCode = getRequestCode(todoItem, index)
                    scheduleAlarm(todoItem, requestCode)
                }
            }
        }
    }




    private fun createAlarmIntent(todoItem: TodoItem, requestCode: Int): Intent {
        return Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_text", todoItem.text)
            putExtra("todo_category", todoItem.category)
            putExtra("todo_priority", todoItem.priority.name)
            putExtra("request_code", requestCode)
            putExtra("is_interval_based", todoItem.isIntervalBased())
            if (todoItem.isIntervalBased()) {
                putExtra("interval_minutes", todoItem.recurrenceInterval ?: 0)
                putExtra("interval_start_time", todoItem.intervalStartTime ?: "")
            }
        }
    }




    fun getRequestCode(todoItem: TodoItem, index: Int): Int {

        return (todoItem.text.hashCode() + index * 1000) and 0x7FFFFFFF
    }




    private fun parseTime(timeString: String): Pair<Int, Int>? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null

        return try {
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour in 0..23 && minute in 0..59) {
                Pair(hour, minute)
            } else {
                null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
