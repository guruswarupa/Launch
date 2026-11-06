package com.guruswarupa.launch.ui.activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.LyricsManager
import com.guruswarupa.launch.managers.LyricsResult
import com.guruswarupa.launch.managers.MediaSessionListener
import com.guruswarupa.launch.managers.MediaSessionMonitor
import com.guruswarupa.launch.managers.NowPlaying
import com.guruswarupa.launch.managers.TorchManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.LaunchNotificationListenerService
import java.lang.ref.WeakReference
import kotlin.math.abs

class LockScreenActivity : AppCompatActivity() {

    companion object {
        private var current: WeakReference<LockScreenActivity>? = null
        fun dismissCurrent() { current?.get()?.finish() }
    }

    private lateinit var content: LinearLayout
    private lateinit var notifBox: LinearLayout
    private lateinit var batteryView: TextView
    private lateinit var hint: TextView
    private lateinit var torchButton: ImageView
    private var torch: TorchManager? = null
    private lateinit var mediaCard: LinearLayout
    private lateinit var mediaTitle: TextView
    private lateinit var mediaArtist: TextView
    private lateinit var playButton: ImageView
    private lateinit var lyricPrev: TextView
    private lateinit var lyricCurrent: TextView
    private lateinit var lyricNext: TextView
    private var mediaMonitor: MediaSessionMonitor? = null
    private var lyricsManager: LyricsManager? = null
    private var lyrics: LyricsResult? = null
    private var lyricIndex = Int.MIN_VALUE
    private var lyricsExecutor: java.util.concurrent.ExecutorService? = null
    private var playing = false
    private var torchOn = false
    private var tracker: VelocityTracker? = null
    private var downY = 0f
    private var downX = 0f
    private var horizontalGesture = false
    @Volatile private var cardDragging = false
    private var unlocking = false
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateNotifications(); updateBattery(); ui.postDelayed(this, 3000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        torch = TorchManager(this)
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    private var wasDeviceLocked = false
    private val unlockWatcher = object : Runnable {
        override fun run() {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isDeviceSecure) {
                val locked = km.isDeviceLocked
                if (wasDeviceLocked && !locked) { unlock(); return }
                wasDeviceLocked = locked
            }
            ui.postDelayed(this, 250)
        }
    }

