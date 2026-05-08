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
                repository.getPortraitBlurLevel(roomCode),
                repository.getPortraitStrength(roomCode),
                repository.getPortraitEffect(roomCode),
                repository.getPortraitSubjectState(roomCode),
                repository.getFaceDetectionOverlayState(roomCode),
                repository.getFlashSupported(roomCode),
                repository.getGridEnabled(roomCode),
                repository.getNightModeEnabled(roomCode),
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
                ControllerRemoteUiState(
                    roomStatus = values[0] as String,
                    connectionState = values[1] as AppConnectionState,
                    lensFacing = values[2] as String,
                    zoomLevel = values[3] as Double,
                    minZoom = values[4] as Double,
                    maxZoom = values[5] as Double,
                    flashMode = values[6] as String,
                    cameraMode = values[7] as String,
                    portraitBlurLevel = values[8] as String,
                    portraitStrength = values[9] as Int,
                    portraitEffect = values[10] as String,
                    portraitStatus = (values[11] as PortraitSubjectState).status,
                    portraitFaceLeft = (values[11] as PortraitSubjectState).left,
                    portraitFaceTop = (values[11] as PortraitSubjectState).top,
                    portraitFaceRight = (values[11] as PortraitSubjectState).right,
                    portraitFaceBottom = (values[11] as PortraitSubjectState).bottom,
                    faceDetected = (values[12] as FaceDetectionOverlayState).faceDetected,
                    faceBox = (values[12] as FaceDetectionOverlayState).faceBox,
                    faceDetectionTimestamp = (values[12] as FaceDetectionOverlayState).timestamp,
                    flashSupported = values[13] as Boolean,
                    gridEnabled = values[14] as Boolean,
                    nightModeEnabled = values[15] as Boolean,
                    toolbarExpanded = values[16] as Boolean,
                    exposureMinIndex = values[17] as Int,
                    exposureMaxIndex = values[18] as Int,
                    exposureIndex = values[19] as Int,
                    answerSdp = values[20] as String?,
                    rtcSessionId = values[21] as String?,
                    sessionVersion = values[22] as Long,
                    previewWidth = values[23] as Int,
                    previewHeight = values[24] as Int,
                    focusRequestId = values[25] as Long,
                    focusLockEnabled = values[26] as Boolean
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
