package com.guruswarupa.launch.apps.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Twelve small months on one page. Tap a day to open it, tap a month name to open that month. */
internal class YearView(
    context: Context,
    private val text: Int,
    private val subtle: Int,
    private val accent: Int,
    private val onAccent: Int
) : View(context) {

    var year: Int = LocalDate.now().year
        set(v) { field = v; invalidate() }
    var selected: LocalDate = LocalDate.now()
        set(v) { field = v; invalidate() }
    var busy: Set<LocalDate> = emptySet()
        set(v) { field = v; invalidate() }
    var firstDay: DayOfWeek = DayOfWeek.MONDAY
        set(v) { field = v; invalidate() }
    var onDay: ((LocalDate) -> Unit)? = null
    var onMonth: ((YearMonth) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val cols = 3
    private val pad = 8f * d
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * d; typeface = Typeface.DEFAULT_BOLD }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 8.5f * d }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { hit(e.x, e.y); return true }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 900 && abs(vx) > abs(vy)) { onSwipe?.invoke(if (vx < 0) 1 else -1); return true }
            return false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 1.5f).toInt())
    }

    private fun cellW() = (width - pad * 2) / cols
    private fun cellH() = (height - pad * 2) / 4f
    private val titleH get() = 22f * d

    private fun gridStart(m: YearMonth): LocalDate {
        val first = m.atDay(1)
        return first.minusDays((((first.dayOfWeek.value - firstDay.value) + 7) % 7).toLong())
    }

    private fun hit(x: Float, y: Float) {
        val cw = cellW(); val ch = cellH()
        val c = ((x - pad) / cw).toInt()
        val r = ((y - pad) / ch).toInt()
        if (c !in 0 until cols || r !in 0 until 4) return
        val m = YearMonth.of(year, r * cols + c + 1)
        val ox = pad + c * cw; val oy = pad + r * ch
        val ly = y - oy
        if (ly < titleH) { onMonth?.invoke(m); return }
        val dayW = (cw - 6f * d) / 7f
        val rowH = (ch - titleH - 14f * d) / 6f
        val col = ((x - ox - 3f * d) / dayW).toInt().coerceIn(0, 6)
        val row = ((ly - titleH - 12f * d) / rowH).toInt()
        if (row !in 0..5) { onMonth?.invoke(m); return }
        val date = gridStart(m).plusDays((row * 7 + col).toLong())
        if (YearMonth.from(date) == m) onDay?.invoke(date) else onMonth?.invoke(m)
    }

    override fun onDraw(c: Canvas) {
        val cw = cellW(); val ch = cellH()
        val today = LocalDate.now()
        for (mi in 1..12) {
            val m = YearMonth.of(year, mi)
            val ox = pad + ((mi - 1) % cols) * cw
            val oy = pad + ((mi - 1) / cols) * ch
            titlePaint.color = if (m == YearMonth.from(today)) accent else text
            c.drawText(
                m.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                ox + 4f * d, oy + 14f * d, titlePaint
            )
            val dayW = (cw - 6f * d) / 7f
            val rowH = (ch - titleH - 14f * d) / 6f
            numPaint.color = subtle; numPaint.typeface = Typeface.DEFAULT_BOLD
            for (i in 0..6) {
                val label = firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault())
                c.drawText(label, ox + 3f * d + dayW * i + dayW / 2, oy + titleH + 8f * d, numPaint)
            }
            val start = gridStart(m)
            for (r in 0..5) for (col in 0..6) {
                val date = start.plusDays((r * 7 + col).toLong())
                if (YearMonth.from(date) != m) continue
                val cx = ox + 3f * d + dayW * col + dayW / 2
                val cy = oy + titleH + 12f * d + rowH * r + rowH / 2
                val isSel = date == selected
                val rad = minOf(dayW, rowH) * 0.5f
                if (isSel) { fill.color = accent; c.drawCircle(cx, cy, rad, fill) }
                else if (date == today) { fill.color = accent and 0x00FFFFFF or 0x44000000; c.drawCircle(cx, cy, rad, fill) }
                numPaint.color = if (isSel) onAccent else text
                numPaint.typeface = if (date == today) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                c.drawText(date.dayOfMonth.toString(), cx, cy + numPaint.textSize * 0.35f, numPaint)
                if (date in busy && !isSel) { fill.color = accent; c.drawCircle(cx, cy + rad * 0.95f, 1.2f * d, fill) }
            }
        }
    }
}

