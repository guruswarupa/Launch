package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.WeatherManager
import kotlin.math.max
import com.guruswarupa.launch.ui.theme.ThemeManager

class WeatherHourlyForecastChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var entries: List<WeatherManager.HourlyForecast> = emptyList()
    private var isFahrenheit = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPxF(1)
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPxF(3)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(dpToPxF(10))
    }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPxF(2)
    }
    private val timeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
        textAlign = Paint.Align.CENTER
    }
    private val rangeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
    }
    private val pillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPxF(1)
    }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
        textAlign = Paint.Align.CENTER
    }
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(13f)
        textAlign = Paint.Align.CENTER
    }

    init {
        updateColors()
    }

    private fun updateColors() {
        val accent = ThemeManager.color(context, R.attr.appAccent)
        val text = ThemeManager.color(context, R.attr.appTextPrimary)
        val secondary = ThemeManager.color(context, R.attr.appTextPrimary)
        val darkSurface = Color.argb(232, 8, 14, 22)
        val darkPill = Color.argb(244, 14, 22, 32)

        gridPaint.color = Color.argb(105, 136, 192, 208)
        linePaint.color = accent
        pointPaint.color = accent
        pointInnerPaint.color = text
        pointStrokePaint.color = withAlpha(text, 125)
        timeLabelPaint.color = withAlpha(secondary, 210)
        rangeLabelPaint.color = withAlpha(secondary, 210)
        pillFillPaint.color = darkPill
        pillStrokePaint.color = withAlpha(accent, 90)
        pillTextPaint.color = text
        surfacePaint.color = darkSurface
        emptyPaint.color = withAlpha(secondary, 210)
    }

    fun setForecastEntries(hourlyEntries: List<WeatherManager.HourlyForecast>, fahrenheit: Boolean) {
        entries = hourlyEntries
        isFahrenheit = fahrenheit
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (entries.isEmpty()) dpToPx(168) else dpToPx(214)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.weather_forecast_chart_empty),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val cardRect = RectF(
            paddingLeft + dpToPx(2).toFloat(),
            paddingTop + dpToPx(4).toFloat(),
            width - paddingRight - dpToPx(2).toFloat(),
            height - paddingBottom - dpToPx(4).toFloat()
        )
        val cardRadius = dpToPx(18).toFloat()
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, surfacePaint)

        val minTemp = entries.minOf { it.temperature }
        val maxTemp = entries.maxOf { it.temperature }
        val displayPadding = max(2, ((maxTemp - minTemp) * 0.18f).toInt())
        val displayMin = minTemp - displayPadding
        val displayMax = maxTemp + displayPadding
        val tempRange = max(1, displayMax - displayMin)

        val topLabelBaseline = cardRect.top + dpToPx(18)
        val chartLeft = cardRect.left + dpToPx(18)
        val chartRight = cardRect.right - dpToPx(18)
        val chartTop = cardRect.top + dpToPx(48)
        val chartBottom = cardRect.bottom - dpToPx(42)
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        canvas.drawText(formatTemperature(maxTemp), chartLeft, topLabelBaseline, rangeLabelPaint)
        rangeLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatTemperature(minTemp), chartRight, topLabelBaseline, rangeLabelPaint)
        rangeLabelPaint.textAlign = Paint.Align.LEFT

        val xStep = if (entries.size == 1) 0f else chartWidth / (entries.size - 1)
        val points = entries.mapIndexed { index, entry ->
            val normalized = (entry.temperature - displayMin).toFloat() / tempRange.toFloat()
            val x = chartLeft + index * xStep
            val y = chartBottom - normalized * chartHeight
            android.graphics.PointF(x, y)
        }

        repeat(3) { level ->
            val y = chartTop + chartHeight * (level / 2f)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
        }

        points.forEachIndexed { index, point ->
            canvas.drawLine(point.x, chartTop, point.x, chartBottom, gridPaint)
            canvas.drawText(entries[index].timeLabel, point.x, cardRect.bottom - dpToPx(16).toFloat(), timeLabelPaint)
        }

        val linePath = buildSmoothPath(points)
        val fillPath = Path(linePath).apply {
            lineTo(points.last().x, chartBottom)
            lineTo(points.first().x, chartBottom)
            close()
        }
        areaPaint.shader = LinearGradient(
            0f,
            chartTop,
            0f,
            chartBottom,
            withAlpha(linePaint.color, 110),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, areaPaint)
        canvas.drawPath(linePath, linePaint)

        points.forEachIndexed { index, point ->
            drawTemperaturePill(canvas, point.x, point.y, entries[index].temperature)
            canvas.drawCircle(point.x, point.y, dpToPxF(5), pointPaint)
            canvas.drawCircle(point.x, point.y, dpToPxF(2.6f), pointInnerPaint)
            canvas.drawCircle(point.x, point.y, dpToPxF(5), pointStrokePaint)
        }
    }

    private fun formatTemperature(value: Int): String {
        return if (isFahrenheit) "$value°F" else "$value°C"
    }

    private fun buildSmoothPath(points: List<android.graphics.PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        path.moveTo(points.first().x, points.first().y)
        if (points.size == 1) {
            return path
        }

        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val midX = (previous.x + current.x) / 2f
            path.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
        }
        return path
    }

    private fun drawTemperaturePill(canvas: Canvas, x: Float, y: Float, temperature: Int) {
        val text = formatTemperature(temperature)
        val textWidth = pillTextPaint.measureText(text)
        val horizontalPadding = dpToPxF(8)
        val pillHeight = dpToPxF(24)
        val spacingAbovePoint = dpToPxF(16)
        val pillTop = max(paddingTop.toFloat(), y - spacingAbovePoint - pillHeight)
        val pillRect = RectF(
            x - textWidth / 2f - horizontalPadding,
            pillTop,
            x + textWidth / 2f + horizontalPadding,
            pillTop + pillHeight
        )
        val radius = pillHeight / 2f
        canvas.drawRoundRect(pillRect, radius, radius, pillFillPaint)
        canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint)
        val textBaseline = pillRect.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f
        canvas.drawText(text, pillRect.centerX(), textBaseline, pillTextPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun dpToPxF(dp: Int): Float = dp * resources.displayMetrics.density

    private fun dpToPxF(dp: Float): Float = dp * resources.displayMetrics.density

    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
