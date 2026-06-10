package com.example.aicameraassistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import org.webrtc.VideoTrack
import kotlinx.coroutines.launch

class ControllerScreenViewModel : ViewModel() {
    private val _remoteUiState = MutableStateFlow(ControllerRemoteUiState())
    val remoteUiState: StateFlow<ControllerRemoteUiState> = _remoteUiState.asStateFlow()
    private var bindJob: Job? = null
    private var boundRoomCode: String? = null

    var remoteTrack by mutableStateOf<VideoTrack?>(WebRtcSessionManager.remoteVideoTrack)
    var offerCreated by mutableStateOf(false)
    var hasSeenConnectedState by mutableStateOf(false)
    var isEndingSession by mutableStateOf(false)
    var zoomUiValue by mutableStateOf(1f)
    var isZoomDragging by mutableStateOf(false)
    var showZoomRing by mutableStateOf(false)
    var lastSentZoom by mutableStateOf(Double.NaN)
    var focusPoint by mutableStateOf<Offset?>(null)
    var focusSucceeded by mutableStateOf<Boolean?>(null)
    var focusLocked by mutableStateOf(false)
    var focusUiToken by mutableIntStateOf(0)
    var showManualBrightnessControl by mutableStateOf(false)
    var manualExposureProgressOverride by mutableStateOf<Float?>(null)
    var previewOverlayRect by mutableStateOf<Rect?>(null)
    var shutterFlashAlpha by mutableFloatStateOf(0f)
    var shutterPressed by mutableStateOf(false)
    var captureRequestSequence by mutableLongStateOf(0L)
    var captureMode by mutableStateOf("photo")
    var videoRecordingInProgress by mutableStateOf(false)
    var videoRecordingPaused by mutableStateOf(false)
    var showPortraitControls by mutableStateOf(false)
    var burstJob by mutableStateOf<Job?>(null)
    var isBurstCapturing by mutableStateOf(false)
    var burstCaptureCount by mutableIntStateOf(0)
    var lastFrameTimestampMs by mutableLongStateOf(0L)
    var uiNowMs by mutableLongStateOf(0L)
    var isRemoteDescriptionSet by mutableStateOf(false)
    var currentOfferSessionId by mutableStateOf<String?>(null)
    var lastOfferCreatedAtMs by mutableLongStateOf(0L)
    var previewRetryCount by mutableIntStateOf(0)

    private fun resetSessionState() {
        remoteTrack = null
        offerCreated = false
        hasSeenConnectedState = false
        isEndingSession = false
        zoomUiValue = 1f
        isZoomDragging = false
        showZoomRing = false
        lastSentZoom = Double.NaN
        focusPoint = null
        focusSucceeded = null
        focusLocked = false
        showManualBrightnessControl = false
        manualExposureProgressOverride = null
        previewOverlayRect = null
        shutterFlashAlpha = 0f
        shutterPressed = false
        captureMode = "photo"
        videoRecordingInProgress = false
        videoRecordingPaused = false
        showPortraitControls = false
        burstJob?.cancel()
        burstJob = null
        isBurstCapturing = false
        burstCaptureCount = 0
        lastFrameTimestampMs = 0L
        uiNowMs = 0L
        isRemoteDescriptionSet = false
        currentOfferSessionId = null
        lastOfferCreatedAtMs = 0L
        previewRetryCount = 0
    }

