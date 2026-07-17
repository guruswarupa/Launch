package com.guruswarupa.launch.handlers

import android.os.Handler
import android.os.Looper
import com.guruswarupa.launch.managers.GestureHandler
import com.guruswarupa.launch.managers.ScreenPagerManager





class NavigationManager(
    private val screenPagerManager: ScreenPagerManager,
    private val gestureHandler: GestureHandler,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var isBlockingBackGesture = false
    private val backGestureBlockDuration = 800L




    fun handleBackPressed(superOnBackPressed: () -> Unit) {

        if (isBlockingBackGesture) {
            return
        }

        val defaultPage = screenPagerManager.getDefaultPage()

        if (!screenPagerManager.isPageOpen(defaultPage)) {
            screenPagerManager.openDefaultHomePage(animated = true)
        } else {
            superOnBackPressed()
        }
    }





    fun blockBackGesturesTemporarily() {
        isBlockingBackGesture = true

        gestureHandler.updateGestureExclusionForWidgetOpening()


        handler.postDelayed({
            isBlockingBackGesture = false

            gestureHandler.updateGestureExclusion()
        }, backGestureBlockDuration)
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }




    @Suppress("unused")
    fun isBlockingBackGesture(): Boolean = isBlockingBackGesture
}
