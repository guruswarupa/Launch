package com.guruswarupa.launch.utils

import android.content.SharedPreferences
import com.guruswarupa.launch.models.Constants

object LayoutMode {

    private fun viewPreference(prefs: SharedPreferences): String =
        prefs.getString(Constants.Prefs.VIEW_PREFERENCE, Constants.Prefs.VIEW_PREFERENCE_LIST)
            ?: Constants.Prefs.VIEW_PREFERENCE_LIST

    fun isStock(prefs: SharedPreferences): Boolean =
        viewPreference(prefs) == Constants.Prefs.VIEW_PREFERENCE_STOCK

    /** True whenever the app list should render as a grid (Grid or Stock display style). */
    fun isGridRendering(prefs: SharedPreferences): Boolean {
        val preference = viewPreference(prefs)
        return preference == Constants.Prefs.VIEW_PREFERENCE_GRID || preference == Constants.Prefs.VIEW_PREFERENCE_STOCK
    }
}
