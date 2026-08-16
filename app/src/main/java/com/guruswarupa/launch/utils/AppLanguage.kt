package com.guruswarupa.launch.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language selection, backed by [AppCompatDelegate.setApplicationLocales].
 *
 * This is the single source of truth for the chosen UI language - there is no
 * separate SharedPreferences key. On API 33+ the framework LocaleManager owns
 * persistence and the choice is mirrored into Settings > Apps > Launch > Language.
 * On API 26-32 the AndroidX backport persists it (see AppLocalesMetadataHolderService
 * in the manifest) and applies it only to AppCompatActivity subclasses.
 *
 * Autonyms are intentionally hardcoded here, not in strings.xml: a language's own
 * name must never be translated (Hindi should always read "हिन्दी", never its
 * English/French/etc. translation).
 */
object AppLanguage {

    /** [tag] is a BCP-47 language tag, or "" to mean "follow system default". */
    data class Option(val tag: String, val autonym: String)

    val options: List<Option> = listOf(
        Option("", "System default"),
        Option("en", "English"),
        Option("hi", "हिन्दी"),
        Option("es", "Español"),
        Option("pt-BR", "Português (Brasil)"),
        Option("de", "Deutsch"),
        Option("fr", "Français"),
        Option("ru", "Русский"),
        Option("id", "Bahasa Indonesia"),
        Option("ar", "العربية"),
        Option("kn", "ಕನ್ನಡ"),
    )

    /** The currently applied tag, or "" when following the system default. */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        return locales.toLanguageTags().substringBefore(',')
    }

    fun indexOf(tag: String): Int {
        val index = options.indexOfFirst { it.tag == tag }
        return if (index >= 0) index else 0
    }

    /** Applies [tag] as the app's UI language; "" resets to the system default. */
    fun apply(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
