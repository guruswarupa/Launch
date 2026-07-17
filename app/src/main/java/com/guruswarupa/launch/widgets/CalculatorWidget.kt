package com.guruswarupa.launch.widgets

import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.CalculatorHistoryItem
import com.guruswarupa.launch.ui.adapters.CalculatorHistoryAdapter
import com.guruswarupa.launch.utils.CurrencyConverter
import com.guruswarupa.launch.utils.NumberBaseConverter
import com.guruswarupa.launch.utils.UnitConverter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

enum class CalculatorMode {
    BASIC, SCIENTIFIC, CONVERTER
}

class CalculatorWidget(private val rootView: View) {
    private val display: EditText? = rootView.findViewById(R.id.calculator_display)
    private val historySection: LinearLayout? = rootView.findViewById(R.id.history_section)
    private val historyRecyclerView: RecyclerView? = rootView.findViewById(R.id.history_recycler_view)
    private val historyAdapter: CalculatorHistoryAdapter


    private val scientificPanel: GridLayout? = rootView.findViewById(R.id.scientific_panel)
    private val converterPanel: LinearLayout? = rootView.findViewById(R.id.converter_panel)
    private val unitConverterSection: LinearLayout? = rootView.findViewById(R.id.unit_converter_section)
    private val baseConverterSection: LinearLayout? = rootView.findViewById(R.id.base_converter_section)
    private val buttonGrid: GridLayout? = rootView.findViewById(R.id.button_grid)


    private val converterCategory: Spinner? = rootView.findViewById(R.id.converter_category)
    private val converterInput: EditText? = rootView.findViewById(R.id.converter_input)
    private val converterFromUnit: Spinner? = rootView.findViewById(R.id.converter_from_unit)
    private val converterToUnit: Spinner? = rootView.findViewById(R.id.converter_to_unit)
    private val converterResult: TextView? = rootView.findViewById(R.id.converter_result)
    private val baseInput: EditText? = rootView.findViewById(R.id.base_input)
    private val baseResult: TextView? = rootView.findViewById(R.id.base_result)
    private val isBound = listOf(
        display,
        historySection,
        historyRecyclerView,
        scientificPanel,
        converterPanel,
        unitConverterSection,
        baseConverterSection,
        buttonGrid,
        converterCategory,
        converterInput,
        converterFromUnit,
        converterToUnit,
        converterResult,
        baseInput,
        baseResult
    ).all { it != null }

    private var currentInput = "0"
    private var previousInput = ""
    private var operation: String? = null
    private var shouldResetDisplay = false
    private var isHistoryVisible = false
    private var currentMode = CalculatorMode.BASIC
    private var isInRadians = true
    private var selectedInputBase = 10
    private var currencyRequestId = 0


    private val unitCategories = listOf("Length", "Area", "Temperature", "Volume", "Mass", "Data", "Speed", "Time", "Currency")
    private val unitMap = mapOf(
        "Length" to listOf("mm", "cm", "m", "km", "in", "ft", "yd", "mi"),
        "Area" to listOf("mm²", "cm²", "m²", "km²", "in²", "ft²", "yd²", "ac", "ha"),
        "Temperature" to listOf("C", "F", "K"),
        "Volume" to listOf("ml", "l", "m³", "fl oz", "cup", "pt", "qt", "gal"),
        "Mass" to listOf("mg", "g", "kg", "oz", "lb", "t"),
        "Data" to listOf("B", "KB", "MB", "GB", "TB"),
        "Speed" to listOf("m/s", "km/h", "mph", "ft/s", "knot"),
        "Time" to listOf("ms", "s", "min", "h", "d", "wk"),
        "Currency" to listOf("USD", "EUR", "GBP", "JPY", "INR", "AUD", "CAD", "CHF", "CNY", "SGD", "AED", "NZD")
    )

