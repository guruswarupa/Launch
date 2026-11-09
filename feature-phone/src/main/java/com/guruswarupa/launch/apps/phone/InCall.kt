package com.guruswarupa.launch.apps.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.apps.common.ContactNames
import java.util.concurrent.CopyOnWriteArrayList

/** What the in-call service knows right now, shared with the call screen. */
object CallStore {
    val calls = ArrayList<Call>()
    var audio: CallAudioState? = null
    var service: LaunchInCallService? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun changed() { main.post { listeners.forEach { it() } } }

    fun number(call: Call): String = call.details.handle?.schemeSpecificPart.orEmpty()

    fun displayName(ctx: Context, call: Call): String {
        val num = number(call)
        return call.details.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: ContactNames.lookup(ctx, num)
            ?: num.ifBlank { ctx.getString(R.string.phone_unknown_caller) }
    }

    /** The call the screen should centre on: ringing first, then the live one, then anything on hold. */
    fun primary(): Call? =
        calls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING || it.state == Call.STATE_PULLING_CALL }
            ?: calls.firstOrNull()
}

class LaunchInCallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) CallNotifier.cancelIncoming(this@LaunchInCallService)
            CallNotifier.ongoing(this@LaunchInCallService)
            CallStore.changed()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) { CallStore.changed() }
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) { CallStore.changed() }
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) { CallStore.changed() }
    }

    override fun onCreate() {
        super.onCreate()
        CallStore.service = this
    }

    override fun onCallAdded(call: Call) {
        CallStore.calls.add(call)
        call.registerCallback(callback)
        val number = CallStore.number(call)
        if (number.isNotEmpty()) Thread { ContactNames.lookup(this, number); CallStore.changed() }.start()
        if (call.state == Call.STATE_RINGING) CallNotifier.incoming(this, call) else showUi()
        CallNotifier.ongoing(this)
        CallStore.changed()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallStore.calls.remove(call)
        CallNotifier.cancelIncoming(this)
        if (call.details.disconnectCause?.code == DisconnectCause.MISSED) CallNotifier.missed(this, CallStore.number(call), CallStore.displayName(this, call))
        if (CallStore.calls.isEmpty()) CallNotifier.cancelOngoing(this) else CallNotifier.ongoing(this)
        CallStore.changed()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallStore.audio = audioState
        CallStore.changed()
    }

    override fun onBringToForeground(showDialpad: Boolean) { showUi() }

    override fun onDestroy() {
        CallStore.service = null
        super.onDestroy()
    }

    private fun showUi() {
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallStore.calls.firstOrNull { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
                context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            ACTION_DECLINE -> CallStore.calls.firstOrNull { it.state == Call.STATE_RINGING }?.reject(false, null)
            ACTION_HANGUP -> CallStore.primary()?.disconnect()
            ACTION_CALL_BACK -> {
                val number = intent.getStringExtra("number").orEmpty()
                if (number.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                NotificationManagerCompat.from(context).cancel(CallNotifier.ID_MISSED)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.guruswarupa.launch.call.ANSWER"
        const val ACTION_DECLINE = "com.guruswarupa.launch.call.DECLINE"
        const val ACTION_HANGUP = "com.guruswarupa.launch.call.HANGUP"
        const val ACTION_CALL_BACK = "com.guruswarupa.launch.call.CALL_BACK"
    }
}

object CallNotifier {
    private const val CH_INCOMING = "calls_incoming"
    private const val CH_ONGOING = "calls_ongoing"
    private const val CH_MISSED = "calls_missed"
    private const val ID_INCOMING = 4101
    private const val ID_ONGOING = 4102
    const val ID_MISSED = 4103

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_INCOMING, ctx.getString(R.string.phone_incoming_call), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, ctx.getString(R.string.phone_ongoing_call), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_MISSED, ctx.getString(R.string.phone_missed_calls), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun action(ctx: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, CallActionReceiver::class.java).setAction(action).apply(extras), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openUi(ctx: Context, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun incoming(ctx: Context, call: Call) {
        channels(ctx)
        val name = CallStore.displayName(ctx, call)
        val n = NotificationCompat.Builder(ctx, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_phone).setContentTitle(name).setContentText(ctx.getString(R.string.phone_incoming_call))
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setContentIntent(openUi(ctx, 1)).setFullScreenIntent(openUi(ctx, 2), true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_INCOMING, n) } catch (_: SecurityException) {}
    }

    fun cancelIncoming(ctx: Context) { NotificationManagerCompat.from(ctx).cancel(ID_INCOMING) }

    fun ongoing(ctx: Context) {
        val call = CallStore.primary() ?: return
        if (call.state == Call.STATE_RINGING) return
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_phone).setContentTitle(CallStore.displayName(ctx, call)).setContentText(ctx.getString(R.string.phone_ongoing_call))
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setUsesChronometer(call.details.connectTimeMillis > 0)
            .setWhen(if (call.details.connectTimeMillis > 0) call.details.connectTimeMillis else System.currentTimeMillis())
            .setContentIntent(openUi(ctx, 3))
            .addAction(0, ctx.getString(R.string.phone_end), action(ctx, CallActionReceiver.ACTION_HANGUP, 12))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_ONGOING, n) } catch (_: SecurityException) {}
    }

    fun cancelOngoing(ctx: Context) { NotificationManagerCompat.from(ctx).cancel(ID_ONGOING) }

    fun missed(ctx: Context, number: String, name: String) {
        channels(ctx)
        val b = NotificationCompat.Builder(ctx, CH_MISSED)
            .setSmallIcon(R.drawable.ic_phone).setContentTitle(ctx.getString(R.string.phone_missed_call_from, name)).setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true).setContentIntent(PendingIntent.getActivity(ctx, 4, Intent(ctx, PhoneActivity::class.java).putExtra("tab", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (number.isNotEmpty()) b.addAction(0, ctx.getString(R.string.phone_call_back), action(ctx, CallActionReceiver.ACTION_CALL_BACK, 13) { putExtra("number", number) })
        try { NotificationManagerCompat.from(ctx).notify(ID_MISSED, b.build()) } catch (_: SecurityException) {}
    }
}
