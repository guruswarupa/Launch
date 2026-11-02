package com.guruswarupa.launch.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private class CatalogSite(val title: String, val url: String, val aliases: String = "")

    private val CATALOG: List<CatalogSite> = listOf(
        CatalogSite("Google", "https://www.google.com", "search"),
        CatalogSite("YouTube", "https://www.youtube.com", "yt video"),
        CatalogSite("YouTube Studio", "https://studio.youtube.com", "yt studio creator channel"),
        CatalogSite("YouTube Music", "https://music.youtube.com", "yt music"),
        CatalogSite("Google Play Console", "https://play.google.com/console", "play console developer publish app store"),
        CatalogSite("Google Play Store", "https://play.google.com/store", "play store apps"),
        CatalogSite("Google Search Console", "https://search.google.com/search-console", "webmaster seo"),
        CatalogSite("Google Analytics", "https://analytics.google.com", "ga4 stats"),
        CatalogSite("Google Ads", "https://ads.google.com", "adwords advertising"),
        CatalogSite("Google AdSense", "https://www.google.com/adsense", "adsense publisher"),
        CatalogSite("Firebase Console", "https://console.firebase.google.com", "firebase"),
        CatalogSite("Google Cloud Console", "https://console.cloud.google.com", "gcp cloud"),
        CatalogSite("Gmail", "https://mail.google.com", "email mail"),
        CatalogSite("Google Maps", "https://maps.google.com", "maps navigation"),
        CatalogSite("Google Drive", "https://drive.google.com", "files storage"),
        CatalogSite("Google Docs", "https://docs.google.com/document", "documents"),
        CatalogSite("Google Sheets", "https://docs.google.com/spreadsheets", "spreadsheet excel"),
        CatalogSite("Google Slides", "https://docs.google.com/presentation", "presentation powerpoint"),
        CatalogSite("Google Photos", "https://photos.google.com", "pictures gallery"),
        CatalogSite("Google Calendar", "https://calendar.google.com", "schedule events"),
        CatalogSite("Google Keep", "https://keep.google.com", "notes"),
        CatalogSite("Google Meet", "https://meet.google.com", "video call"),
        CatalogSite("Google Translate", "https://translate.google.com", "translation"),
        CatalogSite("Google News", "https://news.google.com", "headlines"),
        CatalogSite("Gemini", "https://gemini.google.com", "google ai bard"),
        CatalogSite("ChatGPT", "https://chatgpt.com", "openai gpt ai chat"),
        CatalogSite("Claude", "https://claude.ai", "anthropic ai chat"),
        CatalogSite("Perplexity", "https://www.perplexity.ai", "ai search"),
        CatalogSite("Facebook", "https://www.facebook.com", "fb meta"),
        CatalogSite("Instagram", "https://www.instagram.com", "insta ig meta"),
        CatalogSite("Threads", "https://www.threads.net", "meta"),
        CatalogSite("WhatsApp Web", "https://web.whatsapp.com", "whatsapp chat messaging"),
        CatalogSite("Telegram", "https://web.telegram.org", "chat messaging"),
        CatalogSite("Discord", "https://discord.com/app", "chat community"),
        CatalogSite("X (Twitter)", "https://x.com", "twitter tweets"),
        CatalogSite("Reddit", "https://www.reddit.com", "subreddit"),
        CatalogSite("LinkedIn", "https://www.linkedin.com", "jobs professional"),
        CatalogSite("Pinterest", "https://www.pinterest.com", "pins"),
        CatalogSite("Snapchat", "https://web.snapchat.com", "snap"),
        CatalogSite("TikTok", "https://www.tiktok.com", "short videos"),
        CatalogSite("Quora", "https://www.quora.com", "questions"),
        CatalogSite("Medium", "https://medium.com", "blog articles"),
        CatalogSite("Twitch", "https://www.twitch.tv", "streaming games"),
        CatalogSite("Netflix", "https://www.netflix.com", "movies series"),
        CatalogSite("Prime Video", "https://www.primevideo.com", "amazon movies"),
        CatalogSite("Disney+ Hotstar", "https://www.hotstar.com", "disney movies cricket"),
        CatalogSite("Spotify", "https://open.spotify.com", "music"),
        CatalogSite("Amazon", "https://www.amazon.com", "shopping"),
        CatalogSite("Flipkart", "https://www.flipkart.com", "shopping"),
        CatalogSite("eBay", "https://www.ebay.com", "shopping auction"),
        CatalogSite("AliExpress", "https://www.aliexpress.com", "shopping"),
        CatalogSite("Swiggy", "https://www.swiggy.com", "food delivery"),
        CatalogSite("Zomato", "https://www.zomato.com", "food delivery restaurants"),
        CatalogSite("Uber", "https://m.uber.com", "ride cab taxi"),
        CatalogSite("Outlook", "https://outlook.live.com", "microsoft email mail"),
        CatalogSite("Yahoo Mail", "https://mail.yahoo.com", "email"),
        CatalogSite("Proton Mail", "https://mail.proton.me", "email privacy"),
        CatalogSite("Microsoft Teams", "https://teams.microsoft.com", "meetings"),
        CatalogSite("Zoom", "https://app.zoom.us", "meetings video call"),
        CatalogSite("Slack", "https://app.slack.com", "work chat"),
        CatalogSite("Notion", "https://www.notion.so", "notes wiki"),
        CatalogSite("Trello", "https://trello.com", "boards tasks"),
        CatalogSite("Canva", "https://www.canva.com", "design"),
        CatalogSite("Figma", "https://www.figma.com", "design"),
        CatalogSite("Dropbox", "https://www.dropbox.com", "files storage"),
        CatalogSite("GitHub", "https://github.com", "code git repository"),
        CatalogSite("GitLab", "https://gitlab.com", "code git repository"),
        CatalogSite("Stack Overflow", "https://stackoverflow.com", "programming questions"),
        CatalogSite("Hacker News", "https://news.ycombinator.com", "tech news"),
        CatalogSite("Vercel", "https://vercel.com/dashboard", "hosting deploy"),
        CatalogSite("Netlify", "https://app.netlify.com", "hosting deploy"),
        CatalogSite("Cloudflare", "https://dash.cloudflare.com", "dns cdn"),
        CatalogSite("Wikipedia", "https://www.wikipedia.org", "encyclopedia wiki"),
        CatalogSite("BBC News", "https://www.bbc.com/news", "headlines"),
        CatalogSite("CNN", "https://www.cnn.com", "news"),
        CatalogSite("The Hindu", "https://www.thehindu.com", "news india"),
        CatalogSite("DuckDuckGo", "https://duckduckgo.com", "search private"),
        CatalogSite("Bing", "https://www.bing.com", "search microsoft")
    )

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun localSearch(query: String, limit: Int = 6): List<SearchSuggestion> {
        val words = tokens(query)
        if (words.isEmpty()) return emptyList()
        val joined = words.joinToString("")
        return CATALOG
            .mapNotNull { site ->
                val titleWords = tokens(site.title)
                val aliasWords = tokens(site.aliases)
                val host = hostOf(site.url).orEmpty()
                val allWords = titleWords + aliasWords + tokens(host)
                val titleJoined = titleWords.joinToString("")
                val matches = words.all { word -> allWords.any { it.startsWith(word) } || titleJoined.contains(word) }
                if (!matches) return@mapNotNull null
                val score = when {
                    titleJoined == joined -> 0
                    titleJoined.startsWith(joined) -> 1
                    words.all { word -> titleWords.any { it.startsWith(word) } } -> 2
                    else -> 3
                }
                score to SearchSuggestion(site.title, site.url, host)
            }
            .sortedWith(compareBy({ it.first }, { it.second.title.length }))
            .map { it.second }
            .take(limit)
    }

    fun looksLikeAddress(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return true
        val host = trimmed.substringBefore('/')
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
    }

    fun directAddress(text: String): SearchSuggestion? {
        if (!looksLikeAddress(text)) return null
        val trimmed = text.trim()
        val url = if (trimmed.startsWith("http", true)) trimmed else "https://$trimmed"
        val host = hostOf(url) ?: return null
        return SearchSuggestion(host.removePrefix("www."), url, host)
    }

    fun hostOf(url: String): String? = try {
        java.net.URI(url).host?.lowercase()
    } catch (_: Exception) {
        null
    }

    fun faviconUrl(url: String): String? =
        hostOf(url)?.let { "https://icons.duckduckgo.com/ip3/$it.ico" }

    suspend fun searchGoogle(query: String): List<SearchSuggestion> {
        return withContext(Dispatchers.IO) {
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            try {
                coroutineScope {
                    val duck = async { searchDuckDuckGo(encodedQuery) }
                    val google = async { searchWithMobileUserAgent(encodedQuery) }
                    val first = duck.await()
                    if (first.isNotEmpty()) {
                        google.cancel()
                        first
                    } else {
                        google.await()
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun searchDuckDuckGo(encodedQuery: String): List<SearchSuggestion> {
        return try {
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"

            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(5000)
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
                .timeout(5000)
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
