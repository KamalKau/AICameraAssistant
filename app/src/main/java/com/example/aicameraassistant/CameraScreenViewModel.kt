package com.example.aicameraassistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CameraScreenViewModel : ViewModel() {
    private val _remoteUiState = MutableStateFlow(CameraRemoteUiState())
    val remoteUiState: StateFlow<CameraRemoteUiState> = _remoteUiState.asStateFlow()
    private var bindJob: Job? = null
    private var boundRoomCode: String? = null

    var flashAlpha by mutableFloatStateOf(0f)
    var answerCreated by mutableStateOf(false)
    var hasSeenConnectedState by mutableStateOf(false)
    var isEndingSession by mutableStateOf(false)
    var focusPoint by mutableStateOf<Offset?>(null)
    var focusSucceeded by mutableStateOf<Boolean?>(null)
    var focusLocked by mutableStateOf(false)
    var focusUiToken by mutableIntStateOf(0)
    var lastAppliedRemoteFocusRequestId by mutableStateOf(0L)
    var lastHandledCaptureRequestId by mutableStateOf(0L)
    var exposureMinIndex by mutableIntStateOf(0)
    var exposureMaxIndex by mutableIntStateOf(0)
    var exposureIndex by mutableIntStateOf(0)
    var showManualBrightnessControl by mutableStateOf(false)
    var manualExposureProgressOverride by mutableStateOf<Float?>(null)
    var isRemoteDescriptionSet by mutableStateOf(false)
    var lastHandledOfferSessionId by mutableStateOf<String?>(null)
    var boomerangInProgress by mutableStateOf(false)
    var captureMode by mutableStateOf("photo")
    var videoRecordingState by mutableStateOf(VideoRecordingState.Idle)

    private fun resetSessionState() {
        flashAlpha = 0f
        answerCreated = false
        hasSeenConnectedState = false
        isEndingSession = false
        focusPoint = null
        focusSucceeded = null
        focusLocked = false
        lastAppliedRemoteFocusRequestId = 0L
        lastHandledCaptureRequestId = 0L
        showManualBrightnessControl = false
        manualExposureProgressOverride = null
        isRemoteDescriptionSet = false
        lastHandledOfferSessionId = null
        boomerangInProgress = false
        captureMode = "photo"
        videoRecordingState = VideoRecordingState.Idle
    }

    fun bind(repository: FirebaseRoomRepository, roomCode: String) {
        resetSessionState()
        _remoteUiState.value = CameraRemoteUiState()

        if (boundRoomCode == roomCode && bindJob?.isActive == true) return

        bindJob?.cancel()
        boundRoomCode = roomCode
        bindJob = viewModelScope.launch {
            combine<Any?, CameraRemoteUiState>(
                repository.getRoomStatus(roomCode),
                WebRtcSessionManager.cameraConnectionState,
                repository.getLensFacing(roomCode),
                repository.getZoomLevel(roomCode),
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
                repository.getGridEnabled(roomCode),
                repository.getNightModeEnabled(roomCode),
                repository.getVideoHdrSupported(roomCode),
                repository.getVideoHdrEnabled(roomCode),
                repository.getToolbarExpanded(roomCode),
                repository.getCaptureRequestState(roomCode),
                repository.getRequestReceived(roomCode),
                repository.getControllerApproved(roomCode),
                repository.getFocusRequestId(roomCode),
                repository.getFocusPointX(roomCode),
                repository.getFocusPointY(roomCode),
                repository.getFocusLockEnabled(roomCode),
                repository.getExposureIndex(roomCode),
                repository.getOfferSdp(roomCode),
                repository.getRtcSessionId(roomCode),
                repository.getSessionVersion(roomCode)
            ) { values: Array<Any?> ->
                val portraitSubject = values[10] as? PortraitSubjectState ?: PortraitSubjectState()
                val faceOverlay = values[11] as? FaceDetectionOverlayState ?: FaceDetectionOverlayState()
                CameraRemoteUiState(
                    roomStatus = values[0] as? String ?: "waiting",
                    connectionState = values[1] as? AppConnectionState ?: AppConnectionState.IDLE,
                    lensFacing = values[2] as? String ?: "back",
                    zoomLevel = values[3] as? Double ?: 1.0,
                    flashMode = values[4] as? String ?: "off",
                    cameraMode = values[5] as? String ?: "photo",
                    aspectRatioMode = values[6] as? String ?: "full",
                    portraitBlurLevel = values[7] as? String ?: "blur",
                    portraitStrength = values[8] as? Int ?: 5,
                    portraitEffect = values[9] as? String ?: "blur",
                    portraitStatus = portraitSubject.status,
                    portraitFaceLeft = portraitSubject.left,
                    portraitFaceTop = portraitSubject.top,
                    portraitFaceRight = portraitSubject.right,
                    portraitFaceBottom = portraitSubject.bottom,
                    faceDetected = faceOverlay.faceDetected,
                    faceBox = faceOverlay.faceBox,
                    faceBoxes = faceOverlay.faceBoxes,
                    faceDetectionTimestamp = faceOverlay.timestamp,
                    sceneDetection = values[12] as? SceneDetectionState ?: SceneDetectionState(),
                    sceneDetectionEnabled = values[13] as? Boolean ?: false,
                    gridEnabled = values[14] as? Boolean ?: false,
                    nightModeEnabled = values[15] as? Boolean ?: false,
                    videoHdrSupported = values[16] as? Boolean ?: false,
                    videoHdrEnabled = values[17] as? Boolean ?: false,
                    toolbarExpanded = values[18] as? Boolean ?: false,
                    captureRequestId = (values[19] as? CaptureRequestState)?.requestId ?: 0L,
                    captureRequestType = (values[19] as? CaptureRequestState)?.requestType ?: "photo",
                    requestReceived = values[20] as? Boolean ?: false,
                    controllerApproved = values[21] as? Boolean ?: false,
                    focusRequestId = values[22] as? Long ?: 0L,
                    focusPointX = values[23] as? Double ?: 0.5,
                    focusPointY = values[24] as? Double ?: 0.5,
                    focusLockEnabled = values[25] as? Boolean ?: false,
                    exposureIndex = values[26] as? Int ?: 0,
                    offerSdp = values[27] as String?,
                    rtcSessionId = values[28] as String?,
                    sessionVersion = values[29] as? Long ?: 0L
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
