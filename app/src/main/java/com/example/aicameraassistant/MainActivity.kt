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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
                    onBack = { currentScreen = "home" },
                    onConnect = { roomCode ->
                        pendingRoomCode = roomCode
                        scope.launch {
                            val exists = repository.sendConnectionRequest(roomCode)
                            if (exists) {
                                currentScreen = "waiting_for_approval"
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
                            repository.createRoom(cameraRoomCode)
                            currentScreen = "camera"
                        }
                    },
                    onControlCamera = {
                        if (pendingRoomCode.isNotBlank()) {
                            currentScreen = "waiting_for_approval"
                        } else {
                            currentScreen = "controller"
                        }
                    }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Requesting permissions...")
        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            modifier = Modifier.fillMaxWidth(),
            onClick = onControlCamera
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
