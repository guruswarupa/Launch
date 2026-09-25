package com.guruswarupa.launch.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.jsoup.Jsoup
import java.net.URLEncoder

data class SearchSuggestion(
    val title: String,
    val url: String,
    val description: String = ""
)

object WebAppSearchHelper {
    private const val TAG = "WebAppSearchHelper"

    val POPULAR_WEBSITES = listOf(
        SearchSuggestion("Google", "https://www.google.com", "Search engine"),
        SearchSuggestion("YouTube", "https://www.youtube.com", "Video platform"),
        SearchSuggestion("Gmail", "https://mail.google.com", "Email service"),
        SearchSuggestion("Facebook", "https://www.facebook.com", "Social network"),
        SearchSuggestion("Twitter/X", "https://www.x.com", "Social media"),
        SearchSuggestion("Instagram", "https://www.instagram.com", "Photo sharing"),
        SearchSuggestion("Reddit", "https://www.reddit.com", "Discussion platform"),
        SearchSuggestion("WhatsApp Web", "https://web.whatsapp.com", "Messaging"),
        SearchSuggestion("LinkedIn", "https://www.linkedin.com", "Professional network"),
        SearchSuggestion("Netflix", "https://www.netflix.com", "Streaming service"),
        SearchSuggestion("Amazon", "https://www.amazon.com", "Online shopping"),
        SearchSuggestion("Wikipedia", "https://www.wikipedia.org", "Encyclopedia"),
        SearchSuggestion("GitHub", "https://github.com", "Code repository"),
        SearchSuggestion("Stack Overflow", "https://stackoverflow.com", "Q&A for developers")
    )

    suspend fun searchGoogle(query: String): List<SearchSuggestion> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                var results = searchDuckDuckGo(encodedQuery)

                if (results.isEmpty()) {
                    results = searchWithMobileUserAgent(encodedQuery)
                }

                if (results.isEmpty()) {
                    results = searchWithAutocomplete(encodedQuery)
                }

                results
            } catch (e: Exception) {

                searchWithAutocomplete(URLEncoder.encode(query, "UTF-8"))
            }
        }
    }

    private suspend fun searchDuckDuckGo(encodedQuery: String): List<SearchSuggestion> {
        return try {
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"

            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(8000)
                .get()

            val results = mutableListOf<SearchSuggestion>()

            val elements = doc.select("div.results_links_deep")

            for (element in elements.take(10)) {
                try {
                    val titleElement = element.selectFirst("a.result__a")
                    val descElement = element.selectFirst("a.result__snippet")

                    if (titleElement != null) {
                        val title = titleElement.text()
                        var url = titleElement.attr("href")

                        if (url.contains("uddg=")) {
                            url = url.substringAfter("uddg=").substringBefore("&")
                            url = java.net.URLDecoder.decode(url, "UTF-8")
                        }

                        val description = descElement?.text() ?: ""

                        if (url.startsWith("http") && !url.contains("duckduckgo.com")) {
                            if (results.none { it.url == url }) {
                                results.add(SearchSuggestion(title, url, description))
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchWithMobileUserAgent(encodedQuery: String): List<SearchSuggestion> {
        return try {
            val searchUrl = "https://www.google.com/search?q=$encodedQuery&num=10&hl=en"

            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(10000)
                .followRedirects(true)
                .get()

            val results = mutableListOf<SearchSuggestion>()

            val selectors = listOf(
                "div.g",
                "div[data-sokoposition]",
                ".g",
                "div.tF2Cxc",
                "div.MjjYud"
            )

            var elements = org.jsoup.select.Elements()
            for (selector in selectors) {
                elements = doc.select(selector)
                if (elements.isNotEmpty()) break
            }

            for (element in elements.take(10)) {
                try {
                    val titleElement = element.selectFirst("h3") ?: element.selectFirst("h2")
                    val linkElement = element.selectFirst("a[href]")

                    if (titleElement != null && linkElement != null) {
                        val title = titleElement.text()
                        var url = linkElement.attr("abs:href")

                        if (url.contains("/url?q=")) {
                            url = url.substringAfter("/url?q=").substringBefore("&")
                            url = java.net.URLDecoder.decode(url, "UTF-8")
                        }

                        url = url.substringBefore("#")

                        val descElement = element.selectFirst("[data-sncf], [data-content-snippet], .VwiC3b, .lyLwlc")
                        val description = descElement?.text() ?: ""

                        if (url.startsWith("http") &&
                            !url.contains("google.") &&
                            !url.contains("youtube.com/redirect") &&
                            url.split("/").getOrNull(2)?.contains(".") == true) {

                            if (results.none { it.url == url }) {
                                results.add(SearchSuggestion(title, url, description))
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchWithAutocomplete(encodedQuery: String): List<SearchSuggestion> {
        return try {
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"

            val response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .ignoreContentType(true)
                .timeout(5000)
                .execute()

            val body = response.body()
            val jsonArray = org.json.JSONArray(body)

            if (jsonArray.length() > 1) {
                val suggestions = jsonArray.getJSONArray(1)
                val results = mutableListOf<SearchSuggestion>()

                for (i in 0 until suggestions.length()) {
                    val suggestion = suggestions.getString(i)
                    val searchUrl = "https://www.google.com/search?q=${URLEncoder.encode(suggestion, "UTF-8")}"
                    results.add(SearchSuggestion(
                        title = suggestion,
                        url = searchUrl,
                        description = "Search for: $suggestion"
                    ))
                }

                results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPageTitle(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(3000)
                    .get()

                doc.title()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun filterHttpsOnly(suggestions: List<SearchSuggestion>): List<SearchSuggestion> {
        return suggestions.filter { it.url.startsWith("https://", ignoreCase = true) }
    }
}
