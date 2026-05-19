package com.example.aicameraassistant

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.aicameraassistant.ui.theme.AICameraAssistantTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            WebRtcSessionManager.initialize(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        setContent {
            AICameraAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FirebaseRoomRepository() }

    var currentScreen by remember { mutableStateOf("home") }
    var pendingRoomCode by remember { mutableStateOf("") }
    var cameraRoomCode by remember { mutableStateOf(generateRoomCode()) }
    var controlRoomCodeError by remember { mutableStateOf<String?>(null) }
    
    var permissionsGranted by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted = perms.values.all { it }
        if (!permissionsGranted) {
            Toast.makeText(context, "Camera and Audio permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    if (permissionsGranted) {
        when (currentScreen) {
            "camera" -> {
                CameraScreen(
                    roomCode = cameraRoomCode,
                    repository = repository,
                    onBack = {
                        currentScreen = "home"
                        cameraRoomCode = generateRoomCode()
                    }
                )
            }

            "controller" -> {
                ControlCameraScreen(
                    errorMessage = controlRoomCodeError,
                    onCodeChanged = { controlRoomCodeError = null },
                    onBack = {
                        controlRoomCodeError = null
                        currentScreen = "home"
                    },
                    onConnect = { roomCode ->
                        controlRoomCodeError = null
                        pendingRoomCode = roomCode
                        WebRtcSessionManager.clearConnections()
                        scope.launch {
                            val exists = repository.sendConnectionRequest(roomCode)
                            if (exists) {
                                currentScreen = "waiting_for_approval"
                            } else {
                                controlRoomCodeError = "Room code not found"
                            }
                        }
                    }
                )
            }

            "waiting_for_approval" -> {
                WaitingForApprovalScreen(
                    roomCode = pendingRoomCode,
                    repository = repository,
                    onBack = {
                        currentScreen = "home"
                        pendingRoomCode = ""
                    }
                )
            }

            else -> {
                HomeScreen(
                    onStartCamera = {
                        scope.launch {
                            if (cameraRoomCode.isBlank()) {
                                cameraRoomCode = generateRoomCode()
                            }
                            WebRtcSessionManager.stopLocalCamera()
                            WebRtcSessionManager.clearConnections()
                            repository.createRoom(cameraRoomCode)
                            currentScreen = "camera"
                        }
                    },
                    onControlCamera = {
                        if (pendingRoomCode.isNotBlank()) {
                            currentScreen = "waiting_for_approval"
                        } else {
                            controlRoomCodeError = null
                            currentScreen = "controller"
                        }
                    }
                )
            }
        }
    } else {
        PermissionsLoadingScreen()
    }
}

fun generateRoomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..5)
        .map { chars.random() }
        .joinToString("")
}

@Composable
fun HomeScreen(
    onStartCamera: () -> Unit,
    onControlCamera: () -> Unit
) {
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
                    .padding(bottom = 18.dp)
                    .size(92.dp)
            )

            Text(
                text = "AI Camera Assistant",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            HomeActionButton(
                title = "Start Camera",
                subtitle = "Host your phone camera",
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF9B54),
                        Color(0xFFFF5D7A)
                    )
                ),
                borderColor = Color(0xFFFFC7A7),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                onClick = onStartCamera
            )

            HomeActionButton(
                title = "Control Camera",
                subtitle = "Join with a room code",
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2B59),
                        Color(0xFF2D7CFF)
                    )
                ),
                borderColor = Color(0xFF82B4FF),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                onClick = onControlCamera
            )
        }
    }
}

@Composable
fun PermissionsLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B)),
        contentAlignment = Alignment.Center
    ) {
        HomeWallpaper(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppFrontLogo(modifier = Modifier.size(74.dp))
            Text(
                text = "Requesting permissions",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Camera and microphone access are needed to start a session.",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun HomeWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF05070B),
                    Color(0xFF0B1020),
                    Color(0xFF07080C)
                )
            )
        )

        val gridColor = Color.White.copy(alpha = 0.045f)
        val step = size.minDimension / 7f
        var x = step
        while (x < size.width) {
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
            y += step
        }

        val lensCenter = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.24f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2D7CFF).copy(alpha = 0.24f),
                    Color.Transparent
                ),
                center = lensCenter,
                radius = size.minDimension * 0.42f
            ),
            radius = size.minDimension * 0.42f,
            center = lensCenter,
            style = Fill
        )
        repeat(4) { index ->
            drawCircle(
                color = Color.White.copy(alpha = 0.045f + (index * 0.012f)),
                radius = size.minDimension * (0.12f + index * 0.07f),
                center = lensCenter,
                style = Stroke(width = 1.2f)
            )
        }

        val warmCenter = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.82f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF5D7A).copy(alpha = 0.2f),
                    Color(0xFFFF9B54).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = warmCenter,
                radius = size.minDimension * 0.48f
            ),
            radius = size.minDimension * 0.48f,
            center = warmCenter
        )
    }
}

@Composable
fun HomeActionButton(
    title: String,
    subtitle: String,
    backgroundBrush: Brush,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderColor.copy(alpha = 0.85f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun AppFrontLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF8A5B),
                Color(0xFFFF5E7E),
                Color(0xFF9C4DFF),
                Color(0xFF2D7CFF)
            )
        )
        val corner = size.minDimension * 0.3f
        val shellInset = size.minDimension * 0.14f
        val shellWidth = size.width - (shellInset * 2f)
        val shellHeight = size.height - (shellInset * 2f)
        val shellTopLeft = androidx.compose.ui.geometry.Offset(shellInset, shellInset)

        drawRoundRect(
            brush = gradient,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )

        drawRoundRect(
            color = Color.White,
            topLeft = shellTopLeft,
            size = androidx.compose.ui.geometry.Size(
                width = shellWidth,
                height = shellHeight
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.48f, corner * 0.48f),
            style = Stroke(width = size.minDimension * 0.075f)
        )

        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.2f,
            center = center
        )

        drawCircle(
            color = Color(0xFF141414),
            radius = size.minDimension * 0.095f,
            center = center
        )

        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.63f,
                y = size.height * 0.24f
            ),
            size = androidx.compose.ui.geometry.Size(
                width = size.minDimension * 0.11f,
                height = size.minDimension * 0.11f
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.minDimension * 0.03f,
                size.minDimension * 0.03f
            )
        )

        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.28f,
                y = size.height * 0.73f
            ),
            end = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.72f,
                y = size.height * 0.73f
            ),
            strokeWidth = size.minDimension * 0.06f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
