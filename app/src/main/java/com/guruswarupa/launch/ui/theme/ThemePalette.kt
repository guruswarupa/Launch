package com.guruswarupa.launch.ui.theme

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.guruswarupa.launch.R

/**
 * One selectable color theme. [overlayRes] and [opaqueOverlayRes] point at the
 * ThemeOverlay.Launch.Palette.* styles in res/values/themes_palettes.xml, which
 * [ThemeManager.apply] layers on top of the base Theme.Launch / Theme.Launch.Light.
 *
 * [previewBackground]/[previewSurface]/[previewAccent] are plain literal color ints — NOT
 * resolved from the currently active theme — so a palette picker card always renders in that
 * palette's own colors regardless of which theme is active when the picker is drawn.
 */
data class ThemePalette(
    val id: String,
    @StringRes val nameRes: Int,
    @StyleRes val overlayRes: Int,
    @StyleRes val opaqueOverlayRes: Int,
    val isLight: Boolean,
    val previewBackground: Int,
    val previewSurface: Int,
    val previewAccent: Int,
) {
    companion object {
        val NORD = ThemePalette(
            id = "nord",
            nameRes = R.string.palette_nord,
            overlayRes = R.style.ThemeOverlay_Launch_Palette_Nord,
            opaqueOverlayRes = R.style.ThemeOverlay_Launch_Palette_Nord_Opaque,
            isLight = false,
            previewBackground = Color.parseColor("#121212"),
            previewSurface = Color.parseColor("#2E3440"),
            previewAccent = Color.parseColor("#88C0D0"),
        )
        val DRACULA = ThemePalette(
            id = "dracula",
            nameRes = R.string.palette_dracula,
            overlayRes = R.style.ThemeOverlay_Launch_Palette_Dracula,
            opaqueOverlayRes = R.style.ThemeOverlay_Launch_Palette_Dracula_Opaque,
            isLight = false,
            previewBackground = Color.parseColor("#282A36"),
            previewSurface = Color.parseColor("#44475A"),
            previewAccent = Color.parseColor("#BD93F9"),
        )
        val CATPPUCCIN_MOCHA = ThemePalette(
            id = "catppuccin_mocha",
            nameRes = R.string.palette_catppuccin_mocha,
            overlayRes = R.style.ThemeOverlay_Launch_Palette_CatppuccinMocha,
            opaqueOverlayRes = R.style.ThemeOverlay_Launch_Palette_CatppuccinMocha_Opaque,
            isLight = false,
            previewBackground = Color.parseColor("#1E1E2E"),
            previewSurface = Color.parseColor("#313244"),
            previewAccent = Color.parseColor("#CBA6F7"),
        )
        val MONOCHROME = ThemePalette(
            id = "monochrome",
            nameRes = R.string.palette_monochrome,
            overlayRes = R.style.ThemeOverlay_Launch_Palette_Monochrome,
            opaqueOverlayRes = R.style.ThemeOverlay_Launch_Palette_Monochrome_Opaque,
            isLight = false,
            previewBackground = Color.parseColor("#000000"),
            previewSurface = Color.parseColor("#2B2B2B"),
            previewAccent = Color.parseColor("#FFFFFF"),
        )

        /** Ordered for display in the Settings palette picker. Nord stays first/default. */
        val ALL = listOf(NORD, DRACULA, CATPPUCCIN_MOCHA, MONOCHROME)

        fun of(id: String?): ThemePalette = ALL.firstOrNull { it.id == id } ?: NORD
    }
}
