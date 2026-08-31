package com.guruswarupa.launch.ui

import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
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
 * The wallpaper page's now-playing presence (see ScreenPagerManager.Page.WALLPAPER): a small
 * floating transport-control pill plus, just below it, auto-scrolling synced lyrics. Both
 * pieces share one MediaSessionMonitor subscription. The controls pill shows whenever a media
 * session exists (mirrors the always-on media controller widget); the lyrics block additionally
 * requires the Settings toggle (on by default) and a lyrics match. Everything hides itself
 * rather than surfacing an error/permission state - the media controller widget on the widgets
 * page already owns that conversation.
 */
class WallpaperMediaController(
    private val activity: MainActivity,
    rootView: View
) : MediaSessionListener {

    private val controlsContainer: LinearLayout = rootView.findViewById(R.id.wallpaper_media_controls_container)
    private val prevBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_prev)
    private val playPauseBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_play_pause)
    private val nextBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_next)

    private val lyricsContainer: LinearLayout = rootView.findViewById(R.id.wallpaper_lyrics_container)
    private val prevText: TextView = rootView.findViewById(R.id.lyrics_prev)
    private val currentText: TextView = rootView.findViewById(R.id.lyrics_current)
    private val nextText: TextView = rootView.findViewById(R.id.lyrics_next)

    private val lyricsManager by lazy { LyricsManager(activity, activity.backgroundExecutor) }
    private val tickHandler = Handler(Looper.getMainLooper())

    private var currentTrack: NowPlaying? = null
    private var lyricsResult: LyricsResult? = null
    private var latestPlaybackState: PlaybackState? = null
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

    init {
        prevBtn.setOnClickListener { activity.mediaSessionMonitor.activeController?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { activity.mediaSessionMonitor.activeController?.transportControls?.skipToNext() }
        playPauseBtn.setOnClickListener {
            val controller = activity.mediaSessionMonitor.activeController ?: return@setOnClickListener
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
    }

    fun setup() {
        // Listening starts lazily from onPageShown/onActivityResume so an idle launcher never
        // registers a media-session listener for a page that isn't displayed.
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
        updateLyricsVisibility()
    }

    private fun isLyricsFeatureEnabled(): Boolean =
        activity.sharedPreferences.getBoolean(Constants.Prefs.WALLPAPER_LYRICS_ENABLED, true)

    private fun updateActiveState() {
        val shouldListen = pageVisible && activityResumed
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
            latestPlaybackState = null
            lastLineIndex = Int.MIN_VALUE
            hideLyrics()
            updateControlsVisibility()
        }
        updateTickerState()
    }

    override fun onTrackChanged(track: NowPlaying?) {
        updateControlsVisibility(track)
        if (track == currentTrack) return
        currentTrack = track
        lyricsResult = null
        lastLineIndex = Int.MIN_VALUE
        hideLyrics()

        if (track != null && track.title.isNotBlank() && track.artist.isNotBlank()) {
            lyricsManager.fetch(track) { result ->
                if (track == currentTrack) {
                    lyricsResult = result
                    renderLyrics()
                }
            }
        }
        updateTickerState()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        latestPlaybackState = state
        playPauseBtn.setImageResource(
            if (state?.state == PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play
        )
        updateTickerState()
    }

    private fun updateControlsVisibility(track: NowPlaying? = currentTrack) {
        controlsContainer.visibility = if (track != null) View.VISIBLE else View.GONE
    }

    private fun updateLyricsVisibility() {
        if (!isLyricsFeatureEnabled()) {
            hideLyrics()
        } else {
            renderLyrics()
        }
    }

    private fun renderLyrics() {
        if (!isLyricsFeatureEnabled()) {
            hideLyrics()
            return
        }
        when (val result = lyricsResult) {
            is LyricsResult.Synced -> {
                lyricsContainer.visibility = View.VISIBLE
                lastLineIndex = Int.MIN_VALUE
                updateCurrentLine()
            }
            is LyricsResult.Plain -> {
                lyricsContainer.visibility = View.VISIBLE
                prevText.text = ""
                nextText.text = ""
                currentText.text = result.text
            }
            LyricsResult.NotFound, null -> hideLyrics()
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

    private fun hideLyrics() {
        lyricsContainer.visibility = View.GONE
    }

    private fun updateTickerState() {
        val hasSyncedLyrics = isLyricsFeatureEnabled() && lyricsResult is LyricsResult.Synced
        val isPlaying = latestPlaybackState?.state == PlaybackState.STATE_PLAYING
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
