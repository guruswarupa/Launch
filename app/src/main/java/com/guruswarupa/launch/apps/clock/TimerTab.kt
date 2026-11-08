package com.guruswarupa.launch.apps.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.withAlpha

private class RingView(context: Context, private val track: Int, private val arc: Int) : View(context) {
    var fraction = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f * d; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        val pad = paint.strokeWidth
        val r = RectF(pad, pad, width - pad, height - pad)
        paint.color = track
        c.drawArc(r, 0f, 360f, false, paint)
        paint.color = arc
        c.drawArc(r, -90f, 360f * fraction, false, paint)
    }
}

internal class TimerTab(private val a: ClockActivity) : ClockTab {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { if (updateRunning()) handler.postDelayed(this, 100) } }

    private val root = FrameLayout(a)
    private val picker = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(16)) }
    private val running = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(16)) }
    private val values = intArrayOf(0, 5, 0)
    private val valueViews = ArrayList<TextView>()
    private val ring = RingView(a, withAlpha(a.p.text, 36), a.p.accent)
    private val countdown = a.txt("00:00", 54f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER }
    private lateinit var pauseButton: TextView

    init {
        buildPicker()
        buildRunning()
        root.addView(picker, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(running, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun view(): View = root

    override fun onShow() { sync() }
    override fun onHide() { handler.removeCallbacks(tick) }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit) = a.txt(text, 17f, if (filled) a.p.onAccent else a.p.text, true).apply {
        gravity = Gravity.CENTER
        background = a.dialogs.rounded(if (filled) a.p.accent else withAlpha(a.p.text, 28), 30)
        setOnClickListener { onClick() }
    }

    private fun buildPicker() {
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER }
        val labels = listOf(R.string.clock_hours, R.string.clock_minutes, R.string.clock_seconds)
        val maxes = intArrayOf(23, 59, 59)
        for (i in 0..2) {
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val up = a.txt("▲", 20f, a.p.accent, true).apply { gravity = Gravity.CENTER; setPadding(a.dp(22), a.dp(10), a.dp(22), a.dp(10)) }
            val v = a.txt("00", 52f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER }
            val down = a.txt("▼", 20f, a.p.accent, true).apply { gravity = Gravity.CENTER; setPadding(a.dp(22), a.dp(10), a.dp(22), a.dp(10)) }
            up.setOnClickListener { values[i] = if (values[i] >= maxes[i]) 0 else values[i] + 1; refreshPicker() }
            down.setOnClickListener { values[i] = if (values[i] <= 0) maxes[i] else values[i] - 1; refreshPicker() }
            col.addView(up); col.addView(v); col.addView(down)
            col.addView(a.txt(a.getString(labels[i]), 12f, a.p.text2))
            valueViews.add(v)
            row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        picker.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(16) })

        val chips = LinearLayout(a).apply { gravity = Gravity.CENTER }
        listOf(1, 3, 5, 10, 15, 30).forEach { m ->
            chips.addView(a.txt(a.getString(R.string.clock_min_short, m), 14f, a.p.text, true).apply {
                gravity = Gravity.CENTER
                background = a.dialogs.rounded(withAlpha(a.p.text, 24), 18)
                setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
                setOnClickListener { values[0] = m / 60; values[1] = m % 60; values[2] = 0; refreshPicker() }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = a.dp(8) })
        }
        picker.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = a.dp(24) })
        picker.addView(View(a), LinearLayout.LayoutParams(1, 0, 1f))
        picker.addView(pill(a.getString(R.string.clock_start), true) { start() }, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(60)))
        refreshPicker()
    }

    private fun buildRunning() {
        val holder = FrameLayout(a)
        holder.addView(ring, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        holder.addView(countdown, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        running.addView(holder, LinearLayout.LayoutParams(a.dp(280), a.dp(280)).apply { topMargin = a.dp(16) })
        val row = LinearLayout(a).apply { gravity = Gravity.CENTER }
        row.addView(pill(a.getString(R.string.clock_cancel), false) { cancel() }, LinearLayout.LayoutParams(0, a.dp(56), 1f).apply { marginEnd = a.dp(8) })
        row.addView(pill(a.getString(R.string.clock_plus_minute), false) { addMinute() }, LinearLayout.LayoutParams(0, a.dp(56), 1f).apply { marginEnd = a.dp(8) })
        pauseButton = pill(a.getString(R.string.clock_pause), true) { togglePause() }
        row.addView(pauseButton, LinearLayout.LayoutParams(0, a.dp(56), 1f))
        running.addView(View(a), LinearLayout.LayoutParams(1, 0, 1f))
        running.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun refreshPicker() {
        for (i in 0..2) valueViews[i].text = String.format("%02d", values[i])
    }

    private fun isActive(): Boolean = AlarmStore.timerEnd(a) > System.currentTimeMillis() || AlarmStore.timerPaused(a) > 0

    private fun sync() {
        val on = isActive()
        running.visibility = if (on) View.VISIBLE else View.GONE
        picker.visibility = if (on) View.GONE else View.VISIBLE
        handler.removeCallbacks(tick)
        if (on) tick.run()
    }

    private fun start() {
        val total = (values[0] * 3600L + values[1] * 60L + values[2]) * 1000L
        if (total <= 0) return
        val end = System.currentTimeMillis() + total
        AlarmStore.saveTimer(a, end, total, 0)
        AlarmScheduler.scheduleTimer(a, end)
        sync()
    }

    private fun cancel() {
        AlarmScheduler.cancelTimer(a)
        AlarmStore.saveTimer(a, 0, 0, 0)
        sync()
    }

    private fun togglePause() {
        val end = AlarmStore.timerEnd(a)
        val total = AlarmStore.timerTotal(a)
        if (end > System.currentTimeMillis()) {
            AlarmScheduler.cancelTimer(a)
            AlarmStore.saveTimer(a, 0, total, end - System.currentTimeMillis())
        } else {
            val left = AlarmStore.timerPaused(a)
            val newEnd = System.currentTimeMillis() + left
            AlarmStore.saveTimer(a, newEnd, total, 0)
            AlarmScheduler.scheduleTimer(a, newEnd)
        }
        sync()
    }

    private fun addMinute() {
        val end = AlarmStore.timerEnd(a)
        val total = AlarmStore.timerTotal(a) + 60_000
        if (end > System.currentTimeMillis()) {
            AlarmStore.saveTimer(a, end + 60_000, total, 0)
            AlarmScheduler.scheduleTimer(a, end + 60_000)
        } else {
            AlarmStore.saveTimer(a, 0, total, AlarmStore.timerPaused(a) + 60_000)
        }
        sync()
    }

    private fun updateRunning(): Boolean {
        val end = AlarmStore.timerEnd(a)
        val total = AlarmStore.timerTotal(a).coerceAtLeast(1)
        val now = System.currentTimeMillis()
        if (end > 0 && end <= now) { AlarmStore.saveTimer(a, 0, 0, 0); sync(); return false }
        val paused = end == 0L && AlarmStore.timerPaused(a) > 0
        val left = if (paused) AlarmStore.timerPaused(a) else (end - now).coerceAtLeast(0)
        if (running.visibility != View.VISIBLE && (end > now || paused)) { running.visibility = View.VISIBLE; picker.visibility = View.GONE }
        val shown = formatDuration(left + 999, centis = false)
        if (countdown.text.toString() != shown) countdown.text = shown
        ring.fraction = left.toFloat() / total
        val label = a.getString(if (paused) R.string.clock_resume else R.string.clock_pause)
        if (pauseButton.text.toString() != label) pauseButton.text = label
        countdown.alpha = if (paused) 0.55f else 1f
        return !paused
    }
}
