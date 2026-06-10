package com.example.aicameraassistant

data class CameraRemoteUiState(
    val roomStatus: String = "waiting",
    val connectionState: AppConnectionState = AppConnectionState.IDLE,
    val lensFacing: String = "back",
    val zoomLevel: Double = 1.0,
    val flashMode: String = "off",
    val cameraMode: String = "photo",
    val aspectRatioMode: String = "full",
    val portraitBlurLevel: String = "blur",
    val portraitStrength: Int = 5,
    val portraitEffect: String = "blur",
    val portraitStatus: String = "Finding subject...",
    val portraitFaceLeft: Double = 0.0,
    val portraitFaceTop: Double = 0.0,
    val portraitFaceRight: Double = 0.0,
    val portraitFaceBottom: Double = 0.0,
    val faceDetected: Boolean = false,
    val faceBox: NormalizedFaceBounds = NormalizedFaceBounds(),
    val faceBoxes: List<NormalizedFaceBounds> = emptyList(),
    val faceDetectionTimestamp: Long = 0L,
    val sceneDetection: SceneDetectionState = SceneDetectionState(),
    val sceneDetectionEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val videoHdrSupported: Boolean = false,
    val videoHdrEnabled: Boolean = false,
    val toolbarExpanded: Boolean = false,
    val captureRequestId: Long = 0L,
    val captureRequestType: String = "photo",
    val requestReceived: Boolean = false,
    val controllerApproved: Boolean = false,
    val focusRequestId: Long = 0L,
    val focusPointX: Double? = null,
    val focusPointY: Double? = null,
    val focusLockEnabled: Boolean = false,
    val exposureIndex: Int = 0,
    val videoRecordingState: VideoRecordingState = VideoRecordingState.Idle,
    val offerSdp: String? = null,
    val rtcSessionId: String? = null,
    val sessionVersion: Long = 0L
)

data class CaptureRequestState(
    val requestId: Long = 0L,
    val requestType: String = "photo"
)

data class FocusRequestState(
    val requestId: Long = 0L,
    val x: Double? = null,
    val y: Double? = null
)

data class PortraitSubjectState(
    val status: String = "Finding subject...",
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0
)

data class SceneDetectionState(
    val key: String = "auto",
    val label: String = "Auto",
    val suggestion: String = "Scene detection ready",
    val confidence: Double = 0.0,
    val timestamp: Long = 0L,
    val autoAdjustment: String = ""
)

data class ControllerRemoteUiState(
    val roomStatus: String = "waiting",
    val connectionState: AppConnectionState = AppConnectionState.IDLE,
    val lensFacing: String = "back",
    val zoomLevel: Double = 1.0,
    val minZoom: Double = 1.0,
    val maxZoom: Double = 1.0,
    val flashMode: String = "off",
    val cameraMode: String = "photo",
    val aspectRatioMode: String = "full",
    val portraitBlurLevel: String = "blur",
    val portraitStrength: Int = 5,
    val portraitEffect: String = "blur",
    val portraitStatus: String = "Finding subject...",
    val portraitFaceLeft: Double = 0.0,
    val portraitFaceTop: Double = 0.0,
    val portraitFaceRight: Double = 0.0,
    val portraitFaceBottom: Double = 0.0,
    val faceDetected: Boolean = false,
    val faceBox: NormalizedFaceBounds = NormalizedFaceBounds(),
    val faceBoxes: List<NormalizedFaceBounds> = emptyList(),
    val faceDetectionTimestamp: Long = 0L,
    val sceneDetection: SceneDetectionState = SceneDetectionState(),
    val sceneDetectionEnabled: Boolean = false,
    val flashSupported: Boolean = false,
    val gridEnabled: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val videoHdrSupported: Boolean = false,
    val videoHdrEnabled: Boolean = false,
    val toolbarExpanded: Boolean = false,
    val exposureMinIndex: Int = 0,
    val exposureMaxIndex: Int = 0,
    val exposureIndex: Int = 0,
    val videoRecordingState: VideoRecordingState = VideoRecordingState.Idle,
    val answerSdp: String? = null,
    val rtcSessionId: String? = null,
    val sessionVersion: Long = 0L,
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val focusRequestId: Long = 0L,
    val focusLockEnabled: Boolean = false
)
