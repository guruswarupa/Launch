package com.guruswarupa.launch.apps.common

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.apps.calendar.MonthView
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.withAlpha
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal class WheelView(
    context: Context,
    private val items: List<String>,
    private val textColor: Int,
    private val loop: Boolean,
    private val itemH: Int
) : RecyclerView(context) {

    var onSelected: ((Int) -> Unit)? = null
    private val count = if (loop) items.size * 400 else items.size
    private var current = 0
    private val lm = LinearLayoutManager(context)
    private val snap = LinearSnapHelper()

    val selectedIndex: Int get() = current

    init {
        layoutManager = lm
        setPadding(0, itemH * 2, 0, itemH * 2)
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        snap.attachToRecyclerView(this)
        adapter = object : Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = count
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    textSize = 28f
                    setTextColor(textColor)
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    layoutParams = LayoutParams(MATCH_PARENT, itemH)
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = items[position % items.size]
            }
        }
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { decorate() }
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state == SCROLL_STATE_IDLE) report()
            }
        })
        addOnChildAttachStateChangeListener(object : OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) { post { decorate() } }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }

    fun setIndex(i: Int) {
        val index = i.coerceIn(0, items.size - 1)
        val pos = if (loop) (count / 2 - (count / 2) % items.size) + index else index
        current = index
        lm.scrollToPositionWithOffset(pos, 0)
        post { decorate() }
    }

    private fun decorate() {
        val mid = height / 2f
        for (k in 0 until childCount) {
            val c = getChildAt(k)
            val d = (abs((c.top + c.bottom) / 2f - mid) / itemH).coerceAtMost(2f)
            c.scaleX = 1.12f - 0.22f * d
            c.scaleY = c.scaleX
            c.alpha = 1f - 0.38f * d
            (c as? TextView)?.setTextColor(if (d < 0.5f) textColor else withAlpha(textColor, 255))
        }
    }

    private fun report() {
        val v = snap.findSnapView(lm) ?: return
        val pos = getChildAdapterPosition(v)
        if (pos == NO_POSITION) return
        val i = pos % items.size
        if (i != current) { current = i; onSelected?.invoke(i) } else current = i
    }
}

