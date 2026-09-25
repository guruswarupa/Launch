package com.guruswarupa.launch.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guruswarupa.launch.MainActivity
import java.util.Calendar

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoText = intent.getStringExtra("todo_text") ?: return
        val todoCategory = intent.getStringExtra("todo_category") ?: "General"
        val priorityName = intent.getStringExtra("todo_priority") ?: "MEDIUM"
        val requestCode = intent.getIntExtra("request_code", 0)
        val isIntervalBased = intent.getBooleanExtra("is_interval_based", false)
        val intervalMinutes = intent.getIntExtra("interval_minutes", 0)
        val intervalStartTime = intent.getStringExtra("interval_start_time")

        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationPriority = when (priorityName) {
            "HIGH" -> NotificationCompat.PRIORITY_HIGH
            "MEDIUM" -> NotificationCompat.PRIORITY_DEFAULT
            "LOW" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Todo Reminder")
            .setContentText(todoText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(todoText))
            .setPriority(notificationPriority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(
                android.R.drawable.ic_menu_view,
                "View",
                pendingIntent
            )
            .build()

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(requestCode, notification)
            }

            if (isIntervalBased && intervalMinutes > 0 && intervalStartTime != null) {
                rescheduleIntervalAlarm(context, todoText, todoCategory, priorityName, requestCode, intervalMinutes, intervalStartTime)
            }
        } catch (_: SecurityException) {

        }
    }

    private fun rescheduleIntervalAlarm(
        context: Context,
        todoText: String,
        todoCategory: String,
        priorityName: String,
        requestCode: Int,
        intervalMinutes: Int,
        intervalStartTime: String
    ) {
        val (startHour, startMinute) = parseTime(intervalStartTime) ?: return

        val calendar = Calendar.getInstance()
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        var nextAlarmTimeInMinutes = currentTimeInMinutes + intervalMinutes

        if (nextAlarmTimeInMinutes >= 24 * 60) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            nextAlarmTimeInMinutes -= 24 * 60
        }

        calendar.set(Calendar.HOUR_OF_DAY, nextAlarmTimeInMinutes / 60)
        calendar.set(Calendar.MINUTE, nextAlarmTimeInMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_text", todoText)
            putExtra("todo_category", todoCategory)
            putExtra("todo_priority", priorityName)
            putExtra("request_code", requestCode)
            putExtra("is_interval_based", true)
            putExtra("interval_minutes", intervalMinutes)
            putExtra("interval_start_time", intervalStartTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
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

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Todo Reminders"
            val descriptionText = "Notifications for todo item reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                enableLights(true)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "todo_reminders_channel"
    }
}
