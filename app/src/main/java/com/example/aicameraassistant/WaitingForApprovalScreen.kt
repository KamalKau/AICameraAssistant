package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*

@Composable
fun WaitingForApprovalScreen(
    roomCode: String,
    repository: FirebaseRoomRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val roomStatus by repository.getRoomStatus(roomCode).collectAsState(initial = "waiting")
    val firebaseLensFacing by repository.getLensFacing(roomCode).collectAsState(initial = "back")
    val firebaseZoomLevel by repository.getZoomLevel(roomCode).collectAsState(initial = 1.0)
    val firebaseFlashEnabled by repository.getFlashEnabled(roomCode).collectAsState(initial = false)
    val firebaseAnswer by repository.getAnswerSdp(roomCode).collectAsState(initial = null)
    
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var offerCreated by remember(roomCode) { mutableStateOf(false) }
    
    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by remember { mutableStateOf(false) }

    // Listen for Camera Candidates
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
            remoteTrack?.removeSink(rendererRef)
            rendererRef?.release()
            WebRtcSessionManager.clearConnections()
        }
    }

    LaunchedEffect(roomStatus) {
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
                            Log.d("WEBRTC_LOG", "Controller remote description set, applying ${pendingCandidates.size} candidates")
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

    LaunchedEffect(rendererRef, remoteTrack) {
        val renderer = rendererRef ?: return@LaunchedEffect
        val track = remoteTrack ?: return@LaunchedEffect
        Log.d("WEBRTC_LOG", "Controller rendering remote track")
        WebRtcSessionManager.renderRemoteTrack(track, renderer)
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

            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { renderer ->
                            renderer.init(WebRtcSessionManager.eglBase.eglBaseContext, null)
                            renderer.setMirror(false)
                            renderer.setEnableHardwareScaler(true)
                            // Match CameraScreen's FIT_CENTER to show exact hardware frame
                            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            rendererRef = renderer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.updateFlashEnabled(roomCode, !firebaseFlashEnabled)
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (firebaseFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (firebaseFlashEnabled) Color.Yellow else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                val nextFacing = if (firebaseLensFacing == "back") "front" else "back"
                                repository.updateLensFacing(roomCode, nextFacing)
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.SwitchCamera, contentDescription = "Flip Camera", tint = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomButton(text = "1x", isSelected = firebaseZoomLevel == 1.0) {
                        scope.launch { repository.updateZoomLevel(roomCode, 1.0) }
                    }
                    ZoomButton(text = "2x", isSelected = firebaseZoomLevel == 2.0) {
                        scope.launch { repository.updateZoomLevel(roomCode, 2.0) }
                    }
                    ZoomButton(text = "3x", isSelected = firebaseZoomLevel == 3.0) {
                        scope.launch { repository.updateZoomLevel(roomCode, 3.0) }
                    }
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
fun ZoomButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
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
