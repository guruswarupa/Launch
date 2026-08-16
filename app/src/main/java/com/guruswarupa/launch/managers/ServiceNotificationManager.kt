package com.guruswarupa.launch.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.receivers.NotificationActionReceiver
import java.util.Collections

object ServiceNotificationManager {
    private const val CHANNEL_ID = "launch_services_channel"
    const val NOTIFICATION_ID = 1000

    private val activeServices = Collections.synchronizedSet(mutableSetOf<String>())
    private var isChannelCreated = false

    fun updateServiceStatus(context: Context, serviceName: String, isRunning: Boolean): Notification {
        if (isRunning) {
            activeServices.add(serviceName)
        } else {
            activeServices.remove(serviceName)
        }

        if (!isChannelCreated) {
            createNotificationChannel(context)
            isChannelCreated = true
        }

        val notification = createNotification(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (activeServices.isNotEmpty()) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
        }

        return notification
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.bg_services_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.bg_services_channel_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun createNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (activeServices.isEmpty()) {
            context.getString(R.string.bg_services_default_text)
        } else {
            activeServices.joinToString(", ")
        }

        val title = when {
            activeServices.size == 1 -> context.getString(R.string.bg_services_title_one_active)
            activeServices.size > 1 -> context.getString(R.string.bg_services_title_many_active, activeServices.size)
            else -> context.getString(R.string.bg_services_title_idle)
        }

        val nightModeAction = createAction(context, NotificationActionReceiver.ACTION_TOGGLE_NIGHT_MODE, context.getString(R.string.bg_services_action_night), R.drawable.ic_night_mode)
        val dimmerAction = createAction(context, NotificationActionReceiver.ACTION_TOGGLE_DIMMER, context.getString(R.string.bg_services_action_dim), R.drawable.ic_dimmer)
        val grayscaleAction = createAction(context, NotificationActionReceiver.ACTION_TOGGLE_GRAYSCALE, context.getString(R.string.bg_services_action_gray), R.drawable.ic_grayscale)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setSilent(true)
            .addAction(nightModeAction)
            .addAction(dimmerAction)
            .addAction(grayscaleAction)
            .build()
    }

    private fun createAction(context: Context, action: String, title: String, iconRes: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(iconRes, title, pendingIntent).build()
    }
}
