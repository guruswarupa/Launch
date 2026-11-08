package com.guruswarupa.launch.ui.activities

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

internal class FocusOverlayView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = 0xFFFFD54F.toInt() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * d; color = 0xCCFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFD54F.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; setShadowLayer(4f, 0f, 1f, 0x99000000.toInt()) }

    private var cx = 0f
    private var cy = 0f
    private var shown = false
    private var locked = false
    private var lockedText = ""
    private var scale = 1f
    private var sliderX = 0f
    private var sliderTop = 0f
    private val sliderH = 150f * d
    private val radius = 34f * d
    var fraction = 0.5f
        private set
    var onExposure: ((Float) -> Unit)? = null

    fun show(x: Float, y: Float, lock: Boolean, lockLabel: String) {
        cx = x; cy = y; shown = true; locked = lock; lockedText = lockLabel
        fraction = 0.5f
        sliderX = if (x + radius + 56f * d < width) x + radius + 28f * d else x - radius - 28f * d
        sliderTop = (y - sliderH / 2).coerceIn(8f * d, (height - sliderH - 8f * d).coerceAtLeast(8f * d))
        ValueAnimator.ofFloat(1.35f, 1f).apply { duration = 180; addUpdateListener { scale = it.animatedValue as Float; invalidate() }; start() }
        invalidate()
    }

    fun hide() { shown = false; invalidate() }

    fun hitSlider(x: Float, y: Float): Boolean =
        shown && abs(x - sliderX) < 28f * d && y in (sliderTop - 12f * d)..(sliderTop + sliderH + 12f * d)

    fun drag(y: Float) {
        fraction = (1f - (y - sliderTop) / sliderH).coerceIn(0f, 1f)
        onExposure?.invoke(fraction)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (!shown) return
        val r = radius * scale
        c.drawCircle(cx, cy, r, ring)
        c.drawCircle(cx, cy, 4f * d, knob)
        c.drawLine(sliderX, sliderTop, sliderX, sliderTop + sliderH, track)
        val ky = sliderTop + (1f - fraction) * sliderH
        c.drawCircle(sliderX, ky, 9f * d, knob)
        val rays = 8
        for (i in 0 until rays) {
            val a = Math.PI * 2 * i / rays
            c.drawLine(
                sliderX + (13f * d * Math.cos(a)).toFloat(), ky + (13f * d * Math.sin(a)).toFloat(),
                sliderX + (17f * d * Math.cos(a)).toFloat(), ky + (17f * d * Math.sin(a)).toFloat(), track
            )
        }
        if (locked) c.drawText(lockedText, cx, cy - radius - 8f * d, text)
    }
}

internal class LevelView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND }
    private val fixed = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * d; color = 0x99FFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    private var roll = 0f
    private var pitchFlat = false

    fun update(rollDegrees: Float, flat: Boolean) {
        roll = rollDegrees
        pitchFlat = flat
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val half = 42f * d
        val level = abs(roll) < 1.2f
        paint.color = if (level) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt()
        c.drawLine(cx - half - 24f * d, cy, cx - half - 8f * d, cy, fixed)
        c.drawLine(cx + half + 8f * d, cy, cx + half + 24f * d, cy, fixed)
        c.save()
        c.rotate(-roll, cx, cy)
        c.drawLine(cx - half, cy, cx + half, cy, paint)
        c.restore()
    }
}

internal fun formatShutter(ns: Long): String {
    val s = ns / 1_000_000_000.0
    return if (s >= 0.5) String.format("%.1fs", s) else "1/${(1.0 / s).roundToInt()}"
}

