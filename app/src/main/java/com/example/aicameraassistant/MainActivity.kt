package com.example.aicameraassistant

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.aicameraassistant.ui.theme.AICameraAssistantTheme
import kotlinx.coroutines.delay
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

    var currentScreen by rememberSaveable { mutableStateOf("splash") }
    var pendingRoomCode by rememberSaveable { mutableStateOf("") }
    var cameraRoomCode by rememberSaveable { mutableStateOf(generateRoomCode()) }
    var controlRoomCodeError by rememberSaveable { mutableStateOf<String?>(null) }
    
    var permissionsGranted by rememberSaveable { mutableStateOf(false) }
    var openHomeAfterPermissions by rememberSaveable { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted = perms.values.all { it }
        if (!permissionsGranted) {
            Toast.makeText(context, "Camera and Audio permissions are required", Toast.LENGTH_LONG).show()
        } else if (openHomeAfterPermissions) {
            openHomeAfterPermissions = false
            currentScreen = "home"
        }
    }

    fun continueFromWelcome() {
        if (permissionsGranted) {
            currentScreen = "home"
        } else {
            openHomeAfterPermissions = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 220)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 140))
        },
        label = "main_screen_transition"
    ) { screen ->
        when (screen) {
            "splash" -> {
                SplashScreen(
                    onFinished = { currentScreen = "welcome" }
                )
            }

            "welcome" -> {
                WelcomeScreen(
                    onContinueWithGoogle = ::continueFromWelcome,
                    onContinueWithApple = ::continueFromWelcome,
                    onContinueAsGuest = ::continueFromWelcome
                )
            }

            "camera" -> {
                if (permissionsGranted) {
                CameraScreen(
                    roomCode = cameraRoomCode,
                    repository = repository,
                    onBack = {
                        currentScreen = "home"
                        cameraRoomCode = generateRoomCode()
                    }
                )
                } else {
                    PermissionsLoadingScreen()
                }
            }

            "controller" -> {
                if (permissionsGranted) {
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
                } else {
                    PermissionsLoadingScreen()
                }
            }

            "waiting_for_approval" -> {
                if (permissionsGranted) {
                    WaitingForApprovalScreen(
                        roomCode = pendingRoomCode,
                        repository = repository,
                        onBack = {
                            currentScreen = "home"
                            pendingRoomCode = ""
                        }
                    )
                } else {
                    PermissionsLoadingScreen()
                }
            }

            else -> {
                if (permissionsGranted) {
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
                } else {
                    PermissionsLoadingScreen()
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
fun SplashScreen(onFinished: () -> Unit) {
    var contentVisible by remember { mutableStateOf(false) }
    var featureIndex by remember { mutableStateOf(0) }
    val featureCount = 5

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(featureIndex) {
        delay(1900)
        if (featureIndex < featureCount - 1) {
            featureIndex += 1
        } else {
            onFinished()
        }
    }

    fun moveFeature(delta: Int) {
        val nextIndex = featureIndex + delta
        when {
            nextIndex >= featureCount -> onFinished()
            nextIndex < 0 -> featureIndex = 0
            else -> featureIndex = nextIndex
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .pointerInput(featureIndex) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            totalDrag < -64f -> moveFeature(1)
                            totalDrag > 64f -> moveFeature(-1)
                        }
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
    ) {
        SplashCameraBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.52f))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(640)) + scaleIn(tween(640), initialScale = 0.84f)
            ) {
                WelcomePurpleCameraLogo(
                    modifier = Modifier.size(118.dp)
                )
            }

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 4 }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 430.dp)
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AI Camera Assistant",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Turn any phone into your remote camera controller",
                        color = Color(0xFFB8B8B8),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(760)) + slideInVertically(tween(760)) { it / 5 }
            ) {
                SplashFeatureShowcase(
                    featureIndex = featureIndex,
                    modifier = Modifier
                        .widthIn(max = 430.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(0.48f))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 430.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SplashSwipeIndicator(
                        featureIndex = featureIndex,
                        featureCount = featureCount
                    )
                    SplashNextButton(
                        text = "Next",
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashFeatureShowcase(
    featureIndex: Int,
    modifier: Modifier = Modifier
) {
    val features = remember {
        listOf(
            SplashFeatureItem(
                icon = "remote",
                title = "Remote Camera Control",
                subtitle = "Full control from your second device"
            ),
            SplashFeatureItem(
                icon = "record",
                title = "Remote Video Recording",
                subtitle = "Start and stop recording remotely"
            ),
            SplashFeatureItem(
                icon = "portrait",
                title = "Portrait Mode",
                subtitle = "Beautiful background blur remotely"
            ),
            SplashFeatureItem(
                icon = "hdrNight",
                title = "HDR Support",
                subtitle = "Better dynamic range and details"
            ),
            SplashFeatureItem(
                icon = "scene",
                title = "AI Scene Detection",
                subtitle = "Smart optimization for every scene"
            )
        )
    }
    val selectedFeature = features[featureIndex.coerceIn(features.indices)]

    AnimatedContent(
        targetState = selectedFeature,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 })
                .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(30)) { -it / 5 })
        },
        label = "SplashFeatureCard",
        modifier = modifier
    ) { feature ->
        SplashFeaturePill(
            icon = feature.icon,
            title = feature.title,
            subtitle = feature.subtitle,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class SplashFeatureItem(
    val icon: String,
    val title: String,
    val subtitle: String
)

@Composable
private fun SplashFeaturePill(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171821).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SplashFeatureIcon(icon = icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFFB8B8B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SplashFeatureIcon(icon: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when (icon) {
                    "portrait" -> Color(0xFFFFC845).copy(alpha = 0.82f)
                    "hdrNight" -> Color(0xFF21B66F).copy(alpha = 0.82f)
                    "scene" -> Color(0xFF2D7CFF).copy(alpha = 0.82f)
                    else -> Color(0xFF7C4DFF).copy(alpha = 0.82f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val white = Color.White
            val purple = Color(0xFFC7B6FF)
            when (icon) {
                "remote" -> {
                    drawRoundRect(
                        color = white,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.25f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.48f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                    drawCircle(
                        color = purple,
                        center = center,
                        radius = size.minDimension * 0.15f,
                        style = Stroke(width = size.minDimension * 0.07f)
                    )
                    drawLine(
                        color = purple,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.1f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.1f),
                        strokeWidth = size.minDimension * 0.07f
                    )
                }

                "record" -> {
                    drawRoundRect(
                        color = white,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.25f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.62f, size.height * 0.5f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                    val path = Path().apply {
                        moveTo(size.width * 0.72f, size.height * 0.42f)
                        lineTo(size.width * 0.94f, size.height * 0.3f)
                        lineTo(size.width * 0.94f, size.height * 0.7f)
                        lineTo(size.width * 0.72f, size.height * 0.58f)
                        close()
                    }
                    drawPath(path, white)
                    drawCircle(
                        color = Color(0xFFFF4F6D),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.5f),
                        radius = size.minDimension * 0.12f
                    )
                }

                "portrait" -> {
                    drawCircle(
                        color = purple,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.34f),
                        radius = size.minDimension * 0.16f,
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                    drawRoundRect(
                        color = white,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.56f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.25f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                    drawRoundRect(
                        color = purple,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.1f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f, 9f),
                        style = Stroke(width = size.minDimension * 0.045f)
                    )
                }

                "scene" -> {
                    drawRoundRect(
                        color = white,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                    drawCircle(
                        color = purple,
                        center = center,
                        radius = size.minDimension * 0.14f,
                        style = Stroke(width = size.minDimension * 0.07f)
                    )
                    drawLine(
                        color = white,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.08f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.22f),
                        strokeWidth = size.minDimension * 0.055f
                    )
                    drawLine(
                        color = white,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.78f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.92f),
                        strokeWidth = size.minDimension * 0.055f
                    )
                    drawLine(
                        color = white,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.5f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.5f),
                        strokeWidth = size.minDimension * 0.055f
                    )
                    drawLine(
                        color = white,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.5f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.5f),
                        strokeWidth = size.minDimension * 0.055f
                    )
                }

                else -> {
                    drawCircle(
                        color = purple,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.44f),
                        radius = size.minDimension * 0.24f
                    )
                    drawCircle(
                        color = Color(0xFF2F1B6E),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.54f, size.height * 0.36f),
                        radius = size.minDimension * 0.24f
                    )
                    drawLine(
                        color = white,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.76f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.76f),
                        strokeWidth = size.minDimension * 0.08f
                    )
                    drawCircle(
                        color = white,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.24f),
                        radius = size.minDimension * 0.055f
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashSwipeIndicator(
    featureIndex: Int,
    featureCount: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(featureCount) { index ->
                val selected = index == featureIndex
                Box(
                    modifier = Modifier
                        .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (selected) {
                                Color(0xFF7C4DFF)
                            } else {
                                Color.White.copy(alpha = 0.28f)
                            }
                        )
                )
            }
        }
        Text(
            text = if (featureIndex == featureCount - 1) {
                "Swipe left to continue"
            } else {
                "Swipe to explore"
            },
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SplashNextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF9B6DFF),
                        Color(0xFF7C4DFF),
                        Color(0xFF4A22B8)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$text >",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SplashCameraBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color(0xFF0A0A0A))
        val lensCenter = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.36f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7C4DFF).copy(alpha = 0.26f),
                    Color(0xFF1A1038).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = lensCenter,
                radius = size.minDimension * 0.64f
            ),
            center = lensCenter,
            radius = size.minDimension * 0.64f
        )
        repeat(7) { index ->
            drawCircle(
                color = Color.White.copy(alpha = 0.035f + index * 0.006f),
                center = lensCenter,
                radius = size.minDimension * (0.13f + index * 0.055f),
                style = Stroke(width = 1.1f)
            )
        }
        repeat(10) { index ->
            val y = size.height * (0.16f + index * 0.07f)
            drawLine(
                color = Color.White.copy(alpha = 0.018f),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, y),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, y + 12f),
                strokeWidth = 1f
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.34f),
                    Color.Black.copy(alpha = 0.88f)
                )
            )
        )
    }
}

