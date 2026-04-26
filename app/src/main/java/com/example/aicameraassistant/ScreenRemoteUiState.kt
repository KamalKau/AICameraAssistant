package com.example.aicameraassistant

data class CameraRemoteUiState(
    val roomStatus: String = "waiting",
    val connectionState: AppConnectionState = AppConnectionState.IDLE,
    val lensFacing: String = "back",
    val zoomLevel: Double = 1.0,
    val flashMode: String = "off",
    val gridEnabled: Boolean = false,
    val captureRequestId: Long = 0L,
    val requestReceived: Boolean = false,
    val controllerApproved: Boolean = false,
    val focusRequestId: Long = 0L,
    val focusPointX: Double = 0.5,
    val focusPointY: Double = 0.5,
    val focusLockEnabled: Boolean = false,
    val exposureIndex: Int = 0,
    val offerSdp: String? = null
)

data class ControllerRemoteUiState(
    val roomStatus: String = "waiting",
    val connectionState: AppConnectionState = AppConnectionState.IDLE,
    val lensFacing: String = "back",
    val zoomLevel: Double = 1.0,
    val minZoom: Double = 1.0,
    val maxZoom: Double = 1.0,
    val flashMode: String = "off",
    val flashSupported: Boolean = false,
    val gridEnabled: Boolean = false,
    val exposureMinIndex: Int = 0,
    val exposureMaxIndex: Int = 0,
    val exposureIndex: Int = 0,
    val answerSdp: String? = null,
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val focusRequestId: Long = 0L,
    val focusLockEnabled: Boolean = false
)
