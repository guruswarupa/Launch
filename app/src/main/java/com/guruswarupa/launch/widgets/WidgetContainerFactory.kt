package com.guruswarupa.launch.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.ColorStateList
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.SystemWidgetInfo

class WidgetContainerFactory(
    private val context: Context,
    private val dpToPx: (Int) -> Int,
    private val pxToDp: (Int) -> Int,
    private val updateWidgetCustomHeight: (Int, Int) -> Unit,
    private val applyWidgetSizeOptions: (AppWidgetHostView, Int, View, Int, Int?) -> Unit
) {

    @SuppressLint("ClickableViewAccessibility")
    fun createWidgetContainer(
        widgetView: AppWidgetHostView,
        widgetInfo: SystemWidgetInfo,
        appWidgetInfo: AppWidgetProviderInfo
    ): View {
        val appWidgetId = widgetInfo.appWidgetId
        val resizeHandleSizePx = dpToPx(34)
        val resizeHandleInsetPx = dpToPx(6)
        val minHeightPx = dpToPx(120)
        val maxHeightPx = (context.resources.displayMetrics.heightPixels * 0.85f).toInt()

        val containerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
            background = null
            tag = appWidgetId
        }

        widgetView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            widgetInfo.customHeightDp?.let { dpToPx(it) } ?: FrameLayout.LayoutParams.WRAP_CONTENT
        )
        containerLayout.addView(widgetView)

        val resizeHandle = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                resizeHandleSizePx,
                resizeHandleSizePx,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = resizeHandleInsetPx
                bottomMargin = resizeHandleInsetPx
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundResource(R.drawable.drawer_widgets_action_bg)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, android.R.color.white)
            )
            contentDescription = "Resize widget"
            setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7))
            visibility = View.GONE
        }
        containerLayout.addView(resizeHandle)
        val hideResizeHandleRunnable = Runnable { resizeHandle.visibility = View.GONE }

        val showResizeHandle = {
            resizeHandle.visibility = View.VISIBLE
            resizeHandle.bringToFront()
            resizeHandle.removeCallbacks(hideResizeHandleRunnable)
            resizeHandle.postDelayed(hideResizeHandleRunnable, 4000L)
        }

        containerLayout.setOnLongClickListener {
            showResizeHandle()
            true
        }
        widgetView.setOnLongClickListener {
            showResizeHandle()
            true
        }
        val resizeGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showResizeHandle()
                return true
            }
        })
        
        val touchListener = View.OnTouchListener { _, event ->
            resizeGestureDetector.onTouchEvent(event)
            false
        }
        containerLayout.setOnTouchListener(touchListener)
        widgetView.setOnTouchListener(touchListener)

        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var startRawY = 0f
            private var startHeightPx = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        resizeHandle.removeCallbacks(hideResizeHandleRunnable)
                        startRawY = event.rawY
                        startHeightPx = widgetView.height
                            .takeIf { it > 0 }
                            ?: widgetView.measuredHeight.takeIf { it > 0 }
                            ?: dpToPx(widgetInfo.customHeightDp ?: widgetInfo.minHeight.coerceAtLeast(120))
                        containerLayout.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - startRawY).toInt()
                        val targetHeightPx = (startHeightPx + deltaY).coerceIn(minHeightPx, maxHeightPx)
                        val lp = widgetView.layoutParams as FrameLayout.LayoutParams
                        if (lp.height != targetHeightPx) {
                            lp.height = targetHeightPx
                            widgetView.layoutParams = lp
                            val targetHeightDp = pxToDp(targetHeightPx).coerceAtLeast(1)
                            applyWidgetSizeOptions(
                                widgetView,
                                appWidgetId,
                                containerLayout,
                                appWidgetInfo.minHeight,
                                targetHeightDp
                            )
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val finalHeightDp = pxToDp(widgetView.height).coerceAtLeast(1)
                        updateWidgetCustomHeight(appWidgetId, finalHeightDp)
                        containerLayout.parent?.requestDisallowInterceptTouchEvent(false)
                        resizeHandle.postDelayed(hideResizeHandleRunnable, 2500L)
                        return true
                    }
                }
                return false
            }
        })

        val updateOptions = {
            applyWidgetSizeOptions(
                widgetView,
                appWidgetId,
                containerLayout,
                appWidgetInfo.minHeight,
                widgetInfo.customHeightDp
            )
        }
        containerLayout.post(updateOptions)
        containerLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateOptions()
        }

        return containerLayout
    }
}