@Composable
fun WelcomeScreen(
    onContinueWithGoogle: () -> Unit,
    onContinueWithApple: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    var logoVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var buttonsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(140)
        titleVisible = true
        delay(260)
        buttonsVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        WelcomeCameraScene(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.42f),
                            Color(0xFF0A0A0A).copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = logoVisible,
                enter = fadeIn(tween(620)) + scaleIn(
                    animationSpec = tween(620),
                    initialScale = 0.8f
                )
            ) {
                WelcomePurpleCameraLogo(
                    modifier = Modifier
                        .padding(top = 70.dp)
                        .size(72.dp)
                )
            }

            AnimatedVisibility(
                visible = titleVisible,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 430.dp)
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AI Camera",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Assistant",
                        style = MaterialTheme.typography.headlineSmall.merge(
                            TextStyle(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(0xFFE4DAFF),
                                        Color(0xFF7C4DFF)
                                    )
                                ),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    )
                    Text(
                        text = "Turn any phone into your\nremote camera controller",
                        color = Color.White.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(210.dp))

            AnimatedVisibility(
                visible = buttonsVisible,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 2 }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 430.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WelcomeButton(
                        text = "Continue with Google",
                        leading = "G",
                        primary = true,
                        onClick = onContinueWithGoogle
                    )
                    WelcomeButton(
                        text = "Continue with Apple",
                        leading = "A",
                        primary = true,
                        onClick = onContinueWithApple
                    )
                    WelcomeButton(
                        text = "Continue as Guest",
                        leading = "👤",
                        primary = false,
                        onClick = onContinueAsGuest
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "By continuing, you agree to our",
                            color = Color.White.copy(alpha = 0.48f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Terms of Service   Privacy Policy",
                            color = Color(0xFFC7B6FF),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeButton(
    text: String,
    leading: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val displayLeading = if (text == "Continue as Guest") "U" else leading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (primary) {
                    Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color(0xFFF2F0FA)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.58f),
                            Color.Black.copy(alpha = 0.36f)
                        )
                    )
                }
            )
            .border(
                1.dp,
                if (primary) Color.White.copy(alpha = 0.5f) else Color(0xFF7C4DFF).copy(alpha = 0.28f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (primary) Color.Transparent else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            when (text) {
                "Continue with Google" -> GoogleSignInIcon(modifier = Modifier.size(20.dp))
                "Continue with Apple" -> AppleSignInIcon(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF111116)
                )
                else -> Text(
                    text = displayLeading,
                    color = if (primary) Color(0xFF111116) else Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            text = text,
            color = if (primary) Color(0xFF111116) else Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "→",
            color = if (primary) Color(0xFF111116).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.64f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GoogleSignInIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        val radius = size.minDimension * 0.36f
        val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -30f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 66f,
            sweepAngle = 88f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 154f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 230f,
            sweepAngle = 94f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(center.x + size.width * 0.02f, center.y),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.86f, center.y),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Square
        )
    }
}

