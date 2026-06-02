package com.example.aicameraassistant

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.MediaActionSound
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.webrtc.IceCandidate
import org.webrtc.RendererCommon
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WaitingForApprovalScreen(
    roomCode: String,
    repository: FirebaseRoomRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenViewModel: ControllerScreenViewModel = viewModel()
    val haptic = LocalHapticFeedback.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    LaunchedEffect(roomCode) {
        screenViewModel.bind(repository, roomCode)
    }
    val remoteUiState by screenViewModel.remoteUiState.collectAsState()
    val roomStatus = remoteUiState.roomStatus
    val connectionState = remoteUiState.connectionState
    val firebaseLensFacing = remoteUiState.lensFacing
    val firebaseZoomLevel = remoteUiState.zoomLevel
    val firebaseMinZoom = remoteUiState.minZoom
    val firebaseMaxZoom = remoteUiState.maxZoom
    val firebaseFlashMode = remoteUiState.flashMode
    val firebaseCameraMode = remoteUiState.cameraMode
    val firebaseAspectRatioMode = remoteUiState.aspectRatioMode
    val firebasePortraitBlurLevel = remoteUiState.portraitBlurLevel
    val firebasePortraitStrength = remoteUiState.portraitStrength
    val firebasePortraitEffect = remoteUiState.portraitEffect
    val firebasePortraitStatus = remoteUiState.portraitStatus
    val firebasePortraitFaceLeft = remoteUiState.portraitFaceLeft
    val firebasePortraitFaceTop = remoteUiState.portraitFaceTop
    val firebasePortraitFaceRight = remoteUiState.portraitFaceRight
    val firebasePortraitFaceBottom = remoteUiState.portraitFaceBottom
    val firebaseFaceDetected = remoteUiState.faceDetected
    val firebaseFaceBox = remoteUiState.faceBox
    val firebaseFaceBoxes = remoteUiState.faceBoxes
    val firebaseFaceDetectionTimestamp = remoteUiState.faceDetectionTimestamp
    val firebaseSceneDetection = remoteUiState.sceneDetection
    val firebaseFlashSupported = remoteUiState.flashSupported
    val firebaseGridEnabled = remoteUiState.gridEnabled
    val firebaseNightModeEnabled = remoteUiState.nightModeEnabled
    val firebaseVideoHdrSupported = remoteUiState.videoHdrSupported
    val firebaseVideoHdrEnabled = remoteUiState.videoHdrEnabled
    val firebaseToolbarExpanded = remoteUiState.toolbarExpanded
    val firebaseExposureMinIndex = remoteUiState.exposureMinIndex
    val firebaseExposureMaxIndex = remoteUiState.exposureMaxIndex
    val firebaseExposureIndex = remoteUiState.exposureIndex
    val firebaseAnswer = remoteUiState.answerSdp
    val firebaseRtcSessionId = remoteUiState.rtcSessionId
    val firebaseSessionVersion = remoteUiState.sessionVersion
    val cameraPreviewWidth = remoteUiState.previewWidth
    val cameraPreviewHeight = remoteUiState.previewHeight
    val firebaseFocusRequestId = remoteUiState.focusRequestId
    val firebaseFocusLockEnabled = remoteUiState.focusLockEnabled

    var previewContainerRef by remember { mutableStateOf<ControllerPreviewContainer?>(null) }
    var remoteTrack by screenViewModel::remoteTrack
    var offerCreated by screenViewModel::offerCreated
    var hasSeenConnectedState by screenViewModel::hasSeenConnectedState
    var isEndingSession by screenViewModel::isEndingSession
    var zoomUiValue by screenViewModel::zoomUiValue
    var isZoomDragging by screenViewModel::isZoomDragging
    var showZoomRing by screenViewModel::showZoomRing
    var lastSentZoom by screenViewModel::lastSentZoom
    var focusPoint by screenViewModel::focusPoint
    var focusSucceeded by screenViewModel::focusSucceeded
    var focusLocked by screenViewModel::focusLocked
    var focusUiToken by screenViewModel::focusUiToken
    var showManualBrightnessControl by screenViewModel::showManualBrightnessControl
    var manualExposureProgressOverride by screenViewModel::manualExposureProgressOverride
    var previewOverlayRect by screenViewModel::previewOverlayRect
    var shutterFlashAlpha by screenViewModel::shutterFlashAlpha
    var shutterPressed by screenViewModel::shutterPressed
    var boomerangCaptureEffectVisible by remember { mutableStateOf(false) }
    var boomerangCaptureEffectToken by remember { mutableLongStateOf(0L) }
    var captureRequestSequence by screenViewModel::captureRequestSequence
    var captureMode by screenViewModel::captureMode
    var videoRecordingInProgress by screenViewModel::videoRecordingInProgress
    var videoRecordingPaused by screenViewModel::videoRecordingPaused
    var showPortraitControls by screenViewModel::showPortraitControls
    var burstJob by screenViewModel::burstJob
    var isBurstCapturing by screenViewModel::isBurstCapturing
    var burstCaptureCount by screenViewModel::burstCaptureCount

    var remoteFrameWidth by remember { mutableIntStateOf(0) }
    var remoteFrameHeight by remember { mutableIntStateOf(0) }
    var remoteFrameRotation by remember { mutableIntStateOf(0) }
    var lastFrameTimestampMs by screenViewModel::lastFrameTimestampMs
    var uiNowMs by screenViewModel::uiNowMs
    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by screenViewModel::isRemoteDescriptionSet
    var currentOfferSessionId by screenViewModel::currentOfferSessionId
    var lastOfferCreatedAtMs by screenViewModel::lastOfferCreatedAtMs
    var previewRetryCount by screenViewModel::previewRetryCount
    var remoteFaceBoxBounds by remember { mutableStateOf(emptyList<NormalizedFaceBounds>()) }
    var remoteFaceBoxVisible by remember { mutableStateOf(false) }
    val aspectRatioMode = AspectRatioMode.fromKey(firebaseAspectRatioMode)
    val fillSelectedAspectFrame = aspectRatioMode != AspectRatioMode.Full

    LaunchedEffect(firebaseAspectRatioMode) {
        previewOverlayRect = null
    }

    val shouldSwapRemoteFrame =
        (remoteFrameRotation == 90 || remoteFrameRotation == 270) ||
            (
                remoteFrameRotation == 0 &&
                    cameraPreviewHeight > cameraPreviewWidth &&
                    remoteFrameWidth > remoteFrameHeight
                )

    val normalizedRemoteFrameWidth = when {
        remoteFrameWidth > 0 && shouldSwapRemoteFrame -> remoteFrameHeight
        else -> remoteFrameWidth
    }

    val normalizedRemoteFrameHeight = when {
        remoteFrameHeight > 0 && shouldSwapRemoteFrame -> remoteFrameWidth
        else -> remoteFrameHeight
    }

    val shouldRotatePreviewContent =
        remoteFrameRotation == 0 &&
            cameraPreviewHeight > cameraPreviewWidth &&
            remoteFrameWidth > remoteFrameHeight

    LaunchedEffect(firebaseFaceDetected, firebaseFaceDetectionTimestamp) {
        if (firebaseFaceDetected && (firebaseFaceBoxes.any { it.isValid() } || firebaseFaceBox.isValid())) {
            remoteFaceBoxBounds = firebaseFaceBoxes.ifEmpty { listOf(firebaseFaceBox) }
            remoteFaceBoxVisible = true
        } else {
            remoteFaceBoxVisible = false
            remoteFaceBoxBounds = emptyList()
        }
    }

    LaunchedEffect(
        previewContainerRef,
        firebaseCameraMode,
        remoteFaceBoxBounds,
        remoteFaceBoxVisible
    ) {
        previewContainerRef?.setFaceDetectionOverlay(
            bounds = remoteFaceBoxBounds,
            visible = firebaseCameraMode != "portrait" && remoteFaceBoxVisible
        )
    }

    val controllerDisplayWidth = normalizedRemoteFrameWidth
    val controllerDisplayHeight = normalizedRemoteFrameHeight
    val minZoom = firebaseMinZoom.coerceAtLeast(1.0)
    val maxZoom = firebaseMaxZoom.coerceAtLeast(minZoom)
    val controllerMaxZoom = maxZoom.coerceAtLeast(CONTROLLER_ZOOM_BAR_MAX)
    val exposureUiState = buildExposureUiState(
        minIndex = firebaseExposureMinIndex,
        maxIndex = firebaseExposureMaxIndex,
        currentIndex = firebaseExposureIndex,
        manualProgressOverride = manualExposureProgressOverride,
        visible = showManualBrightnessControl
    )
    val controllerStatusUiState = buildControllerStatusUiState(connectionState)
    val isVideoStalled =
        roomStatus == "connected" &&
            connectionState != AppConnectionState.CONNECTED &&
            lastFrameTimestampMs > 0L &&
            uiNowMs - lastFrameTimestampMs > 2_500L
    val stalledVideoText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK -> "Video paused. Weak network"
        AppConnectionState.RETRYING -> "Reconnecting video..."
        AppConnectionState.DISCONNECTED -> "Connection lost"
        AppConnectionState.CONNECTED -> "Video paused. Network unstable"
        else -> "Connecting video..."
    }
    val commonZoomOptions = remember(minZoom, maxZoom) {
        buildControllerCommonZoomOptions(minZoom, controllerMaxZoom)
    }
    val flashModes = listOf("off", "auto", "on")
    val shutterScale by animateFloatAsState(
        targetValue = if (shutterPressed || isBurstCapturing) 0.92f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "controller_shutter_scale"
    )
    val shutterCoreScale by animateFloatAsState(
        targetValue = if (isBurstCapturing) 0.72f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "controller_shutter_core_scale"
    )
    val shutterSound = remember {
        MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
        }
    }
    val controllerCoordinator = remember(
        roomCode,
        repository,
        scope,
        context,
        onBack,
        haptic,
        vibrator,
        shutterSound
    ) {
        ControllerSessionCoordinator(
            repository = repository,
            roomCode = roomCode,
            scope = scope,
            context = context,
            onExit = onBack,
            haptic = haptic,
            vibrator = vibrator,
            shutterSound = shutterSound
        )
    }
    fun launchRoomWrite(operation: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { Log.w("AICameraAssistant", "Room write failed during $operation", it) }
        }
    }
    val controllerToolRailUiState = buildCameraToolRailUiState(
        flashSupported = firebaseFlashSupported,
        flashMode = firebaseFlashMode,
        lensFacing = firebaseLensFacing,
        aspectRatioMode = firebaseAspectRatioMode,
        gridEnabled = firebaseGridEnabled,
        nightModeEnabled = firebaseNightModeEnabled,
        videoHdrSupported = firebaseVideoHdrSupported,
        videoHdrEnabled = firebaseVideoHdrEnabled,
        cameraMode = firebaseCameraMode,
        toolbarExpanded = firebaseToolbarExpanded,
        boomerangSelected = captureMode == "boomerang",
        exposureSupported = exposureUiState.supported
    )
    val controllerExposureUiActions = ExposureUiActions(
        onToggle = {
            if (exposureUiState.supported) {
                if (!exposureUiState.visible) {
                    manualExposureProgressOverride = exposureUiState.remoteProgress
                }
                showManualBrightnessControl = !showManualBrightnessControl
                if (!showManualBrightnessControl) {
                    manualExposureProgressOverride = null
                }
            }
        },
        onProgressChange = { progress ->
            manualExposureProgressOverride = progress.coerceIn(0f, 1f)
            controllerCoordinator.updateExposureFromProgress(
                progress = progress,
                exposureMinIndex = firebaseExposureMinIndex,
                exposureMaxIndex = firebaseExposureMaxIndex,
                currentExposureIndex = firebaseExposureIndex,
                onUiPulse = { focusUiToken++ }
            )
        },
        onDismiss = {
            showManualBrightnessControl = false
            manualExposureProgressOverride = null
        },
        onReset = {
            val defaultProgress = defaultExposureProgress(
                minIndex = firebaseExposureMinIndex,
                maxIndex = firebaseExposureMaxIndex
            )
            manualExposureProgressOverride = defaultProgress
            controllerCoordinator.updateExposureFromProgress(
                progress = defaultProgress,
                exposureMinIndex = firebaseExposureMinIndex,
                exposureMaxIndex = firebaseExposureMaxIndex,
                currentExposureIndex = firebaseExposureIndex,
                onUiPulse = { focusUiToken++ }
            )
        }
    )
    val controllerToolRailActions = CameraToolRailActions(
        onFlashClick = {
            controllerCoordinator.updateFlashMode(firebaseFlashMode, firebaseFlashSupported)
        },
        onBoomerangClick = {
            val selectingBoomerang = captureMode != "boomerang"
            captureMode = if (selectingBoomerang) "boomerang" else "photo"
            if (selectingBoomerang && firebaseCameraMode != "photo") {
                launchRoomWrite("boomerang mode reset") {
                    repository.updateCameraMode(roomCode, "photo")
                }
            }
        },
        onAspectRatioClick = {
            launchRoomWrite("aspect ratio update") {
                repository.updateAspectRatioMode(
                    roomCode,
                    AspectRatioMode.next(firebaseAspectRatioMode).key
                )
            }
        },
        onLensClick = {
            controllerCoordinator.switchLens(firebaseLensFacing)
        },
        onGridClick = {
            controllerCoordinator.updateGridEnabled(firebaseGridEnabled)
        },
        onNightModeClick = {
            controllerCoordinator.updateNightModeEnabled(firebaseNightModeEnabled)
        },
        onVideoHdrClick = {
            controllerCoordinator.updateVideoHdrEnabled(
                currentEnabled = firebaseVideoHdrEnabled,
                supported = firebaseVideoHdrSupported
            )
        },
        onExposureClick = controllerExposureUiActions.onToggle,
        onToolbarExpandedChange = { expanded ->
            controllerCoordinator.updateToolbarExpanded(expanded)
        }
    )
    val controllerBottomControlsUiState = buildControllerBottomControlsUiState(
        roomCode = roomCode,
        showZoomRing = showZoomRing,
        zoomUiValue = zoomUiValue,
        minZoom = minZoom.toFloat(),
        maxZoom = controllerMaxZoom.toFloat(),
        commonZoomOptions = commonZoomOptions,
        exposureVisible = exposureUiState.visible,
        exposureSupported = exposureUiState.supported,
        exposureProgress = exposureUiState.progress,
        exposureLabel = exposureUiState.label,
        isBurstCapturing = isBurstCapturing,
        isVideoMode = firebaseCameraMode == "video",
        isVideoRecording = videoRecordingInProgress,
        isVideoPaused = videoRecordingPaused,
        videoHdrSupported = firebaseVideoHdrSupported,
        videoHdrEnabled = firebaseVideoHdrEnabled,
        boomerangSelected = captureMode == "boomerang",
        burstCaptureCount = burstCaptureCount,
        shutterScale = shutterScale,
        shutterCoreScale = shutterCoreScale,
        portraitControlsVisible = firebaseCameraMode == "portrait" && showPortraitControls,
        portraitControlsEnabled = firebaseCameraMode == "portrait",
        portraitStrength = firebasePortraitStrength,
        portraitEffect = firebasePortraitEffect,
        lensFacing = firebaseLensFacing
    )
    DisposableEffect(Unit) {
        onDispose {
            shutterSound.release()
        }
    }

    fun releaseControllerPreview() {
        previewContainerRef?.detachRemoteTrack()
        previewContainerRef?.onVideoRectChanged = null
        remoteTrack = null
        remoteFrameWidth = 0
        remoteFrameHeight = 0
        remoteFrameRotation = 0
        lastFrameTimestampMs = 0L
    }

    fun sendZoomUpdate(zoom: Float, force: Boolean = false) {
        controllerCoordinator.sendZoomUpdate(
            zoom = zoom,
            minZoom = minZoom,
            maxZoom = controllerMaxZoom,
            lastSentZoom = lastSentZoom,
            force = force,
            onLastSentZoomChanged = { lastSentZoom = it }
        )
    }

    fun triggerShutterEffect() {
        controllerCoordinator.triggerShutterEffect(
            setShutterPressed = { shutterPressed = it },
            setShutterFlashAlpha = { shutterFlashAlpha = it }
        )
    }

    fun triggerCaptureRequest(requestTypeOverride: String? = null) {
        val requestType = requestTypeOverride ?: if (firebaseCameraMode == "video") {
            if (videoRecordingInProgress) "video_stop" else "video_start"
        } else {
            captureMode
        }
        controllerCoordinator.triggerCaptureRequest(
            nextRequestId = nextCaptureRequestId(captureRequestSequence),
            setCaptureRequestSequence = { captureRequestSequence = it },
            setShutterPressed = { shutterPressed = it },
            setShutterFlashAlpha = { shutterFlashAlpha = it },
            requestType = requestType
        )
        if (requestType == "boomerang") {
            val effectToken = boomerangCaptureEffectToken + 1L
            boomerangCaptureEffectToken = effectToken
            boomerangCaptureEffectVisible = true
            scope.launch {
                repeat(8) { index ->
                    if (boomerangCaptureEffectToken != effectToken) return@launch
                    shutterFlashAlpha = if (index % 2 == 0) 0.38f else 0.16f
                    delay(170)
                }
                if (boomerangCaptureEffectToken == effectToken) {
                    shutterFlashAlpha = 0f
                }
            }
            scope.launch {
                delay(1_650)
                if (boomerangCaptureEffectToken == effectToken) {
                    boomerangCaptureEffectVisible = false
                }
            }
        }
        if (requestType == "video") {
            videoRecordingInProgress = !videoRecordingInProgress
            videoRecordingPaused = false
        } else if (requestType == "video_start") {
            videoRecordingInProgress = true
            videoRecordingPaused = false
        } else if (requestType == "video_stop") {
            videoRecordingInProgress = false
            videoRecordingPaused = false
        } else if (requestType == "video_pause") {
            videoRecordingPaused = true
        } else if (requestType == "video_resume") {
            videoRecordingPaused = false
        }
    }

    fun startBurstCapture() {
        burstJob?.cancel()
        controllerCoordinator.startBurstCapture(
            isBurstCapturing = isBurstCapturing,
            setIsBurstCapturing = { isBurstCapturing = it },
            setBurstCaptureCount = { burstCaptureCount = it },
            setBurstJob = { burstJob = it },
            triggerCapture = { triggerCaptureRequest() }
        )
    }

    fun stopBurstCapture() {
        controllerCoordinator.stopBurstCapture(
            setIsBurstCapturing = { isBurstCapturing = it },
            burstJob = burstJob,
            setBurstJob = { burstJob = it }
        )
    }

    fun sendFocusRequest(tapOffset: Offset, previewRect: Rect, lockFocus: Boolean = false) {
        controllerCoordinator.sendFocusRequest(
            tapOffset = tapOffset,
            previewRect = previewRect,
            currentFocusRequestId = firebaseFocusRequestId,
            lockFocus = lockFocus,
            onFocusUiUpdated = { point, locked ->
                focusPoint = point
                focusSucceeded = null
                focusLocked = locked
                focusUiToken++
            }
        )
    }

    fun updateExposureFromProgress(progress: Float) {
        controllerCoordinator.updateExposureFromProgress(
            progress = progress,
            exposureMinIndex = firebaseExposureMinIndex,
            exposureMaxIndex = firebaseExposureMaxIndex,
            currentExposureIndex = firebaseExposureIndex,
            onUiPulse = { focusUiToken++ }
        )
    }

    fun updateCameraMode(mode: String) {
        if (mode == firebaseCameraMode) return
        if (mode != "portrait") {
            showPortraitControls = false
        }
        launchRoomWrite("camera mode update") {
            repository.updateCameraMode(roomCode, mode)
        }
    }

    fun updatePortraitBlurLevel(blurLevel: String) {
        if (blurLevel == firebasePortraitBlurLevel) return
        launchRoomWrite("portrait blur update") {
            repository.updatePortraitBlurLevel(roomCode, blurLevel)
        }
    }

    fun updatePortraitStrength(strength: Int) {
        if (strength == firebasePortraitStrength) return
        launchRoomWrite("portrait strength update") {
            repository.updatePortraitStrength(roomCode, strength)
        }
    }

    fun updatePortraitEffect(effect: String) {
        if (effect == firebasePortraitEffect) return
        launchRoomWrite("portrait effect update") {
            repository.updatePortraitEffect(roomCode, effect)
        }
    }

    LaunchedEffect(firebaseCameraMode) {
        if (firebaseCameraMode != "video") {
            videoRecordingInProgress = false
            videoRecordingPaused = false
        }
        if (firebaseCameraMode != "portrait") {
            showPortraitControls = false
        }
    }

    DisposableEffect(roomCode, currentOfferSessionId) {
        val activeRtcSessionId = currentOfferSessionId
        if (activeRtcSessionId == null) {
            onDispose { }
        } else {
            val registration = repository.listenToCameraIceCandidates(
                roomCode = roomCode,
                rtcSessionId = activeRtcSessionId
            ) { candidate ->
                if (isEndingSession || roomStatus != "connected") return@listenToCameraIceCandidates
                scope.launch(Dispatchers.Main) {
                    if (isEndingSession || roomStatus != "connected") return@launch
                    val pc = WebRtcSessionManager.controllerPeerConnection
                    if (isRemoteDescriptionSet && pc != null) {
                        Log.d("WEBRTC_LOG", "Controller applying camera candidate immediately")
                        runCatching { pc.addIceCandidate(candidate) }
                            .onFailure { Log.w("WEBRTC_LOG", "Controller ignored late ICE candidate", it) }
                    } else {
                        Log.d("WEBRTC_LOG", "Controller buffering camera candidate")
                        pendingCandidates.add(candidate)
                    }
                }
            }
            onDispose {
                registration.remove()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewContainerRef?.detachRemoteTrack()
            previewContainerRef?.releaseRenderer()
            previewContainerRef = null
            remoteTrack = null
            WebRtcSessionManager.clearConnections()
        }
    }

    LaunchedEffect(roomStatus, connectionState, offerCreated) {
        if (roomStatus == "connected") {
            hasSeenConnectedState = true
        }

        if (roomStatus == "ended" && hasSeenConnectedState) {
            showStatusPopup(
                context = context,
                title = "Session ended",
                detail = "Camera closed the session",
                badge = "END",
                accentColor = AndroidColor.rgb(255, 122, 69)
            )
            controllerCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                performCleanup = { releaseControllerPreview() },
                exitScreen = false
            )
            return@LaunchedEffect
        }

        if (hasSeenConnectedState && roomStatus == "waiting") {
            controllerCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                performCleanup = { releaseControllerPreview() },
                exitScreen = false
            )
            return@LaunchedEffect
        }

        val shouldCreateOffer =
            roomStatus == "connected" &&
                !isEndingSession &&
                (!offerCreated || connectionState == AppConnectionState.DISCONNECTED)

        if (shouldCreateOffer) {
            if (connectionState == AppConnectionState.DISCONNECTED) {
                delay(500)
                releaseControllerPreview()
                pendingCandidates.clear()
                isRemoteDescriptionSet = false
            }

            runCatching {
                repository.clearIceCandidates(roomCode)
            }.onFailure {
                Log.w("WEBRTC_LOG", "Unable to clear ICE candidates before creating offer", it)
            }

            val nextRtcSessionId = "${System.currentTimeMillis()}-${roomCode}"
            currentOfferSessionId = nextRtcSessionId
            lastOfferCreatedAtMs = SystemClock.elapsedRealtime()
            lastFrameTimestampMs = 0L
            val offerStarted = createSharedOffer(
                context = context,
                roomCode = roomCode,
                rtcSessionId = nextRtcSessionId,
                repository = repository,
                onRemoteTrackReady = { track ->
                    scope.launch(Dispatchers.Main) {
                        if (isEndingSession || roomStatus != "connected") return@launch
                        Log.d("WEBRTC_LOG", "Controller received remote track")
                        remoteTrack = track
                        previewContainerRef?.attachRemoteTrack(track) {
                            lastFrameTimestampMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            )
            if (offerStarted) {
                offerCreated = true
            } else {
                currentOfferSessionId = null
                lastOfferCreatedAtMs = 0L
            }
        }
    }

    LaunchedEffect(roomStatus, uiNowMs, offerCreated, lastFrameTimestampMs, lastOfferCreatedAtMs) {
        val waitingForFirstFrame =
            roomStatus == "connected" &&
                offerCreated &&
                lastOfferCreatedAtMs > 0L &&
                lastFrameTimestampMs == 0L &&
                uiNowMs - lastOfferCreatedAtMs > 5_500L

        if (!waitingForFirstFrame || previewRetryCount >= 3) return@LaunchedEffect

        Log.w(
            "WEBRTC_LOG",
            "Controller preview has no first frame; retrying WebRTC offer ${previewRetryCount + 1}/3"
        )
        previewRetryCount += 1
        releaseControllerPreview()
        pendingCandidates.clear()
        isRemoteDescriptionSet = false
        currentOfferSessionId = null
        lastOfferCreatedAtMs = 0L
        offerCreated = false
        WebRtcSessionManager.clearConnections()
    }

    LaunchedEffect(firebaseAnswer, firebaseRtcSessionId, currentOfferSessionId) {
        if (isEndingSession || roomStatus != "connected") return@LaunchedEffect
        val answer = firebaseAnswer ?: return@LaunchedEffect
        if (firebaseRtcSessionId == null || firebaseRtcSessionId != currentOfferSessionId) {
            Log.d("WEBRTC_LOG", "Controller ignoring stale answer for session=$firebaseRtcSessionId")
            return@LaunchedEffect
        }
        val pc = WebRtcSessionManager.controllerPeerConnection ?: return@LaunchedEffect

        if (pc.remoteDescription == null) {
            Log.d("WEBRTC_LOG", "Controller setting remote description (Answer)")
            runCatching {
                pc.setRemoteDescription(
                    WebRtcSessionManager.sessionDescriptionObserver(
                        onSetSuccess = {
                            scope.launch(Dispatchers.Main) {
                                if (isEndingSession || roomStatus != "connected") return@launch
                                Log.d(
                                    "WEBRTC_LOG",
                                    "Controller remote description set, applying ${pendingCandidates.size} candidates"
                                )
                                isRemoteDescriptionSet = true
                                pendingCandidates.forEach { candidate ->
                                    runCatching { pc.addIceCandidate(candidate) }
                                        .onFailure { Log.w("WEBRTC_LOG", "Controller ignored buffered ICE candidate", it) }
                                }
                                pendingCandidates.clear()
                            }
                        }
                    ),
                    SessionDescription(SessionDescription.Type.ANSWER, answer)
                )
            }.onFailure {
                Log.w("WEBRTC_LOG", "Controller ignored late remote description", it)
            }
        }
    }

    LaunchedEffect(roomStatus) {
        if (roomStatus == "connected" && remoteTrack == null) {
            WebRtcSessionManager.remoteVideoTrack?.let { existingTrack ->
                Log.d("WEBRTC_LOG", "Controller reusing existing remote track")
                remoteTrack = existingTrack
            }
        }
    }

    DisposableEffect(previewContainerRef, remoteTrack) {
        val container = previewContainerRef
        val track = remoteTrack
        if (container == null || track == null) {
            onDispose { }
        } else {
            Log.d("WEBRTC_LOG", "Controller rendering remote track")
            container.attachRemoteTrack(track) {
                lastFrameTimestampMs = SystemClock.elapsedRealtime()
            }

            onDispose {
                container.detachRemoteTrack()
            }
        }
    }

    LaunchedEffect(firebaseZoomLevel, minZoom, maxZoom, isZoomDragging) {
        if (!isZoomDragging) {
            zoomUiValue = firebaseZoomLevel.toFloat().coerceIn(
                minZoom.toFloat(),
                controllerMaxZoom.toFloat()
            )
        }
    }

    LaunchedEffect(focusUiToken) {
        if (focusUiToken == 0) return@LaunchedEffect
        if (focusLocked) return@LaunchedEffect
        delay(2600)
        focusPoint = null
        focusSucceeded = null
    }

    LaunchedEffect(roomStatus) {
        while (roomStatus == "connected" && isActive) {
            uiNowMs = SystemClock.elapsedRealtime()
            delay(750)
        }
    }

    if (roomStatus != "connected") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF05070B))
        ) {
            HomeWallpaper(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppFrontLogo(
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .size(76.dp)
                )

                Text(
                    text = when (roomStatus) {
                        "denied" -> "Request denied"
                        "request_received" -> "Waiting for approval"
                        else -> "Connecting"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (roomStatus == "denied") Color(0xFFFF6B72) else Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (roomStatus == "request_received") {
                        "The camera phone needs to approve this controller."
                    } else {
                        "Room $roomCode"
                    },
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (hasSeenConnectedState || roomStatus == "request_received") {
                            controllerCoordinator.endSession(
                                isEndingSession = isEndingSession,
                                setIsEndingSession = { isEndingSession = it },
                                performCleanup = { releaseControllerPreview() },
                                sessionVersion = firebaseSessionVersion
                            )
                        } else {
                            onBack()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = if (hasSeenConnectedState || roomStatus == "request_received") {
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF141414)
                        )
                    }
                ) {
                    Text(
                        if (hasSeenConnectedState || roomStatus == "request_received") {
                            if (isEndingSession) "Ending..." else "End session"
                        } else {
                            "Go back"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
    var previewContainerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { previewContainerSize = it }
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val boxMaxWidthPx = previewContainerSize.width.toFloat()
        val boxMaxHeightPx = previewContainerSize.height.toFloat()
        val previewFrameRect =
            remember(boxMaxWidthPx, boxMaxHeightPx, firebaseAspectRatioMode) {
                aspectRatioFrameRect(
                    containerWidth = boxMaxWidthPx,
                    containerHeight = boxMaxHeightPx,
                    aspectRatioKey = firebaseAspectRatioMode
                )
            }
        val fallbackPreviewContentRect =
            remember(previewFrameRect, controllerDisplayWidth, controllerDisplayHeight, fillSelectedAspectFrame) {
                if (fillSelectedAspectFrame) {
                    previewFrameRect
                } else {
                    fittedPreviewRectInFrame(
                        frameRect = previewFrameRect,
                        contentWidth = controllerDisplayWidth.toFloat(),
                        contentHeight = controllerDisplayHeight.toFloat()
                    )
                }
            }
        val activePreviewRect = previewOverlayRect ?: fallbackPreviewContentRect

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(minZoom, maxZoom) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange == 1f) return@detectTransformGestures

                            val nextZoom =
                                (zoomUiValue * zoomChange).coerceIn(
                                    minZoom.toFloat(),
                                    controllerMaxZoom.toFloat()
                                )

                            isZoomDragging = true
                            zoomUiValue = nextZoom
                            sendZoomUpdate(nextZoom)
                        }
                    }
                    .pointerInteropFilter { motionEvent ->
                        if (motionEvent.pointerCount <= 1 && isZoomDragging) {
                            isZoomDragging = false
                            sendZoomUpdate(zoomUiValue, force = true)
                        }
                        false
                    }
                    .pointerInput(roomCode) {
                        detectTapGestures(
                            onTap = { offset ->
                                activePreviewRect?.let { previewRect ->
                                    sendFocusRequest(
                                        tapOffset = offset,
                                        previewRect = previewRect,
                                        lockFocus = false
                                    )
                                }
                            },
                            onLongPress = { offset ->
                                activePreviewRect?.let { previewRect ->
                                    sendFocusRequest(
                                        tapOffset = offset,
                                        previewRect = previewRect,
                                        lockFocus = true
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        ControllerPreviewContainer(ctx).also { container ->
                            val renderer = container.renderer
                            renderer.init(
                                WebRtcSessionManager.eglBase.eglBaseContext,
                                object : RendererCommon.RendererEvents {
                                    override fun onFirstFrameRendered() {
                                        Log.d("WEBRTC_LOG", "Controller first remote frame rendered")
                                        lastFrameTimestampMs = SystemClock.elapsedRealtime()
                                        previewRetryCount = 0
                                    }

                                    override fun onFrameResolutionChanged(
                                        videoWidth: Int,
                                        videoHeight: Int,
                                        rotation: Int
                                    ) {
                                        scope.launch(Dispatchers.Main) {
                                            val frameChanged =
                                                remoteFrameWidth != videoWidth ||
                                                    remoteFrameHeight != videoHeight ||
                                                    remoteFrameRotation != rotation

                                            if (!frameChanged) return@launch

                                            remoteFrameWidth = videoWidth
                                            remoteFrameHeight = videoHeight
                                            remoteFrameRotation = rotation
                                            lastFrameTimestampMs = SystemClock.elapsedRealtime()

                                            val fittedWidth =
                                                if (rotation == 90 || rotation == 270) {
                                                    videoHeight
                                                } else {
                                                    videoWidth
                                                }
                                            val fittedHeight =
                                                if (rotation == 90 || rotation == 270) {
                                                    videoWidth
                                                } else {
                                                    videoHeight
                                                }

                                            previewContainerRef?.setVideoLayout(
                                                fittedWidth,
                                                fittedHeight,
                                                shouldRotatePreviewContent,
                                                fillSelectedAspectFrame
                                            )
                                        }
                                    }
                                }
                            )
                            renderer.setMirror(false)
                            renderer.setEnableHardwareScaler(true)
                            renderer.setScalingType(
                                RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                                RendererCommon.ScalingType.SCALE_ASPECT_FIT
                            )
                            if (controllerDisplayWidth > 0 && controllerDisplayHeight > 0) {
                                container.setVideoLayout(
                                    controllerDisplayWidth,
                                    controllerDisplayHeight,
                                    shouldRotatePreviewContent,
                                    fillSelectedAspectFrame
                                )
                            }
                            container.setFaceDetectionOverlay(
                                bounds = remoteFaceBoxBounds,
                                visible = firebaseCameraMode != "portrait" && remoteFaceBoxVisible
                            )
                            remoteTrack?.let { track ->
                                container.attachRemoteTrack(track) {
                                    lastFrameTimestampMs = SystemClock.elapsedRealtime()
                                }
                            }
                            container.onVideoRectChanged = { rect ->
                                val frameLeft = previewFrameRect?.left ?: 0f
                                val frameTop = previewFrameRect?.top ?: 0f
                                previewOverlayRect = Rect(
                                    frameLeft + rect.left,
                                    frameTop + rect.top,
                                    frameLeft + rect.right,
                                    frameTop + rect.bottom
                                )
                            }
                            previewContainerRef = container
                        }
                    },
                    update = { container ->
                        val renderer = container.renderer
                        renderer.setEnableHardwareScaler(true)
                        renderer.setScalingType(
                            RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                            RendererCommon.ScalingType.SCALE_ASPECT_FIT
                        )
                        if (controllerDisplayWidth > 0 && controllerDisplayHeight > 0) {
                            container.setVideoLayout(
                                controllerDisplayWidth,
                                controllerDisplayHeight,
                                shouldRotatePreviewContent,
                                fillSelectedAspectFrame
                            )
                        }
                        container.setFaceDetectionOverlay(
                            bounds = remoteFaceBoxBounds,
                            visible = firebaseCameraMode != "portrait" && remoteFaceBoxVisible
                        )
                        container.onVideoRectChanged = { rect ->
                            val frameLeft = previewFrameRect?.left ?: 0f
                            val frameTop = previewFrameRect?.top ?: 0f
                            previewOverlayRect = Rect(
                                frameLeft + rect.left,
                                frameTop + rect.top,
                                frameLeft + rect.right,
                                frameTop + rect.bottom
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                x = (previewFrameRect?.left ?: 0f).roundToInt(),
                                y = (previewFrameRect?.top ?: 0f).roundToInt()
                            )
                        }
                        .size(
                            width = with(density) { (previewFrameRect?.width ?: boxMaxWidthPx).toDp() },
                            height = with(density) { (previewFrameRect?.height ?: boxMaxHeightPx).toDp() }
                        )
                )

                if (shutterFlashAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = shutterFlashAlpha))
                    )
                }

                BoomerangCaptureEffect(
                    visible = boomerangCaptureEffectVisible,
                    modifier = Modifier.align(Alignment.Center)
                )

                if (
                    firebaseLensFacing == "front" &&
                    exposureUiState.supported &&
                    exposureUiState.frontPreviewOverlay.alpha > 0f
                ) {
                    val previewRect =
                        activePreviewRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                    PreviewExposureOverlay(
                        state = exposureUiState.frontPreviewOverlay,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    previewRect.left.roundToInt(),
                                    previewRect.top.roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { previewRect.width.toDp() },
                                height = with(density) { previewRect.height.toDp() }
                            )
                    )
                }

                if (isVideoStalled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF111111).copy(alpha = 0.34f))
                    )

                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(controllerStatusUiState.dotColor)
                            )
                            Text(
                                text = stalledVideoText,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Preview will resume when connection improves",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (firebaseGridEnabled) {
                    val previewRect =
                        activePreviewRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    previewRect.left.roundToInt(),
                                    previewRect.top.roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { previewRect.width.toDp() },
                                height = with(density) { previewRect.height.toDp() }
                            )
                    ) {
                        CameraGridOverlay(modifier = Modifier.fillMaxSize())
                    }
                }

                if (firebaseCameraMode == "portrait") {
                    val previewRect =
                        activePreviewRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                    PortraitPreviewOverlay(
                        effectKey = firebasePortraitEffect,
                        strength = firebasePortraitStrength,
                        status = firebasePortraitStatus,
                        faceLeft = firebasePortraitFaceLeft,
                        faceTop = firebasePortraitFaceTop,
                        faceRight = firebasePortraitFaceRight,
                        faceBottom = firebasePortraitFaceBottom,
                        faceBoxes = firebaseFaceBoxes,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    previewRect.left.roundToInt(),
                                    previewRect.top.roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { previewRect.width.toDp() },
                                height = with(density) { previewRect.height.toDp() }
                            )
                    )
                }

                focusPoint?.let { rawPoint ->
                    val previewRect =
                        activePreviewRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                    val point = rawPoint.clampOffsetTo(previewRect)
                    val localPoint = Offset(
                        x = point.x - previewRect.left,
                        y = point.y - previewRect.top
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    previewRect.left.roundToInt(),
                                    previewRect.top.roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { previewRect.width.toDp() },
                                height = with(density) { previewRect.height.toDp() }
                            )
                    ) {
                        SharedFocusReticleSamsung(
                            point = localPoint,
                            success = focusSucceeded,
                            showExposureHandle = exposureUiState.supported,
                            isLocked = focusLocked || firebaseFocusLockEnabled,
                            exposureProgress = exposureUiState.progress,
                            onToggleLock = {
                                sendFocusRequest(
                                    tapOffset = point,
                                    previewRect = previewRect,
                                    lockFocus = !(focusLocked || firebaseFocusLockEnabled)
                                )
                            },
                            onExposureProgressChange = if (exposureUiState.supported) {
                                { progress -> updateExposureFromProgress(progress) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControllerTopActionBar(
                    isEndingSession = isEndingSession,
                    onEndSession = {
                        controllerCoordinator.endSession(
                            isEndingSession = isEndingSession,
                            setIsEndingSession = { isEndingSession = it },
                            performCleanup = { releaseControllerPreview() },
                            sessionVersion = firebaseSessionVersion
                        )
                    }
                )
            }

            CameraToolRail(
                state = controllerToolRailUiState,
                actions = controllerToolRailActions,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 88.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControllerStatusOverlay(
                    state = controllerStatusUiState
                )
                SceneDetectionChip(state = firebaseSceneDetection)
            }

            val compactBottomControlsActive =
                showZoomRing ||
                    (exposureUiState.visible && exposureUiState.supported) ||
                    (showPortraitControls && firebaseCameraMode == "portrait") ||
                    (videoRecordingInProgress && firebaseCameraMode == "video")
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (compactBottomControlsActive) 6.dp else 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactBottomControlsActive) 8.dp else 12.dp)
            ) {
                ControllerBottomControls(
                    state = controllerBottomControlsUiState,
                    actions = ControllerBottomControlsActions(
                        onZoomBarValueChange = { newValue ->
                            isZoomDragging = true
                            zoomUiValue = newValue
                            sendZoomUpdate(newValue)
                        },
                        onZoomBarFinished = {
                            isZoomDragging = false
                            sendZoomUpdate(zoomUiValue, force = true)
                            showZoomRing = false
                        },
                        onZoomPresetClick = { selectedZoom ->
                            zoomUiValue = selectedZoom
                            isZoomDragging = false
                            sendZoomUpdate(selectedZoom, force = true)
                            showZoomRing = false
                        },
                        onZoomPresetLongPress = { showZoomRing = true },
                        onExposureProgressChange = controllerExposureUiActions.onProgressChange,
                        onExposureDismiss = controllerExposureUiActions.onDismiss,
                        onExposureReset = controllerExposureUiActions.onReset,
                        onPortraitControlsClick = {
                            if (firebaseCameraMode == "portrait") {
                                showPortraitControls = !showPortraitControls
                            }
                        },
                        onPortraitStrengthSelected = { strength ->
                            updatePortraitStrength(strength)
                        },
                        onPortraitEffectSelected = { effect ->
                            updatePortraitEffect(effect)
                        },
                        onVideoHdrClick = controllerToolRailActions.onVideoHdrClick,
                        onLensClick = controllerToolRailActions.onLensClick,
                        onVideoPauseToggle = {
                            triggerCaptureRequest(
                                if (videoRecordingPaused) "video_resume" else "video_pause"
                            )
                        },
                        onVideoStop = {
                            triggerCaptureRequest("video_stop")
                        },
                        onShutterPress = shutter@{ _ ->
                            if (firebaseCameraMode != "video" && captureMode == "boomerang") {
                                if (tryAwaitRelease()) {
                                    triggerCaptureRequest("boomerang")
                                }
                                return@shutter
                            }

                            var burstStarted = false
                            val startBurstJob = if (firebaseCameraMode == "video") {
                                null
                            } else {
                                scope.launch {
                                    delay(350)
                                    burstStarted = true
                                    startBurstCapture()
                                }
                            }

                            val released = tryAwaitRelease()
                            startBurstJob?.cancel()

                            if (burstStarted || isBurstCapturing) {
                                stopBurstCapture()
                            } else if (released) {
                                triggerCaptureRequest()
                            }
                        }
                    )
                )
                if (!compactBottomControlsActive) {
                    ControllerCameraModeStrip(
                        selectedMode = firebaseCameraMode,
                        onModeSelected = { mode -> updateCameraMode(mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerCameraModeStrip(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControllerCameraModeItem(
            label = "VIDEO",
            mode = "video",
            selected = selectedMode == "video",
            onModeSelected = onModeSelected
        )
        ControllerCameraModeItem(
            label = "PORTRAIT",
            mode = "portrait",
            selected = selectedMode == "portrait",
            onModeSelected = onModeSelected
        )
        ControllerCameraModeItem(
            label = "PHOTO",
            mode = "photo",
            selected = selectedMode == "photo",
            onModeSelected = onModeSelected
        )
    }
}

@Composable
private fun BoomerangCaptureEffect(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "boomerang_capture_effect_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.82f,
        label = "boomerang_capture_effect_scale"
    )

    if (alpha <= 0.01f) return

    Column(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "∞",
                color = Color(0xFFFFD54F),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Boomerang",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ControllerCameraModeItem(
    label: String,
    mode: String,
    selected: Boolean,
    onModeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(enabled = !selected) { onModeSelected(mode) }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.54f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .size(width = if (selected) 22.dp else 4.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) {
                        Color(0xFFFFD54F)
                    } else {
                        Color.Transparent
                    }
                )
        )
    }
}

@Composable
private fun FocusReticleSamsung(
    point: Offset,
    success: Boolean?,
    showExposureHandle: Boolean,
    isLocked: Boolean,
    exposureProgress: Float,
    onToggleLock: () -> Unit,
    onExposureProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "controller_focus_reticle_samsung_scale"
    )
    val density = LocalDensity.current
    val ringColor = when {
        isLocked -> Color(0xFFFFC400)
        success == true -> Color(0xFFFFD54F)
        success == false -> Color.White.copy(alpha = 0.72f)
        else -> Color.White
    }

    Box(modifier = modifier) {
        val reticleRadiusPx = with(density) { 25.dp.toPx() * scale }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor,
                radius = reticleRadiusPx,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (point.x - 16.dp.toPx()).roundToInt(),
                        y = (point.y - reticleRadiusPx - 16.dp.toPx()).roundToInt()
                    )
                }
                .size(32.dp)
                .pointerInput(isLocked) {
                    detectTapGestures(onTap = { onToggleLock() })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (isLocked) ringColor else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (showExposureHandle) {
            FocusExposureHandleSamsung(
                center = point,
                ringRadiusPx = reticleRadiusPx,
                isLocked = isLocked,
                progress = exposureProgress,
                onProgressChange = onExposureProgressChange
            )
        }
    }
}

