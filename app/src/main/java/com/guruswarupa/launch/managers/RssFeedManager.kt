package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Xml
import androidx.core.content.edit
import com.guruswarupa.launch.models.Constants
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.ExecutorService

data class RssFeedSource(
    val title: String,
    val url: String,
    val category: String,
    val isCustom: Boolean = false
)

data class RssArticle(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: Long,
    val source: String,
    val category: String,
    val imageUrl: String? = null
)

class RssFeedManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val backgroundExecutor: ExecutorService
) {
    companion object {
        private const val CACHE_DURATION_MS = 300000L
        private fun googleNewsFeed(query: String): String {
            return "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&hl=en-US&gl=US&ceid=US:en"
        }

        private val CATEGORY_ORDER = listOf(
            "News",
            "India",
            "Technology",
            "Finance",
            "Sports",
            "Entertainment",
            "Health",
            "Science",
            "Travel",
            "Politics",
            "Games",
            "Environment",
            "Education",
            "Automotive",
            "Fitness",
            "Arts"
        )
        private val PRESET_FEEDS = listOf(
            RssFeedSource("The Hindu", "https://www.thehindu.com/feeder/default.rss", "India"),
            RssFeedSource("Hindustan Times India", "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml", "India"),
            RssFeedSource("The Indian Express India", "https://indianexpress.com/section/india/feed/", "India"),
            RssFeedSource("Reuters Technology", googleNewsFeed("Reuters Technology"), "Technology"),
            RssFeedSource("Hacker News", "https://hnrss.org/frontpage", "Technology"),
            RssFeedSource("The Verge", "https://www.theverge.com/rss/index.xml", "Technology"),
            RssFeedSource("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Technology"),
            RssFeedSource("TechCrunch", "https://feeds.feedburner.com/TechCrunch/", "Technology"),
            RssFeedSource("Engadget", "https://www.engadget.com/rss.xml", "Technology"),
            RssFeedSource("Wired", "https://www.wired.com/feed/rss", "Technology"),
            RssFeedSource("Gizmodo", "https://gizmodo.com/rss", "Technology"),
            RssFeedSource("Android Authority", "https://www.androidauthority.com/feed/", "Technology"),
            RssFeedSource("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", "Technology"),
            RssFeedSource("Search Engine Journal", "https://www.searchenginejournal.com/feed/", "Technology"),
            RssFeedSource("ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml", "Science"),
            RssFeedSource("NASA Breaking News", "https://www.nasa.gov/rss/dyn/breaking_news.rss", "Science"),
            RssFeedSource("New Scientist", googleNewsFeed("\"New Scientist\""), "Science"),
            RssFeedSource("Scientific American", googleNewsFeed("\"Scientific American\""), "Science"),
            RssFeedSource("Live Science", googleNewsFeed("\"Live Science\""), "Science"),
            RssFeedSource("Phys.org", "https://phys.org/rss-feed/", "Science"),
            RssFeedSource("Politico Politics", googleNewsFeed("Politico politics"), "Politics"),
            RssFeedSource("NPR Politics", "https://feeds.npr.org/1014/rss.xml", "Politics"),
            RssFeedSource("AP Politics", googleNewsFeed("\"Associated Press\" politics"), "Politics"),
            RssFeedSource("The Hill", "https://thehill.com/feed/", "Politics"),
            RssFeedSource("Polygon", "https://www.polygon.com/rss/index.xml", "Games"),
            RssFeedSource("IGN All", "https://feeds.ign.com/ign/all", "Games"),
            RssFeedSource("GamesRadar", "https://www.gamesradar.com/rss/", "Games"),
            RssFeedSource("GameSpot News", "https://www.gamespot.com/feeds/mashup/", "Games"),
            RssFeedSource("Kotaku", "https://kotaku.com/rss", "Games"),
            RssFeedSource("Variety", "https://variety.com/feed/", "Entertainment"),
            RssFeedSource("Rolling Stone Culture", "https://www.rollingstone.com/culture/culture-news/feed/", "Entertainment"),
            RssFeedSource("The Hollywood Reporter", "https://www.hollywoodreporter.com/feed/", "Entertainment"),
            RssFeedSource("Billboard", "https://www.billboard.com/feed/", "Entertainment"),
            RssFeedSource("TMZ", "https://tmz.com/rss.xml", "Entertainment"),
            RssFeedSource("ESPN Top Headlines", "https://www.espn.com/espn/rss/news", "Sports"),
            RssFeedSource("Sky Sports", "https://www.skysports.com/rss/12040", "Sports"),
            RssFeedSource("CBS Sports", "https://www.cbssports.com/rss/headlines/", "Sports"),
            RssFeedSource("Yahoo Sports", "https://sports.yahoo.com/rss/", "Sports"),
            RssFeedSource("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "Sports"),
            RssFeedSource("Reuters Business", googleNewsFeed("Reuters business"), "Finance"),
            RssFeedSource("MarketWatch Top Stories", "https://feeds.marketwatch.com/marketwatch/topstories/", "Finance"),
            RssFeedSource("CNBC Top News", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114", "Finance"),
            RssFeedSource("Yahoo Finance", "https://finance.yahoo.com/news/rssindex", "Finance"),
            RssFeedSource("Forbes Money", googleNewsFeed("Forbes money"), "Finance"),
            RssFeedSource("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "Finance"),
            RssFeedSource("Business Insider", "https://feeds.businessinsider.com/custom/all", "Finance"),
            RssFeedSource("EdSurge", "https://www.edsurge.com/articles_rss", "Education"),
            RssFeedSource("Education Week", googleNewsFeed("\"Education Week\""), "Education"),
            RssFeedSource("Inside Higher Ed", "https://www.insidehighered.com/rss.xml", "Education"),
            RssFeedSource("Times Higher Education", googleNewsFeed("\"Times Higher Education\""), "Education"),
            RssFeedSource("Breaking Muscle", "https://breakingmuscle.com/feed/", "Fitness"),
            RssFeedSource("Fitness Volt", "https://fitnessvolt.com/feed/", "Fitness"),
            RssFeedSource("Muscle & Fitness", "https://www.muscleandfitness.com/feed/", "Fitness"),
            RssFeedSource("Runner's World", "https://www.runnersworld.com/rss/all.xml", "Fitness"),
            RssFeedSource("Inside Climate News", "https://insideclimatenews.org/feed/", "Environment"),
            RssFeedSource("Yale Climate Connections", "https://yaleclimateconnections.org/feed/", "Environment"),
            RssFeedSource("Yale Environment 360", googleNewsFeed("\"Yale Environment 360\""), "Environment"),
            RssFeedSource("Treehugger", googleNewsFeed("Treehugger"), "Environment"),
            RssFeedSource("Autoblog", googleNewsFeed("Autoblog"), "Automotive"),
            RssFeedSource("Motor1", "https://www.motor1.com/rss/news/all/", "Automotive"),
            RssFeedSource("Car and Driver", "https://www.caranddriver.com/rss/all.xml", "Automotive"),
            RssFeedSource("Top Gear", googleNewsFeed("\"Top Gear\" cars"), "Automotive"),
            RssFeedSource("Condé Nast Traveler", "https://www.cntraveler.com/feed/rss", "Travel"),
            RssFeedSource("Lonely Planet", googleNewsFeed("\"Lonely Planet\" travel"), "Travel"),
            RssFeedSource("Travel + Leisure", googleNewsFeed("\"Travel + Leisure\""), "Travel"),
            RssFeedSource("The Points Guy", "https://thepointsguy.com/feed/", "Travel"),
            RssFeedSource("Medical News Today", googleNewsFeed("\"Medical News Today\""), "Health"),
            RssFeedSource("Harvard Health", googleNewsFeed("\"Harvard Health\""), "Health"),
            RssFeedSource("MedlinePlus", googleNewsFeed("MedlinePlus health"), "Health"),
            RssFeedSource("WebMD", googleNewsFeed("WebMD health"), "Health"),
            RssFeedSource("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", "Health"),
            RssFeedSource("Hyperallergic", "https://hyperallergic.com/feed/", "Arts"),
            RssFeedSource("ARTnews", "https://www.artnews.com/feed/", "Arts"),
            RssFeedSource("Smithsonian Magazine Arts", "https://www.smithsonianmag.com/rss/arts-culture/", "Arts"),
            RssFeedSource("Colossal", "https://www.thisiscolossal.com/feed/", "Arts"),
            RssFeedSource("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "News"),
            RssFeedSource("Reuters World", googleNewsFeed("Reuters world"), "News"),
            RssFeedSource("NPR News", "https://feeds.npr.org/1001/rss.xml", "News"),
            RssFeedSource("AP Top News", googleNewsFeed("\"Associated Press\" top news"), "News"),
            RssFeedSource("USA Today", googleNewsFeed("\"USA Today\""), "News"),
            RssFeedSource("The Guardian World", "https://www.theguardian.com/world/rss", "News"),
            RssFeedSource("New York Times Home", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "News")
        )

        private fun readStringList(sharedPreferences: SharedPreferences, key: String): List<String> {
            val raw = sharedPreferences.getString(key, null) ?: return emptyList()
            return try {
                val values = JSONArray(raw)
                buildList {
                    for (index in 0 until values.length()) {
                        val value = values.optString(index).trim()
                        if (value.isNotEmpty()) {
                            add(value)
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun writeStringList(sharedPreferences: SharedPreferences, key: String, urls: List<String>) {
            val normalized = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val data = JSONArray()
            normalized.forEach(data::put)
            sharedPreferences.edit {
                putString(key, data.toString())
            }
        }

        fun getPresetSources(): List<RssFeedSource> = PRESET_FEEDS

        fun getPresetCategories(): List<String> = CATEGORY_ORDER

        fun getStoredFeedUrls(sharedPreferences: SharedPreferences): List<String> {
            return readStringList(sharedPreferences, Constants.Prefs.RSS_FEED_URLS)
        }

        fun storeFeedUrls(sharedPreferences: SharedPreferences, urls: List<String>) {
            writeStringList(sharedPreferences, Constants.Prefs.RSS_FEED_URLS, urls)
        }

        fun getCustomFeedUrls(sharedPreferences: SharedPreferences): List<String> {
            return readStringList(sharedPreferences, Constants.Prefs.RSS_CUSTOM_FEED_URLS)
        }

        fun storeCustomFeedUrls(sharedPreferences: SharedPreferences, urls: List<String>) {
            writeStringList(sharedPreferences, Constants.Prefs.RSS_CUSTOM_FEED_URLS, urls)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchAllFeeds(onComplete: ((List<RssArticle>) -> Unit)? = null) {
        backgroundExecutor.execute {
            val articles = loadFreshArticles(forceRefresh = false)
            onComplete?.let { callback ->
                mainHandler.post { callback(articles) }
            }
        }
    }

    fun refreshFeeds(onComplete: ((List<RssArticle>) -> Unit)? = null) {
        backgroundExecutor.execute {
            val articles = loadFreshArticles(forceRefresh = true)
            onComplete?.let { callback ->
                mainHandler.post { callback(articles) }
            }
        }
    }

    fun getCachedArticles(): List<RssArticle> = readCachedArticles()

    fun getEnabledCategories(): List<String> {
        val enabledUrls = getFeedUrls().map { it.trim() }.toSet()
        val presetCategories = getPresetSources()
            .filter { enabledUrls.contains(it.url.trim()) }
            .map { it.category }
        val customCategories = getCustomFeedUrls()
            .filter { enabledUrls.contains(it.trim()) }
            .map { "Custom" }
        return (presetCategories + customCategories)
            .distinct()
            .sortedWith(compareBy({ getPresetCategories().indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it }))
    }

    fun addFeedUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isEmpty() || !(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            return false
        }
        val customUrls = getCustomFeedUrls().toMutableList()
        if (customUrls.any { it.equals(normalized, ignoreCase = true) }) {
            return false
        }
        customUrls.add(normalized)
        storeCustomFeedUrls(sharedPreferences, customUrls)
        setFeedEnabled(normalized, true)
        return true
    }

    fun removeFeedUrl(url: String) {
        val filteredEnabled = getFeedUrls().filterNot { it.equals(url, ignoreCase = true) }
        storeFeedUrls(sharedPreferences, filteredEnabled)
        val filteredCustom = getCustomFeedUrls().filterNot { it.equals(url, ignoreCase = true) }
        storeCustomFeedUrls(sharedPreferences, filteredCustom)
    }

    fun getFeedUrls(): List<String> = getStoredFeedUrls(sharedPreferences)

    fun getCustomFeedUrls(): List<String> = Companion.getCustomFeedUrls(sharedPreferences)

    fun isFeedEnabled(url: String): Boolean {
        return getFeedUrls().any { it.equals(url, ignoreCase = true) }
    }

    fun setFeedEnabled(url: String, enabled: Boolean) {
        val updated = getFeedUrls().toMutableList()
        val existingIndex = updated.indexOfFirst { it.equals(url, ignoreCase = true) }
        if (enabled && existingIndex == -1) {
            updated.add(url.trim())
        } else if (!enabled && existingIndex >= 0) {
            updated.removeAt(existingIndex)
        }
        storeFeedUrls(sharedPreferences, updated)
    }

    private fun loadFreshArticles(forceRefresh: Boolean): List<RssArticle> {
        val refreshMinutes = sharedPreferences.getInt(Constants.Prefs.RSS_REFRESH_INTERVAL_MINUTES, 5)
            .coerceIn(5, 60)
        val cacheDuration = refreshMinutes * 60 * 1000L
        if (!forceRefresh && isCacheValid(cacheDuration)) {
            return readCachedArticles()
        }

        val articles = getFeedUrls()
            .flatMap { url -> fetchFeed(url) }
            .distinctBy { it.link }
            .sortedByDescending { it.pubDate }
            .take(150)

        saveCachedArticles(articles)
        return articles
    }

    private fun isCacheValid(cacheDuration: Long = CACHE_DURATION_MS): Boolean {
        val cachedTime = sharedPreferences.getLong(Constants.Prefs.RSS_FEED_CACHE_TIME, 0L)
        if (cachedTime <= 0L) {
            return false
        }
        return System.currentTimeMillis() - cachedTime < cacheDuration
    }

    private fun readCachedArticles(): List<RssArticle> {
        val raw = sharedPreferences.getString(Constants.Prefs.RSS_FEED_CACHE, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        RssArticle(
                            title = item.optString("title"),
                            description = item.optString("description"),
                            link = item.optString("link"),
                            pubDate = item.optLong("pubDate"),
                            source = item.optString("source"),
                            category = item.optString("category").ifBlank { "News" },
                            imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCachedArticles(articles: List<RssArticle>) {
        val array = JSONArray()
        articles.forEach { article ->
            array.put(
                JSONObject().apply {
                    put("title", article.title)
                    put("description", article.description)
                    put("link", article.link)
                    put("pubDate", article.pubDate)
                    put("source", article.source)
                    put("category", article.category)
                    put("imageUrl", article.imageUrl ?: "")
                }
            )
        }
        sharedPreferences.edit {
            putString(Constants.Prefs.RSS_FEED_CACHE, array.toString())
            putLong(Constants.Prefs.RSS_FEED_CACHE_TIME, System.currentTimeMillis())
        }
    }

    private fun fetchFeed(feedUrl: String): List<RssArticle> {
        return try {
            val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                setRequestProperty("User-Agent", "${context.packageName}/rss")
                instanceFollowRedirects = true
            }
            connection.connect()
            val category = resolveCategory(feedUrl)
            BufferedInputStream(connection.inputStream).use { inputStream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream, null)
                parseFeed(parser, feedUrl, category)
            }.also {
                connection.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFeed(parser: XmlPullParser, fallbackSource: String, category: String): List<RssArticle> {
        val articles = mutableListOf<RssArticle>()
        var feedTitle = fallbackSource

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "channel", "feed" -> {
                        feedTitle = parseFeedTitle(parser, fallbackSource)
                    }
                    "item", "entry" -> {
                        parseArticle(parser, feedTitle, category)?.let(articles::add)
                    }
                }
            }
            parser.next()
        }

        return articles
    }

    private fun parseFeedTitle(parser: XmlPullParser, fallbackSource: String): String {
        val parent = parser.name
        val startDepth = parser.depth
        var title = fallbackSource

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == parent)) {
            parser.next()
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("title", ignoreCase = true)) {
                title = parser.nextText().trim().ifEmpty { fallbackSource }
                break
            }
        }

        return title
    }

    private fun parseArticle(parser: XmlPullParser, source: String, category: String): RssArticle? {
        val parent = parser.name
        val startDepth = parser.depth
        var title = ""
        var description = ""
        var link = ""
        var pubDate = 0L
        var imageUrl: String? = null

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == parent)) {
            parser.next()
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name.lowercase(Locale.ROOT)) {
                "title" -> title = parser.nextText().trim()
                "description", "summary", "content" -> if (description.isBlank()) {
                    description = cleanText(parser.nextText())
                } else {
                    skipTag(parser)
                }
                "content:encoded" -> if (description.isBlank()) {
                    description = cleanText(parser.nextText())
                } else {
                    skipTag(parser)
                }
                "link" -> {
                    link = parser.getAttributeValue(null, "href")
                        ?: parser.getAttributeValue("", "href")
                        ?: parser.nextText().trim()
                }
                "pubdate", "published", "updated", "dc:date" -> {
                    val rawDate = parser.nextText().trim()
                    parseDate(rawDate)?.let { pubDate = it }
                }
                "enclosure", "media:content", "media:thumbnail" -> {
                    val url = parser.getAttributeValue(null, "url")
                        ?: parser.getAttributeValue("", "url")
                    if (imageUrl == null && !url.isNullOrBlank()) {
                        imageUrl = url
                    }
                    skipTag(parser)
                }
            }
        }

        if (title.isBlank() || link.isBlank()) {
            return null
        }

        return RssArticle(
            title = title,
            description = description,
            link = link,
            pubDate = pubDate,
            source = source,
            category = category,
            imageUrl = imageUrl
        )
    }

    private fun resolveCategory(feedUrl: String): String {
        return getPresetSources().firstOrNull { it.url.equals(feedUrl, ignoreCase = true) }?.category
            ?: if (getCustomFeedUrls().any { it.equals(feedUrl, ignoreCase = true) }) "Custom" else "News"
    }

    private fun cleanText(raw: String): String {
        val noHtml = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        return noHtml.replace("\\s+".toRegex(), " ").trim()
    }

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) {
            return null
        }

        val formatters = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT
        )

        for (formatter in formatters) {
            try {
                return when (formatter) {
                    DateTimeFormatter.RFC_1123_DATE_TIME -> ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME -> OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli()
                    DateTimeFormatter.ISO_ZONED_DATE_TIME -> ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
                    else -> Instant.parse(value).toEpochMilli()
                }
            } catch (_: DateTimeParseException) {
            }
        }

        return null
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }
}
