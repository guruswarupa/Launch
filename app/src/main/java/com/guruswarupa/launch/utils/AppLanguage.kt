package com.guruswarupa.launch.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguage {

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

    private val numberingSystemOverrides = mapOf(
        "hi" to "hi-u-nu-deva",
        "kn" to "kn-u-nu-knda",
    )

    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""

        return Locale.Builder().setLocale(locales[0]).clearExtensions().build().toLanguageTag()
    }

    fun indexOf(tag: String): Int {
        val index = options.indexOfFirst { it.tag == tag }
        return if (index >= 0) index else 0
    }

    fun apply(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            val resolvedTag = numberingSystemOverrides[tag] ?: tag
            LocaleListCompat.forLanguageTags(resolvedTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
