package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.widget.EditText
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FinanceManager
import com.guruswarupa.launch.utils.FinanceWidgetManager




class FinanceWidgetInitializer(
    private val context: Context,
    private val secureStorageManager: com.guruswarupa.launch.core.SecureStorageManager,
    private val delay: Long
) {
    private var onInitializedListener: ((FinanceWidgetManager) -> Unit)? = null




    fun onInitialized(listener: (FinanceWidgetManager) -> Unit): FinanceWidgetInitializer {
        this.onInitializedListener = listener
        return this
    }





    fun initialize(handler: Handler) {
        handler.postDelayed({
            val activity = context as? MainActivity ?: return@postDelayed
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed

            val financePrefs = secureStorageManager.getSecurePrefs(com.guruswarupa.launch.core.SecureStorageManager.FINANCE_PREFS)
            val financeManager = FinanceManager(financePrefs)
            val balanceText = activity.findViewById<TextView>(R.id.balance_text)
            val monthlySpentText = activity.findViewById<TextView>(R.id.monthly_spent_text)
            val amountInput = activity.findViewById<EditText>(R.id.amount_input)
            val descriptionInput = activity.findViewById<EditText>(R.id.description_input)

            if (balanceText != null && monthlySpentText != null &&
                amountInput != null && descriptionInput != null
            ) {
                val manager = FinanceWidgetManager(
                    activity, financePrefs, financeManager,
                    balanceText, monthlySpentText, amountInput, descriptionInput
                )
                manager.setup()
                manager.updateDisplay()
                onInitializedListener?.invoke(manager)
            }
        }, delay)
    }
}
