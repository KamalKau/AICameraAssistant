package com.example.aicameraassistant

internal object HdrCapturePolicy {
    fun shouldUsePhotoExtension(
        enabled: Boolean,
        supported: Boolean,
        cameraMode: String,
        nightModeEnabled: Boolean,
        flashMode: String
    ): Boolean =
        enabled &&
            supported &&
            cameraMode == "photo" &&
            !nightModeEnabled &&
            flashMode == "off"
}
