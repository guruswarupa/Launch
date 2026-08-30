package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.ui.adapters.WidgetConfigAdapter
import com.guruswarupa.launch.utils.WidgetPreviewManager
import java.util.concurrent.Executors
import com.guruswarupa.launch.R
import com.guruswarupa.launch.handlers.ActivityResultHandler
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

class WidgetConfigurationActivity : AppCompatActivity() {

    private lateinit var widgetConfigManager: WidgetConfigurationManager
    private lateinit var previewManager: WidgetPreviewManager
    private lateinit var widgetManager: WidgetManager
    private var adapter: WidgetConfigAdapter? = null
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private val backgroundExecutor = Executors.newFixedThreadPool(2)
    private lateinit var widgetsRecyclerView: RecyclerView
    private lateinit var widgetSectionDecoration: WidgetSectionDecoration
    private lateinit var widgetsHeader: android.widget.TextView
    private lateinit var emptyStateText: android.widget.TextView

    private var allWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
    private var filteredWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
    private var currentQuery: String = ""

    private val prefs by lazy { getSharedPreferences(prefsName, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val systemBarManager = SystemBarManager(this, prefs)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()

            WindowCompat.getInsetsController(window, window.decorView).let { controller ->
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_widget_configuration)
        applyBackgroundTranslucency()

        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }

        val sharedPreferences = getSharedPreferences(prefsName, MODE_PRIVATE)
        widgetConfigManager = WidgetConfigurationManager(this, sharedPreferences)
        previewManager = WidgetPreviewManager(this)


        widgetManager = WidgetManager(this, android.widget.LinearLayout(this), shouldLoadWidgets = false)

        val wallpaperBackground = findViewById<ImageView>(R.id.wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, null, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()

        widgetsRecyclerView = findViewById(R.id.widgets_recycler_view)
        widgetsHeader = findViewById(R.id.widgets_header)
        emptyStateText = findViewById(R.id.empty_state_text)
        widgetSectionDecoration = WidgetSectionDecoration(this)
        widgetsRecyclerView.addItemDecoration(widgetSectionDecoration)

        val searchInput = findViewById<EditText>(R.id.search_widget_input)

        val configuredFontColor = TypographyManager.getConfiguredFontColor(this)
        if (configuredFontColor != null) {
            searchInput?.setTextColor(configuredFontColor)
            searchInput?.setHintTextColor(configuredFontColor)
        }
        TypographyManager.applyToView(searchInput)


        loadWidgets()

        widgetsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)


        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }

                // If searching, we need to map filtered positions back to the original list
                val movedItem = filteredWidgets.removeAt(fromPos)
                filteredWidgets.add(toPos, movedItem)

                if (currentQuery.isEmpty()) {
                    allWidgets.removeAt(fromPos)
                    allWidgets.add(toPos, movedItem)
                } else {
                    val disabled = allWidgets.filter { !it.enabled }
                    val reorderedEnabled = filteredWidgets.toList()
                    allWidgets.clear()
                    allWidgets.addAll(reorderedEnabled)
                    allWidgets.addAll(disabled)
                }

