package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager
import kotlin.math.*

class DailyUsagePieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var appUsages: Map<String, Long> = emptyMap()
    private var selectedSliceIndex: Int = -1

    private val piePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val strokeColor = "#1A1A1A".toColorInt()
    private val noDataColor = "#666666".toColorInt()
    private val zeroUsageColor = "#2A2A2A".toColorInt()

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = 3f
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = ThemeManager.color(context, R.attr.appTextPrimary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }


    private val appColors = listOf(
        "#E53935".toColorInt(),
        "#8E24AA".toColorInt(),
        "#3949AB".toColorInt(),
        "#00897B".toColorInt(),
        "#FDD835".toColorInt(),
        "#FB8C00".toColorInt(),
        "#6D4C41".toColorInt(),
        "#C0CA33".toColorInt(),
        "#00ACC1".toColorInt(),
        "#D81B60".toColorInt(),
        "#5E35B1".toColorInt(),
        "#0277BD".toColorInt(),
        "#00796B".toColorInt(),
        "#689F38".toColorInt(),
        "#F57C00".toColorInt(),
        "#455A64".toColorInt(),
        "#E91E63".toColorInt(),
        "#9C27B0".toColorInt(),
        "#673AB7".toColorInt(),
        "#3F51B5".toColorInt(),
        "#2196F3".toColorInt(),
        "#03A9F4".toColorInt(),
        "#00BCD4".toColorInt(),
        "#009688".toColorInt(),
        "#4CAF50".toColorInt(),
        "#8BC34A".toColorInt(),
        "#CDDC39".toColorInt(),
        "#FFEB3B".toColorInt(),
        "#FFC107".toColorInt(),
        "#FF9800".toColorInt(),
        "#FF5722".toColorInt(),
        "#795548".toColorInt(),
        "#9E9E9E".toColorInt(),
        "#607D8B".toColorInt(),
        "#EF5350".toColorInt(),
        "#EC407A".toColorInt(),
        "#AB47BC".toColorInt(),
        "#7E57C2".toColorInt(),
        "#5C6BC0".toColorInt(),
        "#42A5F5".toColorInt(),
        "#29B6F6".toColorInt(),
        "#26C6DA".toColorInt(),
        "#26A69A".toColorInt(),
        "#66BB6A".toColorInt(),
        "#9CCC65".toColorInt(),
        "#D4E157".toColorInt(),
        "#FFEE58".toColorInt(),
        "#FFCA28".toColorInt(),
        "#FFA726".toColorInt(),
        "#FF7043".toColorInt()
    )

    fun setAppUsageData(data: Map<String, Long>) {
        appUsages = data
        invalidate()
    }

    @Suppress("unused")
    fun setSelectedSlice(index: Int) {
        selectedSliceIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (appUsages.isEmpty()) {
            textPaint.textSize = 32f
            textPaint.color = noDataColor
            canvas.drawText(
                "No usage data",
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 40f

        val totalUsage = appUsages.values.sum()
        if (totalUsage == 0L) {
            piePaint.color = zeroUsageColor
            canvas.drawCircle(centerX, centerY, radius, piePaint)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
            return
        }


        val sortedApps = appUsages.toList().sortedByDescending { it.second }


        var startAngle = -90f
        sortedApps.forEachIndexed { sliceIndex, (_, usage) ->
            val sweepAngle = (usage.toFloat() / totalUsage) * 360f


            val colorIndex = sliceIndex % appColors.size
            piePaint.color = appColors[colorIndex]


            val drawRadius = if (selectedSliceIndex == sliceIndex) radius * 1.1f else radius

            canvas.drawArc(
                centerX - drawRadius,
                centerY - drawRadius,
                centerX + drawRadius,
                centerY + drawRadius,
                startAngle,
                sweepAngle,
                true,
                piePaint
            )


            canvas.drawArc(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                startAngle,
                sweepAngle,
                true,
                strokePaint
            )

            startAngle += sweepAngle
        }
    }

    fun getColorForApp(index: Int): Int {
        return appColors[index % appColors.size]
    }
}
