package com.example.aicameraassistant

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SwitchCamera
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
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

    val roomStatus by repository.getRoomStatus(roomCode).collectAsState(initial = "waiting")
    val connectionState by WebRtcSessionManager.controllerConnectionState.collectAsState()
    val firebaseLensFacing by repository.getLensFacing(roomCode).collectAsState(initial = "back")
    val firebaseZoomLevel by repository.getZoomLevel(roomCode).collectAsState(initial = 1.0)
    val firebaseMinZoom by repository.getMinZoom(roomCode).collectAsState(initial = 1.0)
    val firebaseMaxZoom by repository.getMaxZoom(roomCode).collectAsState(initial = 1.0)
    val firebaseFlashMode by repository.getFlashMode(roomCode).collectAsState(initial = "off")
    val firebaseFlashSupported by repository.getFlashSupported(roomCode).collectAsState(initial = false)
    val firebaseExposureMinIndex by repository.getExposureMinIndex(roomCode).collectAsState(initial = 0)
    val firebaseExposureMaxIndex by repository.getExposureMaxIndex(roomCode).collectAsState(initial = 0)
    val firebaseExposureIndex by repository.getExposureIndex(roomCode).collectAsState(initial = 0)
    val firebaseAnswer by repository.getAnswerSdp(roomCode).collectAsState(initial = null)
    val cameraPreviewWidth by repository.getPreviewWidth(roomCode).collectAsState(initial = 0)
    val cameraPreviewHeight by repository.getPreviewHeight(roomCode).collectAsState(initial = 0)
    val firebaseFocusRequestId by repository.getFocusRequestId(roomCode).collectAsState(initial = 0L)

    var previewContainerRef by remember { mutableStateOf<ControllerPreviewContainer?>(null) }
    var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var offerCreated by remember(roomCode) { mutableStateOf(false) }
    var hasSeenConnectedState by remember(roomCode) { mutableStateOf(false) }
    var isEndingSession by remember(roomCode) { mutableStateOf(false) }
    var zoomUiValue by remember(roomCode) { mutableStateOf(1f) }
    var isZoomDragging by remember(roomCode) { mutableStateOf(false) }
    var lastSentZoom by remember(roomCode) { mutableStateOf(Double.NaN) }
    var focusPoint by remember(roomCode) { mutableStateOf<Offset?>(null) }
    var focusSucceeded by remember(roomCode) { mutableStateOf<Boolean?>(null) }
    var focusUiToken by remember(roomCode) { mutableIntStateOf(0) }
    var previewOverlayRect by remember(roomCode) { mutableStateOf<Rect?>(null) }
    var showGridOverlay by remember(roomCode) { mutableStateOf(false) }

    var remoteFrameWidth by remember { mutableIntStateOf(0) }
    var remoteFrameHeight by remember { mutableIntStateOf(0) }
    var remoteFrameRotation by remember { mutableIntStateOf(0) }
    var lastFrameTimestampMs by remember(roomCode) { mutableLongStateOf(0L) }
    var uiNowMs by remember(roomCode) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by remember { mutableStateOf(false) }

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
    val connectionBadgeText = when (connectionState) {
        AppConnectionState.IDLE,
        AppConnectionState.CONNECTING -> "Connecting..."
        AppConnectionState.CONNECTED -> "Connected"
        AppConnectionState.WEAK_NETWORK -> "Weak network"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Disconnected"
    }
    val connectionBadgeColor = when (connectionState) {
        AppConnectionState.CONNECTED -> Color(0xFF4CAF50)
        AppConnectionState.WEAK_NETWORK -> Color(0xFFFFB300)
        AppConnectionState.RETRYING,
        AppConnectionState.CONNECTING,
        AppConnectionState.IDLE -> Color(0xFFFF9800)
        AppConnectionState.DISCONNECTED -> Color(0xFFF44336)
    }
    val connectionWarningText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK -> "Network unstable"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Connection lost"
        else -> null
    }
    val connectionWarningDetailText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK,
        AppConnectionState.RETRYING -> "Video quality may be affected"
        AppConnectionState.DISCONNECTED -> "Unable to reconnect"
        else -> null
    }
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
    val zoomPresets = remember(minZoom, maxZoom) {
        listOf(1.0, 2.0, 3.0, 5.0)
            .filter { it in minZoom..maxZoom }
            .ifEmpty { listOf(minZoom) }
    }
    val flashModes = listOf("off", "auto", "on")

    fun shutdownControllerSession(exitScreen: Boolean) {
        if (isEndingSession) return

        isEndingSession = true
        remoteTrack?.removeSink(previewContainerRef?.renderer)
        previewContainerRef?.renderer?.release()
        previewContainerRef = null
        remoteTrack = null
        WebRtcSessionManager.stopLocalCamera()
        WebRtcSessionManager.clearConnections()

        if (exitScreen) {
            onBack()
        }
    }

    fun endControllerSession() {
        if (isEndingSession) return

        scope.launch {
            runCatching { repository.endSession(roomCode) }
                .onFailure { Log.e("SESSION_END", "Failed to end controller session", it) }
            shutdownControllerSession(exitScreen = true)
        }
    }

    fun sendZoomUpdate(zoom: Float, force: Boolean = false) {
        val clampedZoom = zoom.toDouble().coerceIn(minZoom, maxZoom)
        if (!force && !lastSentZoom.isNaN() && abs(lastSentZoom - clampedZoom) < 0.02) {
            return
        }

        lastSentZoom = clampedZoom
        scope.launch {
            repository.updateZoomLevel(roomCode, clampedZoom)
        }
    }

    fun sendFocusRequest(tapOffset: Offset, previewRect: Rect) {
        if (!previewRect.contains(tapOffset)) return

        val clampedPoint = tapOffset.clampTo(previewRect)
        val previewWidth = previewRect.width
        val previewHeight = previewRect.height
        if (previewWidth <= 0f || previewHeight <= 0f) return

        val normalizedX = (clampedPoint.x - previewRect.left) / previewWidth
        val normalizedY = (clampedPoint.y - previewRect.top) / previewHeight

        focusPoint = clampedPoint
        focusSucceeded = null
        focusUiToken++

        scope.launch {
            repository.updateFocusRequest(
                roomCode = roomCode,
                normalizedX = normalizedX.toDouble(),
                normalizedY = normalizedY.toDouble(),
                requestId = firebaseFocusRequestId + 1L
            )
        }
    }

    fun updateExposureFromProgress(progress: Float) {
        if (!exposureSupported) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetIndex = (
            firebaseExposureMinIndex +
                ((1f - clampedProgress) * (firebaseExposureMaxIndex - firebaseExposureMinIndex))
        ).roundToInt().coerceIn(firebaseExposureMinIndex, firebaseExposureMaxIndex)

        if (targetIndex == firebaseExposureIndex) {
            focusUiToken++
            return
        }

        focusUiToken++
        scope.launch {
            repository.updateExposureIndex(roomCode, targetIndex)
        }
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
            shutdownControllerSession(exitScreen = true)
            return@LaunchedEffect
        }

        if (hasSeenConnectedState && roomStatus == "waiting") {
            shutdownControllerSession(exitScreen = true)
            return@LaunchedEffect
        }

        if (roomStatus == "connected" && !offerCreated) {
            createOffer(
                context = context,
                roomCode = roomCode,
                repository = repository,
                onRemoteTrackReady = { track ->
                    scope.launch(Dispatchers.Main) {
                        Log.d("WEBRTC_LOG", "Controller received remote track")
                        remoteTrack = track
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

    LaunchedEffect(previewContainerRef, remoteTrack) {
        val renderer = previewContainerRef?.renderer ?: return@LaunchedEffect
        val track = remoteTrack ?: return@LaunchedEffect

        Log.d("WEBRTC_LOG", "Controller rendering remote track")
        track.setEnabled(true)
        track.addSink(renderer)
    }

    LaunchedEffect(firebaseZoomLevel, minZoom, maxZoom, isZoomDragging) {
        if (!isZoomDragging) {
            zoomUiValue = firebaseZoomLevel.toFloat().coerceIn(minZoom.toFloat(), maxZoom.toFloat())
        }
    }

    LaunchedEffect(focusUiToken) {
        if (focusUiToken == 0) return@LaunchedEffect
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
                        endControllerSession()
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
                calculateFittedPreviewRect(
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
                        detectTapGestures { offset ->
                            activePreviewRect?.let { previewRect ->
                                sendFocusRequest(
                                    tapOffset = offset,
                                    previewRect = previewRect
                                )
                            }
                        }
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
                        container.onVideoRectChanged = { rect ->
                            previewOverlayRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
                                    .background(connectionBadgeColor)
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

                if (showGridOverlay) {
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
                    val point = rawPoint.clampTo(previewRect)
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
                        FocusReticle(
                            point = localPoint,
                            success = focusSucceeded,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (exposureSupported) {
                            val sliderOffset = with(density) {
                                val sliderWidthPx = 44.dp.toPx()
                                val sliderHeightPx = 168.dp.toPx()
                                val reticleHalfPx = 34.dp.toPx()
                                val horizontalGapPx = 12.dp.toPx()
                                val minX = 12.dp.toPx()
                                val maxX = previewRect.width - sliderWidthPx - 12.dp.toPx()
                                val desiredRightX = localPoint.x + reticleHalfPx + horizontalGapPx
                                val desiredLeftX =
                                    localPoint.x - reticleHalfPx - horizontalGapPx - sliderWidthPx
                                val desiredX = when {
                                    localPoint.x <= previewRect.width / 2f && desiredRightX <= maxX -> desiredRightX
                                    localPoint.x > previewRect.width / 2f && desiredLeftX >= minX -> desiredLeftX
                                    desiredRightX <= maxX -> desiredRightX
                                    else -> desiredLeftX
                                }
                                val desiredY = localPoint.y - (sliderHeightPx / 2f)
                                val minY = 12.dp.toPx()
                                val maxY = previewRect.height - sliderHeightPx - 12.dp.toPx()
                                IntOffset(
                                    x = desiredX.coerceIn(minX, maxX).roundToInt(),
                                    y = desiredY.coerceIn(minY, maxY).roundToInt()
                                )
                            }
                            val exposureProgress =
                                (firebaseExposureMaxIndex - firebaseExposureIndex).toFloat() /
                                    (firebaseExposureMaxIndex - firebaseExposureMinIndex)
                                        .toFloat()
                                        .coerceAtLeast(1f)
                            val neutralExposureProgress =
                                (firebaseExposureMaxIndex - 0.coerceIn(
                                    firebaseExposureMinIndex,
                                    firebaseExposureMaxIndex
                                )).toFloat() /
                                    (firebaseExposureMaxIndex - firebaseExposureMinIndex)
                                        .toFloat()
                                        .coerceAtLeast(1f)

                            ExposureSliderOverlay(
                                progress = exposureProgress,
                                neutralProgress = neutralExposureProgress,
                                modifier = Modifier
                                    .offset { sliderOffset }
                                    .size(width = 44.dp, height = 168.dp),
                                onProgressChange = { progress ->
                                    updateExposureFromProgress(progress)
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { endControllerSession() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Session",
                        tint = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { endControllerSession() },
                        enabled = !isEndingSession,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (isEndingSession) "Ending..." else "End Session")
                    }

                    CameraModeButton(
                        icon = when {
                            !firebaseFlashSupported -> Icons.Default.FlashOff
                            firebaseFlashMode == "auto" -> Icons.Default.FlashAuto
                            firebaseFlashMode == "on" -> Icons.Default.FlashOn
                            else -> Icons.Default.FlashOff
                        },
                        label = when {
                            !firebaseFlashSupported -> "Unsupported"
                            firebaseFlashMode == "auto" -> "Auto"
                            firebaseFlashMode == "on" -> "On"
                            else -> "Off"
                        },
                        enabled = firebaseFlashSupported,
                        onClick = {
                            if (!firebaseFlashSupported) return@CameraModeButton
                            val nextFlashMode = when (firebaseFlashMode) {
                                "off" -> "auto"
                                "auto" -> "on"
                                else -> "off"
                            }
                            scope.launch {
                                repository.updateFlashMode(roomCode, nextFlashMode)
                            }
                        }
                    )

                    CameraModeButton(
                        icon = Icons.Default.SwitchCamera,
                        label = if (firebaseLensFacing == "back") "Rear" else "Front",
                        onClick = {
                            scope.launch {
                                val nextFacing =
                                    if (firebaseLensFacing == "back") "front" else "back"
                                repository.updateLensFacing(roomCode, nextFacing)
                            }
                        }
                    )

                    GridToggleButton(
                        isActive = showGridOverlay,
                        onClick = { showGridOverlay = !showGridOverlay }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 88.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(connectionBadgeColor)
                        )
                        Text(
                            text = connectionBadgeText,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (connectionWarningText != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = connectionWarningText,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            connectionWarningDetailText?.let { detailText ->
                                Text(
                                    text = detailText,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (controllerDisplayWidth > 0 && controllerDisplayHeight > 0) {
                            "Controller frame: $controllerDisplayWidth x $controllerDisplayHeight"
                        } else {
                            "Controller frame: Detecting..."
                        },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (cameraPreviewWidth > 0 && cameraPreviewHeight > 0) {
                            "Camera hardware: $cameraPreviewWidth × $cameraPreviewHeight"
                        } else {
                            "Camera hardware: Detecting..."
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = Color(0xFFFFC83D).copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = String.format(
                                "%.1fx",
                                zoomUiValue.coerceIn(minZoom.toFloat(), maxZoom.toFloat())
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(
                                Color(0xFF111111).copy(alpha = 0.9f),
                                RoundedCornerShape(24.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        zoomPresets.forEach { preset ->
                            ZoomPresetPill(
                                text = "${preset.roundToInt()}x",
                                isSelected = abs(zoomUiValue - preset.toFloat()) < 0.15f,
                                onClick = {
                                    val presetValue = preset.toFloat()
                                    zoomUiValue = presetValue
                                    isZoomDragging = false
                                    sendZoomUpdate(presetValue, force = true)
                                }
                            )
                        }
                    }

                    Slider(
                        value = zoomUiValue.coerceIn(minZoom.toFloat(), maxZoom.toFloat()),
                        onValueChange = { newValue ->
                            isZoomDragging = true
                            zoomUiValue = newValue
                            sendZoomUpdate(newValue)
                        },
                        valueRange = minZoom.toFloat()..maxZoom.toFloat(),
                        onValueChangeFinished = {
                            isZoomDragging = false
                            sendZoomUpdate(zoomUiValue, force = true)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFC83D),
                            activeTrackColor = Color(0xFFFFC83D),
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.width(292.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            scope.launch {
                                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                db.collection("rooms").document(roomCode).update("captureRequest", true)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun ZoomPresetPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isSelected) Color(0xFFFFC83D) else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    Color(0xFFFFE082)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.95f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CameraModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
            )
        }

        Text(
            text = label,
            color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun calculateFittedPreviewRect(
    containerWidth: Float,
    containerHeight: Float,
    contentWidth: Float,
    contentHeight: Float
): Rect? {
    if (containerWidth <= 0f || containerHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) {
        return null
    }

    val scale = minOf(containerWidth / contentWidth, containerHeight / contentHeight)
    val fittedWidth = contentWidth * scale
    val fittedHeight = contentHeight * scale
    val left = (containerWidth - fittedWidth) / 2f
    val top = (containerHeight - fittedHeight) / 2f
    return Rect(left, top, left + fittedWidth, top + fittedHeight)
}

private fun Offset.clampTo(rect: Rect): Offset =
    Offset(
        x = x.coerceIn(rect.left, rect.right),
        y = y.coerceIn(rect.top, rect.bottom)
    )

@Composable
private fun FocusReticle(
    point: Offset,
    success: Boolean?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "controller_focus_reticle_scale"
    )
    val ringColor = when (success) {
        true -> Color(0xFFFFD54F)
        false -> Color.White.copy(alpha = 0.72f)
        null -> Color.White
    }

    Canvas(modifier = modifier) {
        val reticleSize = 68.dp.toPx() * scale
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(point.x - reticleSize / 2f, point.y - reticleSize / 2f),
            size = androidx.compose.ui.geometry.Size(reticleSize, reticleSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        drawCircle(
            color = ringColor,
            radius = 3.dp.toPx(),
            center = point
        )
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
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.42f))
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
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val trackX = size.width / 2f
            val trackTop = 14.dp.toPx()
            val trackBottom = size.height - 14.dp.toPx()
            val trackHeight = trackBottom - trackTop
            val clampedNeutralProgress = neutralProgress.coerceIn(0f, 1f)
            val thumbY = trackTop + (trackHeight * dragProgress.coerceIn(0f, 1f))
            val neutralY = trackTop + (trackHeight * clampedNeutralProgress)

            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(trackX, trackTop),
                end = Offset(trackX, trackBottom),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFFFFD54F),
                start = Offset(trackX, minOf(thumbY, neutralY)),
                end = Offset(trackX, maxOf(thumbY, neutralY)),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.48f),
                start = Offset(trackX - 6.dp.toPx(), neutralY),
                end = Offset(trackX + 6.dp.toPx(), neutralY),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = 6.dp.toPx(),
                center = Offset(trackX, thumbY)
            )
        }

        Icon(
            imageVector = Icons.Default.WbSunny,
            contentDescription = "Exposure",
            tint = Color(0xFFFFD54F),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(14.dp)
        )
    }
}

fun createOffer(
    context: Context,
    roomCode: String,
    repository: FirebaseRoomRepository,
    onRemoteTrackReady: (VideoTrack) -> Unit
) {
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createControllerPeerConnection(
        onIceCandidate = { candidate ->
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch {
                repository.addControllerIceCandidate(roomCode, candidate)
            }
        },
        onRemoteTrack = { videoTrack ->
            onRemoteTrackReady(videoTrack)
        }
    ) ?: return

    pc.createOffer(
        WebRtcSessionManager.sessionDescriptionObserver(
            onCreateSuccess = { desc ->
                pc.setLocalDescription(
                    WebRtcSessionManager.sessionDescriptionObserver(),
                    desc
                )
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch {
                    repository.saveOffer(roomCode, desc.description)
                }
            }
        ),
        MediaConstraints()
    )
}
