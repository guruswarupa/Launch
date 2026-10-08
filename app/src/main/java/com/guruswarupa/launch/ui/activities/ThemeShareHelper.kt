package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.guruswarupa.launch.models.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ThemeShareHelper {

    private val SHAREABLE_KEYS = setOf(
        Constants.Prefs.SELECTED_THEME,
        Constants.Prefs.VIEW_PREFERENCE,
        Constants.Prefs.STOCK_HOME_APP_ORDER,
        Constants.Prefs.STOCK_HOME_FOLDERS,
        Constants.Prefs.STOCK_DRAWER_ENABLED,
        Constants.Prefs.STOCK_HOTSEAT_COUNT,
        Constants.Prefs.STOCK_DOCK_ORDER,
        Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT,
        Constants.Prefs.TYPOGRAPHY_FONT_STYLE,
        Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY,
        Constants.Prefs.TYPOGRAPHY_FONT_COLOR,
        Constants.Prefs.GRID_COLUMNS,
        Constants.Prefs.SHOW_APP_NAME_IN_GRID,
        Constants.Prefs.HIDE_APP_ICON_IN_LIST,
        Constants.Prefs.ICON_STYLE,
        Constants.Prefs.ICON_SIZE,
        Constants.Prefs.ICON_PACK_PACKAGE,
        Constants.Prefs.ICON_PACK_ENABLED,
        Constants.Prefs.BACKGROUND_TRANSLUCENCY
    )

    private fun buildThemeJson(prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        val all = prefs.all
        SHAREABLE_KEYS.forEach { key ->
            when (val value = all[key]) {
                is String -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is Long -> json.put(key, value)
                is Set<*> -> json.put(key, JSONArray(value.toList()))
                else -> {}
            }
        }
        return json
    }

    /** Writes a shareable theme file to the cache dir and returns a share Intent for it. */
    fun createShareIntent(context: Context, prefs: SharedPreferences): Intent? {
        return try {
            val file = File(context.cacheDir, "launch-theme.json")
            file.writeText(buildThemeJson(prefs).toString(2))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun exportThemeToUri(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(buildThemeJson(prefs).toString(2).toByteArray())
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importThemeFromUri(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: return false
            val json = JSONObject(text)
            prefs.edit {
                SHAREABLE_KEYS.forEach { key ->
                    if (!json.has(key)) return@forEach
                    when (val value = json.get(key)) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Double -> putFloat(key, value.toFloat())
                        is Long -> putLong(key, value)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) set.add(value.getString(i))
                            putStringSet(key, set)
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
