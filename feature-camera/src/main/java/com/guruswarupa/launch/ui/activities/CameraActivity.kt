package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.guruswarupa.launch.camera.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@OptIn(ExperimentalCamera2Interop::class)
class CameraActivity : AppCompatActivity() {

    private enum class Mode(val labelRes: Int) {
        PHOTO(R.string.cam_photo), PORTRAIT(R.string.cam_mode_portrait), NIGHT(R.string.cam_mode_night),
        HDR(R.string.cam_mode_hdr), PRO(R.string.cam_mode_pro), VIDEO(R.string.cam_video)
    }

    private lateinit var previewView: PreviewView
    private lateinit var grid: View
    private lateinit var overlay: FocusOverlayView
    private lateinit var levelView: LevelView
    private lateinit var flashButton: ImageView
    private lateinit var timerButton: ImageView
    private lateinit var timerLabel: TextView
    private lateinit var ratioButton: TextView
    private lateinit var levelButton: ImageView
    private lateinit var gridButton: ImageView
    private lateinit var optionsRow: LinearLayout
    private lateinit var zoomRow: LinearLayout
    private lateinit var zoomLabel: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var proPanel: LinearLayout
    private lateinit var proIsoValue: TextView
    private lateinit var proShutterValue: TextView
    private lateinit var proFocusValue: TextView
    private lateinit var proIso: SeekBar
    private lateinit var proShutter: SeekBar
    private lateinit var proFocus: SeekBar
    private lateinit var proWbRow: LinearLayout
    private lateinit var shutter: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var countdown: TextView
    private lateinit var recordingLabel: TextView
    private lateinit var thumb: ImageView

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0
    private var mode = Mode.PHOTO
    private var available: List<Mode> = listOf(Mode.PHOTO, Mode.VIDEO)
    private var gridOn = false
    private var levelOn = false
    private var ratio16x9 = false
    private var maxQuality = true
    private var stabilize = false
    private var muted = false
    private var videoQuality: Quality? = null
    private var timer: CountDownTimer? = null
    private var lastUri: Uri? = null
    private var recordStart = 0L
    private var rotation = Surface.ROTATION_0
    private var draggingSlider = false
    private var proIsoRange: android.util.Range<Int>? = null
    private var proShutterRange: android.util.Range<Long>? = null
    private var proMinFocus = 0f
    private var proIsoOn = false
    private var proShutterOn = false
    private var proFocusOn = false
    private var proWb: Int? = null
    private val ui = Handler(Looper.getMainLooper())
    private val hideFocus = Runnable { overlay.hide() }
    private val hideZoomLabel = Runnable { zoomLabel.animate().alpha(0f).setDuration(250).start() }
    private var sensorManager: SensorManager? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasCamera()) startCamera() else {
            Toast.makeText(this, getString(R.string.cam_permission), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(deg: Int) {
                if (deg == ORIENTATION_UNKNOWN) return
                val r = when (deg) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (r != rotation) {
                    rotation = r
                    imageCapture?.targetRotation = r
                    videoCapture?.targetRotation = r
                }
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
            val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
            levelView.update(if (abs(roll) > 90) 0f else -roll, abs(z) > 8f)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setContentView(buildUi())
        if (hasCamera()) startCamera() else permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()
        updateSensors()
    }

    override fun onPause() {
        orientationListener.disable()
        sensorManager?.unregisterListener(sensorListener)
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event.repeatCount == 0) {
            onShutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun icon(res: Int, desc: Int, size: Int = 44, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        contentDescription = getString(desc)
        val p = dp(10)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, selected: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (selected) 0xFF000000.toInt() else Color.WHITE)
        background = round(if (selected) 0xFFFFD54F.toInt() else 0x55000000, 16)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { onClick() }
    }

    private fun chipLp() = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        grid = object : View(this) {
            private val paint = android.graphics.Paint().apply { color = 0x88FFFFFF.toInt(); strokeWidth = 2f }
            override fun onDraw(c: android.graphics.Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                for (i in 1..2) {
                    c.drawLine(w * i / 3, 0f, w * i / 3, h, paint)
                    c.drawLine(0f, h * i / 3, w, h * i / 3, paint)
                }
            }
        }.apply { visibility = View.GONE }
        root.addView(grid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        levelView = LevelView(this).apply { visibility = View.GONE }
        root.addView(levelView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        overlay = FocusOverlayView(this).apply {
            onExposure = { f ->
                val info = camera?.cameraInfo?.exposureState
                val range = info?.exposureCompensationRange
                if (range != null && range.upper != range.lower) {
                    val idx = (range.lower + f * (range.upper - range.lower)).roundToInt()
                    camera?.cameraControl?.setExposureCompensationIndex(idx)
                }
                ui.removeCallbacks(hideFocus); ui.postDelayed(hideFocus, 3500)
            }
        }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        installGestures()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val topRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(icon(R.drawable.ic_f_back, R.string.cam_cd_back) { finish() })
        topRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        flashButton = icon(R.drawable.ic_cam_flash_off, R.string.cam_cd_flash) { cycleFlash() }
        topRow.addView(flashButton)
        val timerBox = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setOnClickListener { cycleTimer() } }
        timerButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_cam_timer); setColorFilter(Color.WHITE)
            contentDescription = getString(R.string.cam_cd_timer)
            val p = dp(10); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        timerLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; visibility = View.GONE }
        timerBox.addView(timerButton); timerBox.addView(timerLabel)
        topRow.addView(timerBox)
        ratioButton = TextView(this).apply {
            text = "4:3"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.cam_cd_ratio)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { ratio16x9 = !ratio16x9; text = if (ratio16x9) "16:9" else "4:3"; bind() }
        }
        topRow.addView(ratioButton)
        gridButton = icon(R.drawable.ic_cam_grid, R.string.cam_cd_grid) {
            gridOn = !gridOn; grid.visibility = if (gridOn) View.VISIBLE else View.GONE
            gridButton.setColorFilter(if (gridOn) 0xFFFFD54F.toInt() else Color.WHITE)
        }
        topRow.addView(gridButton)
        levelButton = icon(R.drawable.ic_cam_level, R.string.cam_cd_level) {
            levelOn = !levelOn
            levelView.visibility = if (levelOn) View.VISIBLE else View.GONE
            levelButton.setColorFilter(if (levelOn) 0xFFFFD54F.toInt() else Color.WHITE)
            updateSensors()
        }
        topRow.addView(levelButton)
        top.addView(topRow)
        optionsRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
        top.addView(optionsRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        recordingLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            background = round(0xCCE53935.toInt(), 14)
            setPadding(dp(12), dp(4), dp(12), dp(4)); visibility = View.GONE
        }
        root.addView(recordingLabel, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(116) })

        countdown = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 96f; gravity = Gravity.CENTER; visibility = View.GONE
        }
        root.addView(countdown, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
            setBackgroundColor(0x66000000)
        }

        zoomLabel = TextView(this).apply { setTextColor(0xFFFFD54F.toInt()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; alpha = 0f }
        bottom.addView(zoomLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        zoomRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(8)) }
        bottom.addView(zoomRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        proPanel = buildProPanel().apply { visibility = View.GONE }
        bottom.addView(proPanel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val modeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        modeRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        modeScroll.addView(modeRow)
        bottom.addView(modeScroll, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = round(0x44FFFFFF, 12)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            contentDescription = getString(R.string.cam_cd_gallery)
            setOnClickListener { openLast() }
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        shutter = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(4), Color.WHITE) }
            contentDescription = getString(R.string.cam_cd_shutter)
            setOnClickListener { onShutter() }
        }
        shutterInner = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) } }
        shutter.addView(shutterInner, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
        row.addView(shutter, LinearLayout.LayoutParams(dp(78), dp(78)))
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(icon(R.drawable.ic_cam_flip, R.string.cam_cd_flip, 52) { flipCamera() }.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x44FFFFFF) }
        })
        bottom.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(dp(12), b.top + dp(8), dp(12), dp(4))
            bottom.setPadding(dp(16), dp(8), dp(16), b.bottom + dp(20))
            insets
        }
        rebuildModeChips()
        rebuildOptions()
        return root
    }

    private fun buildProPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(6)) }
        fun label(res: Int) = TextView(this).apply { setText(res); setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        fun value() = TextView(this).apply { setTextColor(0xFFFFD54F.toInt()); textSize = 12f; gravity = Gravity.END }
        fun row(l: Int, v: TextView, s: SeekBar): LinearLayout = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(l), LinearLayout.LayoutParams(dp(64), WRAP_CONTENT))
            addView(s, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(v, LinearLayout.LayoutParams(dp(64), WRAP_CONTENT))
        }
        fun seek(onChange: (Float) -> Unit) = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p / 1000f) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        proIsoValue = value(); proShutterValue = value(); proFocusValue = value()
        proIso = seek { f ->
            val r = proIsoRange ?: return@seek
            proIsoOn = true
            proIsoValue.text = isoFor(f, r).toString()
            applyPro()
        }
        proShutter = seek { f ->
            val r = proShutterRange ?: return@seek
            proShutterOn = true
            proShutterValue.text = formatShutter(shutterFor(f, r))
            applyPro()
        }
        proFocus = seek { f ->
            proFocusOn = true
            proFocusValue.text = String.format(Locale.US, "%.1f", f * proMinFocus)
            applyPro()
        }
        panel.addView(row(R.string.cam_pro_iso, proIsoValue, proIso))
        panel.addView(row(R.string.cam_pro_shutter, proShutterValue, proShutter))
        panel.addView(row(R.string.cam_pro_focus, proFocusValue, proFocus))
        val wbScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        proWbRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        wbScroll.addView(proWbRow)
        panel.addView(wbScroll)
        rebuildWb()
        return panel
    }

    private fun rebuildWb() {
        proWbRow.removeAllViews()
        proWbRow.addView(TextView(this).apply { setText(R.string.cam_pro_wb); setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, dp(10), 0) })
        val options: List<Pair<Int, Int?>> = listOf(
            R.string.cam_auto to null,
            R.string.cam_wb_sunny to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
            R.string.cam_wb_cloudy to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            R.string.cam_wb_tungsten to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
            R.string.cam_wb_fluorescent to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
        )
        options.forEach { (res, value) ->
            proWbRow.addView(chip(getString(res), proWb == value) { proWb = value; rebuildWb(); applyPro() }, chipLp())
        }
        proWbRow.addView(chip(getString(R.string.cam_auto) + " ↺") { resetPro() }, chipLp())
    }

    private fun isoFor(f: Float, r: android.util.Range<Int>): Int =
        exp(ln(r.lower.toDouble()) + f * (ln(r.upper.toDouble()) - ln(r.lower.toDouble()))).roundToInt().coerceIn(r.lower, r.upper)

    private fun shutterFor(f: Float, r: android.util.Range<Long>): Long {
        val lo = maxOf(r.lower, 125_000L)
        val hi = minOf(r.upper, 500_000_000L)
        if (hi <= lo) return lo
        return exp(ln(lo.toDouble()) + f * (ln(hi.toDouble()) - ln(lo.toDouble()))).toLong().coerceIn(r.lower, r.upper)
    }

    private fun applyPro() {
        val cam = camera ?: return
        val control = Camera2CameraControl.from(cam.cameraControl)
        val b = CaptureRequestOptions.Builder()
        var any = false
        val isoR = proIsoRange
        val shR = proShutterRange
        if ((proIsoOn || proShutterOn) && isoR != null && shR != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, if (proIsoOn) isoFor(proIso.progress / 1000f, isoR) else isoR.clamp(400))
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, if (proShutterOn) shutterFor(proShutter.progress / 1000f, shR) else shR.clamp(16_000_000L))
            any = true
        }
        if (proFocusOn && proMinFocus > 0f) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, proFocus.progress / 1000f * proMinFocus)
            any = true
        }
        proWb?.let {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, it)
            any = true
        }
        if (any) control.setCaptureRequestOptions(b.build()) else control.clearCaptureRequestOptions()
    }

    private fun resetPro() {
        proIsoOn = false; proShutterOn = false; proFocusOn = false; proWb = null
        proIso.progress = 500; proShutter.progress = 500; proFocus.progress = 0
        proIsoValue.text = ""; proShutterValue.text = ""; proFocusValue.text = ""
        rebuildWb()
        camera?.let { Camera2CameraControl.from(it.cameraControl).clearCaptureRequestOptions() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installGestures() {
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val z = camera?.cameraInfo?.zoomState?.value ?: return true
                camera?.cameraControl?.setZoomRatio((z.zoomRatio * d.scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                return true
            }
        })
        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { focusAt(e.x, e.y, false); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { flipCamera(); return true }
            override fun onLongPress(e: MotionEvent) { focusAt(e.x, e.y, true); previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > 1400f && abs(vx) > abs(vy) * 1.5f && recording == null) { stepMode(if (vx < 0) 1 else -1); return true }
                return false
            }
        })
        previewView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN && overlay.hitSlider(e.x, e.y)) draggingSlider = true
            if (draggingSlider) {
                overlay.drag(e.y)
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) draggingSlider = false
                return@setOnTouchListener true
            }
            scale.onTouchEvent(e)
            if (!scale.isInProgress) gesture.onTouchEvent(e)
            true
        }
    }

    private fun focusAt(x: Float, y: Float, lock: Boolean) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
        if (lock) builder.disableAutoCancel() else builder.setAutoCancelDuration(5, TimeUnit.SECONDS)
        cam.cameraControl.setExposureCompensationIndex(0)
        cam.cameraControl.startFocusAndMetering(builder.build())
        overlay.show(x, y, lock, getString(R.string.cam_locked))
        ui.removeCallbacks(hideFocus)
        ui.postDelayed(hideFocus, if (lock) 6000 else 3500)
    }

    private fun updateSensors() {
        sensorManager?.unregisterListener(sensorListener)
        if (levelOn) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            provider = f.get()
            ExtensionsManager.getInstanceAsync(this, provider!!).addListener({
                extensions = try { ExtensionsManager.getInstanceAsync(this, provider!!).get() } catch (e: Exception) { null }
                bind()
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
        loadLastThumb()
    }

    private fun extensionFor(m: Mode): Int? = when (m) {
        Mode.PORTRAIT -> ExtensionMode.BOKEH
        Mode.NIGHT -> ExtensionMode.NIGHT
        Mode.HDR -> ExtensionMode.HDR
        else -> null
    }

    private fun extensionAvailable(base: CameraSelector, m: Mode): Boolean {
        val ext = extensions ?: return false
        return when (m) {
            Mode.PORTRAIT -> ext.isExtensionAvailable(base, ExtensionMode.BOKEH) || ext.isExtensionAvailable(base, ExtensionMode.FACE_RETOUCH)
            Mode.NIGHT -> ext.isExtensionAvailable(base, ExtensionMode.NIGHT)
            Mode.HDR -> ext.isExtensionAvailable(base, ExtensionMode.HDR)
            else -> true
        }
    }

    private fun resolutionSelector(wide: Boolean): ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            if (wide) AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
            else AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        ).build()

    private fun bind() {
        val p = provider ?: return
        p.unbindAll()
        val base = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val modes = ArrayList<Mode>()
        modes.add(Mode.PHOTO)
        if (extensionAvailable(base, Mode.PORTRAIT)) modes.add(Mode.PORTRAIT)
        if (extensionAvailable(base, Mode.NIGHT)) modes.add(Mode.NIGHT)
        if (extensionAvailable(base, Mode.HDR)) modes.add(Mode.HDR)
        modes.add(Mode.VIDEO)
        if (mode !in modes && mode != Mode.PRO) mode = Mode.PHOTO

        var selector = base
        val ext = extensions
        if (ext != null && mode in listOf(Mode.PORTRAIT, Mode.NIGHT, Mode.HDR)) {
            val code = when {
                mode == Mode.PORTRAIT && ext.isExtensionAvailable(base, ExtensionMode.BOKEH) -> ExtensionMode.BOKEH
                mode == Mode.PORTRAIT -> ExtensionMode.FACE_RETOUCH
                else -> extensionFor(mode)!!
            }
            selector = ext.getExtensionEnabledCameraSelector(base, code)
        }
        val wide = ratio16x9 || mode == Mode.VIDEO
        val preview = Preview.Builder().setResolutionSelector(resolutionSelector(wide)).build().also { it.surfaceProvider = previewView.surfaceProvider }
        try {
            camera = if (mode == Mode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(videoQuality ?: Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                videoCapture = VideoCapture.Builder(recorder).setVideoStabilizationEnabled(stabilize).setTargetRotation(rotation).build()
                imageCapture = null
                p.bindToLifecycle(this, base, preview, videoCapture)
            } else {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(if (maxQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(if (maxQuality) 97 else 85)
                    .setResolutionSelector(resolutionSelector(ratio16x9))
                    .setFlashMode(flashMode)
                    .setTargetRotation(rotation)
                    .build()
                videoCapture = null
                p.bindToLifecycle(this, selector, preview, imageCapture)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cam_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        readProCapabilities()
        if (proIsoRange != null && proShutterRange != null) modes.add(modes.indexOf(Mode.VIDEO), Mode.PRO)
        available = modes
        if (mode == Mode.PRO && Mode.PRO !in available) mode = Mode.PHOTO
        camera?.cameraInfo?.zoomState?.observe(this) { rebuildZoomChips() }
        applyFlash()
        rebuildModeChips()
        rebuildOptions()
        updateProVisibility()
        if (mode == Mode.PRO) applyPro()
    }

    private fun readProCapabilities() {
        val cam = camera ?: return
        try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val manual = caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
            proIsoRange = if (manual) info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) else null
            proShutterRange = if (manual) info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) else null
            proMinFocus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        } catch (e: Exception) {
            proIsoRange = null; proShutterRange = null; proMinFocus = 0f
        }
    }

    private fun updateProVisibility() {
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE
        proFocus.isEnabled = proMinFocus > 0f
        proFocus.alpha = if (proMinFocus > 0f) 1f else 0.35f
    }

    private fun rebuildZoomChips() {
        val z = camera?.cameraInfo?.zoomState?.value ?: return
        zoomRow.removeAllViews()
        val candidates = ArrayList<Float>()
        if (z.minZoomRatio < 0.95f) candidates.add(z.minZoomRatio)
        candidates.add(1f)
        listOf(2f, 5f, 10f).forEach { if (it <= z.maxZoomRatio) candidates.add(it) }
        candidates.forEach { v ->
            val selected = abs(z.zoomRatio - v) < 0.12f
            val label = if (v < 1f) String.format(Locale.US, "%.1f", v) else if (selected && abs(z.zoomRatio - v) > 0.01f) String.format(Locale.US, "%.1f×", z.zoomRatio) else "${v.roundToInt()}×"
            zoomRow.addView(chip(if (v < 1f) "$label×" else label, selected) { camera?.cameraControl?.setZoomRatio(v) }, chipLp())
        }
        if (abs(z.zoomRatio - 1f) > 0.02f) {
            zoomLabel.text = String.format(Locale.US, "%.1f×", z.zoomRatio)
            zoomLabel.alpha = 1f
            ui.removeCallbacks(hideZoomLabel); ui.postDelayed(hideZoomLabel, 1200)
        }
    }

    private fun rebuildModeChips() {
        modeRow.removeAllViews()
        available.forEach { m ->
            modeRow.addView(TextView(this).apply {
                text = getString(m.labelRes)
                textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (m == mode) 0xFFFFD54F.toInt() else 0x99FFFFFF.toInt())
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener { selectMode(m) }
            })
        }
        (shutterInner.background as GradientDrawable).setColor(if (mode == Mode.VIDEO) 0xFFE53935.toInt() else Color.WHITE)
    }

    private fun rebuildOptions() {
        optionsRow.removeAllViews()
        if (mode == Mode.VIDEO) {
            val supported = camera?.cameraInfo?.let { QualitySelector.getSupportedQualities(it) } ?: emptyList()
            supported.filter { it in listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD) }.forEach { q ->
                val label = when (q) { Quality.UHD -> "4K"; Quality.FHD -> "1080p"; Quality.HD -> "720p"; else -> "480p" }
                val current = (videoQuality ?: Quality.HD)
                optionsRow.addView(chip(label, q == current) { videoQuality = q; bind() }, chipLp())
            }
            optionsRow.addView(chip(getString(R.string.cam_stabilize), stabilize) { stabilize = !stabilize; bind() }, chipLp())
            optionsRow.addView(chip(getString(if (muted) R.string.cam_mic_off else R.string.cam_mic_on), !muted) { muted = !muted; rebuildOptions() }, chipLp())
        } else {
            optionsRow.addView(chip(getString(if (maxQuality) R.string.cam_quality_max else R.string.cam_quality_fast), maxQuality) { maxQuality = !maxQuality; bind() }, chipLp())
        }
    }

    private fun selectMode(m: Mode) {
        if (recording != null || m == mode) return
        mode = m
        bind()
    }

    private fun stepMode(dir: Int) {
        val i = available.indexOf(mode)
        val n = (i + dir).coerceIn(0, available.lastIndex)
        if (n != i) selectMode(available[n])
    }

    private fun flipCamera() {
        if (recording != null) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        resetPro()
        bind()
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        applyFlash()
    }

    private fun applyFlash() {
        flashButton.setImageResource(when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_cam_flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_cam_flash_auto
            else -> R.drawable.ic_cam_flash_off
        })
        imageCapture?.flashMode = flashMode
        if (mode == Mode.VIDEO) camera?.cameraControl?.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON)
    }

    private fun cycleTimer() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        timerLabel.visibility = if (timerSeconds == 0) View.GONE else View.VISIBLE
        timerLabel.text = getString(R.string.cam_timer_seconds, timerSeconds)
        timerButton.setColorFilter(if (timerSeconds == 0) Color.WHITE else 0xFFFFD54F.toInt())
    }

    private fun onShutter() {
        if (recording != null) { recording?.stop(); return }
        timer?.cancel()
        shutter.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (timerSeconds > 0) {
            countdown.visibility = View.VISIBLE
            timer = object : CountDownTimer(timerSeconds * 1000L, 1000) {
                override fun onTick(ms: Long) { countdown.text = ((ms + 999) / 1000).toString() }
                override fun onFinish() { countdown.visibility = View.GONE; capture() }
            }.start()
        } else capture()
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun capture() {
        if (mode == Mode.VIDEO) recordVideo() else takePhoto()
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val out = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        flashShutter()
        ic.takePicture(out, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                lastUri = r.savedUri
                loadLastThumb()
            }
            override fun onError(e: ImageCaptureException) {
                Toast.makeText(this@CameraActivity, getString(R.string.cam_save_failed), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun flashShutter() {
        previewView.animate().alpha(0.2f).setDuration(60).withEndAction { previewView.animate().alpha(1f).setDuration(120).start() }.start()
    }

    private fun recordVideo() {
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = vc.output.prepareRecording(this, opts)
        if (!muted && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> {
                    recordStart = SystemClock.elapsedRealtime()
                    recordingLabel.visibility = View.VISIBLE
                    shutterInner.animate().scaleX(0.55f).scaleY(0.55f).setDuration(150).start()
                    tickRecording()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recordingLabel.visibility = View.GONE
                    shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    if (!ev.hasError()) { lastUri = ev.outputResults.outputUri; loadLastThumb() }
                    else Toast.makeText(this, getString(R.string.cam_record_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tickRecording() {
        if (recording == null) return
        val s = (SystemClock.elapsedRealtime() - recordStart) / 1000
        recordingLabel.text = "● %02d:%02d".format(s / 60, s % 60)
        recordingLabel.postDelayed({ tickRecording() }, 500)
    }

    private fun loadLastThumb() {
        Thread {
            val uri = lastUri ?: try { latestMedia() } catch (e: Exception) { null } ?: return@Thread
            lastUri = uri
            val bmp = try {
                if (android.os.Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null) else null
            } catch (e: Exception) { null }
            runOnUiThread { if (bmp != null) thumb.setImageBitmap(bmp) }
        }.start()
    }

    private fun latestMedia(): Uri? {
        val q = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,3) AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("DCIM/Camera%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        ) ?: return null
        q.use {
            if (!it.moveToFirst()) return null
            val id = it.getLong(0)
            val base = if (it.getInt(1) == 3) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            return Uri.withAppendedPath(base, id.toString())
        }
    }

    private fun openLast() {
        if ((getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked) return
        val uri = lastUri ?: return
        val type = contentResolver.getType(uri) ?: "image/*"
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClassName(packageName, if (type.startsWith("video")) "com.guruswarupa.launch.ui.activities.VideoPlayerExternalActivity" else "com.guruswarupa.launch.ui.activities.PhotoViewerExternalActivity")
        }
        try { startActivity(i) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        timer?.cancel()
        ui.removeCallbacksAndMessages(null)
        recording?.stop()
        super.onDestroy()
    }
}

private fun android.util.Range<Int>.clamp(v: Int): Int = v.coerceIn(lower, upper)
private fun android.util.Range<Long>.clamp(v: Long): Long = v.coerceIn(lower, upper)
