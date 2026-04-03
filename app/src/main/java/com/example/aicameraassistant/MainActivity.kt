package com.example.aicameraassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.example.aicameraassistant.ui.theme.AICameraAssistantTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AICameraAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()
                    val repository = remember { FirebaseRoomRepository() }

                    var currentScreen by remember { mutableStateOf("home") }
                    var pendingRoomCode by remember { mutableStateOf("") }
                    var cameraRoomCode by remember { mutableStateOf(generateRoomCode()) }

                    when (currentScreen) {
                        "camera" -> {
                            CameraScreen(
                                onBack = { currentScreen = "home" },
                                roomCode = cameraRoomCode
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
                                onBack = { currentScreen = "home" }
                            )
                        }

                        else -> {
                            HomeScreen(
                                onStartCamera = {
                                    currentScreen = "camera"
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
                }
            }
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