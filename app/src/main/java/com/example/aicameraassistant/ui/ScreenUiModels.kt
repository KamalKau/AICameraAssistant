package com.example.aicameraassistant

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class StatusUiState(
    val text: String,
    val dotColor: Color,
    val warningText: String? = null,
    val warningDetailText: String? = null
)

data class HostTopOverlayUiState(
    val status: StatusUiState,
    val roomCode: String,
    val sessionIsActive: Boolean,
    val showApprovalPrompt: Boolean,
    val isEndingSession: Boolean
)

data class HostTopOverlayActions(
    val onEndSession: () -> Unit,
    val onAllowController: () -> Unit,
    val onDenyController: () -> Unit
)

data class CameraToolRailUiState(
    val flashIcon: ImageVector,
    val flashLabel: String,
    val flashEnabled: Boolean,
    val lensLabel: String,
    val gridEnabled: Boolean,
    val nightModeEnabled: Boolean,
    val toolbarExpanded: Boolean,
    val boomerangSelected: Boolean,
    val exposureSupported: Boolean
)

data class CameraToolRailActions(
    val onFlashClick: () -> Unit,
    val onBoomerangClick: () -> Unit,
    val onLensClick: () -> Unit,
    val onGridClick: () -> Unit,
    val onNightModeClick: () -> Unit,
    val onExposureClick: () -> Unit,
    val onToolbarExpandedChange: (Boolean) -> Unit
)

data class ExposureUiState(
    val supported: Boolean,
    val visible: Boolean,
    val progress: Float,
    val remoteProgress: Float,
    val neutralProgress: Float,
    val label: String,
    val frontPreviewOverlay: PreviewExposureOverlayState
)

data class ExposureUiActions(
    val onToggle: () -> Unit,
    val onProgressChange: (Float) -> Unit,
    val onDismiss: () -> Unit,
    val onReset: () -> Unit
)

data class ControllerBottomControlsUiState(
    val roomCode: String,
    val showZoomRing: Boolean,
    val zoomUiValue: Float,
    val minZoom: Float,
    val maxZoom: Float,
    val commonZoomOptions: List<Float>,
    val isBurstCapturing: Boolean,
    val burstCaptureCount: Int,
    val shutterScale: Float,
    val shutterCoreScale: Float,
    val portraitControlsVisible: Boolean,
    val portraitControlsEnabled: Boolean,
    val portraitStrength: Int,
    val portraitEffect: String
)

data class ControllerBottomControlsActions(
    val onZoomBarValueChange: (Float) -> Unit,
    val onZoomBarFinished: () -> Unit,
    val onZoomPresetClick: (Float) -> Unit,
    val onZoomPresetLongPress: () -> Unit,
    val onPortraitControlsClick: () -> Unit,
    val onPortraitStrengthSelected: (Int) -> Unit,
    val onPortraitEffectSelected: (String) -> Unit,
    val onShutterPress: suspend androidx.compose.foundation.gestures.PressGestureScope.(androidx.compose.ui.geometry.Offset) -> Unit
)
