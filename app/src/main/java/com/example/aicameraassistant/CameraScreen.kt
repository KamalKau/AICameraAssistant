package com.example.aicameraassistant

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    roomCode: String
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val repository = remember { FirebaseRoomRepository() }
    val scope = rememberCoroutineScope()

    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission not granted")
        }
        return
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashAlpha by remember { mutableFloatStateOf(0f) }

    var firebaseRequestReceived by remember { mutableStateOf(false) }
    var firebaseControllerApproved by remember { mutableStateOf(false) }
    var roomStatus by remember { mutableStateOf("waiting") }
    var firebaseCaptureRequest by remember { mutableStateOf(false) }
    var firebaseLensFacing by remember { mutableStateOf("back") }
    var firebaseZoomLevel by remember { mutableStateOf(1.0) }
    var firebaseFlashEnabled by remember { mutableStateOf(false) }
    var firebaseOffer by remember { mutableStateOf<String?>(null) }
    var firebaseAnswer by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var localRendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var answerCreated by remember(roomCode) { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(roomCode) {
        try {
            repository.createRoom(roomCode)
            Log.d("ROOM_CREATE", "Room created or reused: $roomCode")
        } catch (e: Exception) {
            Log.e("ROOM_CREATE", "Failed to create room", e)
        }
    }

    DisposableEffect(roomCode) {
        val registration = repository.listenToRoom(
            roomCode
        ) { request, approved, status, captureRequest, lensFacingValue, zoomLevelValue, flashEnabledValue, offer, answer ->
            firebaseRequestReceived = request
            firebaseControllerApproved = approved
            roomStatus = status
            firebaseCaptureRequest = captureRequest
            firebaseLensFacing = lensFacingValue
            firebaseZoomLevel = zoomLevelValue
            firebaseFlashEnabled = flashEnabledValue
            firebaseOffer = offer
            firebaseAnswer = answer
        }

        onDispose {
            registration.remove()
        }
    }

    DisposableEffect(roomCode) {
        val registration = repository.listenToControllerIceCandidates(roomCode) { candidate ->
            WebRtcSessionManager.cameraPeerConnection?.addIceCandidate(candidate)
        }

        onDispose {
            registration.remove()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                WebRtcSessionManager.localVideoTrack?.removeSink(localRendererRef)
            } catch (_: Exception) {
            }

            try {
                localRendererRef?.release()
            } catch (_: Exception) {
            }

            localRendererRef = null
        }
    }

    LaunchedEffect(roomStatus) {
        isStreaming = roomStatus == "connected"
    }

    LaunchedEffect(firebaseOffer, firebaseAnswer, roomStatus) {
        val offer = firebaseOffer
        if (roomStatus == "connected" && offer != null && firebaseAnswer == null && !answerCreated) {
            try {
                cameraProviderFuture.get().unbindAll()
                Log.d("CAMERA_BIND", "CameraX unbound before createAnswer")
            } catch (e: Exception) {
                Log.e("CAMERA_BIND", "Failed to unbind before createAnswer", e)
            }

            createAnswer(
                context = context,
                roomCode = roomCode,
                offerSdp = offer,
                repository = repository,
                useFrontCamera = firebaseLensFacing == "front"
            )
            answerCreated = true
        }
    }

    LaunchedEffect(localRendererRef, isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        val renderer = localRendererRef ?: return@LaunchedEffect

        while (WebRtcSessionManager.localVideoTrack == null) {
            delay(100)
        }

        val track = WebRtcSessionManager.localVideoTrack ?: return@LaunchedEffect
        WebRtcSessionManager.renderLocalTrack(track, renderer)
    }

    LaunchedEffect(firebaseLensFacing, isStreaming) {
        if (!isStreaming) {
            lensFacing =
                if (firebaseLensFacing == "front") {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            return@LaunchedEffect
        }

        val shouldBeFront = firebaseLensFacing == "front"
        if (WebRtcSessionManager.isFrontCamera != shouldBeFront) {
            WebRtcSessionManager.switchCamera { isFront ->
                Log.d("WEBRTC_CAMERA", "Remote flip applied. front=$isFront")
            }
        }
    }

    LaunchedEffect(firebaseLensFacing, localRendererRef) {
        localRendererRef?.setMirror(firebaseLensFacing == "front")
    }

    fun takePhotoWithCameraX() {
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

        imageCapture.takePicture(
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

    LaunchedEffect(lensFacing, firebaseZoomLevel, firebaseFlashEnabled, isStreaming) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            if (!isStreaming) {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()

            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            val cameraControl = camera.cameraControl
            val cameraInfo = camera.cameraInfo

            val zoomState = cameraInfo.zoomState.value
            val maxZoom = zoomState?.maxZoomRatio ?: 1f
            val minZoom = zoomState?.minZoomRatio ?: 1f
            val clampedZoom = firebaseZoomLevel.toFloat().coerceIn(minZoom, maxZoom)

            cameraControl.setZoomRatio(clampedZoom)
            cameraControl.enableTorch(firebaseFlashEnabled)

            Log.d(
                "CAMERA_BIND",
                "Camera bound. streaming=$isStreaming lens=$lensFacing zoom=$clampedZoom flash=$firebaseFlashEnabled"
            )
        } catch (e: Exception) {
            Log.e("CAMERA_BIND", "Camera bind failed", e)
        }
    }

    LaunchedEffect(flashAlpha) {
        if (flashAlpha > 0f) {
            delay(120)
            flashAlpha = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isStreaming) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { renderer ->
                        renderer.init(WebRtcSessionManager.eglBase.eglBaseContext, null)
                        renderer.setMirror(firebaseLensFacing == "front")
                        renderer.setEnableHardwareScaler(true)
                        localRendererRef = renderer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

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
                onClick = onBack
            ) {
                Text(
                    text = "Back",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
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
                                when {
                                    roomStatus == "connected" -> Color(0xFF4CAF50)
                                    roomStatus == "request_received" -> Color(0xFFFF9800)
                                    roomStatus == "denied" -> Color(0xFFF44336)
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

                    Text(
                        text = buildString {
                            append(if (firebaseLensFacing == "front") "Front" else "Back")
                            append(" • ")
                            append("${firebaseZoomLevel}x")
                            append(" • ")
                            append(if (firebaseFlashEnabled) "Flash On" else "Flash Off")
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (firebaseRequestReceived && !firebaseControllerApproved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                repository.updateApproval(roomCode, true)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Allow")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                repository.updateApproval(roomCode, false)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deny")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    IconButton(
                        onClick = {
                            if (isStreaming) {
                                val nextFacing =
                                    if (WebRtcSessionManager.isFrontCamera) "back" else "front"

                                scope.launch {
                                    repository.updateLensFacing(roomCode, nextFacing)
                                }
                            } else {
                                scope.launch {
                                    val nextFacing =
                                        if (firebaseLensFacing == "back") "front" else "back"
                                    repository.updateLensFacing(roomCode, nextFacing)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cached,
                            contentDescription = "Flip Camera",
                            tint = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = CircleShape,
                    color = Color.White,
                    onClick = {
                        flashAlpha = 0.85f
                        takePhotoWithCameraX()
                    }
                ) {}
            }

            Box(modifier = Modifier.weight(1f))
        }
    }
}

fun createAnswer(
    context: Context,
    roomCode: String,
    offerSdp: String,
    repository: FirebaseRoomRepository,
    useFrontCamera: Boolean
) {
    WebRtcSessionManager.initialize(context)
    WebRtcSessionManager.stopLocalCamera()
    WebRtcSessionManager.clearConnections()
    WebRtcSessionManager.startLocalCamera(context, useFrontCamera)

    val pc = WebRtcSessionManager.createCameraPeerConnection(
        onIceCandidate = { candidate ->
            kotlinx.coroutines.GlobalScope.launch {
                repository.addCameraIceCandidate(roomCode, candidate)
            }
        }
    ) ?: return

    WebRtcSessionManager.addLocalTracksToCameraPeer()

    val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)

    pc.setRemoteDescription(
        WebRtcSessionManager.sessionDescriptionObserver(
            onSetSuccess = {
                pc.createAnswer(
                    WebRtcSessionManager.sessionDescriptionObserver(
                        onCreateSuccess = { desc ->
                            pc.setLocalDescription(
                                WebRtcSessionManager.sessionDescriptionObserver(),
                                desc
                            )

                            kotlinx.coroutines.GlobalScope.launch {
                                repository.saveAnswer(roomCode, desc.description)
                            }
                        }
                    ),
                    MediaConstraints()
                )
            }
        ),
        offer
    )
}