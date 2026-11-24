package com.myra.assistant.managers

import android.content.Context
import android.hardware.camera2.CameraManager

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../managers/FlashlightManager.java).
 */
class FlashlightManager(private val context: Context) {

    /**
     * Turns the torch on/off on the FIRST camera id returned by the
     * CameraManager (no check that that camera actually has a flash —
     * that is exactly what the decompiled code does). Does nothing when
     * the device reports no cameras.
     *
     * NOTE: the decompiled method has NO try/catch, so a
     * CameraAccessException from setTorchMode propagates to the caller,
     * exactly as in the original. Callers: MainViewModel.setFlashlight.
     */
    fun setFlashlight(on: Boolean) {
        val cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
        cameraManager.setTorchMode(cameraId, on)
    }
}