/** Hour grid for one or seven days with events laid out in columns. Lives inside a ScrollView. */
internal class TimelineView(
    context: Context,
    private val text: Int,
    private val subtle: Int,
    private val accent: Int,
    private val is24h: Boolean
) : View(context) {

    var days: List<LocalDate> = listOf(LocalDate.now())
        set(v) { field = v; requestLayout(); invalidate() }
    var events: List<CalEvent> = emptyList()
        set(v) { field = v; invalidate() }
    var onEvent: ((CalEvent) -> Unit)? = null
    var onEmpty: ((LocalDate, Int) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    val hourH = 56f * d
    val gutter = 46f * d
    private val zone = ZoneId.systemDefault()

    private val line = Paint().apply { color = subtle and 0x00FFFFFF or 0x33000000; strokeWidth = 1f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subtle; textSize = 10.5f * d; textAlign = Paint.Align.RIGHT }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt(); strokeWidth = 2f * d }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f * d; typeface = Typeface.DEFAULT_BOLD }

    private class Hit(val rect: RectF, val event: CalEvent)
    private val hits = ArrayList<Hit>()

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            hits.asReversed().firstOrNull { it.rect.contains(e.x, e.y) }?.let { onEvent?.invoke(it.event); return true }
            if (e.x > gutter) {
                val col = (((e.x - gutter) / ((width - gutter) / days.size)).toInt()).coerceIn(0, days.size - 1)
                val minutes = ((e.y / hourH) * 60).toInt().coerceIn(0, 23 * 60) / 30 * 30
                onEmpty?.invoke(days[col], minutes)
            }
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 900 && abs(vx) > abs(vy) * 1.5f) { onSwipe?.invoke(if (vx < 0) 1 else -1); return true }
            return false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (hourH * 24).toInt())
    }

    private fun hourLabel(h: Int): String =
        if (is24h) "%02d:00".format(h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

    private class Placed(val e: CalEvent, val startMin: Int, val endMin: Int, var col: Int = 0, var cols: Int = 1)

    private fun place(day: LocalDate): List<Placed> {
        val list = ArrayList<Placed>()
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        for (e in events) {
            if (e.allDay || e.end <= dayStart || e.begin >= dayEnd) continue
            val s = ((maxOf(e.begin, dayStart) - dayStart) / 60000L).toInt()
            val en = ((minOf(e.end, dayEnd) - dayStart) / 60000L).toInt()
            list.add(Placed(e, s, maxOf(en, s + 30)))
        }
        list.sortWith(compareBy({ it.startMin }, { -it.endMin }))
        // Group overlapping events into clusters, then give each a column inside its cluster.
        var i = 0
        while (i < list.size) {
            var clusterEnd = list[i].endMin
            var j = i + 1
            while (j < list.size && list[j].startMin < clusterEnd) { clusterEnd = maxOf(clusterEnd, list[j].endMin); j++ }
            val colEnds = ArrayList<Int>()
            for (k in i until j) {
                val p = list[k]
                var col = colEnds.indexOfFirst { it <= p.startMin }
                if (col < 0) { colEnds.add(p.endMin); col = colEnds.size - 1 } else colEnds[col] = p.endMin
                p.col = col
            }
            for (k in i until j) list[k].cols = colEnds.size
            i = j
        }
        return list
    }

    override fun onDraw(c: Canvas) {
        hits.clear()
        val colW = (width - gutter) / days.size
        for (h in 0..23) {
            val y = h * hourH
            if (h > 0) c.drawText(hourLabel(h), gutter - 6f * d, y + 4f * d, labelPaint)
            c.drawLine(gutter, y, width.toFloat(), y, line)
        }
        for (i in 1 until days.size) c.drawLine(gutter + colW * i, 0f, gutter + colW * i, height.toFloat(), line)

        val today = LocalDate.now()
        days.forEachIndexed { di, day ->
            if (day == today) {
                fill.color = accent and 0x00FFFFFF or 0x14000000
                c.drawRect(gutter + colW * di, 0f, gutter + colW * (di + 1), height.toFloat(), fill)
            }
            for (p in place(day)) {
                val w = colW / p.cols
                val l = gutter + colW * di + w * p.col + 1.5f * d
                val r = l + w - 3f * d
                val t = p.startMin / 60f * hourH + 1f * d
                val b = maxOf(p.endMin / 60f * hourH - 1f * d, t + 18f * d)
                val rect = RectF(l, t, r, b)
                fill.color = p.e.color and 0x00FFFFFF or 0x38000000
                c.drawRoundRect(rect, 6f * d, 6f * d, fill)
                fill.color = p.e.color
                c.drawRoundRect(RectF(l, t, l + 3.5f * d, b), 2f * d, 2f * d, fill)
                tp.color = text
                val tw = (r - l - 10f * d).toInt()
                if (tw > 12 * d) {
                    val sl = StaticLayout.Builder.obtain(p.e.title.ifBlank { "·" }, 0, p.e.title.ifBlank { "·" }.length, tp, tw)
                        .setMaxLines(maxOf(1, ((b - t - 4f * d) / (tp.textSize * 1.2f)).toInt()))
                        .setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                    c.save(); c.clipRect(rect); c.translate(l + 8f * d, t + 3f * d); sl.draw(c); c.restore()
                }
                hits.add(Hit(rect, p.e))
            }
            if (day == today) {
                val now = LocalTime.now()
                val y = (now.hour * 60 + now.minute) / 60f * hourH
                c.drawLine(gutter + colW * di, y, gutter + colW * (di + 1), y, nowPaint)
                c.drawCircle(gutter + colW * di, y, 4.5f * d, nowPaint)
            }
        }
    }

    /** Scroll offset that puts "now" (or 7 am for other days) near the top. */
    fun suggestedScroll(): Int {
        val minutes = if (LocalDate.now() in days) LocalTime.now().let { it.hour * 60 + it.minute - 90 } else 7 * 60
        return (minutes.coerceAtLeast(0) / 60f * hourH).toInt()
    }
}
