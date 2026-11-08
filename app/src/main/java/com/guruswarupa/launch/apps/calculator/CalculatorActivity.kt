package com.guruswarupa.launch.apps.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import org.json.JSONArray

class CalculatorActivity : AppCompatActivity() {

    private lateinit var p: FilesPalette
    private lateinit var dialogs: FilesDialogs
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private lateinit var sciPanel: LinearLayout
    private lateinit var degButton: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var converter: ConverterView
    private lateinit var display: LinearLayout
    private lateinit var pad: LinearLayout
    private var converting = false

    private val expr = StringBuilder()
    private var degrees = true
    private var scientific = false
    private var justEvaluated = false
    private val prefs by lazy { getSharedPreferences("calculator", MODE_PRIVATE) }

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
        degrees = prefs.getBoolean("degrees", true)
        scientific = prefs.getBoolean("scientific", false)
        converting = prefs.getBoolean("converting", false)
        savedInstanceState?.getString("expr")?.let { expr.append(it) }
        setContentView(buildUi())
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expr", expr.toString())
    }

    private fun iconBtn(res: Int, desc: Int, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(p.text)
        contentDescription = getString(desc)
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private enum class Kind { NUM, OP, FN, EQUALS, CLEAR }

    private fun key(label: String, kind: Kind, weight: Float = 1f, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = if (kind == Kind.NUM) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        textSize = if (kind == Kind.FN) 16f else 24f
        val (bg, fg) = when (kind) {
            Kind.NUM -> p.card to p.text
            Kind.OP -> withAlpha(p.accent, 60) to p.accent
            Kind.FN -> withAlpha(p.text, 18) to p.text
            Kind.EQUALS -> p.accent to p.onAccent
            Kind.CLEAR -> withAlpha(p.error, 70) to p.error
        }
        setTextColor(fg)
        background = dialogs.rounded(bg, 22)
        layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, weight).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        isClickable = true
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    }

    private fun rowOf(vararg keys: View, weight: Float = 1f) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        keys.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, weight)
    }

    private fun buildUi(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(p.scrim) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(iconBtn(R.drawable.ic_f_back, R.string.files_back) { finish() })
        bar.addView(TextView(this).apply {
            text = getString(R.string.app_calculator); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(iconBtn(R.drawable.ic_f_recent, R.string.calc_history) { showHistory() })
        column.addView(bar)

        // Basic / Scientific / Converter switch
        modeRow = LinearLayout(this).apply {
            background = dialogs.rounded(withAlpha(p.text, 18), 20); setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        listOf(
            Triple(0, R.string.calc_basic, "basic"), Triple(1, R.string.calc_scientific, "sci"), Triple(2, R.string.conv_title, "conv")
        ).forEach { (_, label, tag) ->
            modeRow.addView(TextView(this).apply {
                text = getString(label); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                this.tag = tag; setPadding(0, dp(8), 0, dp(8)); maxLines = 1
                setOnClickListener {
                    scientific = tag == "sci"; converting = tag == "conv"
                    prefs.edit().putBoolean("scientific", scientific).putBoolean("converting", converting).apply()
                    updateModes()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        column.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })

        display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }
        degButton = TextView(this).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(p.accent)
            setPadding(dp(12), dp(5), dp(12), dp(5)); background = dialogs.rounded(withAlpha(p.accent, 40), 14)
            setOnClickListener { degrees = !degrees; prefs.edit().putBoolean("degrees", degrees).apply(); updateModes(); refresh() }
        }
        display.addView(degButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.START })
        exprView = TextView(this).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            setTextColor(p.text); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            maxLines = 3
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 26, 60, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnLongClickListener { copyResult(); true }
        }
        resultView = TextView(this).apply {
            gravity = Gravity.END; textSize = 26f; setTextColor(p.text2); maxLines = 1
            setOnLongClickListener { copyResult(); true }
        }
        display.addView(exprView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        display.addView(resultView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        column.addView(display, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.15f))

        sciPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), 0, dp(8), 0)
        }
        fun fn(label: String, insert: String = label) = key(label, Kind.FN) { append(insert, true) }
        sciPanel.addView(rowOf(fn("sin", "sin("), fn("cos", "cos("), fn("tan", "tan("), fn("ln", "ln("), fn("log", "log(")))
        sciPanel.addView(rowOf(fn("asin", "asin("), fn("acos", "acos("), fn("atan", "atan("), fn("√", "√("), fn("^", "^")))
        sciPanel.addView(rowOf(fn("π"), fn("e", "e"), fn("x!", "!"), fn("1/x", "1÷"), fn("abs", "abs(")))
        column.addView(sciPanel, LinearLayout.LayoutParams(MATCH_PARENT, dp(3 * 52)))

        pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(12)) }
        pad.addView(rowOf(
            key("AC", Kind.CLEAR) { clearAll() },
            key("( )", Kind.OP) { parenthesis() },
            key("⌫", Kind.OP) { backspace() }.also { it.setOnLongClickListener { clearAll(); true } },
            key("÷", Kind.OP) { append("÷", false) }
        ))
        pad.addView(rowOf(key("7", Kind.NUM) { append("7", false) }, key("8", Kind.NUM) { append("8", false) }, key("9", Kind.NUM) { append("9", false) }, key("×", Kind.OP) { append("×", false) }))
        pad.addView(rowOf(key("4", Kind.NUM) { append("4", false) }, key("5", Kind.NUM) { append("5", false) }, key("6", Kind.NUM) { append("6", false) }, key("−", Kind.OP) { append("−", false) }))
        pad.addView(rowOf(key("1", Kind.NUM) { append("1", false) }, key("2", Kind.NUM) { append("2", false) }, key("3", Kind.NUM) { append("3", false) }, key("+", Kind.OP) { append("+", false) }))
        pad.addView(rowOf(key("%", Kind.OP) { append("%", false) }, key("0", Kind.NUM) { append("0", false) }, key(".", Kind.NUM) { append(".", false) }, key("=", Kind.EQUALS) { equals() }))
        column.addView(pad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2.3f))

        converter = ConverterView(this, p, dialogs).apply { visibility = View.GONE }
        column.addView(converter, LinearLayout.LayoutParams(MATCH_PARENT, 0, 3.45f))

        frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        updateModes()
        return frame
    }

    private fun updateModes() {
        degButton.text = getString(if (degrees) R.string.calc_deg else R.string.calc_rad)
        sciPanel.visibility = if (scientific && !converting) View.VISIBLE else View.GONE
        display.visibility = if (converting) View.GONE else View.VISIBLE
        pad.visibility = if (converting) View.GONE else View.VISIBLE
        converter.visibility = if (converting) View.VISIBLE else View.GONE
        degButton.visibility = if (scientific && !converting) View.VISIBLE else View.INVISIBLE
        val current = if (converting) "conv" else if (scientific) "sci" else "basic"
        for (i in 0 until modeRow.childCount) {
            val t = modeRow.getChildAt(i) as TextView
            val on = t.tag == current
            t.background = if (on) dialogs.rounded(p.accent, 17) else null
            t.setTextColor(if (on) p.onAccent else p.text2)
        }
    }

    private fun lastChar(): Char? = expr.lastOrNull()

    private fun isOperator(c: Char?) = c != null && c in "+−×÷^"

    private fun append(token: String, fnLike: Boolean) {
        val first = token.first()
        if (justEvaluated) {
            if (first.isDigit() || first == '.' || fnLike || first == 'π' || first == 'e' || first == '√') {
                if (!(token == "!" || token == "^" || token == "%")) expr.setLength(0)
            }
            justEvaluated = false
        }
        when {
            token == "." -> {
                val currentNumber = expr.takeLastWhile { it.isDigit() || it == '.' }
                if (currentNumber.contains('.')) return
                if (currentNumber.isEmpty()) expr.append("0")
                expr.append(".")
            }
            isOperator(first) && token.length == 1 -> {
                if (expr.isEmpty()) { if (token == "−") expr.append("−"); refresh(); return }
                val last = lastChar()
                if (last == '(' && token != "−") return
                if (isOperator(last)) {
                    if (token == "−" && (last == '×' || last == '÷' || last == '^')) expr.append("−")
                    else { expr.setLength(expr.length - 1); expr.append(token) }
                } else expr.append(token)
            }
            token == "%" || token == "!" -> {
                val last = lastChar()
                if (last == null || isOperator(last) || last == '(') return
                expr.append(token)
            }
            else -> {
                if (token.first().isDigit() && lastChar().let { it == ')' || it == '%' || it == '!' || it == 'π' || it == 'e' }) expr.append("×")
                expr.append(token)
            }
        }
        refresh()
    }

    private fun parenthesis() {
        if (justEvaluated) { expr.setLength(0); justEvaluated = false }
        val open = expr.count { it == '(' }
        val close = expr.count { it == ')' }
        val last = lastChar()
        if (open > close && last != null && last != '(' && !isOperator(last)) expr.append(")")
        else {
            if (last != null && (last.isDigit() || last == ')' || last == '%' || last == '!' || last == 'π' || last == 'e')) expr.append("×")
            expr.append("(")
        }
        refresh()
    }

    private fun backspace() {
        if (expr.isEmpty()) return
        val functions = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "log(", "abs(", "ln(", "√(")
        val s = expr.toString()
        val f = functions.firstOrNull { s.endsWith(it) }
        expr.setLength(expr.length - (f?.length ?: 1))
        justEvaluated = false
        refresh()
    }

    private fun clearAll() {
        expr.setLength(0)
        justEvaluated = false
        refresh()
    }

    private fun equals() {
        if (expr.isEmpty()) return
        try {
            val text = expr.toString()
            val value = CalcEngine.format(CalcEngine.evaluate(text, degrees))
            addHistory(text, value)
            expr.setLength(0)
            expr.append(value.replace("-", "−"))
            justEvaluated = true
            refresh()
        } catch (e: CalcException) {
            resultView.setTextColor(p.error)
            resultView.text = errorText(e)
        }
    }

    private fun errorText(e: CalcException) = getString(when (e.kind) {
        CalcException.Kind.DIVIDE_BY_ZERO -> R.string.calc_err_div0
        CalcException.Kind.DOMAIN -> R.string.calc_err_domain
        CalcException.Kind.RANGE -> R.string.calc_err_range
        CalcException.Kind.SYNTAX -> R.string.calc_err_format
    })

    private fun refresh() {
        exprView.text = if (expr.isEmpty()) "0" else expr.toString()
        exprView.alpha = if (expr.isEmpty()) 0.4f else 1f
        resultView.setTextColor(p.text2)
        resultView.text = when {
            expr.isEmpty() || justEvaluated -> ""
            else -> try {
                val t = expr.toString()
                if (t.any { it in "+−×÷^%!(" } || t.contains(Regex("[a-zπ√]"))) "= " + CalcEngine.format(CalcEngine.evaluate(t, degrees)).replace("-", "−") else ""
            } catch (e: CalcException) { "" }
        }
    }

    private fun copyResult() {
        val text = (if (justEvaluated || resultView.text.isEmpty()) exprView.text.toString() else resultView.text.toString().removePrefix("= "))
        if (text == "0" && expr.isEmpty()) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("result", text.replace("−", "-")))
        Toast.makeText(this, getString(R.string.calc_copied), Toast.LENGTH_SHORT).show()
    }

    private fun history(): MutableList<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONArray(i)
                out.add(o.getString(0) to o.getString(1))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun addHistory(e: String, r: String) {
        val list = history()
        list.add(0, e to r)
        val arr = JSONArray()
        list.take(50).forEach { arr.put(JSONArray().put(it.first).put(it.second)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun showHistory() {
        val list = history()
        if (list.isEmpty()) { Toast.makeText(this, getString(R.string.calc_history_empty), Toast.LENGTH_SHORT).show(); return }
        val items = ArrayList<SheetItem>()
        list.take(30).forEach { (e, r) ->
            items.add(SheetItem(R.drawable.ic_f_recent, "$e = $r") {
                if (justEvaluated) { expr.setLength(0); justEvaluated = false }
                expr.append(r.replace("-", "−"))
                refresh()
            })
        }
        items.add(SheetItem(R.drawable.ic_f_delete, getString(R.string.calc_clear_history), danger = true) { prefs.edit().remove("history").apply() })
        dialogs.sheet(getString(R.string.calc_history), items)
    }
}
