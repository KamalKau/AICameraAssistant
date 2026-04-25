package com.example.aicameraassistant

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val firebaseGridEnabled by repository.getGridEnabled(roomCode).collectAsState(initial = false)
    val firebaseExposureMinIndex by repository.getExposureMinIndex(roomCode).collectAsState(initial = 0)
    val firebaseExposureMaxIndex by repository.getExposureMaxIndex(roomCode).collectAsState(initial = 0)
    val firebaseExposureIndex by repository.getExposureIndex(roomCode).collectAsState(initial = 0)
    val firebaseAnswer by repository.getAnswerSdp(roomCode).collectAsState(initial = null)
    val cameraPreviewWidth by repository.getPreviewWidth(roomCode).collectAsState(initial = 0)
    val cameraPreviewHeight by repository.getPreviewHeight(roomCode).collectAsState(initial = 0)
    val firebaseFocusRequestId by repository.getFocusRequestId(roomCode).collectAsState(initial = 0L)
    val firebaseFocusLockEnabled by repository.getFocusLockEnabled(roomCode).collectAsState(initial = false)

    var previewContainerRef by remember { mutableStateOf<ControllerPreviewContainer?>(null) }
    var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var offerCreated by remember(roomCode) { mutableStateOf(false) }
    var hasSeenConnectedState by remember(roomCode) { mutableStateOf(false) }
    var isEndingSession by remember(roomCode) { mutableStateOf(false) }
    var zoomUiValue by remember(roomCode) { mutableStateOf(1f) }
    var isZoomDragging by remember(roomCode) { mutableStateOf(false) }
    var showZoomRing by remember(roomCode) { mutableStateOf(false) }
    var lastSentZoom by remember(roomCode) { mutableStateOf(Double.NaN) }
    var focusPoint by remember(roomCode) { mutableStateOf<Offset?>(null) }
    var focusSucceeded by remember(roomCode) { mutableStateOf<Boolean?>(null) }
    var focusLocked by remember(roomCode) { mutableStateOf(false) }
    var focusUiToken by remember(roomCode) { mutableIntStateOf(0) }
    var previewOverlayRect by remember(roomCode) { mutableStateOf<Rect?>(null) }

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
    val commonZoomOptions = remember(minZoom, maxZoom) {
        listOf(0.6f, 1f, 2f, 3f)
            .filter { it.toDouble() in minZoom..maxZoom }
            .ifEmpty { listOf(minZoom.toFloat()) }
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
                .onSuccess {
                    shutdownControllerSession(exitScreen = true)
                }
                .onFailure {
                    isEndingSession = false
                    Log.e("SESSION_END", "Failed to end controller session", it)
                    Toast.makeText(context, "Unable to end session", Toast.LENGTH_SHORT).show()
                }
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

    fun sendFocusRequest(tapOffset: Offset, previewRect: Rect, lockFocus: Boolean = false) {
        if (!previewRect.contains(tapOffset)) return

        val clampedPoint = tapOffset.clampTo(previewRect)
        val previewWidth = previewRect.width
        val previewHeight = previewRect.height
        if (previewWidth <= 0f || previewHeight <= 0f) return

        val normalizedX = (clampedPoint.x - previewRect.left) / previewWidth
        val normalizedY = (clampedPoint.y - previewRect.top) / previewHeight

        focusPoint = clampedPoint
        focusSucceeded = null
        focusLocked = lockFocus
        focusUiToken++

        scope.launch {
            repository.updateFocusRequest(
                roomCode = roomCode,
                normalizedX = normalizedX.toDouble(),
                normalizedY = normalizedY.toDouble(),
                requestId = firebaseFocusRequestId + 1L,
                lockEnabled = lockFocus
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
                        calculateFocusUiBounds(
                            previewRect = previewRect,
                            topInsetPx = 12.dp.toPx(),
                            rightInsetPx = 88.dp.toPx(),
                            edgePaddingPx = 12.dp.toPx(),
                            reticleRadiusPx = 34.dp.toPx()
                        )
                    }
                    val point = rawPoint.clampTo(previewRect)
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
                        FocusReticleSamsung(
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { endControllerSession() },
                        enabled = !isEndingSession,
                        modifier = Modifier.background(
                            Color(0xFFE53935),
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = if (isEndingSession) "Ending session" else "End session",
                            tint = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    isActive = firebaseGridEnabled,
                    onClick = {
                        scope.launch {
                            repository.updateGridEnabled(roomCode, !firebaseGridEnabled)
                        }
                    }
                )
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
                }            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showZoomRing) {
                    AndroidZoomBar(
                        value = zoomUiValue.coerceIn(minZoom.toFloat(), maxZoom.toFloat()),
                        minZoom = minZoom.toFloat(),
                        maxZoom = maxZoom.toFloat(),
                        onValueChange = { newValue ->
                            isZoomDragging = true
                            zoomUiValue = newValue
                            sendZoomUpdate(newValue)
                        },
                        onValueChangeFinished = {
                            isZoomDragging = false
                            sendZoomUpdate(zoomUiValue, force = true)
                            showZoomRing = false
                        }
                    )
                }

                ZoomPresetSelector(
                    options = commonZoomOptions,
                    currentValue = zoomUiValue.coerceIn(minZoom.toFloat(), maxZoom.toFloat()),
                    onOptionClick = { selectedZoom ->
                        zoomUiValue = selectedZoom
                        isZoomDragging = false
                        sendZoomUpdate(selectedZoom, force = true)
                        showZoomRing = false
                    },
                    onLongPress = { showZoomRing = true }
                )

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomPresetSelector(
    options: List<Float>,
    currentValue: Float,
    modifier: Modifier = Modifier,
    onOptionClick: (Float) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = abs(currentValue - option) < 0.16f
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { onOptionClick(option) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${formatZoomLabel(option)}x",
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AndroidZoomBar(
    value: Float,
    minZoom: Float,
    maxZoom: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val clampedValue = value.coerceIn(minZoom, maxZoom)
    val normalizedValue =
        ((clampedValue - minZoom) / (maxZoom - minZoom).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    val startAngle = 135f
    val sweepAngle = 270f

    fun updateFromPoint(point: Offset, sizePx: Float) {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val rawAngle = ((Math.toDegrees(
            atan2(
                (point.y - center.y).toDouble(),
                (point.x - center.x).toDouble()
            )
        ) + 360.0) % 360.0).toFloat()
        val extendedAngle = if (rawAngle < startAngle) rawAngle + 360f else rawAngle
        val normalized =
            ((extendedAngle.coerceIn(startAngle, startAngle + sweepAngle) - startAngle) /
                sweepAngle)
                .coerceIn(0f, 1f)
        onValueChange(minZoom + ((maxZoom - minZoom) * normalized))
    }

    Box(
        modifier = modifier
            .size(196.dp)
            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            .border(0.8.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .pointerInput(minZoom, maxZoom) {
                detectDragGestures(
                    onDragStart = { point -> updateFromPoint(point, size.width.toFloat()) },
                    onDragEnd = {
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        onValueChangeFinished()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromPoint(
                            change.position,
                            size.width.toFloat()
                        )
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringInset = 24.dp.toPx()
            val ringStroke = 8.dp.toPx()
            val ringSize = androidx.compose.ui.geometry.Size(
                width = size.width - (ringInset * 2f),
                height = size.height - (ringInset * 2f)
            )
            val ringTopLeft = Offset(ringInset, ringInset)
            val ringRadius = ringSize.width / 2f

            drawArc(
                color = Color.White.copy(alpha = 0.13f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.96f),
                startAngle = startAngle,
                sweepAngle = sweepAngle * normalizedValue,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            val centerPoint = Offset(size.width / 2f, size.height / 2f)
            repeat(19) { index ->
                val tickNormalized = index / 18f
                val angleRadians =
                    Math.toRadians((startAngle + (sweepAngle * tickNormalized)).toDouble())
                val outerRadius = ringRadius + (ringStroke / 2f) + 8.dp.toPx()
                val innerRadius = outerRadius - 6.dp.toPx()
                val outer = Offset(
                    x = centerPoint.x + (cos(angleRadians).toFloat() * outerRadius),
                    y = centerPoint.y + (sin(angleRadians).toFloat() * outerRadius)
                )
                val inner = Offset(
                    x = centerPoint.x + (cos(angleRadians).toFloat() * innerRadius),
                    y = centerPoint.y + (sin(angleRadians).toFloat() * innerRadius)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = inner,
                    end = outer,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val thumbAngleRadians =
                Math.toRadians((startAngle + (sweepAngle * normalizedValue)).toDouble())
            val thumbCenter = Offset(
                x = centerPoint.x + (cos(thumbAngleRadians).toFloat() * ringRadius),
                y = centerPoint.y + (sin(thumbAngleRadians).toFloat() * ringRadius)
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.46f),
                radius = 2.2.dp.toPx(),
                center = thumbCenter
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${formatZoomLabel(clampedValue)}x",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Zoom",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatZoomLabel(minZoom)}x",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${formatZoomLabel(maxZoom)}x",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatZoomLabel(value: Float): String =
    if (value % 1f == 0f) {
        value.roundToInt().toString()
    } else {
        String.format("%.1f", value)
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
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = label,
            color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
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

private fun calculateFocusUiBounds(
    previewRect: Rect,
    topInsetPx: Float,
    rightInsetPx: Float,
    edgePaddingPx: Float,
    reticleRadiusPx: Float
): Rect {
    val left = previewRect.left + edgePaddingPx + reticleRadiusPx
    val top = previewRect.top + topInsetPx + reticleRadiusPx
    val right = previewRect.right - rightInsetPx - edgePaddingPx
    val bottom = previewRect.bottom - edgePaddingPx - reticleRadiusPx

    return if (right > left && bottom > top) {
        Rect(left, top, right, bottom)
    } else {
        previewRect
    }
}

private fun calculateExposureSliderOffset(
    point: Offset,
    previewRect: Rect,
    safeBounds: Rect,
    sliderWidthPx: Float,
    sliderHeightPx: Float,
    reticleHalfPx: Float,
    horizontalGapPx: Float
): IntOffset {
    val rightAnchorX = point.x + reticleHalfPx + horizontalGapPx
    val leftAnchorX = point.x - reticleHalfPx - horizontalGapPx - sliderWidthPx
    val minX = safeBounds.left
    val maxX = safeBounds.right - sliderWidthPx
    val rightFits = rightAnchorX in minX..maxX
    val leftFits = leftAnchorX in minX..maxX
    val desiredX = when {
        point.x < previewRect.center.x && rightFits -> rightAnchorX
        point.x >= previewRect.center.x && leftFits -> leftAnchorX
        point.x < previewRect.center.x && leftFits -> leftAnchorX
        point.x >= previewRect.center.x && rightFits -> rightAnchorX
        else -> {
            val clampedRightX = rightAnchorX.coerceIn(minX, maxX)
            val clampedLeftX = leftAnchorX.coerceIn(minX, maxX)
            val rightDelta = kotlin.math.abs(clampedRightX - rightAnchorX)
            val leftDelta = kotlin.math.abs(clampedLeftX - leftAnchorX)
            if (rightDelta <= leftDelta) clampedRightX else clampedLeftX
        }
    }

    val minY = safeBounds.top
    val maxY = safeBounds.bottom - sliderHeightPx
    val centeredY = point.y - (sliderHeightPx / 2f)
    val attachedTopY = point.y - reticleHalfPx
    val attachedBottomY = point.y + reticleHalfPx - sliderHeightPx
    val desiredY = when {
        centeredY < minY -> attachedTopY.coerceAtLeast(minY)
        centeredY > maxY -> attachedBottomY.coerceAtMost(maxY)
        else -> centeredY
    }

    return IntOffset(
        x = desiredX.coerceIn(minX, maxX).roundToInt(),
        y = desiredY.coerceIn(minY, maxY).roundToInt()
    )
}

