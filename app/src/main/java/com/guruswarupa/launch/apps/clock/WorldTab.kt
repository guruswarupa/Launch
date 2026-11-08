package com.guruswarupa.launch.apps.clock

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.withAlpha
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.TimeZone

internal class WorldTab(private val a: ClockActivity) : ClockTab {
    private val root = FrameLayout(a)
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(110)) }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this, 15_000) } }
    private val prefs = a.getSharedPreferences("clock_world", android.content.Context.MODE_PRIVATE)

    init {
        root.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(list) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_add); setColorFilter(a.p.onAccent)
            val pd = a.dp(16); setPadding(pd, pd, pd, pd)
            background = a.dialogs.rounded(a.p.accent, 28)
            elevation = a.dp(6).toFloat()
            contentDescription = a.getString(R.string.clock_add_city)
            setOnClickListener { pick() }
        }, FrameLayout.LayoutParams(a.dp(56), a.dp(56), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, a.dp(20), a.dp(20)) })
    }

    override fun view(): View = root
    override fun onShow() { handler.removeCallbacks(tick); tick.run() }
    override fun onHide() { handler.removeCallbacks(tick) }

    private fun zones(): List<String> = (prefs.getString("zones", "") ?: "").split("|").filter { it.isNotBlank() }

    private fun saveZones(z: List<String>) { prefs.edit().putString("zones", z.joinToString("|")).apply() }

    private fun city(id: String): String = id.substringAfterLast('/').replace('_', ' ')

    private fun render() {
        list.removeAllViews()
        val local = ZoneId.systemDefault()
        list.addView(row(local.id, true), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) })
        zones().forEach { id -> list.addView(row(id, false), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) }) }
        if (zones().isEmpty()) list.addView(a.txt(a.getString(R.string.clock_world_hint), 14f, a.p.text2).apply { setPadding(a.dp(6), a.dp(8), a.dp(6), 0) })
    }

    private fun row(id: String, isLocal: Boolean): View {
        val zone = try { ZoneId.of(id) } catch (e: Exception) { ZoneId.systemDefault() }
        val now = ZonedDateTime.now(zone)
        val here = ZonedDateTime.now(ZoneId.systemDefault())
        val diffMin = (now.offset.totalSeconds - here.offset.totalSeconds) / 60
        val dayDiff = ChronoDays.between(here.toLocalDate(), now.toLocalDate())
        val dayText = when {
            dayDiff == 0L -> a.getString(R.string.clock_today)
            dayDiff > 0 -> a.getString(R.string.clock_tomorrow)
            else -> a.getString(R.string.clock_yesterday)
        }
        val diffText = when {
            isLocal -> a.getString(R.string.clock_local_time)
            diffMin == 0 -> a.getString(R.string.clock_same_time)
            else -> {
                val h = Math.abs(diffMin) / 60; val m = Math.abs(diffMin) % 60
                val amount = if (m == 0) a.getString(R.string.clock_h, h.toLong()) else a.getString(R.string.clock_h, h.toLong()) + " " + a.getString(R.string.clock_m, m.toLong())
                a.getString(if (diffMin > 0) R.string.clock_ahead else R.string.clock_behind, amount)
            }
        }
        val fmt = if (DateFormat.is24HourFormat(a)) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val row = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = a.dialogs.rounded(if (isLocal) withAlpha(a.p.accent, 40) else a.p.card, 22)
            setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14))
            if (!isLocal) setOnLongClickListener {
                a.dialogs.confirm(city(id), a.getString(R.string.clock_remove_city), a.getString(R.string.clock_remove), true) { saveZones(zones() - id); render() }
                true
            }
        }
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(a.txt(city(id), 17f, bold = true))
        texts.addView(a.txt("$dayText · $diffText", 12.5f, a.p.text2))
        texts.addView(a.txt(TimeZone.getTimeZone(id).getDisplayName(TimeZone.getTimeZone(id).inDaylightTime(java.util.Date()), TimeZone.LONG, Locale.getDefault()), 11.5f, a.p.text2))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(a.txt(now.format(fmt), 30f).apply { typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) })
        return row
    }

    private fun pick() {
        val all = ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && listOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific").any { c -> it.startsWith("$c/") } }
            .sortedBy { city(it) }
        val c = a.dialogs.card()
        c.addView(a.txt(a.getString(R.string.clock_add_city), 19f, bold = true))
        val search = EditText(a).apply {
            hint = a.getString(R.string.files_search); setHintTextColor(a.p.text2); setTextColor(a.p.text); textSize = 15f; setSingleLine()
            background = a.dialogs.rounded(withAlpha(a.p.text, 24), 14)
            setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10))
        }
        c.addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(10) })
        var shown: List<String> = all
        val lv = ListView(a).apply { divider = null }
        val adapter = object : ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, ArrayList<String>()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                val id = shown[position]
                v.text = city(id) + "  ·  " + id.substringBefore('/')
                v.setTextColor(a.p.text); v.textSize = 15f
                return v
            }
            override fun getCount() = shown.size
            override fun getItem(position: Int): String = shown[position]
        }
        lv.adapter = adapter
        c.addView(lv, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(300)).apply { topMargin = a.dp(8) })
        val dialog = a.dialogs.makeDialog(c, false)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty().trim()
                shown = if (q.isEmpty()) all else all.filter { id ->
                    city(id).contains(q, true) || id.contains(q, true) ||
                        TimeZone.getTimeZone(id).getDisplayName(Locale.getDefault()).contains(q, true)
                }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        lv.setOnItemClickListener { _, _, pos, _ ->
            val id = shown[pos]
            if (id !in zones()) saveZones(zones() + id)
            dialog.dismiss(); render()
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }
}

private object ChronoDays {
    fun between(a: LocalDate, b: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(a, b)
}