@Composable
private fun AppleSignInIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val apple = Path().apply {
            moveTo(w * 0.52f, h * 0.28f)
            cubicTo(w * 0.58f, h * 0.18f, w * 0.66f, h * 0.14f, w * 0.75f, h * 0.14f)
            cubicTo(w * 0.72f, h * 0.24f, w * 0.66f, h * 0.31f, w * 0.57f, h * 0.34f)
            cubicTo(w * 0.48f, h * 0.29f, w * 0.38f, h * 0.29f, w * 0.3f, h * 0.36f)
            cubicTo(w * 0.16f, h * 0.49f, w * 0.2f, h * 0.77f, w * 0.36f, h * 0.88f)
            cubicTo(w * 0.43f, h * 0.93f, w * 0.49f, h * 0.88f, w * 0.55f, h * 0.88f)
            cubicTo(w * 0.62f, h * 0.88f, w * 0.68f, h * 0.94f, w * 0.76f, h * 0.87f)
            cubicTo(w * 0.84f, h * 0.8f, w * 0.9f, h * 0.67f, w * 0.86f, h * 0.55f)
            cubicTo(w * 0.75f, h * 0.52f, w * 0.72f, h * 0.38f, w * 0.82f, h * 0.31f)
            cubicTo(w * 0.72f, h * 0.2f, w * 0.61f, h * 0.22f, w * 0.52f, h * 0.28f)
            close()
        }
        drawPath(apple, color)
        val leaf = Path().apply {
            moveTo(w * 0.55f, h * 0.2f)
            cubicTo(w * 0.58f, h * 0.08f, w * 0.68f, h * 0.03f, w * 0.76f, h * 0.03f)
            cubicTo(w * 0.76f, h * 0.15f, w * 0.68f, h * 0.23f, w * 0.57f, h * 0.25f)
            close()
        }
        drawPath(leaf, color)
    }
}

