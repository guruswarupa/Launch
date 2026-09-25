package com.guruswarupa.launch.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants

object ThemeManager {

    @ColorInt
    fun color(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        check(resolved) { "Theme attribute 0x${Integer.toHexString(attr)} not resolvable on $context" }
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    @ColorInt
    fun colorOrNull(context: Context, @AttrRes attr: Int): Int? = try {
        color(context, attr)
    } catch (_: IllegalStateException) {
        null
    }

    fun dimenPx(context: Context, @AttrRes attr: Int): Float {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        check(resolved) { "Theme attribute 0x${Integer.toHexString(attr)} not resolvable on $context" }
        return typedValue.getDimension(context.resources.displayMetrics)
    }

    fun boolean(context: Context, @AttrRes attr: Int, default: Boolean = false): Boolean {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attr, typedValue, true)) return default
        return typedValue.data != 0
    }

    fun isLight(context: Context): Boolean = boolean(context, R.attr.appIsLight, default = false)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun currentPalette(context: Context): ThemePalette =
        ThemePalette.of(prefs(context).getString(Constants.Prefs.COLOR_THEME, null))

    fun currentAccent(context: Context): AccentSwatch =
        AccentSwatch.of(prefs(context).getString(Constants.Prefs.COLOR_ACCENT, null))

    private fun isOpaqueSurfacesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)

    fun apply(activity: Activity) {
        val base = baseThemeFor(activity) ?: return
        activity.setTheme(base)
        applyOverlaysOnly(activity)
        activity.window?.decorView?.setTag(R.id.tag_theme_signature, signature(prefs(activity)))
    }

    fun applyOverlaysOnly(context: Context) {
        val palette = currentPalette(context)
        val accent = currentAccent(context)
        context.theme.applyStyle(palette.overlayRes, true)
        accent.overlayRes?.let { context.theme.applyStyle(it, true) }
        if (isOpaqueSurfacesEnabled(context)) {
            context.theme.applyStyle(palette.opaqueOverlayRes, true)
        }
    }

    fun themedContext(context: Context): Context {
        val base = (context as? Activity)?.let { baseThemeFor(it) } ?: R.style.Theme_Launch
        val wrapper = ContextThemeWrapper(context, base)
        applyOverlaysOnly(wrapper)
        return wrapper
    }

    fun themedContext(context: Context, @StyleRes fallbackBase: Int): Context {
        val wrapper = ContextThemeWrapper(context, fallbackBase)
        applyOverlaysOnly(wrapper)
        return wrapper
    }

    fun signature(prefs: SharedPreferences): String =
        "${prefs.getString(Constants.Prefs.COLOR_THEME, null) ?: ThemePalette.NORD.id}|" +
            "${prefs.getString(Constants.Prefs.COLOR_ACCENT, null) ?: AccentSwatch.PALETTE.id}|" +
            "${prefs.getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)}"

    fun isStale(activity: Activity, prefs: SharedPreferences): Boolean {
        val live = activity.window?.decorView?.getTag(R.id.tag_theme_signature) as? String ?: return true
        return live != signature(prefs)
    }

    private val manifestThemeCache = HashMap<String, Int>()

    private fun baseThemeFor(activity: Activity): Int? {
        val declared = manifestThemeResId(activity)
        val light = currentPalette(activity).isLight
        return when (declared) {
            R.style.Theme_Launch -> if (light) R.style.Theme_Launch_Light else R.style.Theme_Launch
            R.style.Theme_Launch_Settings -> if (light) R.style.Theme_Launch_Settings_Light else R.style.Theme_Launch_Settings
            else -> null
        }
    }

    private fun manifestThemeResId(activity: Activity): Int {
        val className = activity.javaClass.name
        manifestThemeCache[className]?.let { return it }
        val resId = try {
            activity.packageManager.getActivityInfo(activity.componentName, 0).themeResource
        } catch (_: Exception) {
            0
        }
        manifestThemeCache[className] = resId
        return resId
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
