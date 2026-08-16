package com.guruswarupa.launch.ui.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.utils.WidgetPreviewManager

class WidgetConfigAdapter(
    private var widgets: MutableList<WidgetConfigurationManager.WidgetInfo>,
    private val previewManager: WidgetPreviewManager,
    private val onWidgetTapped: (WidgetConfigurationManager.WidgetInfo) -> Unit,
    private val onWidgetResized: (WidgetConfigurationManager.WidgetInfo, Int) -> Unit
) : RecyclerView.Adapter<WidgetConfigAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val previewContainer: FrameLayout = itemView.findViewById(R.id.widget_preview_container)
        val previewImage: ImageView = itemView.findViewById(R.id.widget_preview_image)
        val loadingProgress: ProgressBar = itemView.findViewById(R.id.preview_loading_progress)
        val disabledOverlay: View = itemView.findViewById(R.id.disabled_overlay)
        val disabledText: TextView = itemView.findViewById(R.id.disabled_text)
        val resizeBorder: View = itemView.findViewById(R.id.resize_border)
        val resizeHandle: ImageView = itemView.findViewById(R.id.resize_handle)
        val widgetName: TextView = itemView.findViewById(R.id.widget_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_preview_item, parent, false)
        TypographyManager.applyToView(view)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        holder.widgetName.text = widget.name

        applyPreviewHeight(holder, widget)
        setupResizeInteraction(holder, widget)

        if (widget.isProvider) {
            holder.disabledOverlay.visibility = View.VISIBLE
            holder.disabledText.visibility = View.VISIBLE
            holder.disabledText.text = holder.itemView.context.getString(R.string.widget_config_tap_to_add)
            holder.widgetName.alpha = 0.6f
            holder.previewImage.alpha = 1.0f
        } else {
            updateWidgetState(holder, widget.enabled)
            holder.disabledText.text = holder.itemView.context.getString(R.string.widget_config_disabled)
        }

        loadPreviewImage(holder, widget)

        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                val selectedWidget = widgets[currentPos]
                if (!selectedWidget.isProvider) {
                    widgets[currentPos] = selectedWidget.copy(enabled = !selectedWidget.enabled)
                    notifyItemChanged(currentPos)
                }
                onWidgetTapped(selectedWidget)
            }
        }
    }

    override fun getItemCount(): Int = widgets.size

    fun getWidgets(): List<WidgetConfigurationManager.WidgetInfo> = widgets.toList()

    fun updateWidgets(newWidgets: MutableList<WidgetConfigurationManager.WidgetInfo>) {
        widgets = newWidgets
    }

    private fun updateWidgetState(holder: WidgetViewHolder, isEnabled: Boolean) {
        holder.disabledOverlay.visibility = if (isEnabled) View.GONE else View.VISIBLE
        holder.disabledText.visibility = if (isEnabled) View.GONE else View.VISIBLE
        holder.widgetName.alpha = if (isEnabled) 1.0f else 0.5f
        holder.previewImage.alpha = 1.0f
    }

    private fun applyPreviewHeight(holder: WidgetViewHolder, widget: WidgetConfigurationManager.WidgetInfo) {
        val targetHeightDp = widget.customHeightDp?.coerceIn(MIN_PREVIEW_HEIGHT_DP, MAX_PREVIEW_HEIGHT_DP)
            ?: DEFAULT_PREVIEW_HEIGHT_DP
        val targetHeightPx = dpToPx(holder.itemView, targetHeightDp)
        val layoutParams = holder.previewContainer.layoutParams
        if (layoutParams.height != targetHeightPx) {
            layoutParams.height = targetHeightPx
            holder.previewContainer.layoutParams = layoutParams
        }
    }

    private fun setupResizeInteraction(holder: WidgetViewHolder, widget: WidgetConfigurationManager.WidgetInfo) {
        val canResize = widget.isSystemWidget && !widget.isProvider && widget.appWidgetId != null
        holder.resizeBorder.visibility = View.GONE
        holder.resizeHandle.visibility = View.GONE
        holder.itemView.setOnLongClickListener(null)
        holder.resizeHandle.setOnTouchListener(null)

        if (!canResize) return

        val hideResizeUi = Runnable {
            holder.resizeBorder.visibility = View.GONE
            holder.resizeHandle.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            holder.resizeBorder.visibility = View.VISIBLE
            holder.resizeHandle.visibility = View.VISIBLE
            holder.resizeHandle.removeCallbacks(hideResizeUi)
            holder.resizeHandle.postDelayed(hideResizeUi, 4000L)
            true
        }

        holder.resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var startRawY = 0f
            private var startHeightPx = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holder.resizeBorder.visibility = View.VISIBLE
                        holder.resizeHandle.visibility = View.VISIBLE
                        holder.resizeHandle.removeCallbacks(hideResizeUi)
                        startRawY = event.rawY
                        startHeightPx = holder.previewContainer.height
                            .takeIf { it > 0 }
                            ?: dpToPx(holder.itemView, widget.customHeightDp ?: DEFAULT_PREVIEW_HEIGHT_DP)
                        holder.itemView.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - startRawY).toInt()
                        val targetHeightPx = (startHeightPx + deltaY).coerceIn(
                            dpToPx(holder.itemView, MIN_PREVIEW_HEIGHT_DP),
                            dpToPx(holder.itemView, MAX_PREVIEW_HEIGHT_DP)
                        )
                        val layoutParams = holder.previewContainer.layoutParams
                        if (layoutParams.height != targetHeightPx) {
                            layoutParams.height = targetHeightPx
                            holder.previewContainer.layoutParams = layoutParams
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val currentPos = holder.bindingAdapterPosition
                        if (currentPos != RecyclerView.NO_POSITION) {
                            val heightDp = pxToDp(holder.itemView, holder.previewContainer.height)
                                .coerceIn(MIN_PREVIEW_HEIGHT_DP, MAX_PREVIEW_HEIGHT_DP)
                            val updatedWidget = widgets[currentPos].copy(customHeightDp = heightDp)
                            widgets[currentPos] = updatedWidget
                            onWidgetResized(updatedWidget, heightDp)
                        }
                        holder.itemView.parent?.requestDisallowInterceptTouchEvent(false)
                        holder.resizeHandle.postDelayed(hideResizeUi, 2500L)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun loadPreviewImage(holder: WidgetViewHolder, widget: WidgetConfigurationManager.WidgetInfo) {
        holder.previewImage.tag = widget.id
        holder.previewImage.setImageDrawable(null)
        holder.loadingProgress.visibility = View.VISIBLE

        previewManager.generatePreview(widget.id, widget.name) { bitmap ->
            if (holder.previewImage.tag != widget.id) {
                return@generatePreview
            }
            holder.loadingProgress.visibility = View.GONE
            if (bitmap != null && !bitmap.isRecycled) {
                holder.previewImage.setImageBitmap(bitmap)
            } else {
                holder.previewImage.setImageResource(R.drawable.ic_widget_placeholder)
            }
        }
    }

    private fun dpToPx(view: View, dp: Int): Int {
        return (dp * view.resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(view: View, px: Int): Int {
        return (px / view.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val DEFAULT_PREVIEW_HEIGHT_DP = 180
        private const val MIN_PREVIEW_HEIGHT_DP = 140
        private const val MAX_PREVIEW_HEIGHT_DP = 360
    }
}
