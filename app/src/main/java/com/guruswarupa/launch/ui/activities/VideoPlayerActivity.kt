package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.document.BinaryConverters
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VideoPlayerActivity : VaultBaseActivity() {

    private lateinit var root: View
    private lateinit var playerView: PlayerView
    private lateinit var controlsTop: View
    private lateinit var controlsCenter: View
    private lateinit var controlsBottom: View
    private lateinit var btnPlay: ImageView
    private lateinit var btnLock: ImageView
    private lateinit var btnUnlock: ImageView
    private lateinit var btnFullscreen: ImageView
    private lateinit var btnLoop: ImageView
    private lateinit var btnSpeed: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var buffering: View
    private lateinit var indicator: View
    private lateinit var indicatorIcon: ImageView
    private lateinit var indicatorText: TextView
    private lateinit var seekPreview: TextView
    private lateinit var flashLeft: TextView
    private lateinit var flashRight: TextView
    private lateinit var audioPanel: View

    private var player: ExoPlayer? = null
    private var file: File? = null
    private var sourceUri: Uri? = null
    private var displayName = ""
    private var deleteOnExit = false
    private var isAudio = false

    private var controlsVisible = true
    private var locked = false
    private var landscapeForced = false
    private var userSeeking = false
    private var loopEnabled = false
    private var speed = 1f
    private var zoomFill = false
    private var longPressBoost = false

    private enum class Aspect(val labelRes: Int, val ratio: Float?) {
        FIT(R.string.aspect_fit, null), FILL(R.string.aspect_fill, null), STRETCH(R.string.aspect_stretch, null),
        R16_9(R.string.aspect_16_9, 16f / 9f), R4_3(R.string.aspect_4_3, 4f / 3f), R21_9(R.string.aspect_21_9, 21f / 9f),
        R1_1(R.string.aspect_1_1, 1f), ORIGINAL(R.string.aspect_original, null)
    }

    private var aspect = Aspect.FIT
    private lateinit var btnAspect: TextView
    private var resumeId = ""
    private var resumeTicks = 0
    private var resumeChip: TextView? = null
    private val palette by lazy { FilesPalette(this) }
    private val sheets by lazy { FilesDialogs(this, palette) }
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private val hideIndicator = Runnable { indicator.animate().alpha(0f).setDuration(200).start() }
    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            if (++resumeTicks % 20 == 0) saveResume()
            handler.postDelayed(this, 250)
        }
    }

    private lateinit var audioManager: AudioManager
    private var gestureMode = 0
    private var startBrightness = 0.5f
    private var startVolume = 0
    private var seekStartMs = 0L
    private var seekTargetMs = 0L
    private var siblings: Array<String>? = null
    private var siblingIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_video_player)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        bindViews()

        val viaAlias = intent.component?.className != VideoPlayerActivity::class.java.name
        val data = intent.data
        val target: File?
        if (viaAlias || data != null && intent.getStringExtra(EXTRA_PATH) == null) {
            if (data == null) {
                Toast.makeText(this, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            sourceUri = data
            target = null
            displayName = queryDisplayName(data)
            deleteOnExit = false
            val type = contentResolver.getType(data).orEmpty()
            isAudio = type.startsWith("audio/") || displayName.substringAfterLast('.', "").lowercase() in BinaryConverters.AUDIO_EXTENSIONS
        } else {
            val path = intent.getStringExtra(EXTRA_PATH)
            displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
            deleteOnExit = intent.getBooleanExtra(EXTRA_DELETE, false)
            target = path?.let { File(it) }
            if (target == null || !target.exists()) {
                Toast.makeText(this, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            file = target
            sourceUri = Uri.fromFile(target)
            if (displayName.isEmpty()) displayName = target.name
            isAudio = target.extension.lowercase() in BinaryConverters.AUDIO_EXTENSIONS
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            controlsTop.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(12), controlsTop.paddingBottom)
            controlsBottom.setPadding(bars.left, controlsBottom.paddingTop, bars.right, bars.bottom + dp(8))
            insets
        }

        findViewById<TextView>(R.id.video_title).text = displayName
        setupPlayer(sourceUri!!)
        setupButtons()
        setupGestures()
        if (isAudio) setupAudioPanel(sourceUri!!)
        setupSiblings()
        handler.post(progressTick)
        scheduleHide()
    }

    private fun setupSiblings() {
        val list = intent.getStringArrayExtra(EXTRA_SIBLINGS) ?: return
        siblings = list
        siblingIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        fun skip(res: Int, dir: Int) = ImageView(this).apply {
            setImageResource(res)
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            alpha = if (siblingIndex + dir in list.indices) 1f else 0.3f
            setOnClickListener { goSibling(dir) }
        }
        val prev = skip(R.drawable.ic_media_prev, -1)
        val next = skip(R.drawable.ic_media_next, 1)
        (controlsCenter as android.widget.LinearLayout).addView(prev, 0, android.widget.LinearLayout.LayoutParams(dp(48), dp(48)))
        (controlsCenter as android.widget.LinearLayout).addView(next, android.widget.LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun goSibling(dir: Int) {
        val list = siblings ?: return
        val n = siblingIndex + dir
        if (n !in list.indices) return
        startActivity(Intent(this, VideoPlayerActivity::class.java)
            .putExtra(EXTRA_PATH, list[n])
            .putExtra(EXTRA_NAME, File(list[n]).name)
            .putExtra(EXTRA_SIBLINGS, list)
            .putExtra(EXTRA_INDEX, n))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun bindViews() {
        root = findViewById(R.id.player_root)
        playerView = findViewById(R.id.player_view)
        controlsTop = findViewById(R.id.controls_top)
        controlsCenter = findViewById(R.id.controls_center)
        controlsBottom = findViewById(R.id.controls_bottom)
        btnPlay = findViewById(R.id.btn_play)
        btnLock = findViewById(R.id.btn_lock)
        btnUnlock = findViewById(R.id.btn_unlock)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnLoop = findViewById(R.id.btn_loop)
        btnSpeed = findViewById(R.id.btn_speed)
        btnAspect = findViewById(R.id.btn_aspect)
        seekBar = findViewById(R.id.seek_bar)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        buffering = findViewById(R.id.buffering)
        indicator = findViewById(R.id.indicator)
        indicatorIcon = findViewById(R.id.indicator_icon)
        indicatorText = findViewById(R.id.indicator_text)
        seekPreview = findViewById(R.id.seek_preview)
        flashLeft = findViewById(R.id.seek_flash_left)
        flashRight = findViewById(R.id.seek_flash_right)
        audioPanel = findViewById(R.id.audio_panel)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun queryDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "video" }
    }

    private fun subtitleMime(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        "ttml", "xml" -> "application/ttml+xml"
        else -> null
    }

    /** Subtitle files that sit next to the video and share its name are picked up automatically. */
    private fun sidecarSubtitles(): List<MediaItem.SubtitleConfiguration> {
        val video = file ?: return emptyList()
        val dir = video.parentFile ?: return emptyList()
        val base = video.nameWithoutExtension
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name != video.name && it.name.startsWith(base) && subtitleMime(it.name) != null && it.extension.lowercase() != "xml" }
            .take(6)
            .map { MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(it)).setMimeType(subtitleMime(it.name)!!).setLabel(it.name.removePrefix(base).trimStart('.', '_', '-', ' ').ifBlank { it.extension.uppercase() }).build() }
    }

    private fun setupPlayer(source: Uri) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        resumeId = file?.absolutePath ?: source.toString()
        val item = MediaItem.Builder().setUri(source).setSubtitleConfigurations(sidecarSubtitles()).build()
        val saved = VideoResume.get(this, resumeId)
        if (saved != null) exo.setMediaItem(item, saved.position) else exo.setMediaItem(item)
        aspect = runCatching { Aspect.valueOf(getSharedPreferences("video_player", MODE_PRIVATE).getString("aspect", "FIT")!!) }.getOrDefault(Aspect.FIT)
        zoomFill = aspect == Aspect.FILL
        if (!isAudio) applyAspect()
        if (saved != null && !isAudio) showResumeChip(saved.position)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_ENDED) {
                    VideoResume.clear(this@VideoPlayerActivity, resumeId)
                    setControlsVisible(true)
                    handler.removeCallbacks(hideControls)
                }
                updatePlayIcon()
                updateProgress()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                applyAspect()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleHide()
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    saveResume()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@VideoPlayerActivity, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                openWith()
                finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun saveResume() {
        val p = player ?: return
        if (resumeId.isEmpty() || p.playbackState == Player.STATE_IDLE) return
        VideoResume.save(this, resumeId, displayName, p.currentPosition, p.duration.takeIf { it > 0 } ?: 0L)
    }

    private fun showResumeChip(position: Long) {
        val chip = TextView(this).apply {
            text = getString(R.string.video_resumed, formatTime(position)) + "   •   " + getString(R.string.video_start_over)
            setTextColor(Color.WHITE); textSize = 13.5f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xCC202020.toInt()); cornerRadius = dp(22).toFloat() }
            setOnClickListener {
                player?.seekTo(0)
                VideoResume.clear(this@VideoPlayerActivity, resumeId)
                animate().alpha(0f).setDuration(150).withEndAction { visibility = View.GONE }.start()
            }
        }
        resumeChip = chip
        root.let { (it as FrameLayout).addView(chip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM or android.view.Gravity.START).apply { setMargins(dp(20), 0, 0, dp(110)) }) }
        handler.postDelayed({ chip.animate().alpha(0f).setDuration(300).withEndAction { chip.visibility = View.GONE }.start() }, 6000)
    }

    // ---- aspect ratio / view modes ----

    private fun setAspect(mode: Aspect, announce: Boolean = false) {
        aspect = mode
        zoomFill = mode == Aspect.FILL
        getSharedPreferences("video_player", MODE_PRIVATE).edit().putString("aspect", mode.name).apply()
        applyAspect()
        if (announce) showIndicator(R.drawable.ic_media_fullscreen, getString(mode.labelRes))
    }

    private fun applyAspect() {
        if (!::btnAspect.isInitialized) return
        btnAspect.text = getString(aspect.labelRes)
        val frame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return
        val size = player?.videoSize
        val vw = size?.width ?: 0
        val vh = size?.height ?: 0
        val natural = if (vw > 0 && vh > 0) vw * (size?.pixelWidthHeightRatio ?: 1f) / vh else 0f
        frame.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER)
        when (aspect) {
            Aspect.FIT -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; if (natural > 0) frame.setAspectRatio(natural) }
            Aspect.FILL -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; if (natural > 0) frame.setAspectRatio(natural) }
            Aspect.STRETCH -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            Aspect.ORIGINAL -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                if (vw > 0 && vh > 0) frame.layoutParams = FrameLayout.LayoutParams((vw * (size?.pixelWidthHeightRatio ?: 1f)).toInt(), vh, android.view.Gravity.CENTER)
            }
            else -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; aspect.ratio?.let { frame.setAspectRatio(it) } }
        }
    }

    private fun showAspectSheet() {
        sheets.sheet(getString(R.string.video_aspect), Aspect.values().map { m ->
            SheetItem(R.drawable.ic_f_view_grid, getString(m.labelRes), checked = m == aspect) { setAspect(m) }
        })
    }

    // ---- tracks ----

    private fun trackLabel(group: Tracks.Group, index: Int): String {
        val f = group.getTrackFormat(0)
        val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale(it).displayLanguage }
        val bits = ArrayList<String>()
        (f.label ?: lang)?.let { bits.add(it) }
        if (f.channelCount > 0 && group.type == C.TRACK_TYPE_AUDIO) bits.add(when (f.channelCount) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount}ch" })
        f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { group.type == C.TRACK_TYPE_AUDIO }?.let { bits.add(it) }
        return if (bits.isEmpty()) getString(R.string.video_track_n, index + 1) else bits.joinToString(" · ")
    }

    private fun showAudioTracks() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (groups.isEmpty()) { Toast.makeText(this, R.string.video_no_tracks, Toast.LENGTH_SHORT).show(); return }
        sheets.sheet(getString(R.string.video_audio_track), groups.mapIndexed { i, g ->
            SheetItem(R.drawable.ic_f_music, trackLabel(g, i), checked = g.isSelected) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            }
        })
    }

    private fun showSubtitles() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        val textOff = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val items = ArrayList<SheetItem>()
        items.add(SheetItem(R.drawable.ic_f_doc, getString(R.string.video_subs_off), checked = textOff || groups.none { it.isSelected }) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        })
        groups.forEachIndexed { i, g ->
            items.add(SheetItem(R.drawable.ic_f_doc, trackLabel(g, i), checked = g.isSelected && !textOff) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            })
        }
        items.add(SheetItem(R.drawable.ic_f_newfolder, getString(R.string.video_subs_add)) {
            try { subtitlePicker.launch(arrayOf("*/*")) } catch (_: Exception) {}
        })
        sheets.sheet(getString(R.string.video_subtitles), items)
    }

    private fun addSubtitle(uri: Uri) {
        val p = player ?: return
        val name = queryDisplayName(uri)
        val mime = subtitleMime(name) ?: "application/x-subrip"
        val pos = p.currentPosition
        val wasPlaying = p.playWhenReady
        val item = MediaItem.Builder().setUri(sourceUri!!)
            .setSubtitleConfigurations(sidecarSubtitles() + MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLabel(name).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build())
            .build()
        p.setMediaItem(item, pos)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    private fun showVideoInfo() {
        val p = player ?: return
        val rows = ArrayList<Pair<String, String>>()
        p.videoFormat?.let { v ->
            if (v.width > 0) rows.add(getString(R.string.video_info_resolution) to "${v.width} × ${v.height}")
            v.sampleMimeType?.let { rows.add(getString(R.string.video_info_codec) to (it.substringAfter('/').uppercase() + (v.codecs?.let { c -> " ($c)" } ?: ""))) }
            if (v.frameRate > 0) rows.add(getString(R.string.video_info_framerate) to String.format(Locale.US, "%.2f fps", v.frameRate))
            if (v.bitrate > 0) rows.add(getString(R.string.video_info_bitrate) to String.format(Locale.US, "%.1f Mbps", v.bitrate / 1_000_000f))
        }
        p.audioFormat?.let { a ->
            rows.add(getString(R.string.video_info_audio) to listOfNotNull(a.sampleMimeType?.substringAfter('/')?.uppercase(), if (a.channelCount > 0) "${a.channelCount}ch" else null, if (a.sampleRate > 0) "${a.sampleRate / 1000} kHz" else null).joinToString(" · "))
        }
        if (p.duration > 0) rows.add(getString(R.string.video_info_duration) to formatTime(p.duration))
        file?.let { rows.add(getString(R.string.video_info_size) to android.text.format.Formatter.formatFileSize(this, it.length())) }
        sheets.details(displayName, R.drawable.ic_f_video, palette.accent, rows)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnPlay.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.btn_rewind).setOnClickListener { seekBy(-10_000) }
        findViewById<View>(R.id.btn_forward).setOnClickListener { seekBy(10_000) }
        btnLock.setOnClickListener { setLocked(true) }
        btnUnlock.setOnClickListener { setLocked(false) }
        btnFullscreen.setOnClickListener { toggleOrientation() }
        btnLoop.setOnClickListener {
            loopEnabled = !loopEnabled
            player?.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            btnLoop.alpha = if (loopEnabled) 1f else 0.6f
            scheduleHide()
        }
        btnSpeed.setOnClickListener { showSpeedMenu(it) }
        btnAspect.setOnClickListener { showAspectSheet(); scheduleHide() }
        findViewById<View>(R.id.btn_share).setOnClickListener { share() }
        findViewById<View>(R.id.btn_more).setOnClickListener { showMoreMenu(it) }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0) timeCurrent.text = formatTime(duration * progress / 1000)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                userSeeking = true
                handler.removeCallbacks(hideControls)
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val duration = player?.duration ?: 0L
                if (duration > 0 && bar != null) player?.seekTo(duration * bar.progress / 1000)
                userSeeking = false
                scheduleHide()
            }
        })
    }

    private fun setupAudioPanel(source: Uri) {
        audioPanel.visibility = View.VISIBLE
        playerView.visibility = View.INVISIBLE
        val title = findViewById<TextView>(R.id.audio_title)
        val artist = findViewById<TextView>(R.id.audio_artist)
        title.text = displayName.substringBeforeLast('.')
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, source)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title.text = it }
            artist.text = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = com.guruswarupa.launch.utils.SampledBitmap.fromBytes(art, 1024)
                if (bitmap != null) {
                    val image = findViewById<ImageView>(R.id.audio_art)
                    image.setPadding(0, 0, 0, 0)
                    image.setImageBitmap(bitmap)
                }
            }
            retriever.release()
        } catch (_: Exception) {
        }
        controlsVisible = true
    }

    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (locked || isAudio) return false
                if (detector.scaleFactor > 1.04f && !zoomFill) {
                    zoomFill = true
                    setAspect(Aspect.FILL, announce = true)
                } else if (detector.scaleFactor < 0.96f && zoomFill) {
                    zoomFill = false
                    setAspect(Aspect.FIT, announce = true)
                }
                return true
            }
        })
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gestureMode = 0
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!locked) setControlsVisible(!controlsVisible || isAudio)
                else showUnlock()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val width = root.width
                when {
                    e.x < width / 3f -> { seekBy(-10_000); flash(flashLeft) }
                    e.x > width * 2f / 3f -> { seekBy(10_000); flash(flashRight) }
                    else -> togglePlay()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (locked) return
                longPressBoost = true
                player?.setPlaybackSpeed(2f)
                showIndicator(R.drawable.ic_media_fullscreen, "2x")
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || e1 == null || scaleDetector.isInProgress) return false
                if (gestureMode == 0) {
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (abs(dx) < dp(12) && abs(dy) < dp(12)) return false
                    gestureMode = when {
                        abs(dx) > abs(dy) -> if (isAudio) -1 else 1
                        e1.x < root.width / 2f -> 2
                        else -> 3
                    }
                    when (gestureMode) {
                        1 -> {
                            seekStartMs = player?.currentPosition ?: 0L
                            seekTargetMs = seekStartMs
                        }
                        2 -> startBrightness = currentBrightness()
                        3 -> startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    }
                }
                val totalDx = e2.x - e1.x
                val totalDy = e1.y - e2.y
                when (gestureMode) {
                    1 -> {
                        val duration = max(player?.duration ?: 0L, 0L)
                        val delta = (totalDx / root.width * 90_000).toLong()
                        seekTargetMs = (seekStartMs + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                        seekPreview.text = "${formatTime(seekTargetMs)} / ${formatTime(duration)}"
                        seekPreview.alpha = 1f
                    }
                    2 -> {
                        val value = (startBrightness + totalDy / root.height * 1.4f).coerceIn(0.02f, 1f)
                        val attrs = window.attributes
                        attrs.screenBrightness = value
                        window.attributes = attrs
                        showIndicator(R.drawable.ic_media_brightness, "${(value * 100).toInt()}%")
                    }
                    3 -> {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val value = (startVolume + totalDy / root.height * maxVol * 1.4f).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        showIndicator(R.drawable.ic_media_volume, "${(value * 100 / max(maxVol, 1))}%")
                    }
                }
                return true
            }
        })
        root.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == 1) {
                    player?.seekTo(seekTargetMs)
                    seekPreview.animate().alpha(0f).setDuration(160).start()
                }
                if (longPressBoost) {
                    longPressBoost = false
                    player?.setPlaybackSpeed(speed)
                    handler.postDelayed(hideIndicator, 300)
                }
                gestureMode = 0
            }
            true
        }
    }

    private fun currentBrightness(): Float {
        val current = window.attributes.screenBrightness
        if (current >= 0f) return current
        return try {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun showIndicator(icon: Int, text: String) {
        indicatorIcon.setImageResource(icon)
        indicatorText.text = text
        indicator.animate().cancel()
        indicator.alpha = 1f
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 900)
    }

    private fun flash(view: TextView) {
        view.animate().cancel()
        view.alpha = 1f
        view.animate().alpha(0f).setStartDelay(350).setDuration(250).start()
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            p.play()
        } else if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        scheduleHide()
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (duration > 0) min(target, duration) else target)
        updateProgress()
        scheduleHide()
    }

    private fun updatePlayIcon() {
        val p = player
        val ended = p?.playbackState == Player.STATE_ENDED
        btnPlay.setImageResource(
            when {
                ended -> R.drawable.ic_media_replay
                p?.isPlaying == true || p?.playWhenReady == true && p.playbackState == Player.STATE_BUFFERING -> R.drawable.ic_media_pause
                else -> R.drawable.ic_media_play
            }
        )
    }

    private fun updateProgress() {
        val p = player ?: return
        if (userSeeking) return
        val duration = p.duration
        val position = p.currentPosition
        if (duration > 0) {
            seekBar.progress = (position * 1000 / duration).toInt()
            seekBar.secondaryProgress = (p.bufferedPosition * 1000 / duration).toInt()
            timeTotal.text = formatTime(duration)
        }
        timeCurrent.text = formatTime(position)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = max(ms, 0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (!isAudio && player?.isPlaying == true && !locked) handler.postDelayed(hideControls, 3500)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (locked) return
        controlsVisible = visible
        val alpha = if (visible) 1f else 0f
        listOf(controlsTop, controlsCenter, controlsBottom).forEach {
            it.animate().alpha(alpha).setDuration(180).start()
            it.visibility = View.VISIBLE
            it.isClickable = visible
        }
        controlsCenter.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            scheduleHide()
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setLocked(value: Boolean) {
        locked = value
        if (value) {
            listOf(controlsTop, controlsCenter, controlsBottom).forEach { it.visibility = View.INVISIBLE }
            btnUnlock.visibility = View.VISIBLE
            handler.removeCallbacks(hideControls)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            handler.postDelayed({ btnUnlock.animate().alpha(0f).setDuration(250).start() }, 2500)
        } else {
            btnUnlock.visibility = View.GONE
            btnUnlock.alpha = 1f
            listOf(controlsTop, controlsCenter, controlsBottom).forEach { it.visibility = View.VISIBLE; it.alpha = 1f }
            controlsVisible = true
            scheduleHide()
        }
    }

    private fun showUnlock() {
        btnUnlock.animate().cancel()
        btnUnlock.alpha = 1f
        btnUnlock.visibility = View.VISIBLE
        handler.postDelayed({ if (locked) btnUnlock.animate().alpha(0f).setDuration(250).start() }, 2500)
    }

    private fun toggleOrientation() {
        landscapeForced = !landscapeForced
        requestedOrientation = if (landscapeForced) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        btnFullscreen.setImageResource(if (landscapeForced) R.drawable.ic_media_fullscreen_exit else R.drawable.ic_media_fullscreen)
        scheduleHide()
    }

    private fun showSpeedMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speeds.forEachIndexed { index, value ->
            val item = menu.menu.add(0, index, index, getString(R.string.media_speed_format, formatSpeed(value)))
            item.isCheckable = true
            item.isChecked = abs(value - speed) < 0.01f
        }
        menu.setOnMenuItemClickListener { item ->
            speed = speeds[item.itemId]
            player?.setPlaybackSpeed(speed)
            btnSpeed.text = getString(R.string.media_speed_format, formatSpeed(speed))
            true
        }
        menu.show()
    }

    private fun formatSpeed(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0')

    private fun showMoreMenu(anchor: View) {
        val items = ArrayList<SheetItem>()
        if (!isAudio) {
            items.add(SheetItem(R.drawable.ic_f_view_grid, getString(R.string.video_aspect)) { showAspectSheet() })
            items.add(SheetItem(R.drawable.ic_f_doc, getString(R.string.video_subtitles)) { showSubtitles() })
        }
        items.add(SheetItem(R.drawable.ic_f_music, getString(R.string.video_audio_track)) { showAudioTracks() })
        items.add(SheetItem(R.drawable.ic_f_info, getString(R.string.video_info)) { showVideoInfo() })
        items.add(SheetItem(R.drawable.ic_f_share, getString(R.string.media_action_open_with)) { openWith() })
        sheets.sheet(null, items)
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: (if (isAudio) "audio/*" else "video/*")

    private fun shareableUri(): Uri? {
        val local = file
        if (local != null) return FileProvider.getUriForFile(this, "$packageName.fileprovider", local)
        return sourceUri
    }

    private fun share() {
        try {
            val uri = shareableUri() ?: return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(displayName)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_document_to_share, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWith() {
        try {
            val uri = shareableUri() ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeOf(displayName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        saveResume()
        player?.pause()
    }

    override fun onDestroy() {
        saveResume()
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
        if (deleteOnExit) {
            try {
                val target = file
                if (target != null && target.parentFile?.name == "doc_viewer") target.delete()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "video_path"
        const val EXTRA_NAME = "video_name"
        const val EXTRA_DELETE = "video_delete_on_exit"
        const val EXTRA_SIBLINGS = "video_siblings"
        const val EXTRA_INDEX = "video_index"
    }
}
