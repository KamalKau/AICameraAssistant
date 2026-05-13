package com.example.aicameraassistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.graphics.Color

fun buildHostTopOverlayUiState(
    roomCode: String,
    roomStatus: String,
    connectionState: AppConnectionState,
    sessionIsActive: Boolean,
    requestReceived: Boolean,
    controllerApproved: Boolean,
    isEndingSession: Boolean
): HostTopOverlayUiState {
    val statusText = when {
        !sessionIsActive -> when (roomStatus) {
            "request_received" -> "Connection request received"
            "denied" -> "Request denied"
            else -> "Waiting for controller"
        }

        else -> when (connectionState) {
            AppConnectionState.IDLE,
            AppConnectionState.CONNECTING -> "Connecting..."
            AppConnectionState.CONNECTED -> "Controller connected"
            AppConnectionState.WEAK_NETWORK -> "Weak network"
            AppConnectionState.RETRYING -> "Reconnecting..."
            AppConnectionState.DISCONNECTED -> "Controller disconnected"
        }
    }

    val statusDotColor = when {
        !sessionIsActive -> when (roomStatus) {
            "request_received" -> Color(0xFFFF9800)
            "denied" -> Color(0xFFF44336)
            else -> Color(0xFFFFC107)
        }

        else -> when (connectionState) {
            AppConnectionState.CONNECTED -> Color(0xFF4CAF50)
            AppConnectionState.WEAK_NETWORK -> Color(0xFFFFB300)
            AppConnectionState.RETRYING,
            AppConnectionState.CONNECTING,
            AppConnectionState.IDLE -> Color(0xFFFF9800)
            AppConnectionState.DISCONNECTED -> Color(0xFFF44336)
        }
    }

    val warningText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK -> "Network unstable"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Connection lost"
        else -> null
    }

    val warningDetailText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK,
        AppConnectionState.RETRYING -> "Video quality may be affected"
        AppConnectionState.DISCONNECTED -> "Unable to reconnect"
        else -> null
    }

    return HostTopOverlayUiState(
        status = StatusUiState(
            text = statusText,
            dotColor = statusDotColor,
            warningText = warningText,
            warningDetailText = warningDetailText
        ),
        roomCode = roomCode,
        sessionIsActive = sessionIsActive,
        showApprovalPrompt = requestReceived && !controllerApproved,
        isEndingSession = isEndingSession
    )
}

fun buildControllerStatusUiState(connectionState: AppConnectionState): StatusUiState {
    val badgeText = when (connectionState) {
        AppConnectionState.IDLE,
        AppConnectionState.CONNECTING -> "Connecting..."
        AppConnectionState.CONNECTED -> "Connected"
        AppConnectionState.WEAK_NETWORK -> "Weak network"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Disconnected"
    }

    val badgeColor = when (connectionState) {
        AppConnectionState.CONNECTED -> Color(0xFF4CAF50)
        AppConnectionState.WEAK_NETWORK -> Color(0xFFFFB300)
        AppConnectionState.RETRYING,
        AppConnectionState.CONNECTING,
        AppConnectionState.IDLE -> Color(0xFFFF9800)
        AppConnectionState.DISCONNECTED -> Color(0xFFF44336)
    }

    val warningText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK -> "Network unstable"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Connection lost"
        else -> null
    }

    val warningDetailText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK,
        AppConnectionState.RETRYING -> "Video quality may be affected"
        AppConnectionState.DISCONNECTED -> "Unable to reconnect"
        else -> null
    }

    return StatusUiState(
        text = badgeText,
        dotColor = badgeColor,
        warningText = warningText,
        warningDetailText = warningDetailText
    )
}

fun buildCameraToolRailUiState(
    flashSupported: Boolean,
    flashMode: String,
    lensFacing: String,
    gridEnabled: Boolean,
    nightModeEnabled: Boolean,
    toolbarExpanded: Boolean,
    boomerangSelected: Boolean,
    exposureSupported: Boolean
): CameraToolRailUiState =
    CameraToolRailUiState(
        flashIcon = when {
            !flashSupported -> Icons.Default.FlashOff
            flashMode == "auto" -> Icons.Default.FlashAuto
            flashMode == "on" -> Icons.Default.FlashOn
            else -> Icons.Default.FlashOff
        },
        flashLabel = when {
            !flashSupported -> "Unsupported"
            flashMode == "auto" -> "Auto"
            flashMode == "on" -> "On"
            else -> "Off"
        },
        flashEnabled = flashSupported,
        lensLabel = if (lensFacing == "back") "Rear" else "Front",
        gridEnabled = gridEnabled,
        nightModeEnabled = nightModeEnabled,
        toolbarExpanded = toolbarExpanded,
        boomerangSelected = boomerangSelected,
        exposureSupported = exposureSupported
    )

fun buildExposureUiState(
    minIndex: Int,
    maxIndex: Int,
    currentIndex: Int,
    manualProgressOverride: Float?,
    visible: Boolean
): ExposureUiState {
    val supported = minIndex != maxIndex
    val remoteProgress =
        ((maxIndex - currentIndex).toFloat() / (maxIndex - minIndex).toFloat().coerceAtLeast(1f))
            .coerceIn(0f, 1f)
    val progress = (manualProgressOverride ?: remoteProgress).coerceIn(0f, 1f)
    val neutralProgress = defaultExposureProgress(minIndex, maxIndex)

    return ExposureUiState(
        supported = supported,
        visible = visible,
        progress = progress,
        remoteProgress = remoteProgress,
        neutralProgress = neutralProgress,
        label = buildExposureLabel(currentIndex, minIndex, maxIndex),
        frontPreviewOverlay = buildPreviewExposureOverlay(
            currentProgress = progress,
            neutralProgress = neutralProgress
        )
    )
}

fun buildControllerCommonZoomOptions(
    minZoom: Double,
    maxZoom: Double
): List<Float> =
    listOf(0.6f, 1f, 2f, 3f)
        .filter { it.toDouble() in minZoom..maxZoom }
        .ifEmpty { listOf(minZoom.toFloat()) }

fun buildControllerBottomControlsUiState(
    roomCode: String,
    showZoomRing: Boolean,
    zoomUiValue: Float,
    minZoom: Float,
    maxZoom: Float,
    commonZoomOptions: List<Float>,
    isBurstCapturing: Boolean,
    isVideoMode: Boolean,
    isVideoRecording: Boolean,
    isVideoPaused: Boolean,
    burstCaptureCount: Int,
    shutterScale: Float,
    shutterCoreScale: Float,
    portraitControlsVisible: Boolean,
    portraitControlsEnabled: Boolean,
    portraitStrength: Int,
    portraitEffect: String
): ControllerBottomControlsUiState =
    ControllerBottomControlsUiState(
        roomCode = roomCode,
        showZoomRing = showZoomRing,
        zoomUiValue = zoomUiValue,
        minZoom = minZoom,
        maxZoom = maxZoom,
        commonZoomOptions = commonZoomOptions,
        isBurstCapturing = isBurstCapturing,
        isVideoMode = isVideoMode,
        isVideoRecording = isVideoRecording,
        isVideoPaused = isVideoPaused,
        burstCaptureCount = burstCaptureCount,
        shutterScale = shutterScale,
        shutterCoreScale = shutterCoreScale,
        portraitControlsVisible = portraitControlsVisible,
        portraitControlsEnabled = portraitControlsEnabled,
        portraitStrength = portraitStrength,
        portraitEffect = portraitEffect
    )
