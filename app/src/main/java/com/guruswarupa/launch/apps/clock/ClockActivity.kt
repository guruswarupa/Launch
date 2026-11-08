package com.guruswarupa.launch.apps.clock

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.withAlpha

internal interface ClockTab {
    fun view(): View
    fun onShow() {}
    fun onHide() {}
}

class ClockActivity : AppCompatActivity() {

    internal lateinit var p: FilesPalette
    internal lateinit var dialogs: FilesDialogs
    private lateinit var content: FrameLayout
    private lateinit var navItems: List<Pair<ImageView, TextView>>
    private lateinit var tabs: List<ClockTab>
    private var current = -1

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    internal fun txt(text: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

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
        tabs = listOf(AlarmsTab(this), WorldTab(this), StopwatchTab(this), TimerTab(this))
        setContentView(buildUi())
        val start = savedInstanceState?.getInt("tab") ?: intent.getIntExtra("tab", 0)
        select(start.coerceIn(0, tabs.lastIndex))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    override fun onResume() {
        super.onResume()
        if (current >= 0) tabs[current].onShow()
    }

    override fun onPause() {
        if (current >= 0) tabs[current].onHide()
        super.onPause()
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(16), dp(4)) }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text)
            contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(txt(getString(R.string.app_clock), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
        column.addView(header)

        content = FrameLayout(this)
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val nav = LinearLayout(this).apply {
            background = dialogs.rounded(p.cardStrong, 0)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val defs = listOf(
            R.drawable.ic_clock_alarm to R.string.clock_tab_alarm,
            R.drawable.ic_clock_world to R.string.clock_tab_world,
            R.drawable.ic_clock_stopwatch to R.string.clock_tab_stopwatch,
            R.drawable.ic_clock_timer to R.string.clock_tab_timer
        )
        navItems = defs.mapIndexed { i, (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                isClickable = true
                setOnClickListener { select(i) }
            }
            val iv = ImageView(this).apply { setImageResource(icon) }
            val tv = txt(getString(label), 11.5f, p.text2, true).apply { gravity = Gravity.CENTER; maxLines = 1 }
            item.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(2) })
            nav.addView(item, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            iv to tv
        }
        column.addView(nav)

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, 0)
            nav.setPadding(dp(8), dp(6), dp(8), b.bottom + dp(6))
            insets
        }
        return frame
    }

    internal fun select(index: Int) {
        if (index == current) return
        if (current >= 0) tabs[current].onHide()
        current = index
        content.removeAllViews()
        content.addView(tabs[index].view(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        navItems.forEachIndexed { i, (icon, label) ->
            val on = i == index
            icon.setColorFilter(if (on) p.accent else p.text2)
            label.setTextColor(if (on) p.accent else p.text2)
            (icon.parent as View).background = if (on) dialogs.rounded(withAlpha(p.accent, 36), 16) else null
        }
        tabs[index].onShow()
    }
}
