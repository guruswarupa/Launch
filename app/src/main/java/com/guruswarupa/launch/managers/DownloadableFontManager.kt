package com.guruswarupa.launch.managers

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.guruswarupa.launch.R
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "com.guruswarupa.launch.DOWNLOADABLE_FONTS"
private const val KEY_DOWNLOADED_FONTS = "downloaded_fonts"

object DownloadableFontManager {

    data class FontOption(
        val styleKey: String,
        val displayName: String,
        val queryName: String,
        val weight: Int = 400
    )

    private val fontOptionsList = listOf(
        FontOption("inter", "Inter", "Inter"),
        FontOption("roboto", "Roboto", "Roboto"),
        FontOption("montserrat", "Montserrat", "Montserrat"),
        FontOption("lato", "Lato", "Lato"),
        FontOption("poppins", "Poppins", "Poppins"),
        FontOption("ubuntu_regular", "Ubuntu Regular", "Ubuntu"),
        FontOption("noto_sans", "Noto Sans", "Noto Sans"),
        FontOption("noto_serif", "Noto Serif", "Noto Serif"),
        FontOption("open_sans", "Open Sans", "Open Sans"),
        FontOption("raleway", "Raleway", "Raleway"),
        FontOption("oswald", "Oswald", "Oswald"),
        FontOption("merriweather", "Merriweather", "Merriweather"),
        FontOption("nunito", "Nunito", "Nunito"),
        FontOption("rubik", "Rubik", "Rubik"),
        FontOption("quicksand", "Quicksand", "Quicksand"),
        FontOption("titillium_web", "Titillium Web", "Titillium Web"),
        FontOption("playfair_display", "Playfair Display", "Playfair Display"),
        FontOption("exo_2", "Exo 2", "Exo 2"),
        FontOption("orbitron", "Orbitron", "Orbitron"),
        FontOption("jetbrains_mono", "JetBrains Mono", "JetBrains Mono"),
        FontOption("fira_code", "Fira Code", "Fira Code"),
        FontOption("source_code_pro", "Source Code Pro", "Source Code Pro"),
        FontOption("pacifico", "Pacifico", "Pacifico"),
        FontOption("lobster", "Lobster", "Lobster"),
        FontOption("caveat", "Caveat", "Caveat")
    )

    private val fontOptionsMap = fontOptionsList.associateBy { it.styleKey }
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val pendingCallbacks = ConcurrentHashMap<String, MutableList<(Boolean) -> Unit>>()
    private val handler = Handler(Looper.getMainLooper())

    fun getFontOptions(): List<FontOption> = fontOptionsList

    fun getTypeface(styleKey: String): Typeface? = typefaceCache[styleKey]

    fun isDownloaded(context: Context, styleKey: String): Boolean {
        return getDownloadedFonts(context).contains(styleKey)
    }

    fun preloadDownloadedFonts(context: Context) {
        val downloaded = getDownloadedFonts(context)
        downloaded.forEach { styleKey ->
            requestFont(context, styleKey, silent = true) { _ -> }
        }
    }

    fun uninstallFont(context: Context, styleKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getDownloadedFonts(context).apply { remove(styleKey) }
        prefs.edit { putStringSet(KEY_DOWNLOADED_FONTS, updated) }
        typefaceCache.remove(styleKey)
    }

    private fun getDownloadedFonts(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DOWNLOADED_FONTS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun requestFont(context: Context, styleKey: String, silent: Boolean = false, callback: (Boolean) -> Unit) {
        val option = fontOptionsMap[styleKey] ?: run {
            callback(false)
            return
        }
        typefaceCache[styleKey]?.let {
            callback(true)
            return
        }
        val queue = pendingCallbacks.computeIfAbsent(styleKey) { mutableListOf() }
        synchronized(queue) {
            queue.add(callback)
            if (queue.size > 1) return
        }
        val query = "name=${option.queryName}&weight=${option.weight}&width=100"
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            query,
            R.array.com_google_android_gms_fonts_certs
        )

        val mainExecutor = ContextCompat.getMainExecutor(context)

        val fontRequestCallback = object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                typefaceCache[styleKey] = typeface
                markFontDownloaded(context, styleKey)
                dispatch(styleKey, true)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                if (!silent) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.font_download_failed_with_name, option.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dispatch(styleKey, false)
            }
        }



        FontsContractCompat.requestFont(
            context,
            request,
            Typeface.NORMAL,
            mainExecutor,
            mainExecutor,
            fontRequestCallback
        )
    }

    private fun markFontDownloaded(context: Context, styleKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getDownloadedFonts(context).apply { add(styleKey) }
        prefs.edit { putStringSet(KEY_DOWNLOADED_FONTS, updated) }
    }

    private fun dispatch(styleKey: String, success: Boolean) {
        val callbacks = pendingCallbacks.remove(styleKey) ?: return
        handler.post {
            callbacks.forEach { it(success) }
        }
    }
}