                adapter?.updateWidgets(filteredWidgets)
                persistWidgetConfiguration()
                adapter?.notifyItemMoved(fromPos, toPos)
                refreshSectionHeaders()
                updateListChrome()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                    viewHolder?.itemView?.scaleX = 1.05f
                    viewHolder?.itemView?.scaleY = 1.05f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(widgetsRecyclerView)

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterWidgets(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun loadWidgets() {
        lifecycleScope.launch {
            val widgets = withContext(Dispatchers.IO) {
                widgetConfigManager.invalidateCache()
                previewManager.clearCache()
                widgetConfigManager.getWidgetConfiguration()
            }
            
            allWidgets = widgets.toMutableList()
            
            if (currentQuery.isEmpty()) {
                filteredWidgets = allWidgets.toMutableList()
            } else {
                filteredWidgets = allWidgets.filter {
                    it.name.contains(currentQuery, ignoreCase = true) ||
                    getWidgetDescription(it).contains(currentQuery, ignoreCase = true)
                }.toMutableList()
            }

            if (adapter == null) {
                adapter = WidgetConfigAdapter(
                    widgets = filteredWidgets,
                    previewManager = previewManager,
                    onWidgetTapped = { widget ->
                        if (widget.isProvider) {
                            addSystemWidgetProvider(widget)
                        } else {
                            updateWidgetState(widget.id, !widget.enabled)
                        }
                    },
                    onWidgetResized = { widget, heightDp ->
                        updateWidgetPreviewHeight(widget, heightDp)
                    }
                )
                widgetsRecyclerView.adapter = adapter
            } else {
                adapter?.updateWidgets(filteredWidgets)
                adapter?.notifyDataSetChanged()
            }

            refreshSectionHeaders()
            updateListChrome()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ActivityResultHandler.REQUEST_PICK_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    widgetManager.handleWidgetPicked(this, data)
                }
                scheduleWidgetReload()
            }
            ActivityResultHandler.REQUEST_CONFIGURE_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    widgetManager.handleWidgetConfigured(appWidgetId?.takeIf { it != -1 })
                } else {
                    val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    widgetManager.handleWidgetConfigurationCanceled(appWidgetId?.takeIf { it != -1 })
                }
                scheduleWidgetReload()
            }
            ActivityResultHandler.REQUEST_BIND_WIDGET -> {
                widgetManager.handleBindRequestResult(this, approved = resultCode == RESULT_OK)
                scheduleWidgetReload()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::widgetManager.isInitialized) {
            widgetManager.onDestroy()
        }
        if (::wallpaperManagerHelper.isInitialized) {
            wallpaperManagerHelper.cleanup()
        }
        if (::previewManager.isInitialized) {
            previewManager.cleanup()
        }
        backgroundExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (::widgetConfigManager.isInitialized) {
            loadWidgets()
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }

    fun updateWidgetState(widgetId: String, enabled: Boolean) {
        val filteredPosition = filteredWidgets.indexOfFirst { it.id == widgetId }
        val originalPosition = allWidgets.indexOfFirst { it.id == widgetId }


        if (filteredPosition >= 0) {
            filteredWidgets[filteredPosition] = filteredWidgets[filteredPosition].copy(enabled = enabled)
        }

        if (originalPosition >= 0) {
            val widget = allWidgets[originalPosition]
            allWidgets[originalPosition] = widget.copy(enabled = enabled)
            
            // If it's a system widget being disabled, we must destroy the widget ID to properly remove it from the system
            if (widget.isSystemWidget && !enabled && widget.appWidgetId != null) {
                widgetManager.removeWidget(widget.appWidgetId)
                // Update the item in list to remove the appWidgetId so it shows as "TAP TO ADD" again
                allWidgets[originalPosition] = allWidgets[originalPosition].copy(appWidgetId = null)
            }
        }

        persistWidgetConfiguration()


        if (currentQuery.isEmpty()) {
            moveWidgetToCorrectSection(widgetId)
        } else {

            filterWidgets(currentQuery)
        }

        val widget = allWidgets.find { it.id == widgetId }
        if (widget != null) {
            val action = if (enabled) "enabled" else "disabled"
            Toast.makeText(this, this.getString(R.string.toast_str, widget.name, action), Toast.LENGTH_SHORT).show()
        }
    }


    private fun moveWidgetToCorrectSection(widgetId: String) {
        val widgetIndex = allWidgets.indexOfFirst { it.id == widgetId }
        if (widgetIndex < 0) return

        val widget = allWidgets[widgetIndex]


        allWidgets.removeAt(widgetIndex)


        val newPosition = calculateWidgetPosition(widget)


        allWidgets.add(newPosition, widget)


        filteredWidgets = allWidgets.toMutableList()


        adapter?.updateWidgets(filteredWidgets)
        adapter?.notifyDataSetChanged()


        refreshSectionHeaders()
        updateListChrome()
        persistWidgetConfiguration()
    }


    private fun calculateWidgetPosition(widget: WidgetConfigurationManager.WidgetInfo): Int {
        return when {
            widget.enabled -> {
                // When enabled, always place at the end of the enabled section
                val lastEnabledIndex = allWidgets.indexOfLast { it.enabled }
                if (lastEnabledIndex >= 0) {
                    lastEnabledIndex + 1
                } else {
                    0
                }
            }

            else -> {
                // For all disabled widgets, put them at the very end
                allWidgets.size
            }
        }
    }

    private fun updateWidgetPreviewHeight(widget: WidgetConfigurationManager.WidgetInfo, heightDp: Int) {
        val appWidgetId = widget.appWidgetId ?: return

        val filteredPosition = filteredWidgets.indexOfFirst { it.id == widget.id }
        if (filteredPosition >= 0) {
            filteredWidgets[filteredPosition] = filteredWidgets[filteredPosition].copy(customHeightDp = heightDp)
            adapter?.notifyItemChanged(filteredPosition)
        }

        val originalPosition = allWidgets.indexOfFirst { it.id == widget.id }
        if (originalPosition >= 0) {
            allWidgets[originalPosition] = allWidgets[originalPosition].copy(customHeightDp = heightDp)
        }

        widgetManager.updateWidgetCustomHeight(appWidgetId, heightDp)
        persistWidgetConfiguration()
    }

    fun addSystemWidgetProvider(widget: WidgetConfigurationManager.WidgetInfo) {

        val alreadyExists = allWidgets.any {
            it.providerPackage == widget.providerPackage &&
            it.providerClass == widget.providerClass &&
            it.appWidgetId != null
        }

        if (alreadyExists) {
            Toast.makeText(this, this.getString(R.string.toast_is_already_added, widget.name), Toast.LENGTH_SHORT).show()
            return
        }

        val pkg = widget.providerPackage ?: return
        val cls = widget.providerClass ?: return
        widgetManager.bindProvider(this, pkg, cls, ActivityResultHandler.REQUEST_BIND_WIDGET)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterWidgets(query: String) {
        currentQuery = query.trim()
        filteredWidgets = if (currentQuery.isEmpty()) {
            allWidgets.toMutableList()
        } else {
            allWidgets.filter {
                it.name.contains(currentQuery, ignoreCase = true) ||
                getWidgetDescription(it).contains(currentQuery, ignoreCase = true)
            }.toMutableList()
        }

        adapter?.updateWidgets(filteredWidgets)
        adapter?.notifyDataSetChanged()

        refreshSectionHeaders()
        updateListChrome()
    }

    private fun persistWidgetConfiguration() {
        widgetConfigManager.saveWidgetOrder(allWidgets)
        notifyWidgetConfigurationChanged()
    }

    fun notifyWidgetConfigurationChanged() {
        setResult(RESULT_OK)
    }

    private fun refreshSectionHeaders() {
        val sections = mutableListOf<WidgetSectionDecoration.SectionInfo>()
        val seenCategories = mutableSetOf<WidgetSectionCategory>()

        filteredWidgets.forEachIndexed { index, widget ->
            val category = when {
                widget.enabled -> WidgetSectionCategory.ENABLED
                else -> WidgetSectionCategory.DISABLED
            }

            if (seenCategories.add(category)) {
                sections.add(WidgetSectionDecoration.SectionInfo(index, getString(category.titleRes)))
            }
        }

        widgetSectionDecoration.updateSections(sections)
        widgetsRecyclerView.invalidateItemDecorations()
    }

    private fun updateListChrome() {
        val totalCount = allWidgets.size
        val filteredCount = filteredWidgets.size

        widgetsHeader.text = if (currentQuery.isBlank()) {
            "$totalCount widgets"
        } else {
            "$filteredCount matches"
        }

        val isEmpty = filteredWidgets.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        widgetsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            emptyStateText.text = if (currentQuery.isBlank()) {
                "No widgets available yet."
            } else {
                "No widgets match \"$currentQuery\"."
            }
        }
    }

    private fun scheduleWidgetReload() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            loadWidgets()
        }, 350)
    }

    private fun getWidgetDescription(widget: WidgetConfigurationManager.WidgetInfo): String {
        if (widget.isSystemWidget) return "System provided widget"

        return when (widget.id) {
            "calculator_widget_container" -> "Perform calculations and unit conversions"
            "compass_widget_container" -> "Digital compass with direction tracking"
            "calendar_events_widget_container" -> "Upcoming calendar events and reminders"
            "countdown_widget_container" -> "Countdown timers for important events"
            "dns_widget_container" -> "Switch between DNS providers for privacy and ad-blocking"
            "note_widget_container" -> "Create and manage quick notes"
            "physical_activity_widget_container" -> "Track steps and physical activity"
            "pressure_widget_container" -> "Atmospheric pressure monitoring"
            "temperature_widget_container" -> "Temperature monitoring and alerts"
            "weather_forecast_widget_container" -> "Multi-day outlook, hourly forecast, and alert summary"
            "noise_decibel_widget_container" -> "Sound level measurement in decibels"
            "workout_widget_container" -> "Workout tracking and fitness metrics"
            "todo_recycler_view" -> "Task manager"
            "finance_widget" -> "Financial tracking and budget monitoring"
            "weekly_usage_widget" -> "Weekly app usage statistics"
            "network_stats_widget_container" -> "Network connection and data usage"
            "device_info_widget_container" -> "Device information and system stats"
            else -> "System provided widget"
        }
    }

    private enum class WidgetSectionCategory(@StringRes val titleRes: Int) {
        ENABLED(R.string.widget_section_enabled),
        DISABLED(R.string.widget_section_custom_disabled)
    }

    private class WidgetSectionDecoration(private val context: Context) : RecyclerView.ItemDecoration() {
        data class SectionInfo(val position: Int, val title: String)

        private val headerHeightPx = (context.resources.displayMetrics.density * 32f).toInt().coerceAtLeast(1)
        private val horizontalMarginPx = (context.resources.displayMetrics.density * 4f).toInt()
        private val backgroundPaint = Paint().apply {
            color = Color.TRANSPARENT
        }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemeManager.color(context, R.attr.appAccent)
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                13f,
                context.resources.displayMetrics
            )
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }

        private val sections = mutableListOf<SectionInfo>()
        private val headerPositions = mutableSetOf<Int>()

        fun updateSections(newSections: List<SectionInfo>) {
            sections.clear()
            sections.addAll(newSections.sortedBy { it.position })
            headerPositions.clear()
            headerPositions.addAll(sections.map { it.position })
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            if (headerPositions.contains(position)) {
                outRect.top = headerHeightPx
            }
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            sections.forEach { section ->
                val view = parent.layoutManager?.findViewByPosition(section.position) ?: return@forEach
                val top = view.top - headerHeightPx
                val left = parent.paddingLeft.toFloat()
                val right = (parent.width - parent.paddingRight).toFloat().coerceAtLeast(left)
                val bottom = top + headerHeightPx

                canvas.drawRect(left, top.toFloat(), right, bottom.toFloat(), backgroundPaint)
                val textBaseline = top + headerHeightPx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(
                    section.title,
                    left + horizontalMarginPx,
                    textBaseline,
                    textPaint
                )
            }
        }
    }
}
