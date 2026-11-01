package com.guruswarupa.launch.managers

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.DownloadableFontManager

object TypographyManager {

    private const val DEFAULT_SCALE_PERCENT = 100
    private const val MIN_SCALE_PERCENT = 80
    private const val MAX_SCALE_PERCENT = 140

    fun applyToActivity(activity: Activity) {
        val root = activity.window?.decorView ?: return
        val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Activity.MODE_PRIVATE)
        val signature = listOf(
            prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, DEFAULT_SCALE_PERCENT),
            prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default"),
            prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular"),
            prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT),
            DownloadableFontManager.getTypeface(prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default") != null
        ).hashCode()
        val textViewCount = countTextViews(root)
        val applied = root.getTag(R.id.tag_typography_applied) as? Pair<*, *>
        if (applied != null && applied.first == signature && applied.second == textViewCount) return
        applyToView(root)
        root.setTag(R.id.tag_typography_applied, signature to textViewCount)
    }

    private fun countTextViews(view: View): Int {
        var count = if (view is TextView) 1 else 0
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) count += countTextViews(view.getChildAt(i))
        }
        return count
    }

    fun applyToView(view: View) {
        val context = view.context
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Activity.MODE_PRIVATE)

        val configuredScalePercent = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, DEFAULT_SCALE_PERCENT)
            .coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)
        val fontScale = configuredScalePercent / 100f

        val fontStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        val fontColorValue = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)
            ?: Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT
        val fontColor = resolveFontColor(fontColorValue)

        applyToViewTree(view, fontScale, fontStyle, intensity, fontColor)
    }

    fun applyToViewTree(view: View, fontScale: Float, fontStyle: String, intensity: String, fontColor: Int?) {
        if (view is TextView) {
            val baseSizePx = (view.getTag(R.id.tag_typography_base_size_px) as? Float)
                ?: view.textSize.also { view.setTag(R.id.tag_typography_base_size_px, it) }
            val baseItalic = (view.getTag(R.id.tag_typography_base_italic) as? Boolean)
                ?: ((view.typeface?.style ?: Typeface.NORMAL) and Typeface.ITALIC != 0)
                    .also { view.setTag(R.id.tag_typography_base_italic, it) }
            val baseFakeBold = (view.getTag(R.id.tag_typography_base_fake_bold) as? Boolean)
                ?: view.paint.isFakeBoldText.also { view.setTag(R.id.tag_typography_base_fake_bold, it) }
            val baseScaleX = (view.getTag(R.id.tag_typography_base_scale_x) as? Float)
                ?: view.textScaleX.also { view.setTag(R.id.tag_typography_base_scale_x, it) }
            val baseAlpha = (view.getTag(R.id.tag_typography_base_alpha) as? Float)
                ?: view.alpha.also { view.setTag(R.id.tag_typography_base_alpha, it) }

            val targetSizePx = baseSizePx * fontScale
            if (kotlin.math.abs(view.textSize - targetSizePx) > 0.01f) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetSizePx)
            }

            val wantsBold = intensity == "bold" || intensity == "extra_bold"
            val style = when {
                wantsBold && baseItalic -> Typeface.BOLD_ITALIC
                wantsBold -> Typeface.BOLD
                baseItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val targetTypeface = resolveTypeface(view.context, fontStyle, intensity, style)
            if (view.typeface !== targetTypeface) {
                view.typeface = targetTypeface
            }
            val targetFakeBold = if (intensity == "extra_bold") true else baseFakeBold
            if (view.paint.isFakeBoldText != targetFakeBold) {
                view.paint.isFakeBoldText = targetFakeBold
            }
            val (scaleFactor, alphaFactor) = resolveIntensityVisuals(intensity)
            val targetScaleX = baseScaleX * scaleFactor
            if (view.textScaleX != targetScaleX) {
                view.textScaleX = targetScaleX
            }
            val targetAlpha = baseAlpha * alphaFactor
            if (view.alpha != targetAlpha) {
                view.alpha = targetAlpha
            }

            val baseTextColor = resolveBaseTextColor(view)
            val baseHintColor = resolveBaseHintColor(view)

            if (fontColor != null) {
                val alpha = Color.alpha(fontColor)
                val hintAlpha = (alpha * 0.6f).toInt()
                val hintColor = Color.argb(hintAlpha, Color.red(fontColor), Color.green(fontColor), Color.blue(fontColor))
                if (view.currentTextColor != fontColor) view.setTextColor(fontColor)
                if (view.currentHintTextColor != hintColor) view.setHintTextColor(hintColor)
            } else {
                if (view.currentTextColor != baseTextColor) view.setTextColor(baseTextColor)
                if (view.currentHintTextColor != baseHintColor) view.setHintTextColor(baseHintColor)
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToViewTree(view.getChildAt(i), fontScale, fontStyle, intensity, fontColor)
            }
        }
    }

    private fun resolveBaseTextColor(textView: TextView): Int {
        return (textView.getTag(R.id.tag_typography_base_text_color) as? Int)
            ?: textView.currentTextColor.also {
                textView.setTag(R.id.tag_typography_base_text_color, it)
            }
    }

    private fun resolveBaseHintColor(textView: TextView): Int {
        return (textView.getTag(R.id.tag_typography_base_hint_color) as? Int)
            ?: textView.currentHintTextColor.also {
                textView.setTag(R.id.tag_typography_base_hint_color, it)
            }
    }

    private fun resolveFontColor(value: String): Int? {
        if (value == Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT) return null
        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun getConfiguredFontColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Activity.MODE_PRIVATE)
        val fontColorValue = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)
            ?: Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT
        return resolveFontColor(fontColorValue)
    }

    private fun resolveTypeface(context: Context, styleValue: String, intensity: String, style: Int): Typeface {
        val customTypeface = resolveCustomTypeface(context, styleValue)
        return if (customTypeface != null) {
            Typeface.create(customTypeface, style)
        } else {
            val baseFamily = resolveFamily(styleValue, intensity)
            Typeface.create(baseFamily, style)
        }
    }

    private fun resolveCustomTypeface(context: Context, style: String): Typeface? {
        DownloadableFontManager.getTypeface(style)?.let { return it }
        return when (style) {
            "inter" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "ubuntu_regular" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "noto_sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "noto_serif" -> Typeface.create("serif", Typeface.NORMAL)
            "fira_code" -> Typeface.create("monospace", Typeface.NORMAL)
            else -> null
        }
    }

    private fun resolveIntensityVisuals(intensity: String): Pair<Float, Float> {
        return when (intensity) {
            "light" -> Pair(0.94f, 0.88f)
            "bold" -> Pair(1.05f, 1f)
            "extra_bold" -> Pair(1.12f, 1f)
            else -> Pair(1f, 1f)
        }
    }

    private fun resolveFamily(style: String, intensity: String): String {
        return when (style) {
            "serif" -> "serif"
            "monospace" -> "monospace"
            "condensed" -> "sans-serif-condensed"
            "rounded" -> "sans-serif-rounded"
            "condensed_light" -> "sans-serif-condensed-light"
            "condensed_medium" -> "sans-serif-condensed-medium"
            "serif_monospace" -> "serif-monospace"
            "display" -> "sans-serif"
            "thin" -> "sans-serif-thin"
            "medium" -> "sans-serif-medium"
            "black" -> "sans-serif-black"
            "smallcaps" -> "sans-serif-smallcaps"
            "casual" -> "casual"
            "cursive" -> "cursive"
            "sans_serif_light" -> "sans-serif-light"
            else -> if (intensity == "light") "sans-serif-light" else "sans-serif"
        }
    }
}
