package com.example.aicameraassistant

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import org.webrtc.VideoTrack
import android.media.MediaActionSound

private class RoomWriteDispatcher(
    private val scope: CoroutineScope,
    private val tag: String = "SESSION_END"
) {
    fun launch(operation: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { Log.w(tag, "Room write failed during $operation", it) }
        }
    }
}

private class LocalSessionShutdown(
    private val scope: CoroutineScope,
    private val onExit: () -> Unit
) {
    private var shutdownStarted = false

    fun run(
        setIsEndingSession: (Boolean) -> Unit,
        exitScreen: Boolean,
        cleanup: () -> Unit = {}
    ) {
        if (shutdownStarted) return
        shutdownStarted = true
        setIsEndingSession(true)
        runCatching { cleanup() }
        runCatching { WebRtcSessionManager.stopLocalCamera() }
        runCatching { WebRtcSessionManager.clearConnections() }

        if (exitScreen) {
            scope.launch {
                delay(80)
                onExit()
            }
        }
    }
}

class HostSessionCoordinator(
    private val repository: FirebaseRoomRepository,
    private val roomCode: String,
    private val scope: CoroutineScope,
    private val context: Context,
    private val onExit: () -> Unit
) {
    private var lastRequestedExposureIndex: Int? = null
    private val remoteEndScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val roomWrites = RoomWriteDispatcher(scope)
    private val localShutdown = LocalSessionShutdown(scope, onExit)

    fun shutdownSession(
        isEndingSession: Boolean,
        setIsEndingSession: (Boolean) -> Unit,
        exitScreen: Boolean
    ) {
        if (isEndingSession) return

        localShutdown.run(
            setIsEndingSession = setIsEndingSession,
            exitScreen = exitScreen
        )
    }

    fun endSession(
        isEndingSession: Boolean,
        setIsEndingSession: (Boolean) -> Unit,
        sessionVersion: Long
    ) {
        remoteEndScope.launch {
            runCatching { repository.endSession(roomCode, sessionVersion) }
                .onFailure {
                    Log.e("SESSION_END", "Failed to end host session remotely", it)
                }
            }

        localShutdown.run(
            setIsEndingSession = setIsEndingSession,
            exitScreen = true
        )
    }

    fun updateApproval(approved: Boolean) {
        roomWrites.launch("approval update") {
            repository.updateApproval(roomCode, approved)
        }
    }

    fun updateFlashMode(currentMode: String, flashSupported: Boolean) {
        if (!flashSupported) return

        val nextFlashMode = when (currentMode) {
            "off" -> "auto"
            "auto" -> "on"
            else -> "off"
        }
        roomWrites.launch("flash update") {
            repository.updateFlashMode(roomCode, nextFlashMode)
        }
    }

    fun switchLens(currentFacing: String) {
        roomWrites.launch("lens switch") {
            repository.updateLensFacing(roomCode, if (currentFacing == "back") "front" else "back")
        }
    }

    fun updateGridEnabled(currentEnabled: Boolean) {
        roomWrites.launch("grid update") {
            repository.updateGridEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateNightModeEnabled(currentEnabled: Boolean) {
        roomWrites.launch("night mode update") {
            repository.updateNightModeEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateSceneDetectionEnabled(currentEnabled: Boolean) {
        roomWrites.launch("scene detection update") {
            repository.updateSceneDetectionEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateVideoHdrEnabled(currentEnabled: Boolean, supported: Boolean) {
        if (!supported) return
        roomWrites.launch("video HDR update") {
            repository.updateVideoHdrEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateToolbarExpanded(expanded: Boolean) {
        roomWrites.launch("toolbar update") {
            repository.updateToolbarExpanded(roomCode, expanded)
        }
    }

    fun updateExposureFromProgress(
        progress: Float,
        exposureMinIndex: Int,
        exposureMaxIndex: Int,
        currentExposureIndex: Int,
        onUiPulse: () -> Unit
    ) {
        if (exposureMinIndex == exposureMaxIndex) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetIndex = (
            exposureMinIndex +
                ((1f - clampedProgress) * (exposureMaxIndex - exposureMinIndex))
        ).roundToInt().coerceIn(exposureMinIndex, exposureMaxIndex)

        if (lastRequestedExposureIndex == currentExposureIndex) {
            lastRequestedExposureIndex = null
        }
        onUiPulse()
        if (targetIndex == currentExposureIndex || targetIndex == lastRequestedExposureIndex) return

        lastRequestedExposureIndex = targetIndex

        roomWrites.launch("exposure update") {
            repository.updateExposureIndex(roomCode, targetIndex)
        }
    }
}

class ControllerSessionCoordinator(
    private val repository: FirebaseRoomRepository,
    private val roomCode: String,
    private val scope: CoroutineScope,
    private val context: Context,
    private val onExit: () -> Unit,
    private val haptic: HapticFeedback,
    private val vibrator: Vibrator?,
    private val shutterSound: MediaActionSound
) {
    private var lastRequestedExposureIndex: Int? = null
    private val remoteEndScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val roomWrites = RoomWriteDispatcher(scope)
    private val localShutdown = LocalSessionShutdown(scope, onExit)

    fun shutdownSession(
        isEndingSession: Boolean,
        setIsEndingSession: (Boolean) -> Unit,
        performCleanup: () -> Unit,
        exitScreen: Boolean
    ) {
        if (isEndingSession) return

        localShutdown.run(
            setIsEndingSession = setIsEndingSession,
            exitScreen = exitScreen,
            cleanup = performCleanup
        )
    }

    fun endSession(
        isEndingSession: Boolean,
        setIsEndingSession: (Boolean) -> Unit,
        performCleanup: () -> Unit,
        sessionVersion: Long
    ) {
        remoteEndScope.launch {
            runCatching { repository.endSession(roomCode, sessionVersion) }
                .onFailure {
                    Log.e("SESSION_END", "Failed to end controller session remotely", it)
                }
            }

        localShutdown.run(
            setIsEndingSession = setIsEndingSession,
            exitScreen = true,
            cleanup = performCleanup
        )
    }

    fun updateFlashMode(currentMode: String, flashSupported: Boolean) {
        if (!flashSupported) return

        val nextFlashMode = when (currentMode) {
            "off" -> "auto"
            "auto" -> "on"
            else -> "off"
        }
        roomWrites.launch("flash update") {
            repository.updateFlashMode(roomCode, nextFlashMode)
        }
    }

    fun switchLens(currentFacing: String) {
        roomWrites.launch("lens switch") {
            repository.updateLensFacing(roomCode, if (currentFacing == "back") "front" else "back")
        }
    }

    fun updateGridEnabled(currentEnabled: Boolean) {
        roomWrites.launch("grid update") {
            repository.updateGridEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateNightModeEnabled(currentEnabled: Boolean) {
        roomWrites.launch("night mode update") {
            repository.updateNightModeEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateSceneDetectionEnabled(currentEnabled: Boolean) {
        roomWrites.launch("scene detection update") {
            repository.updateSceneDetectionEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateVideoHdrEnabled(currentEnabled: Boolean, supported: Boolean) {
        if (!supported) return
        roomWrites.launch("video HDR update") {
            repository.updateVideoHdrEnabled(roomCode, !currentEnabled)
        }
    }

    fun updateToolbarExpanded(expanded: Boolean) {
        roomWrites.launch("toolbar update") {
            repository.updateToolbarExpanded(roomCode, expanded)
        }
    }

    fun sendZoomUpdate(
        zoom: Float,
        minZoom: Double,
        maxZoom: Double,
        lastSentZoom: Double,
        force: Boolean,
        onLastSentZoomChanged: (Double) -> Unit
    ) {
        val clampedZoom = zoom.toDouble().coerceIn(minZoom, maxZoom)
        if (!force && !lastSentZoom.isNaN() && abs(lastSentZoom - clampedZoom) < 0.02) {
            return
        }

        onLastSentZoomChanged(clampedZoom)
        roomWrites.launch("zoom update") {
            repository.updateZoomLevel(roomCode, clampedZoom)
        }
    }

    fun triggerShutterEffect(
        setShutterPressed: (Boolean) -> Unit,
        setShutterFlashAlpha: (Float) -> Unit
    ) {
        setShutterPressed(true)
        setShutterFlashAlpha(0.15f)
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        runCatching {
            val deviceVibrator = vibrator
            if (deviceVibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    deviceVibrator.vibrate(VibrationEffect.createOneShot(24L, 110))
                } else {
                    @Suppress("DEPRECATION")
                    deviceVibrator.vibrate(24L)
                }
            }
        }
        scope.launch {
            delay(70)
            setShutterPressed(false)
            setShutterFlashAlpha(0f)
        }
    }

    fun triggerCaptureRequest(
        nextRequestId: Long,
        setCaptureRequestSequence: (Long) -> Unit,
        setShutterPressed: (Boolean) -> Unit,
        setShutterFlashAlpha: (Float) -> Unit,
        requestType: String = "photo"
    ) {
        setCaptureRequestSequence(nextRequestId)
        triggerShutterEffect(
            setShutterPressed = setShutterPressed,
            setShutterFlashAlpha = setShutterFlashAlpha
        )
        roomWrites.launch("capture request") {
            Log.d("AICameraAssistant", "Sending capture request type=$requestType id=$nextRequestId")
            repository.sendCaptureRequest(roomCode, nextRequestId, requestType)
        }
    }

    fun startBurstCapture(
        isBurstCapturing: Boolean,
        setIsBurstCapturing: (Boolean) -> Unit,
        setBurstCaptureCount: (Int) -> Unit,
        setBurstJob: (Job?) -> Unit,
        triggerCapture: () -> Unit
    ) {
        if (isBurstCapturing) return
        setIsBurstCapturing(true)
        setBurstCaptureCount(0)
        setBurstJob(null)

        val job = scope.launch {
            var localCount = 0
            while (isActive) {
                localCount += 1
                setBurstCaptureCount(localCount)
                triggerCapture()
                delay(220)
            }
        }
        setBurstJob(job)
    }

    fun stopBurstCapture(
        setIsBurstCapturing: (Boolean) -> Unit,
        burstJob: Job?,
        setBurstJob: (Job?) -> Unit
    ) {
        setIsBurstCapturing(false)
        burstJob?.cancel()
        setBurstJob(null)
    }

    fun sendFocusRequest(
        tapOffset: Offset,
        previewRect: Rect,
        currentFocusRequestId: Long,
        lockFocus: Boolean,
        onFocusUiUpdated: (Offset, Boolean) -> Unit
    ) {
        if (!previewRect.contains(tapOffset)) return

        val clampedPoint = tapOffset.clampOffsetTo(previewRect)
        val previewWidth = previewRect.width
        val previewHeight = previewRect.height
        if (previewWidth <= 0f || previewHeight <= 0f) return

        val normalizedX = (clampedPoint.x - previewRect.left) / previewWidth
        val normalizedY = (clampedPoint.y - previewRect.top) / previewHeight

        onFocusUiUpdated(clampedPoint, lockFocus)
        roomWrites.launch("focus request") {
            repository.updateFocusRequest(
                roomCode = roomCode,
                normalizedX = normalizedX.toDouble(),
                normalizedY = normalizedY.toDouble(),
                requestId = currentFocusRequestId + 1L,
                lockEnabled = lockFocus
            )
        }
    }

    fun updateExposureFromProgress(
        progress: Float,
        exposureMinIndex: Int,
        exposureMaxIndex: Int,
        currentExposureIndex: Int,
        onUiPulse: () -> Unit
    ) {
        if (exposureMinIndex == exposureMaxIndex) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetIndex = (
            exposureMinIndex +
                ((1f - clampedProgress) * (exposureMaxIndex - exposureMinIndex))
        ).roundToInt().coerceIn(exposureMinIndex, exposureMaxIndex)

        if (lastRequestedExposureIndex == currentExposureIndex) {
            lastRequestedExposureIndex = null
        }
        onUiPulse()
        if (targetIndex == currentExposureIndex || targetIndex == lastRequestedExposureIndex) return

        lastRequestedExposureIndex = targetIndex

        roomWrites.launch("exposure update") {
            repository.updateExposureIndex(roomCode, targetIndex)
        }
    }
}

fun nextCaptureRequestId(currentRequestId: Long): Long =
    maxOf(currentRequestId + 1L, System.currentTimeMillis())

fun releaseRemotePreview(
    remoteTrack: VideoTrack?,
    rendererReleaser: () -> Unit
) {
    remoteTrack?.let { _ -> rendererReleaser() }
}
