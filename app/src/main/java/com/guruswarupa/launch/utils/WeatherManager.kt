package com.guruswarupa.launch.utils

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.guruswarupa.launch.R
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@ActivityScoped
class WeatherManager @Inject constructor(@ActivityContext private val context: android.content.Context) {
    private val lifecycleScope = (context as LifecycleOwner).lifecycleScope

    private data class GeocodingResult(
        val latitude: Double,
        val longitude: Double,
        val displayName: String
    )

    private data class CachedWeather(
        val temperature: Int,
        val description: String,
        val weatherCode: Int,
        val timestamp: Long,
        val location: String
    )

    data class ForecastDay(
        val dayLabel: String,
        val weatherCode: Int,
        val highTemperature: Int,
        val lowTemperature: Int
    )

    data class HourlyForecast(
        val timeLabel: String,
        val weatherCode: Int,
        val temperature: Int
    )

    data class ForecastAlert(
        val title: String,
        val detail: String
    )

    data class WeatherForecast(
        val location: String,
        val currentTemperature: Int,
        val currentDescription: String,
        val currentWeatherCode: Int,
        val dailyForecasts: List<ForecastDay>,
        val hourlyForecasts: List<HourlyForecast>,
        val alerts: List<ForecastAlert>
    )

    fun getTemperatureUnit(): String {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        return prefs.getString("weather_temperature_unit", "celsius") ?: "celsius"
    }

    fun getConfiguredLocation(): String? = getStoredLocation()

