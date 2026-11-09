package com.guruswarupa.launch.apps.clock

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guruswarupa.launch.clock.R
import com.guruswarupa.launch.ui.activities.withAlpha
import org.json.JSONArray
import java.util.Locale

internal fun formatDuration(ms: Long, centis: Boolean = true): String {
    val total = ms.coerceAtLeast(0)
    val h = total / 3_600_000
    val m = (total / 60_000) % 60
    val s = (total / 1000) % 60
    val c = (total / 10) % 100
    val base = if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    return if (centis) String.format(Locale.getDefault(), "%s.%02d", base, c) else base
}

internal class StopwatchTab(private val a: ClockActivity) : ClockTab {
    private val prefs = a.getSharedPreferences("clock_stopwatch", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { updateTime(); if (running) handler.postDelayed(this, 33) } }

    private var running = false
    private var base = 0L
    private var accumulated = 0L
    private val laps = ArrayList<Long>()

    private val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(16)) }
    private val timeView = a.txt("00:00.00", 64f).apply { typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); gravity = Gravity.CENTER }
    private val lapList = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var startButton: TextView
    private lateinit var lapButton: TextView

    init {
        restore()
        root.addView(timeView, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(150)))
        val buttons = LinearLayout(a).apply { gravity = Gravity.CENTER }
        lapButton = pill(a.getString(R.string.clock_lap), false) { lapOrReset() }
        startButton = pill(a.getString(R.string.clock_start), true) { toggle() }
        buttons.addView(lapButton, LinearLayout.LayoutParams(0, a.dp(60), 1f).apply { marginEnd = a.dp(12) })
        buttons.addView(startButton, LinearLayout.LayoutParams(0, a.dp(60), 1f))
        root.addView(buttons)
        root.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(lapList) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = a.dp(16) })
        update()
    }

    override fun view(): View = root

    override fun onShow() { restore(); update(); handler.removeCallbacks(tick); if (running) tick.run() }
    override fun onHide() { handler.removeCallbacks(tick); persist() }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit) = a.txt(text, 18f, if (filled) a.p.onAccent else a.p.text, true).apply {
        gravity = Gravity.CENTER
        background = a.dialogs.rounded(if (filled) a.p.accent else withAlpha(a.p.text, 28), 30)
        setOnClickListener { onClick() }
    }

    private fun elapsed(): Long = accumulated + if (running) SystemClock.elapsedRealtime() - base else 0L

    private fun toggle() {
        if (running) { accumulated += SystemClock.elapsedRealtime() - base; running = false }
        else { base = SystemClock.elapsedRealtime(); running = true }
        persist(); update()
        handler.removeCallbacks(tick); if (running) tick.run()
    }

    private fun lapOrReset() {
        if (running) { laps.add(elapsed()); persist(); update() }
        else { accumulated = 0; laps.clear(); persist(); update() }
    }

    private fun updateTime() { timeView.text = formatDuration(elapsed()) }

    private fun update() {
        updateTime()
        startButton.text = a.getString(when { running -> R.string.clock_stop; accumulated > 0 -> R.string.clock_resume; else -> R.string.clock_start })
        lapButton.text = a.getString(if (running || accumulated == 0L) R.string.clock_lap else R.string.clock_reset)
        lapButton.alpha = if (!running && accumulated == 0L) 0.4f else 1f
        lapList.removeAllViews()
        if (laps.isEmpty()) return
        val splits = laps.mapIndexed { i, t -> t - (if (i == 0) 0L else laps[i - 1]) }
        val fastest = splits.minOrNull(); val slowest = splits.maxOrNull()
        for (i in laps.indices.reversed()) {
            val color = if (laps.size > 2 && splits[i] == fastest) 0xFF8FBF7A.toInt() else if (laps.size > 2 && splits[i] == slowest) a.p.error else a.p.text
            val row = LinearLayout(a).apply { setPadding(a.dp(8), a.dp(10), a.dp(8), a.dp(10)) }
            row.addView(a.txt(a.getString(R.string.clock_lap_n, i + 1), 15f, a.p.text2), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(a.txt(formatDuration(splits[i]), 15f, color, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.2f).apply { })
            row.addView(a.txt(formatDuration(laps[i]), 15f, a.p.text2).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.2f))
            lapList.addView(row)
        }
    }

    private fun persist() {
        prefs.edit().putBoolean("running", running).putLong("base", base).putLong("acc", accumulated)
            .putLong("boot", SystemClock.elapsedRealtime())
            .putString("laps", JSONArray(laps).toString()).apply()
    }

    private fun restore() {
        running = prefs.getBoolean("running", false)
        base = prefs.getLong("base", 0L)
        accumulated = prefs.getLong("acc", 0L)
        if (running && prefs.getLong("boot", 0L) > SystemClock.elapsedRealtime()) { running = false; accumulated = 0; base = 0 }
        laps.clear()
        try { val arr = JSONArray(prefs.getString("laps", "[]")); for (i in 0 until arr.length()) laps.add(arr.getLong(i)) } catch (_: Exception) {}
    }
}
