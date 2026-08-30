package com.guruswarupa.launch.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager
import java.time.LocalDate
import kotlin.math.min

class YearProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()


    private val colorCompleted = ThemeManager.color(context, R.attr.appSuccess)
    private val colorRemaining = Color.parseColor("#ECEFF4")

    private var daysInYear = 365
    private var currentDayOfYear = 1
    private var year = 0
    private var cellSize = 0f
    private var spacing = 4f
    private var cornerRadius = 3f

    init {
        updateCurrentDateInfo()
    }

    private fun updateCurrentDateInfo() {
        val today = LocalDate.now()
        year = today.year
        currentDayOfYear = today.dayOfYear
        daysInYear = if (today.isLeapYear) 366 else 365
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)


        val availableWidth = width - paddingStart - paddingEnd
        val columns = 53
        cellSize = (availableWidth - (columns - 1) * spacing) / columns


        val rows = 7
        val calculatedHeight = (rows * cellSize + (rows - 1) * spacing + paddingTop + paddingBottom).toInt()

        setMeasuredDimension(width, calculatedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startX = paddingStart.toFloat()
        val startY = paddingTop.toFloat()


        for (day in 1..daysInYear) {
            val position = getCellPosition(day)
            if (position != null) {
                val (row, col) = position
                val x = startX + col * (cellSize + spacing)
                val y = startY + row * (cellSize + spacing)


                val color = if (day <= currentDayOfYear) {
                    colorCompleted
                } else {
                    colorRemaining
                }

                paint.color = color
                rectF.set(x, y, x + cellSize, y + cellSize)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            }
        }


        val currentPos = getCellPosition(currentDayOfYear)
        if (currentPos != null) {
            val (row, col) = currentPos
            val x = startX + col * (cellSize + spacing)
            val y = startY + row * (cellSize + spacing)


            paint.color = ThemeManager.color(context, R.attr.appTextPrimary)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            rectF.set(x - 1, y - 1, x + cellSize + 1, y + cellSize + 1)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)


            paint.style = Paint.Style.FILL
        }
    }

    private fun getCellPosition(dayOfYear: Int): Pair<Int, Int>? {



        val week = (dayOfYear - 1) / 7
        val dayOfWeek = (dayOfYear - 1) % 7


        if (week >= 53) return null

        return Pair(dayOfWeek, week)
    }

    fun refresh() {
        updateCurrentDateInfo()
        invalidate()
    }


    fun getProgressInfo(): ProgressInfo {
        val percentage = (currentDayOfYear.toDouble() / daysInYear.toDouble() * 100).toInt()
        val daysRemaining = daysInYear - currentDayOfYear
        return ProgressInfo(currentDayOfYear, daysInYear, percentage, daysRemaining, year)
    }

    data class ProgressInfo(
        val daysCompleted: Int,
        val totalDays: Int,
        val percentage: Int,
        val daysRemaining: Int,
        val currentYear: Int
    )
}