internal object Pickers {

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun label(c: Context, p: FilesPalette, t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(c).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun buttons(c: Context, p: FilesPalette, d: FilesDialogs, onCancel: () -> Unit, extra: Pair<String, () -> Unit>?, onOk: () -> Unit): LinearLayout {
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 14), 0, 0) }
        if (extra != null) row.addView(label(c, p, extra.first, 15f, p.accent, true).apply {
            setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10)); setOnClickListener { extra.second() }
        })
        row.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(label(c, p, c.getString(R.string.files_cancel), 15f, p.text2, true).apply {
            setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10)); setOnClickListener { onCancel() }
        })
        row.addView(label(c, p, c.getString(R.string.pick_ok), 15f, p.onAccent, true).apply {
            background = d.rounded(p.accent, 22); setPadding(dp(c, 22), dp(c, 10), dp(c, 22), dp(c, 10)); setOnClickListener { onOk() }
        })
        return row
    }

    private fun band(c: Context, p: FilesPalette, d: FilesDialogs, itemH: Int, wheels: List<Pair<View, Float>>): FrameLayout {
        val frame = FrameLayout(c)
        frame.addView(View(c).apply { background = d.rounded(withAlpha(p.accent, 36), 16) },
            FrameLayout.LayoutParams(MATCH_PARENT, itemH, Gravity.CENTER_VERTICAL).apply { setMargins(dp(c, 8), 0, dp(c, 8), 0) })
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER }
        wheels.forEach { (w, weight) -> row.addView(w, LinearLayout.LayoutParams(0, itemH * 5, weight)) }
        frame.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, itemH * 5))
        return frame
    }

    fun time(c: Context, d: FilesDialogs, p: FilesPalette, hour: Int, minute: Int, is24: Boolean, onPick: (Int, Int) -> Unit) {
        val itemH = dp(c, 54)
        val card = d.card()
        card.addView(label(c, p, c.getString(R.string.pick_select_time), 14f, p.text2, true))
        val preview = label(c, p, "", 44f).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
            setPadding(0, dp(c, 8), 0, dp(c, 4))
        }
        card.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hours = if (is24) (0..23).map { String.format("%02d", it) } else (1..12).map { it.toString() }
        val minutes = (0..59).map { String.format("%02d", it) }
        val hourWheel = WheelView(c, hours, p.text, true, itemH)
        val minuteWheel = WheelView(c, minutes, p.text, true, itemH)
        val ampm = DateFormatSymbols.getInstance(Locale.getDefault()).amPmStrings.toList()
        val ampmWheel = WheelView(c, ampm, p.text, false, itemH)
        hourWheel.setIndex(if (is24) hour else (if (hour % 12 == 0) 12 else hour % 12) - 1)
        minuteWheel.setIndex(minute)
        ampmWheel.setIndex(if (hour < 12) 0 else 1)

        fun currentHour(): Int {
            if (is24) return hourWheel.selectedIndex
            val h12 = hourWheel.selectedIndex + 1
            return (h12 % 12) + if (ampmWheel.selectedIndex == 1) 12 else 0
        }
        fun refresh() {
            val h = currentHour()
            val m = minuteWheel.selectedIndex
            preview.text = if (is24) String.format(Locale.getDefault(), "%02d:%02d", h, m)
            else String.format(Locale.getDefault(), "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, ampm[if (h < 12) 0 else 1])
        }
        hourWheel.onSelected = { refresh() }; minuteWheel.onSelected = { refresh() }; ampmWheel.onSelected = { refresh() }
        refresh()

        val wheels = ArrayList<Pair<View, Float>>()
        wheels.add(hourWheel to 1f)
        wheels.add(minuteWheel to 1f)
        if (!is24) wheels.add(ampmWheel to 0.9f)
        card.addView(band(c, p, d, itemH, wheels), LinearLayout.LayoutParams(MATCH_PARENT, itemH * 5).apply { topMargin = dp(c, 6) })

        lateinit var dialog: android.app.Dialog
        card.addView(buttons(c, p, d, { dialog.dismiss() }, null) { dialog.dismiss(); onPick(currentHour(), minuteWheel.selectedIndex) })
        dialog = d.makeDialog(card, false)
        dialog.show()
    }

    fun date(c: Context, d: FilesDialogs, p: FilesPalette, initial: LocalDate, onPick: (LocalDate) -> Unit) {
        var month = YearMonth.from(initial)
        var selected = initial
        var sync: () -> Unit = {}
        val card = d.card()
        val headline = label(c, p, "", 22f, p.text, true)
        card.addView(label(c, p, c.getString(R.string.pick_select_date), 14f, p.text2, true))
        card.addView(headline, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(c, 2) })

        val nav = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 10), 0, 0) }
        val monthTitle = label(c, p, "", 16f, p.text, true).apply { setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 8)) }
        fun arrow(t: String, dir: Int) = label(c, p, t, 22f, p.accent, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(c, 14), dp(c, 4), dp(c, 14), dp(c, 4))
            setOnClickListener { month = month.plusMonths(dir.toLong()); sync() }
        }
        nav.addView(monthTitle, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nav.addView(arrow("‹", -1)); nav.addView(arrow("›", 1))
        card.addView(nav)

        val first = Calendar.getInstance().firstDayOfWeek
        val grid = MonthView(c, p.text, p.text2, p.accent, p.onAccent).apply {
            firstDay = DayOfWeek.of(((first + 5) % 7) + 1)
            this.onPick = { day -> selected = day; if (YearMonth.from(day) != month) month = YearMonth.from(day); sync() }
            onSwipe = { dir -> month = month.plusMonths(dir.toLong()); sync() }
        }
        val itemH = dp(c, 44)
        val months = (1..12).map { java.time.Month.of(it).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) }
        val years = (1970..2100).map { it.toString() }
        val monthWheel = WheelView(c, months, p.text, true, itemH)
        val yearWheel = WheelView(c, years, p.text, false, itemH)
        val wheelPanel = band(c, p, d, itemH, listOf(monthWheel to 1.4f, yearWheel to 1f)).apply { visibility = View.GONE }
        val gridBox = FrameLayout(c)
        gridBox.addView(grid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        card.addView(gridBox, LinearLayout.LayoutParams(MATCH_PARENT, dp(c, 276)).apply { topMargin = dp(c, 4) })
        card.addView(wheelPanel, LinearLayout.LayoutParams(MATCH_PARENT, itemH * 5).apply { topMargin = dp(c, 4) })

        var wheelsOpen = false
        sync = {
            headline.text = selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            monthTitle.text = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + month.year + "  ▾"
            grid.month = month; grid.selected = selected
        }
        sync()
        monthTitle.setOnClickListener {
            wheelsOpen = !wheelsOpen
            gridBox.visibility = if (wheelsOpen) View.GONE else View.VISIBLE
            wheelPanel.visibility = if (wheelsOpen) View.VISIBLE else View.GONE
            if (wheelsOpen) { monthWheel.setIndex(month.monthValue - 1); yearWheel.setIndex((month.year - 1970).coerceIn(0, years.size - 1)) }
        }
        val onWheel = {
            if (wheelsOpen) {
                month = YearMonth.of(1970 + yearWheel.selectedIndex, monthWheel.selectedIndex + 1)
                sync()
            }
        }
        monthWheel.onSelected = { onWheel() }
        yearWheel.onSelected = { onWheel() }

        lateinit var dialog: android.app.Dialog
        card.addView(buttons(c, p, d, { dialog.dismiss() }, c.getString(R.string.pick_today) to {
            selected = LocalDate.now(); month = YearMonth.now(); sync()
            if (wheelsOpen) { monthWheel.setIndex(month.monthValue - 1); yearWheel.setIndex((month.year - 1970).coerceIn(0, years.size - 1)) }
        }) { dialog.dismiss(); onPick(selected) })
        dialog = d.makeDialog(card, false)
        dialog.show()
    }

}
