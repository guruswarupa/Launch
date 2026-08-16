package com.guruswarupa.launch.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.guruswarupa.launch.R
import com.guruswarupa.launch.services.LaunchNotificationListenerService

class MediaControllerWidget(private val context: Context, private val rootView: View) {
    private val trackTitle: TextView = rootView.findViewById(R.id.media_track_title)
    private val artistName: TextView = rootView.findViewById(R.id.media_artist)
    private val playPauseBtn: ImageButton = rootView.findViewById(R.id.media_play_pause)
    private val prevBtn: ImageButton = rootView.findViewById(R.id.media_prev)
    private val nextBtn: ImageButton = rootView.findViewById(R.id.media_next)
    private val controlsLayout: View = rootView.findViewById(R.id.media_controls_layout)
    private val permissionButton: Button = rootView.findViewById(R.id.request_media_permission_button)

    private var activeController: MediaController? = null
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, LaunchNotificationListenerService::class.java)
    private var sessionListenerRegistered = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val newController = controllers?.firstOrNull()
        if (newController != activeController) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            newController?.registerCallback(callback)

            updateMetadata(newController?.metadata)
            updatePlaybackState(newController?.playbackState)
        }
    }

    init {
        setupListeners()
        permissionButton.setOnClickListener {
            openNotificationSettings()
        }
        registerSessionListener()
        refreshController()
    }

    private fun setupListeners() {
        playPauseBtn.setOnClickListener {
            activeController?.playbackState?.let { state ->
                if (state.state == PlaybackState.STATE_PLAYING) {
                    activeController?.transportControls?.pause()
                } else {
                    activeController?.transportControls?.play()
                }
            }
        }
        prevBtn.setOnClickListener { activeController?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { activeController?.transportControls?.skipToNext() }
    }

    private fun registerSessionListener() {
        if (!sessionListenerRegistered && isNotificationListenerEnabled()) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
                sessionListenerRegistered = true
            } catch (e: SecurityException) {
                sessionListenerRegistered = false
            }
        }
    }

    fun refreshController() {
        if (!isNotificationListenerEnabled()) {
            showPermissionState()
            return
        }

        try {
            permissionButton.visibility = View.GONE
            controlsLayout.visibility = View.VISIBLE

            if (!sessionListenerRegistered) {
                registerSessionListener()
            }

            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val newController = controllers?.firstOrNull()

            if (newController != activeController) {
                activeController?.unregisterCallback(callback)
                activeController = newController
                activeController?.registerCallback(callback)

                updateMetadata(activeController?.metadata)
                updatePlaybackState(activeController?.playbackState)
            }
        } catch (e: SecurityException) {
            showPermissionState()
        }
    }

    private fun showPermissionState() {
        trackTitle.text = context.getString(R.string.dlg_permission_required)
        artistName.text = context.getString(R.string.lbl_to_control_media_please_enable_notification_acce)
        controlsLayout.visibility = View.GONE
        permissionButton.visibility = View.VISIBLE
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata != null) {
            trackTitle.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track"
            artistName.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
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

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (flat.isNullOrEmpty()) return false
        val names = flat.split(":")
        return names.any { name ->
            val componentName = ComponentName.unflattenFromString(name)
            componentName?.packageName == packageName
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
        activeController?.unregisterCallback(callback)
        activeController = null
        if (sessionListenerRegistered) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
            } catch (_: Exception) {
            }
            sessionListenerRegistered = false
        }
    }
}
