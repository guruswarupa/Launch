package com.guruswarupa.launch.handlers

import android.os.SystemClock





object GestureCoordinator {
    private var lastTriggerTime = 0L
    private const val MUTUAL_EXCLUSION_WINDOW = 1200L





    @Synchronized
    fun requestTrigger(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < MUTUAL_EXCLUSION_WINDOW) {
            return false
        }
        lastTriggerTime = now
        return true
    }





    fun isInCooldown(): Boolean {
        return SystemClock.elapsedRealtime() - lastTriggerTime < MUTUAL_EXCLUSION_WINDOW
    }
}
