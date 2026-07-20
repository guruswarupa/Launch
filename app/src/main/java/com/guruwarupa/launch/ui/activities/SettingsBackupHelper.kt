package com.guruwarupa.launch.ui.activities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SettingsBackupHelper {

    private const val PHYSICAL_ACTIVITY_PREFS_NAME = "physical_activity_prefs"

    fun exportToUri(context: Context, prefs: SharedPreferences, uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    val j = JSONObject()
                    j.put("main_preferences", sharedPreferencesToJson(prefs))
                    zos.putNextEntry(ZipEntry("settings.json"))
                    zos.write(j.toString(2).toByteArray())
                    zos.closeEntry()
                    val webAppsJson = prefs.getString("web_apps", "[]") ?: "[]"
                    zos.putNextEntry(ZipEntry("webapps.json"))
                    zos.write(webAppsJson.toByteArray())
                    zos.closeEntry()
                    val weatherData = JSONObject().apply {
                        put("location", prefs.getString("weather_stored_location", "") ?: "")
                        put("city_name", prefs.getString("weather_stored_city_name", "") ?: "")
                        put("temperature_unit", prefs.getString("weather_temperature_unit", "celsius") ?: "celsius")
                    }
                    zos.putNextEntry(ZipEntry("weather.json"))
                    zos.write(weatherData.toString(2).toByteArray())
                    zos.closeEntry()

                    val physicalActivityPrefs = context.getSharedPreferences(PHYSICAL_ACTIVITY_PREFS_NAME, Context.MODE_PRIVATE)
                    val physicalActivityJson = JSONObject().apply {
                        put("physical_activity_preferences", sharedPreferencesToJson(physicalActivityPrefs))
                    }
                    zos.putNextEntry(ZipEntry("physical_activity.json"))
                    zos.write(physicalActivityJson.toString(2).toByteArray())
                    zos.closeEntry()

                    try {
                        val notesJson = prefs.getString("note_widget_items", "[]") ?: "[]"
                        zos.putNextEntry(ZipEntry("notes.json"))
                        zos.write(notesJson.toByteArray())
                        zos.closeEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun importFromUri(context: Context, prefs: SharedPreferences, uri: Uri, onImported: () -> Unit) {
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "settings.json" -> {
                                val p = JSONObject(zis.bufferedReader().readText()).optJSONObject("main_preferences")
                                if (p != null) {
                                    prefs.edit {
                                        val stringSetKeys = setOf("favorite_apps", "hidden_apps", "focus_mode_allowed_apps", "locked_apps")
                                        p.keys().forEach { k ->
                                            val v = p.get(k)
                                            if (k in stringSetKeys) {
                                                val set = when (v) {
                                                    is JSONArray -> {
                                                        val s = mutableSetOf<String>()
                                                        for (i in 0 until v.length()) s.add(v.getString(i))
                                                        s
                                                    }
                                                    is String -> {
                                                        if (v.startsWith("[") && v.endsWith("]")) {
                                                            v.substring(1, v.length - 1)
                                                                .split(",")
                                                                .map { it.trim() }
                                                                .filter { it.isNotEmpty() }
                                                                .toSet()
                                                        } else {
                                                            setOf(v)
                                                        }
                                                    }
                                                    else -> emptySet<String>()
                                                }
                                                putStringSet(k, set)
                                            } else {
                                                when (v) {
                                                    is String -> putString(k, v)
                                                    is Boolean -> putBoolean(k, v)
                                                    is Int -> putInt(k, v)
                                                    is Long -> putLong(k, v)
                                                    is Double -> putFloat(k, v.toFloat())
                                                    is JSONArray -> putString(k, v.toString())
                                                }
                                            }
                                        }
                                        putBoolean("contacts_permission_denied", false)
                                        putBoolean("usage_stats_permission_denied", false)
                                    }
                                }
                            }
                            "webapps.json" -> {
                                val data = zis.bufferedReader().readText()
                                prefs.edit { putString("web_apps", data) }
                            }
                            "weather.json" -> {
                                val weatherJson = JSONObject(zis.bufferedReader().readText())
                                prefs.edit {
                                    val location = weatherJson.optString("location").ifBlank {
                                        weatherJson.optString("city_name")
                                    }
                                    if (location.isNotBlank()) {
                                        putString("weather_stored_location", location)
                                        putString("weather_stored_city_name", location)
                                    }
                                    val unit = weatherJson.optString("temperature_unit")
                                    if (unit.isNotBlank()) {
                                        putString("weather_temperature_unit", unit)
                                    }
                                }
                            }
                            "physical_activity.json" -> {
                                val physicalActivityJson = JSONObject(zis.bufferedReader().readText())
                                val physicalPrefs = physicalActivityJson.optJSONObject("physical_activity_preferences")
                                if (physicalPrefs != null) {
                                    val activityPrefs = context.getSharedPreferences(PHYSICAL_ACTIVITY_PREFS_NAME, Context.MODE_PRIVATE)
                                    restorePreferencesFromJson(activityPrefs, physicalPrefs)
                                }
                            }
                            "notes.json" -> {
                                try {
                                    val notesJsonString = zis.bufferedReader().readText()
                                    prefs.edit { putString("note_widget_items", notesJsonString) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            onImported()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharedPreferencesToJson(sharedPreferences: SharedPreferences): JSONObject {
        val json = JSONObject()
        sharedPreferences.all.forEach { (key, value) ->
            when (value) {
                is Set<*> -> json.put(key, JSONArray(value.toList()))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun restorePreferencesFromJson(sharedPreferences: SharedPreferences, json: JSONObject) {
        sharedPreferences.edit {
            clear()
            val stringSetKeys = setOf("favorite_apps", "hidden_apps", "focus_mode_allowed_apps", "locked_apps")
            json.keys().forEach { key ->
                val value = json.get(key)
                if (key in stringSetKeys) {
                    val set = when (value) {
                        is JSONArray -> {
                            val items = mutableSetOf<String>()
                            for (i in 0 until value.length()) items.add(value.getString(i))
                            items
                        }
                        is String -> setOf(value)
                        else -> emptySet()
                    }
                    putStringSet(key, set)
                } else {
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putFloat(key, value.toFloat())
                        is Float -> putFloat(key, value)
                        is JSONArray -> putString(key, value.toString())
                    }
                }
            }
        }
    }
}