    private fun goHome() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
        finish()
    }

    private val lyricTicker = object : Runnable {
        override fun run() { updateLyricLine(); ui.postDelayed(this, 400) }
    }

    private val mediaListener = object : MediaSessionListener {
        override fun onTrackChanged(track: NowPlaying?) { onTrack(track) }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playing = state?.state == PlaybackState.STATE_PLAYING
            if (::playButton.isInitialized) playButton.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        }
    }

    private fun onTrack(track: NowPlaying?) {
        if (!::mediaCard.isInitialized) return
        lyrics = null
        lyricIndex = Int.MIN_VALUE
        lyricPrev.text = ""; lyricCurrent.text = ""; lyricNext.text = ""
        if (track == null || (track.title.isEmpty() && track.artist.isEmpty())) { mediaCard.visibility = View.GONE; return }
        mediaCard.visibility = View.VISIBLE
        mediaTitle.text = track.title
        mediaArtist.text = track.artist
        lyricsManager?.fetch(track) { result -> lyrics = result; lyricIndex = Int.MIN_VALUE; updateLyricLine() }
    }

    private fun updateLyricLine() {
        val synced = lyrics as? LyricsResult.Synced ?: return
        val pos = mediaMonitor?.estimatedPositionMs() ?: return
        var idx = -1
        for (i in synced.lines.indices) { if (synced.lines[i].timeMs <= pos) idx = i else break }
        if (idx == lyricIndex) return
        lyricIndex = idx
        lyricPrev.text = synced.lines.getOrNull(idx - 1)?.text.orEmpty()
        lyricCurrent.text = if (idx >= 0) synced.lines[idx].text else ""
        lyricNext.text = synced.lines.getOrNull(idx + 1)?.text.orEmpty()
    }

    private fun startMedia() {
        if (mediaMonitor != null) return
        val monitor = MediaSessionMonitor(this)
        mediaMonitor = monitor
        lyricsExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        lyricsManager = LyricsManager(this, lyricsExecutor!!)
        monitor.addListener(mediaListener)
        ui.post(lyricTicker)
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
        wasDeviceLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        ui.removeCallbacks(unlockWatcher)
        ui.postDelayed(unlockWatcher, 250)
        startMedia()
        mediaMonitor?.refresh()
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        ui.removeCallbacks(unlockWatcher)
        super.onPause()
    }

    override fun onStop() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked && !unlocking) finish()
        super.onStop()
    }

    override fun onDestroy() {
        ui.removeCallbacks(lyricTicker)
        mediaMonitor?.cleanup()
        mediaMonitor = null
        lyricsExecutor?.shutdownNow()
        if (current?.get() === this) current = null
        try { if (torchOn) torch?.turnOffTorch() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        setShadowLayer(8f, 0f, 2f, 0x66000000)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun circle(res: Int, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        val pd = dp(16); setPadding(pd, pd, pd, pd)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x44FFFFFF) }
        setOnClickListener { onClick() }
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val wallpaperView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(wallpaperView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(0x77000000) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        Thread {
            val d = try { android.app.WallpaperManager.getInstance(this).drawable } catch (e: Exception) { null }
            if (d != null) runOnUiThread { wallpaperView.setImageDrawable(d) }
        }.start()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(64), dp(20), dp(24))
        }
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        content.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"
            textSize = if (is24) 84f else 84f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setShadowLayer(10f, 0f, 3f, 0x66000000)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        content.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
            textSize = 17f
            setTextColor(0xFFE0E0E0.toInt())
            setShadowLayer(8f, 0f, 2f, 0x66000000)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) })
        batteryView = label("", 13f, 0xCCFFFFFF.toInt())
        content.addView(batteryView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        mediaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0x55000000); cornerRadius = dp(22).toFloat(); setStroke(1, 0x33FFFFFF) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            visibility = View.GONE
        }
        val mediaRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mediaTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mediaTitle = label("", 16f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        mediaArtist = label("", 13f, 0xCCFFFFFF.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        mediaTexts.addView(mediaTitle); mediaTexts.addView(mediaArtist)
        mediaRow.addView(mediaTexts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        fun ctl(res: Int, size: Int, onClick: () -> Unit) = ImageView(this).apply {
            setImageResource(res); setColorFilter(Color.WHITE)
            val pd = dp(10); setPadding(pd, pd, pd, pd)
            setOnClickListener { onClick() }
        }.also { mediaRow.addView(it, LinearLayout.LayoutParams(dp(size), dp(size))) }
        ctl(R.drawable.ic_media_prev, 44) { mediaMonitor?.activeController?.transportControls?.skipToPrevious() }
        playButton = ctl(R.drawable.ic_media_play, 52) {
            val c = mediaMonitor?.activeController ?: return@ctl
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        }
        ctl(R.drawable.ic_media_next, 44) { mediaMonitor?.activeController?.transportControls?.skipToNext() }
        mediaCard.addView(mediaRow)
        lyricPrev = label("", 13f, 0x88FFFFFF.toInt()).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        lyricCurrent = label("", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        lyricNext = label("", 13f, 0x88FFFFFF.toInt()).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        mediaCard.addView(lyricPrev, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        mediaCard.addView(lyricCurrent, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(2) })
        mediaCard.addView(lyricNext, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(2) })
        content.addView(mediaCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(24) })

        notifBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(notifBox, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(20) })

        val bottom = FrameLayout(this)
        torchButton = circle(R.drawable.ic_cam_flash_on) { toggleTorch() }
        bottom.addView(torchButton, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START or Gravity.CENTER_VERTICAL))
        bottom.addView(circle(R.drawable.ic_cam_camera) { startActivity(Intent(this, CameraActivity::class.java)) },
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.CENTER_VERTICAL))
        hint = label(getString(R.string.lock_swipe_up), 13.5f, 0xCCFFFFFF.toInt())
        bottom.addView(hint, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        content.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)))
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(dp(20), b.top + dp(48), dp(20), b.bottom + dp(24))
            insets
        }
        return root
    }

    private fun toggleTorch() {
        if (torch?.toggleTorch() == true) {
            torchOn = !torchOn
            torchButton.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (torchOn) 0xCCFFFFFF.toInt() else 0x44FFFFFF) }
            torchButton.setColorFilter(if (torchOn) Color.BLACK else Color.WHITE)
        }
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        batteryView.text = "$level%" + if (charging) " · " + getString(R.string.lock_charging) else ""
    }

    private fun updateNotifications() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val showContent = prefs.getBoolean(Constants.Prefs.LOCKSCREEN_NOTIF_CONTENT, false)
        val active = try { LaunchNotificationListenerService.instance?.activeNotifications?.toList() } catch (e: Exception) { null }
        if (cardDragging) return
        notifBox.removeAllViews()
        if (active == null) return
        val shown = active.filter { !it.isOngoing && (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 && it.packageName != packageName && it.isClearable }
            .sortedByDescending { it.postTime }
        val pm = packageManager
        if (showContent) {
            shown.take(5).forEach { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
                notifBox.addView(card(sbn.packageName, appLabel(sbn.packageName), listOf(title, text).filter { it.isNotEmpty() }.joinToString(" · "), listOf(sbn.key)), cardParams())
            }
        } else {
            shown.groupBy { it.packageName }.entries.take(5).forEach { (pkg, list) ->
                notifBox.addView(card(pkg, appLabel(pkg), if (list.size == 1) getString(R.string.lock_one_notification) else getString(R.string.lock_n_notifications, list.size), list.map { it.key }), cardParams())
            }
        }
        val extra = if (showContent) shown.size - 5 else shown.groupBy { it.packageName }.size - 5
        if (extra > 0) notifBox.addView(label(getString(R.string.lock_more, extra), 12.5f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    private fun cardParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun card(pkg: String, title: String, body: String, keys: List<String>): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(0x55000000); cornerRadius = dp(18).toFloat(); setStroke(1, 0x33FFFFFF) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val icon = ImageView(this)
        try { icon.setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
        row.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 14f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (body.isNotEmpty()) texts.addView(label(body, 13f, 0xDDFFFFFF.toInt()).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        attachSwipe(row, keys)
        return row
    }

    private fun attachSwipe(view: View, keys: List<String>) {
        var startX = 0f
        var startY = 0f
        var dragging = false
        var vt: VelocityTracker? = null
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY; dragging = false
                    vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (!dragging && abs(dx) > dp(10) && abs(dx) > abs(dy)) {
                        dragging = true
                        cardDragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        v.translationX = dx
                        v.alpha = 1f - (abs(dx) / v.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 0.8f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    vt?.recycle(); vt = null
                    val dx = e.rawX - startX
                    val flung = abs(vx) > 1200f && (vx > 0) == (dx > 0)
                    if (dragging && e.actionMasked == MotionEvent.ACTION_UP && (abs(dx) > v.width * 0.35f || flung)) {
                        val dir = if (dx > 0) 1 else -1
                        v.animate().translationX(dir * v.width * 1.2f).alpha(0f).setDuration(180).withEndAction {
                            try { LaunchNotificationListenerService.instance?.cancelNotifications(keys.toTypedArray()) } catch (_: Exception) {}
                            (v.parent as? android.view.ViewGroup)?.removeView(v)
                            cardDragging = false
                        }.start()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(150).withEndAction { cardDragging = false }.start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.rawY
                downX = ev.rawX
                horizontalGesture = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dy = ev.rawY - downY
                if (!horizontalGesture && abs(ev.rawX - downX) > dp(10) && abs(ev.rawX - downX) > abs(dy)) horizontalGesture = true
                if (!horizontalGesture && dy < -dp(12)) {
                    val p = (abs(dy) / (resources.displayMetrics.heightPixels * 0.35f)).coerceIn(0f, 1f)
                    content.translationY = dy
                    content.alpha = 1f - p * 0.8f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                val dy = ev.rawY - downY
                tracker?.recycle(); tracker = null
                if (!horizontalGesture && ev.actionMasked == MotionEvent.ACTION_UP && (dy < -resources.displayMetrics.heightPixels * 0.15f || vy < -1500f)) unlock()
                else content.animate().translationY(0f).alpha(1f).setDuration(180).start()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun unlock() {
        if (unlocking) return
        unlocking = true
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) { goHome(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { goHome() }
            override fun onDismissCancelled() { unlocking = false; content.animate().translationY(0f).alpha(1f).setDuration(180).start() }
            override fun onDismissError() { unlocking = false; content.animate().translationY(0f).alpha(1f).setDuration(180).start() }
        })
    }
}
