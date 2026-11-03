package com.guruswarupa.launch.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PdfReaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    class Highlight(val page: Int, val rect: RectF, val current: Boolean)

    var onPageChanged: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private class Detail(val bitmap: Bitmap, val area: RectF, val scale: Float)

    private var renderer: PdfRenderer? = null
    private var pageW = FloatArray(0)
    private var pageH = FloatArray(0)
    private var tops = FloatArray(0)
    private var heights = FloatArray(0)
    private var contentW = 0f
    private var contentH = 0f
    private var pageCount = 0

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val maxScale = 8f
    private val density = resources.displayMetrics.density
    private val gap = 10f * density

    private val renderLock = Any()
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    @Volatile private var closed = false
    private val pending = HashSet<Int>()
    private val baseCache = object : LruCache<Int, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 6).toInt().coerceAtLeast(32 * 1024)) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount / 1024
    }
    private val details = HashMap<Int, Detail>()
    private var detailJobGen = 0

    private var highlights: Map<Int, List<Highlight>> = emptyMap()
    private var lastPage = -1

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val shadowPaint = Paint().apply { color = 0x33000000 }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E9E9E.toInt(); textSize = 18f * density; textAlign = Paint.Align.CENTER }
    private val hlPaint = Paint().apply { color = 0x66FFEB3B.toInt() }
    private val hlCurrent = Paint().apply { color = 0x99FF9800.toInt() }

    private val scroller = OverScroller(context)
    private var zoomAnimator: ValueAnimator? = null
    private var scrollAnimator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomAnimator?.cancel(); scroller.forceFinished(true); scrollAnimator?.cancel()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAt(scale * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) { scheduleDetails() }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            scrollAnimator?.cancel()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (scale > 1.4f) 1f else 2.6f
            animateZoom(target, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress) return false
            tx -= dx
            ty -= dy
            clamp()
            invalidate()
            reportPage()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (scaleDetector.isInProgress) return false
            val minX = min(0f, width - contentW * scale)
            val minY = min(0f, height - contentH * scale)
            scroller.fling(tx.toInt(), ty.toInt(), vx.toInt(), vy.toInt(), minX.toInt(), 0, minY.toInt(), 0)
            postInvalidateOnAnimation()
            return true
        }
    })

    fun setDocument(renderer: PdfRenderer, widths: FloatArray, heights: FloatArray) {
        this.renderer = renderer
        pageW = widths
        pageH = heights
        pageCount = widths.size
        scale = 1f; tx = 0f; ty = 0f
        baseCache.evictAll(); details.clear(); pending.clear()
        generation++
        layoutPages()
        invalidate()
    }

    fun pageCount() = pageCount

    fun currentPage(): Int {
        if (pageCount == 0) return 0
        val y = (height / 2f - ty) / scale
        var lo = 0
        var hi = pageCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (tops[mid] <= y + gap / 2) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun scrollToPage(page: Int, animate: Boolean = true) {
        if (pageCount == 0) return
        val p = page.coerceIn(0, pageCount - 1)
        animateScrollTo(tx, -(tops[p] - gap / 2) * scale, animate)
    }

    fun scrollToRect(page: Int, r: RectF) {
        if (pageCount == 0) return
        val p = page.coerceIn(0, pageCount - 1)
        val cy = tops[p] + (r.top + r.bottom) / 2f * heights[p]
        val cx = (r.left + r.right) / 2f * contentW
        var targetScale = scale
        if (targetScale < 1.6f) { zoomAnimator?.cancel(); targetScale = 1.6f }
        val newTx = width / 2f - cx * targetScale
        val newTy = height * 0.4f - cy * targetScale
        if (targetScale != scale) {
            scale = targetScale
            scheduleDetails()
        }
        animateScrollTo(newTx, newTy, true)
    }

    fun setHighlights(list: List<Highlight>) {
        highlights = list.groupBy { it.page }
        invalidate()
    }

    fun clearHighlights() { highlights = emptyMap(); invalidate() }

    fun release() {
        closed = true
        generation++
        zoomAnimator?.cancel(); scrollAnimator?.cancel()
        executor.shutdownNow()
        try { executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) {}
        synchronized(renderLock) {
            baseCache.evictAll()
            details.clear()
            try { renderer?.close() } catch (_: Exception) {}
            renderer = null
        }
    }

    private fun layoutPages() {
        if (pageCount == 0 || width == 0) return
        contentW = width.toFloat()
        tops = FloatArray(pageCount)
        heights = FloatArray(pageCount)
        var y = gap
        for (i in 0 until pageCount) {
            val h = contentW * pageH[i] / max(1f, pageW[i])
            tops[i] = y
            heights[i] = h
            y += h + gap
        }
        contentH = y
        clamp()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val oldContentW = contentW
        val anchor = if (oldContentW > 0f && pageCount > 0) currentPage() else 0
        layoutPages()
        baseCache.evictAll(); details.clear(); pending.clear(); generation++
        if (oldContentW > 0f && pageCount > 0) { ty = -(tops[anchor] - gap / 2) * scale; clamp() }
        invalidate()
    }

    private fun clamp() {
        val cw = contentW * scale
        val ch = contentH * scale
        tx = if (cw <= width) (width - cw) / 2f else tx.coerceIn(width - cw, 0f)
        ty = if (ch <= height) 0f else ty.coerceIn(height - ch, 0f)
    }

    private fun zoomAt(target: Float, fx: Float, fy: Float) {
        val ns = target.coerceIn(1f, maxScale)
        if (ns == scale) return
        val ratio = ns / scale
        tx = fx - (fx - tx) * ratio
        ty = fy - (fy - ty) * ratio
        scale = ns
        clamp()
        invalidate()
        reportPage()
    }

    private fun animateZoom(target: Float, fx: Float, fy: Float) {
        zoomAnimator?.cancel()
        val start = scale
        zoomAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { zoomAt(it.animatedValue as Float, fx, fy) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { scheduleDetails() }
            })
            start()
        }
    }

    private fun animateScrollTo(x: Float, y: Float, animate: Boolean) {
        scrollAnimator?.cancel()
        scroller.forceFinished(true)
        val cw = contentW * scale
        val ch = contentH * scale
        val targetX = if (cw <= width) (width - cw) / 2f else x.coerceIn(width - cw, 0f)
        val targetY = if (ch <= height) 0f else y.coerceIn(height - ch, 0f)
        if (!animate) { tx = targetX; ty = targetY; invalidate(); reportPage(); scheduleDetails(); return }
        val sx = tx; val sy = ty
        scrollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                tx = sx + (targetX - sx) * f
                ty = sy + (targetY - sy) * f
                invalidate(); reportPage()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { scheduleDetails() }
            })
            start()
        }
    }

    private fun reportPage() {
        val p = currentPage()
        if (p != lastPage) { lastPage = p; onPageChanged?.invoke(p) }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            tx = scroller.currX.toFloat()
            ty = scroller.currY.toFloat()
            clamp()
            invalidate()
            reportPage()
            if (scroller.isFinished) scheduleDetails()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageCount == 0) return super.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (scroller.isFinished) scheduleDetails()
        }
        return true
    }

    private fun visibleRange(): IntRange {
        if (pageCount == 0) return IntRange.EMPTY
        val top = -ty / scale
        val bottom = (height - ty) / scale
        var first = 0
        var last = pageCount - 1
        var lo = 0; var hi = pageCount - 1
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (tops[mid] <= top) lo = mid else hi = mid - 1 }
        first = lo
        lo = first; hi = pageCount - 1
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (tops[mid] <= bottom) lo = mid else hi = mid - 1 }
        last = lo
        return first..last
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0)
        if (pageCount == 0) return
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)
        val range = visibleRange()
        for (i in range) {
            val l = 0f
            val t = tops[i]
            val r = contentW
            val b = t + heights[i]
            canvas.drawRect(l, t + 2f, r, b + 2f, shadowPaint)
            val base = baseCache.get(i)
            if (base != null && !base.isRecycled) {
                canvas.drawBitmap(base, null, RectF(l, t, r, b), paint)
            } else {
                canvas.drawRect(l, t, r, b, pagePaint)
                canvas.drawText("${i + 1}", (l + r) / 2f, (t + b) / 2f, numberPaint)
                requestBase(i)
            }
            details[i]?.let { d ->
                if (!d.bitmap.isRecycled) canvas.drawBitmap(d.bitmap, null, d.area, paint)
            }
            highlights[i]?.forEach { h ->
                val rr = RectF(h.rect.left * contentW, t + h.rect.top * heights[i], h.rect.right * contentW, t + h.rect.bottom * heights[i])
                canvas.drawRect(rr, if (h.current) hlCurrent else hlPaint)
            }
        }
        canvas.restore()
        if (scale <= 1.02f) {
            val next = range.last + 1
            if (next < pageCount) requestBase(next)
        }
    }

    private fun requestBase(page: Int) {
        if (closed || pending.contains(page) || baseCache.get(page) != null) return
        pending.add(page)
        val gen = generation
        val w = max(1, width)
        try {
            executor.execute {
                if (gen != generation || closed) { post { pending.remove(page) }; return@execute }
                var bmp: Bitmap? = null
                try {
                    synchronized(renderLock) {
                        val r = renderer ?: return@synchronized
                        val ph = max(1, (w * pageH[page] / max(1f, pageW[page])).toInt())
                        val b = Bitmap.createBitmap(w, ph, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        r.openPage(page).use { it.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        bmp = b
                    }
                } catch (_: Throwable) {}
                post {
                    pending.remove(page)
                    if (gen == generation && !closed) {
                        bmp?.let { baseCache.put(page, it) }
                        invalidate()
                    }
                }
            }
        } catch (_: Exception) { pending.remove(page) }
    }

    private val detailRunnable = Runnable { renderDetails() }

    private fun scheduleDetails() {
        removeCallbacks(detailRunnable)
        postDelayed(detailRunnable, 160)
    }

    private fun renderDetails() {
        if (closed || pageCount == 0) return
        val job = ++detailJobGen
        if (scale < 1.08f) {
            details.clear()
            invalidate()
            return
        }
        val s = scale
        val viewLeft = -tx / s
        val viewTop = -ty / s
        val viewRight = (width - tx) / s
        val viewBottom = (height - ty) / s
        val marginX = (viewRight - viewLeft) * 0.25f
        val marginY = (viewBottom - viewTop) * 0.25f
        val range = visibleRange()
        val gen = generation
        val keep = range.toSet()
        details.keys.retainAll(keep)
        for (i in range) {
            val pageRect = RectF(0f, tops[i], contentW, tops[i] + heights[i])
            val area = RectF(max(pageRect.left, viewLeft - marginX), max(pageRect.top, viewTop - marginY), min(pageRect.right, viewRight + marginX), min(pageRect.bottom, viewBottom + marginY))
            if (area.width() <= 1 || area.height() <= 1) continue
            var pxW = (area.width() * s).toInt()
            var pxH = (area.height() * s).toInt()
            val maxPx = 5_500_000
            if (pxW.toLong() * pxH > maxPx) {
                val f = Math.sqrt(maxPx.toDouble() / (pxW.toDouble() * pxH)).toFloat()
                pxW = max(1, (pxW * f).toInt()); pxH = max(1, (pxH * f).toInt())
            }
            val renderScale = pxW / area.width()
            val pw = pageW[i]
            val ph = pageH[i]
            val relLeft = area.left - pageRect.left
            val relTop = area.top - pageRect.top
            val finalW = pxW; val finalH = pxH
            try {
                executor.execute {
                    if (gen != generation || job != detailJobGen || closed) return@execute
                    var out: Detail? = null
                    try {
                        synchronized(renderLock) {
                            val r = renderer ?: return@synchronized
                            val b = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            val k = contentW * renderScale / pw
                            val m = Matrix()
                            m.postScale(k, k)
                            m.postTranslate(-relLeft * renderScale, -relTop * renderScale)
                            r.openPage(i).use { it.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                            out = Detail(b, RectF(area), s)
                        }
                    } catch (_: Throwable) {}
                    post {
                        if (gen == generation && !closed) {
                            out?.let { details[i] = it; invalidate() }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
