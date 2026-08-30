package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import kotlin.math.sqrt
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager
class WeeklyUsageGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var usageData: List<Pair<String, Long>> = emptyList()
    private var appUsageData: List<Pair<String, Map<String, Long>>> = emptyList()

    var onDaySelected: ((String, Map<String, Long>) -> Unit)? = null

    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchedCardIndex: Int = -1
    private val tapThreshold = 50f

    private val cardPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val cardStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val dayTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val timeTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 28f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val instructionPaint = Paint().apply {
        isAntiAlias = true
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    private val dividerPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 1f
    }


    private val reusableRectF = RectF()
    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = "#15000000".toColorInt()
    }

    init {
        updatePaints()
    }

    private fun updatePaints() {
        val widgetText = ThemeManager.color(context, R.attr.appTextPrimary)
        val widgetTextSecondary = ThemeManager.color(context, R.attr.appTextPrimary)
        val itemBackground = ThemeManager.color(context, R.attr.appSurfaceVariant)
        val itemStroke = ThemeManager.color(context, R.attr.appSurfaceBorder)
        val divider = ThemeManager.color(context, R.attr.appSurfaceBorder)

        cardPaint.color = itemBackground
        cardStrokePaint.color = itemStroke
        dayTextPaint.color = widgetText
        timeTextPaint.color = widgetTextSecondary
        instructionPaint.color = widgetTextSecondary
        dividerPaint.color = divider
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updatePaints()
        invalidate()
    }

    fun setUsageData(data: List<Pair<String, Long>>) {
        usageData = data
        requestLayout()
        invalidate()
    }

    fun setAppUsageData(data: List<Pair<String, Map<String, Long>>>) {
        appUsageData = data
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dataToUse = appUsageData.ifEmpty {
            usageData.map { it.first to mapOf("Total" to it.second) }
        }

        if (dataToUse.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val cardHeight = 100f
        val cardSpacing = 12f
        val topPadding = 4f
        val bottomPadding = 8f
        val instructionHeight = 20f

        val totalHeight = topPadding +
            (cardHeight + cardSpacing) * dataToUse.size - cardSpacing +
            bottomPadding + instructionHeight

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = totalHeight.toInt()

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dataToUse = appUsageData.ifEmpty {
            usageData.map { it.first to mapOf("Total" to it.second) }
        }

        if (dataToUse.isEmpty()) {

            instructionPaint.textSize = 32f
            canvas.drawText(
                "No usage data available",
                width / 2f,
                height / 2f,
                instructionPaint
            )
            return
        }

        val padding = 20f
        val cardSpacing = 12f
        val cardHeight = 100f
        val topPadding = 4f


        val startY = topPadding


        dataToUse.forEachIndexed { dayIndex, (day, appUsages) ->
            val cardY = startY + (dayIndex * (cardHeight + cardSpacing))
            val cardRight = width - padding
            val cardBottom = cardY + cardHeight


            val totalUsage = appUsages.values.sum()


            reusableRectF.set(padding, cardY, cardRight, cardBottom)


            val isNightMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (isNightMode) {
                canvas.drawRoundRect(
                    padding + 1f, cardY + 2f, cardRight + 1f, cardBottom + 2f,
                    14f, 14f, shadowPaint
                )
            }


            canvas.drawRoundRect(reusableRectF, 14f, 14f, cardPaint)
            canvas.drawRoundRect(reusableRectF, 14f, 14f, cardStrokePaint)


            dayTextPaint.textAlign = Paint.Align.LEFT
            dayTextPaint.textSize = 36f
            val dayY = cardY + cardHeight / 2f - 15f
            canvas.drawText(day, padding + 24f, dayY, dayTextPaint)


            val usageText = formatUsageTimeInHours(totalUsage)
            timeTextPaint.textAlign = Paint.Align.RIGHT
            timeTextPaint.textSize = 28f
            canvas.drawText(usageText, cardRight - 24f, dayY + 8f, timeTextPaint)


            val dividerY = cardY + cardHeight - 1f
            canvas.drawLine(padding + 24f, dividerY, cardRight - 24f, dividerY, dividerPaint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dataToUse = appUsageData.ifEmpty {
            usageData.map { it.first to mapOf("Total" to it.second) }
        }

        if (dataToUse.isEmpty()) return super.onTouchEvent(event)

        val padding = 20f
        val cardSpacing = 12f
        val cardHeight = 100f
        val topPadding = 4f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchedCardIndex = -1


                dataToUse.forEachIndexed { dayIndex, _ ->
                    val cardY = topPadding + (dayIndex * (cardHeight + cardSpacing))
                    val cardRight = width - padding
                    val cardBottom = cardY + cardHeight

                    if (touchDownX in padding..cardRight &&
                        touchDownY >= cardY && touchDownY <= cardBottom) {
                        touchedCardIndex = dayIndex
                        return true
                    }
                }
                return false
            }

            MotionEvent.ACTION_UP -> {

                if (touchedCardIndex >= 0) {
                    val deltaX = abs(event.x - touchDownX)
                    val deltaY = abs(event.y - touchDownY)
                    val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()


                    if (distance < tapThreshold) {
                        val (day, appUsages) = dataToUse[touchedCardIndex]
                        onDaySelected?.invoke(day, appUsages)
                        performClick()
                        return true
                    }
                }
                touchedCardIndex = -1
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                touchedCardIndex = -1
                return false
            }
        }

        return super.onTouchEvent(event)
    }

    private fun formatUsageTimeInHours(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0h"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
