package com.guruswarupa.launch.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.apps.Bloat
import com.guruswarupa.launch.apps.BloatCatalog
import com.guruswarupa.launch.apps.BloatRisk
import com.guruswarupa.launch.apps.OptionalApps

/**
 * Finds preinstalled apps from the phone maker, shows their package ids, and points to the Launch app that can replace each.
 * Android doesn't let a normal app remove system apps, so removal happens through app info or an adb command.
 */
class BloatwareActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var list: LinearLayout
    private lateinit var summary: LinearLayout

    private class Found(val bloat: Bloat, val label: String, val system: Boolean, val enabled: Boolean)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
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
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        scan()
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_f_back); setColorFilter(p.text); contentDescription = getString(R.string.files_back)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(label(getString(R.string.bloat_title), 20f, bold = true).apply { setPadding(dp(6), 0, 0, 0) })
        column.addView(bar)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(28)) }
        summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = dialogs.rounded(p.card, 22); setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        content.addView(summary, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        column.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        return frame
    }

    private fun scan() {
        Thread {
            val pm = packageManager
            val found = BloatCatalog.all.mapNotNull { b ->
                try {
                    val info = pm.getApplicationInfo(b.pkg, 0)
                    Found(b, pm.getApplicationLabel(info).toString().ifBlank { b.name }, info.flags and ApplicationInfo.FLAG_SYSTEM != 0, info.enabled)
                } catch (_: Exception) { null }
            }
            runOnUiThread { render(found) }
        }.start()
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("bloat", text))
        Toast.makeText(this, getString(R.string.calc_copied), Toast.LENGTH_SHORT).show()
    }

    private fun disableCmd(pkg: String) = "adb shell pm disable-user --user 0 $pkg"
    private fun uninstallCmd(pkg: String) = "adb shell pm uninstall -k --user 0 $pkg"

    private fun render(found: List<Found>) {
        summary.removeAllViews()
        list.removeAllViews()
        val active = found.filter { it.enabled }
        val replaceable = active.filter { it.bloat.alt != null }
        summary.addView(label(Build_MANUFACTURER(), 12.5f, p.text2, true))
        summary.addView(label(
            if (active.isEmpty()) getString(R.string.bloat_none) else getString(R.string.bloat_found, active.size, replaceable.size),
            17f, bold = true
        ).apply { setPadding(0, dp(4), 0, dp(6)) })
        summary.addView(label(getString(R.string.bloat_howto), 13f, p.text2))
        val safe = active.filter { it.bloat.risk == BloatRisk.SAFE }
        if (safe.isNotEmpty()) {
            summary.addView(label(getString(R.string.bloat_copy_script, safe.size), 14.5f, p.onAccent, true).apply {
                gravity = Gravity.CENTER; background = dialogs.rounded(p.accent, 22); setPadding(dp(18), dp(11), dp(18), dp(11))
                setOnClickListener {
                    copy(safe.joinToString("\n") { disableCmd(it.bloat.pkg) })
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        }
        summary.addView(label(getString(R.string.bloat_restore_hint), 12f, p.text2).apply { setPadding(0, dp(10), 0, 0) })

        if (found.isEmpty()) return
        fun section(title: String, items: List<Found>) {
            if (items.isEmpty()) return
            list.addView(label(title, 13f, p.text2, true).apply { setPadding(dp(6), dp(8), 0, dp(8)) })
            items.forEach { list.addView(row(it), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }) }
        }
        section(getString(R.string.bloat_section_replaceable), found.filter { it.bloat.alt != null && it.enabled })
        section(getString(R.string.bloat_section_other), found.filter { it.bloat.alt == null && it.enabled })
        section(getString(R.string.bloat_section_disabled), found.filter { !it.enabled })
    }

    @Suppress("FunctionName")
    private fun Build_MANUFACTURER(): String = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + android.os.Build.MODEL

    private fun row(f: Found): View {
        val b = f.bloat
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = dialogs.rounded(p.card, 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            alpha = if (f.enabled) 1f else 0.55f
            setOnClickListener { showActions(f) }
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(this).apply {
            try { setImageDrawable(packageManager.getApplicationIcon(b.pkg)) } catch (_: Exception) { setImageResource(R.drawable.ic_apps_grid) }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(f.label, 15.5f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        texts.addView(label(b.pkg, 11.5f, p.text2).apply {
            typeface = Typeface.MONOSPACE; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        top.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12); marginEnd = dp(8) })
        val risk = b.risk == BloatRisk.SAFE
        top.addView(label(getString(if (risk) R.string.bloat_safe else R.string.bloat_caution), 11.5f, if (risk) 0xFF66BB6A.toInt() else 0xFFFFA726.toInt(), true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = dialogs.rounded(if (risk) 0x3366BB6A else 0x33FFA726, 12)
        })
        card.addView(top)
        val alt = b.alt?.let { OptionalApps.byId(it) }
        if (alt != null && f.enabled) {
            card.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0)
                addView(ImageView(context).apply { setImageResource(alt.iconRes) }, LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(label(getString(R.string.bloat_use_instead, getString(alt.labelRes)), 13.5f, p.accent, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
                addView(label(getString(R.string.bloat_open), 13f, p.onAccent, true).apply {
                    setPadding(dp(14), dp(6), dp(14), dp(6)); background = dialogs.rounded(p.accent, 16)
                    setOnClickListener { openAlt(alt) }
                })
            })
        }
        return card
    }

    private fun openAlt(alt: com.guruswarupa.launch.apps.SuiteApp) {
        if (!OptionalApps.isEnabled(this, alt.id)) OptionalApps.setEnabled(this, alt.id, true)
        try { startActivity(OptionalApps.launchIntent(alt, this)) } catch (_: Exception) {}
    }

    private fun showActions(f: Found) {
        val b = f.bloat
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_f_info, getString(R.string.bloat_app_info)) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${b.pkg}"))) } catch (_: Exception) {}
        })
        b.alt?.let { OptionalApps.byId(it) }?.let { alt ->
            items.add(SheetItem(alt.iconRes, getString(R.string.bloat_use_instead, getString(alt.labelRes))) { openAlt(alt) })
        }
        items.add(SheetItem(R.drawable.ic_f_copy, getString(R.string.bloat_copy_id)) { copy(b.pkg) })
        items.add(SheetItem(R.drawable.ic_f_copy, getString(R.string.bloat_copy_disable)) { copy(disableCmd(b.pkg)) })
        items.add(SheetItem(R.drawable.ic_f_delete, getString(R.string.bloat_copy_uninstall), danger = true) { copy(uninstallCmd(b.pkg)) })
        dialogs.sheet(f.label + "\n" + b.pkg, items)
    }
}
