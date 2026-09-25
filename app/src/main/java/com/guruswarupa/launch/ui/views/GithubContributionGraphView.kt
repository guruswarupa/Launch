package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.getColorOrThrow
import java.util.*

class GithubContributionGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var contributions: Map<String, Int> = emptyMap()
    private var cellSize = 0f
    private var cellSpacing = 0f
    private var cornerRadius = 0f

    private val colorLevels = listOf(
        android.graphics.Color.parseColor("#EBEDF0"),
        android.graphics.Color.parseColor("#9BE9A8"),
        android.graphics.Color.parseColor("#40C463"),
        android.graphics.Color.parseColor("#30A14E"),
        android.graphics.Color.parseColor("#216E39")
    )

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 1f
        borderPaint.color = android.graphics.Color.parseColor("#D1D5DA")
        cellSize = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_size)
        cellSpacing = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_spacing)
        cornerRadius = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_radius)
    }

    fun setContributions(contributions: Map<String, Int>) {
        this.contributions = contributions

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (contributions.isEmpty()) {
            drawPlaceholder(canvas)
            return
        }

        val maxWeeks = calculateMaxWeeks()
        val cellWidth = (width - paddingRight - paddingLeft - (maxWeeks - 1) * cellSpacing) / maxWeeks
        val cellHeight = (height - paddingTop - paddingBottom - 6 * cellSpacing) / 7

        val adjustedCellSize = minOf(cellWidth, cellHeight)

        var currentX = paddingLeft.toFloat()
        var currentY = paddingTop.toFloat()

        val maxCount = if (contributions.values.maxOrNull() ?: 0 > 0) contributions.values.maxOrNull() ?: 0 else 1

        val sortedDates = contributions.keys.sorted()
        var dateIndex = 0

        for (weekIndex in 0 until maxWeeks) {
            currentY = paddingTop.toFloat()

            for (dayIndex in 0 until 7) {
                if (dateIndex < sortedDates.size) {
                    val date = sortedDates[dateIndex]
                    val count = contributions[date] ?: 0

                    val colorLevel = when {
                        count == 0 -> 0
                        maxCount <= 4 -> when (count) {
                            1 -> 1
                            2 -> 2
                            3 -> 3
                            else -> 4
                        }
                        else -> {
                            val percentile = count.toDouble() / maxCount.toDouble()
                            when {
                                percentile <= 0.25 -> 1
                                percentile <= 0.5 -> 2
                                percentile <= 0.75 -> 3
                                else -> 4
                            }
                        }
                    }

                    cellPaint.color = colorLevels[colorLevel]

                    val rect = RectF(
                        currentX,
                        currentY,
                        currentX + adjustedCellSize,
                        currentY + adjustedCellSize
                    )

                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

                    currentY += adjustedCellSize + cellSpacing
                    dateIndex++
                } else {

                    cellPaint.color = colorLevels[0]
                    val rect = RectF(
                        currentX,
                        currentY,
                        currentX + adjustedCellSize,
                        currentY + adjustedCellSize
                    )

                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

                    currentY += adjustedCellSize + cellSpacing
                }
            }

            currentX += adjustedCellSize + cellSpacing
        }
    }

    private fun calculateMaxWeeks(): Int {
        return if (contributions.isEmpty()) 52 else {

            val sortedDates = contributions.keys.sorted()
            if (sortedDates.size < 7) 1 else sortedDates.size / 7
        }
    }

    private fun drawPlaceholder(canvas: Canvas) {

        cellPaint.color = colorLevels[0]
        cellPaint.style = Paint.Style.FILL

        val rect = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            width - paddingRight.toFloat(),
            height - paddingBottom.toFloat()
        )

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (7 * cellSize + 6 * cellSpacing + paddingTop + paddingBottom).toInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec)
            else -> (52 * cellSize + 51 * cellSpacing + paddingLeft + paddingRight).toInt()
        }

        setMeasuredDimension(width, height)
    }
}