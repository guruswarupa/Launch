package com.guruswarupa.launch.widgets

import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.MediaSessionListener
import com.guruswarupa.launch.managers.MediaSessionMonitor
import com.guruswarupa.launch.managers.NowPlaying

class MediaControllerWidget(
    private val context: Context,
    private val rootView: View,
    private val mediaSessionMonitor: MediaSessionMonitor
) : MediaSessionListener {
    private val trackTitle: TextView = rootView.findViewById(R.id.media_track_title)
    private val artistName: TextView = rootView.findViewById(R.id.media_artist)
    private val playPauseBtn: ImageButton = rootView.findViewById(R.id.media_play_pause)
    private val prevBtn: ImageButton = rootView.findViewById(R.id.media_prev)
    private val nextBtn: ImageButton = rootView.findViewById(R.id.media_next)
    private val controlsLayout: View = rootView.findViewById(R.id.media_controls_layout)
    private val permissionButton: Button = rootView.findViewById(R.id.request_media_permission_button)

    init {
        setupListeners()
        permissionButton.setOnClickListener {
            openNotificationSettings()
        }
        mediaSessionMonitor.addListener(this)
        refreshController()
    }

    private fun setupListeners() {
        playPauseBtn.setOnClickListener {
            mediaSessionMonitor.activeController?.playbackState?.let { state ->
                if (state.state == PlaybackState.STATE_PLAYING) {
                    mediaSessionMonitor.activeController?.transportControls?.pause()
                } else {
                    mediaSessionMonitor.activeController?.transportControls?.play()
                }
            }
        }
        prevBtn.setOnClickListener { mediaSessionMonitor.activeController?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { mediaSessionMonitor.activeController?.transportControls?.skipToNext() }
    }

    fun refreshController() {
        if (!mediaSessionMonitor.isNotificationListenerEnabled()) {
            showPermissionState()
            return
        }

        permissionButton.visibility = View.GONE
        controlsLayout.visibility = View.VISIBLE
        mediaSessionMonitor.refresh()
    }

    override fun onTrackChanged(track: NowPlaying?) {
        updateMetadata(track)
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        updatePlaybackState(state)
    }

    private fun showPermissionState() {
        trackTitle.text = context.getString(R.string.dlg_permission_required)
        artistName.text = context.getString(R.string.lbl_to_control_media_please_enable_notification_acce)
        controlsLayout.visibility = View.GONE
        permissionButton.visibility = View.VISIBLE
    }

    private fun updateMetadata(track: NowPlaying?) {
        if (track != null) {
            trackTitle.text = track.title.ifEmpty { "Unknown Track" }
            artistName.text = track.artist.ifEmpty { "Unknown Artist" }
        } else {
            trackTitle.text = context.getString(R.string.not_playing)
            artistName.text = ""
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
            playPauseBtn.setImageResource(R.drawable.ic_pause)
        } else {
            playPauseBtn.setImageResource(R.drawable.ic_play)
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, context.getString(R.string.toast_enable_launch_in_the_list), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_could_not_open_settings), Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanup() {
        mediaSessionMonitor.removeListener(this)
    }
}
