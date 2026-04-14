package com.example.aicameraassistant

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val firebaseLensFacing by repository.getLensFacing(roomCode).collectAsState(initial = "back")
    val firebaseZoomLevel by repository.getZoomLevel(roomCode).collectAsState(initial = 1.0)
    val firebaseFlashEnabled by repository.getFlashEnabled(roomCode).collectAsState(initial = false)
    val firebaseCaptureRequest by repository.getCaptureRequest(roomCode).collectAsState(initial = false)
    val firebaseRequestReceived by repository.getRequestReceived(roomCode).collectAsState(initial = false)
    val firebaseControllerApproved by repository.getControllerApproved(roomCode).collectAsState(initial = false)
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

    var flashAlpha by remember { mutableFloatStateOf(0f) }
    val isStreaming = roomStatus == "connected"
    var answerCreated by remember { mutableStateOf(false) }
    var hasSeenConnectedState by remember(roomCode) { mutableStateOf(false) }
    var isEndingSession by remember(roomCode) { mutableStateOf(false) }

    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by remember { mutableStateOf(false) }

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

    fun takePhotoWithCameraX() {
        val currentCapture = imageCapture ?: run {
            Log.e("AICameraAssistant", "ImageCapture is not initialized yet")
            return
        }

        currentCapture.flashMode =
            if (firebaseFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

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
            flashAlpha = 0.85f
            takePhotoWithCameraX()
            scope.launch {
                repository.resetCaptureRequest(roomCode)
            }
        }
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
                val streamMaxLongEdge = 1280
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

    LaunchedEffect(camera, firebaseFlashEnabled) {
        val currentCamera = camera ?: return@LaunchedEffect
        currentCamera.cameraControl.enableTorch(firebaseFlashEnabled)
    }

    LaunchedEffect(flashAlpha) {
        if (flashAlpha > 0f) {
            delay(120)
            flashAlpha = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            WebRtcSessionManager.stopLocalCamera()
            WebRtcSessionManager.clearConnections()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.45f),
                onClick = {
                    if (hasSeenConnectedState || roomStatus == "request_received") {
                        endHostSession()
                    } else {
                        onBack()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasSeenConnectedState || roomStatus == "request_received") {
                            if (isEndingSession) "Ending..." else "End Session"
                        } else {
                            "Back"
                        },
                        color = Color.White
                    )
                }
            }

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
                            .background(
                                when (roomStatus) {
                                    "connected" -> Color(0xFF4CAF50)
                                    "request_received" -> Color(0xFFFF9800)
                                    "denied" -> Color(0xFFF44336)
                                    else -> Color(0xFFFFC107)
                                }
                            )
                    )

                    Text(
                        text = when (roomStatus) {
                            "connected" -> "Controller connected"
                            "request_received" -> "Connection request received"
                            "denied" -> "Request denied"
                            else -> "Waiting for controller"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

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
                                append(if (firebaseFlashEnabled) "Flash On" else "Flash Off")
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

            if (roomStatus == "connected") {
                Button(
                    onClick = { endHostSession() },
                    enabled = !isEndingSession,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isEndingSession) "Ending..." else "Stop Camera Session")
                }
            }
        }
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
