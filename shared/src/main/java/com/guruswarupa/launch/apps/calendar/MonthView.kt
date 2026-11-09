package com.guruswarupa.launch.apps.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class MonthView(
    context: Context,
    private val text: Int,
    private val subtle: Int,
    private val accent: Int,
    private val onAccent: Int
) : View(context) {

    var month: YearMonth = YearMonth.now()
        set(v) { field = v; invalidate() }
    var selected: LocalDate = LocalDate.now()
        set(v) { field = v; invalidate() }
    var dots: Map<LocalDate, List<Int>> = emptyMap()
        set(v) { field = v; invalidate() }
    var firstDay: DayOfWeek = DayOfWeek.MONDAY
        set(v) { field = v; invalidate() }

    /** When set, cells list event titles as little colored bars instead of dots. */
    var chips: Boolean = false
        set(v) { field = v; invalidate() }
    var items: Map<LocalDate, List<Pair<String, Int>>> = emptyMap()
        set(v) { field = v; invalidate() }

    var showWeeks: Boolean = false
        set(v) { field = v; invalidate() }

    var onPick: ((LocalDate) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val headerH = 30f * d
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 15f * d }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 11.5f * d; typeface = Typeface.DEFAULT_BOLD; color = subtle }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipText = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f * d; typeface = Typeface.DEFAULT_BOLD }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * d; color = accent }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { dateAt(e.x, e.y)?.let { onPick?.invoke(it) }; return true }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 900 && abs(vx) > abs(vy)) { onSwipe?.invoke(if (vx < 0) 1 else -1); return true }
            return false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)

    private fun gridStart(): LocalDate {
        val first = month.atDay(1)
        val offset = ((first.dayOfWeek.value - firstDay.value) + 7) % 7
        return first.minusDays(offset.toLong())
    }

    private fun gridX() = if (showWeeks) 26f * d else 0f
    private fun gridW() = width - gridX()

    private fun dateAt(x: Float, y: Float): LocalDate? {
        if (y < headerH) return null
        val cw = gridW() / 7f
        val ch = (height - headerH) / 6f
        val col = ((x - gridX()) / cw).toInt().coerceIn(0, 6)
        val row = ((y - headerH) / ch).toInt().coerceIn(0, 5)
        return gridStart().plusDays((row * 7 + col).toLong())
    }

    override fun onDraw(c: Canvas) {
        val cw = gridW() / 7f
        val gx = gridX()
        val ch = (height - headerH) / 6f
        for (i in 0..6) {
            val day = firstDay.plus(i.toLong())
            c.drawText(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), gx + cw * i + cw / 2, headerH * 0.7f, headPaint)
        }
        val start = gridStart()
        val today = LocalDate.now()
        if (showWeeks) {
            headPaint.textSize = 10f * d
            for (r in 0..5) {
                val wk = start.plusDays((r * 7 + 3).toLong()).get(java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
                c.drawText(wk.toString(), gx / 2, headerH + ch * r + ch / 2 - 4f * d + 4f * d, headPaint)
            }
            headPaint.textSize = 11.5f * d
        }
        for (r in 0..5) for (col in 0..6) {
            val date = start.plusDays((r * 7 + col).toLong())
            val cx = gx + cw * col + cw / 2
            val inMonth = YearMonth.from(date) == month
            val isSel = date == selected
            val radius = if (chips) 11f * d else minOf(cw, ch) * 0.38f
            val cy = if (chips) headerH + ch * r + 4f * d + radius else headerH + ch * r + ch / 2 - 4f * d
            if (isSel) { fill.color = accent; c.drawCircle(cx, cy, radius, fill) }
            else if (date == today) c.drawCircle(cx, cy, radius, ring)
            numPaint.color = when { isSel -> onAccent; inMonth -> text; else -> subtle and 0x00FFFFFF or 0x66000000 }
            numPaint.typeface = if (date == today) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            numPaint.textSize = if (chips) 12.5f * d else 15f * d
            c.drawText(date.dayOfMonth.toString(), cx, cy + numPaint.textSize * 0.35f, numPaint)
            if (chips) {
                val list = items[date].orEmpty()
                val chipH = 11f * d
                val room = ((ch - 8f * d - radius * 2) / (chipH + 1.5f * d)).toInt().coerceAtLeast(0)
                val shown = if (list.size > room) maxOf(room - 1, 0) else list.size
                var y = headerH + ch * r + 6f * d + radius * 2
                for (k in 0 until shown) {
                    val (name, color) = list[k]
                    val rect = android.graphics.RectF(gx + cw * col + 1.5f * d, y, gx + cw * (col + 1) - 1.5f * d, y + chipH)
                    fill.color = color and 0x00FFFFFF or (if (inMonth) 0xCC000000.toInt() else 0x55000000)
                    c.drawRoundRect(rect, 3f * d, 3f * d, fill)
                    chipText.color = Color.WHITE
                    val label = android.text.TextUtils.ellipsize(name.ifBlank { "·" }, chipText, rect.width() - 4f * d, android.text.TextUtils.TruncateAt.END)
                    c.drawText(label, 0, label.length, rect.left + 2f * d, rect.bottom - 2.6f * d, chipText)
                    y += chipH + 1.5f * d
                }
                if (list.size > shown) {
                    headPaint.textSize = 9f * d
                    c.drawText("+${list.size - shown}", cx, y + chipH - 2f * d, headPaint)
                    headPaint.textSize = 11.5f * d
                }
            } else {
                dots[date]?.take(3)?.forEachIndexed { i, color ->
                    val n = minOf(3, dots[date]!!.size)
                    fill.color = if (isSel) onAccent else color
                    c.drawCircle(cx + (i - (n - 1) / 2f) * 7f * d, cy + radius + 5f * d, 2.4f * d, fill)
                }
            }
        }
    }
}
