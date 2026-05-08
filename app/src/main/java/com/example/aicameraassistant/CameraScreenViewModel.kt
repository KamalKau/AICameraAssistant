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
                repository.getPortraitBlurLevel(roomCode),
                repository.getPortraitStrength(roomCode),
                repository.getPortraitEffect(roomCode),
                repository.getPortraitSubjectState(roomCode),
                repository.getFaceDetectionOverlayState(roomCode),
                repository.getGridEnabled(roomCode),
                repository.getNightModeEnabled(roomCode),
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
                CameraRemoteUiState(
                    roomStatus = values[0] as String,
                    connectionState = values[1] as AppConnectionState,
                    lensFacing = values[2] as String,
                    zoomLevel = values[3] as Double,
                    flashMode = values[4] as String,
                    cameraMode = values[5] as String,
                    portraitBlurLevel = values[6] as String,
                    portraitStrength = values[7] as Int,
                    portraitEffect = values[8] as String,
                    portraitStatus = (values[9] as PortraitSubjectState).status,
                    portraitFaceLeft = (values[9] as PortraitSubjectState).left,
                    portraitFaceTop = (values[9] as PortraitSubjectState).top,
                    portraitFaceRight = (values[9] as PortraitSubjectState).right,
                    portraitFaceBottom = (values[9] as PortraitSubjectState).bottom,
                    faceDetected = (values[10] as FaceDetectionOverlayState).faceDetected,
                    faceBox = (values[10] as FaceDetectionOverlayState).faceBox,
                    faceDetectionTimestamp = (values[10] as FaceDetectionOverlayState).timestamp,
                    gridEnabled = values[11] as Boolean,
                    nightModeEnabled = values[12] as Boolean,
                    toolbarExpanded = values[13] as Boolean,
                    captureRequestId = (values[14] as CaptureRequestState).requestId,
                    captureRequestType = (values[14] as CaptureRequestState).requestType,
                    requestReceived = values[15] as Boolean,
                    controllerApproved = values[16] as Boolean,
                    focusRequestId = values[17] as Long,
                    focusPointX = values[18] as Double,
                    focusPointY = values[19] as Double,
                    focusLockEnabled = values[20] as Boolean,
                    exposureIndex = values[21] as Int,
                    offerSdp = values[22] as String?,
                    rtcSessionId = values[23] as String?,
                    sessionVersion = values[24] as Long
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
