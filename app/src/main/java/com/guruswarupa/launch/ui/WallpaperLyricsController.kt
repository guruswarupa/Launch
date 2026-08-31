package com.guruswarupa.launch.ui

import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.LrcParser
import com.guruswarupa.launch.managers.LyricsManager
import com.guruswarupa.launch.managers.LyricsResult
import com.guruswarupa.launch.managers.MediaSessionListener
import com.guruswarupa.launch.managers.NowPlaying
import com.guruswarupa.launch.models.Constants

/**
 * Ambient, auto-scrolling song lyrics shown on the wallpaper page while music plays (see
 * ScreenPagerManager.Page.WALLPAPER). On by default (toggle in Settings > Widgets); hides
 * itself whenever there's nothing to show rather than surfacing any error/permission state -
 * the media controller widget already owns that conversation.
 */
class WallpaperLyricsController(
    private val activity: MainActivity,
    rootView: View
) : MediaSessionListener {

    private val container: LinearLayout = rootView.findViewById(R.id.wallpaper_lyrics_container)
    private val prevText: TextView = rootView.findViewById(R.id.lyrics_prev)
    private val currentText: TextView = rootView.findViewById(R.id.lyrics_current)
    private val nextText: TextView = rootView.findViewById(R.id.lyrics_next)

    private val lyricsManager by lazy { LyricsManager(activity, activity.backgroundExecutor) }
    private val tickHandler = Handler(Looper.getMainLooper())

    private var currentTrack: NowPlaying? = null
    private var lyricsResult: LyricsResult? = null
    private var lastLineIndex = Int.MIN_VALUE
    private var listenerAttached = false
    private var pageVisible = false
    private var activityResumed = true

    private val ticker = object : Runnable {
        override fun run() {
            updateCurrentLine()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun setup() {
        // Listening starts lazily from onPageShown/onActivityResume so an idle launcher never
        // registers a media-session listener for a feature it isn't displaying.
    }

    fun onPageShown() {
        pageVisible = true
        updateActiveState()
    }

    fun onPageHidden() {
        pageVisible = false
        updateActiveState()
    }

    fun onActivityResume() {
        activityResumed = true
        updateActiveState()
    }

    fun onActivityPause() {
        activityResumed = false
        updateActiveState()
    }

    fun onActivityDestroy() {
        stopTicker()
        if (listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false
        }
    }

    fun onSettingsUpdated() {
        updateActiveState()
    }

    private fun isFeatureEnabled(): Boolean =
        activity.sharedPreferences.getBoolean(Constants.Prefs.WALLPAPER_LYRICS_ENABLED, true)

    private fun updateActiveState() {
        val shouldListen = isFeatureEnabled() && pageVisible && activityResumed
        if (shouldListen && !listenerAttached) {
            activity.mediaSessionMonitor.addListener(this)
            listenerAttached = true
        } else if (!shouldListen && listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false
            // Reset so re-attaching later (song unchanged) still re-fetches/re-renders instead
            // of short-circuiting on an unchanged NowPlaying and leaving the view hidden.
            currentTrack = null
            lyricsResult = null
            lastLineIndex = Int.MIN_VALUE
            hide()
        }
        updateTickerState()
    }

    override fun onTrackChanged(track: NowPlaying?) {
        if (track == currentTrack) return
        currentTrack = track
        lyricsResult = null
        lastLineIndex = Int.MIN_VALUE
        hide()

        if (track != null && track.title.isNotBlank() && track.artist.isNotBlank()) {
            lyricsManager.fetch(track) { result ->
                if (track == currentTrack) {
                    lyricsResult = result
                    render()
                }
            }
        }
        updateTickerState()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        updateTickerState()
    }

    private fun render() {
        when (val result = lyricsResult) {
            is LyricsResult.Synced -> {
                container.visibility = View.VISIBLE
                lastLineIndex = Int.MIN_VALUE
                updateCurrentLine()
            }
            is LyricsResult.Plain -> {
                container.visibility = View.VISIBLE
                prevText.text = ""
                nextText.text = ""
                currentText.text = result.text
            }
            LyricsResult.NotFound, null -> hide()
        }
        updateTickerState()
    }

    private fun updateCurrentLine() {
        val lines = (lyricsResult as? LyricsResult.Synced)?.lines ?: return
        val posMs = activity.mediaSessionMonitor.estimatedPositionMs()
        val index = LrcParser.indexAt(lines, posMs)
        if (index == lastLineIndex) return
        lastLineIndex = index

        prevText.text = lines.getOrNull(index - 1)?.text.orEmpty()
        currentText.text = lines.getOrNull(index)?.text.orEmpty()
        nextText.text = lines.getOrNull(index + 1)?.text.orEmpty()

        currentText.alpha = 0f
        currentText.animate().alpha(1f).setDuration(CROSSFADE_MS).start()
    }

    private fun hide() {
        container.visibility = View.GONE
    }

    private fun updateTickerState() {
        val hasSyncedLyrics = lyricsResult is LyricsResult.Synced
        val isPlaying = activity.mediaSessionMonitor.activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (listenerAttached && hasSyncedLyrics && isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        tickHandler.removeCallbacks(ticker)
        tickHandler.post(ticker)
    }

    private fun stopTicker() {
        tickHandler.removeCallbacks(ticker)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 250L
        private const val CROSSFADE_MS = 150L
    }
}
