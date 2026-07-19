package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.FocusModeConfigActivity
import com.guruswarupa.launch.ui.activities.WorkspaceConfigActivity
import com.guruswarupa.launch.ui.activities.EncryptedVaultActivity
import com.guruswarupa.launch.utils.DialogStyler
import java.util.Locale
import kotlin.math.abs

class AppDockManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout
) {
    companion object {
        private const val TAG = "AppDockManager"
    }

    private val context: Context = activity
    private val dockIconSizePx = (Constants.Dimensions.DOCK_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val focusModeKey = "focus_mode_enabled"
    private val focusModeAllowedAppsKey = "focus_mode_allowed_apps"
    private val focusModeEndTimeKey = "focus_mode_end_time"
    private val focusModeDndEnabledKey = "focus_mode_dnd_enabled"
    private lateinit var focusModeToggle: ImageView
    private lateinit var focusTimerText: TextView
    private lateinit var workspaceToggle: ImageView
    private lateinit var workProfileToggle: ImageView
    private lateinit var workProfileNameText: TextView
    private val pomodoroManager: PomodoroManager
    private val focusModeDialogs: FocusModeDialogs
    private var isFocusMode: Boolean = false
    private val res = context.resources
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private val workspaceManager: WorkspaceManager
    private val workProfileManager: WorkProfileManager
    private val workspaceProfileDialogs: WorkspaceProfileDialogs
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        isFocusMode = sharedPreferences.getBoolean(focusModeKey, false)
        workspaceManager = WorkspaceManager(sharedPreferences)
        workProfileManager = WorkProfileManager(context, sharedPreferences)
        workspaceProfileDialogs = WorkspaceProfileDialogs(
            context = context,
            activity = activity,
            workspaceManager = workspaceManager,
            workProfileManager = workProfileManager,
            callbacks = object : WorkspaceProfileDialogs.Callbacks {
                override fun updateWorkspaceIcon() = this@AppDockManager.updateWorkspaceIcon()
                override fun updateWorkProfileIcon() = this@AppDockManager.updateWorkProfileIcon()
                override fun updateDockVisibility() = this@AppDockManager.updateDockVisibility()
                override fun refreshAppsForWorkspace() = this@AppDockManager.refreshAppsForWorkspace()
                override fun scrollToTop() = this@AppDockManager.scrollToTop()
                override fun showWorkspaceSettings() = this@AppDockManager.showWorkspaceSettings()
            }
        )

        val focusModeManager = FocusModeManager(context, sharedPreferences)
        pomodoroManager = PomodoroManager(context, sharedPreferences, focusModeManager)
        focusModeDialogs = FocusModeDialogs(
            context = context,
            sharedPreferences = sharedPreferences,
            pomodoroManager = pomodoroManager,
            onShowPomodoroSettings = { showPomodoroSettingsDialog() },
            onEnableFocusMode = { durationMinutes, enableDnd, modeType ->
                enableFocusMode(durationMinutes, enableDnd, modeType)
            }
        )
        pomodoroManager.onTimerTick = { remainingMillis, state ->
            updatePomodoroTimerDisplay(remainingMillis, state)
        }
        pomodoroManager.onStateChanged = { _, isWorkFocus ->
            isFocusMode = isWorkFocus
            updateFocusModeIcon()
            updateDockVisibility()
            lockDrawerForFocusMode(isWorkFocus)
            refreshAppsForFocusMode()

            if (isWorkFocus && sharedPreferences.getBoolean(focusModeDndEnabledKey, false)) {
                updateDndState(true)
            } else if (!isWorkFocus) {
                updateDndState(false)
            }
        }
        pomodoroManager.onSessionEnded = {
            isFocusMode = false
            updateFocusModeIcon()
            stopTimerDisplay()
            updateDockVisibility()
            lockDrawerForFocusMode(false)
            refreshAppsForFocusMode()
            updateDndState(false)
        }

        val focusEndTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
        if (isFocusMode && focusEndTime > 0 && System.currentTimeMillis() > focusEndTime) {
            isFocusMode = false
            sharedPreferences.edit {
                putBoolean(focusModeKey, false)
                remove(focusModeEndTimeKey)
            }
        }

        refreshDock()

        pomodoroManager.resumeIfNeeded()

        ensureWorkspaceToggle()


        if (isFocusMode) {
            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            if (endTime > System.currentTimeMillis()) {
                startTimerDisplay()
                startFocusModeTimer(endTime)
                if (sharedPreferences.getBoolean(focusModeDndEnabledKey, false)) {
                    updateDndState(true)
                }
            } else {

                disableFocusMode()
            }
        }
    }

    private fun ensureVaultButton() {

    }

    private fun openVault() {
        val intent = Intent(context, EncryptedVaultActivity::class.java)
        context.startActivity(intent)
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        ensureWorkProfileToggle()
        ensureWorkspaceToggle()
        ensureFocusModeToggle()
        updateDockVisibility()
    }

    fun updateDockIcons() {

        if (::focusModeToggle.isInitialized) {
            updateFocusModeIcon()
        }
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
        }
        if (::workProfileToggle.isInitialized) {
            updateWorkProfileIcon()
        }
    }

    fun refreshWorkspaceToggle() {
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
        }
    }

    private fun createDockItemContainer(containerTag: String): LinearLayout {
        val horizontalPadding = (28 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            tag = containerTag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (44 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = 16
            }
            background = getGlassyBackground()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            isClickable = true
            isFocusable = true
        }
    }

    private fun getGlassyBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 1000f

            setColor(Color.parseColor("#80000000"))
            setStroke(1, Color.parseColor("#40FFFFFF"))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureFocusModeToggle() {
        if (appDock.findViewWithTag<View>("focus_mode_container") == null) {
            val focusContainer = createDockItemContainer("focus_mode_container")

            focusModeToggle = ImageView(context).apply {
                tag = "focus_mode_toggle"
                setImageResource(if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            focusTimerText = TextView(context).apply {
                tag = "focus_timer_text"
                textSize = Constants.Dimensions.FOCUS_TIMER_TEXT_SIZE_SP
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = Constants.Dimensions.MARGIN_START_DP
                }
                gravity = Gravity.CENTER
                text = ""
                visibility = if (isFocusMode || pomodoroManager.isPomodoroActive()) View.VISIBLE else View.GONE
                isClickable = false
                isFocusable = false
            }

            focusContainer.addView(focusModeToggle)
            focusContainer.addView(focusTimerText)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleFocusMode()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    if (!isFocusMode) {
                        showFocusModeSettings()
                    } else {
                        Toast.makeText(context, "Focus mode settings unavailable during focus mode", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            focusContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }


            var insertIndex = 1
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "workspace_container") {
                    insertIndex = i + 1
                    break
                }
            }
            appDock.addView(focusContainer, insertIndex)

            if (isFocusMode) {
                startTimerDisplay()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureWorkspaceToggle() {
        if (appDock.findViewWithTag<View>("workspace_container") == null) {
            val workspaceContainer = createDockItemContainer("workspace_container")

            workspaceToggle = ImageView(context).apply {
                tag = "workspace_toggle"
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            workspaceContainer.addView(workspaceToggle)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (abs(diffY) > abs(diffX)) {
                        if (abs(diffY) > Constants.Limits.FLING_DISTANCE_THRESHOLD && abs(velocityY) > Constants.Limits.FLING_VELOCITY_THRESHOLD) {
                            if (diffY < 0) cycleWorkspaces() else turnOffWorkspace()
                            return true
                        }
                    }
                    return false
                }
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleWorkspace()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    showWorkspaceSettings()
                }
            })

            workspaceContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }

            appDock.addView(workspaceContainer, 0)
            updateWorkspaceIcon()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureWorkProfileToggle() {
        if (appDock.findViewWithTag<View>("work_profile_container") == null) {
            val workProfileContainer = createDockItemContainer("work_profile_container")

            workProfileToggle = ImageView(context).apply {
                tag = "work_profile_toggle"
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            workProfileNameText = TextView(context).apply {
                tag = "work_profile_name_text"
                textSize = Constants.Dimensions.FOCUS_TIMER_TEXT_SIZE_SP
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = Constants.Dimensions.MARGIN_START_DP
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = ""
                visibility = View.GONE
                isClickable = false
                isFocusable = false
            }

            workProfileContainer.addView(workProfileToggle)
            workProfileContainer.addView(workProfileNameText)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (abs(diffY) > abs(diffX)) {
                        if (abs(diffY) > Constants.Limits.FLING_DISTANCE_THRESHOLD && abs(velocityY) > Constants.Limits.FLING_VELOCITY_THRESHOLD) {
                            if (diffY < 0) {

                                if (!workProfileManager.isWorkProfileEnabled()) {
                                    toggleWorkProfile()
                                } else {

                                    updateWorkProfileIcon()
                                    refreshAppsForWorkspace()
                                }
                            } else {

                                if (workProfileManager.isWorkProfileEnabled()) {
                                    toggleWorkProfile()
                                }
                            }
                            return true
                        }
                    }
                    return false
                }
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleWorkProfile()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    if (!workProfileManager.hasActualWorkProfile()) {
                        showWorkProfileManagementDialog()
                    }
                }
            })

            workProfileContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }

            appDock.addView(workProfileContainer, 0)
            updateWorkProfileIcon()
        }
    }

    private fun toggleWorkspace() {
        showWorkspaceSelector()
    }

    private fun toggleWorkProfile() {
        val isWorkModeEnabled = workProfileManager.syncWorkProfileEnabledState()

        if (isWorkModeEnabled) {
            if (!workProfileManager.setWorkProfileQuietMode(false)) {
                Toast.makeText(context, "Unable to pause the work profile", Toast.LENGTH_SHORT).show()
                return
            }
            updateWorkProfileIcon()
            updateDockVisibility()
            activity.refreshAppsForWorkspace()
            return
        }

        if (!workProfileManager.hasActualWorkProfile()) {
            showCreateWorkProfileDialog()
            return
        }

        val isProfileRunning = workProfileManager.isWorkProfileAvailableAndEnabled()
        if (!isProfileRunning && !workProfileManager.setWorkProfileQuietMode(true)) {
            Toast.makeText(context, "Unable to resume the work profile", Toast.LENGTH_SHORT).show()
            return
        }

        workProfileManager.syncWorkProfileEnabledState()
        updateWorkProfileIcon()
        updateDockVisibility()
        activity.refreshAppsForWorkspace()
    }

    private fun showWorkProfileSettings() {
        val intent = Intent(context, WorkspaceConfigActivity::class.java)
        context.startActivity(intent)
    }

    private fun showWorkProfileManagementDialog() {
        workspaceProfileDialogs.showWorkProfileManagementDialog()
    }

    private fun startWorkProfileCreation() {
        workspaceProfileDialogs.startWorkProfileCreation()
    }

    private fun showCreateWorkProfileDialog() {
        workspaceProfileDialogs.showCreateWorkProfileDialog()
    }

    private fun showWorkspaceSelector() {
        workspaceProfileDialogs.showWorkspaceSelector()
    }

    private fun cycleWorkspaces() {
        val workspaces = workspaceManager.getAllWorkspaces()

        if (workspaces.isEmpty()) {
            showWorkspaceSettings()
            return
        }

        val activeId = workspaceManager.getActiveWorkspaceId()
        val currentIndex = workspaces.indexOfFirst { it.id == activeId }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % workspaces.size
        val selectedWorkspace = workspaces[nextIndex]

        workspaceManager.setActiveWorkspaceId(selectedWorkspace.id)
        updateWorkspaceIcon()
        refreshAppsForWorkspace()
        scrollToTop()
    }

    private fun turnOffWorkspace() {
        if (workspaceManager.isWorkspaceModeActive()) {
            workspaceManager.setActiveWorkspaceId(null)
            updateWorkspaceIcon()
            refreshAppsForWorkspace()
            scrollToTop()
        }
    }

    private fun scrollToTop() {
        if (activity.views.isRecyclerViewInitialized()) {
            activity.views.recyclerView.postDelayed({
                activity.views.recyclerView.scrollToPosition(0)
            }, 100)
        }
    }

    private fun showWorkspaceSettings() {
        val intent = Intent(context, WorkspaceConfigActivity::class.java)
        context.startActivity(intent)
    }

    private fun updateWorkspaceIcon() {
        if (!::workspaceToggle.isInitialized) return

        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        workspaceToggle.setImageResource(
            if (isWorkspaceActive) R.drawable.ic_workspace_active else R.drawable.ic_workspace_inactive
        )

        val container = appDock.findViewWithTag<LinearLayout>("workspace_container")
        if (container != null) {
            if (isWorkspaceActive) {
                val bg = GradientDrawable().apply {
                    cornerRadius = 1000f
                    setColor(Color.parseColor("#80000000"))
                    setStroke(2, Color.parseColor("#8FBCBB"))
                }
                container.background = bg
            } else {
                container.background = getGlassyBackground()
            }
        }
    }

    private fun updateWorkProfileIcon() {
        if (!::workProfileToggle.isInitialized) return

        val hasWorkProfile = workProfileManager.hasActualWorkProfile()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()

        if ((!hasWorkProfile || !workProfileManager.isWorkProfileAvailableAndEnabled()) && isWorkProfileEnabled) {
            workProfileManager.setWorkProfileEnabled(false)
        }

        workProfileToggle.setImageResource(
            when {
                !hasWorkProfile || !workProfileManager.isWorkProfileEnabled() -> R.drawable.ic_work_inactive
                else -> R.drawable.ic_work_profile_active
            }
        )

        if (::workProfileNameText.isInitialized) {
            workProfileNameText.text = ""
            workProfileNameText.visibility = View.GONE

            val container = appDock.findViewWithTag<LinearLayout>("work_profile_container")
            if (container != null) {
                if (isWorkProfileEnabled) {
                    val bg = GradientDrawable().apply {
                        cornerRadius = 1000f
                        setColor(Color.parseColor("#80000000"))
                        setStroke(2, Color.parseColor("#4CAF50"))
                    }
                    container.background = bg
                } else {
                    container.background = getGlassyBackground()
                }
            }
        }
    }

    private fun refreshAppsForWorkspace() {
        activity.refreshAppsForWorkspace()
    }

    fun isWorkspaceModeActive(): Boolean {
        return workspaceManager.isWorkspaceModeActive()
    }

    fun isWorkProfileModeEnabled(): Boolean {
        return workProfileManager.isWorkProfileEnabled()
    }

    fun isAppInActiveWorkspace(packageName: String): Boolean {
        return workspaceManager.isAppInActiveWorkspace(packageName)
    }



    private fun ensureSettingsButton() {
    }

    private fun saveFocusMode() {
        sharedPreferences.edit { putBoolean(focusModeKey, isFocusMode) }
    }

    private fun toggleFocusMode() {
        if (isFocusMode) {
            val modeType = sharedPreferences.getString(Constants.Prefs.FOCUS_MODE_TYPE,
                Constants.Prefs.FOCUS_MODE_TYPE_STRICT)


            if (modeType == Constants.Prefs.FOCUS_MODE_TYPE_STRICT) {
                val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
                val remainingMinutes = (endTime - System.currentTimeMillis()) / (1000 * 60)
                Toast.makeText(context, "Strict mode active - $remainingMinutes minutes remaining", Toast.LENGTH_LONG).show()
                return
            }


            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime < endTime) {
                val remainingMinutes = (endTime - currentTime) / (1000 * 60)
                val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle("End Focus Mode?")
                    .setMessage("Casual mode - $remainingMinutes minutes remaining. End early?")
                    .setPositiveButton("End") { _, _ ->
                        disableFocusMode()
                    }
                    .setNegativeButton("Continue", null)
                    .create()
                DialogStyler.styleDialog(dialog)
                dialog.show()
            } else if (pomodoroManager.isPomodoroActive()) {
                val state = pomodoroManager.getCurrentState()
                Toast.makeText(context, "Pomodoro $state session active", Toast.LENGTH_SHORT).show()
            } else {
                disableFocusMode()
            }
        } else if (pomodoroManager.isPomodoroActive()) {
            val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(res.getString(R.string.pomodoro_stop_title))
                .setMessage(res.getString(R.string.pomodoro_stop_message))
                .setPositiveButton("Stop") { _, _ ->
                    pomodoroManager.stopPomodoro()
                }
                .setNegativeButton("Cancel", null)
                .create()
            DialogStyler.styleDialog(dialog)
            dialog.show()
        } else {
            showFocusModeDurationPicker()
        }
    }

    private fun showFocusModeDurationPicker() {
        focusModeDialogs.showDurationPicker()
    }

    private fun enableFocusMode(durationMinutes: Int, enableDnd: Boolean, modeType: String) {
        if (enableDnd && !notificationManager.isNotificationPolicyAccessGranted) {
            showDndPermissionDialog()
            return
        }

        isFocusMode = true
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        saveFocusMode()
        sharedPreferences.edit {
            putLong(focusModeEndTimeKey, endTime)
            putBoolean(focusModeDndEnabledKey, enableDnd)
            putString(Constants.Prefs.FOCUS_MODE_TYPE, modeType)
        }

        val focusModeManager = FocusModeManager(context, sharedPreferences)
        focusModeManager.setFocusModeEnabled(true)

        updateFocusModeIcon()
        updateDockVisibility()
        lockDrawerForFocusMode(true)
        refreshAppsForFocusMode()
        startTimerDisplay()

        if (enableDnd) updateDndState(true)

        startFocusModeTimer(endTime)
    }

    private fun updatePomodoroTimerDisplay(remainingMillis: Long, state: String) {
        if (!::focusTimerText.isInitialized) return

        val container = appDock.findViewWithTag<LinearLayout>("focus_mode_container")
        if (container != null) {
            val isWork = state == PomodoroManager.STATE_WORK
            val bg = GradientDrawable().apply {
                cornerRadius = 1000f
                setColor(Color.parseColor("#80000000"))
                setStroke(2, if (isWork) Color.parseColor("#BF616A") else Color.parseColor("#A3BE8C"))
            }
            container.background = bg
        }

        focusTimerText.visibility = View.VISIBLE
        val minutes = (remainingMillis / (1000 * 60)).toInt()
        val seconds = ((remainingMillis % (1000 * 60)) / 1000).toInt()

        val stateLabel = when (state) {
            PomodoroManager.STATE_WORK -> res.getString(R.string.pomodoro_work)
            PomodoroManager.STATE_LONG_BREAK -> res.getString(R.string.pomodoro_long_break)
            else -> res.getString(R.string.pomodoro_break)
        }
        focusTimerText.text = String.format(Locale.getDefault(), "%s %02d:%02d", stateLabel, minutes, seconds)

        val isWorkFocus = state == PomodoroManager.STATE_WORK
        focusModeToggle.setImageResource(if (isWorkFocus) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
    }

    private fun showPomodoroSettingsDialog() {
        PomodoroSettingsDialog(context, pomodoroManager).show()
    }

    private fun showDndPermissionDialog() {
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("DND Access Required")
            .setMessage("Muting notifications requires Do Not Disturb access. Please grant it in the settings or start Focus Mode without DND.")
            .setPositiveButton("Grant Access") { _, _ ->
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .create()

        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun updateDndState(enabled: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        val filter = if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        if (notificationManager.currentInterruptionFilter != filter) {
            try { notificationManager.setInterruptionFilter(filter) } catch (_: Exception) {}
        }
    }

    private fun disableFocusMode() {
        isFocusMode = false
        saveFocusMode()

        val dndWasEnabled = sharedPreferences.getBoolean(focusModeDndEnabledKey, false)
        sharedPreferences.edit {
            remove(focusModeEndTimeKey)
            remove(focusModeDndEnabledKey)
        }

        val focusModeManager = FocusModeManager(context, sharedPreferences)
        focusModeManager.setFocusModeEnabled(false)

        updateFocusModeIcon()
        updateDockVisibility()
        lockDrawerForFocusMode(false)
        refreshAppsForFocusMode()
        stopTimerDisplay()

        if (dndWasEnabled) updateDndState(false)
    }

    private fun startFocusModeTimer(endTime: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkTimer = object : Runnable {
            override fun run() {
                if (isFocusMode && System.currentTimeMillis() >= endTime) {
                    disableFocusMode()
                } else if (isFocusMode) {
                    val shouldHaveDnd = sharedPreferences.getBoolean(focusModeDndEnabledKey, false)
                    if (shouldHaveDnd && notificationManager.isNotificationPolicyAccessGranted &&
                        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        updateDndState(true)
                    }
                    handler.postDelayed(this, 30000)
                }
            }
        }
        handler.postDelayed(checkTimer, 30000)
    }

    private fun updateFocusModeIcon() {
        if (!::focusModeToggle.isInitialized) return
        focusModeToggle.setImageResource(if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)

        val container = appDock.findViewWithTag<LinearLayout>("focus_mode_container")
        if (container != null) {
            if (isFocusMode) {
                val bg = GradientDrawable().apply {
                    cornerRadius = 1000f
                    setColor(Color.parseColor("#80000000"))
                    setStroke(2, Color.parseColor("#5E81AC"))
                }
                container.background = bg
                if (::focusTimerText.isInitialized) {
                    focusTimerText.visibility = View.VISIBLE
                }
            } else {
                container.background = getGlassyBackground()
                if (::focusTimerText.isInitialized) {
                    if (!pomodoroManager.isPomodoroActive()) {
                        focusTimerText.text = ""
                        focusTimerText.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun refreshAppsForFocusMode() {
        activity.refreshAppsForFocusMode()
    }

    fun lockDrawerForFocusMode(lock: Boolean) {
        val mainActivity = activity
        if (lock) {
            mainActivity.setWidgetsPageLocked(true)
            mainActivity.openDefaultHomePage(animated = true)
        } else {
            mainActivity.setWidgetsPageLocked(false)
        }
    }

    fun refreshDockVisibility() {
        updateDockVisibility()
    }

    private fun updateDockVisibility() {
        val isWorkMode = workProfileManager.isWorkProfileEnabled()
        val isFocusActive = isFocusMode || pomodoroManager.isPomodoroActive()

        val hideWorkProfile = sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_WORK_PROFILE, false)
        val hideFocusMode = sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_FOCUS_MODE, false)
        val hideWorkspaces = sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_WORKSPACES, false)

        val allThreeHiddenInSettings = hideWorkProfile && hideFocusMode && hideWorkspaces

        var hasVisibleChildren = false
        for (i in 0 until appDock.childCount) {
            val child = appDock.getChildAt(i)
            val shouldBeVisible = when (child.tag) {
                "workspace_container" -> !hideWorkspaces && !isWorkMode && !isFocusActive
                "focus_mode_container" -> !hideFocusMode && !isWorkMode
                "work_profile_container" -> !hideWorkProfile
                else -> !isFocusActive
            }
            
            val finalVisibility = if (allThreeHiddenInSettings || !shouldBeVisible) View.GONE else View.VISIBLE
            child.visibility = finalVisibility
            if (finalVisibility == View.VISIBLE) {
                hasVisibleChildren = true
            }
        }

        val dockVisible = !allThreeHiddenInSettings && hasVisibleChildren
        appDock.visibility = if (dockVisible) View.VISIBLE else View.GONE
        (appDock.parent as? View)?.visibility = if (dockVisible) View.VISIBLE else View.GONE
    }

    private fun startTimerDisplay() {
        stopTimerDisplay()
        focusTimerText.visibility = View.VISIBLE
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        timerHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                if (isFocusMode) {
                    val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
                    val currentTime = System.currentTimeMillis()
                    if (endTime > currentTime) {
                        val remainingTime = endTime - currentTime
                        val minutes = (remainingTime / (1000 * 60)).toInt()
                        val seconds = ((remainingTime % (1000 * 60)) / 1000).toInt()
                        focusTimerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        handler.postDelayed(this, 1000)
                    } else {
                        focusTimerText.text = context.getString(R.string.timer_zero)
                    }
                }
            }
        }
        timerRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTimerDisplay() {
        timerHandler?.removeCallbacks(timerRunnable ?: return)
        timerHandler = null
        timerRunnable = null
        if (!pomodoroManager.isPomodoroActive()) {
            if (::focusTimerText.isInitialized) {
                focusTimerText.text = ""
                focusTimerText.visibility = View.GONE
            }
        }
    }

    private fun showFocusModeSettings() {
        val intent = Intent(context, FocusModeConfigActivity::class.java)
        context.startActivity(intent)
    }

    private fun getAllowedAppsInFocusMode(): Set<String> {
        return sharedPreferences.getStringSet(focusModeAllowedAppsKey, mutableSetOf()) ?: mutableSetOf()
    }

    fun isAppHiddenInFocusMode(packageName: String): Boolean {
        return if (isFocusMode) !getAllowedAppsInFocusMode().contains(packageName) else false
    }

    fun getCurrentMode(): Boolean {
        return isFocusMode
    }
}