    fun bind(repository: FirebaseRoomRepository, roomCode: String) {
        resetSessionState()
        _remoteUiState.value = ControllerRemoteUiState(roomStatus = "request_received")

        if (boundRoomCode == roomCode && bindJob?.isActive == true) return

        bindJob?.cancel()
        boundRoomCode = roomCode
        bindJob = viewModelScope.launch {
            combine<Any?, ControllerRemoteUiState>(
                repository.getRoomStatus(roomCode),
                WebRtcSessionManager.controllerConnectionState,
                repository.getLensFacing(roomCode),
                repository.getZoomLevel(roomCode),
                repository.getMinZoom(roomCode),
                repository.getMaxZoom(roomCode),
                repository.getFlashMode(roomCode),
                repository.getCameraMode(roomCode),
                repository.getAspectRatioMode(roomCode),
                repository.getPortraitBlurLevel(roomCode),
                repository.getPortraitStrength(roomCode),
                repository.getPortraitEffect(roomCode),
                repository.getPortraitSubjectState(roomCode),
                repository.getFaceDetectionOverlayState(roomCode),
                repository.getSceneDetectionState(roomCode),
                repository.getSceneDetectionEnabled(roomCode),
                repository.getFlashSupported(roomCode),
                repository.getGridEnabled(roomCode),
                repository.getNightModeEnabled(roomCode),
                repository.getVideoHdrSupported(roomCode),
                repository.getVideoHdrEnabled(roomCode),
                repository.getVideoRecordingState(roomCode),
                repository.getToolbarExpanded(roomCode),
                repository.getExposureMinIndex(roomCode),
                repository.getExposureMaxIndex(roomCode),
                repository.getExposureIndex(roomCode),
                repository.getAnswerSdp(roomCode),
                repository.getRtcSessionId(roomCode),
                repository.getSessionVersion(roomCode),
                repository.getPreviewWidth(roomCode),
                repository.getPreviewHeight(roomCode),
                repository.getFocusRequestId(roomCode),
                repository.getFocusLockEnabled(roomCode)
            ) { values: Array<Any?> ->
                val portraitSubject = values[12] as? PortraitSubjectState ?: PortraitSubjectState()
                val faceOverlay = values[13] as? FaceDetectionOverlayState ?: FaceDetectionOverlayState()
                ControllerRemoteUiState(
                    roomStatus = values[0] as? String ?: "waiting",
                    connectionState = values[1] as? AppConnectionState ?: AppConnectionState.IDLE,
                    lensFacing = values[2] as? String ?: "back",
                    zoomLevel = values[3] as? Double ?: 1.0,
                    minZoom = values[4] as? Double ?: 1.0,
                    maxZoom = values[5] as? Double ?: 1.0,
                    flashMode = values[6] as? String ?: "off",
                    cameraMode = values[7] as? String ?: "photo",
                    aspectRatioMode = values[8] as? String ?: "full",
                    portraitBlurLevel = values[9] as? String ?: "blur",
                    portraitStrength = values[10] as? Int ?: 5,
                    portraitEffect = values[11] as? String ?: "blur",
                    portraitStatus = portraitSubject.status,
                    portraitFaceLeft = portraitSubject.left,
                    portraitFaceTop = portraitSubject.top,
                    portraitFaceRight = portraitSubject.right,
                    portraitFaceBottom = portraitSubject.bottom,
                    faceDetected = faceOverlay.faceDetected,
                    faceBox = faceOverlay.faceBox,
                    faceBoxes = faceOverlay.faceBoxes,
                    faceDetectionTimestamp = faceOverlay.timestamp,
                    sceneDetection = values[14] as? SceneDetectionState ?: SceneDetectionState(),
                    sceneDetectionEnabled = values[15] as? Boolean ?: false,
                    flashSupported = values[16] as? Boolean ?: false,
                    gridEnabled = values[17] as? Boolean ?: false,
                    nightModeEnabled = values[18] as? Boolean ?: false,
                    videoHdrSupported = values[19] as? Boolean ?: false,
                    videoHdrEnabled = values[20] as? Boolean ?: false,
                    videoRecordingState = values[21] as? VideoRecordingState ?: VideoRecordingState.Idle,
                    toolbarExpanded = values[22] as? Boolean ?: false,
                    exposureMinIndex = values[23] as? Int ?: 0,
                    exposureMaxIndex = values[24] as? Int ?: 0,
                    exposureIndex = values[25] as? Int ?: 0,
                    answerSdp = values[26] as String?,
                    rtcSessionId = values[27] as String?,
                    sessionVersion = values[28] as? Long ?: 0L,
                    previewWidth = values[29] as? Int ?: 0,
                    previewHeight = values[30] as? Int ?: 0,
                    focusRequestId = values[31] as? Long ?: 0L,
                    focusLockEnabled = values[32] as? Boolean ?: false
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
