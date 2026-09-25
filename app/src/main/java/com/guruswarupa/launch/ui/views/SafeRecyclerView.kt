package com.guruswarupa.launch.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.recyclerview.widget.RecyclerView

class SafeRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

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
