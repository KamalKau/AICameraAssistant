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
import kotlinx.coroutines.flow.update
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
    var isRemoteDescriptionSet by mutableStateOf(false)

    fun bind(repository: FirebaseRoomRepository, roomCode: String) {
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
                repository.getGridEnabled(roomCode),
                repository.getCaptureRequestId(roomCode),
                repository.getRequestReceived(roomCode),
                repository.getControllerApproved(roomCode),
                repository.getFocusRequestId(roomCode),
                repository.getFocusPointX(roomCode),
                repository.getFocusPointY(roomCode),
                repository.getFocusLockEnabled(roomCode),
                repository.getExposureIndex(roomCode),
                repository.getOfferSdp(roomCode)
            ) { values: Array<Any?> ->
                CameraRemoteUiState(
                    roomStatus = values[0] as String,
                    connectionState = values[1] as AppConnectionState,
                    lensFacing = values[2] as String,
                    zoomLevel = values[3] as Double,
                    flashMode = values[4] as String,
                    gridEnabled = values[5] as Boolean,
                    captureRequestId = values[6] as Long,
                    requestReceived = values[7] as Boolean,
                    controllerApproved = values[8] as Boolean,
                    focusRequestId = values[9] as Long,
                    focusPointX = values[10] as Double,
                    focusPointY = values[11] as Double,
                    focusLockEnabled = values[12] as Boolean,
                    exposureIndex = values[13] as Int,
                    offerSdp = values[14] as String?
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
