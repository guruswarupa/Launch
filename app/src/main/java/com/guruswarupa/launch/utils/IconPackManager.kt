package com.guruswarupa.launch.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.edit
import com.guruswarupa.launch.models.Constants
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val developer: String? = null
)

object IconPackManager {
    
    private val iconPackCache = ConcurrentHashMap<String, String>()
    private var cachedIconPackPackage: String? = null
    
    private fun loadAppFilter(context: Context, iconPackPackage: String) {
        iconPackCache.clear()
        cachedIconPackPackage = iconPackPackage
        
        var inputStream: java.io.InputStream? = null
        var parser: XmlPullParser? = null
        
        try {
            val pm = context.packageManager
            val resources = pm.getResourcesForApplication(iconPackPackage)
            
            try {
                inputStream = resources.assets.open("appfilter.xml")
            } catch (e: Exception) {
                // Ignore
            }
            
            if (inputStream != null) {
                val factory = XmlPullParserFactory.newInstance()
                parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")
            } else {
                val resId = resources.getIdentifier("appfilter", "xml", iconPackPackage)
                if (resId != 0) {
                    parser = resources.getXml(resId)
                }
            }
            
            if (parser != null) {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        
                        if (component != null && drawable != null) {
                            val start = component.indexOf("{")
                            val end = component.indexOf("}")
                            if (start != -1 && end != -1 && end > start + 1) {
                                val compKey = component.substring(start + 1, end)
                                iconPackCache[compKey] = drawable
                                
                                val pkgName = compKey.substringBefore('/')
                                if (!iconPackCache.containsKey(pkgName)) {
                                    iconPackCache[pkgName] = drawable
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("IconPackManager", "Error parsing appfilter.xml from $iconPackPackage", e)
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {}
        }
    }
    
    fun getDrawableName(context: Context, packageName: String, activityName: String?, sharedPreferences: SharedPreferences): String? {
        val iconPackPackage = getSelectedIconPack(sharedPreferences)
        val isIconPackEnabled = isIconPackEnabled(sharedPreferences)
        if (!isIconPackEnabled || iconPackPackage == null) return null
        
        synchronized(iconPackCache) {
            if (cachedIconPackPackage != iconPackPackage) {
                loadAppFilter(context, iconPackPackage)
            }
        }
        
        if (activityName != null) {
            val fullKey = "$packageName/$activityName"
            val drawable = iconPackCache[fullKey]
            if (drawable != null) return drawable
        }
        
        return iconPackCache[packageName]
    }

    private const val ICON_PACK_INTENT_ACTION = "com.gorgon.zicons.constellation.ICON_PACK"
    private const val ADW_LAUNCHER_INTENT = "org.adw.launcher.THEMES"
    private const val GO_LAUNCHER_INTENT = "com.gau.go.launcherex.theme"
    private const val NOVA_LAUNCHER_INTENT = "com.teslacoilsw.launcher.THEME"
    
    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = mutableListOf<IconPackInfo>()
        
        val intentActions = listOf(
            ICON_PACK_INTENT_ACTION,
            ADW_LAUNCHER_INTENT,
            GO_LAUNCHER_INTENT,
            NOVA_LAUNCHER_INTENT
        )
        
        for (action in intentActions) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            
            for (resolveInfo in resolveInfos) {
                val packageName = resolveInfo.activityInfo.packageName
                
                if (!packs.any { it.packageName == packageName }) {
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(packageName)
                    
                    val developer = try {
                        appInfo.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        null
                    }
                    
                    packs.add(IconPackInfo(
                        packageName = packageName,
                        name = name,
                        icon = icon,
                        developer = developer
                    ))
                }
            }
        }
        
        return packs.sortedBy { it.name.lowercase() }
    }
    
    fun getSelectedIconPack(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(Constants.Prefs.ICON_PACK_PACKAGE, null)
    }
    
    fun isIconPackEnabled(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
    }
    
    fun setIconPack(sharedPreferences: SharedPreferences, packageName: String?) {
        sharedPreferences.edit {
            putString(Constants.Prefs.ICON_PACK_PACKAGE, packageName)
            putBoolean(Constants.Prefs.ICON_PACK_ENABLED, packageName != null)
        }
    }
    
    fun toggleIconPack(sharedPreferences: SharedPreferences, enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(Constants.Prefs.ICON_PACK_ENABLED, enabled)
        }
    }
    
    fun clearIconPack(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit {
            putString(Constants.Prefs.ICON_PACK_PACKAGE, null)
            putBoolean(Constants.Prefs.ICON_PACK_ENABLED, false)
        }
    }
    
    fun openIconPackInPlayStore(context: Context, packageName: String) {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.android.vending")
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(webIntent)
        }
    }
    
    fun browseIconPacks(context: Context) {
        val uri = Uri.parse("market://search?q=icon+pack+for+android&c=apps")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.android.vending")
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/search?q=icon+pack+for+android&c=apps")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(webIntent)
        }
    }
}
