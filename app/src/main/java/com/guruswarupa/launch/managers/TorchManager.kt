package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import com.guruswarupa.launch.R





class TorchManager(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var isTorchOn = false
    private var cameraId: String? = null

    init {
        cameraManager?.let { manager ->
            try {

                for (id in manager.cameraIdList) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (flashAvailable) {
                        cameraId = id
                        break
                    }
                }
            } catch (_: Exception) {

            }
        }
    }





    fun toggleTorch(): Boolean {
        val manager = cameraManager
        val id = cameraId
        if (manager == null || id == null) {
            Toast.makeText(context, R.string.torch_not_available, Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            isTorchOn = !isTorchOn
            manager.setTorchMode(id, isTorchOn)
            true
        } catch (_: CameraAccessException) {
            Toast.makeText(context, R.string.torch_camera_unavailable, Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn
            false
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.torch_toggle_error, e.message), Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn
            false
        }
    }




    fun turnOffTorch() {
        val manager = cameraManager
        val id = cameraId
        if (isTorchOn && manager != null && id != null) {
            try {
                manager.setTorchMode(id, false)
                isTorchOn = false
            } catch (_: Exception) {

            }
        }
    }
}
