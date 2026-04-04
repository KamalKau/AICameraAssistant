package com.example.aicameraassistant

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

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

    DisposableEffect(roomCode) {
        val registration = repository.listenToCameraIceCandidates(roomCode) { candidate ->
            WebRtcSessionManager.controllerPeerConnection?.addIceCandidate(candidate)
        }

        onDispose {
            registration.remove()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                remoteTrack?.removeSink(rendererRef)
            } catch (_: Exception) {
            }

            try {
                rendererRef?.release()
            } catch (_: Exception) {
            }

            rendererRef = null
            remoteTrack = null
        }
    }

    LaunchedEffect(roomStatus) {
        if (roomStatus == "connected" && !offerCreated) {
            createOffer(
                context = context,
                roomCode = roomCode,
                repository = repository,
                onRemoteTrackReady = { track ->
                    remoteTrack = track
                }
            )
            offerCreated = true
        }
    }

    LaunchedEffect(firebaseAnswer) {
        val answer = firebaseAnswer ?: return@LaunchedEffect
        val pc = WebRtcSessionManager.controllerPeerConnection ?: return@LaunchedEffect

        if (pc.remoteDescription == null) {
            pc.setRemoteDescription(
                WebRtcSessionManager.sessionDescriptionObserver(),
                SessionDescription(SessionDescription.Type.ANSWER, answer)
            )
        }
    }

    LaunchedEffect(rendererRef, remoteTrack) {
        val renderer = rendererRef ?: return@LaunchedEffect
        val track = remoteTrack ?: return@LaunchedEffect
        WebRtcSessionManager.renderRemoteTrack(track, renderer)
    }

    val statusText = when (roomStatus) {
        "connected" -> "Connected"
        "denied" -> "Request Denied"
        "request_received" -> "Waiting for approval"
        else -> "Waiting for approval"
    }

    val statusColor = when (roomStatus) {
        "connected" -> Color(0xFF4CAF50)
        "denied" -> Color(0xFFF44336)
        else -> Color.Unspecified
    }

    val streamZoom = firebaseZoomLevel.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onBack
        ) {
            Text(
                text = "Back",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium,
            color = statusColor
        )

        Text(
            text = "Room Code: $roomCode",
            style = MaterialTheme.typography.bodyLarge
        )

        if (roomStatus == "request_received") {
            Text(
                text = "Waiting for camera to approve...",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (roomStatus == "connected") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also { renderer ->
                            renderer.init(WebRtcSessionManager.eglBase.eglBaseContext, null)
                            renderer.setMirror(false)
                            renderer.setEnableHardwareScaler(true)
                            rendererRef = renderer
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = streamZoom,
                            scaleY = streamZoom
                        )
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        // repository.sendCaptureRequest(roomCode) // Need to add this to repo
                        // For now let's use the one that works
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("rooms").document(roomCode).update("captureRequest", true)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Capture Photo")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val nextFacing =
                                if (firebaseLensFacing == "back") "front" else "back"
                            repository.updateLensFacing(roomCode, nextFacing)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Flip Camera")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.updateFlashEnabled(roomCode, !firebaseFlashEnabled)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (firebaseFlashEnabled) "Flash On" else "Flash Off")
                }
            }

            Text(
                text = "Zoom",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.updateZoomLevel(roomCode, 1.0)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("1x")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.updateZoomLevel(roomCode, 2.0)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("2x")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.updateZoomLevel(roomCode, 3.0)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("3x")
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (roomStatus) {
                    "connected" -> "Done"
                    "denied" -> "Back"
                    else -> "Cancel Request"
                }
            )
        }
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
            GlobalScope.launch {
                repository.addControllerIceCandidate(roomCode, candidate)
            }
        },
        onRemoteTrack = { videoTrack ->
            onRemoteTrackReady(videoTrack)
        }
    ) ?: return

    val mediaConstraints = MediaConstraints()

    pc.createOffer(
        WebRtcSessionManager.sessionDescriptionObserver(
            onCreateSuccess = { desc ->
                pc.setLocalDescription(
                    WebRtcSessionManager.sessionDescriptionObserver(),
                    desc
                )

                GlobalScope.launch {
                    repository.saveOffer(roomCode, desc.description)
                }
            }
        ),
        mediaConstraints
    )
}
