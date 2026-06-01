package com.guruswarupa.launch.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.ScreenPagerManager
import com.guruswarupa.launch.ui.activities.SettingsActivity
import kotlin.math.abs





class FeatureTutorialManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences
) {
    private var currentStep = 0
    private var tutorialOverlay: View? = null
    private var isTutorialActive = false
    private var renderToken = 0
    private var onTutorialCompleteCallback: (() -> Unit)? = null
    private var waitingForUserSwipe = false

    companion object {
        private const val PREF_TUTORIAL_SHOWN = "feature_tutorial_shown"
        private const val PREF_TUTORIAL_STEP = "feature_tutorial_current_step"
        private const val PAGE_SETTLE_DELAY_MS = 260L
        private const val RETRY_DELAY_MS = 180L
        private const val MAX_RENDER_ATTEMPTS = 10
        private const val SCROLL_SETTLE_DELAY_MS = 320L
    }

    private enum class TutorialPage(val rootViewId: Int) {
        RSS(R.id.rss_feed_page),
        HOME(R.id.main_content),
        WIDGETS(R.id.widgets_drawer),
        WALLPAPER(R.id.wallpaper_drawer)
    }

    private enum class HighlightPosition {
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }

    private enum class TutorialStep(
        val page: TutorialPage,
        val title: String,
        val description: String,
        val targetViewId: Int,
        val highlightPosition: HighlightPosition = HighlightPosition.BOTTOM,
        val targetViewTag: String? = null,
        val highlightVisible: Boolean = true,
        val scrollToTarget: Boolean = true,
        val waitForUserSwipe: Boolean = false,
        val waitForUserScroll: Boolean = false
    ) {
        HOME_OVERVIEW(
            page = TutorialPage.HOME,
            title = "Home Page",
            description = "This middle page is your launcher home. It combines widgets, search, shortcuts, and the full app list in one vertical flow.",
            targetViewId = R.id.main_content,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        ),
        WORKSPACE_TOGGLE(
            page = TutorialPage.HOME,
            title = "Workspaces",
            description = "Long press this icon to create and manage workspaces. Tap it to enable a saved workspace, switch workspaces, or turn workspace mode off.",
            targetViewId = R.id.app_dock,
            targetViewTag = "workspace_container"
        ),
        FOCUS_MODE(
            page = TutorialPage.HOME,
            title = "Focus Mode",
            description = "Long press here to build your focus app list. Tap it to start a focus session, pick a duration, and optionally enable Do Not Disturb.",
            targetViewId = R.id.app_dock,
            targetViewTag = "focus_mode_container"
        ),
        WORK_PROFILE(
            page = TutorialPage.HOME,
            title = "Work Profile",
            description = "This dock control handles your work profile. Tap it to turn work apps on or off, and long press it to open work profile setup or settings.",
            targetViewId = R.id.app_dock,
            targetViewTag = "work_profile_container"
        ),
        LAUNCH_SETTINGS_SHORTCUT(
            page = TutorialPage.HOME,
            title = "Launch Settings",
            description = "At the bottom of the app list you can quickly open Launch Settings without searching for it.",
            targetViewId = R.id.app_list,
            targetViewTag = "launcher_settings_shortcut",
            highlightPosition = HighlightPosition.TOP,
            waitForUserScroll = true
        ),
        LAUNCH_VAULT_SHORTCUT(
            page = TutorialPage.HOME,
            title = "Launch Vault",
            description = "Right below that, Launch Vault opens your encrypted vault directly from the bottom of the app list.",
            targetViewId = R.id.app_list,
            targetViewTag = "launcher_vault_shortcut",
            highlightPosition = HighlightPosition.TOP
        ),
        PAGE_SWIPE_HINT(
            page = TutorialPage.HOME,
            title = "More Pages",
            description = "Swipe sideways to move between pages. You can explore widgets and other pages by swiping left or right from home anytime.",
            targetViewId = R.id.main_content,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        )
    }

    fun shouldShowTutorial(): Boolean {
        return !sharedPreferences.getBoolean(PREF_TUTORIAL_SHOWN, false)
    }

    fun startTutorial(onComplete: (() -> Unit)? = null) {
        removeTutorialOverlay()
        currentStep = sharedPreferences.getInt(PREF_TUTORIAL_STEP, 0)
        if (currentStep >= TutorialStep.entries.size) {
            markTutorialComplete()
            onComplete?.invoke()
            return
        }

        isTutorialActive = true
        onTutorialCompleteCallback = onComplete
        showCurrentStep()
    }

    private fun showCurrentStep() {
        if (!isTutorialActive) return
        if (currentStep >= TutorialStep.entries.size) {
            finishTutorial()
            return
        }

        val step = TutorialStep.entries[currentStep]
        if (shouldSkipStep(step)) {
            nextStep()
            return
        }

        removeTutorialOverlay()
        val token = ++renderToken
        if (step.waitForUserSwipe && !isTutorialPageOpen(step.page)) {
            showSwipeHint(step, token)
            return
        }
        if (step.waitForUserScroll && !isStepTargetVisible(step)) {
            showScrollHint(step, token)
            return
        }
        openPage(step.page, animated = false) {
            renderStep(step, token, attempt = 0)
        }
    }

    private fun showSwipeHint(step: TutorialStep, token: Int) {
        waitingForUserSwipe = true
        openPage(TutorialPage.HOME, animated = false) {
            if (!isTutorialActive || token != renderToken) return@openPage
            val homeRoot = activity.findViewById<ViewGroup>(TutorialPage.HOME.rootViewId) ?: return@openPage
            val homeTarget = activity.findViewById<View>(R.id.main_content) ?: homeRoot
            showTutorialOverlay(
                step = step,
                parentView = homeRoot,
                targetView = homeTarget,
                titleOverride = step.title,
                descriptionOverride = swipeHintDescription(step.page),
                allowTouchThrough = true,
                showNextButton = false
            )
            waitForExpectedPage(step, token)
        }
    }

    private fun waitForExpectedPage(step: TutorialStep, token: Int) {
        val contentRoot = activity.findViewById<View>(android.R.id.content) ?: return
        contentRoot.postDelayed({
            if (!isTutorialActive || token != renderToken || !waitingForUserSwipe) return@postDelayed
            if (isTutorialPageOpen(step.page)) {
                waitingForUserSwipe = false
                removeTutorialOverlay()
                openPage(step.page, animated = false) {
                    renderStep(step, token, attempt = 0)
                }
            } else {
                waitForExpectedPage(step, token)
            }
        }, RETRY_DELAY_MS)
    }

    private fun showScrollHint(step: TutorialStep, token: Int) {
        openPage(step.page, animated = false) {
            if (!isTutorialActive || token != renderToken) return@openPage
            val pageRoot = activity.findViewById<ViewGroup>(step.page.rootViewId) ?: return@openPage
            val appList = pageRoot.findViewById<View>(R.id.app_list) ?: pageRoot
            showTutorialOverlay(
                step = step,
                parentView = pageRoot,
                targetView = appList,
                titleOverride = step.title,
                descriptionOverride = "Scroll to the bottom of the app list. The tutorial will continue when Launch Settings comes into view.",
                allowTouchThrough = true,
                showNextButton = false
            )
            waitForUserScrollTarget(step, token)
        }
    }

    private fun waitForUserScrollTarget(step: TutorialStep, token: Int) {
        val contentRoot = activity.findViewById<View>(android.R.id.content) ?: return
        contentRoot.postDelayed({
            if (!isTutorialActive || token != renderToken) return@postDelayed
            if (isStepTargetVisible(step)) {
                removeTutorialOverlay()
                openPage(step.page, animated = false) {
                    renderStep(step, token, attempt = 0)
                }
            } else {
                waitForUserScrollTarget(step, token)
            }
        }, RETRY_DELAY_MS)
    }

    private fun shouldSkipStep(step: TutorialStep): Boolean {
        return when (step) {
            TutorialStep.WORKSPACE_TOGGLE -> sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_WORKSPACES, false)
            TutorialStep.FOCUS_MODE -> sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_FOCUS_MODE, false)
            TutorialStep.WORK_PROFILE -> sharedPreferences.getBoolean(Constants.Prefs.DOCK_HIDE_WORK_PROFILE, false)
            else -> false
        }
    }

    private fun isStepTargetVisible(step: TutorialStep): Boolean {
        val pageRoot = activity.findViewById<ViewGroup>(step.page.rootViewId) ?: return false
        return resolveTargetView(step, pageRoot)?.isAttachedToWindow == true
    }

    private fun isTutorialPageOpen(page: TutorialPage): Boolean {
        return try {
            val currentPager = activity.screenPagerManager
            when (page) {
                TutorialPage.RSS -> currentPager.isPageOpen(ScreenPagerManager.Page.RSS)
                TutorialPage.HOME -> currentPager.isPageOpen(ScreenPagerManager.Page.CENTER)
                TutorialPage.WIDGETS -> currentPager.isPageOpen(ScreenPagerManager.Page.WIDGETS)
                TutorialPage.WALLPAPER -> currentPager.isPageOpen(ScreenPagerManager.Page.WALLPAPER)
            }
        } catch (e: UninitializedPropertyAccessException) {

            false
        }
    }

    private fun swipeHintDescription(page: TutorialPage): String {
        return when (page) {
            TutorialPage.RSS -> "Swipe right until you reach the News Feed page. The tutorial will continue there once you land on it."
            TutorialPage.WIDGETS -> "Swipe right to the Widgets page. The tutorial will continue after you open that page yourself."
            TutorialPage.WALLPAPER -> "Swipe left to the Wallpaper page. The tutorial will continue there once you reach it."
            TutorialPage.HOME -> ""
        }
    }

    private fun renderStep(step: TutorialStep, token: Int, attempt: Int) {
        if (!isTutorialActive || token != renderToken) return

        val pageRoot = activity.findViewById<ViewGroup>(step.page.rootViewId)
        if (pageRoot != null && scrollRecyclerToTutorialTarget(step, pageRoot)) {

            pageRoot.doOnLayout {
                renderStep(step, token, attempt + 1)
            }
            return
        }
        val targetView = resolveTargetView(step, pageRoot)
        if (pageRoot == null || targetView == null || !targetView.isAttachedToWindow) {
            if (attempt >= MAX_RENDER_ATTEMPTS) {
                nextStep()
                return
            }

            (pageRoot ?: activity.findViewById(android.R.id.content))?.doOnLayout {
                renderStep(step, token, attempt + 1)
            }
            return
        }

        if (step.scrollToTarget) {
            scrollToView(targetView)
        }


        pageRoot.doOnPreDraw {
            if (isTutorialActive && token == renderToken) {
                showTutorialOverlay(step, pageRoot, targetView)
            }
        }
    }

    private fun resolveTargetView(step: TutorialStep, pageRoot: ViewGroup?): View? {
        if (pageRoot == null) return null

        if (step.targetViewTag != null && step.targetViewId == R.id.app_list) {
            val recyclerView = pageRoot.findViewById<RecyclerView>(R.id.app_list) ?: return null
            return recyclerView.findViewWithTag(step.targetViewTag)
        }

        return if (step.targetViewTag != null) {
            pageRoot.findViewWithTag(step.targetViewTag)
        } else {
            pageRoot.findViewById(step.targetViewId)
        }
    }

    private fun openPage(page: TutorialPage, animated: Boolean, onReady: () -> Unit) {
        when (page) {
            TutorialPage.RSS -> activity.openRssPage(animated)
            TutorialPage.HOME -> activity.openHomePage(animated)
            TutorialPage.WIDGETS -> activity.openWidgetsPage(animated)
            TutorialPage.WALLPAPER -> activity.openWallpaperPage(animated)
        }


        val rootView = activity.findViewById<View>(page.rootViewId)
        if (animated) {
            rootView?.doOnLayout { onReady() } ?: onReady()
        } else {
            onReady()
        }
    }

    @SuppressLint("InflateParams")
    private fun showTutorialOverlay(
        step: TutorialStep,
        parentView: ViewGroup,
        targetView: View,
        titleOverride: String? = null,
        descriptionOverride: String? = null,
        allowTouchThrough: Boolean = false,
        showNextButton: Boolean? = null
    ) {
        removeTutorialOverlay()

        tutorialOverlay = LayoutInflater.from(activity).inflate(R.layout.tutorial_overlay, null)
        parentView.addView(tutorialOverlay)

        tutorialOverlay?.isClickable = !allowTouchThrough
        tutorialOverlay?.isFocusable = !allowTouchThrough
        if (!allowTouchThrough) {
            tutorialOverlay?.setOnClickListener { }
        }
        tutorialOverlay?.bringToFront()

        val titleText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_title)
        val descriptionText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_description)
        val buttonsContainer = tutorialOverlay?.findViewById<View>(R.id.tutorial_buttons_container)
        val skipButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_skip)
        val nextButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_next)
        val gotItButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_got_it)

        titleText?.text = titleOverride ?: step.title
        descriptionText?.text = descriptionOverride ?: step.description

        val isLastStep = currentStep == TutorialStep.entries.lastIndex
        buttonsContainer?.visibility = if (isLastStep) View.GONE else View.VISIBLE
        nextButton?.visibility = when {
            showNextButton != null -> if (showNextButton) View.VISIBLE else View.GONE
            isLastStep -> View.GONE
            else -> View.VISIBLE
        }
        gotItButton?.visibility = if (isLastStep) View.VISIBLE else View.GONE

        skipButton?.setOnClickListener { finishTutorial() }
        nextButton?.setOnClickListener { nextStep() }
        gotItButton?.setOnClickListener { finishTutorial() }

        tutorialOverlay?.post {
            positionTutorialOverlay(step, parentView, targetView)
        }
    }

    private fun positionTutorialOverlay(step: TutorialStep, parentView: ViewGroup, targetView: View) {
        val overlay = tutorialOverlay ?: return
        val highlightView = overlay.findViewById<View>(R.id.tutorial_highlight)
        val textContainer = overlay.findViewById<View>(R.id.tutorial_text_container)

        val targetLocation = IntArray(2)
        targetView.getLocationOnScreen(targetLocation)
        val rootLocation = IntArray(2)
        parentView.getLocationOnScreen(rootLocation)

        val targetX = targetLocation[0] - rootLocation[0]
        val targetY = targetLocation[1] - rootLocation[1]
        val targetWidth = targetView.width.takeIf { it > 0 } ?: targetView.measuredWidth
        val targetHeight = targetView.height.takeIf { it > 0 } ?: targetView.measuredHeight

        if (!step.highlightVisible || targetWidth <= 0 || targetHeight <= 0) {
            highlightView.visibility = View.GONE
        } else {
            val params = (highlightView.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(targetWidth + 40, targetHeight + 40)
            params.leftMargin = (targetX - 20).coerceAtLeast(0)
            params.topMargin = (targetY - 20).coerceAtLeast(0)
            params.width = targetWidth + 40
            params.height = targetHeight + 40
            highlightView.layoutParams = params
            highlightView.visibility = View.VISIBLE
        }

        val textParams = (textContainer.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        val screenWidth = parentView.width
        val screenHeight = parentView.height
        val padding = 40

        textContainer.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth - padding * 2, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textHeight = textContainer.measuredHeight
        val longTarget = targetHeight > screenHeight * 0.6
        var centerText = step.highlightPosition == HighlightPosition.CENTER

        when (step.highlightPosition) {
            HighlightPosition.TOP -> {
                val topMargin = (targetY - textHeight - padding).coerceAtLeast(padding)
                if (topMargin <= padding) {
                    centerText = true
                } else {
                    textParams.topMargin = topMargin
                }
            }

            HighlightPosition.BOTTOM -> {
                val topMargin = targetY + targetHeight + padding
                val maxTop = screenHeight - textHeight - padding
                if (longTarget || topMargin > maxTop) {
                    centerText = true
                } else {
                    textParams.topMargin = topMargin.coerceAtMost(maxTop)
                }
            }

            HighlightPosition.LEFT -> {
                textParams.topMargin = targetY
                textParams.leftMargin = padding
            }

            HighlightPosition.RIGHT -> {
                textParams.topMargin = targetY
                textParams.leftMargin = padding
            }

            HighlightPosition.CENTER -> {
                centerText = true
            }
        }

        if (centerText) {
            textParams.topMargin = ((screenHeight - textHeight) / 2).coerceAtLeast(padding)
        }

        textParams.leftMargin = padding
        textParams.rightMargin = padding
        textContainer.layoutParams = textParams
    }

    private fun scrollToView(targetView: View) {
        targetView.post {
            val scrollView = findVerticalScrollParent(targetView) ?: run {
                val rect = Rect(0, 0, targetView.width, targetView.height)
                val rootHeight = activity.findViewById<ViewGroup>(android.R.id.content)?.height ?: 0
                val centerOffset = (rootHeight / 2) - (targetView.height / 2)
                rect.top -= centerOffset
                rect.bottom += centerOffset
                targetView.requestRectangleOnScreen(rect, true)
                return@post
            }

            scrollView.post {
                val currentScrollY = scrollView.scrollY

                val targetLocation = IntArray(2)
                targetView.getLocationOnScreen(targetLocation)
                val scrollLocation = IntArray(2)
                scrollView.getLocationOnScreen(scrollLocation)

                val targetTopInContent = targetLocation[1] - scrollLocation[1] + currentScrollY
                val desiredScrollY =
                    (targetTopInContent - (scrollView.height / 2) + (targetView.height / 2)).coerceAtLeast(0)
                val maxScrollY =
                    (scrollView.getChildAt(0)?.height?.minus(scrollView.height) ?: 0).coerceAtLeast(0)
                val finalScrollY = desiredScrollY.coerceIn(0, maxScrollY)

                if (abs(finalScrollY - currentScrollY) > 10) {
                    scrollView.smoothScrollTo(0, finalScrollY)
                }
            }
        }
    }

    private fun scrollRecyclerToTutorialTarget(step: TutorialStep, pageRoot: ViewGroup): Boolean {
        if (step.targetViewId != R.id.app_list || step.targetViewTag == null) {
            return false
        }
        val recyclerView = pageRoot.findViewById<RecyclerView>(R.id.app_list) ?: return false
        if (recyclerView.findViewWithTag<View>(step.targetViewTag) != null) {
            return false
        }
        val itemCount = recyclerView.adapter?.itemCount ?: return false
        if (itemCount <= 0) {
            return false
        }
        val targetPosition = when (step.targetViewTag) {
            "launcher_settings_shortcut" -> (itemCount - 2).coerceAtLeast(0)
            "launcher_vault_shortcut" -> (itemCount - 1).coerceAtLeast(0)
            else -> return false
        }
        recyclerView.scrollToPosition(targetPosition)
        return true
    }

    private fun findVerticalScrollParent(view: View): NestedScrollView? {
        var parent = view.parent
        while (parent is View) {
            if (parent is NestedScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun nextStep() {
        waitingForUserSwipe = false
        currentStep++
        sharedPreferences.edit { putInt(PREF_TUTORIAL_STEP, currentStep) }

        if (currentStep >= TutorialStep.entries.size) {
            finishTutorial()
            return
        }

        showCurrentStep()
    }

    private fun launchSettingsTutorial() {
        currentStep++
        sharedPreferences.edit { putInt(PREF_TUTORIAL_STEP, currentStep) }

        removeTutorialOverlay()
        isTutorialActive = false
        renderToken++

        activity.startActivity(
            Intent(activity, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_START_SETTINGS_TUTORIAL, true)
            }
        )
    }

    private fun finishTutorial() {
        removeTutorialOverlay()
        isTutorialActive = false
        waitingForUserSwipe = false
        renderToken++
        activity.openHomePage(animated = false)
        markTutorialComplete()
        onTutorialCompleteCallback?.invoke()
        onTutorialCompleteCallback = null
    }

    private fun markTutorialComplete() {
        sharedPreferences.edit {
            putBoolean(PREF_TUTORIAL_SHOWN, true)
            putInt(PREF_TUTORIAL_STEP, TutorialStep.entries.size)
        }
    }

    private fun removeTutorialOverlay() {
        tutorialOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        tutorialOverlay = null
    }

    @Suppress("unused")
    fun resetTutorial() {
        renderToken++
        removeTutorialOverlay()
        isTutorialActive = false
        waitingForUserSwipe = false
        sharedPreferences.edit {
            putBoolean(PREF_TUTORIAL_SHOWN, false)
            putInt(PREF_TUTORIAL_STEP, 0)
        }
    }
}
