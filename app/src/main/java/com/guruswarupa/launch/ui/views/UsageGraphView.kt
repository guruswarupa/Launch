package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager

class UsageGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dataPoints = mutableListOf<Float>()
    private val maxPoints = 50

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ThemeManager.color(context, R.attr.appAccent)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (ThemeManager.color(context, R.attr.appAccent) and 0x00FFFFFF) or 0x33000000
    }

    private val path = Path()
    private val fillPath = Path()

    fun addDataPoint(value: Float) {
        dataPoints.add(value)
        if (dataPoints.size > maxPoints) {
            dataPoints.removeAt(0)
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) return

        val width = width.toFloat()
        val height = height.toFloat()
        val stepX = width / (maxPoints - 1)

        path.reset()
        fillPath.reset()

        val startX = if (dataPoints.size < maxPoints) (maxPoints - dataPoints.size) * stepX else 0f
        
        for (i in dataPoints.indices) {
            val x = startX + i * stepX
            val y = height - (dataPoints[i] / 100f * height)
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(startX + (dataPoints.size - 1) * stepX, height)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