    init {
        val historyItems = mutableListOf<CalculatorHistoryItem>()
        historyAdapter = CalculatorHistoryAdapter(historyItems) { item ->
            currentInput = item.result
            shouldResetDisplay = true
            updateDisplay()
        }

        if (isBound) {
            historyRecyclerView?.layoutManager = LinearLayoutManager(rootView.context)
            historyRecyclerView?.adapter = historyAdapter

            display?.setOnClickListener {
                toggleHistory()
            }

            setupModeSwitcher()
            setupConverter()
            setupButtons()
            setMode(CalculatorMode.BASIC)
        }
    }

    private fun setupModeSwitcher() {
        rootView.findViewById<Button?>(R.id.btn_mode_basic)?.setOnClickListener {
            setMode(CalculatorMode.BASIC)
        }
        rootView.findViewById<Button?>(R.id.btn_mode_scientific)?.setOnClickListener {
            setMode(CalculatorMode.SCIENTIFIC)
        }
        rootView.findViewById<Button?>(R.id.btn_mode_converter)?.setOnClickListener {
            setMode(CalculatorMode.CONVERTER)
        }
    }

    private fun setMode(mode: CalculatorMode) {
        if (!isBound) return
        currentMode = mode
        scientificPanel?.visibility = if (mode == CalculatorMode.SCIENTIFIC) View.VISIBLE else View.GONE
        converterPanel?.visibility = if (mode == CalculatorMode.CONVERTER) View.VISIBLE else View.GONE


        if (mode == CalculatorMode.CONVERTER) {
            display?.visibility = View.GONE
            buttonGrid?.visibility = View.GONE
        } else {
            display?.visibility = View.VISIBLE
            buttonGrid?.visibility = View.VISIBLE
        }


        rootView.findViewById<Button?>(R.id.btn_mode_basic)?.isSelected = mode == CalculatorMode.BASIC
        rootView.findViewById<Button?>(R.id.btn_mode_scientific)?.isSelected = mode == CalculatorMode.SCIENTIFIC
        rootView.findViewById<Button?>(R.id.btn_mode_converter)?.isSelected = mode == CalculatorMode.CONVERTER
    }

    private fun setupConverter() {
        if (!isBound) return

        rootView.findViewById<Button?>(R.id.btn_converter_unit)?.setOnClickListener {
            showUnitConverter()
        }
        rootView.findViewById<Button?>(R.id.btn_converter_base)?.setOnClickListener {
            showBaseConverter()
        }


        val categoryAdapter = ArrayAdapter(rootView.context, android.R.layout.simple_spinner_item, unitCategories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        converterCategory?.adapter = categoryAdapter

        converterCategory?.setSelection(0)
        updateUnitSpinners(unitCategories[0])

        converterCategory?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateUnitSpinners(unitCategories[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }


        showUnitConverter()


        converterInput?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) performUnitConversion()
        }

        converterFromUnit?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                performUnitConversion()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        converterToUnit?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                performUnitConversion()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }


        rootView.findViewById<Button?>(R.id.btn_input_decimal)?.setOnClickListener {
            selectedInputBase = 10
            updateInputBaseButtons()
            performBaseConversion()
        }
        rootView.findViewById<Button?>(R.id.btn_input_binary)?.setOnClickListener {
            selectedInputBase = 2
            updateInputBaseButtons()
            performBaseConversion()
        }
        rootView.findViewById<Button?>(R.id.btn_input_hex)?.setOnClickListener {
            selectedInputBase = 16
            updateInputBaseButtons()
            performBaseConversion()
        }
        rootView.findViewById<Button?>(R.id.btn_input_octal)?.setOnClickListener {
            selectedInputBase = 8
            updateInputBaseButtons()
            performBaseConversion()
        }


