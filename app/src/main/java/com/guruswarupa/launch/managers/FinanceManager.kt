package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*

class FinanceManager(private val sharedPreferences: SharedPreferences) {

    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val currentMonth = dateFormat.format(Date())
    private var transactionCounter = 0L

    companion object {
        private const val BALANCE_KEY = "finance_balance"
        private const val CURRENCY_KEY = "finance_currency"
        private const val CENTS_PER_UNIT = 100L

        val SUPPORTED_CURRENCIES = mapOf(
            "USD" to "$",
            "EUR" to "€",
            "GBP" to "£",
            "JPY" to "¥",
            "INR" to "₹",
            "CNY" to "¥",
            "CAD" to "C$",
            "AUD" to "A$",
            "CHF" to "CHF",
            "SEK" to "kr",
            "NOK" to "kr",
            "DKK" to "kr",
            "PLN" to "zł",
            "RUB" to "₽",
            "BRL" to "R$",
            "KRW" to "₩",
            "MXN" to "$",
            "SGD" to "S$",
            "HKD" to "HK$",
            "NZD" to "NZ$"
        )
    }

    fun getCurrency(): String {
        val currencyCode = sharedPreferences.getString(CURRENCY_KEY, "USD") ?: "USD"
        return SUPPORTED_CURRENCIES[currencyCode] ?: "$"
    }

    fun getCurrencyCode(): String {
        return sharedPreferences.getString(CURRENCY_KEY, "USD") ?: "USD"
    }

    fun setCurrency(currencyCode: String) {
        if (SUPPORTED_CURRENCIES.containsKey(currencyCode)) {
            sharedPreferences.edit { putString(CURRENCY_KEY, currencyCode) }
        }
    }

    fun addIncome(amount: Double, description: String = "") {
        val newBalanceCents = getBalanceCents() + amountToCents(amount)
        sharedPreferences.edit { putLong(BALANCE_KEY, newBalanceCents) }

        val monthlyIncomeCents = getMonthlyIncomeCents() + amountToCents(amount)
        sharedPreferences.edit { putLong("finance_income_$currentMonth", monthlyIncomeCents) }

        addTransaction(amount, "income", description)
    }

    fun addExpense(amount: Double, description: String = "") {
        val newBalanceCents = getBalanceCents() - amountToCents(amount)
        sharedPreferences.edit { putLong(BALANCE_KEY, newBalanceCents) }

        val monthlyExpensesCents = getMonthlyExpensesCents() + amountToCents(amount)
        sharedPreferences.edit { putLong("finance_expenses_$currentMonth", monthlyExpensesCents) }

        addTransaction(-amount, "expense", description)
    }

    fun getBalance(): Double = centsToAmount(getBalanceCents())

    fun getMonthlyExpenses(): Double = centsToAmount(getMonthlyExpensesCents())

    fun getMonthlyIncome(): Double = centsToAmount(getMonthlyIncomeCents())

    private fun getBalanceCents(): Long {
        return try {
            sharedPreferences.getLong(BALANCE_KEY, 0L)
        } catch (_: ClassCastException) {
            (sharedPreferences.getFloat(BALANCE_KEY, 0.0f).toDouble() * CENTS_PER_UNIT).toLong()
                .also { sharedPreferences.edit { putLong(BALANCE_KEY, it) } }
        }
    }

    private fun getMonthlyExpensesCents(): Long {
        return try {
            sharedPreferences.getLong("finance_expenses_$currentMonth", 0L)
        } catch (_: ClassCastException) {
            (sharedPreferences.getFloat("finance_expenses_$currentMonth", 0.0f).toDouble() * CENTS_PER_UNIT).toLong()
                .also { sharedPreferences.edit { putLong("finance_expenses_$currentMonth", it) } }
        }
    }

    private fun getMonthlyIncomeCents(): Long {
        return try {
            sharedPreferences.getLong("finance_income_$currentMonth", 0L)
        } catch (_: ClassCastException) {
            (sharedPreferences.getFloat("finance_income_$currentMonth", 0.0f).toDouble() * CENTS_PER_UNIT).toLong()
                .also { sharedPreferences.edit { putLong("finance_income_$currentMonth", it) } }
        }
    }

