package com.guruswarupa.launch.apps

import android.content.SharedPreferences
import com.guruswarupa.launch.managers.FavoriteAppManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bare-bones in-memory SharedPreferences, just enough for the favorites store. */
private class MemoryPrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (map[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>(); private val removals = HashSet<String>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v?.toSet() }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { removals.add(k) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { removals.forEach { map.remove(it) }; map.putAll(pending) }
    }
}

class FavoriteAppManagerTest {
    private val own = "com.guruswarupa.launch"

    @Test
    fun oldBarePackageEntryNoLongerFavoritesEveryLaunchApp() {
        val prefs = MemoryPrefs().apply { map["favorite_apps"] = setOf(own, "com.whatsapp") }
        val manager = FavoriteAppManager(prefs)
        assertEquals(setOf("com.whatsapp"), manager.getFavoriteApps())
    }

    @Test
    fun favoritingOneLaunchAppLeavesTheOthersAlone() {
        val manager = FavoriteAppManager(MemoryPrefs())
        manager.addFavoriteApp("$own/$own.apps.calculator.CalculatorActivity")
        assertTrue(manager.isFavoriteApp("$own/$own.apps.calculator.CalculatorActivity"))
        assertFalse(manager.isFavoriteApp("$own/$own.apps.clock.ClockActivity"))
        assertFalse(manager.isFavoriteApp(own))
    }
}
