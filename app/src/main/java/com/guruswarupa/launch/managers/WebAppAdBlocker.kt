package com.guruswarupa.launch.managers

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import com.guruswarupa.launch.core.BackgroundWork
import java.io.ByteArrayInputStream
import java.util.Locale

object WebAppAdBlocker {
    private const val HOSTS_ASSET = "adblock_hosts.txt"

    private val builtInHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adservice.google.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "serving-sys.com",
        "adsafeprotected.com",
        "moatads.com",
        "media.net",
        "adroll.com",
        "adform.net",
        "yieldmo.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "propellerads.com",
        "adcash.com",
        "clickadu.com",
        "criteo.com",
        "criteo.net",
        "amazon-adsystem.com",
        "casalemedia.com",
        "contextweb.com",
        "smartadserver.com",
        "adsrvr.org",
        "scorecardresearch.com",
        "quantserve.com",
        "zedo.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "popcash.net",
        "revcontent.com",
        "mgid.com",
        "teads.tv",
        "33across.com",
        "sharethrough.com",
        "indexww.com",
        "lijit.com",
        "sovrn.com",
        "bidswitch.net",
        "advertising.com",
        "adtechus.com",
        "everesttech.net",
        "mathtag.com",
        "bluekai.com",
        "krxd.net",
        "demdex.net",
        "adition.com",
        "inmobi.com",
        "applovin.com",
        "unityads.unity3d.com",
        "ads.mopub.com",
        "supersonicads.com",
        "vungle.com",
        "chartboost.com",
        "adcolony.com",
        "onesignal.com"
    )

    private val blockedHostLabels = setOf(
        "ads",
        "adservice",
        "adservices",
        "adserver",
        "adlog",
        "advertising",
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "pagead",
        "adsystem"
    )

    private val blockedPathFragments = listOf(
        "/ads/",
        "/adserver/",
        "/adstream/",
        "/pagead/",
        "/adsbygoogle",
        "/gampad/",
        "/doubleclick/",
        "/adsense/",
        "/prebid",
        "/advert/",
        "/adframe",
        "/banner-ads/"
    )

    @Volatile
    private var hosts: Set<String> = builtInHosts

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val appContext = context.applicationContext
        BackgroundWork.execute {
            if (loaded) return@execute
            try {
                val merged = HashSet<String>(builtInHosts)
                appContext.assets.open(HOSTS_ASSET).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val host = line.trim().lowercase(Locale.ROOT)
                        if (host.isNotEmpty() && !host.startsWith("#")) merged.add(host)
                    }
                }
                hosts = merged
                loaded = true
            } catch (_: Exception) {
            }
        }
    }

    private fun hostIsBlocked(host: String): Boolean {
        val set = hosts
        var start = 0
        while (true) {
            if (host.substring(start) in set) return true
            val next = host.indexOf('.', start)
            if (next < 0) return false
            start = next + 1
        }
    }

    fun shouldBlock(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
        if (host.isBlank()) return false

        if (hostIsBlocked(host)) return true

        if (host.split('.').any { it in blockedHostLabels }) return true

        val path = uri?.path?.lowercase(Locale.ROOT).orEmpty()
        if (path.isNotEmpty() && blockedPathFragments.any { path.contains(it) }) return true

        return false
    }

    private fun isYouTubeHost(host: String?): Boolean {
        val h = host?.lowercase(Locale.ROOT).orEmpty()
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") ||
            h == "youtube-nocookie.com" || h.endsWith(".youtube-nocookie.com")
    }

    fun shouldBlockRequest(uri: Uri?, pageUrl: String?): Boolean {
        if (isYouTubeHost(pageUrl?.let { Uri.parse(it).host })) return false
        return shouldBlock(uri)
    }

    fun cosmeticScriptFor(pageUrl: String?): String {
        return if (isYouTubeHost(pageUrl?.let { Uri.parse(it).host })) youTubeScript else cosmeticScript
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    private const val HIDE_SELECTORS =
        "ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads_iframe\"],[id^=\"div-gpt-ad\"],[id^=\"google_ads\"]," +
            "[data-ad-slot],[data-ad-client],[data-google-query-id],[aria-label=\"Advertisement\"],[aria-label=\"advertisement\"]," +
            "iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"],iframe[src*=\"/ads/\"],iframe[id^=\"aswift\"]," +
            "[id^=\"taboola-\"],.trc_related_container,.OUTBRAIN,[id^=\"outbrain\"],[class*=\"adsbox\"]," +
            "[class*=\"ad-banner\"],[class*=\"ad-slot\"],[class*=\"ad-container\"],[class*=\"advert\"]," +
            "[id*=\"ad-container\"],[id*=\"advert\"],.sponsored-ad,.ad-wrapper,.ad-unit,.ads-container," +
            "ytd-ad-slot-renderer,ytd-promoted-sparkles-web-renderer,#masthead-ad,.ytp-ad-module"

    val cosmeticScript: String = """
        (function() {
          if (window.__launchAdBlock) return;
          window.__launchAdBlock = true;
          var css = '$HIDE_SELECTORS { display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; }';
          function inject() {
            var root = document.head || document.documentElement;
            if (!root || document.getElementById('__launch_adblock_css')) return;
            var style = document.createElement('style');
            style.id = '__launch_adblock_css';
            style.textContent = css;
            root.appendChild(style);
          }
          inject();
          new MutationObserver(inject).observe(document.documentElement, { childList: true });
          var skip = function() {
            var btn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
            if (btn) btn.click();
            var v = document.querySelector('.ad-showing video');
            if (v && isFinite(v.duration)) { v.muted = true; v.currentTime = v.duration; }
          };
          setInterval(skip, 400);
          var origOpen = window.open;
          window.open = function() { return null; };
        })();
    """.trimIndent()

    private val youTubeScript: String = """
        (function() {
          if (window.__launchYt) return;
          window.__launchYt = true;
          var css = 'ytd-ad-slot-renderer,ytd-promoted-sparkles-web-renderer,ytd-display-ad-renderer,ytd-banner-promo-renderer,' +
            '#masthead-ad,.ytp-ad-overlay-container,.ytp-ad-text-overlay,.ytp-ad-image-overlay,ytm-promoted-sparkles-web-renderer,' +
            'ytm-companion-slot,ad-slot-renderer,#player-ads,.ytp-ad-module .ytp-ad-player-overlay { display: none !important; }';
          function inject() {
            var root = document.head || document.documentElement;
            if (!root || document.getElementById('__launch_yt_css')) return;
            var style = document.createElement('style');
            style.id = '__launch_yt_css';
            style.textContent = css;
            root.appendChild(style);
          }
          inject();
          new MutationObserver(inject).observe(document.documentElement, { childList: true });
          var mutedByUs = false;
          setInterval(function() {
            var player = document.querySelector('#movie_player, .html5-video-player');
            var showing = player && player.classList.contains('ad-showing');
            var video = document.querySelector('video.html5-main-video') || document.querySelector('video');
            if (showing && video) {
              if (!video.muted) { video.muted = true; mutedByUs = true; }
              try { video.playbackRate = 16; } catch (e) {}
              if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
                try { video.currentTime = video.duration - 0.05; } catch (e) {}
              }
              var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button');
              if (skip) skip.click();
            } else if (video) {
              if (video.playbackRate === 16) video.playbackRate = 1;
              if (mutedByUs) { video.muted = false; mutedByUs = false; }
            }
            var close = document.querySelector('.ytp-ad-overlay-close-button');
            if (close) close.click();
          }, 200);
        })();
    """.trimIndent()
}
