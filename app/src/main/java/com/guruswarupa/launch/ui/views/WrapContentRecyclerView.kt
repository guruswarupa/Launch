package com.guruswarupa.launch.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class WrapContentRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {

        isNestedScrollingEnabled = false
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)

        val layoutManager = layoutManager
        val adapter = adapter
        if (layoutManager is LinearLayoutManager && adapter != null && adapter.itemCount > 0) {
            val itemCount = adapter.itemCount
            var totalHeight = paddingTop + paddingBottom

            var measuredItems = 0
            for (i in 0 until itemCount) {
                val view = layoutManager.findViewByPosition(i)
                if (view != null) {
                    if (view.measuredHeight == 0) {
                        measureChild(view, widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    }
                    totalHeight += view.measuredHeight
                    measuredItems++
                }
            }

            if (measuredItems in 1 until itemCount) {
                val averageItemHeight = (totalHeight - paddingTop - paddingBottom) / measuredItems
                totalHeight = paddingTop + paddingBottom + (averageItemHeight * itemCount)
            } else if (measuredItems == 0 && itemCount > 0) {

                val estimatedItemHeight = 80
                totalHeight = paddingTop + paddingBottom + (estimatedItemHeight * itemCount)
            }

            if (totalHeight > 0) {
                val heightMode = MeasureSpec.getMode(heightSpec)
                val heightSize = MeasureSpec.getSize(heightSpec)

                val finalHeight = when (heightMode) {
                    MeasureSpec.EXACTLY -> heightSize
                    MeasureSpec.AT_MOST -> minOf(totalHeight, heightSize)
                    else -> totalHeight
                }

                setMeasuredDimension(
                    MeasureSpec.getSize(widthSpec),
                    finalHeight
                )
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        post {
            recalculateHeight()
        }
    }

    private var isRecalculating = false

    private fun recalculateHeight() {

        if (isRecalculating) return

        val layoutManager = layoutManager
        val adapter = adapter
        if (layoutManager is LinearLayoutManager && adapter != null && adapter.itemCount > 0) {
            val itemCount = adapter.itemCount
            val currentHeight = layoutParams.height

            val measuredCount = (0 until itemCount).count {
                layoutManager.findViewByPosition(it) != null
            }

            if (measuredCount >= itemCount * 0.8) {
                isRecalculating = true
                var totalHeight = paddingTop + paddingBottom

                for (i in 0 until itemCount) {
                    val view = layoutManager.findViewByPosition(i)
                    if (view != null && view.height > 0) {
                        val itemLayoutParams = view.layoutParams as? MarginLayoutParams
                        val topMargin = itemLayoutParams?.topMargin ?: 0
                        val bottomMargin = itemLayoutParams?.bottomMargin ?: 0
                        totalHeight += view.height + topMargin + bottomMargin
                    }
                }

                val extraPaddingDp = 32f
                val extraPadding = (extraPaddingDp * resources.displayMetrics.density).toInt()
                totalHeight += extraPadding

                if (totalHeight > 0 && abs(totalHeight - currentHeight) > 10) {
                    layoutParams.height = totalHeight
                    requestLayout()
                }

                isRecalculating = false
                return
            }

            if (currentHeight < 10000) {
                isRecalculating = true
                layoutParams.height = 10000
                requestLayout()

                post {

                    var totalHeight = paddingTop + paddingBottom
                    var totalMeasuredHeight = 0
                    var finalMeasuredCount = 0

                    for (i in 0 until itemCount) {
                        val view = layoutManager.findViewByPosition(i)
                        if (view != null && view.height > 0) {
                            val itemLayoutParams = view.layoutParams as? MarginLayoutParams
                            val topMargin = itemLayoutParams?.topMargin ?: 0
                            val bottomMargin = itemLayoutParams?.bottomMargin ?: 0
                            totalMeasuredHeight += view.height + topMargin + bottomMargin
                            finalMeasuredCount++
                        }
                    }

                    totalHeight += totalMeasuredHeight

                    if (finalMeasuredCount in 1 until itemCount) {
                        val averageHeight = totalMeasuredHeight / finalMeasuredCount
                        totalHeight += averageHeight * (itemCount - finalMeasuredCount)
                    }

                    val extraPaddingDp = 32f
                    val extraPadding = (extraPaddingDp * resources.displayMetrics.density).toInt()
                    totalHeight += extraPadding

                    if (totalHeight > 0) {
                        layoutParams.height = totalHeight
                        requestLayout()
                    }

                    isRecalculating = false
                }
            }
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)

        post {
            recalculateHeight()
        }
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        try {
            super.addChildrenForAccessibility(outChildren)
        } catch (_: IllegalArgumentException) {

        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        try {
            super.onInitializeAccessibilityNodeInfo(info)
        } catch (_: IllegalArgumentException) {
            info.className = RecyclerView::class.java.name
        }
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        return try {
            super.dispatchPopulateAccessibilityEvent(event)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override fun onRequestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
        return try {
            super.onRequestSendAccessibilityEvent(child, event)
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
