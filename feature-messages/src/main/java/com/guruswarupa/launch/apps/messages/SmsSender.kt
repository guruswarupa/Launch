package com.guruswarupa.launch.apps.messages

import android.app.PendingIntent
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.guruswarupa.launch.messages.R

object SmsSender {

    private fun manager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** Saves the text as outgoing, then hands it to the carrier. The sent receiver updates its status. */
    fun send(ctx: Context, address: String, body: String): Boolean {
        val cleaned = address.filter { it.isDigit() || it in "+*#" }.ifEmpty { address.trim() }
        if (cleaned.isEmpty() || body.isBlank()) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, cleaned); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        }
        val row: Uri? = try { ctx.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values) } catch (_: Exception) { null }
        return try {
            val sms = manager(ctx)
            val parts = sms.divideMessage(body)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val sent = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val intent = Intent(ctx, SmsSentReceiver::class.java).setData(row).putExtra("part", i)
                sent.add(PendingIntent.getBroadcast(ctx, (row?.lastPathSegment?.toIntOrNull() ?: 0) * 16 + i, intent, flags))
            }
            if (parts.size == 1) sms.sendTextMessage(cleaned, null, body, sent[0], null)
            else sms.sendMultipartTextMessage(cleaned, null, parts, sent, null)
            true
        } catch (_: Exception) {
            markType(ctx, row, Telephony.Sms.MESSAGE_TYPE_FAILED)
            false
        }
    }

    fun markType(ctx: Context, row: Uri?, type: Int) {
        row ?: return
        try { ctx.contentResolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null) } catch (_: Exception) {}
    }
}

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        val type = if (resultCode == android.app.Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
        SmsSender.markType(context, row, type)
    }
}

/** Receives texts while Launch Messages is the default SMS app, stores them and shows a notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = LinkedHashMap<String, StringBuilder>()
        var date = System.currentTimeMillis()
        parts.forEach { m ->
            val from = m.originatingAddress ?: return@forEach
            bySender.getOrPut(from) { StringBuilder() }.append(m.messageBody.orEmpty())
            if (m.timestampMillis > 0) date = m.timestampMillis
        }
        bySender.forEach { (from, body) ->
            SmsRepo.insertInbox(context, from, body.toString(), date)
            Notifier.incoming(context, from, body.toString())
        }
    }
}

/** Quick replies from the call screen ("can't talk now") arrive here. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val address = uri?.schemeSpecificPart?.substringBefore('?')
        if (!address.isNullOrBlank() && !body.isNullOrBlank() && SmsRepo.isDefault(this)) SmsSender.send(this, address, body)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

object Notifier {
    private const val CHANNEL = "messages_incoming"

    fun incoming(ctx: Context, address: String, body: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.msg_notif_channel), NotificationManager.IMPORTANCE_HIGH))
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val name = SmsRepo.title(ctx, address)
        val open = PendingIntent.getActivity(
            ctx, address.hashCode(),
            Intent(ctx, ConversationActivity::class.java).putExtra(ConversationActivity.EXTRA_ADDRESS, address).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_message).setContentTitle(name).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(ctx).notify(address.hashCode(), n) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, address: String) { NotificationManagerCompat.from(ctx).cancel(address.hashCode()) }
}