    private fun amountToCents(amount: Double): Long = java.lang.Math.round(amount * CENTS_PER_UNIT)

    private fun centsToAmount(cents: Long): Double = cents.toDouble() / CENTS_PER_UNIT

    fun addTransaction(amount: Double, type: String, description: String = "") {
        val timestamp = System.currentTimeMillis()
        val transactionKey = "transaction_${timestamp}_$transactionCounter"
        transactionCounter++
        val transactionData = "$type:$amount:$timestamp:$description"
        sharedPreferences.edit { putString(transactionKey, transactionData) }


        cleanupOldTransactions()
    }

    fun deleteTransaction(timestamp: Long) {
        val key = "transaction_$timestamp"
        val transactionData = sharedPreferences.getString(key, "") ?: ""

        if (transactionData.isNotEmpty()) {
            val parts = transactionData.split(":", limit = 4)
            if (parts.size >= 3) {
                val type = parts[0]
                val amount = parts[1].toDoubleOrNull() ?: 0.0


                val absAmountCents = amountToCents(kotlin.math.abs(amount))
                val currentBalanceCents = if (type == "income") {
                    getBalanceCents() - absAmountCents
                } else {
                    getBalanceCents() + absAmountCents
                }
                sharedPreferences.edit { putLong(BALANCE_KEY, currentBalanceCents) }


                val date = Date(timestamp)
                val monthStr = dateFormat.format(date)
                if (type == "income") {
                    val monthlyIncomeCents = getMonthlyIncomeCentsForMonth(monthStr) - absAmountCents
                    sharedPreferences.edit { putLong("finance_income_$monthStr", monthlyIncomeCents) }
                } else {
                    val monthlyExpensesCents = getMonthlyExpensesCentsForMonth(monthStr) - absAmountCents
                    sharedPreferences.edit { putLong("finance_expenses_$monthStr", monthlyExpensesCents) }
                }

                sharedPreferences.edit { remove(key) }
            }
        }
    }

    private fun getMonthlyExpensesCentsForMonth(monthStr: String): Long {
        return try {
            sharedPreferences.getLong("finance_expenses_$monthStr", 0L)
        } catch (_: ClassCastException) {
            (sharedPreferences.getFloat("finance_expenses_$monthStr", 0.0f).toDouble() * CENTS_PER_UNIT).toLong()
        }
    }

    private fun getMonthlyIncomeCentsForMonth(monthStr: String): Long {
        return try {
            sharedPreferences.getLong("finance_income_$monthStr", 0L)
        } catch (_: ClassCastException) {
            (sharedPreferences.getFloat("finance_income_$monthStr", 0.0f).toDouble() * CENTS_PER_UNIT).toLong()
        }
    }

    @Suppress("unused")
    fun getTransactionHistory(): List<Triple<String, Double, String>> {
        val allPrefs = sharedPreferences.all
        val transactionEntries = mutableListOf<Pair<Long, Triple<String, Double, String>>>()

        allPrefs.forEach { (key, value) ->
            if (key.startsWith("transaction_") && value is String) {
                val parts = value.split(":", limit = 4)
                if (parts.size >= 3) {
                    val type = parts[0]
                    val amount = parts[1].toDoubleOrNull() ?: 0.0
                    val timestamp = parts[2].toLongOrNull() ?: 0L
                    val description = if (parts.size > 3) parts[3] else ""
                    transactionEntries.add(timestamp to Triple(type, amount, description))
                }
            }
        }

        return transactionEntries
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun cleanupOldTransactions() {

        val allPrefs = sharedPreferences.all
        val transactionKeys = allPrefs.keys.filter { it.startsWith("transaction_") }

        if (transactionKeys.size > 100) {

            val sortedKeys = transactionKeys.sortedByDescending { key ->
                key.substringAfter("transaction_").toLongOrNull() ?: 0L
            }


            sharedPreferences.edit {
                sortedKeys.drop(100).forEach { key ->
                    remove(key)
                }
            }
        }
    }

    fun resetData() {
        val allPrefs = sharedPreferences.all
        sharedPreferences.edit {
            for (key in allPrefs.keys) {
                if (key.startsWith("finance_") || key.startsWith("transaction_")) {
                    remove(key)
                }
            }
        }
    }
}
