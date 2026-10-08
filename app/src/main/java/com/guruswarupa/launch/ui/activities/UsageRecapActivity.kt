package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ai.prediction.LaunchEventStore
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UsageRecapActivity : AppCompatActivity() {

    @Inject lateinit var launchEventStore: LaunchEventStore

    private lateinit var usageManager: AppUsageStatsManager
    private lateinit var root: LinearLayout

    private data class Recap(
        val todayMs: Long,
        val weekMs: Long,
        val topApps: List<Triple<String, Long, android.graphics.drawable.Drawable?>>,
        val mostLaunched: List<Triple<String, Int, android.graphics.drawable.Drawable?>>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usageManager = AppUsageStatsManager(this)
        title = getString(R.string.usage_recap_title)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(ThemeManager.color(this@UsageRecapActivity, R.attr.appSurface))
            clipToPadding = false
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)
        setContentView(scroll)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        addText(getString(R.string.usage_recap_title), 28f, bold = true)
        if (!usageManager.hasUsageStatsPermission()) {
            addText(getString(R.string.usage_recap_permission), 16f, topMargin = 16)
            root.addView(Button(this).apply {
                text = getString(R.string.usage_recap_grant)
                isAllCaps = false
                setOnClickListener { startActivity(usageManager.requestUsageStatsPermission()) }
            })
            return
        }
        Thread {
            val recap = buildRecap()
            runOnUiThread { if (!isFinishing) show(recap) }
        }.start()
    }

    private fun buildRecap(): Recap {
        val week = usageManager.getWeeklyAppUsageData()
        val totals = mutableMapOf<String, Long>()
        week.forEach { (_, map) -> map.forEach { (pkg, ms) -> totals[pkg] = (totals[pkg] ?: 0L) + ms } }
        val todayMs = week.lastOrNull()?.second?.values?.sum() ?: 0L
        val pm = packageManager
        val top = totals.entries.sortedByDescending { it.value }.take(5).map {
            Triple(labelOf(it.key), it.value, iconOf(it.key))
        }
        val launched = launchEventStore.snapshot().entries
            .filter { !it.key.startsWith("contact:") && pm.getLaunchIntentForPackage(it.key) != null }
            .sortedByDescending { it.value.launchCount }
            .take(5)
            .map { Triple(labelOf(it.key), it.value.launchCount, iconOf(it.key)) }
        return Recap(todayMs, totals.values.sum(), top, launched)
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun iconOf(pkg: String) = try {
        packageManager.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun show(recap: Recap) {
        if (recap.weekMs == 0L && recap.mostLaunched.isEmpty()) {
            addText(getString(R.string.usage_recap_empty), 16f, topMargin = 16)
            return
        }
        addStat(getString(R.string.usage_recap_total_today), usageManager.formatUsageTime(recap.todayMs))
        addStat(getString(R.string.usage_recap_total_week), usageManager.formatUsageTime(recap.weekMs))
        addStat(getString(R.string.usage_recap_daily_avg), usageManager.formatUsageTime(recap.weekMs / 7))

        if (recap.topApps.isNotEmpty()) {
            addText(getString(R.string.usage_recap_top_apps), 18f, bold = true, topMargin = 24)
            recap.topApps.forEach { addRow(it.first, usageManager.formatUsageTime(it.second), it.third) }
        }
        if (recap.mostLaunched.isNotEmpty()) {
            addText(getString(R.string.usage_recap_most_launched), 18f, bold = true, topMargin = 24)
            recap.mostLaunched.forEach { addRow(it.first, getString(R.string.usage_recap_launches, it.second), it.third) }
        }
    }

    private fun addStat(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(textView(label, 16f, false, ThemeManager.color(this, R.attr.appTextSecondary)), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(textView(value, 20f, true, ThemeManager.color(this, R.attr.appAccent)))
        root.addView(row)
    }

    private fun addRow(name: String, value: String, icon: android.graphics.drawable.Drawable?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val iv = ImageView(this).apply { setImageDrawable(icon) }
        row.addView(iv, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        row.addView(textView(name, 16f, false, ThemeManager.color(this, R.attr.appTextPrimary)), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(textView(value, 14f, false, ThemeManager.color(this, R.attr.appTextSecondary)))
        root.addView(row)
    }

    private fun addText(text: String, sp: Float, bold: Boolean = false, topMargin: Int = 0) {
        val tv = textView(text, sp, bold, ThemeManager.color(this, R.attr.appTextPrimary))
        root.addView(tv, LinearLayout.LayoutParams(-1, -2).apply { this.topMargin = dp(topMargin) })
    }

    private fun textView(text: String, sp: Float, bold: Boolean, color: Int) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
