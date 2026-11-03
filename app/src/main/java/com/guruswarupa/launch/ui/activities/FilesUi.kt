package com.guruswarupa.launch.ui.activities

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

internal fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

internal class FilesPalette(ctx: Context) {
    val text = ThemeManager.color(ctx, R.attr.appTextPrimary)
    val text2 = ThemeManager.color(ctx, R.attr.appTextSecondary)
    val accent = ThemeManager.color(ctx, R.attr.appAccent)
    val onAccent = ThemeManager.color(ctx, R.attr.appOnAccent)
    val error = ThemeManager.color(ctx, R.attr.appError)
    val divider = ThemeManager.color(ctx, R.attr.appDivider)
    val isLight = ThemeManager.isLight(ctx)
    val scrim: Int
    val card: Int
    val cardStrong: Int
    val dialog: Int

    init {
        val prefs = ctx.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val base = ThemeManager.color(ctx, R.attr.appScrim)
        scrim = withAlpha(base, maxOf(translucency * 255 / 100, 120))
        val surface = ThemeManager.color(ctx, R.attr.appSurface)
        card = withAlpha(surface, 150)
        cardStrong = withAlpha(surface, 215)
        dialog = withAlpha(ThemeManager.color(ctx, R.attr.appSurfaceElevated), 245)
    }
}

internal data class SheetItem(
    val icon: Int,
    val label: String,
    val danger: Boolean = false,
    val checked: Boolean = false,
    val action: () -> Unit
)

internal class ProgressHandle(
    private val dialog: Dialog,
    private val message: TextView,
    private val bar: ProgressBar,
    private val activity: Activity
) {
    @Volatile var cancelled = false

    fun update(done: Int, total: Int, label: String) {
        activity.runOnUiThread {
            bar.isIndeterminate = total <= 0
            if (total > 0) { bar.max = total; bar.progress = done }
            message.text = label
        }
    }

    fun dismiss() { activity.runOnUiThread { if (dialog.isShowing) dialog.dismiss() } }
}

internal class FilesDialogs(private val a: Activity, private val p: FilesPalette) {

    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(a).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun makeDialog(content: View, bottom: Boolean): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.45f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val w = (a.resources.displayMetrics.widthPixels * if (bottom) 1.0 else 0.88).toInt()
            setLayout(w, WRAP_CONTENT)
            setGravity(if (bottom) Gravity.BOTTOM else Gravity.CENTER)
            setWindowAnimations(if (bottom) android.R.style.Animation_InputMethod else android.R.style.Animation_Dialog)
        }
        return d
    }

    private fun button(text: String, filled: Boolean, color: Int, onClick: () -> Unit): TextView = TextView(a).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (filled) p.onAccent else color)
        background = if (filled) rounded(color, 22) else null
        setPadding(dp(18), dp(10), dp(18), dp(10))
        setOnClickListener { onClick() }
    }

    private fun buttonRow(vararg buttons: View): LinearLayout = LinearLayout(a).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, dp(16), 0, 0)
        buttons.forEachIndexed { i, b ->
            addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun card(): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(p.dialog, 24, withAlpha(p.text, 40), 1)
        setPadding(dp(22), dp(22), dp(22), dp(16))
        elevation = dp(8).toFloat()
    }

    fun confirm(title: String, message: String, positive: String, destructive: Boolean = false, onOk: () -> Unit) {
        val c = card()
        c.addView(label(title, 19f, p.text, true))
        c.addView(label(message, 14.5f, p.text2).apply { setPadding(0, dp(10), 0, 0) })
        lateinit var d: Dialog
        c.addView(buttonRow(
            button("Cancel", false, p.text2) { d.dismiss() },
            button(positive, true, if (destructive) p.error else p.accent) { d.dismiss(); onOk() }
        ))
        d = makeDialog(c, false)
        d.show()
    }

    fun input(title: String, hint: String, initial: String, positive: String, onOk: (String) -> Unit) {
        val c = card()
        c.addView(label(title, 19f, p.text, true))
        val field = EditText(a).apply {
            setText(initial)
            this.hint = hint
            setTextColor(p.text)
            setHintTextColor(p.text2)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            background = rounded(withAlpha(p.text, 24), 14, withAlpha(p.accent, 160), 1)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setSelection(0, initial.substringBeforeLast('.', initial).length)
        }
        c.addView(field, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        lateinit var d: Dialog
        val submit = { val t = field.text.toString().trim(); if (t.isNotEmpty()) { d.dismiss(); onOk(t) } }
        c.addView(buttonRow(button("Cancel", false, p.text2) { d.dismiss() }, button(positive, true, p.accent) { submit() }))
        d = makeDialog(c, false)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.setOnEditorActionListener { _, _, _ -> submit(); true }
        d.show()
        field.requestFocus()
        field.post { (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }
    }

    fun sheet(title: String?, items: List<SheetItem>) {
        val c = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(p.dialog)
                cornerRadii = floatArrayOf(dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), dp(26).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(8), dp(10), dp(8), dp(12))
        }
        c.addView(View(a).apply { background = rounded(withAlpha(p.text, 70), 3) }, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) })
        if (title != null) {
            c.addView(label(title, 16f, p.text, true).apply {
                setPadding(dp(16), dp(4), dp(16), dp(10)); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
        }
        lateinit var d: Dialog
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { item ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                isClickable = true
                val ripple = android.util.TypedValue()
                a.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener { d.dismiss(); item.action() }
            }
            val tint = if (item.danger) p.error else p.text
            row.addView(ImageView(a).apply { setImageResource(item.icon); setColorFilter(tint) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(label(item.label, 16f, tint), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(18) })
            if (item.checked) row.addView(label("✓", 18f, p.accent, true))
            list.addView(row)
        }
        val scroll = ScrollView(a).apply { addView(list); isVerticalScrollBarEnabled = false }
        c.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        d = makeDialog(c, true)
        d.show()
    }

    fun details(title: String, iconRes: Int, iconColor: Int, rows: List<Pair<String, String>>): List<TextView> {
        val c = card()
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(a).apply {
            setImageResource(iconRes); setColorFilter(iconColor)
            background = rounded(withAlpha(iconColor, 45), 14)
            val pad = dp(10); setPadding(pad, pad, pad, pad)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        head.addView(label(title, 18f, p.text, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        c.addView(head)
        val values = ArrayList<TextView>()
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        rows.forEach { (k, v) ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            row.addView(label(k, 12f, p.text2))
            val value = label(v, 14.5f, p.text).apply { setTextIsSelectable(true) }
            values.add(value)
            row.addView(value)
            body.addView(row)
            body.addView(View(a).apply { setBackgroundColor(withAlpha(p.divider, 90)) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
        }
        c.addView(ScrollView(a).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        lateinit var d: Dialog
        c.addView(buttonRow(button("Close", true, p.accent) { d.dismiss() }))
        d = makeDialog(c, false)
        d.show()
        return values
    }

    fun progress(title: String, onCancel: () -> Unit): ProgressHandle {
        val c = card()
        c.addView(label(title, 19f, p.text, true))
        val msg = label("Preparing…", 13.5f, p.text2).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE; setPadding(0, dp(10), 0, dp(10)) }
        c.addView(msg)
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(p.accent)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(p.accent)
        }
        c.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
        lateinit var handle: ProgressHandle
        c.addView(buttonRow(button("Cancel", false, p.text2) { handle.cancelled = true; onCancel() }))
        val d = makeDialog(c, false)
        d.setCancelable(false)
        handle = ProgressHandle(d, msg, bar, a)
        d.show()
        return handle
    }
}
