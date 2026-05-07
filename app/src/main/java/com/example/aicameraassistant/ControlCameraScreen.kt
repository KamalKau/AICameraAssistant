package com.example.aicameraassistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlCameraScreen(
    errorMessage: String? = null,
    onCodeChanged: () -> Unit = {},
    onBack: () -> Unit,
    onConnect: (String) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
    val cleanedRoomCode = roomCode.trim().uppercase()
    val canConnect = cleanedRoomCode.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pulse by rememberInfiniteTransition(label = "control_camera_pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "control_camera_signal_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        HomeWallpaper(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF132653).copy(alpha = 0.88f),
                            Color(0xFF1C68E8).copy(alpha = 0.82f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.22f),
                    RoundedCornerShape(20.dp)
                )
                .clickable(onClick = onBack)
                .padding(start = 7.dp, top = 7.dp, end = 13.dp, bottom = 7.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f + (pulse * 0.08f))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Back",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFF82B4FF).copy(alpha = 0.85f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1B2B59),
                                    Color(0xFF2D7CFF)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((24 + (pulse * 16)).dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Control Camera",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter the camera phone room code",
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = roomCode,
                            onValueChange = { value ->
                                onCodeChanged()
                                val nextCode = value
                                    .uppercase()
                                    .filter { it.isLetterOrDigit() }
                                    .take(5)
                                roomCode = nextCode
                                if (nextCode.length == 5) {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = Color.Transparent
                            ),
                            modifier = Modifier
                                .size(width = 1.dp, height = 1.dp)
                                .focusRequester(focusRequester)
                        )
                        RoomCodePreview(
                            code = cleanedRoomCode,
                            modifier = Modifier.clickable {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFFC7A7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (canConnect) {
                                onConnect(cleanedRoomCode)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = canConnect,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1B2B59),
                            disabledContainerColor = Color.White.copy(alpha = 0.18f),
                            disabledContentColor = Color.White.copy(alpha = 0.32f)
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Connect",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "The camera phone must be open and waiting for controller approval.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
private fun RoomCodePreview(
    code: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val char = code.getOrNull(index)?.toString() ?: ""
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 46.dp)
                    .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (char.isNotEmpty()) Color.White.copy(alpha = 0.82f)
                        else Color.White.copy(alpha = 0.24f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
