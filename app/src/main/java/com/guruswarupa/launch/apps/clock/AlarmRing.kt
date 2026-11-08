package com.guruswarupa.launch.apps.clock

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0)
        var label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()
        var timer = false
        when (action) {
            AlarmScheduler.ACTION_ALARM -> {
                val list = AlarmStore.load(context)
                val a = list.firstOrNull { it.id == id } ?: return
                label = a.label
                if (a.days == 0) { a.enabled = false; AlarmStore.save(context, list) } else AlarmScheduler.schedule(context, a)
            }
            AlarmScheduler.ACTION_SNOOZE_FIRE -> {}
            AlarmScheduler.ACTION_TIMER -> {
                timer = true
                AlarmStore.saveTimer(context, 0L, 0L, 0L)
            }
            else -> return
        }
        val svc = Intent(context, AlarmRingService::class.java)
            .setAction(AlarmRingService.ACTION_START)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmRingService.EXTRA_TIMER, timer)
        ContextCompat.startForegroundService(context, svc)
    }
}

class ClockSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}

class AlarmRingService : Service() {
    companion object {
        const val ACTION_START = "com.guruswarupa.launch.clock.RING_START"
        const val ACTION_SNOOZE = "com.guruswarupa.launch.clock.RING_SNOOZE"
        const val ACTION_DISMISS = "com.guruswarupa.launch.clock.RING_DISMISS"
        const val ACTION_STOPPED = "com.guruswarupa.launch.clock.RING_STOPPED"
        const val EXTRA_TIMER = "ring_timer"
        private const val CHANNEL = "clock_alarms"
        private const val NOTIF_ID = 4711
        private const val MAX_RING_MS = 5 * 60_000L
    }

    private var player: MediaPlayer? = null
    private var label = ""
    private var timer = false
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { stopRinging(missed = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> { AlarmScheduler.snooze(this, label); stopRinging(false); return START_NOT_STICKY }
            ACTION_DISMISS -> { stopRinging(false); return START_NOT_STICKY }
        }
        label = intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()
        timer = intent?.getBooleanExtra(EXTRA_TIMER, false) == true
        startForegroundNotification()
        startSound()
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, MAX_RING_MS)
        return START_NOT_STICKY
    }

    private fun title(): String = if (timer) getString(R.string.clock_timer_done) else label.ifBlank { getString(R.string.clock_alarm) }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.clock_alarms_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        val ring = Intent(this, AlarmRingActivity::class.java).putExtra(AlarmScheduler.EXTRA_LABEL, label).putExtra(EXTRA_TIMER, timer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val full = PendingIntent.getActivity(this, 11, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AlarmRingService::class.java).setAction(a), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title())
            .setContentText(ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, getString(if (timer) R.string.clock_stop else R.string.clock_dismiss), action(ACTION_DISMISS, 12))
        if (!timer) b.addAction(0, getString(R.string.clock_snooze), action(ACTION_SNOOZE, 13))
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun startSound() {
        stopSound()
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (_: Exception) {}
    }

    private fun stopSound() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
        } catch (_: Exception) {}
    }

    private fun stopRinging(missed: Boolean) {
        handler.removeCallbacks(giveUp)
        stopSound()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (missed) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID + 1, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(getString(R.string.clock_missed)).setContentText(title()).setAutoCancel(true).build())
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        stopSound()
        super.onDestroy()
    }
}

class AlarmRingActivity : Activity() {
    private val stopped = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { finish() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val timer = intent.getBooleanExtra(AlarmRingService.EXTRA_TIMER, false)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0B0D12.toInt())
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        root.addView(TextView(this).apply {
            text = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            textSize = 72f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (timer) getString(R.string.clock_timer_done) else label.ifBlank { getString(R.string.clock_alarm) }
            textSize = 22f; setTextColor(0xFFB0B8C8.toInt()); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        fun button(text: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
            this.text = text; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(if (filled) 0xFF0B0D12.toInt() else Color.WHITE)
            background = GradientDrawable().apply { setColor(if (filled) 0xFF88C0D0.toInt() else 0x33FFFFFF); cornerRadius = dp(34).toFloat() }
            setOnClickListener { onClick() }
        }
        if (!timer) root.addView(button(getString(R.string.clock_snooze_minutes, AlarmScheduler.SNOOZE_MINUTES), false) {
            startService(Intent(this, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_SNOOZE)); finish()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(16) })
        root.addView(button(getString(if (timer) R.string.clock_stop else R.string.clock_dismiss), true) {
            startService(Intent(this, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_DISMISS)); finish()
        }, LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(root)
        ContextCompat.registerReceiver(this, stopped, IntentFilter(AlarmRingService.ACTION_STOPPED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopped) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}

