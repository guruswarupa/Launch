package com.guruswarupa.launch.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Guards the two folder-naming traps that would silently ship an unreachable
 * language: Indonesian's legacy "in" resource qualifier (Java normalizes the
 * BCP-47 tag "id" to "in") and Brazilian Portuguese's "pt-rBR" qualifier.
 */
class AppLanguageTest {

    // Mirrors the values-<qualifier> folders shipped under app/src/main/res.
    private val shippedResourceQualifiers = setOf(
        "hi", "es", "pt-rBR", "de", "fr", "ru", "in", "ar", "kn"
    )

    private fun expectedQualifierFor(tag: String): String =
        when (tag) {
            "id" -> "in"
            "pt-BR" -> "pt-rBR"
            else -> tag
        }

    @Test
    fun everyNonEmptyTagParsesToAValidLocale() {
        AppLanguage.options
            .filter { it.tag.isNotEmpty() }
            .forEach { option ->
                val locale = Locale.forLanguageTag(option.tag)
                assertTrue(
                    "Tag '${option.tag}' did not parse to a locale with a language",
                    locale.language.isNotEmpty()
                )
            }
    }

    @Test
    fun everyNonEnglishTagHasAShippedTranslationFolder() {
        AppLanguage.options
            .filter { it.tag.isNotEmpty() && it.tag != "en" }
            .forEach { option ->
                val qualifier = expectedQualifierFor(option.tag)
                assertTrue(
                    "Tag '${option.tag}' expects values-$qualifier, which is not in the shipped set",
                    shippedResourceQualifiers.contains(qualifier)
                )
            }
    }

    @Test
    fun systemDefaultOptionIsFirstAndUsesEmptyTag() {
        assertEquals("", AppLanguage.options.first().tag)
    }

    @Test
    fun indexOfRoundTripsForEveryOption() {
        AppLanguage.options.forEachIndexed { index, option ->
            assertEquals(index, AppLanguage.indexOf(option.tag))
        }
    }

    @Test
    fun indexOfUnknownTagFallsBackToSystemDefault() {
        assertEquals(0, AppLanguage.indexOf("zz-unknown"))
    }

    @Test
    fun autonymsAreUnique() {
        val autonyms = AppLanguage.options.map { it.autonym }
        assertEquals(autonyms.size, autonyms.toSet().size)
    }
}
