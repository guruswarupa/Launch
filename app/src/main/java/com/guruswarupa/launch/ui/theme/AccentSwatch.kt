package com.guruswarupa.launch.ui.theme

import android.graphics.Color
import androidx.annotation.StyleRes
import com.guruswarupa.launch.R

data class AccentSwatch(
    val id: String,
    @StyleRes val overlayRes: Int?,
    val previewColor: Int?,
) {
    companion object {
        val PALETTE = AccentSwatch("palette", overlayRes = null, previewColor = null)

        val BLUE = swatch("blue", R.style.ThemeOverlay_Launch_Accent_Blue, "#4C8DF6")
        val INDIGO = swatch("indigo", R.style.ThemeOverlay_Launch_Accent_Indigo, "#6C7CF0")
        val VIOLET = swatch("violet", R.style.ThemeOverlay_Launch_Accent_Violet, "#A78BFA")
        val MAGENTA = swatch("magenta", R.style.ThemeOverlay_Launch_Accent_Magenta, "#E879C7")
        val PINK = swatch("pink", R.style.ThemeOverlay_Launch_Accent_Pink, "#F472A0")
        val RED = swatch("red", R.style.ThemeOverlay_Launch_Accent_Red, "#EF5350")
        val CORAL = swatch("coral", R.style.ThemeOverlay_Launch_Accent_Coral, "#FF7A59")
        val AMBER = swatch("amber", R.style.ThemeOverlay_Launch_Accent_Amber, "#F5B942")
        val LIME = swatch("lime", R.style.ThemeOverlay_Launch_Accent_Lime, "#A8CC50")
        val GREEN = swatch("green", R.style.ThemeOverlay_Launch_Accent_Green, "#4CAF7D")
        val TEAL = swatch("teal", R.style.ThemeOverlay_Launch_Accent_Teal, "#2FBFA0")
        val CYAN = swatch("cyan", R.style.ThemeOverlay_Launch_Accent_Cyan, "#56C7E0")
        val SKY = swatch("sky", R.style.ThemeOverlay_Launch_Accent_Sky, "#63B3ED")
        val SLATE = swatch("slate", R.style.ThemeOverlay_Launch_Accent_Slate, "#8A94A6")
        val SAND = swatch("sand", R.style.ThemeOverlay_Launch_Accent_Sand, "#C9A97E")
        val MONO = swatch("mono", R.style.ThemeOverlay_Launch_Accent_Mono, "#E6E6E6")

        val ALL = listOf(
            PALETTE, BLUE, INDIGO, VIOLET, MAGENTA, PINK, RED, CORAL,
            AMBER, LIME, GREEN, TEAL, CYAN, SKY, SLATE, SAND, MONO
        )

        fun of(id: String?): AccentSwatch = ALL.firstOrNull { it.id == id } ?: PALETTE

        private fun swatch(id: String, @StyleRes overlayRes: Int, hex: String) =
            AccentSwatch(id, overlayRes, Color.parseColor(hex))
    }
}
