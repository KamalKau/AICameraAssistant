package com.example.aicameraassistant

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription

@Composable
fun CameraScreen(
    roomCode: String,
    repository: FirebaseRoomRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val roomStatus by repository.getRoomStatus(roomCode).collectAsState(initial = "waiting")
    val connectionState by WebRtcSessionManager.cameraConnectionState.collectAsState()
    val firebaseLensFacing by repository.getLensFacing(roomCode).collectAsState(initial = "back")
    val firebaseZoomLevel by repository.getZoomLevel(roomCode).collectAsState(initial = 1.0)
    val firebaseFlashMode by repository.getFlashMode(roomCode).collectAsState(initial = "off")
    val firebaseGridEnabled by repository.getGridEnabled(roomCode).collectAsState(initial = false)
    val firebaseCaptureRequest by repository.getCaptureRequest(roomCode).collectAsState(initial = false)
    val firebaseRequestReceived by repository.getRequestReceived(roomCode).collectAsState(initial = false)
    val firebaseControllerApproved by repository.getControllerApproved(roomCode).collectAsState(initial = false)
    val firebaseFocusRequestId by repository.getFocusRequestId(roomCode).collectAsState(initial = 0L)
    val firebaseFocusPointX by repository.getFocusPointX(roomCode).collectAsState(initial = 0.5)
    val firebaseFocusPointY by repository.getFocusPointY(roomCode).collectAsState(initial = 0.5)
    val firebaseExposureIndex by repository.getExposureIndex(roomCode).collectAsState(initial = 0)
    val offerSdp by repository.getOfferSdp(roomCode).collectAsState(initial = null)

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    var resolvedWidth by remember { mutableIntStateOf(0) }
    var resolvedHeight by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val activity = context.findActivity()

    var flashAlpha by remember { mutableFloatStateOf(0f) }
    val isStreaming = roomStatus == "connected"
    var answerCreated by remember { mutableStateOf(false) }
    var hasSeenConnectedState by remember(roomCode) { mutableStateOf(false) }
    var isEndingSession by remember(roomCode) { mutableStateOf(false) }
    var focusPoint by remember(roomCode) { mutableStateOf<Offset?>(null) }
    var focusSucceeded by remember(roomCode) { mutableStateOf<Boolean?>(null) }
    var focusUiToken by remember(roomCode) { mutableIntStateOf(0) }
    var lastAppliedRemoteFocusRequestId by remember(roomCode) { mutableStateOf(0L) }
    var exposureMinIndex by remember(roomCode) { mutableIntStateOf(0) }
    var exposureMaxIndex by remember(roomCode) { mutableIntStateOf(0) }
    var exposureIndex by remember(roomCode) { mutableIntStateOf(0) }

    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by remember { mutableStateOf(false) }
    val sessionIsActive = roomStatus == "connected"
    val hasLedFlash = camera?.cameraInfo?.hasFlashUnit() == true
    val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
    val flashSupported = hasLedFlash || lensFacing == CameraSelector.LENS_FACING_FRONT
    val exposureSupported = exposureMinIndex != exposureMaxIndex
    val captureFlashMode = when {
        lensFacing == CameraSelector.LENS_FACING_FRONT -> ImageCapture.FLASH_MODE_OFF
        firebaseFlashMode == "on" -> ImageCapture.FLASH_MODE_ON
        firebaseFlashMode == "auto" -> ImageCapture.FLASH_MODE_AUTO
        else -> ImageCapture.FLASH_MODE_OFF
    }
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
    val transientWarningText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK -> "Network unstable"
        AppConnectionState.RETRYING -> "Reconnecting..."
        AppConnectionState.DISCONNECTED -> "Connection lost"
        else -> null
    }
    val transientWarningDetailText = when (connectionState) {
        AppConnectionState.WEAK_NETWORK,
        AppConnectionState.RETRYING -> "Video quality may be affected"
        AppConnectionState.DISCONNECTED -> "Unable to reconnect"
        else -> null
    }

    fun shutdownHostSession(exitScreen: Boolean) {
        if (isEndingSession) return

        isEndingSession = true
        WebRtcSessionManager.stopLocalCamera()
        WebRtcSessionManager.clearConnections()

        if (exitScreen) {
            onBack()
        }
    }

    fun endHostSession() {
        if (isEndingSession) return

        scope.launch {
            runCatching { repository.endSession(roomCode) }
                .onFailure { Log.e("SESSION_END", "Failed to end host session", it) }
            shutdownHostSession(exitScreen = true)
        }
    }

    fun triggerTapToFocus(tapOffset: Offset) {
        if (!sessionIsActive) return

        focusPoint = tapOffset
        focusSucceeded = null
        focusUiToken++

        val currentCamera = camera ?: return
        val action = FocusMeteringAction.Builder(
            previewView.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y),
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        )
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        runCatching {
            val future = currentCamera.cameraControl.startFocusAndMetering(action)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result -> focusSucceeded = result.isFocusSuccessful }
                        .onFailure {
                            Log.w("CAMERA_FOCUS", "Tap to focus result failed", it)
                            focusSucceeded = false
                        }
                },
                ContextCompat.getMainExecutor(context)
            )
        }.onFailure {
            Log.w("CAMERA_FOCUS", "Tap to focus request failed", it)
            focusSucceeded = false
        }
    }

    fun triggerRemoteTapToFocus(normalizedX: Double, normalizedY: Double) {
        val width = previewView.width.toFloat()
        val height = previewView.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val previewRect =
            calculateFittedPreviewRect(
                containerWidth = width,
                containerHeight = height,
                contentWidth = resolvedWidth.toFloat(),
                contentHeight = resolvedHeight.toFloat()
            ) ?: Rect(0f, 0f, width, height)
        val mappedNormalizedX =
            if (isFrontCamera) {
                1.0 - normalizedX.coerceIn(0.0, 1.0)
            } else {
                normalizedX.coerceIn(0.0, 1.0)
            }
        val mappedNormalizedY = normalizedY.coerceIn(0.0, 1.0)

        triggerTapToFocus(
            Offset(
                x = previewRect.left + (mappedNormalizedX.toFloat() * previewRect.width),
                y = previewRect.top + (mappedNormalizedY.toFloat() * previewRect.height)
            ).clampTo(previewRect)
        )
    }

    fun updateExposureFromProgress(progress: Float) {
        if (!exposureSupported) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetIndex = (
            exposureMinIndex +
                ((1f - clampedProgress) * (exposureMaxIndex - exposureMinIndex))
        ).roundToInt().coerceIn(exposureMinIndex, exposureMaxIndex)

        focusUiToken++
        if (targetIndex == firebaseExposureIndex) return

        scope.launch {
            repository.updateExposureIndex(roomCode, targetIndex)
        }
    }

    DisposableEffect(roomCode) {
        val registration = repository.listenToControllerIceCandidates(roomCode) { candidate ->
            val pc = WebRtcSessionManager.cameraPeerConnection
            if (isRemoteDescriptionSet && pc != null) {
                pc.addIceCandidate(candidate)
            } else {
                pendingCandidates.add(candidate)
            }
        }
        onDispose { registration.remove() }
    }

    LaunchedEffect(offerSdp, isStreaming) {
        val currentOfferSdp = offerSdp
        if (isStreaming && currentOfferSdp != null && !answerCreated) {
            createAnswer(
                context = context,
                roomCode = roomCode,
                offerSdp = currentOfferSdp,
                repository = repository,
                onRemoteDescriptionSet = {
                    isRemoteDescriptionSet = true
                    val pc = WebRtcSessionManager.cameraPeerConnection
                    if (pc != null) {
                        pendingCandidates.forEach { pc.addIceCandidate(it) }
                        pendingCandidates.clear()
                    }
                }
            )
            answerCreated = true
        }
    }

    LaunchedEffect(roomStatus) {
        if (roomStatus == "connected") {
            hasSeenConnectedState = true
        } else {
            focusPoint = null
            focusSucceeded = null
        }

        if (roomStatus == "ended") {
            Toast.makeText(context, "Session ended", Toast.LENGTH_SHORT).show()
            shutdownHostSession(exitScreen = true)
        } else if (hasSeenConnectedState && roomStatus == "waiting") {
            shutdownHostSession(exitScreen = true)
        }
    }

    LaunchedEffect(firebaseLensFacing) {
        lensFacing = if (firebaseLensFacing == "front") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun shouldUseFrontScreenFlash(): Boolean {
        if (!isFrontCamera) return false

        return when (firebaseFlashMode) {
            "on" -> true
            "auto" -> isPreviewSceneDark(previewView)
            else -> false
        }
    }

    fun takePhotoWithCameraX(useFrontScreenFlash: Boolean) {
        val currentCapture = imageCapture ?: run {
            Log.e("AICameraAssistant", "ImageCapture is not initialized yet")
            return
        }

        val resolvedCaptureFlashMode = when {
            useFrontScreenFlash -> ImageCapture.FLASH_MODE_SCREEN
            isFrontCamera -> ImageCapture.FLASH_MODE_OFF
            !hasLedFlash -> ImageCapture.FLASH_MODE_OFF
            firebaseFlashMode == "on" -> ImageCapture.FLASH_MODE_ON
            firebaseFlashMode == "auto" -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        currentCapture.screenFlash = if (useFrontScreenFlash) {
            previewView.screenFlash
        } else {
            null
        }
        currentCapture.flashMode = resolvedCaptureFlashMode

        val name = "IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AICameraAssistant")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        currentCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("AICameraAssistant", "Photo saved: ${output.savedUri}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("AICameraAssistant", "Photo capture failed", exception)
                }
            }
        )
    }

    LaunchedEffect(firebaseCaptureRequest) {
        if (firebaseCaptureRequest) {
            val useFrontScreenFlash = shouldUseFrontScreenFlash()

            if (!useFrontScreenFlash && captureFlashMode != ImageCapture.FLASH_MODE_OFF) {
                flashAlpha = 0.85f
            }

            takePhotoWithCameraX(useFrontScreenFlash = useFrontScreenFlash)
            scope.launch {
                repository.resetCaptureRequest(roomCode)
            }
        }
    }

    LaunchedEffect(activity) {
        previewView.setScreenFlashOverlayColor(AndroidColor.WHITE)
        previewView.setScreenFlashWindow(activity?.window)
    }


    LaunchedEffect(lensFacing, isStreaming) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = try {
            cameraProviderFuture.get()
        } catch (_: Exception) {
            return@LaunchedEffect
        }

        delay(300)

        val targetSize = Size(1080, 1920)
        val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    targetSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val localPreview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(targetRotation)
            .build()

        localPreview.setSurfaceProvider(previewView.surfaceProvider)

        val newImageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(targetRotation)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()

            val firstCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                localPreview,
                newImageCapture
            )

            camera = firstCamera
            imageCapture = newImageCapture
            exposureMinIndex = firstCamera.cameraInfo.exposureState.exposureCompensationRange.lower
            exposureMaxIndex = firstCamera.cameraInfo.exposureState.exposureCompensationRange.upper
            exposureIndex = firstCamera.cameraInfo.exposureState.exposureCompensationIndex
            scope.launch {
                repository.updateExposureState(
                    roomCode = roomCode,
                    minIndex = exposureMinIndex,
                    maxIndex = exposureMaxIndex,
                    currentIndex = exposureIndex
                )
            }

            scope.launch {
                repository.updateFlashSupported(
                    roomCode = roomCode,
                    flashSupported = firstCamera.cameraInfo.hasFlashUnit() ||
                        lensFacing == CameraSelector.LENS_FACING_FRONT
                )
            }

            firstCamera.cameraInfo.zoomState.value?.let { zoomState ->
                scope.launch {
                    repository.updateZoomRange(
                        roomCode = roomCode,
                        minZoom = zoomState.minZoomRatio.toDouble(),
                        maxZoom = zoomState.maxZoomRatio.toDouble()
                    )
                }
            }

            val rawSize =
                newImageCapture.resolutionInfo?.resolution
                    ?: localPreview.resolutionInfo?.resolution
                    ?: targetSize

            val rotationDegrees =
                newImageCapture.resolutionInfo?.rotationDegrees
                    ?: localPreview.resolutionInfo?.rotationDegrees
                    ?: 0

            val displayedWidth =
                if (rotationDegrees == 90 || rotationDegrees == 270) rawSize.height else rawSize.width
            val displayedHeight =
                if (rotationDegrees == 90 || rotationDegrees == 270) rawSize.width else rawSize.height

            resolvedWidth = displayedWidth
            resolvedHeight = displayedHeight

            Log.d(
                "CAMERA_SIZE",
                "CameraX raw=${rawSize.width}x${rawSize.height}, rotation=$rotationDegrees, displayed=${resolvedWidth}x${resolvedHeight}"
            )
            Log.w(
                "PREVIEW_MATCH",
                "camera_publish room=$roomCode raw=${rawSize.width}x${rawSize.height} rotation=$rotationDegrees published=${resolvedWidth}x${resolvedHeight}"
            )

            scope.launch {
                repository.updatePreviewSize(roomCode, resolvedWidth, resolvedHeight)
            }

            cameraProvider.unbindAll()

            val finalUseCases = mutableListOf<UseCase>(localPreview, newImageCapture)

            if (isStreaming) {
                val streamMaxLongEdge = 2560
                val streamScale =
                    (streamMaxLongEdge.toFloat() / maxOf(rawSize.width, rawSize.height)).coerceAtMost(1f)
                val streamWidth = (rawSize.width * streamScale).toInt().coerceAtLeast(1)
                val streamHeight = (rawSize.height * streamScale).toInt().coerceAtLeast(1)

                val webRtcSurface = WebRtcSessionManager.startWebRtcCameraSource(
                    context = context,
                    width = streamWidth,
                    height = streamHeight,
                    rotationDegrees = rotationDegrees
                )

                if (webRtcSurface != null) {
                    val streamingPreview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(targetRotation)
                        .build()

                    streamingPreview.setSurfaceProvider { request ->
                        request.provideSurface(
                            webRtcSurface,
                            ContextCompat.getMainExecutor(context)
                        ) {}
                    }

                    finalUseCases.add(streamingPreview)
                }
            } else {
                WebRtcSessionManager.stopLocalCamera()
            }

            val finalCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *finalUseCases.toTypedArray()
            )

            camera = finalCamera
            imageCapture = newImageCapture
        } catch (e: Exception) {
            Log.e("CAMERA_BIND", "Camera bind failed", e)
        }
    }

    LaunchedEffect(camera, firebaseZoomLevel) {
        val currentCamera = camera ?: return@LaunchedEffect
        val zoomState = currentCamera.cameraInfo.zoomState.value
        val maxZoom = zoomState?.maxZoomRatio ?: 1f
        val minZoom = zoomState?.minZoomRatio ?: 1f
        val clampedZoom = firebaseZoomLevel.toFloat().coerceIn(minZoom, maxZoom)
        currentCamera.cameraControl.setZoomRatio(clampedZoom)
        if (clampedZoom.toDouble() != firebaseZoomLevel) {
            scope.launch {
                repository.updateZoomLevel(roomCode, clampedZoom.toDouble())
            }
        }
    }

    LaunchedEffect(flashAlpha) {
        if (flashAlpha > 0f && !(isFrontCamera && firebaseFlashMode != "off")) {
            delay(220)
            flashAlpha = 0f
        }
    }

    LaunchedEffect(camera, firebaseExposureIndex, exposureMinIndex, exposureMaxIndex) {
        val currentCamera = camera ?: return@LaunchedEffect
        if (!exposureSupported) return@LaunchedEffect

        val clampedExposure = firebaseExposureIndex.coerceIn(exposureMinIndex, exposureMaxIndex)
        if (clampedExposure != exposureIndex) {
            exposureIndex = clampedExposure
            currentCamera.cameraControl.setExposureCompensationIndex(clampedExposure)
            if (clampedExposure != firebaseExposureIndex) {
                scope.launch {
                    repository.updateExposureIndex(roomCode, clampedExposure)
                }
            }
        }
    }

    LaunchedEffect(focusUiToken) {
        if (focusUiToken == 0) return@LaunchedEffect
        delay(2600)
        focusPoint = null
        focusSucceeded = null
    }

    LaunchedEffect(firebaseFocusRequestId, firebaseFocusPointX, firebaseFocusPointY, camera) {
        if (firebaseFocusRequestId <= 0L || firebaseFocusRequestId == lastAppliedRemoteFocusRequestId) {
            return@LaunchedEffect
        }
        lastAppliedRemoteFocusRequestId = firebaseFocusRequestId
        triggerRemoteTapToFocus(firebaseFocusPointX, firebaseFocusPointY)
    }

    DisposableEffect(Unit) {
        onDispose {
            WebRtcSessionManager.stopLocalCamera()
            WebRtcSessionManager.clearConnections()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val boxMaxWidthPx = with(density) { maxWidth.toPx() }
        val boxMaxHeightPx = with(density) { maxHeight.toPx() }
        val previewContentRect =
            remember(boxMaxWidthPx, boxMaxHeightPx, resolvedWidth, resolvedHeight) {
                calculateFittedPreviewRect(
                    containerWidth = boxMaxWidthPx,
                    containerHeight = boxMaxHeightPx,
                    contentWidth = resolvedWidth.toFloat(),
                    contentHeight = resolvedHeight.toFloat()
                )
            }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }

        if (sessionIsActive && transientWarningText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = transientWarningText,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    transientWarningDetailText?.let { detailText ->
                        Text(
                            text = detailText,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        if (firebaseGridEnabled) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
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

        if (sessionIsActive) {
            focusPoint?.let { rawPoint ->
                val point = previewContentRect?.let(rawPoint::clampTo) ?: rawPoint
                FocusReticle(
                    point = point,
                    success = focusSucceeded,
                    modifier = Modifier.fillMaxSize()
                )

                if (exposureSupported) {
                    val sliderOffset = with(density) {
                        val previewRect =
                            previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                        val sliderWidthPx = 44.dp.toPx()
                        val sliderHeightPx = 168.dp.toPx()
                        val reticleHalfPx = 34.dp.toPx()
                        val horizontalGapPx = 12.dp.toPx()
                        val minX = previewRect.left + 12.dp.toPx()
                        val maxX = previewRect.right - sliderWidthPx - 12.dp.toPx()
                        val desiredRightX = point.x + reticleHalfPx + horizontalGapPx
                        val desiredLeftX = point.x - reticleHalfPx - horizontalGapPx - sliderWidthPx
                        val desiredX = when {
                            point.x <= previewRect.center.x && desiredRightX <= maxX -> desiredRightX
                            point.x > previewRect.center.x && desiredLeftX >= minX -> desiredLeftX
                            desiredRightX <= maxX -> desiredRightX
                            else -> desiredLeftX
                        }
                        val desiredY = point.y - (sliderHeightPx / 2f)
                        val minY = previewRect.top + 12.dp.toPx()
                        val maxY = previewRect.bottom - sliderHeightPx - 12.dp.toPx()
                        IntOffset(
                            x = desiredX.coerceIn(minX, maxX).roundToInt(),
                            y = desiredY.coerceIn(minY, maxY).roundToInt()
                        )
                    }
                    val exposureProgress =
                        (exposureMaxIndex - firebaseExposureIndex).toFloat() /
                            (exposureMaxIndex - exposureMinIndex)
                                .toFloat()
                                .coerceAtLeast(1f)
                    val neutralExposureProgress =
                        (exposureMaxIndex - 0.coerceIn(exposureMinIndex, exposureMaxIndex)).toFloat() /
                            (exposureMaxIndex - exposureMinIndex)
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { endHostSession() },
                    enabled = !isEndingSession,
                    modifier = Modifier.background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = if (isEndingSession) "Ending session" else "End session",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )

                        Text(
                            text = statusText,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!sessionIsActive) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Room Code",
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                text = roomCode,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = buildString {
                                    append(if (firebaseLensFacing == "front") "Front" else "Back")
                                    append(" | ")
                                    append("${firebaseZoomLevel}x")
                                    append(" | ")
                                    append(
                                        when {
                                            isFrontCamera && firebaseFlashMode == "on" -> "Screen Flash On"
                                            isFrontCamera && firebaseFlashMode == "auto" -> "Screen Flash Auto"
                                            !flashSupported && firebaseFlashMode != "off" -> "Flash Unsupported"
                                            firebaseFlashMode == "auto" -> "Flash Auto"
                                            firebaseFlashMode == "on" -> "Flash On"
                                            else -> "Flash Off"
                                        }
                                    )
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (resolvedWidth > 0 && resolvedHeight > 0) {
                                    "Hardware: ${resolvedWidth} x ${resolvedHeight}"
                                } else {
                                    "Hardware: Detecting..."
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (firebaseRequestReceived && !firebaseControllerApproved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch { repository.updateApproval(roomCode, true) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Allow")
                    }

                    Button(
                        onClick = {
                            scope.launch { repository.updateApproval(roomCode, false) }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Deny")
                    }
                }
            }

        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HostCameraModeButton(
                icon = when {
                    !flashSupported -> Icons.Default.FlashOff
                    firebaseFlashMode == "auto" -> Icons.Default.FlashAuto
                    firebaseFlashMode == "on" -> Icons.Default.FlashOn
                    else -> Icons.Default.FlashOff
                },
                label = when {
                    !flashSupported -> "Unsupported"
                    firebaseFlashMode == "auto" -> "Auto"
                    firebaseFlashMode == "on" -> "On"
                    else -> "Off"
                },
                enabled = flashSupported,
                onClick = {
                    if (!flashSupported) return@HostCameraModeButton
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

            HostCameraModeButton(
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
    }
}

@Composable
fun CameraGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = 0.24f)
        val strokeWidth = 0.9.dp.toPx()
        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f

        repeat(2) { index ->
            val verticalX = thirdWidth * (index + 1)
            drawLine(
                color = lineColor,
                start = Offset(verticalX, 0f),
                end = Offset(verticalX, size.height),
                strokeWidth = strokeWidth
            )

            val horizontalY = thirdHeight * (index + 1)
            drawLine(
                color = lineColor,
                start = Offset(0f, horizontalY),
                end = Offset(size.width, horizontalY),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
fun GridToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = if (isActive) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f))
        } else {
            null
        }
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val iconColor = Color.White.copy(alpha = if (isActive) 0.96f else 0.88f)
                val strokeWidth = 1.2.dp.toPx()
                val thirdWidth = size.width / 3f
                val thirdHeight = size.height / 3f

                repeat(2) { index ->
                    val verticalX = thirdWidth * (index + 1)
                    drawLine(
                        color = iconColor,
                        start = Offset(verticalX, 0f),
                        end = Offset(verticalX, size.height),
                        strokeWidth = strokeWidth
                    )

                    val horizontalY = thirdHeight * (index + 1)
                    drawLine(
                        color = iconColor,
                        start = Offset(0f, horizontalY),
                        end = Offset(size.width, horizontalY),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
    }
}

@Composable
fun HostCameraModeButton(
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
private fun FocusReticle(
    point: Offset,
    success: Boolean?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "focus_reticle_scale"
    )
    val ringColor = when (success) {
        true -> Color(0xFFFFD54F)
        false -> Color.White.copy(alpha = 0.72f)
        null -> Color.White
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isPreviewSceneDark(previewView: PreviewView): Boolean {
    val bitmap = previewView.bitmap ?: return true
    return try {
        val startX = bitmap.width / 4
        val endX = bitmap.width - startX
        val startY = bitmap.height / 4
        val endY = bitmap.height - startY
        val sampleX = max(1, (endX - startX) / 18)
        val sampleY = max(1, (endY - startY) / 18)
        var luminanceSum = 0.0
        var samples = 0

        for (x in startX until endX step sampleX) {
            for (y in startY until endY step sampleY) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                luminanceSum += (0.299 * red) + (0.587 * green) + (0.114 * blue)
                samples++
            }
        }

        val averageLuminance = if (samples == 0) 255.0 else luminanceSum / samples
        averageLuminance < 60.0
    } finally {
        bitmap.recycle()
    }
}

fun createAnswer(
    context: Context,
    roomCode: String,
    offerSdp: String,
    repository: FirebaseRoomRepository,
    onRemoteDescriptionSet: () -> Unit
) {
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createCameraPeerConnection { candidate ->
        CoroutineScope(Dispatchers.IO).launch {
            repository.addCameraIceCandidate(roomCode, candidate)
        }
    } ?: return

    pc.setRemoteDescription(
        WebRtcSessionManager.sessionDescriptionObserver(
            onSetSuccess = { onRemoteDescriptionSet() }
        ),
        SessionDescription(SessionDescription.Type.OFFER, offerSdp)
    )

    pc.createAnswer(
        WebRtcSessionManager.sessionDescriptionObserver(
            onCreateSuccess = { desc ->
                pc.setLocalDescription(
                    WebRtcSessionManager.sessionDescriptionObserver(),
                    desc
                )
                CoroutineScope(Dispatchers.IO).launch {
                    repository.saveAnswer(roomCode, desc.description)
                }
            }
        ),
        MediaConstraints()
    )
}
