package com.guruswarupa.launch.managers

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class WidgetConfigurationManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val PREF_WIDGET_ORDER = "widget_order"
        private const val PREFS_SYSTEM_WIDGETS_KEY = "saved_widgets"
        private const val REMOVED_NOTIFICATIONS_WIDGET_ID = "notifications_widget_container"

        val IN_APP_WIDGETS = listOf(
            WidgetInfo("media_controller_widget_container", "Media Controller", false),
            WidgetInfo("calendar_events_widget_container", "Calendar Events", false),
            WidgetInfo("countdown_widget_container", "Countdown", false),
            WidgetInfo("dns_widget_container", "DNS Provider", false),
            WidgetInfo("physical_activity_widget_container", "Physical Activity", false),
            WidgetInfo("compass_widget_container", "Compass", false),
            WidgetInfo("pressure_widget_container", "Pressure", false),
            WidgetInfo("temperature_widget_container", "Temperature", false),
            WidgetInfo("weather_forecast_widget_container", "Weather Forecast", false),
            WidgetInfo("noise_decibel_widget_container", "Noise Decibel Analyzer", false),
            WidgetInfo("workout_widget_container", "Workout Tracker", false),
            WidgetInfo("calculator_widget_container", "Calculator", false),
            WidgetInfo("todo_recycler_view", "Todo List", false),
            WidgetInfo("note_widget_container", "Notes", false),
            WidgetInfo("battery_health_widget_container", "Battery Health", false),
            WidgetInfo("finance_widget", "Finance Tracker", false),
            WidgetInfo("weekly_usage_widget", "Weekly Usage", false),
            WidgetInfo("network_stats_widget_container", "Network Stats", false),
            WidgetInfo("device_info_widget_container", "Device Info", false),
            WidgetInfo("year_progress_widget_container", "Year Progress", false),
            WidgetInfo("github_contributions_widget_container", "GitHub Contributions", false)
        )
    }


    @Volatile
    private var cachedWidgetConfiguration: List<WidgetInfo>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_VALID_DURATION = 60 * 1000L

    data class WidgetInfo(
        val id: String,
        val name: String,
        val enabled: Boolean,
        val isSystemWidget: Boolean = false,
        val providerPackage: String? = null,
        val providerClass: String? = null,
        val appWidgetId: Int? = null,
        val isProvider: Boolean = false,
        val appName: String? = null,
        val customHeightDp: Int? = null
    )

    fun getWidgetConfiguration(): List<WidgetInfo> {

        val currentTime = System.currentTimeMillis()
        cachedWidgetConfiguration?.let { cached ->
            if (currentTime - cacheTimestamp < CACHE_VALID_DURATION) {
                return cached
            }
        }

        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null)
        val savedWidgetsList = mutableListOf<WidgetInfo>()

        if (orderJson != null) {
            try {
                val jsonArray = JSONArray(orderJson)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val enabled = jsonObject.getBoolean("enabled")
                    val isSystem = jsonObject.optBoolean("isSystemWidget", id.startsWith("system_widget_"))
                    val pkg = jsonObject.optString("providerPackage").takeIf { it.isNotEmpty() }
                    val cls = jsonObject.optString("providerClass").takeIf { it.isNotEmpty() }
                    val widgetId = if (jsonObject.has("appWidgetId")) jsonObject.getInt("appWidgetId") else null

                    if (id != REMOVED_NOTIFICATIONS_WIDGET_ID) {
                        val customHeightDp = if (jsonObject.has("customHeightDp")) jsonObject.optInt("customHeightDp") else null
                        savedWidgetsList.add(
                            WidgetInfo(
                                id,
                                name,
                                enabled,
                                isSystem,
                                pkg,
                                cls,
                                widgetId,
                                appName = getAppName(pkg),
                                customHeightDp = customHeightDp
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val boundSystemWidgets = getBoundSystemWidgets()
        val boundIds = boundSystemWidgets.map { it.id }.toSet()


        val seenIds = mutableSetOf<String>()
        val deduplicatedSaved = savedWidgetsList.filter { widget ->
            if (seenIds.contains(widget.id)) {
                false
            } else {
                seenIds.add(widget.id)
                true
            }
        }.toMutableList()


        if (deduplicatedSaved.size != savedWidgetsList.size) {
            saveWidgetOrder(deduplicatedSaved)
        }

        val result = deduplicatedSaved.filter { !it.isSystemWidget || boundIds.contains(it.id) }.toMutableList()

        // Sync relative order of existing system widgets with the drawer order (boundSystemWidgets)
        val existingSystemIds = result.filter { it.isSystemWidget }.map { it.id }.toSet()
        val orderedSystemWidgets = boundSystemWidgets.filter { it.id in existingSystemIds }
        
        var systemInsertionIdx = 0
        for (i in result.indices) {
            if (result[i].isSystemWidget) {
                if (systemInsertionIdx < orderedSystemWidgets.size) {
                    result[i] = orderedSystemWidgets[systemInsertionIdx++]
                }
            }
        }

        // Add new bound system widgets that weren't in the saved order yet
        val currentIds = result.map { it.id }.toSet()
        
        boundSystemWidgets.forEach { systemWidget ->
            if (!currentIds.contains(systemWidget.id)) {
                // Add new system widgets to the end of the list.
                result.add(systemWidget)
            }
        }

        IN_APP_WIDGETS.forEach { defaultWidget ->
            if (!currentIds.contains(defaultWidget.id)) {
                result.add(defaultWidget)
            }
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val installedProviders = try { appWidgetManager.installedProviders } catch (_: Exception) { emptyList() }

        val boundProviders = boundSystemWidgets.map { "${it.providerPackage}/${it.providerClass}" }.toSet()

        installedProviders.forEach { provider ->
            val providerKey = "${provider.provider.packageName}/${provider.provider.className}"
            if (!boundProviders.contains(providerKey)) {
                val providerId = "provider_${provider.provider.packageName}_${provider.provider.className}"
                if (!currentIds.contains(providerId)) {
                    val label = provider.loadLabel(context.packageManager)
                    val pkg = provider.provider.packageName
                    result.add(WidgetInfo(
                        id = providerId,
                        name = label,
                        enabled = false,
                        isSystemWidget = true,
                        providerPackage = pkg,
                        providerClass = provider.provider.className,
                        isProvider = true,
                        appName = getAppName(pkg)
                    ))
                }
            }
        }

        val enabledWidgets = result.filter { it.enabled }

        val customDisabled = result.filter { !it.enabled && !it.isSystemWidget }.sortedBy { it.name }


        val systemDisabled = result.filter { !it.enabled && it.isSystemWidget && !it.isProvider }
            .sortedWith(
                compareBy<WidgetInfo> { it.appName ?: "" }
                    .thenBy { it.name }
            )


        val providers = result.filter { it.isProvider }
            .sortedWith(
                compareBy<WidgetInfo> { it.appName ?: "" }
                    .thenBy { it.name }
            )

        val finalResult = enabledWidgets + customDisabled + systemDisabled + providers


        cachedWidgetConfiguration = finalResult
        cacheTimestamp = System.currentTimeMillis()

        return finalResult
    }

    private fun getAppName(packageName: String?): String? {
        if (packageName == null) return null
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun getWidgetOrder(): List<WidgetInfo> {
        return getWidgetConfiguration().filter { !it.isProvider }
    }


    fun forceRefresh() {
        invalidateCache()
        getWidgetOrder()
    }

    private fun getBoundSystemWidgets(): List<WidgetInfo> {
        val widgetsJson = sharedPreferences.getString(PREFS_SYSTEM_WIDGETS_KEY, null) ?: return emptyList()
        val systemWidgets = mutableListOf<WidgetInfo>()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        try {
            val jsonArray = JSONArray(widgetsJson)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val appWidgetId = json.getInt("appWidgetId")
                val packageName = json.optString("providerPackage", "")
                val className = json.optString("providerClass", "")

                val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (info != null) {
                    val id = "system_widget_$appWidgetId"
                    val label = info.loadLabel(context.packageManager)
                    systemWidgets.add(WidgetInfo(
                        id = id,
                        name = label,
                        enabled = true,
                        isSystemWidget = true,
                        providerPackage = packageName,
                        providerClass = className,
                        appWidgetId = appWidgetId,
                        appName = getAppName(packageName),
                        customHeightDp = if (json.has("customHeightDp")) json.optInt("customHeightDp") else null
                    ))
                }
            }
        } catch (_: Exception) {}
        return systemWidgets
    }

    fun saveWidgetOrder(widgets: List<WidgetInfo>) {
        val jsonArray = JSONArray()
        widgets.forEach { widget ->
            if (!widget.isProvider && widget.id != REMOVED_NOTIFICATIONS_WIDGET_ID) {
                val jsonObject = JSONObject()
                jsonObject.put("id", widget.id)
                jsonObject.put("name", widget.name)
                jsonObject.put("enabled", widget.enabled)
                jsonObject.put("isSystemWidget", widget.isSystemWidget)
                widget.providerPackage?.let { jsonObject.put("providerPackage", it) }
                widget.providerClass?.let { jsonObject.put("providerClass", it) }
                widget.appWidgetId?.let { jsonObject.put("appWidgetId", it) }
                widget.customHeightDp?.let { jsonObject.put("customHeightDp", it) }
                jsonArray.put(jsonObject)
            }
        }
        sharedPreferences.edit {
            putString(PREF_WIDGET_ORDER, jsonArray.toString())
        }
        invalidateCache()
    }

    fun isWidgetEnabled(widgetId: String): Boolean {
        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null) ?: return false
        try {
            val jsonArray = JSONArray(orderJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                if (jsonObject.getString("id") == widgetId) {
                    return jsonObject.getBoolean("enabled")
                }
            }
        } catch (_: Exception) {}
        return false
    }


    fun invalidateCache() {
        cachedWidgetConfiguration = null
        cacheTimestamp = 0
    }
}
