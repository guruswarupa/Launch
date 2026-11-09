package com.guruswarupa.launch.apps.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import com.guruswarupa.launch.apps.common.Pickers
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.calendar.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

enum class CalMode { YEAR, MONTH, WEEK, DAY, AGENDA }

class CalendarActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var monthView: MonthView
    private lateinit var yearView: YearView
    private lateinit var timeline: TimelineView
    private lateinit var title: TextView
    private lateinit var agenda: LinearLayout
    private lateinit var permissionBox: LinearLayout
    private lateinit var monthBody: LinearLayout
    private lateinit var yearScroll: ScrollView
    private lateinit var timeBody: LinearLayout
    private lateinit var timeScroll: ScrollView
    private lateinit var dayHeader: LinearLayout
    private lateinit var allDayStrip: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var fab: ImageView

    private val prefs by lazy { getSharedPreferences("calendar_prefs", MODE_PRIVATE) }
    private var mode = CalMode.MONTH
    private var selected: LocalDate = LocalDate.now()
    private var showWeeks = false
    private var hidden: Set<Long> = emptySet()
    private var firstDay: DayOfWeek = DayOfWeek.MONDAY
    private var events: List<CalEvent> = emptyList()
    private var loadedFrom: LocalDate? = null
    private var loadedTo: LocalDate? = null
    private var token = 0
    private var asked = false
    private var bottomInset = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh(true) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun txt(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun granted(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        p = FilesPalette(this)
        dialogs = FilesDialogs(this, p)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = p.isLight
            isAppearanceLightNavigationBars = p.isLight
        }
        firstDay = DayOfWeek.of(((Calendar.getInstance().firstDayOfWeek + 5) % 7) + 1)
        showWeeks = prefs.getBoolean("weeks", false)
        hidden = prefs.getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        mode = runCatching { CalMode.valueOf(prefs.getString("mode", "MONTH")!!) }.getOrDefault(CalMode.MONTH)
        savedInstanceState?.let {
            selected = LocalDate.parse(it.getString("selected", selected.toString()))
            mode = runCatching { CalMode.valueOf(it.getString("mode", mode.name)!!) }.getOrDefault(mode)
        }
        setContentView(buildUi())
        refresh(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected", selected.toString()); outState.putString("mode", mode.name)
    }

    override fun onResume() {
        super.onResume()
        if (::agenda.isInitialized) refresh(true)
    }

    private fun iconBtn(res: Int, desc: Int, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res); setColorFilter(p.text); contentDescription = getString(desc)
        val pad = dp(10); setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun modeLabel(m: CalMode) = getString(when (m) {
        CalMode.YEAR -> R.string.cal_year; CalMode.MONTH -> R.string.cal_month; CalMode.WEEK -> R.string.cal_week
        CalMode.DAY -> R.string.cal_day; CalMode.AGENDA -> R.string.cal_agenda
    })

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(2)) }
        bar.addView(iconBtn(R.drawable.ic_f_back, R.string.files_back) { finish() })
        title = txt("", 20f, bold = true).apply {
            setPadding(dp(6), 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { jumpToDate() }
        }
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(iconBtn(R.drawable.ic_back, R.string.cal_previous) { shift(-1) })
        bar.addView(iconBtn(R.drawable.ic_arrow_right, R.string.cal_next) { shift(1) })
        bar.addView(iconBtn(R.drawable.ic_f_more, R.string.cal_more_options) { showMenu() })
        column.addView(bar)

        modeRow = LinearLayout(this).apply { setPadding(dp(12), dp(4), dp(12), dp(8)) }
        CalMode.values().forEach { m ->
            modeRow.addView(txt(modeLabel(m), 12.5f, p.text2, true).apply {
                gravity = Gravity.CENTER; tag = m; setPadding(0, dp(7), 0, dp(7)); maxLines = 1
                setOnClickListener { setMode(m) }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        column.addView(modeRow)

        permissionBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28)); visibility = View.GONE
        }
        permissionBox.addView(ImageView(this).apply { setImageResource(R.drawable.ic_glyph_calendar); setColorFilter(p.accent) }, LinearLayout.LayoutParams(dp(72), dp(72)))
        permissionBox.addView(txt(getString(R.string.cal_permission_title), 20f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(6)) })
        permissionBox.addView(txt(getString(R.string.cal_permission_message), 14f, p.text2).apply { gravity = Gravity.CENTER })
        permissionBox.addView(txt(getString(R.string.files_allow_access), 16f, p.onAccent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 26); setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener { requestPermissions() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(20) })
        column.addView(permissionBox, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Year
        yearView = YearView(this, p.text, p.text2, p.accent, p.onAccent).apply {
            firstDay = this@CalendarActivity.firstDay
            onDay = { d -> this@CalendarActivity.selected = d; mode = CalMode.DAY; refresh() }
            onMonth = { m -> this@CalendarActivity.selected = m.atDay(1).let { f -> if (YearMonth.from(LocalDate.now()) == m) LocalDate.now() else f }; mode = CalMode.MONTH; refresh() }
            onSwipe = { shift(it) }
        }
        yearScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(yearView) }

        // Month + day list
        monthBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        monthView = MonthView(this, p.text, p.text2, p.accent, p.onAccent).apply {
            firstDay = this@CalendarActivity.firstDay
            chips = true
            onPick = { d -> this@CalendarActivity.selected = d; refresh() }
            onSwipe = { shift(it) }
        }
        monthBody.addView(monthView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 3f).apply { setMargins(dp(6), 0, dp(6), 0) })
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        agenda = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(110)) }
        scroll.addView(agenda)
        monthBody.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2f))

        // Week / day timeline
        timeBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dayHeader = LinearLayout(this).apply { setPadding(0, 0, dp(4), 0) }
        allDayStrip = LinearLayout(this).apply { setPadding(0, 0, dp(4), dp(4)) }
        timeBody.addView(dayHeader, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        timeBody.addView(allDayStrip, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        timeline = TimelineView(this, p.text, p.text2, p.accent, DateFormat.is24HourFormat(this)).apply {
            onEvent = { showEvent(it) }
            onEmpty = { d, m -> editEvent(newDraft(d, m)) }
            onSwipe = { shift(it) }
        }
        timeScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(timeline) }
        timeBody.addView(timeScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val content = FrameLayout(this)
        content.addView(yearScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(monthBody, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(timeBody, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        content.tag = "content"

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        fab = ImageView(this).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(p.onAccent)
            val pd = dp(16); setPadding(pd, pd, pd, pd)
            background = dialogs.rounded(p.accent, 28)
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.cal_new_event)
            setOnClickListener { editEvent(newDraft(selected, (LocalTime.now().hour + 1).coerceAtMost(23) * 60)) }
        }
        frame.addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomInset = b.bottom
            column.setPadding(0, b.top, 0, 0)
            (fab.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, dp(20), b.bottom + dp(20))
            agenda.setPadding(dp(14), dp(10), dp(14), b.bottom + dp(110))
            yearView.setPadding(0, 0, 0, b.bottom + dp(90))
            timeline.setPadding(0, 0, 0, 0)
            insets
        }
        return frame
    }

    // ---------- navigation ----------

    private fun setMode(m: CalMode) {
        mode = m
        prefs.edit().putString("mode", m.name).apply()
        refresh()
    }

    private fun shift(dir: Int) {
        selected = when (mode) {
            CalMode.YEAR -> selected.plusYears(dir.toLong())
            CalMode.MONTH -> selected.plusMonths(dir.toLong())
            CalMode.WEEK -> selected.plusWeeks(dir.toLong())
            CalMode.DAY -> selected.plusDays(dir.toLong())
            CalMode.AGENDA -> selected
        }
        refresh()
    }

    private fun jumpToDate() {
        Pickers.date(this, dialogs, p, selected) { d -> selected = d; refresh() }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else { asked = true; permissionLauncher.launch(perms) }
    }

    private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(firstDay))

    private fun neededRange(): Pair<LocalDate, LocalDate> = when (mode) {
        CalMode.YEAR -> LocalDate.of(selected.year, 1, 1) to LocalDate.of(selected.year + 1, 1, 1)
        CalMode.MONTH -> YearMonth.from(selected).let { it.atDay(1).minusDays(7) to it.atEndOfMonth().plusDays(8) }
        CalMode.WEEK -> weekStart(selected).let { it to it.plusDays(7) }
        CalMode.DAY -> selected to selected.plusDays(1)
        CalMode.AGENDA -> LocalDate.now() to LocalDate.now().plusDays(61)
    }

    private fun refresh(force: Boolean = false) {
        applyUi()
        if (!granted(Manifest.permission.READ_CALENDAR)) return
        val (from, to) = neededRange()
        val lf = loadedFrom; val lt = loadedTo
        if (!force && lf != null && lt != null && !from.isBefore(lf) && !to.isAfter(lt)) { render(); return }
        val zone = ZoneId.systemDefault()
        val mine = ++token
        Thread {
            val list = CalendarRepo.events(contentResolver, from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli())
            runOnUiThread {
                if (mine != token) return@runOnUiThread
                events = list; loadedFrom = from; loadedTo = to
                render()
            }
        }.start()
    }

    // Phones often carry the same holiday in two subscribed calendars; show it once.
    private fun visibleEvents() = events.filter { it.calendarId !in hidden }
        .distinctBy { listOf(it.title.trim().lowercase(), it.begin, it.end, it.allDay) }

    // ---------- UI state ----------

    private fun applyUi() {
        val ok = granted(Manifest.permission.READ_CALENDAR)
        permissionBox.visibility = if (ok) View.GONE else View.VISIBLE
        modeRow.visibility = if (ok) View.VISIBLE else View.GONE
        fab.visibility = if (ok) View.VISIBLE else View.GONE
        for (i in 0 until modeRow.childCount) {
            val t = modeRow.getChildAt(i) as TextView
            val on = t.tag == mode
            t.background = if (on) dialogs.rounded(withAlpha(p.accent, 50), 16) else null
            t.setTextColor(if (on) p.accent else p.text2)
        }
        val content = (yearScroll.parent as View)
        content.visibility = if (ok) View.VISIBLE else View.GONE
        yearScroll.visibility = if (mode == CalMode.YEAR) View.VISIBLE else View.GONE
        monthBody.visibility = if (mode == CalMode.MONTH || mode == CalMode.AGENDA) View.VISIBLE else View.GONE
        timeBody.visibility = if (mode == CalMode.WEEK || mode == CalMode.DAY) View.VISIBLE else View.GONE
        monthView.visibility = if (mode == CalMode.MONTH) View.VISIBLE else View.GONE
        title.text = when (mode) {
            CalMode.YEAR -> selected.year.toString()
            CalMode.MONTH -> selected.format(pattern("LLLLyyyy")).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            CalMode.WEEK -> weekStart(selected).let { s -> val e = s.plusDays(6); s.format(pattern("MMMd")) + " – " + e.format(pattern("MMMd")) }
            CalMode.DAY -> selected.format(pattern("EEEMMMd")) + if (selected.year != LocalDate.now().year) " " + selected.year else ""
            CalMode.AGENDA -> getString(R.string.cal_upcoming)
        }
    }

    private fun render() {
        val list = visibleEvents()
        when (mode) {
            CalMode.YEAR -> {
                yearView.year = selected.year; yearView.selected = selected
                val busy = HashSet<LocalDate>()
                list.forEach { e -> spread(e) { busy.add(it) } }
                yearView.busy = busy
            }
            CalMode.MONTH, CalMode.AGENDA -> {
                monthView.showWeeks = showWeeks
                monthView.month = YearMonth.from(selected); monthView.selected = selected
                val map = HashMap<LocalDate, MutableList<Int>>()
                list.forEach { e -> spread(e) { map.getOrPut(it) { ArrayList() }.add(e.color) } }
                monthView.dots = map
                val titles = HashMap<LocalDate, MutableList<Pair<String, Int>>>()
                list.sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.begin }).forEach { e -> spread(e) { titles.getOrPut(it) { ArrayList() }.add(e.title to e.color) } }
                monthView.items = titles
                renderAgenda(list)
            }
            CalMode.WEEK, CalMode.DAY -> renderTimeline(list)
        }
    }

    private fun spread(e: CalEvent, f: (LocalDate) -> Unit) {
        var d = e.startDate
        var guard = 0
        while (!d.isAfter(e.endDate) && guard++ < 62) { f(d); d = d.plusDays(1) }
    }

    private fun renderTimeline(list: List<CalEvent>) {
        val days = if (mode == CalMode.DAY) listOf(selected) else weekStart(selected).let { s -> (0L..6L).map { s.plusDays(it) } }
        val today = LocalDate.now()
        val gutter = timeline.gutter.toInt()
        dayHeader.removeAllViews()
        val corner = txt(if (mode == CalMode.WEEK) getString(R.string.cal_week_short, days[0].get(java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())) else "", 10.5f, p.text2, true)
        corner.gravity = Gravity.CENTER
        dayHeader.addView(corner, LinearLayout.LayoutParams(gutter, MATCH_PARENT))
        days.forEach { day ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4), 0, dp(6))
                setOnClickListener { selected = day; if (mode == CalMode.WEEK) mode = CalMode.DAY; refresh() }
            }
            cell.addView(txt(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), 11.5f, if (day == today) p.accent else p.text2, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            val isSel = day == selected && mode == CalMode.WEEK
            cell.addView(txt(day.dayOfMonth.toString(), 15f, if (day == today) p.onAccent else p.text, day == today).apply {
                gravity = Gravity.CENTER
                background = when { day == today -> dialogs.rounded(p.accent, 16); isSel -> dialogs.rounded(withAlpha(p.accent, 50), 16); else -> null }
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { topMargin = dp(2) })
            dayHeader.addView(cell, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        allDayStrip.removeAllViews()
        val hasAllDay = days.any { d -> list.any { it.allDay && it.occursOn(d) } }
        allDayStrip.visibility = if (hasAllDay) View.VISIBLE else View.GONE
        if (hasAllDay) {
            allDayStrip.addView(View(this), LinearLayout.LayoutParams(gutter, 1))
            days.forEach { day ->
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val mine = list.filter { it.allDay && it.occursOn(day) }
                mine.take(3).forEach { e ->
                    col.addView(txt(e.title.ifBlank { getString(R.string.cal_untitled) }, 10.5f, p.text, true).apply {
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                        background = dialogs.rounded(withAlpha(e.color, 90), 6); setPadding(dp(4), dp(2), dp(4), dp(2))
                        setOnClickListener { showEvent(e) }
                    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
                if (mine.size > 3) col.addView(txt(getString(R.string.cal_more_count, mine.size - 3), 10f, p.text2))
                allDayStrip.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
        }
        timeline.days = days
        timeline.events = list
        timeScroll.post { timeScroll.scrollTo(0, timeline.suggestedScroll()) }
    }

    private fun timeText(e: CalEvent): String {
        if (e.allDay) return getString(R.string.cal_all_day)
        val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(e.begin).atZone(zone).format(fmt) + " – " + Instant.ofEpochMilli(e.end).atZone(zone).format(fmt)
    }

    private fun renderAgenda(list: List<CalEvent>) {
        agenda.removeAllViews()
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        if (mode == CalMode.MONTH) {
            agenda.addView(txt(selected.format(fmt), 14f, p.text2, true).apply { setPadding(dp(6), 0, 0, dp(8)) })
            val day = list.filter { it.occursOn(selected) }
            if (day.isEmpty()) agenda.addView(txt(getString(R.string.cal_no_events), 14f, p.text2).apply { setPadding(dp(6), dp(6), 0, 0) })
            day.forEach { agenda.addView(eventCard(it), cardLp()) }
            return
        }
        val today = LocalDate.now()
        var any = false
        var d = today
        while (!d.isAfter(today.plusDays(60))) {
            val day = list.filter { it.occursOn(d) }
            if (day.isNotEmpty()) {
                any = true
                agenda.addView(txt(d.format(fmt), 13.5f, if (d == today) p.accent else p.text2, true).apply { setPadding(dp(6), dp(10), 0, dp(6)) })
                day.forEach { agenda.addView(eventCard(it), cardLp()) }
            }
            d = d.plusDays(1)
        }
        if (!any) agenda.addView(txt(getString(R.string.cal_no_upcoming), 14f, p.text2).apply { setPadding(dp(6), dp(6), 0, 0) })
    }

    private fun cardLp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }

    private fun eventCard(e: CalEvent): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = dialogs.rounded(p.card, 18)
            setPadding(0, dp(12), dp(14), dp(12))
            setOnClickListener { showEvent(e) }
        }
        row.addView(View(this).apply { background = dialogs.rounded(e.color, 3) }, LinearLayout.LayoutParams(dp(5), dp(38)).apply { setMargins(dp(12), 0, dp(12), 0) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(txt(e.title.ifBlank { getString(R.string.cal_untitled) }, 15.5f, bold = true).apply { maxLines = 2 })
        texts.addView(txt(listOf(timeText(e), e.location.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), 12.5f, p.text2).apply { maxLines = 1 })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    // ---------- menu, search, calendars ----------

    private fun showMenu() {
        dialogs.sheet(null, listOf(
            SheetItem(R.drawable.ic_f_search, getString(R.string.cal_search)) { showSearch() },
            SheetItem(R.drawable.ic_glyph_calendar, getString(R.string.cal_go_to_date)) { jumpToDate() },
            SheetItem(R.drawable.ic_f_recent, getString(R.string.cal_today)) { selected = LocalDate.now(); refresh() },
            SheetItem(R.drawable.ic_glyph_calendar, getString(R.string.cal_calendars)) { showCalendars() },
            SheetItem(R.drawable.ic_f_info, getString(R.string.cal_week_numbers), checked = showWeeks) {
                showWeeks = !showWeeks; prefs.edit().putBoolean("weeks", showWeeks).apply(); render()
            }
        ))
    }

    private fun showCalendars() {
        Thread {
            val cals = CalendarRepo.calendars(contentResolver)
            runOnUiThread {
                if (cals.isEmpty()) return@runOnUiThread
                dialogs.sheet(getString(R.string.cal_calendars), cals.map { cal ->
                    SheetItem(R.drawable.ic_glyph_calendar, cal.name.ifBlank { getString(R.string.cal_untitled) }, checked = cal.id !in hidden) {
                        hidden = if (cal.id in hidden) hidden - cal.id else hidden + cal.id
                        prefs.edit().putStringSet("hidden", hidden.map { it.toString() }.toSet()).apply()
                        render(); showCalendars()
                    }
                })
            }
        }.start()
    }

    private fun showSearch() {
        val c = dialogs.card()
        c.addView(txt(getString(R.string.cal_search), 19f, bold = true))
        val input = EditText(this).apply {
            hint = getString(R.string.cal_search_hint); setHintTextColor(p.text2); setTextColor(p.text); textSize = 16f; setSingleLine()
            background = dialogs.rounded(withAlpha(p.text, 24), 14); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        c.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(results) }
        c.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, dp(300)).apply { topMargin = dp(10) })
        val dialog = dialogs.makeDialog(c, false)
        var mine = 0
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s.toString().trim()
                val id = ++mine
                results.removeAllViews()
                if (q.length < 2) return
                Thread {
                    val found = CalendarRepo.search(contentResolver, q).filter { it.calendarId !in hidden }
                    runOnUiThread {
                        if (id != mine) return@runOnUiThread
                        results.removeAllViews()
                        if (found.isEmpty()) results.addView(txt(getString(R.string.cal_no_results), 14f, p.text2).apply { setPadding(dp(4), dp(8), 0, 0) })
                        found.forEach { e ->
                            val row = LinearLayout(this@CalendarActivity).apply {
                                gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9))
                                setOnClickListener { dialog.dismiss(); selected = e.startDate; mode = CalMode.DAY; refresh() }
                            }
                            row.addView(View(this@CalendarActivity).apply { background = dialogs.rounded(e.color, 3) }, LinearLayout.LayoutParams(dp(5), dp(34)).apply { marginEnd = dp(12) })
                            val t = LinearLayout(this@CalendarActivity).apply { orientation = LinearLayout.VERTICAL }
                            t.addView(txt(e.title.ifBlank { getString(R.string.cal_untitled) }, 15f, bold = true).apply { maxLines = 1 })
                            t.addView(txt(e.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + if (e.allDay) "" else " · " + timeText(e), 12f, p.text2).apply { maxLines = 1 })
                            row.addView(t)
                            results.addView(row)
                        }
                    }
                }.start()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
        })
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    // ---------- event details ----------

    private fun repeatLabel(r: String?) = when (r) {
        null -> getString(R.string.cal_repeat_none)
        "FREQ=DAILY" -> getString(R.string.cal_repeat_daily)
        "FREQ=WEEKLY" -> getString(R.string.cal_repeat_weekly)
        "FREQ=MONTHLY" -> getString(R.string.cal_repeat_monthly)
        "FREQ=YEARLY" -> getString(R.string.cal_repeat_yearly)
        else -> getString(R.string.cal_repeat_custom)
    }

    private fun reminderLabel(m: Int?) = when (m) {
        null -> getString(R.string.cal_remind_none)
        0 -> getString(R.string.cal_remind_0)
        10 -> getString(R.string.cal_remind_10)
        30 -> getString(R.string.cal_remind_30)
        60 -> getString(R.string.cal_remind_60)
        1440 -> getString(R.string.cal_remind_1440)
        else -> getString(R.string.cal_remind_custom, m)
    }

    private fun showEvent(e: CalEvent) {
        Thread {
            val draft = CalendarRepo.load(contentResolver, e.id)
            val calName = CalendarRepo.calendarName(contentResolver, e.calendarId)
            runOnUiThread { showEventDialog(e, draft, calName) }
        }.start()
    }

    private fun showEventDialog(e: CalEvent, draft: CalendarRepo.Draft?, calName: String?) {
        val writable = granted(Manifest.permission.WRITE_CALENDAR) && draft != null
        val c = dialogs.card()
        lateinit var dialog: android.app.Dialog
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(this).apply { background = dialogs.rounded(e.color, 3) }, LinearLayout.LayoutParams(dp(6), dp(44)).apply { marginEnd = dp(12) })
        head.addView(txt(e.title.ifBlank { getString(R.string.cal_untitled) }, 20f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        c.addView(head)
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val zone = ZoneId.systemDefault()
        val whenText = when {
            e.allDay && e.startDate == e.endDate -> e.startDate.format(dateFmt) + "\n" + getString(R.string.cal_all_day)
            e.allDay -> e.startDate.format(dateFmt) + " –\n" + e.endDate.format(dateFmt)
            e.startDate == e.endDate -> e.startDate.format(dateFmt) + "\n" + timeText(e)
            else -> Instant.ofEpochMilli(e.begin).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)) + " –\n" +
                Instant.ofEpochMilli(e.end).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        }
        fun row(label: String, value: String) {
            if (value.isBlank()) return
            c.addView(txt(label, 11.5f, p.text2, true).apply { setPadding(0, dp(12), 0, dp(2)) })
            c.addView(txt(value, 15f))
        }
        row(getString(R.string.cal_when), whenText)
        row(getString(R.string.cal_location_label), e.location.orEmpty())
        row(getString(R.string.cal_description_label), draft?.description.orEmpty())
        if (draft?.rrule != null) row(getString(R.string.cal_repeat), repeatLabel(draft.rrule))
        if (draft?.reminderMin != null) row(getString(R.string.cal_reminder), reminderLabel(draft.reminderMin))
        row(getString(R.string.cal_calendar), calName.orEmpty())

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; setPadding(0, dp(18), 0, 0) }
        if (writable) {
            buttons.addView(txt(getString(R.string.files_delete), 15f, p.error, true).apply {
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener {
                    dialog.dismiss()
                    val msg = if (draft?.rrule != null) getString(R.string.cal_delete_series_message) else getString(R.string.cal_delete_message)
                    dialogs.confirm(e.title.ifBlank { getString(R.string.cal_untitled) }, msg, getString(R.string.files_delete), true) {
                        Thread { CalendarRepo.delete(contentResolver, e.id); runOnUiThread { refresh(true) } }.start()
                    }
                }
            })
        }
        buttons.addView(txt(getString(R.string.cal_open_in_app_short), 15f, p.text2, true).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                dialog.dismiss()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/events/${e.id}"))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end))
                } catch (_: Exception) {}
            }
        })
        if (writable) {
            buttons.addView(txt(getString(R.string.cal_edit), 15f, p.onAccent, true).apply {
                background = dialogs.rounded(p.accent, 22); setPadding(dp(20), dp(10), dp(20), dp(10))
                setOnClickListener { dialog.dismiss(); editEvent(draft!!) }
            })
        }
        c.addView(buttons)
        dialog = dialogs.makeDialog(ScrollView(this).apply { addView(c) }, false)
        dialog.show()
    }

    // ---------- editor ----------

    private fun newDraft(date: LocalDate, startMin: Int): CalendarRepo.Draft {
        val s = startMin.coerceIn(0, 23 * 60)
        val e = (s + 60).coerceAtMost(23 * 60 + 59)
        return CalendarRepo.Draft(null, 0L, "", "", "", date, s, date, e, false, null, 10)
    }

    private fun editEvent(draft: CalendarRepo.Draft) {
        if (!granted(Manifest.permission.WRITE_CALENDAR)) { requestPermissions(); return }
        val writable = CalendarRepo.calendars(contentResolver).filter { it.writable }
        if (writable.isEmpty()) { android.widget.Toast.makeText(this, getString(R.string.cal_no_calendar), android.widget.Toast.LENGTH_LONG).show(); return }
        val isNew = draft.id == null
        var calendar = writable.firstOrNull { it.id == draft.calendarId } ?: writable.first()
        var startDate = draft.startDate; var endDate = draft.endDate
        var start = draft.startMin; var end = draft.endMin
        var allDay = draft.allDay
        var rrule = draft.rrule
        var reminder = draft.reminderMin
        val is24 = DateFormat.is24HourFormat(this)

        val c = dialogs.card()
        c.addView(txt(getString(if (isNew) R.string.cal_new_event else R.string.cal_edit_event), 19f, bold = true))
        fun field(hintRes: Int, value: String, size: Float, lines: Int = 1) = EditText(this).apply {
            hint = getString(hintRes); setHintTextColor(p.text2); setTextColor(p.text); textSize = size
            if (lines == 1) setSingleLine() else { minLines = lines; maxLines = 4; gravity = Gravity.TOP }
            setText(value)
            background = dialogs.rounded(withAlpha(p.text, 24), 14); setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        val titleEdit = field(R.string.cal_title_hint, draft.title, 16f)
        c.addView(titleEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        val locationEdit = field(R.string.cal_location_hint, draft.location, 15f)
        c.addView(locationEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        val descEdit = field(R.string.cal_description_hint, draft.description, 15f, 2)
        c.addView(descEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val allDayRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        allDayRow.addView(txt(getString(R.string.cal_all_day), 15f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val startDateBtn = txt("", 14f, p.accent, true); val startTimeBtn = txt("", 14f, p.accent, true)
        val endDateBtn = txt("", 14f, p.accent, true); val endTimeBtn = txt("", 14f, p.accent, true)
        val repeatBtn = txt("", 14f, p.accent, true); val remindBtn = txt("", 14f, p.accent, true)
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        fun minText(m: Int) = LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        fun refresh() {
            startDateBtn.text = startDate.format(dateFmt); endDateBtn.text = endDate.format(dateFmt)
            startTimeBtn.text = minText(start); endTimeBtn.text = minText(end)
            startTimeBtn.visibility = if (allDay) View.GONE else View.VISIBLE
            endTimeBtn.visibility = if (allDay) View.GONE else View.VISIBLE
            repeatBtn.text = repeatLabel(rrule); remindBtn.text = reminderLabel(reminder)
        }
        allDayRow.addView(SwitchCompat(this).apply { isChecked = allDay; setOnCheckedChangeListener { _, on -> allDay = on; refresh() } })
        c.addView(allDayRow)
        listOf(startDateBtn, startTimeBtn, endDateBtn, endTimeBtn, repeatBtn, remindBtn).forEach {
            it.background = dialogs.rounded(withAlpha(p.accent, 36), 14); it.setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        fun fixEnd() { if (endDate.isBefore(startDate)) endDate = startDate; if (endDate == startDate && end <= start) end = (start + 60).coerceAtMost(23 * 60 + 59) }
        startDateBtn.setOnClickListener { Pickers.date(this, dialogs, p, startDate) { d -> val span = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate); startDate = d; endDate = d.plusDays(span); refresh() } }
        endDateBtn.setOnClickListener { Pickers.date(this, dialogs, p, endDate) { d -> endDate = d; fixEnd(); refresh() } }
        startTimeBtn.setOnClickListener { Pickers.time(this, dialogs, p, start / 60, start % 60, is24) { h, m -> start = h * 60 + m; fixEnd(); refresh() } }
        endTimeBtn.setOnClickListener { Pickers.time(this, dialogs, p, end / 60, end % 60, is24) { h, m -> end = h * 60 + m; refresh() } }

        fun pairRow(label: Int, d: View, t: View) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(txt(getString(label), 13f, p.text2, true), LinearLayout.LayoutParams(dp(52), WRAP_CONTENT))
            r.addView(d); r.addView(t, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
            c.addView(r, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        pairRow(R.string.cal_from, startDateBtn, startTimeBtn)
        pairRow(R.string.cal_to, endDateBtn, endTimeBtn)

        repeatBtn.setOnClickListener {
            val opts = listOf<String?>(null, "FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY")
            dialogs.sheet(getString(R.string.cal_repeat), opts.map { o -> SheetItem(R.drawable.ic_glyph_calendar, repeatLabel(o), checked = o == rrule) { rrule = o; refresh() } })
        }
        remindBtn.setOnClickListener {
            val opts = listOf<Int?>(null, 0, 10, 30, 60, 1440)
            dialogs.sheet(getString(R.string.cal_reminder), opts.map { o -> SheetItem(R.drawable.ic_glyph_clock, reminderLabel(o), checked = o == reminder) { reminder = o; refresh() } })
        }
        val opts = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        opts.addView(repeatBtn); opts.addView(remindBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
        c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(opts) })

        if (isNew && writable.size > 1) {
            val chips = LinearLayout(this)
            writable.forEach { cal ->
                val chip = txt(cal.name.take(22), 12.5f, bold = true).apply {
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        calendar = cal
                        for (k in 0 until chips.childCount) {
                            val on = k == writable.indexOf(cal)
                            (chips.getChildAt(k) as TextView).apply { background = dialogs.rounded(if (on) writable[k].color else withAlpha(p.text, 24), 16); setTextColor(if (on) Color.WHITE else p.text) }
                        }
                    }
                }
                val on = cal.id == calendar.id
                chip.background = dialogs.rounded(if (on) cal.color else withAlpha(p.text, 24), 16)
                chip.setTextColor(if (on) Color.WHITE else p.text)
                chips.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            }
            c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        lateinit var dialog: android.app.Dialog
        val buttons = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, dp(16), 0, 0) }
        buttons.addView(txt(getString(R.string.files_cancel), 15f, p.text2, true).apply { setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { dialog.dismiss() } })
        buttons.addView(txt(getString(R.string.clock_save), 15f, p.onAccent, true).apply {
            background = dialogs.rounded(p.accent, 22); setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener {
                val t = titleEdit.text.toString().trim()
                if (t.isEmpty()) { titleEdit.error = getString(R.string.cal_title_required); return@setOnClickListener }
                dialog.dismiss()
                val out = CalendarRepo.Draft(draft.id, calendar.id, t, locationEdit.text.toString().trim(), descEdit.text.toString().trim(),
                    startDate, start, endDate, end, allDay, rrule, reminder)
                Thread {
                    val ok = CalendarRepo.save(contentResolver, out)
                    runOnUiThread {
                        if (ok) { selected = startDate; refresh(true) }
                        else android.widget.Toast.makeText(this@CalendarActivity, getString(R.string.cal_save_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        })
        c.addView(buttons)
        refresh()
        dialog = dialogs.makeDialog(ScrollView(this).apply { addView(c) }, false)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }
}