        baseInput?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) performBaseConversion()
        }

        rootView.findViewById<Button?>(R.id.btn_base_decimal)?.setOnClickListener {
            convertToBase(10)
        }
        rootView.findViewById<Button?>(R.id.btn_base_binary)?.setOnClickListener {
            convertToBase(2)
        }
        rootView.findViewById<Button?>(R.id.btn_base_hex)?.setOnClickListener {
            convertToBase(16)
        }
        rootView.findViewById<Button?>(R.id.btn_base_octal)?.setOnClickListener {
            convertToBase(8)
        }


        updateInputBaseButtons()
    }

    private fun updateUnitSpinners(category: String) {
        if (!isBound) return
        val units = unitMap[category] ?: return
        val unitAdapter = ArrayAdapter(rootView.context, android.R.layout.simple_spinner_item, units)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        converterFromUnit?.adapter = unitAdapter
        converterToUnit?.adapter = unitAdapter
        converterToUnit?.setSelection(if (units.size > 1) 1 else 0)
    }

    private fun performUnitConversion() {
        if (!isBound) return
        val inputText = converterInput?.text?.toString().orEmpty()
        if (inputText.isEmpty()) {
            converterResult?.text = rootView.context.getString(R.string.calculator_zero)
            return
        }

        try {
            val value = BigDecimal(inputText)
            val categorySpinner = converterCategory ?: return
            val fromSpinner = converterFromUnit ?: return
            val toSpinner = converterToUnit ?: return
            val category = unitCategories.getOrNull(categorySpinner.selectedItemPosition) ?: return
            val units = unitMap[category] ?: return
            val fromUnit = units.getOrNull(fromSpinner.selectedItemPosition) ?: return
            val toUnit = units.getOrNull(toSpinner.selectedItemPosition) ?: return

            if (category == "Currency") {
                performCurrencyConversion(value, fromUnit, toUnit)
                return
            }

            val result = when (category) {
                "Length" -> UnitConverter.convertLength(value, fromUnit, toUnit)
                "Area" -> UnitConverter.convertArea(value, fromUnit, toUnit)
                "Temperature" -> UnitConverter.convertTemperature(value, fromUnit, toUnit)
                "Volume" -> UnitConverter.convertVolume(value, fromUnit, toUnit)
                "Mass" -> UnitConverter.convertMass(value, fromUnit, toUnit)
                "Data" -> UnitConverter.convertData(value, fromUnit, toUnit)
                "Speed" -> UnitConverter.convertSpeed(value, fromUnit, toUnit)
                "Time" -> UnitConverter.convertTime(value, fromUnit, toUnit)
                else -> value
            }

            converterResult?.text = rootView.context.getString(R.string.converter_result_format, result.stripTrailingZeros().toPlainString(), toUnit)
        } catch (_: Exception) {
            converterResult?.text = rootView.context.getString(R.string.calculator_error)
        }
    }

    private fun performCurrencyConversion(value: BigDecimal, fromCurrency: String, toCurrency: String) {
        if (!isBound) return
        val requestId = ++currencyRequestId
        converterResult?.text = rootView.context.getString(R.string.converter_loading_rates)
        CurrencyConverter.convert(rootView.context, value, fromCurrency, toCurrency) { result, isCachedFallback ->
            if (requestId != currencyRequestId) return@convert
            if (result == null) {
                converterResult?.text = rootView.context.getString(R.string.converter_rate_unavailable)
                return@convert
            }
            val resultText = rootView.context.getString(
                R.string.converter_result_format,
                result.stripTrailingZeros().toPlainString(),
                toCurrency
            )
            converterResult?.text = if (isCachedFallback) {
                rootView.context.getString(R.string.converter_result_cached_format, resultText)
            } else {
                resultText
            }
        }
    }

    private fun convertToBase(targetBase: Int) {
        if (!isBound) return
        val inputText = baseInput?.text?.toString()?.trim().orEmpty()
        if (inputText.isEmpty()) {
            baseResult?.text = rootView.context.getString(R.string.calculator_zero)
            return
        }

        try {

            val decimalValue = when (selectedInputBase) {
                2 -> NumberBaseConverter.binaryToDecimal(inputText)
                8 -> NumberBaseConverter.octalToDecimal(inputText)
                16 -> NumberBaseConverter.hexToDecimal(inputText)
                10 -> inputText.toLongOrNull() ?: 0L
                else -> 0L
            }


            val result = when (targetBase) {
                2 -> NumberBaseConverter.decimalToBinary(decimalValue)
                8 -> NumberBaseConverter.decimalToOctal(decimalValue)
                16 -> NumberBaseConverter.decimalToHex(decimalValue)
                10 -> decimalValue.toString()
                else -> rootView.context.getString(R.string.calculator_error)
            }
            baseResult?.text = result
        } catch (_: Exception) {
            baseResult?.text = rootView.context.getString(R.string.calculator_error)
        }
    }

    private fun updateInputBaseButtons() {
        if (!isBound) return
        rootView.findViewById<Button?>(R.id.btn_input_decimal)?.isSelected = selectedInputBase == 10
        rootView.findViewById<Button?>(R.id.btn_input_binary)?.isSelected = selectedInputBase == 2
        rootView.findViewById<Button?>(R.id.btn_input_hex)?.isSelected = selectedInputBase == 16
        rootView.findViewById<Button?>(R.id.btn_input_octal)?.isSelected = selectedInputBase == 8
    }

    private fun performBaseConversion() {

        convertToBase(10)
    }

    private fun showUnitConverter() {
        if (!isBound) return
        unitConverterSection?.visibility = View.VISIBLE
        baseConverterSection?.visibility = View.GONE
        rootView.findViewById<Button?>(R.id.btn_converter_unit)?.isSelected = true
        rootView.findViewById<Button?>(R.id.btn_converter_base)?.isSelected = false
    }

    private fun showBaseConverter() {
        if (!isBound) return
        unitConverterSection?.visibility = View.GONE
        baseConverterSection?.visibility = View.VISIBLE
        rootView.findViewById<Button?>(R.id.btn_converter_unit)?.isSelected = false
        rootView.findViewById<Button?>(R.id.btn_converter_base)?.isSelected = true
    }

    private fun setupButtons() {
        if (!isBound) return

        rootView.findViewById<Button?>(R.id.btn_0)?.setOnClickListener { appendNumber("0") }
        rootView.findViewById<Button?>(R.id.btn_1)?.setOnClickListener { appendNumber("1") }
        rootView.findViewById<Button?>(R.id.btn_2)?.setOnClickListener { appendNumber("2") }
        rootView.findViewById<Button?>(R.id.btn_3)?.setOnClickListener { appendNumber("3") }
        rootView.findViewById<Button?>(R.id.btn_4)?.setOnClickListener { appendNumber("4") }
        rootView.findViewById<Button?>(R.id.btn_5)?.setOnClickListener { appendNumber("5") }
        rootView.findViewById<Button?>(R.id.btn_6)?.setOnClickListener { appendNumber("6") }
        rootView.findViewById<Button?>(R.id.btn_7)?.setOnClickListener { appendNumber("7") }
        rootView.findViewById<Button?>(R.id.btn_8)?.setOnClickListener { appendNumber("8") }
        rootView.findViewById<Button?>(R.id.btn_9)?.setOnClickListener { appendNumber("9") }


        rootView.findViewById<Button?>(R.id.btn_add)?.setOnClickListener { setOperation("+") }
        rootView.findViewById<Button?>(R.id.btn_subtract)?.setOnClickListener { setOperation("−") }
        rootView.findViewById<Button?>(R.id.btn_multiply)?.setOnClickListener { setOperation("×") }
        rootView.findViewById<Button?>(R.id.btn_divide)?.setOnClickListener { setOperation("÷") }


        rootView.findViewById<Button?>(R.id.btn_equals)?.setOnClickListener { calculate() }
        rootView.findViewById<Button?>(R.id.btn_decimal)?.setOnClickListener { appendDecimal() }
        rootView.findViewById<Button?>(R.id.btn_clear)?.setOnClickListener { clear() }
        rootView.findViewById<Button?>(R.id.btn_backspace)?.setOnClickListener { backspace() }


        rootView.findViewById<Button?>(R.id.btn_clear_history)?.setOnClickListener { clearHistory() }


        rootView.findViewById<Button?>(R.id.btn_sin)?.setOnClickListener { applyScientificFunction("sin") }
        rootView.findViewById<Button?>(R.id.btn_cos)?.setOnClickListener { applyScientificFunction("cos") }
        rootView.findViewById<Button?>(R.id.btn_tan)?.setOnClickListener { applyScientificFunction("tan") }
        rootView.findViewById<Button?>(R.id.btn_log)?.setOnClickListener { applyScientificFunction("log") }
        rootView.findViewById<Button?>(R.id.btn_ln)?.setOnClickListener { applyScientificFunction("ln") }
        rootView.findViewById<Button?>(R.id.btn_sqrt)?.setOnClickListener { applyScientificFunction("sqrt") }
        rootView.findViewById<Button?>(R.id.btn_power)?.setOnClickListener { setOperation("^") }
        rootView.findViewById<Button?>(R.id.btn_pi)?.setOnClickListener { insertConstant("π") }
        rootView.findViewById<Button?>(R.id.btn_e)?.setOnClickListener { insertConstant("e") }
        rootView.findViewById<Button?>(R.id.btn_factorial)?.setOnClickListener { applyScientificFunction("factorial") }
    }

    private fun applyScientificFunction(func: String) {
        val errorText = rootView.context.getString(R.string.calculator_error)
        if (currentInput == errorText) return

        try {
            val value = currentInput.toDouble()
            val result = when (func) {
                "sin" -> {
                    val rad = if (isInRadians) value else Math.toRadians(value)
                    sin(rad)
                }
                "cos" -> {
                    val rad = if (isInRadians) value else Math.toRadians(value)
                    cos(rad)
                }
                "tan" -> {
                    val rad = if (isInRadians) value else Math.toRadians(value)
                    tan(rad)
                }
                "log" -> if (value > 0) log10(value) else Double.NaN
                "ln" -> if (value > 0) ln(value) else Double.NaN
                "sqrt" -> if (value >= 0) sqrt(value) else Double.NaN
                "factorial" -> {
                    if (value < 0 || value != value.toInt().toDouble()) {
                        Double.NaN
                    } else {
                        var fact = 1.0
                        for (i in 1..value.toInt()) {
                            fact *= i
                        }
                        fact
                    }
                }
                else -> Double.NaN
            }

            if (result.isNaN() || result.isInfinite()) {
                currentInput = errorText
            } else {
                currentInput = BigDecimal(result).stripTrailingZeros().toPlainString()
                historyAdapter.addItem(CalculatorHistoryItem("$func($value)", currentInput))
            }
            shouldResetDisplay = true
            updateDisplay()
        } catch (_: Exception) {
            currentInput = errorText
            updateDisplay()
        }
    }

    private fun insertConstant(constant: String) {
        if (shouldResetDisplay || currentInput == "0") {
            currentInput = when (constant) {
                "π" -> Math.PI.toString()
                "e" -> Math.E.toString()
                else -> currentInput
            }
        } else {
            currentInput += when (constant) {
                "π" -> Math.PI.toString()
                "e" -> Math.E.toString()
                else -> ""
            }
        }
        updateDisplay()
    }

    private fun appendNumber(number: String) {
        if (currentMode == CalculatorMode.CONVERTER) return

        if (shouldResetDisplay) {
            currentInput = "0"
            shouldResetDisplay = false
        }

        if (currentInput == "0") {
            currentInput = number
        } else {
            currentInput += number
        }
        updateDisplay()
    }

    private fun appendDecimal() {
        if (currentMode == CalculatorMode.CONVERTER) return

        if (shouldResetDisplay) {
            currentInput = "0"
            shouldResetDisplay = false
        }

        if (!currentInput.contains(".")) {
            currentInput += "."
            updateDisplay()
        }
    }

    private fun setOperation(op: String) {
        if (currentMode == CalculatorMode.CONVERTER) return

        if (previousInput.isNotEmpty() && operation != null && !shouldResetDisplay) {
            calculate()
        } else {
            previousInput = currentInput
        }

        operation = op
        shouldResetDisplay = true
    }

    private fun calculate() {
        if (currentMode == CalculatorMode.CONVERTER) return
        if (previousInput.isEmpty() || operation == null) {
            return
        }

        val errorText = rootView.context.getString(R.string.calculator_error)

        try {
            val prev = BigDecimal(previousInput)
            val curr = BigDecimal(currentInput)
            val result: BigDecimal

            when (operation) {
                "+" -> result = prev + curr
                "−" -> result = prev - curr
                "×" -> result = prev * curr
                "÷" -> {
                    if (curr.compareTo(BigDecimal.ZERO) == 0) {
                        currentInput = errorText
                        updateDisplay()
                        reset()
                        return
                    }
                    result = prev.divide(curr, 10, RoundingMode.HALF_UP)
                }
                "^" -> {
                    val powResult = prev.toDouble().pow(curr.toDouble())
                    if (powResult.isNaN() || powResult.isInfinite()) {
                        currentInput = errorText
                        updateDisplay()
                        reset()
                        return
                    }
                    val resultStr = BigDecimal(powResult).stripTrailingZeros().toPlainString()
                    val expression = buildExpression(previousInput, operation, currentInput)
                    historyAdapter.addItem(CalculatorHistoryItem(expression, resultStr))
                    currentInput = resultStr
                    previousInput = ""
                    operation = null
                    shouldResetDisplay = true
                    updateDisplay()
                    return
                }
                else -> return
            }

            val resultStr = result.stripTrailingZeros().toPlainString()
            val expression = buildExpression(previousInput, operation, currentInput)
            historyAdapter.addItem(CalculatorHistoryItem(expression, resultStr))

            currentInput = resultStr
            previousInput = ""
            operation = null
            shouldResetDisplay = true
            updateDisplay()
        } catch (_: Exception) {
            currentInput = errorText
            updateDisplay()
            reset()
        }
    }

    private fun clear() {
        if (!isBound) return
        val zeroText = rootView.context.getString(R.string.calculator_zero)
        if (currentMode == CalculatorMode.CONVERTER) {
            converterInput?.setText("")
            converterResult?.text = zeroText
            baseInput?.setText("")
            baseResult?.text = zeroText
            return
        }

        currentInput = zeroText
        previousInput = ""
        operation = null
        shouldResetDisplay = false
        updateDisplay()
    }

    private fun backspace() {
        if (currentMode == CalculatorMode.CONVERTER) return
        if (shouldResetDisplay) return

        currentInput = if (currentInput.length > 1) {
            currentInput.dropLast(1)
        } else {
            rootView.context.getString(R.string.calculator_zero)
        }
        updateDisplay()
    }

    private fun reset() {
        previousInput = ""
        operation = null
        shouldResetDisplay = false
    }

    private fun updateDisplay() {
        display?.setText(currentInput)
    }

    private fun buildExpression(prev: String, op: String?, curr: String): String {
        val operationSymbol = when (op) {
            "+" -> "+"
            "−" -> "−"
            "×" -> "×"
            "÷" -> "÷"
            "^" -> "^"
            else -> ""
        }
        return "$prev $operationSymbol $curr"
    }

    private fun toggleHistory() {
        if (!isBound) return
        isHistoryVisible = !isHistoryVisible
        historySection?.visibility = if (isHistoryVisible) View.VISIBLE else View.GONE
    }

    private fun clearHistory() {
        historyAdapter.clearHistory()
    }
}
