package com.example.aicameraassistant

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                    onBack = { currentScreen = "home" }
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
                    onBack = { currentScreen = "home" }
                )
            }

            else -> {
                HomeScreen(
                    onStartCamera = {
                        scope.launch {
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
        Text(
            text = "AI Camera Assistant",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onStartCamera,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text("Start Camera")
        }

        Button(
            onClick = onControlCamera,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Control Camera")
        }
    }
}