    private fun saveTemperatureUnit(unit: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString("weather_temperature_unit", unit) }
    }

    private fun getStoredLocation(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        return prefs.getString("weather_stored_location", null)
    }

    private fun saveLocation(location: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        prefs.edit {
            putString("weather_stored_location", location)
            putString("weather_stored_city_name", location)
        }
    }

    private fun getCachedWeather(location: String?): CachedWeather? {
        if (location.isNullOrBlank()) return null
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("weather_cached_timestamp", 0)
        val temperature = prefs.getInt("weather_cached_temperature", Int.MIN_VALUE)
        val description = prefs.getString("weather_cached_description", null)
        val weatherCode = prefs.getInt("weather_cached_weather_code", Int.MIN_VALUE)
        val cachedLocation = prefs.getString("weather_cached_location", null)
        if (timestamp == 0L || temperature == Int.MIN_VALUE || description == null || weatherCode == Int.MIN_VALUE || cachedLocation.isNullOrBlank()) {
            return null
        }
        if (cachedLocation != location) return null
        return CachedWeather(temperature, description, weatherCode, timestamp, cachedLocation)
    }

    private fun saveCachedWeather(temperature: Int, description: String, weatherCode: Int, location: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit {
            putInt("weather_cached_temperature", temperature)
                .putString("weather_cached_description", description)
                .putInt("weather_cached_weather_code", weatherCode)
                .putLong("weather_cached_timestamp", currentTime)
                .putString("weather_cached_location", location)
        }
    }

    private fun isCacheValid(cachedWeather: CachedWeather, maxAgeMinutes: Long = 45): Boolean {
        val currentTime = System.currentTimeMillis()
        val ageMinutes = (currentTime - cachedWeather.timestamp) / (1000 * 60)
        return ageMinutes < maxAgeMinutes
    }

    private fun convertTemperatureToSelectedUnit(temperature: Int): Int = temperature

    private fun getTemperatureFormatString(): String {
        val unit = getTemperatureUnit()
        return if (unit == "fahrenheit") {
            context.getString(R.string.weather_temp_format_fahrenheit)
        } else {
            context.getString(R.string.weather_temp_format_celsius)
        }
    }

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, forcePrompt: Boolean = false) {
        val storedLocation = getStoredLocation()
        val cachedWeather = getCachedWeather(storedLocation)
        if (cachedWeather != null && isCacheValid(cachedWeather)) {
            lifecycleScope.launch {
                val convertedTemp = convertTemperatureToSelectedUnit(cachedWeather.temperature)
                val formatString = getTemperatureFormatString()
                weatherText.text = String.format(formatString, convertedTemp, cachedWeather.description)
                setWeatherIcon(weatherIcon, cachedWeather.weatherCode)
                setupRefreshListeners(weatherIcon, weatherText)
            }
            return
        }

        if (storedLocation.isNullOrBlank()) {
            lifecycleScope.launch {
                weatherText.text = context.getString(R.string.tap_to_enter_location)
                weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                setupRefreshListeners(weatherIcon, weatherText)
            }
            if (forcePrompt && !isSettingDialogVisible) {
                showWeatherSettings(weatherIcon, weatherText)
            }
            return
        }
        fetchWeatherFromOpenMeteo(weatherIcon, weatherText, storedLocation)
    }

    fun fetchWeatherForecast(
        onSuccess: (WeatherForecast) -> Unit,
        onError: () -> Unit
    ) {
        val storedLocation = getStoredLocation()
        if (storedLocation.isNullOrBlank()) {
            lifecycleScope.launch { onError() }
            return
        }
        fetchWeatherForecast(storedLocation, onSuccess, onError)
    }

    fun fetchWeatherForecast(
        location: String,
        onSuccess: (WeatherForecast) -> Unit,
        onError: () -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val forecast = withContext(Dispatchers.IO) {
                    val geocodingResult = geocodeLocation(location) ?: throw Exception("Location not found")
                    val unitParam = if (getTemperatureUnit() == "fahrenheit") "fahrenheit" else "celsius"
                    val lat = String.format(Locale.US, "%.5f", geocodingResult.latitude)
                    val lon = String.format(Locale.US, "%.5f", geocodingResult.longitude)
                    val url =
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&temperature_unit=$unitParam&timezone=auto"
                    val response = NetworkUtils.readTextFromUrl(url)
                    parseForecastResponse(JSONObject(response), geocodingResult.displayName.ifBlank { location })
                }
                saveLocation(forecast.location)
                saveCachedWeather(
                    forecast.currentTemperature,
                    forecast.currentDescription,
                    forecast.currentWeatherCode,
                    forecast.location
                )
                onSuccess(forecast)
            } catch (_: Exception) {
                onError()
            }
        }
    }

    private fun fetchWeatherFromOpenMeteo(weatherIcon: ImageView, weatherText: TextView, location: String) {
        lifecycleScope.launch {
            try {
                val currentWeather = withContext(Dispatchers.IO) {
                    val geocodingResult = geocodeLocation(location) ?: throw Exception("Location not found")
                    val unitParam = if (getTemperatureUnit() == "fahrenheit") "fahrenheit" else "celsius"
                    val lat = String.format(Locale.US, "%.5f", geocodingResult.latitude)
                    val lon = String.format(Locale.US, "%.5f", geocodingResult.longitude)
                    val url =
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&temperature_unit=$unitParam&timezone=auto"
                    val response = NetworkUtils.readTextFromUrl(url)
                    val jsonObject = JSONObject(response)
                    val currentWeather = jsonObject.getJSONObject("current_weather")
                    val temperature = currentWeather.getDouble("temperature").roundToInt()
                    val weatherCode = currentWeather.getInt("weathercode")
                    val description = describeOpenMeteoWeatherCode(weatherCode)
                    val displayName = if (geocodingResult.displayName.isNotBlank()) geocodingResult.displayName else location
                    CurrentWeatherPayload(temperature, weatherCode, description, displayName)
                }

                saveLocation(currentWeather.displayName)
                saveCachedWeather(
                    currentWeather.temperature,
                    currentWeather.description,
                    currentWeather.weatherCode,
                    currentWeather.displayName
                )

                val convertedTemp = convertTemperatureToSelectedUnit(currentWeather.temperature)
                val formatString = getTemperatureFormatString()
                weatherText.text = String.format(formatString, convertedTemp, currentWeather.description)
                setWeatherIcon(weatherIcon, currentWeather.weatherCode)
                setupRefreshListeners(weatherIcon, weatherText)
            } catch (_: Exception) {
                weatherText.text = context.getString(R.string.weather_unavailable)
                weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                setupRefreshListeners(weatherIcon, weatherText)
            }
        }
    }

    private fun geocodeLocation(location: String): GeocodingResult? {
        val encodedLocation = try {
            java.net.URLEncoder.encode(location, "UTF-8")
        } catch (_: Exception) {
            location
        }
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedLocation&count=1"
        val response = NetworkUtils.readTextFromUrl(url)
        val jsonObject = JSONObject(response)
        val results = jsonObject.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val firstResult = results.getJSONObject(0)
        val latitude = firstResult.optDouble("latitude", Double.NaN)
        val longitude = firstResult.optDouble("longitude", Double.NaN)
        val name = firstResult.optString("name", location)
        val country = firstResult.optString("country", "")
        if (latitude.isNaN() || longitude.isNaN()) {
            return null
        }
        val displayName = if (country.isNotBlank()) "$name, $country" else name
        return GeocodingResult(latitude, longitude, displayName)
    }

    private fun parseForecastResponse(jsonObject: JSONObject, displayName: String): WeatherForecast {
        val current = jsonObject.getJSONObject("current")
        val currentTemperature = current.getDouble("temperature_2m").roundToInt()
        val currentWeatherCode = current.getInt("weather_code")
        val currentDescription = describeOpenMeteoWeatherCode(currentWeatherCode)
        val daily = jsonObject.getJSONObject("daily")
        val dailyTimes = daily.getJSONArray("time")
        val dailyCodes = daily.getJSONArray("weather_code")
        val dailyHighs = daily.getJSONArray("temperature_2m_max")
        val dailyLows = daily.getJSONArray("temperature_2m_min")
        val dayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dailyForecasts = buildList {
            for (index in 0 until minOf(dailyTimes.length(), 5)) {
                val date = java.time.LocalDate.parse(dailyTimes.getString(index))
                add(
                    ForecastDay(
                        dayLabel = if (index == 0) context.getString(R.string.weather_today) else date.format(dayFormatter),
                        weatherCode = dailyCodes.getInt(index),
                        highTemperature = dailyHighs.getDouble(index).roundToInt(),
                        lowTemperature = dailyLows.getDouble(index).roundToInt()
                    )
                )
            }
        }
        val hourly = jsonObject.getJSONObject("hourly")
        val hourlyTimes = hourly.getJSONArray("time")
        val hourlyCodes = hourly.getJSONArray("weather_code")
        val hourlyTemps = hourly.getJSONArray("temperature_2m")
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("ha", Locale.getDefault())
        val now = java.time.LocalDateTime.now()
        val hourlyForecasts = buildList {
            for (index in 0 until hourlyTimes.length()) {
                val dateTime = java.time.LocalDateTime.parse(hourlyTimes.getString(index))
                if (dateTime.isBefore(now)) {
                    continue
                }
                add(
                    HourlyForecast(
                        timeLabel = dateTime.format(timeFormatter).replace("AM", "a").replace("PM", "p"),
                        weatherCode = hourlyCodes.getInt(index),
                        temperature = hourlyTemps.getDouble(index).roundToInt()
                    )
                )
                if (size == 6) {
                    break
                }
            }
        }
        return WeatherForecast(
            location = displayName,
            currentTemperature = currentTemperature,
            currentDescription = currentDescription,
            currentWeatherCode = currentWeatherCode,
            dailyForecasts = dailyForecasts,
            hourlyForecasts = hourlyForecasts,
            alerts = buildForecastAlerts(dailyForecasts, hourlyForecasts)
        )
    }

    private fun buildForecastAlerts(
        dailyForecasts: List<ForecastDay>,
        hourlyForecasts: List<HourlyForecast>
    ): List<ForecastAlert> {
        val alerts = mutableListOf<ForecastAlert>()
        if (hourlyForecasts.any { it.weatherCode in 95..99 }) {
            alerts.add(
                ForecastAlert(
                    context.getString(R.string.weather_alert_storm_risk_title),
                    context.getString(R.string.weather_alert_storm_risk_detail)
                )
            )
        }
        if (hourlyForecasts.any { it.weatherCode in 71..77 || it.weatherCode in 85..86 }) {
            alerts.add(
                ForecastAlert(
                    context.getString(R.string.weather_alert_snow_expected_title),
                    context.getString(R.string.weather_alert_snow_expected_detail)
                )
            )
        }
        if (hourlyForecasts.any { it.weatherCode in 51..67 || it.weatherCode in 80..82 }) {
            alerts.add(
                ForecastAlert(
                    context.getString(R.string.weather_alert_rain_ahead_title),
                    context.getString(R.string.weather_alert_rain_ahead_detail)
                )
            )
        }
        val warmThreshold = if (getTemperatureUnit() == "fahrenheit") 90 else 32
        val coldThreshold = if (getTemperatureUnit() == "fahrenheit") 32 else 0
        if (dailyForecasts.any { it.highTemperature >= warmThreshold }) {
            alerts.add(
                ForecastAlert(
                    context.getString(R.string.weather_alert_heat_watch_title),
                    context.getString(R.string.weather_alert_heat_watch_detail)
                )
            )
        }
        if (dailyForecasts.any { it.lowTemperature <= coldThreshold }) {
            alerts.add(
                ForecastAlert(
                    context.getString(R.string.weather_alert_freeze_risk_title),
                    context.getString(R.string.weather_alert_freeze_risk_detail)
                )
            )
        }
        return alerts.take(3)
    }

    fun describeOpenMeteoWeatherCode(code: Int): String {
        return when (code) {
            0 -> context.getString(R.string.weather_condition_clear)
            in 1..3 -> context.getString(R.string.weather_condition_cloudy)
            45, 48 -> context.getString(R.string.weather_condition_fog)
            in 51..67, in 80..82 -> context.getString(R.string.weather_condition_rain)
            in 71..77, in 85..86 -> context.getString(R.string.weather_condition_snow)
            in 95..99 -> context.getString(R.string.weather_condition_thunderstorm)
            else -> context.getString(R.string.weather_condition_cloudy)
        }
    }

    fun getWeatherIconResource(weatherCode: Int): Int {
        return when (weatherCode) {
            0 -> R.drawable.ic_weather_sunny
            in 1..3, 45, 48 -> R.drawable.ic_weather_cloudy
            in 51..67, in 80..82, in 95..99 -> R.drawable.ic_weather_rainy
            in 71..77, in 85..86 -> R.drawable.ic_weather_snowy
            else -> R.drawable.ic_weather_cloudy
        }
    }

    private fun setWeatherIcon(weatherIcon: ImageView, weatherCode: Int) {
        val iconResource = getWeatherIconResource(weatherCode)
        weatherIcon.setImageResource(iconResource)
    }

    private fun setupRefreshListeners(weatherIcon: ImageView, weatherText: TextView) {
        val refreshClickListener = View.OnClickListener {
            val storedLocation = getStoredLocation()
            if (storedLocation.isNullOrBlank()) {
                showWeatherSettings(weatherIcon, weatherText)
            } else {
                fetchWeatherFromOpenMeteo(weatherIcon, weatherText, storedLocation)
            }
        }
        weatherIcon.setOnClickListener(refreshClickListener)
        weatherText.setOnClickListener(refreshClickListener)
    }

    private var isSettingDialogVisible = false

    fun showWeatherSettings(weatherIcon: ImageView? = null, weatherText: TextView? = null) {
        lifecycleScope.launch {
            if (isSettingDialogVisible) return@launch
            isSettingDialogVisible = true
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_weather_settings, null)
            val locationInput = dialogView.findViewById<EditText>(R.id.weather_location_input)
            val celsiusRadio = dialogView.findViewById<android.widget.RadioButton>(R.id.unit_celsius)
            val fahrenheitRadio = dialogView.findViewById<android.widget.RadioButton>(R.id.unit_fahrenheit)

            locationInput.setText(getStoredLocation() ?: "")

            val currentUnit = getTemperatureUnit()
            if (currentUnit == "fahrenheit") {
                fahrenheitRadio.isChecked = true
            } else {
                celsiusRadio.isChecked = true
            }

            val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            builder.setTitle(R.string.weather_settings_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val location = locationInput.text.toString().trim()
                    val selectedUnit = if (fahrenheitRadio.isChecked) "fahrenheit" else "celsius"

                    saveTemperatureUnit(selectedUnit)
                    if (location.isNotEmpty()) {
                        saveLocation(location)
                        if (weatherIcon != null && weatherText != null) {
                            fetchWeatherFromOpenMeteo(weatherIcon, weatherText, location)
                        }
                    }
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    isSettingDialogVisible = false
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    isSettingDialogVisible = false
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    isSettingDialogVisible = false
                }
                .show()
        }
    }

    private data class CurrentWeatherPayload(
        val temperature: Int,
        val weatherCode: Int,
        val description: String,
        val displayName: String
    )
}
