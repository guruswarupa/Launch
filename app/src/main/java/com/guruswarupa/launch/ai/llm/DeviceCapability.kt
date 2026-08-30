package com.guruswarupa.launch.ai.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeviceCapabilityResult {
    data object Supported : DeviceCapabilityResult
    data class Unsupported(val reason: Reason) : DeviceCapabilityResult

    enum class Reason { LOW_RAM, UNSUPPORTED_ABI, LOW_STORAGE }
}

/**
 * Gates the on-device AI assistant behind a capability check so it is never offered
 * on a device where it would run poorly or fail to download. Checked once when the
 * user opens the AI settings section, not on every app launch.
 */
@Singleton
class DeviceCapability @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MIN_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024 // 3 GB
        private const val MIN_FREE_STORAGE_BYTES = 1_500_000_000L // 1.5 GB, covers download + final copy
        private val SUPPORTED_64_BIT_ABIS = setOf("arm64-v8a", "x86_64")
    }

    fun check(): DeviceCapabilityResult {
        if (Build.SUPPORTED_ABIS.none { it in SUPPORTED_64_BIT_ABIS }) {
            return DeviceCapabilityResult.Unsupported(DeviceCapabilityResult.Reason.UNSUPPORTED_ABI)
        }

        val activityManager = context.getSystemService<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem in 1..(MIN_TOTAL_RAM_BYTES - 1)) {
            return DeviceCapabilityResult.Unsupported(DeviceCapabilityResult.Reason.LOW_RAM)
        }

        val freeBytes = StatFs(context.filesDir.path).let { it.availableBlocksLong * it.blockSizeLong }
        if (freeBytes < MIN_FREE_STORAGE_BYTES) {
            return DeviceCapabilityResult.Unsupported(DeviceCapabilityResult.Reason.LOW_STORAGE)
        }

        return DeviceCapabilityResult.Supported
    }
}
