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

/**
 * Resolves the semantic theme attributes declared in res/values/attrs.xml (appAccent,
 * appSurface, appTextPrimary, ...), and applies the user's selected color theme — a
 * [ThemePalette] plus an optional [AccentSwatch] — to activities and other UI-owning contexts.
 *
 * The palette/accent overlays live in res/values/themes_palettes.xml as ThemeOverlay.Launch.*
 * styles with `parent=""`; they carry only the attrs from attrs.xml, so stacking them via
 * `Resources.Theme.applyStyle` never pulls in unrelated Material defaults. See
 * `ThemeOverlay.Launch.Palette.Nord` for the full attr list every palette must declare.
 */
object ThemeManager {

    // --- attribute resolution -------------------------------------------------------------

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

    /** Returns null instead of throwing when the attribute can't be resolved (e.g. a stale/odd Context). */
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

    // --- palette / accent application ------------------------------------------------------

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun currentPalette(context: Context): ThemePalette =
        ThemePalette.of(prefs(context).getString(Constants.Prefs.COLOR_THEME, null))

    fun currentAccent(context: Context): AccentSwatch =
        AccentSwatch.of(prefs(context).getString(Constants.Prefs.COLOR_ACCENT, null))

    private fun isOpaqueSurfacesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)

    /**
     * Applies the selected palette + accent (+ opaque-surfaces overlay if enabled) to
     * [activity]'s own theme. Safe to call multiple times — each call re-applies from scratch
     * rather than stacking further, since `applyStyle(force = true)` overwrites prior values for
     * the attrs each overlay declares.
     *
     * Must run before `setContentView` — see the `onActivityPreCreated` / `onActivityCreated`
     * hook in LaunchApplication.kt for why. A no-op (throws are swallowed) for activities whose
     * manifest theme isn't one of ours (e.g. ScreenRecordPermissionActivity's framework
     * translucent theme) — there's nothing to layer our attrs onto there, and forcing it would
     * risk corrupting a theme we don't own.
     */
    fun apply(activity: Activity) {
        val base = baseThemeFor(activity) ?: return
        activity.setTheme(base)
        applyOverlaysOnly(activity)
        activity.window?.decorView?.setTag(R.id.tag_theme_signature, signature(prefs(activity)))
    }

    /**
     * Applies just the palette/accent/opaque overlays on top of whatever base theme [context]
     * already has. Exposed for callers that need to build their own themed wrapper (e.g. one
     * that must use androidx.appcompat.view.ContextThemeWrapper specifically, for AppCompat
     * widgets like PopupMenu that rely on its resource remapping) rather than going through
     * [themedContext]'s framework `android.view.ContextThemeWrapper`.
     */
    fun applyOverlaysOnly(context: Context) {
        val palette = currentPalette(context)
        val accent = currentAccent(context)
        context.theme.applyStyle(palette.overlayRes, true)
        accent.overlayRes?.let { context.theme.applyStyle(it, true) }
        if (isOpaqueSurfacesEnabled(context)) {
            context.theme.applyStyle(palette.opaqueOverlayRes, true)
        }
    }

    /**
     * A themed [Context] for UI built outside an Activity — accessibility-service overlays
     * (edge panel, control center) and similar. Wraps [context] in the same base theme an
     * Activity would get (falling back to the non-Settings base when the manifest lookup fails,
     * e.g. for a Service) plus the current palette/accent overlays.
     */
    fun themedContext(context: Context): Context {
        val base = (context as? Activity)?.let { baseThemeFor(it) } ?: R.style.Theme_Launch
        val wrapper = ContextThemeWrapper(context, base)
        applyOverlaysOnly(wrapper)
        return wrapper
    }

    /** Same as [themedContext] but always non-Settings — for non-Activity, non-manifest contexts. */
    fun themedContext(context: Context, @StyleRes fallbackBase: Int): Context {
        val wrapper = ContextThemeWrapper(context, fallbackBase)
        applyOverlaysOnly(wrapper)
        return wrapper
    }

    /**
     * A signature capturing everything [apply] reads, so callers can cheaply detect "did the
     * active theme on this decor view fall behind the stored prefs" (see
     * SettingsChangeCoordinator.handleSettingsUpdate and MainActivity.onResume) without
     * re-running the whole apply/recreate dance speculatively.
     */
    fun signature(prefs: SharedPreferences): String =
        "${prefs.getString(Constants.Prefs.COLOR_THEME, null) ?: ThemePalette.NORD.id}|" +
            "${prefs.getString(Constants.Prefs.COLOR_ACCENT, null) ?: AccentSwatch.PALETTE.id}|" +
            "${prefs.getBoolean(Constants.Prefs.OPAQUE_SURFACES_ENABLED, false)}"

    /** True when [activity]'s current decor view was themed with an older pref state than [prefs] now holds. */
    fun isStale(activity: Activity, prefs: SharedPreferences): Boolean {
        val live = activity.window?.decorView?.getTag(R.id.tag_theme_signature) as? String ?: return true
        return live != signature(prefs)
    }

    // --- base theme resolution ---------------------------------------------------------------

    private val manifestThemeCache = HashMap<String, Int>()

    /**
     * The manifest-declared `android:theme` for [activity]'s exact class, re-pointed at the
     * palette's Light sibling when the active palette is light. Returns null when the manifest
     * theme isn't one of ours (Theme.Launch / Theme.Launch.Settings) — callers should leave such
     * activities alone.
     */
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

/** Walks the Context wrapper chain to find the nearest Activity, if any (e.g. from a View's context). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
