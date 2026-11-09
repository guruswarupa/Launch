package com.guruswarupa.launch.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/** Reads colors and flags from the current theme's attributes. Shared by every Launch module. */
object ThemeAttrs {
    @ColorInt
    fun color(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        check(resolved) { "Theme attribute 0x${Integer.toHexString(attr)} not resolvable on $context" }
        return if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
    }

    fun boolean(context: Context, @AttrRes attr: Int, default: Boolean = false): Boolean {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attr, typedValue, true)) return default
        return typedValue.data != 0
    }
}
