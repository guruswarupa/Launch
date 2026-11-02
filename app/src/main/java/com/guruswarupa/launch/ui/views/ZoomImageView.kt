package com.guruswarupa.launch.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var onSingleTap: (() -> Unit)? = null
    var onDismissProgress: ((Float) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onZoomChanged: ((Boolean) -> Unit)? = null

    private val drawMatrix = Matrix()
    private val values = FloatArray(9)
    private val rect = RectF()
    private var fitScale = 1f
    private var maxScale = 8f
    private val scroller = OverScroller(context)
    private var flingX = 0
    private var flingY = 0
    private var dismissOffset = 0f
    private var dismissing = false
    private var zoomAnimator: ValueAnimator? = null
    private var wasZoomed = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomAnimator?.cancel()
            scroller.forceFinished(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val current = currentScale()
            var factor = detector.scaleFactor
            val target = (current * factor).coerceIn(fitScale * 0.85f, maxScale * 1.15f)
            factor = target / current
            drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            apply()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            val current = currentScale()
            if (current < fitScale) animateTo(fitScale, width / 2f, height / 2f)
            else if (current > maxScale) animateTo(maxScale, detector.focusX, detector.focusY)
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (isZoomed()) fitScale else min(fitScale * 3f, maxScale)
            animateTo(target, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress) return false
            if (isZoomed()) {
                drawMatrix.postTranslate(-distanceX, -distanceY)
                apply()
                return true
            }
            if (dismissing || abs(distanceY) > abs(distanceX)) {
                dismissing = true
                dismissOffset -= distanceY
                translationY = dismissOffset
                onDismissProgress?.invoke(min(1f, abs(dismissOffset) / (height * 0.4f)))
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!isZoomed()) {
                if (dismissing && abs(velocityY) > 2500f && abs(dismissOffset) > height * 0.08f) {
                    finishDismiss()
                    return true
                }
                return false
            }
            val bounds = displayRect()
            flingX = 0
            flingY = 0
            scroller.fling(
                0, 0, velocityX.toInt(), velocityY.toInt(),
                Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE
            )
            postOnAnimation(flingRunnable)
            return bounds.width() > 0
        }
    })

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val x = scroller.currX
                val y = scroller.currY
                drawMatrix.postTranslate((x - flingX).toFloat(), (y - flingY).toFloat())
                flingX = x
                flingY = y
                apply()
                postOnAnimation(this)
            }
        }
    }

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        resetToFit()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetToFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetToFit()
    }

    private fun resetToFit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        fitScale = min(width / dw, height / dh)
        maxScale = max(fitScale * 8f, 4f)
        drawMatrix.reset()
        drawMatrix.postScale(fitScale, fitScale)
        drawMatrix.postTranslate((width - dw * fitScale) / 2f, (height - dh * fitScale) / 2f)
        imageMatrix = drawMatrix
        wasZoomed = false
    }

    private fun currentScale(): Float {
        drawMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    fun isZoomed(): Boolean = currentScale() > fitScale * 1.02f

    private fun displayRect(): RectF {
        val d = drawable
        if (d == null) {
            rect.set(0f, 0f, 0f, 0f)
            return rect
        }
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)
        return rect
    }

    private fun clampTranslation() {
        val r = displayRect()
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        if (dx != 0f || dy != 0f) drawMatrix.postTranslate(dx, dy)
    }

    private fun apply() {
        clampTranslation()
        imageMatrix = drawMatrix
        notifyZoom()
    }

    private fun notifyZoom() {
        val zoomed = isZoomed()
        if (zoomed != wasZoomed) {
            wasZoomed = zoomed
            onZoomChanged?.invoke(zoomed)
        }
    }

    private fun animateTo(target: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()
        val start = currentScale()
        zoomAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                val factor = scale / currentScale()
                drawMatrix.postScale(factor, factor, focusX, focusY)
                apply()
            }
            start()
        }
    }

    private fun finishDismiss() {
        val direction = if (dismissOffset >= 0) 1 else -1
        animate().translationY(direction * height.toFloat()).setDuration(180).withEndAction { onDismiss?.invoke() }.start()
    }

    private fun settleDismiss() {
        if (!dismissing) return
        dismissing = false
        if (abs(dismissOffset) > height * 0.18f) {
            finishDismiss()
        } else {
            dismissOffset = 0f
            animate().translationY(0f).setDuration(180).start()
            onDismissProgress?.invoke(0f)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            settleDismiss()
        }
        return true
    }
}
