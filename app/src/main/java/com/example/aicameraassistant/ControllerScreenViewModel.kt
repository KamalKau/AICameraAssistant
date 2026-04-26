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
    var previewOverlayRect by mutableStateOf<Rect?>(null)
    var shutterFlashAlpha by mutableFloatStateOf(0f)
    var shutterPressed by mutableStateOf(false)
    var captureRequestSequence by mutableLongStateOf(0L)
    var burstJob by mutableStateOf<Job?>(null)
    var isBurstCapturing by mutableStateOf(false)
    var burstCaptureCount by mutableIntStateOf(0)
    var lastFrameTimestampMs by mutableLongStateOf(0L)
    var uiNowMs by mutableLongStateOf(0L)
    var isRemoteDescriptionSet by mutableStateOf(false)

    fun bind(repository: FirebaseRoomRepository, roomCode: String) {
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
                repository.getFlashSupported(roomCode),
                repository.getGridEnabled(roomCode),
                repository.getExposureMinIndex(roomCode),
                repository.getExposureMaxIndex(roomCode),
                repository.getExposureIndex(roomCode),
                repository.getAnswerSdp(roomCode),
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
                    flashSupported = values[7] as Boolean,
                    gridEnabled = values[8] as Boolean,
                    exposureMinIndex = values[9] as Int,
                    exposureMaxIndex = values[10] as Int,
                    exposureIndex = values[11] as Int,
                    answerSdp = values[12] as String?,
                    previewWidth = values[13] as Int,
                    previewHeight = values[14] as Int,
                    focusRequestId = values[15] as Long,
                    focusLockEnabled = values[16] as Boolean
                )
            }.collect { state ->
                _remoteUiState.value = state
            }
        }
    }
}