@Composable
fun WelcomePurpleCameraLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val corner = size.minDimension * 0.25f
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.86f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 1.08f, corner * 1.08f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7C4DFF).copy(alpha = 0.48f),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension * 0.72f
            ),
            center = center,
            radius = size.minDimension * 0.72f
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE6DDFF),
                    Color(0xFFB89CFF),
                    Color(0xFF7C4DFF),
                    Color(0xFF4D26C8)
                )
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.06f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.88f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.minDimension * 0.22f,
                size.minDimension * 0.22f
            ),
            style = Stroke(width = size.minDimension * 0.045f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.96f),
            center = center,
            radius = size.minDimension * 0.19f,
            style = Stroke(width = size.minDimension * 0.052f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.34f),
            center = center,
            radius = size.minDimension * 0.105f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            center = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.29f),
            radius = size.minDimension * 0.055f
        )
    }
}

@Composable
private fun WelcomeCameraScene(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFF0A0A0A))) {
        Image(
            painter = painterResource(id = R.drawable.welcome_camera_wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF321B6E).copy(alpha = 0.44f),
                            Color(0xFF1A1038).copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF7C4DFF).copy(alpha = 0.14f))
        )
    }
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
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WelcomePurpleCameraLogo(
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .size(112.dp)
            )

            Text(
                text = "AI Camera",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Assistant",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFF9B6DFF), Color(0xFF7C4DFF))
                    )
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Professional remote camera control\nfrom anywhere",
                color = Color(0xFFB8B8B8),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 26.dp)
            )

            HomeActionButton(
                icon = "remote",
                title = "Start Camera",
                subtitle = "Use this phone as the live camera",
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF21143D).copy(alpha = 0.92f),
                        Color(0xFF14151F).copy(alpha = 0.96f)
                    )
                ),
                borderColor = Color(0xFF7C4DFF),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                onClick = onStartCamera
            )

            HomeActionButton(
                icon = "scene",
                title = "Control Camera",
                subtitle = "Connect and control another phone",
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF171821).copy(alpha = 0.94f),
                        Color(0xFF0F1018).copy(alpha = 0.98f)
                    )
                ),
                borderColor = Color(0xFF9B6DFF),
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
                    Color(0xFF030305),
                    Color(0xFF0A0A0A),
                    Color(0xFF080712)
                )
            )
        )

        val gridColor = Color.White.copy(alpha = 0.024f)
        val step = size.minDimension / 6f
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
                    Color(0xFF7C4DFF).copy(alpha = 0.32f),
                    Color(0xFF251653).copy(alpha = 0.22f),
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
                color = Color.White.copy(alpha = 0.034f + (index * 0.01f)),
                radius = size.minDimension * (0.12f + index * 0.07f),
                center = lensCenter,
                style = Stroke(width = 1.2f)
            )
        }

        val warmCenter = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.8f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF9B6DFF).copy(alpha = 0.24f),
                    Color(0xFF7C4DFF).copy(alpha = 0.1f),
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
    icon: String,
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
            .border(1.dp, borderColor.copy(alpha = 0.62f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SplashFeatureIcon(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
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
                    color = Color(0xFFB8B8B8),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = ">",
                color = Color(0xFF9B6DFF),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
