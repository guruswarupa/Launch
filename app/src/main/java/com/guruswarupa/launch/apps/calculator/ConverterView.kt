package com.guruswarupa.launch.apps.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.FilesDialogs
import com.guruswarupa.launch.ui.activities.FilesPalette
import com.guruswarupa.launch.ui.activities.SheetItem
import com.guruswarupa.launch.ui.activities.withAlpha
import com.guruswarupa.launch.utils.CurrencyConverter
import com.guruswarupa.launch.utils.UnitConverter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Unit, currency and number-base converter with its own keypad, same engines as the home-screen calculator widget. */
internal class ConverterView(
    context: Context,
    private val p: FilesPalette,
    private val dialogs: FilesDialogs
) : LinearLayout(context) {

    private class Cat(val key: String, val labelRes: Int, val units: List<String>, val from: String, val to: String)

    private val cats = listOf(
        Cat("Length", R.string.conv_length, listOf("mm", "cm", "m", "km", "in", "ft", "yd", "mi"), "m", "ft"),
        Cat("Area", R.string.conv_area, listOf("mm²", "cm²", "m²", "km²", "in²", "ft²", "yd²", "ac", "ha"), "m²", "ft²"),
        Cat("Temperature", R.string.conv_temperature, listOf("C", "F", "K"), "C", "F"),
        Cat("Volume", R.string.conv_volume, listOf("ml", "l", "m³", "fl oz", "cup", "pt", "qt", "gal"), "l", "gal"),
        Cat("Mass", R.string.conv_mass, listOf("mg", "g", "kg", "oz", "lb", "t"), "kg", "lb"),
        Cat("Data", R.string.conv_data, listOf("B", "KB", "MB", "GB", "TB"), "GB", "MB"),
        Cat("Speed", R.string.conv_speed, listOf("m/s", "km/h", "mph", "ft/s", "knot"), "km/h", "mph"),
        Cat("Time", R.string.conv_time, listOf("ms", "s", "min", "h", "d", "wk"), "h", "min"),
        Cat("Currency", R.string.conv_currency, listOf("USD", "EUR", "GBP", "JPY", "INR", "AUD", "CAD", "CHF", "CNY", "SGD", "AED", "NZD"), "USD", "EUR"),
        Cat("Base", R.string.conv_base, listOf("BIN", "OCT", "DEC", "HEX"), "DEC", "HEX")
    )
    private val baseOf = mapOf("BIN" to 2, "OCT" to 8, "DEC" to 10, "HEX" to 16)

    private var cat = cats[0]
    private var from = cat.from
    private var to = cat.to
    private val input = StringBuilder()
    private var request = 0

    private val chipRow = LinearLayout(context)
    private lateinit var fromUnit: TextView
    private lateinit var toUnit: TextView
    private lateinit var inputView: TextView
    private lateinit var resultView: TextView
    private lateinit var noteView: TextView
    private lateinit var toCard: LinearLayout
    private lateinit var baseCard: LinearLayout
    private val baseValues = HashMap<String, TextView>()
    private lateinit var hexRow: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int = p.text, bold: Boolean = false) = TextView(context).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(4), dp(14), dp(12))

        chipRow.setPadding(0, 0, 0, dp(10))
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) })

        // From
        val fromCard = card()
        fromUnit = unitChip { pickUnit(true) }
        inputView = TextView(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; setTextColor(p.text); maxLines = 1
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 22, 44, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnLongClickListener { copy(input.toString()); true }
        }
        fromCard.addView(fromUnit)
        fromCard.addView(inputView, LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(12) })
        addView(fromCard, LayoutParams(MATCH_PARENT, dp(76)))

        // Swap
        addView(label("⇅", 22f, p.accent, true).apply {
            gravity = Gravity.CENTER; background = dialogs.rounded(withAlpha(p.accent, 40), 20)
            contentDescription = context.getString(R.string.conv_swap)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); swap() }
        }, LayoutParams(dp(44), dp(36)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(6); bottomMargin = dp(6) })

        // To (units / currency)
        toCard = card()
        toUnit = unitChip { pickUnit(false) }
        resultView = TextView(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; setTextColor(p.accent); maxLines = 1
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 22, 44, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnLongClickListener { copy(resultView.text.toString()); true }
        }
        toCard.addView(toUnit)
        toCard.addView(resultView, LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(12) })
        addView(toCard, LayoutParams(MATCH_PARENT, dp(76)))

        // To (number base: all four at once)
        baseCard = LinearLayout(context).apply {
            orientation = VERTICAL; background = dialogs.rounded(p.card, 20); setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        listOf("BIN", "OCT", "DEC", "HEX").forEach { name ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            row.addView(label(name, 13f, p.text2, true), LayoutParams(dp(48), WRAP_CONTENT))
            val v = label("", 17f).apply { gravity = Gravity.END; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
            v.setOnClickListener { copy(v.text.toString()) }
            baseValues[name] = v
            row.addView(v, LayoutParams(0, WRAP_CONTENT, 1f))
            baseCard.addView(row)
        }
        baseCard.visibility = GONE
        addView(baseCard, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        noteView = label("", 12f, p.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        addView(noteView)

        addView(View(context), LayoutParams(MATCH_PARENT, 0, 1f))

        // Keypad
        hexRow = LinearLayout(context)
        "ABCDEF".forEach { c -> hexRow.addView(key(c.toString(), p.accent) { type(c.toString()) }, LayoutParams(0, MATCH_PARENT, 1f)) }
        hexRow.visibility = GONE
        addView(hexRow, LayoutParams(MATCH_PARENT, dp(46)))
        fun row(vararg k: View) = LinearLayout(context).apply { k.forEach { addView(it, LayoutParams(0, MATCH_PARENT, 1f)) } }
        val rows = listOf(
            row(key("7") { type("7") }, key("8") { type("8") }, key("9") { type("9") }, key("⌫", p.accent) { back() }.also { it.setOnLongClickListener { input.clear(); update(); true } }),
            row(key("4") { type("4") }, key("5") { type("5") }, key("6") { type("6") }, key("AC", p.accent) { input.clear(); update() }),
            row(key("1") { type("1") }, key("2") { type("2") }, key("3") { type("3") }, key("±", p.accent) { sign() }),
            row(key("0") { type("0") }, key(".") { type(".") }, key("00") { type("00") }, key(context.getString(R.string.files_copy), p.accent) { copy(if (cat.key == "Base") baseValues[to]?.text.toString() else resultView.text.toString()) })
        )
        (rows[3] as LinearLayout).getChildAt(3).let { (it as TextView).textSize = 14f }
        rows.forEach { addView(it, LayoutParams(MATCH_PARENT, dp(56))) }

        cats.forEach { c ->
            chipRow.addView(label(context.getString(c.labelRes), 13.5f, p.text2, true).apply {
                tag = c; setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { choose(c) }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        choose(cats[0])
    }

    private fun card() = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL; background = dialogs.rounded(p.card, 20); setPadding(dp(14), 0, dp(18), 0)
    }

    private fun unitChip(onClick: () -> Unit) = label("", 16f, p.accent, true).apply {
        gravity = Gravity.CENTER; minWidth = dp(76)
        background = dialogs.rounded(withAlpha(p.accent, 40), 16); setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

    private fun key(text: String, color: Int = p.text, onClick: () -> Unit) = label(text, 21f, color).apply {
        gravity = Gravity.CENTER
        background = dialogs.rounded(withAlpha(p.text, 18), 16)
        layoutParams = LayoutParams(0, MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    }

    private fun choose(c: Cat) {
        cat = c; from = c.from; to = c.to; input.clear()
        for (i in 0 until chipRow.childCount) {
            val t = chipRow.getChildAt(i) as TextView
            val on = t.tag == c
            t.background = if (on) dialogs.rounded(withAlpha(p.accent, 50), 18) else dialogs.rounded(withAlpha(p.text, 18), 18)
            t.setTextColor(if (on) p.accent else p.text2)
        }
        val isBase = c.key == "Base"
        toCard.visibility = if (isBase) GONE else VISIBLE
        baseCard.visibility = if (isBase) VISIBLE else GONE
        update()
    }

    private fun pickUnit(isFrom: Boolean) {
        dialogs.sheet(context.getString(cat.labelRes), cat.units.map { u ->
            SheetItem(R.drawable.ic_f_move, u, checked = u == (if (isFrom) from else to)) {
                if (isFrom) from = u else to = u
                if (cat.key == "Base" && isFrom) input.clear()
                update()
            }
        })
    }

    private fun swap() {
        if (cat.key == "Base") return
        val t = from; from = to; to = t
        // Carry the result over so a swap keeps the number on screen.
        val r = resultView.text.toString()
        if (r.isNotEmpty() && r != "—" && r.toBigDecimalOrNull() != null) { input.clear(); input.append(r) }
        update()
    }

    private fun validChar(c: String): Boolean {
        if (cat.key != "Base") return true
        val base = baseOf[from] ?: 10
        if (c == ".") return false
        return c.all { Character.digit(it, base) >= 0 }
    }

    private fun type(c: String) {
        if (!validChar(c)) return
        if (c == "." && (input.contains('.'))) return
        if (c == "." && input.isEmpty()) input.append("0")
        if (input.length >= 20) return
        if (input.toString() == "0" && c != ".") input.clear()
        if (input.toString() == "-0" && c != ".") input.setLength(1)
        input.append(c)
        update()
    }

    private fun back() { if (input.isNotEmpty()) input.setLength(input.length - 1); if (input.toString() == "-") input.clear(); update() }

    private fun sign() {
        if (input.isEmpty()) { input.append("-"); update(); return }
        if (input.startsWith("-")) input.deleteCharAt(0) else input.insert(0, "-")
        update()
    }

    private fun copy(text: String) {
        if (text.isBlank() || text == "—") return
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("converter", text))
        Toast.makeText(context, context.getString(R.string.calc_copied), Toast.LENGTH_SHORT).show()
    }

    private fun fmt(v: BigDecimal): String {
        val r = v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (r.signum() == 0) "0" else r.toPlainString()
    }

    private fun update() {
        fromUnit.text = from; toUnit.text = to
        inputView.text = if (input.isEmpty()) "0" else input.toString()
        inputView.alpha = if (input.isEmpty()) 0.4f else 1f
        hexRow.visibility = if (cat.key == "Base" && from == "HEX") VISIBLE else GONE
        noteView.text = ""
        val text = input.toString()
        val id = ++request
        if (cat.key == "Base") {
            val base = baseOf[from] ?: 10
            val n = try { if (text.isEmpty() || text == "-") BigInteger.ZERO else BigInteger(text, base) } catch (_: Exception) { null }
            baseOf.forEach { (name, b) -> baseValues[name]?.text = n?.toString(b)?.uppercase() ?: "—" }
            return
        }
        val value = try { if (text.isEmpty() || text == "-" || text == "-0.") BigDecimal.ZERO else BigDecimal(text.trimEnd('.')) } catch (_: Exception) { null }
        if (value == null) { resultView.text = "—"; return }
        if (cat.key == "Currency") {
            resultView.text = "…"
            CurrencyConverter.convert(context, value, from, to) { result, cached ->
                if (id != request) return@convert
                if (result == null) { resultView.text = "—"; noteView.text = context.getString(R.string.converter_rate_unavailable); return@convert }
                resultView.text = fmt(result)
                if (cached) noteView.text = context.getString(R.string.converter_result_cached_format, "").trim()
            }
            return
        }
        val out = when (cat.key) {
            "Length" -> UnitConverter.convertLength(value, from, to)
            "Area" -> UnitConverter.convertArea(value, from, to)
            "Temperature" -> UnitConverter.convertTemperature(value, from, to)
            "Volume" -> UnitConverter.convertVolume(value, from, to)
            "Mass" -> UnitConverter.convertMass(value, from, to)
            "Data" -> UnitConverter.convertData(value, from, to)
            "Speed" -> UnitConverter.convertSpeed(value, from, to)
            "Time" -> UnitConverter.convertTime(value, from, to)
            else -> value
        }
        resultView.text = fmt(out)
    }
}
