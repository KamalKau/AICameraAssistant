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
                    gridEnabled = values[9] as Boolean,
                    nightModeEnabled = values[10] as Boolean,
                    toolbarExpanded = values[11] as Boolean,
                    captureRequestId = (values[12] as CaptureRequestState).requestId,
                    captureRequestType = (values[12] as CaptureRequestState).requestType,
                    requestReceived = values[13] as Boolean,
                    controllerApproved = values[14] as Boolean,
                    focusRequestId = values[15] as Long,
                    focusPointX = values[16] as Double,
                    focusPointY = values[17] as Double,
                    focusLockEnabled = values[18] as Boolean,
                    exposureIndex = values[19] as Int,
                    offerSdp = values[20] as String?,
                    rtcSessionId = values[21] as String?,
                    sessionVersion = values[22] as Long
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
