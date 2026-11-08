package com.guruswarupa.launch.ui.activities

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.apps.OptionalApps

/** Settings screen where each app in the Launch suite can be turned on or off. */
class LaunchAppsActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        setContentView(buildUi())
    }

    private fun buildUi(): android.view.View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(TextView(this).apply {
            text = getString(R.string.launch_apps_title); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text)
            setPadding(dp(6), 0, 0, 0)
        })
        column.addView(bar)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(24)) }
        list.addView(TextView(this).apply {
            text = getString(R.string.launch_apps_intro); textSize = 14f; setTextColor(p.text2); setPadding(dp(4), 0, dp(4), dp(14))
        })
        OptionalApps.all.forEach { app ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = dialogs.rounded(p.card, 20)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            row.addView(ImageView(this).apply { setImageResource(app.iconRes) }, LinearLayout.LayoutParams(dp(46), dp(46)))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply { text = getString(app.labelRes); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text) })
            texts.addView(TextView(this).apply { text = getString(app.descRes); textSize = 12.5f; setTextColor(p.text2) })
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14); marginEnd = dp(8) })
            row.addView(SwitchCompat(this).apply {
                isChecked = OptionalApps.isEnabled(this@LaunchAppsActivity, app.id)
                setOnCheckedChangeListener { _, on -> OptionalApps.setEnabled(this@LaunchAppsActivity, app.id, on) }
            })
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        list.addView(TextView(this).apply {
            text = getString(R.string.bloat_title); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.accent)
            gravity = Gravity.CENTER; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = dialogs.rounded(androidx.core.graphics.ColorUtils.setAlphaComponent(p.accent, 40), 22)
            setOnClickListener { startActivity(android.content.Intent(this@LaunchAppsActivity, BloatwareActivity::class.java)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        column.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        return frame
    }
}
