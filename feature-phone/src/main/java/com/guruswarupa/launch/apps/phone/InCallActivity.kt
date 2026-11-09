package com.guruswarupa.launch.apps.phone

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.phone.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha

/** The call screen: ringing, dialling and ongoing calls with the usual controls. */
class InCallActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private val listener: () -> Unit = { render() }
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1000) } }
    private var showKeypad = false
    private var dtmf = StringBuilder()
    private var proximity: PowerManager.WakeLock? = null
    private var ended = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        p = FilesPalette(this)
        dialogs = FilesDialogs(this, p)
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        root = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        if (Build_proximitySupported()) proximity = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "launch:incall")
    }

    @Suppress("FunctionName")
    private fun Build_proximitySupported(): Boolean = (getSystemService(Context.POWER_SERVICE) as PowerManager).isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)

    override fun onStart() {
        super.onStart()
        CallStore.listen(listener)
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        CallStore.unlisten(listener)
        handler.removeCallbacks(tick)
        releaseProximity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render()
    }

    private fun releaseProximity() { try { if (proximity?.isHeld == true) proximity?.release() } catch (_: Exception) {} }

    private fun updateProximity(call: Call?) {
        val earpiece = CallStore.audio?.route == CallAudioState.ROUTE_EARPIECE || CallStore.audio?.route == CallAudioState.ROUTE_WIRED_HEADSET
        val want = call != null && call.state == Call.STATE_ACTIVE && CallStore.audio?.route == CallAudioState.ROUTE_EARPIECE && earpiece && !showKeypad
        try { if (want && proximity?.isHeld != true) proximity?.acquire(10 * 60 * 1000L) else if (!want) releaseProximity() } catch (_: Exception) {}
    }

    private fun stateText(call: Call): String = when (call.state) {
        Call.STATE_RINGING -> getString(R.string.phone_incoming_call)
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> getString(R.string.phone_calling)
        Call.STATE_HOLDING -> getString(R.string.phone_on_hold)
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> getString(R.string.phone_call_ended)
        Call.STATE_ACTIVE -> {
            val start = call.details.connectTimeMillis
            if (start <= 0) getString(R.string.phone_calling) else {
                val s = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0)
                if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) else String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
            }
        }
        else -> ""
    }

    private fun roundButton(icon: Int, label: String, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit): View {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.35f; setOnClickListener { if (enabled) onClick() } }
        cell.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(if (on) 0xFF0E1216.toInt() else Color.WHITE)
            val pd = dp(18); setPadding(pd, pd, pd, pd)
            background = dialogs.rounded(if (on) Color.WHITE else withAlpha(Color.WHITE, 30), 36)
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        cell.addView(txt(label, 12.5f, withAlpha(Color.WHITE, 210)).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        return cell
    }

    private fun callButton(color: Int, label: String, rotate: Float, onClick: () -> Unit): View {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { onClick() } }
        cell.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone); setColorFilter(Color.WHITE); rotation = rotate
            val pd = dp(20); setPadding(pd, pd, pd, pd); background = dialogs.rounded(color, 40); contentDescription = label
        }, LinearLayout.LayoutParams(dp(80), dp(80)))
        cell.addView(txt(label, 13f, withAlpha(Color.WHITE, 220)).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        return cell
    }

    private fun render() {
        if (isFinishing) return
        val call = CallStore.primary()
        root.removeAllViews()
        if (call == null) {
            if (!ended) { ended = true; handler.postDelayed({ finish() }, 900) }
            root.addView(txt(getString(R.string.phone_call_ended), 20f).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            releaseProximity()
            return
        }
        ended = false
        updateProximity(call)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(24), dp(24), dp(28)) }

        // Second call banner (call waiting or one on hold)
        val other = CallStore.calls.firstOrNull { it !== call }
        if (other != null) {
            val waiting = other.state == Call.STATE_RINGING
            column.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; background = dialogs.rounded(withAlpha(Color.WHITE, 28), 20); setPadding(dp(16), dp(10), dp(16), dp(10))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(txt(CallStore.displayName(context, other), 15f, bold = true))
                    addView(txt(if (call.state == Call.STATE_RINGING) stateText(other) else getString(R.string.phone_on_hold), 12f, withAlpha(Color.WHITE, 180)))
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (waiting) {
                    addView(txt(getString(R.string.phone_decline), 13f, 0xFFFF6B6B.toInt(), true).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { other.reject(false, null) } })
                    addView(txt(getString(R.string.phone_answer), 13f, 0xFF6BE58F.toInt(), true).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { call.hold(); other.answer(VideoProfile.STATE_AUDIO_ONLY) } })
                } else if (call.state != Call.STATE_RINGING) addView(txt(getString(R.string.phone_swap), 13f, 0xFF6BB5FF.toInt(), true).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { swap(call, other) } })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val name = CallStore.displayName(this, call)
        val number = CallStore.number(call)
        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 0.6f))
        column.addView(txt(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", 44f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(withAlpha(Color.WHITE, 36), 60)
        }, LinearLayout.LayoutParams(dp(120), dp(120)))
        column.addView(txt(name, 28f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0); maxLines = 2 })
        if (name != number && number.isNotEmpty()) column.addView(txt(number, 14f, withAlpha(Color.WHITE, 170)).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
        column.addView(txt(stateText(call), 16f, withAlpha(Color.WHITE, 210)).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        if (call.state == Call.STATE_RINGING) {
            column.addView(swipeControl(onAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }, onDecline = { call.reject(false, null) }),
                LinearLayout.LayoutParams(MATCH_PARENT, dp(76)).apply { bottomMargin = dp(12) })
        } else if (showKeypad) {
            column.addView(txt(dtmf.toString(), 26f).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)); maxLines = 1 })
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#")).forEach { r ->
                val row = LinearLayout(this)
                r.forEach { k -> row.addView(txt(k, 28f).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { dtmf.append(k); call.playDtmfTone(k[0]); handler.postDelayed({ call.stopDtmfTone() }, 140); render() }
                }, LinearLayout.LayoutParams(0, dp(62), 1f)) }
                grid.addView(row)
            }
            column.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            column.addView(txt(getString(R.string.phone_hide_keypad), 15f, 0xFF6BB5FF.toInt(), true).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { showKeypad = false; render() } })
            column.addView(callButton(0xFFE53935.toInt(), getString(R.string.phone_end), 135f) { call.disconnect() })
        } else {
            val audio = CallStore.audio
            val muted = audio?.isMuted == true
            val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
            val holding = call.state == Call.STATE_HOLDING
            val canHold = call.details.can(Call.Details.CAPABILITY_HOLD) || call.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun row(vararg v: View) = LinearLayout(this).apply { gravity = Gravity.CENTER; v.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) } }
            grid.addView(row(
                roundButton(R.drawable.ic_mic_off, getString(R.string.phone_mute), on = muted) { CallStore.service?.setMuted(!muted) },
                roundButton(R.drawable.ic_f_view_grid, getString(R.string.phone_keypad), on = false) { showKeypad = true; render() },
                roundButton(R.drawable.ic_volume_up, getString(R.string.phone_audio), on = speaker || (audio?.route == CallAudioState.ROUTE_BLUETOOTH)) { audioRoutes() }
            ), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })
            val canMerge = other != null && (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || other.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty())
            grid.addView(row(
                roundButton(R.drawable.ic_add, getString(R.string.phone_add_call)) { startActivity(Intent(this, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                roundButton(R.drawable.ic_pause, getString(R.string.phone_hold), on = holding, enabled = canHold) { if (holding) call.unhold() else call.hold() },
                if (other != null) roundButton(R.drawable.ic_arrow_right, if (canMerge) getString(R.string.phone_merge) else getString(R.string.phone_swap), enabled = true) { if (canMerge) merge(call, other) else swap(call, other) }
                else roundButton(R.drawable.ic_message, getString(R.string.phone_message)) { quickMessage(number) }
            ), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(26) })
            column.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            column.addView(callButton(0xFFE53935.toInt(), getString(R.string.phone_end), 135f) { call.disconnect() })
        }
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Answering and declining need a deliberate swipe, so a stray touch can't pick up or drop a call. */
    private fun swipeControl(onAnswer: () -> Unit, onDecline: () -> Unit): View {
        val handleSize = dp(64)
        val green = 0xFF2E9E4F.toInt(); val red = 0xFFE53935.toInt()
        val track = FrameLayout(this).apply { background = dialogs.rounded(withAlpha(Color.WHITE, 40), 40) }
        track.addView(txt("‹‹  " + getString(R.string.phone_decline), 14f, withAlpha(Color.WHITE, 200), true).apply { setPadding(dp(22), 0, 0, 0) },
            FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL))
        track.addView(txt(getString(R.string.phone_answer) + "  ››", 14f, withAlpha(Color.WHITE, 200), true).apply { setPadding(0, 0, dp(22), 0) },
            FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))
        track.getChildAt(0).let { (it as TextView).gravity = Gravity.CENTER_VERTICAL }
        (track.getChildAt(1) as TextView).gravity = Gravity.CENTER_VERTICAL
        val handle = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone); setColorFilter(Color.WHITE)
            val pd = dp(18); setPadding(pd, pd, pd, pd); background = dialogs.rounded(0xFF6B7782.toInt(), 32)
            contentDescription = getString(R.string.phone_answer)
        }
        track.addView(handle, FrameLayout.LayoutParams(handleSize, handleSize, Gravity.CENTER))
        var startX = 0f
        var armed = 0
        track.setOnTouchListener { v, e ->
            val max = (v.width - handleSize) / 2f
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { startX = e.rawX; v.parent?.requestDisallowInterceptTouchEvent(true); true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - startX).coerceIn(-max, max)
                    handle.translationX = dx
                    handle.rotation = if (dx < 0) 135f else 0f
                    val state = when { dx >= max * 0.8f -> 1; dx <= -max * 0.8f -> -1; else -> 0 }
                    handle.background = dialogs.rounded(when { dx > max * 0.15f -> green; dx < -max * 0.15f -> red; else -> 0xFF6B7782.toInt() }, 32)
                    if (state != armed) { armed = state; if (state != 0) v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val done = if (e.actionMasked == android.view.MotionEvent.ACTION_UP) armed else 0
                    armed = 0
                    handle.animate().translationX(0f).rotation(0f).setDuration(160).start()
                    handle.background = dialogs.rounded(0xFF6B7782.toInt(), 32)
                    if (done > 0) onAnswer() else if (done < 0) onDecline()
                    true
                }
                else -> false
            }
        }
        return track
    }

    private fun swap(active: Call, other: Call) {
        if (active.state == Call.STATE_HOLDING) { other.hold(); active.unhold() } else { active.hold(); other.unhold() }
    }

    private fun merge(call: Call, other: Call) {
        try {
            if (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) call.mergeConference()
            else if (other.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) other.mergeConference()
            else call.conference(other)
        } catch (_: Exception) {}
    }

    private fun quickMessage(number: String) {
        if (number.isNotEmpty()) startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun audioRoutes() {
        val audio = CallStore.audio ?: return
        val svc = CallStore.service ?: return
        val mask = audio.supportedRouteMask
        val items = ArrayList<SheetItem>()
        fun add(route: Int, label: Int, icon: Int) {
            if (mask and route != 0) items.add(SheetItem(icon, getString(label), checked = audio.route == route) { svc.setAudioRoute(route) })
        }
        add(CallAudioState.ROUTE_EARPIECE, R.string.phone_route_earpiece, R.drawable.ic_phone)
        add(CallAudioState.ROUTE_SPEAKER, R.string.phone_route_speaker, R.drawable.ic_volume_up)
        add(CallAudioState.ROUTE_BLUETOOTH, R.string.phone_route_bluetooth, R.drawable.ic_bluetooth)
        add(CallAudioState.ROUTE_WIRED_HEADSET, R.string.phone_route_wired, R.drawable.ic_phone)
        if (items.size <= 1) { svc.setAudioRoute(if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER); return }
        dialogs.sheet(getString(R.string.phone_audio), items)
    }
}
