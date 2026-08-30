package com.guruswarupa.launch.utils

import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FinanceManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.ui.adapters.Transaction
import com.guruswarupa.launch.ui.adapters.TransactionAdapter
import java.util.Locale
import com.guruswarupa.launch.ui.theme.ThemeManager




class FinanceWidgetManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val financeManager: FinanceManager,
    private val balanceText: TextView,
    private val monthlySpentText: TextView,
    private val amountInput: EditText,
    private val descriptionInput: EditText
) {

    fun setup() {
        TypographyManager.applyToView(balanceText.parent as View)

        activity.findViewById<Button>(R.id.add_income_btn).setOnClickListener {
            addTransaction(true)
        }

        activity.findViewById<Button>(R.id.add_expense_btn).setOnClickListener {
            addTransaction(false)
        }


        balanceText.setOnClickListener {
            showTransactionHistory()
        }


        activity.findViewById<LinearLayout>(R.id.balance_card)?.setOnClickListener {
            showTransactionHistory()
        }

        setupCurrencySpinner()
    }

    private fun setupCurrencySpinner() {
        val spinner = activity.findViewById<Spinner>(R.id.finance_currency_spinner) ?: return

        val currencies = FinanceManager.SUPPORTED_CURRENCIES.map { (code, symbol) ->
            "$code ($symbol)"
        }.toTypedArray()

        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, currencies)

        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        spinner.adapter = adapter


        val currentCurrency = financeManager.getCurrencyCode()
        val index = FinanceManager.SUPPORTED_CURRENCIES.keys.indexOf(currentCurrency)
        if (index >= 0) {
            spinner.setSelection(index)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val codes = FinanceManager.SUPPORTED_CURRENCIES.keys.toList()
                if (position >= 0 && position < codes.size) {
                    val selectedCode = codes[position]
                    if (selectedCode != financeManager.getCurrencyCode()) {
                        financeManager.setCurrency(selectedCode)
                        updateDisplay()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    fun updateDisplay() {
        val currencySymbol = financeManager.getCurrency()
        val balance = financeManager.getBalance()
        val monthlyExpenses = financeManager.getMonthlyExpenses()
        val monthlyIncome = financeManager.getMonthlyIncome()
        val netSavings = monthlyIncome - monthlyExpenses


        balanceText.text = String.format(Locale.getDefault(), "%s%.2f", currencySymbol, balance)


        val netText = if (netSavings >= 0) {
            "This Month: +$currencySymbol${String.format(Locale.getDefault(), "%.2f", netSavings)}"
        } else {
            "This Month: -$currencySymbol${String.format(Locale.getDefault(), "%.2f", kotlin.math.abs(netSavings))}"
        }
        monthlySpentText.text = netText
        monthlySpentText.setTextColor(ThemeManager.color(activity, R.attr.appTextSecondary))
    }

    private fun addTransaction(isIncome: Boolean) {
        val amountText = amountInput.text.toString()
        val description = descriptionInput.text.toString().trim()

        if (amountText.isNotEmpty()) {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {

                if (isIncome) {
                    financeManager.addIncome(amount, description)
                } else {
                    financeManager.addExpense(amount, description)
                }


                amountInput.text.clear()
                descriptionInput.text.clear()

                updateDisplay()

                val currencySymbol = financeManager.getCurrency()
                val action = if (isIncome) "Income" else "Expense"
                val message = if (description.isNotEmpty()) {
                    "$action of $currencySymbol$amount added: $description"
                } else {
                    "$action of $currencySymbol$amount added"
                }
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_please_enter_a_valid_amount), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, activity.getString(R.string.toast_please_enter_an_amount), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTransactionHistory() {
        val currencySymbol = financeManager.getCurrency()


        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_transaction_history, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.transaction_recycler_view)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)
        val clearAllButton = dialogView.findViewById<ImageButton>(R.id.clear_all_transactions_button)

        recyclerView.layoutManager = LinearLayoutManager(activity)

        fun getLatestTransactions(): MutableList<Transaction> {
            val allPrefs = sharedPreferences.all
            val list = mutableListOf<Transaction>()
            allPrefs.keys.filter { it.startsWith("transaction_") }.forEach { key ->
                val transactionData = sharedPreferences.getString(key, "") ?: ""
                val parts = transactionData.split(":")
                if (parts.size >= 3) {
                    val type = parts[0]
                    val amount = parts[1].toDoubleOrNull() ?: 0.0
                    val timestamp = key.substringAfter("transaction_").toLongOrNull() ?: 0L
                    val description = if (parts.size > 3) parts[3] else ""
                    list.add(Transaction(type, amount, description, timestamp))
                }
            }
            return list.sortedByDescending { it.timestamp }.toMutableList()
        }

        val sortedTransactions = getLatestTransactions()

        val adapter =
            TransactionAdapter(currencySymbol) { transactionToDelete ->
                val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                    .setTitle(activity.getString(R.string.dlg_delete_transaction))
                    .setMessage(activity.getString(R.string.dlg_are_you_sure_you_want_to_delete_this_transaction))
                    .setPositiveButton(activity.getString(R.string.delete_button)) { _, _ ->
                        financeManager.deleteTransaction(transactionToDelete.timestamp)
                        updateDisplay()

                        val newList = getLatestTransactions()
                        (recyclerView.adapter as TransactionAdapter).updateData(newList)
                        if (newList.isEmpty()) {
                            Toast.makeText(activity, activity.getString(R.string.toast_no_transactions_remaining), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(activity.getString(R.string.cancel_button), null)
                    .show()

                fixDialogTextColors(dialog)
            }
        recyclerView.adapter = adapter
        adapter.updateData(sortedTransactions)

        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        clearAllButton?.setOnClickListener {
            val d = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                .setTitle(activity.getString(R.string.dlg_reset_finance_data))
                .setMessage(activity.getString(R.string.dlg_are_you_sure_you_want_to_reset_all_finance_data))
                .setPositiveButton(activity.getString(R.string.reset)) { _, _ ->
                    financeManager.resetData()
                    updateDisplay()
                    (recyclerView.adapter as TransactionAdapter).updateData(mutableListOf())
                    dialog.dismiss()
                    Toast.makeText(activity, activity.getString(R.string.toast_finance_data_reset_successfully), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(activity.getString(R.string.cancel_button), null)
                .show()

            fixDialogTextColors(d)
        }

        if (sortedTransactions.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.toast_no_transactions_found), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        TypographyManager.applyToView(dialogView)
        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ThemeManager.color(activity, R.attr.appTextPrimary)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }
}