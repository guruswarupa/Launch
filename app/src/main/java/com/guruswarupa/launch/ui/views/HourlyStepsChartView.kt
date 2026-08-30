package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.HourlyActivityData
import com.guruswarupa.launch.ui.theme.ThemeManager

class HourlyStepsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var hourlyData: List<HourlyActivityData> = emptyList()
    private val barRect = RectF()

    private val chartPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = "#88A3BE".toColorInt()
    }

    private val gridPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = "#333333".toColorInt()
        strokeWidth = 1f
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = "#AAAAAA".toColorInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val labelTextPaint = Paint().apply {
        isAntiAlias = true
        color = "#666666".toColorInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    private val valueTextPaint = Paint().apply {
        isAntiAlias = true
        color = ThemeManager.color(context, R.attr.appTextPrimary)
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    fun setHourlyData(data: List<HourlyActivityData>) {
        hourlyData = data
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = 400.dpToPx()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (hourlyData.isEmpty()) {

            textPaint.textSize = 32f
            textPaint.color = "#666666".toColorInt()
            canvas.drawText(
                "No hourly data available",
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val padding = 40f
        val topPadding = 60f
        val bottomPadding = 80f
        val chartWidth = width - (padding * 2)
        val chartHeight = height - topPadding - bottomPadding
        val chartRight = padding + chartWidth
        val chartBottom = topPadding + chartHeight


        val maxSteps = hourlyData.maxOfOrNull { it.steps }?.coerceAtLeast(100) ?: 100


        val gridLines = 5
        for (i in 0..gridLines) {
            val y = topPadding + (chartHeight / gridLines) * i
            canvas.drawLine(padding, y, chartRight, y, gridPaint)
        }


        val hourInterval = 4
        for (hour in 0..24 step hourInterval) {
            val x = padding + (chartWidth / 24f) * hour
            canvas.drawLine(x, topPadding, x, chartBottom, gridPaint)
        }


        val barWidth = chartWidth / 24f * 0.7f
        val barSpacing = chartWidth / 24f * 0.3f

        hourlyData.forEachIndexed { index, data ->
            val x = padding + (chartWidth / 24f) * index + barSpacing / 2
            val barHeight = if (maxSteps > 0) {
                (chartHeight * data.steps / maxSteps.toFloat()).coerceAtLeast(0f)
            } else 0f

            val barTop = chartBottom - barHeight
            val barLeft = x
            val barRight = x + barWidth


            barRect.set(barLeft, barTop, barRight, chartBottom)
            canvas.drawRoundRect(barRect, 4f, 4f, chartPaint)


            if (data.steps > 0 && barHeight > 30f) {
                valueTextPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    data.steps.toString(),
                    barLeft + barWidth / 2,
                    barTop - 8f,
                    valueTextPaint
                )
            }
        }


        for (hour in 0..23 step 2) {
            val x = padding + (chartWidth / 24f) * hour + chartWidth / 48f
            val hourLabel = if (hour == 0) "12 AM" else if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM"
            canvas.drawText(
                hourLabel,
                x,
                chartBottom + 30f,
                labelTextPaint
            )
        }


        for (i in 0..gridLines) {
            val value = maxSteps - (maxSteps / gridLines) * i
            val y = topPadding + (chartHeight / gridLines) * i
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                formatSteps(value),
                padding - 10f,
                y + 8f,
                textPaint
            )
        }
    }

    private fun formatSteps(steps: Int): String {
        return when {
            steps >= 1000 -> "${steps / 1000}k"
            else -> steps.toString()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
