package com.guruswarupa.launch.apps.clock

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import com.guruswarupa.launch.clock.R
import com.guruswarupa.launch.ui.activities.withAlpha
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

internal class AlarmsTab(private val a: ClockActivity) : ClockTab {
    private val root = FrameLayout(a)
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(110)) }

    init {
        root.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(list) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(a.p.onAccent)
            val pd = a.dp(16); setPadding(pd, pd, pd, pd)
            background = a.dialogs.rounded(a.p.accent, 28)
            elevation = a.dp(6).toFloat()
            contentDescription = a.getString(R.string.clock_add_alarm)
            setOnClickListener { edit(null) }
        }, FrameLayout.LayoutParams(a.dp(56), a.dp(56), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, a.dp(20), a.dp(20)) })
    }

    override fun view(): View = root

    override fun onShow() = render()

    private fun is24() = DateFormat.is24HourFormat(a)

    private fun timeText(h: Int, m: Int): String {
        if (is24()) return String.format(Locale.getDefault(), "%02d:%02d", h, m)
        val hh = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.getDefault(), "%d:%02d", hh, m)
    }

    private fun suffix(h: Int): String = if (is24()) "" else DateFormatSymbols.getInstance(Locale.getDefault()).amPmStrings[if (h < 12) 0 else 1]

    private fun repeatText(days: Int): String {
        if (days == 0) return a.getString(R.string.clock_once)
        if (days == AlarmTimes.ALL_DAYS) return a.getString(R.string.clock_every_day)
        val weekdays = 0b0011111
        if (days == weekdays) return a.getString(R.string.clock_weekdays)
        if (days == 0b1100000) return a.getString(R.string.clock_weekends)
        return DayOfWeek.values().filter { days and AlarmTimes.dayBit(it) != 0 }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    private fun nextSummary(alarms: List<AlarmItem>): String? {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val next = alarms.filter { it.enabled }.minOfOrNull { AlarmTimes.next(now, it.hour, it.minute, it.days).toInstant().toEpochMilli() } ?: return null
        val d = Duration.ofMillis(next - System.currentTimeMillis())
        val days = d.toDays(); val hours = d.toHours() % 24; val mins = d.toMinutes() % 60
        val parts = ArrayList<String>()
        if (days > 0) parts.add(a.getString(R.string.clock_d, days))
        if (hours > 0) parts.add(a.getString(R.string.clock_h, hours))
        if (mins > 0 || parts.isEmpty()) parts.add(a.getString(R.string.clock_m, if (parts.isEmpty() && mins == 0L) 1 else mins))
        return a.getString(R.string.clock_rings_in, parts.joinToString(" "))
    }

    private fun render() {
        list.removeAllViews()
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(a).areNotificationsEnabled()) {
            val banner = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                background = a.dialogs.rounded(withAlpha(a.p.accent, 40), 18)
                setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))
            }
            banner.addView(a.txt(a.getString(R.string.clock_notif_banner), 13.5f))
            banner.addView(a.txt(a.getString(R.string.perm_allow), 14f, a.p.accent, true).apply {
                setPadding(0, a.dp(8), 0, 0)
                setOnClickListener {
                    a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
                }
            })
            list.addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(12) })
        }
        val alarms = AlarmStore.load(a).sortedWith(compareBy({ it.hour }, { it.minute }))
        val summary = nextSummary(alarms)
        list.addView(a.txt(summary ?: a.getString(R.string.clock_no_alarms), 15f, if (summary != null) a.p.accent else a.p.text2, true).apply {
            setPadding(a.dp(6), a.dp(4), a.dp(6), a.dp(12))
        })
        alarms.forEach { al -> list.addView(card(al), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) }) }
    }

    private fun card(al: AlarmItem): View {
        val row = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = a.dialogs.rounded(a.p.card, 22)
            setPadding(a.dp(18), a.dp(14), a.dp(14), a.dp(14))
            alpha = if (al.enabled) 1f else 0.6f
            setOnClickListener { edit(al) }
        }
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val time = LinearLayout(a).apply { gravity = Gravity.BOTTOM }
        time.addView(a.txt(timeText(al.hour, al.minute), 34f, a.p.text).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        time.addView(a.txt(suffix(al.hour), 14f, a.p.text2).apply { setPadding(a.dp(6), 0, 0, a.dp(6)) })
        texts.addView(time)
        texts.addView(a.txt(listOf(al.label, repeatText(al.days)).filter { it.isNotBlank() }.joinToString(" · "), 13f, a.p.text2))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(androidx.appcompat.widget.SwitchCompat(a).apply {
            isChecked = al.enabled
            setOnCheckedChangeListener { _, on ->
                al.enabled = on
                val all = AlarmStore.load(a)
                all.firstOrNull { it.id == al.id }?.enabled = on
                AlarmStore.save(a, all)
                AlarmScheduler.schedule(a, al)
                render()
            }
        })
        return row
    }

    private fun edit(existing: AlarmItem?) {
        val now = Calendar.getInstance()
        var hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        var minute = existing?.minute ?: now.get(Calendar.MINUTE)
        var days = existing?.days ?: 0
        val c = a.dialogs.card()
        val timeView = a.txt("", 48f).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER
            setPadding(0, a.dp(8), 0, a.dp(8))
        }
        fun refreshTime() { timeView.text = timeText(hour, minute) + (suffix(hour).let { if (it.isEmpty()) "" else " $it" }) }
        refreshTime()
        timeView.setOnClickListener {
            com.guruswarupa.launch.apps.common.Pickers.time(a, a.dialogs, a.p, hour, minute, is24()) { h, m -> hour = h; minute = m; refreshTime() }
        }
        c.addView(a.txt(a.getString(if (existing == null) R.string.clock_add_alarm else R.string.clock_edit_alarm), 19f, bold = true))
        c.addView(timeView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val dayRow = LinearLayout(a).apply { gravity = Gravity.CENTER }
        val first = Calendar.getInstance().firstDayOfWeek
        val order = (0..6).map { DayOfWeek.of(((first + 5 + it) % 7) + 1) }
        order.forEach { d ->
            val chip = a.txt(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), 13f, bold = true).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    days = days xor AlarmTimes.dayBit(d)
                    style(this, days and AlarmTimes.dayBit(d) != 0)
                }
            }
            style(chip, days and AlarmTimes.dayBit(d) != 0)
            dayRow.addView(chip, LinearLayout.LayoutParams(a.dp(38), a.dp(38)).apply { setMargins(a.dp(3), 0, a.dp(3), 0) })
        }
        c.addView(dayRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(6) })
        val label = EditText(a).apply {
            setText(existing?.label.orEmpty()); hint = a.getString(R.string.clock_label)
            setTextColor(a.p.text); setHintTextColor(a.p.text2); textSize = 16f; setSingleLine()
            background = a.dialogs.rounded(withAlpha(a.p.text, 24), 14)
            setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))
        }
        c.addView(label, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(14) })

        val buttons = LinearLayout(a).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setPadding(0, a.dp(14), 0, 0) }
        lateinit var dialog: android.app.Dialog
        fun btn(text: String, filled: Boolean, color: Int, onClick: () -> Unit) = a.txt(text, 15f, if (filled) a.p.onAccent else color, true).apply {
            setPadding(a.dp(18), a.dp(10), a.dp(18), a.dp(10))
            if (filled) background = a.dialogs.rounded(color, 22)
            setOnClickListener { onClick() }
        }
        if (existing != null) {
            buttons.addView(btn(a.getString(R.string.files_delete), false, a.p.error) {
                dialog.dismiss()
                AlarmScheduler.cancel(a, existing.id)
                AlarmStore.save(a, AlarmStore.load(a).filter { it.id != existing.id })
                render()
            })
            buttons.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        }
        buttons.addView(btn(a.getString(R.string.files_cancel), false, a.p.text2) { dialog.dismiss() })
        buttons.addView(btn(a.getString(R.string.clock_save), true, a.p.accent) {
            dialog.dismiss()
            val all = AlarmStore.load(a)
            val item = existing?.let { e -> all.firstOrNull { it.id == e.id } } ?: AlarmItem(AlarmStore.nextId(all), hour, minute, days, "", true).also { all.add(it) }
            item.hour = hour; item.minute = minute; item.days = days; item.label = label.text.toString().trim(); item.enabled = true
            AlarmStore.save(a, all)
            AlarmScheduler.schedule(a, item)
            render()
        })
        c.addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        dialog = a.dialogs.makeDialog(c, false)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun style(v: TextView, on: Boolean) {
        v.background = a.dialogs.rounded(if (on) a.p.accent else withAlpha(a.p.text, 24), 19)
        v.setTextColor(if (on) a.p.onAccent else a.p.text)
    }
}

