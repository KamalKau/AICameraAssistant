package com.example.aicameraassistant

import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
    val firebaseFlashSupported = remoteUiState.flashSupported
    val firebaseGridEnabled = remoteUiState.gridEnabled
    val firebaseExposureMinIndex = remoteUiState.exposureMinIndex
    val firebaseExposureMaxIndex = remoteUiState.exposureMaxIndex
    val firebaseExposureIndex = remoteUiState.exposureIndex
    val firebaseAnswer = remoteUiState.answerSdp
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
    var previewOverlayRect by screenViewModel::previewOverlayRect
    var shutterFlashAlpha by screenViewModel::shutterFlashAlpha
    var shutterPressed by screenViewModel::shutterPressed
    var captureRequestSequence by screenViewModel::captureRequestSequence
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

    val controllerDisplayWidth = normalizedRemoteFrameWidth
    val controllerDisplayHeight = normalizedRemoteFrameHeight
    val minZoom = firebaseMinZoom.coerceAtLeast(1.0)
    val maxZoom = firebaseMaxZoom.coerceAtLeast(minZoom)
    val exposureSupported = firebaseExposureMinIndex != firebaseExposureMaxIndex
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
        buildControllerCommonZoomOptions(minZoom, maxZoom)
    }
    val flashModes = listOf("off", "auto", "on")
    val shutterScale by animateFloatAsState(
        targetValue = if (shutterPressed || isBurstCapturing) 0.92f else 1f,
        label = "controller_shutter_scale"
    )
    val shutterCoreScale by animateFloatAsState(
        targetValue = if (isBurstCapturing) 0.72f else 1f,
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
    val controllerToolRailUiState = buildCameraToolRailUiState(
        flashSupported = firebaseFlashSupported,
        flashMode = firebaseFlashMode,
        lensFacing = firebaseLensFacing,
        gridEnabled = firebaseGridEnabled
    )
    val controllerToolRailActions = CameraToolRailActions(
        onFlashClick = {
            controllerCoordinator.updateFlashMode(firebaseFlashMode, firebaseFlashSupported)
        },
        onLensClick = {
            controllerCoordinator.switchLens(firebaseLensFacing)
        },
        onGridClick = {
            controllerCoordinator.updateGridEnabled(firebaseGridEnabled)
        }
    )
    val controllerBottomControlsUiState = buildControllerBottomControlsUiState(
        roomCode = roomCode,
        showZoomRing = showZoomRing,
        zoomUiValue = zoomUiValue,
        minZoom = minZoom.toFloat(),
        maxZoom = maxZoom.toFloat(),
        commonZoomOptions = commonZoomOptions,
        isBurstCapturing = isBurstCapturing,
        burstCaptureCount = burstCaptureCount,
        shutterScale = shutterScale,
        shutterCoreScale = shutterCoreScale
    )
    DisposableEffect(Unit) {
        onDispose {
            shutterSound.release()
        }
    }

    fun releaseControllerPreview() {
        remoteTrack?.removeSink(previewContainerRef?.renderer)
        previewContainerRef?.renderer?.release()
        previewContainerRef = null
        remoteTrack = null
    }

    fun sendZoomUpdate(zoom: Float, force: Boolean = false) {
        controllerCoordinator.sendZoomUpdate(
            zoom = zoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
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

    fun triggerCaptureRequest() {
        controllerCoordinator.triggerCaptureRequest(
            nextRequestId = nextCaptureRequestId(captureRequestSequence),
            setCaptureRequestSequence = { captureRequestSequence = it },
            setShutterPressed = { shutterPressed = it },
            setShutterFlashAlpha = { shutterFlashAlpha = it }
        )
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

    DisposableEffect(roomCode) {
        val registration = repository.listenToCameraIceCandidates(roomCode) { candidate ->
            scope.launch(Dispatchers.Main) {
                val pc = WebRtcSessionManager.controllerPeerConnection
                if (isRemoteDescriptionSet && pc != null) {
                    Log.d("WEBRTC_LOG", "Controller applying camera candidate immediately")
                    pc.addIceCandidate(candidate)
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

    DisposableEffect(Unit) {
        onDispose {
            remoteTrack?.removeSink(previewContainerRef?.renderer)
            previewContainerRef?.renderer?.release()
            WebRtcSessionManager.clearConnections()
        }
    }

    LaunchedEffect(roomStatus) {
        if (roomStatus == "connected") {
            hasSeenConnectedState = true
        }

        if (roomStatus == "ended") {
            Toast.makeText(context, "Session ended by camera", Toast.LENGTH_SHORT).show()
            controllerCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                performCleanup = { releaseControllerPreview() },
                exitScreen = true
            )
            return@LaunchedEffect
        }

        if (hasSeenConnectedState && roomStatus == "waiting") {
            controllerCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                performCleanup = { releaseControllerPreview() },
                exitScreen = true
            )
            return@LaunchedEffect
        }

        if (roomStatus == "connected" && !offerCreated) {
            createSharedOffer(
                context = context,
                roomCode = roomCode,
                repository = repository,
                onRemoteTrackReady = { track ->
                    scope.launch(Dispatchers.Main) {
                        Log.d("WEBRTC_LOG", "Controller received remote track")
                        remoteTrack = track
                        previewContainerRef?.renderer?.let { renderer ->
                            track.setEnabled(true)
                            runCatching { track.removeSink(renderer) }
                            track.addSink(renderer)
                        }
                    }
                }
            )
            offerCreated = true
        }
    }

    LaunchedEffect(firebaseAnswer) {
        val answer = firebaseAnswer ?: return@LaunchedEffect
        val pc = WebRtcSessionManager.controllerPeerConnection ?: return@LaunchedEffect

        if (pc.remoteDescription == null) {
            Log.d("WEBRTC_LOG", "Controller setting remote description (Answer)")
            pc.setRemoteDescription(
                WebRtcSessionManager.sessionDescriptionObserver(
                    onSetSuccess = {
                        scope.launch(Dispatchers.Main) {
                            Log.d(
                                "WEBRTC_LOG",
                                "Controller remote description set, applying ${pendingCandidates.size} candidates"
                            )
                            isRemoteDescriptionSet = true
                            pendingCandidates.forEach { pc.addIceCandidate(it) }
                            pendingCandidates.clear()
                        }
                    }
                ),
                SessionDescription(SessionDescription.Type.ANSWER, answer)
            )
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
        val renderer = previewContainerRef?.renderer
        val track = remoteTrack
        if (renderer == null || track == null) {
            onDispose { }
        } else {
            Log.d("WEBRTC_LOG", "Controller rendering remote track")
            track.setEnabled(true)
            track.removeSink(renderer)
            track.addSink(renderer)

            onDispose {
                runCatching { track.removeSink(renderer) }
            }
        }
    }

    LaunchedEffect(firebaseZoomLevel, minZoom, maxZoom, isZoomDragging) {
        if (!isZoomDragging) {
            zoomUiValue = firebaseZoomLevel.toFloat().coerceIn(minZoom.toFloat(), maxZoom.toFloat())
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (roomStatus) {
                    "denied" -> "Request Denied"
                    "request_received" -> "Waiting for approval..."
                    else -> "Connecting..."
                },
                style = MaterialTheme.typography.headlineMedium,
                color = if (roomStatus == "denied") Color.Red else Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Room Code: $roomCode",
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (hasSeenConnectedState || roomStatus == "request_received") {
                        controllerCoordinator.endSession(
                            isEndingSession = isEndingSession,
                            setIsEndingSession = { isEndingSession = it },
                            performCleanup = { releaseControllerPreview() }
                        )
                    } else {
                        onBack()
                    }
                },
                enabled = !isEndingSession,
                colors = if (hasSeenConnectedState || roomStatus == "request_received") {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    if (hasSeenConnectedState || roomStatus == "request_received") {
                        if (isEndingSession) "Ending..." else "End Session"
                    } else {
                        "Go Back"
                    }
                )
            }
        }
    } else {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val boxMaxWidthPx = with(density) { maxWidth.toPx() }
        val boxMaxHeightPx = with(density) { maxHeight.toPx() }
        val previewContentRect =
            remember(boxMaxWidthPx, boxMaxHeightPx, controllerDisplayWidth, controllerDisplayHeight) {
                fittedPreviewRect(
                    containerWidth = boxMaxWidthPx,
                    containerHeight = boxMaxHeightPx,
                    contentWidth = controllerDisplayWidth.toFloat(),
                    contentHeight = controllerDisplayHeight.toFloat()
                )
            }
        val activePreviewRect = previewOverlayRect ?: previewContentRect

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
                                    maxZoom.toFloat()
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
                                                shouldRotatePreviewContent
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
                                    shouldRotatePreviewContent
                                )
                            }
                            remoteTrack?.let { track ->
                                track.setEnabled(true)
                                runCatching { track.removeSink(renderer) }
                                track.addSink(renderer)
                            }
                            container.onVideoRectChanged = { rect ->
                                previewOverlayRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
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
                                shouldRotatePreviewContent
                            )
                        }
                        remoteTrack?.let { track ->
                            track.setEnabled(true)
                            runCatching { track.removeSink(renderer) }
                            track.addSink(renderer)
                        }
                        container.onVideoRectChanged = { rect ->
                            previewOverlayRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (shutterFlashAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = shutterFlashAlpha))
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

                focusPoint?.let { rawPoint ->
                    val previewRect =
                        activePreviewRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                    val focusUiBounds = with(density) {
                        focusUiBounds(
                            previewRect = previewRect,
                            topInsetPx = 12.dp.toPx(),
                            rightInsetPx = 88.dp.toPx(),
                            edgePaddingPx = 12.dp.toPx(),
                            reticleRadiusPx = 34.dp.toPx()
                        )
                    }
                    val point = rawPoint.clampOffsetTo(previewRect)
                    val localPoint = Offset(
                        x = point.x - previewRect.left,
                        y = point.y - previewRect.top
                    )
                    val localFocusUiBounds = Rect(
                        left = focusUiBounds.left - previewRect.left,
                        top = focusUiBounds.top - previewRect.top,
                        right = focusUiBounds.right - previewRect.left,
                        bottom = focusUiBounds.bottom - previewRect.top
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
                            showExposureHandle = exposureSupported,
                            isLocked = focusLocked || firebaseFocusLockEnabled,
                            exposureProgress = (
                                (firebaseExposureMaxIndex - firebaseExposureIndex).toFloat() /
                                    (firebaseExposureMaxIndex - firebaseExposureMinIndex).toFloat()
                                        .coerceAtLeast(1f)
                                ),
                            onToggleLock = {
                                sendFocusRequest(
                                    tapOffset = point,
                                    previewRect = previewRect,
                                    lockFocus = !(focusLocked || firebaseFocusLockEnabled)
                                )
                            },
                            onExposureProgressChange = if (exposureSupported) {
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
                            performCleanup = { releaseControllerPreview() }
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ControllerStatusOverlay(
                    state = controllerStatusUiState
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        onShutterPress = { _ ->
                        var burstStarted = false
                        val startBurstJob = scope.launch {
                            delay(350)
                            burstStarted = true
                            startBurstCapture()
                        }

                        val released = tryAwaitRelease()
                        startBurstJob.cancel()

                        if (burstStarted || isBurstCapturing) {
                            stopBurstCapture()
                        } else if (released) {
                            triggerCaptureRequest()
                        }
                    })
                )
            }
        }
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
        true -> Color(0xFFFFD54F)
        false -> Color.White.copy(alpha = 0.72f)
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
        true -> Color(0xFFFFD54F)
        false -> Color.White.copy(alpha = 0.72f)
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
                    text = "−",
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


