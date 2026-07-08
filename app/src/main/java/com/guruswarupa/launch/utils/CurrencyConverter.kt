package com.guruswarupa.launch.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executors

object CurrencyConverter {
    private const val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private const val CACHE_KEY_PREFIX = "currency_rates_"
    private const val CACHE_TIME_PREFIX = "currency_rates_time_"
    private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun convert(
        context: Context,
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        onComplete: (BigDecimal?, Boolean) -> Unit
    ) {
        if (fromCurrency == toCurrency) {
            onComplete(amount, false)
            return
        }

        val cachedRates = getCachedRates(context, fromCurrency)
        if (cachedRates != null && isCacheFresh(context, fromCurrency)) {
            onComplete(applyRate(amount, cachedRates, toCurrency), false)
            return
        }

        executor.execute {
            try {
                val liveRates = fetchRates(fromCurrency)
                saveRates(context, fromCurrency, liveRates)
                val result = applyRate(amount, liveRates, toCurrency)
                handler.post { onComplete(result, false) }
            } catch (_: Exception) {
                val fallbackResult = cachedRates?.let { applyRate(amount, it, toCurrency) }
                handler.post { onComplete(fallbackResult, fallbackResult != null) }
            }
        }
    }

    private fun applyRate(amount: BigDecimal, rates: Map<String, BigDecimal>, toCurrency: String): BigDecimal? {
        val rate = rates[toCurrency] ?: return null
        return amount.multiply(rate).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    private fun fetchRates(baseCurrency: String): Map<String, BigDecimal> {
        val response = NetworkUtils.readTextFromUrl("https://open.er-api.com/v6/latest/$baseCurrency")
        val json = JSONObject(response)
        if (json.optString("result") != "success") {
            throw IllegalStateException("Currency rate fetch failed")
        }
        val ratesJson = json.getJSONObject("rates")
        val rates = LinkedHashMap<String, BigDecimal>()
        val keys = ratesJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            rates[key] = ratesJson.get(key).toString().toBigDecimal()
        }
        return rates
    }

    private fun saveRates(context: Context, baseCurrency: String, rates: Map<String, BigDecimal>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        rates.forEach { (currency, rate) ->
            json.put(currency, rate.toPlainString())
        }
        prefs.edit {
            putString("$CACHE_KEY_PREFIX$baseCurrency", json.toString())
            putLong("$CACHE_TIME_PREFIX$baseCurrency", System.currentTimeMillis())
        }
    }

    private fun getCachedRates(context: Context, baseCurrency: String): Map<String, BigDecimal>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString("$CACHE_KEY_PREFIX$baseCurrency", null) ?: return null
        return try {
            val json = JSONObject(serialized)
            val rates = LinkedHashMap<String, BigDecimal>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                rates[key] = json.getString(key).toBigDecimal()
            }
            rates
        } catch (_: Exception) {
            null
        }
    }

    private fun isCacheFresh(context: Context, baseCurrency: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong("$CACHE_TIME_PREFIX$baseCurrency", 0L)
        return cachedAt > 0L && System.currentTimeMillis() - cachedAt < CACHE_MAX_AGE_MS
    }
}