@Composable
private fun FocusExposureHandleSamsung(
    center: Offset,
    ringRadiusPx: Float,
    isLocked: Boolean,
    progress: Float,
    onProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val controlWidth = 42.dp
    val controlHeight = 14.dp
    val trackWidth = 32.dp
    var dragProgress by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress.coerceIn(0f, 1f)
        }
    }

    val controlWidthPx = with(density) { controlWidth.toPx() }
    val trackWidthPx = with(density) { trackWidth.toPx() }
    val trackStartX = (controlWidthPx - trackWidthPx) / 2f

    fun progressFromTouchX(x: Float): Float =
        ((x - trackStartX) / trackWidthPx).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (center.x - (controlWidthPx / 2f)).roundToInt(),
                        y = (center.y + ringRadiusPx + 2.dp.toPx()).roundToInt()
                    )
                }
                .width(controlWidth)
                .height(controlHeight)
                .pointerInput(onProgressChange) {
                    if (onProgressChange == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val nextProgress = progressFromTouchX(offset.x)
                            dragProgress = nextProgress
                            onProgressChange(nextProgress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nextProgress = progressFromTouchX(change.position.x)
                            dragProgress = nextProgress
                            onProgressChange(nextProgress)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(width = trackWidth, height = controlHeight)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val halfLine = trackWidthPx / 2f
                val lineGap = 5.dp.toPx()
                val iconCenterX =
                    (centerX - halfLine + ((halfLine * 2f) * dragProgress.coerceIn(0f, 1f)))
                        .coerceIn(4.5.dp.toPx(), size.width - 4.5.dp.toPx())
                val accentColor = if (isLocked) Color(0xFFFFC400) else Color(0xFFFFD54F)

                drawLine(
                    color = Color.White.copy(alpha = 0.68f),
                    start = Offset(centerX - halfLine, centerY),
                    end = Offset(iconCenterX - lineGap, centerY),
                    strokeWidth = 1.1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.68f),
                    start = Offset(iconCenterX + lineGap, centerY),
                    end = Offset(centerX + halfLine, centerY),
                    strokeWidth = 1.1.dp.toPx()
                )
                drawCircle(
                    color = accentColor,
                    radius = 2.3.dp.toPx(),
                    center = Offset(iconCenterX, centerY)
                )

                val rayStart = 4.6.dp.toPx()
                val rayLength = 2.8.dp.toPx()
                val rayStroke = 0.8.dp.toPx()
                repeat(8) { index ->
                    val angle = (index * 45f) * (Math.PI.toFloat() / 180f)
                    val dx = kotlin.math.cos(angle)
                    val dy = kotlin.math.sin(angle)
                    drawLine(
                        color = accentColor,
                        start = Offset(iconCenterX + (dx * rayStart), centerY + (dy * rayStart)),
                        end = Offset(
                            iconCenterX + (dx * (rayStart + rayLength)),
                            centerY + (dy * (rayStart + rayLength))
                        ),
                        strokeWidth = rayStroke
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusReticle(
    point: Offset,
    success: Boolean?,
    showExposureHandle: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "controller_focus_reticle_scale"
    )
    val ringColor = when {
        isLocked -> Color(0xFFFFC400)
        success == true -> Color(0xFFFFD54F)
        success == false -> Color.White.copy(alpha = 0.72f)
        else -> Color.White
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleRadius = 25.dp.toPx() * scale
            drawCircle(
                color = ringColor,
                radius = reticleRadius,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

            if (showExposureHandle) {
                val handleY = point.y + reticleRadius + 16.dp.toPx()
                val lineHalf = 14.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.84f),
                    start = Offset(point.x - lineHalf, handleY),
                    end = Offset(point.x + lineHalf, handleY),
                    strokeWidth = 1.6.dp.toPx()
                )
            }
        }

        if (isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (point.x - 8.dp.toPx()).roundToInt(),
                            y = (point.y - 38.dp.toPx()).roundToInt()
                        )
                    }
                    .size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (point.x - 18.dp.toPx()).roundToInt(),
                        y = (point.y - 44.dp.toPx()).roundToInt()
                    )
                }
                .size(36.dp)
                .pointerInput(isLocked) {
                    detectTapGestures(onTap = { onToggleLock() })
                }
        )

        if (showExposureHandle) {
            Row(
                modifier = Modifier.offset {
                    IntOffset(
                        x = (point.x - 16.dp.toPx()).roundToInt(),
                        y = (point.y + 26.dp.toPx()).roundToInt()
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "-",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = if (isLocked) ringColor else Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "+",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun ExposureSliderOverlay(
    progress: Float,
    neutralProgress: Float,
    modifier: Modifier = Modifier,
    onProgressChange: (Float) -> Unit
) {
    var dragProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress.coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .pointerInput(progress) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragProgress = progress.coerceIn(0f, 1f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val nextProgress =
                            (dragProgress + (dragAmount / size.height.toFloat())).coerceIn(0f, 1f)
                        dragProgress = nextProgress
                        onProgressChange(nextProgress)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 8.dp)
        ) {
            val trackX = size.width / 2f
            val trackTop = 10.dp.toPx()
            val trackBottom = size.height - 10.dp.toPx()
            val trackHeight = trackBottom - trackTop
            val clampedNeutralProgress = neutralProgress.coerceIn(0f, 1f)
            val thumbY = trackTop + (trackHeight * dragProgress.coerceIn(0f, 1f))
            val neutralY = trackTop + (trackHeight * clampedNeutralProgress)

            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(trackX, trackTop),
                end = Offset(trackX, trackBottom),
                strokeWidth = 1.4.dp.toPx()
            )
            drawLine(
                color = Color(0xFFFFC400),
                start = Offset(trackX, minOf(thumbY, neutralY)),
                end = Offset(trackX, maxOf(thumbY, neutralY)),
                strokeWidth = 1.8.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.48f),
                start = Offset(trackX - 4.dp.toPx(), neutralY),
                end = Offset(trackX + 4.dp.toPx(), neutralY),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = Color(0xFFFFC400),
                radius = 5.dp.toPx(),
                center = Offset(trackX, thumbY)
            )
        }
    }
}

@Composable
private fun FocusExposureConnector(
    focusPoint: Offset,
    sliderTopLeft: Offset,
    sliderSize: androidx.compose.ui.geometry.Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sliderOnRight = sliderTopLeft.x > focusPoint.x
        val reticleHalf = 34.dp.toPx()
        val startX = if (sliderOnRight) focusPoint.x + reticleHalf else focusPoint.x - reticleHalf
        val endX = if (sliderOnRight) sliderTopLeft.x else sliderTopLeft.x + sliderSize.width
        val connectorY = focusPoint.y.coerceIn(
            sliderTopLeft.y + 18.dp.toPx(),
            sliderTopLeft.y + sliderSize.height - 18.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(startX, connectorY),
            end = Offset(endX, connectorY),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

private const val CONTROLLER_ZOOM_BAR_MAX = 30.0


