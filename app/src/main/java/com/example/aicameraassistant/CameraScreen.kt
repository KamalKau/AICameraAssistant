package com.example.aicameraassistant

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaActionSound
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.ExperimentalPreviewViewScreenFlash
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import org.webrtc.IceCandidate

@ExperimentalPreviewViewScreenFlash
@Composable
fun CameraScreen(
    roomCode: String,
    repository: FirebaseRoomRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val screenViewModel: CameraScreenViewModel = viewModel()
    LaunchedEffect(roomCode) {
        screenViewModel.bind(repository, roomCode)
    }
    val remoteUiState by screenViewModel.remoteUiState.collectAsState()
    val roomStatus = remoteUiState.roomStatus
    val connectionState = remoteUiState.connectionState
    val firebaseLensFacing = remoteUiState.lensFacing
    val firebaseZoomLevel = remoteUiState.zoomLevel
    val firebaseFlashMode = remoteUiState.flashMode
    val firebaseCameraMode = remoteUiState.cameraMode
    val firebaseAspectRatioMode = remoteUiState.aspectRatioMode
    val firebasePortraitBlurLevel = remoteUiState.portraitBlurLevel
    val firebasePortraitStrength = remoteUiState.portraitStrength
    val firebasePortraitEffect = remoteUiState.portraitEffect
    val firebasePortraitStatus = remoteUiState.portraitStatus
    val firebasePortraitFaceLeft = remoteUiState.portraitFaceLeft
    val firebasePortraitFaceTop = remoteUiState.portraitFaceTop
    val firebasePortraitFaceRight = remoteUiState.portraitFaceRight
    val firebasePortraitFaceBottom = remoteUiState.portraitFaceBottom
    val firebaseFaceBoxes = remoteUiState.faceBoxes
    val firebaseSceneDetection = remoteUiState.sceneDetection
    val firebaseSceneDetectionEnabled = remoteUiState.sceneDetectionEnabled
    val firebaseGridEnabled = remoteUiState.gridEnabled
    val firebaseNightModeEnabled = remoteUiState.nightModeEnabled
    val firebaseVideoHdrSupported = remoteUiState.videoHdrSupported
    val firebaseVideoHdrEnabled = remoteUiState.videoHdrEnabled
    val firebaseToolbarExpanded = remoteUiState.toolbarExpanded
    val firebaseCaptureRequestId = remoteUiState.captureRequestId
    val firebaseCaptureRequestType = remoteUiState.captureRequestType
    val firebaseRequestReceived = remoteUiState.requestReceived
    val firebaseControllerApproved = remoteUiState.controllerApproved
    val firebaseFocusRequestId = remoteUiState.focusRequestId
    val firebaseFocusPointX = remoteUiState.focusPointX
    val firebaseFocusLockEnabled = remoteUiState.focusLockEnabled
    val firebaseFocusPointY = remoteUiState.focusPointY
    val firebaseExposureIndex = remoteUiState.exposureIndex
    val offerSdp = remoteUiState.offerSdp
    val firebaseRtcSessionId = remoteUiState.rtcSessionId
    val firebaseSessionVersion = remoteUiState.sessionVersion

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lastGestureZoomPublishMs by remember { mutableLongStateOf(0L) }
    var lastGestureZoomPublishedRatio by remember { mutableFloatStateOf(Float.NaN) }
    var lastGestureExposurePublishMs by remember { mutableLongStateOf(0L) }
    var lastGestureExposurePublishedIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val lensFadeAlpha = remember { Animatable(0f) }
    val modeSlideProgress = remember { Animatable(0f) }
    var hasSeenLensTransitionKey by remember { mutableStateOf(false) }
    var lastModeTransitionKey by remember { mutableStateOf(firebaseCameraMode) }
    var modeSlideDirection by remember { mutableFloatStateOf(1f) }
    var cameraPreviewReady by remember { mutableStateOf(false) }
    var cameraStartupFailed by remember { mutableStateOf(false) }

    var resolvedWidth by remember { mutableIntStateOf(0) }
    var resolvedHeight by remember { mutableIntStateOf(0) }
    var webRtcSourceReady by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    LaunchedEffect(firebaseAspectRatioMode) {
        previewView.scaleType = if (AspectRatioMode.fromKey(firebaseAspectRatioMode) == AspectRatioMode.Full) {
            PreviewView.ScaleType.FIT_CENTER
        } else {
            PreviewView.ScaleType.FILL_CENTER
        }
    }
    val activity = context.findActivity()

    var flashAlpha by screenViewModel::flashAlpha
    val isStreaming = roomStatus == "connected"
    var answerCreated by screenViewModel::answerCreated
    var hasSeenConnectedState by screenViewModel::hasSeenConnectedState
    var isEndingSession by screenViewModel::isEndingSession
    var focusPoint by screenViewModel::focusPoint
    var focusSucceeded by screenViewModel::focusSucceeded
    var focusLocked by screenViewModel::focusLocked
    var focusUiToken by screenViewModel::focusUiToken
    var lastAppliedRemoteFocusRequestId by screenViewModel::lastAppliedRemoteFocusRequestId
    var lastHandledCaptureRequestId by screenViewModel::lastHandledCaptureRequestId
    var exposureMinIndex by screenViewModel::exposureMinIndex
    var exposureMaxIndex by screenViewModel::exposureMaxIndex
    var exposureIndex by screenViewModel::exposureIndex
    var showManualBrightnessControl by screenViewModel::showManualBrightnessControl
    var manualExposureProgressOverride by screenViewModel::manualExposureProgressOverride
    var boomerangInProgress by screenViewModel::boomerangInProgress
    var captureMode by screenViewModel::captureMode
    var videoRecordingState by screenViewModel::videoRecordingState
    val videoRecorder = remember(context) { CameraVideoRecorder(context) }
    val videoFrameSource = remember { WebRtcImageFrameSource() }
    var selfieLightVisible by remember { mutableStateOf(false) }
    var restartRecordingAfterCameraBind by remember { mutableStateOf(false) }
    var nightAssistInProgress by remember { mutableStateOf(false) }
    var lastPortraitFacePublishMs by remember { mutableStateOf(0L) }
    var lastPortraitStatus by remember { mutableStateOf("Finding subject...") }
    var lastPortraitFaceBounds by remember { mutableStateOf(PortraitFaceBounds()) }
    var photoFaceBounds by remember { mutableStateOf(emptyList<PortraitFaceBounds>()) }
    var photoFaceBoxVisible by remember { mutableStateOf(false) }
    var photoFaceBoxToken by remember { mutableLongStateOf(0L) }
    var pendingPhotoFaceBounds by remember { mutableStateOf(emptyList<PortraitFaceBounds>()) }
    var consecutivePhotoFaceHits by remember { mutableIntStateOf(0) }
    var consecutivePhotoFaceMisses by remember { mutableIntStateOf(0) }
    var lastPhotoFaceSeenMs by remember { mutableLongStateOf(0L) }
    var lastPhotoFaceMeteringMs by remember { mutableStateOf(0L) }
    var lastMeteredPhotoFaceBounds by remember { mutableStateOf(PortraitFaceBounds()) }
    var lastScenePublishMs by remember { mutableLongStateOf(0L) }
    var lastSceneKey by remember { mutableStateOf("auto") }
    var lastNightAutoAppliedMs by remember { mutableLongStateOf(0L) }
    var cameraAnalysisActive by remember { mutableStateOf(false) }
    var lastCameraAnalysisResultMs by remember { mutableLongStateOf(0L) }
    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.08f)
                .enableTracking()
                .build()
            )
    }
    val sceneAnalyzer = remember { SceneDetectionAnalyzer() }
    val faceAnalysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val faceBoundsMapper = remember { FaceBoundsMapper() }
    val faceOverlayPublisher = remember(roomCode, repository, scope) {
        FaceOverlayPublisher(repository = repository, roomCode = roomCode, scope = scope)
    }
    val previewBitmapFaceDetector = remember(faceDetector, resolvedWidth, resolvedHeight) {
        PreviewBitmapFaceDetector(
            detector = faceDetector,
            previewRectProvider = { bitmap ->
                fittedPreviewRect(
                    containerWidth = bitmap.width.toFloat(),
                    containerHeight = bitmap.height.toFloat(),
                    contentWidth = resolvedWidth.toFloat(),
                    contentHeight = resolvedHeight.toFloat()
                )
            }
        )
    }

    val pendingCandidates = remember { mutableListOf<IceCandidate>() }
    var isRemoteDescriptionSet by screenViewModel::isRemoteDescriptionSet
    var lastHandledOfferSessionId by screenViewModel::lastHandledOfferSessionId
    val sessionIsActive = roomStatus == "connected"
    val isPortraitMode = firebaseCameraMode == "portrait"
    val hasLedFlash = camera?.cameraInfo?.hasFlashUnit() == true
    val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
    val flashSupported = hasLedFlash || lensFacing == CameraSelector.LENS_FACING_FRONT
    val nightModeExposurePolicy = remember { NightModeExposurePolicy() }
    val shutterSound = remember {
        MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
        }
    }
    fun playShutterClick() {
        runCatching { shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
    }
    val exposureUiState = buildExposureUiState(
        minIndex = exposureMinIndex,
        maxIndex = exposureMaxIndex,
        currentIndex = firebaseExposureIndex,
        manualProgressOverride = manualExposureProgressOverride,
        visible = showManualBrightnessControl
    )
    val selfieLightAlpha by animateFloatAsState(
        targetValue = if (selfieLightVisible && isFrontCamera) 1f else 0f,
        label = "selfie_light_alpha"
    )
    val captureFlashMode = when {
        lensFacing == CameraSelector.LENS_FACING_FRONT -> ImageCapture.FLASH_MODE_OFF
        firebaseFlashMode == "on" -> ImageCapture.FLASH_MODE_ON
        firebaseFlashMode == "auto" -> ImageCapture.FLASH_MODE_AUTO
        else -> ImageCapture.FLASH_MODE_OFF
    }
    val hostCoordinator = remember(roomCode, repository, scope, context, onBack) {
        HostSessionCoordinator(
            repository = repository,
            roomCode = roomCode,
            scope = scope,
            context = context,
            onExit = onBack
        )
    }
    fun launchRoomWrite(operation: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { Log.w("AICameraAssistant", "Room write failed during $operation", it) }
        }
    }
    val hostTopOverlayUiState = buildHostTopOverlayUiState(
        roomCode = roomCode,
        roomStatus = roomStatus,
        connectionState = connectionState,
        sessionIsActive = sessionIsActive,
        requestReceived = firebaseRequestReceived,
        controllerApproved = firebaseControllerApproved,
        isEndingSession = isEndingSession
    )
    val hostToolRailUiState = buildCameraToolRailUiState(
        flashSupported = flashSupported,
        flashMode = firebaseFlashMode,
        lensFacing = firebaseLensFacing,
        aspectRatioMode = firebaseAspectRatioMode,
        sceneDetectionEnabled = firebaseSceneDetectionEnabled,
        gridEnabled = firebaseGridEnabled,
        nightModeEnabled = firebaseNightModeEnabled,
        videoHdrSupported = firebaseVideoHdrSupported,
        videoHdrEnabled = firebaseVideoHdrEnabled,
        cameraMode = firebaseCameraMode,
        toolbarExpanded = firebaseToolbarExpanded,
        boomerangSelected = captureMode == "boomerang",
        exposureSupported = exposureUiState.supported
    )
    val hostExposureUiActions = ExposureUiActions(
        onToggle = {
            if (exposureUiState.supported) {
                if (!exposureUiState.visible) {
                    manualExposureProgressOverride = exposureUiState.remoteProgress
                }
                showManualBrightnessControl = !showManualBrightnessControl
                if (!showManualBrightnessControl) {
                    manualExposureProgressOverride = null
                }
            }
        },
        onProgressChange = { progress ->
            manualExposureProgressOverride = progress.coerceIn(0f, 1f)
            hostCoordinator.updateExposureFromProgress(
                progress = progress,
                exposureMinIndex = exposureMinIndex,
                exposureMaxIndex = exposureMaxIndex,
                currentExposureIndex = firebaseExposureIndex,
                onUiPulse = { focusUiToken++ }
            )
        },
        onDismiss = {
            showManualBrightnessControl = false
            manualExposureProgressOverride = null
        },
        onReset = {
            val defaultProgress = defaultExposureProgress(
                minIndex = exposureMinIndex,
                maxIndex = exposureMaxIndex
            )
            manualExposureProgressOverride = defaultProgress
            hostCoordinator.updateExposureFromProgress(
                progress = defaultProgress,
                exposureMinIndex = exposureMinIndex,
                exposureMaxIndex = exposureMaxIndex,
                currentExposureIndex = firebaseExposureIndex,
                onUiPulse = { focusUiToken++ }
            )
        }
    )
    val hostToolRailActions = CameraToolRailActions(
        onFlashClick = {
            hostCoordinator.updateFlashMode(firebaseFlashMode, flashSupported)
        },
        onBoomerangClick = {
            val selectingBoomerang = captureMode != "boomerang"
            captureMode = if (selectingBoomerang) "boomerang" else "photo"
            if (selectingBoomerang && firebaseCameraMode != "photo") {
                launchRoomWrite("boomerang mode reset") {
                    repository.updateCameraMode(roomCode, "photo")
                }
            }
        },
        onAspectRatioClick = {
            launchRoomWrite("aspect ratio update") {
                repository.updateAspectRatioMode(
                    roomCode,
                    AspectRatioMode.next(firebaseAspectRatioMode).key
                )
            }
        },
        onLensClick = {
            hostCoordinator.switchLens(firebaseLensFacing)
        },
        onGridClick = {
            hostCoordinator.updateGridEnabled(firebaseGridEnabled)
        },
        onSceneDetectionClick = {
            hostCoordinator.updateSceneDetectionEnabled(firebaseSceneDetectionEnabled)
        },
        onNightModeClick = {
            hostCoordinator.updateNightModeEnabled(firebaseNightModeEnabled)
        },
        onVideoHdrClick = {
            hostCoordinator.updateVideoHdrEnabled(firebaseVideoHdrEnabled, firebaseVideoHdrSupported)
        },
        onExposureClick = hostExposureUiActions.onToggle,
        onToolbarExpandedChange = { expanded ->
            hostCoordinator.updateToolbarExpanded(expanded)
        }
    )
    val hostTopOverlayActions = HostTopOverlayActions(
        onEndSession = {
            hostCoordinator.endSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                sessionVersion = firebaseSessionVersion
            )
        },
        onAllowController = { hostCoordinator.updateApproval(true) },
        onDenyController = { hostCoordinator.updateApproval(false) }
    )

    fun triggerTapToFocus(tapOffset: Offset, lockFocus: Boolean = false) {
        if (!sessionIsActive) return

        focusPoint = tapOffset
        focusSucceeded = null
        focusLocked = lockFocus
        focusUiToken++

        val currentCamera = camera ?: return
        val action = FocusMeteringAction.Builder(
            previewView.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y),
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        )
            .apply {
                if (!lockFocus) {
                    setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            .build()

        runCatching {
            val future = currentCamera.cameraControl.startFocusAndMetering(action)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result -> focusSucceeded = result.isFocusSuccessful }
                        .onFailure {
                            Log.w("CAMERA_FOCUS", "Tap to focus result failed", it)
                            focusSucceeded = false
                        }
                },
                ContextCompat.getMainExecutor(context)
            )
        }.onFailure {
            Log.w("CAMERA_FOCUS", "Tap to focus request failed", it)
            focusSucceeded = false
        }
    }

    fun triggerRemoteTapToFocus(normalizedX: Double, normalizedY: Double, lockFocus: Boolean) {
        val width = previewView.width.toFloat()
        val height = previewView.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val previewRect =
            fittedPreviewRect(
                containerWidth = width,
                containerHeight = height,
                contentWidth = resolvedWidth.toFloat(),
                contentHeight = resolvedHeight.toFloat()
            ) ?: Rect(0f, 0f, width, height)
        val mappedNormalizedX =
            if (isFrontCamera) {
                1.0 - normalizedX.coerceIn(0.0, 1.0)
            } else {
                normalizedX.coerceIn(0.0, 1.0)
            }
        val mappedNormalizedY = normalizedY.coerceIn(0.0, 1.0)

        triggerTapToFocus(
            Offset(
                x = previewRect.left + (mappedNormalizedX.toFloat() * previewRect.width),
                y = previewRect.top + (mappedNormalizedY.toFloat() * previewRect.height)
            ).clampOffsetTo(previewRect),
            lockFocus = lockFocus
        )
    }

    fun updateExposureFromProgress(progress: Float) {
        if (exposureMinIndex == exposureMaxIndex) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        manualExposureProgressOverride = clampedProgress
        val targetIndex = (
            exposureMinIndex +
                ((1f - clampedProgress) * (exposureMaxIndex - exposureMinIndex))
            ).roundToInt().coerceIn(exposureMinIndex, exposureMaxIndex)
        val previousExposureIndex = exposureIndex

        if (targetIndex != previousExposureIndex) {
            exposureIndex = targetIndex
            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
        }

        val now = System.currentTimeMillis()
        val shouldPublish =
            targetIndex != lastGestureExposurePublishedIndex &&
                (targetIndex != previousExposureIndex || now - lastGestureExposurePublishMs >= 120L)
        if (shouldPublish) {
            lastGestureExposurePublishMs = now
            lastGestureExposurePublishedIndex = targetIndex
            hostCoordinator.updateExposureFromProgress(
                progress = clampedProgress,
                exposureMinIndex = exposureMinIndex,
                exposureMaxIndex = exposureMaxIndex,
                currentExposureIndex = previousExposureIndex,
                onUiPulse = {}
            )
        }
    }

    fun applyGestureZoom(zoomRatio: Float, forcePublish: Boolean = false) {
        val currentCamera = camera ?: return
        val zoomState = currentCamera.cameraInfo.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1f
        val maxZoom = zoomState?.maxZoomRatio ?: 1f
        val clampedZoom = zoomRatio.coerceIn(minZoom, maxZoom)

        currentCamera.cameraControl.setZoomRatio(clampedZoom)

        val now = System.currentTimeMillis()
        val publishDue = forcePublish || now - lastGestureZoomPublishMs >= 90L
        val publishDistanceDue =
            !lastGestureZoomPublishedRatio.isFinite() ||
                abs(lastGestureZoomPublishedRatio - clampedZoom) >= 0.04f
        if (publishDue || publishDistanceDue) {
            lastGestureZoomPublishMs = now
            lastGestureZoomPublishedRatio = clampedZoom
            launchRoomWrite("gesture zoom update") {
                repository.updateZoomLevel(roomCode, clampedZoom.toDouble())
            }
        }
    }

    fun updateCameraMode(mode: String) {
        if (mode == firebaseCameraMode) return
        launchRoomWrite("camera mode update") {
            repository.updateCameraMode(roomCode, mode)
        }
    }

    fun updateCameraModeFromSwipe(deltaX: Float) {
        val modes = listOf("video", "portrait", "photo")
        val currentIndex = modes.indexOf(firebaseCameraMode).takeIf { it >= 0 } ?: modes.lastIndex
        val nextIndex = (currentIndex + if (deltaX < 0f) 1 else -1).coerceIn(0, modes.lastIndex)
        if (nextIndex != currentIndex) {
            updateCameraMode(modes[nextIndex])
        }
    }

    fun publishExposureState(activeCamera: Camera) {
        val exposureState = activeCamera.cameraInfo.exposureState
        exposureMinIndex = exposureState.exposureCompensationRange.lower
        exposureMaxIndex = exposureState.exposureCompensationRange.upper
        exposureIndex = exposureState.exposureCompensationIndex
        launchRoomWrite("exposure state publish") {
            repository.updateExposureState(
                roomCode = roomCode,
                minIndex = exposureMinIndex,
                maxIndex = exposureMaxIndex,
                currentIndex = exposureIndex
            )
        }
    }

    DisposableEffect(roomCode, firebaseRtcSessionId) {
        val activeRtcSessionId = firebaseRtcSessionId
        if (activeRtcSessionId == null) {
            onDispose { }
        } else {
            val registration = repository.listenToControllerIceCandidates(
                roomCode = roomCode,
                rtcSessionId = activeRtcSessionId
            ) { candidate ->
                if (isEndingSession || roomStatus != "connected") return@listenToControllerIceCandidates
                val pc = WebRtcSessionManager.cameraPeerConnection
                if (isRemoteDescriptionSet && pc != null) {
                    runCatching { pc.addIceCandidate(candidate) }
                        .onFailure { Log.w("WEBRTC_LOG", "Camera ignored late ICE candidate", it) }
                } else {
                    pendingCandidates.add(candidate)
                }
            }
            onDispose { registration.remove() }
        }
    }

    fun publishPortraitSubjectState(
        status: String,
        bounds: PortraitFaceBounds,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPortraitFacePublishMs < 350L) return
        if (!force && status == lastPortraitStatus && bounds.nearlyEquals(lastPortraitFaceBounds)) return

        lastPortraitFacePublishMs = now
        lastPortraitStatus = status
        lastPortraitFaceBounds = bounds
        launchRoomWrite("portrait subject publish") {
            repository.updatePortraitSubjectState(
                roomCode = roomCode,
                status = status,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }
    }

    fun publishFaceDetectionOverlay(
        detected: Boolean,
        bounds: PortraitFaceBounds = PortraitFaceBounds(),
        force: Boolean = false
    ) {
        faceOverlayPublisher.publish(detected = detected, bounds = bounds, force = force)
    }

    fun publishSceneDetection(
        result: SceneDetectionResult,
        force: Boolean = false,
        autoAdjustment: String = ""
    ) {
        val now = System.currentTimeMillis()
        val shouldPublish =
            force ||
                result.key != lastSceneKey ||
                now - lastScenePublishMs >= 1400L
        if (!shouldPublish) return

        lastScenePublishMs = now
        lastSceneKey = result.key
        launchRoomWrite("scene detection publish") {
            repository.updateSceneDetectionState(
                roomCode = roomCode,
                state = result.copy(timestamp = now).toState(autoAdjustment = autoAdjustment)
            )
        }
    }

    fun applyPhotoFaceMetering(bounds: PortraitFaceBounds) {
        if (isPortraitMode || !bounds.isValid()) return
        val currentCamera = camera ?: return
        val width = previewView.width.toFloat()
        val height = previewView.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val now = System.currentTimeMillis()
        val elapsedSinceLastMeteringMs = now - lastPhotoFaceMeteringMs
        val facePositionUnchanged = bounds.nearlyEquals(lastMeteredPhotoFaceBounds)
        if (elapsedSinceLastMeteringMs < 1200L) {
            return
        }
        if (facePositionUnchanged && elapsedSinceLastMeteringMs < 2400L) {
            return
        }

        lastPhotoFaceMeteringMs = now
        lastMeteredPhotoFaceBounds = bounds
        val centerX = (((bounds.left + bounds.right) / 2.0).toFloat() * width).coerceIn(0f, width)
        val centerY = (((bounds.top + bounds.bottom) / 2.0).toFloat() * height).coerceIn(0f, height)
        val action = FocusMeteringAction.Builder(
            previewView.meteringPointFactory.createPoint(centerX, centerY),
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        )
            .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        runCatching {
            currentCamera.cameraControl.startFocusAndMetering(action)
        }.onFailure {
            Log.w("PHOTO_FACE", "Face metering request failed", it)
        }
    }

    fun clearPhotoFaceDetection() {
        photoFaceBounds = emptyList()
        photoFaceBoxVisible = false
        pendingPhotoFaceBounds = emptyList()
        consecutivePhotoFaceHits = 0
        consecutivePhotoFaceMisses = 0
        lastPhotoFaceSeenMs = 0L
        publishFaceDetectionOverlay(detected = false, force = true)
    }

    fun registerPhotoFaceMiss() {
        consecutivePhotoFaceHits = 0
        pendingPhotoFaceBounds = emptyList()
        consecutivePhotoFaceMisses += 1
        val now = System.currentTimeMillis()
        if (lastPhotoFaceSeenMs > 0L && now - lastPhotoFaceSeenMs >= 700L) {
            photoFaceBounds = emptyList()
            photoFaceBoxVisible = false
            publishFaceDetectionOverlay(detected = false)
        } else if (consecutivePhotoFaceMisses >= 2 && lastPhotoFaceSeenMs == 0L) {
            photoFaceBounds = emptyList()
            photoFaceBoxVisible = false
        }
    }

    fun acceptPhotoFaceCandidate(bounds: List<PortraitFaceBounds>) {
        val plausibleBounds = bounds.filter { it.isPlausiblePhotoFace() }
        if (plausibleBounds.isEmpty()) {
            registerPhotoFaceMiss()
            return
        }

        consecutivePhotoFaceMisses = 0
        lastPhotoFaceSeenMs = System.currentTimeMillis()
        consecutivePhotoFaceHits =
            if (plausibleBounds.isStableCandidateAfter(pendingPhotoFaceBounds)) {
                consecutivePhotoFaceHits + 1
            } else {
                1
            }
        pendingPhotoFaceBounds = plausibleBounds

        if (consecutivePhotoFaceHits >= 1) {
            val shouldShowBox =
                photoFaceBounds.isEmpty() || plausibleBounds.haveMovedSignificantlyFrom(photoFaceBounds)
            photoFaceBounds = plausibleBounds
            if (shouldShowBox) {
                photoFaceBoxVisible = true
                val pulseTimestamp = System.currentTimeMillis()
                photoFaceBoxToken = pulseTimestamp
                faceOverlayPublisher.publish(detected = true, bounds = plausibleBounds, force = true)
            }
            plausibleBounds.firstOrNull()?.let { applyPhotoFaceMetering(it) }
        }
    }

    suspend fun detectPreviewBitmapFace(): List<NormalizedFaceBounds> {
        val bitmap = previewView.bitmap ?: return emptyList()
        return previewBitmapFaceDetector.detect(bitmap)
    }

    fun handlePreviewFaces(bounds: List<PortraitFaceBounds>) {
        if (bounds.isEmpty()) {
            if (isPortraitMode) {
                publishPortraitSubjectState(
                    status = "Finding subject...",
                    bounds = PortraitFaceBounds()
                )
                val now = System.currentTimeMillis()
                if (lastPhotoFaceSeenMs > 0L && now - lastPhotoFaceSeenMs >= 700L) {
                    publishFaceDetectionOverlay(detected = false)
                }
            } else {
                registerPhotoFaceMiss()
            }
            return
        }

        lastPhotoFaceSeenMs = System.currentTimeMillis()
        publishSceneDetection(
            result = sceneDetectionResult("face", 0.92),
            force = lastSceneKey != "face",
            autoAdjustment = "Face metering"
        )
        val primaryBounds = bounds.first()
        if (isPortraitMode) {
            val status = if (primaryBounds.area < 0.035) {
                "Move closer"
            } else {
                "Portrait ready"
            }
            faceOverlayPublisher.publish(detected = true, bounds = bounds, force = false)
            publishPortraitSubjectState(
                status = status,
                bounds = primaryBounds
            )
        } else {
            acceptPhotoFaceCandidate(bounds)
        }
    }

    fun handleAnalyzedFaces(bounds: List<NormalizedFaceBounds>) {
        handlePreviewFaces(faceBoundsMapper.mapAnalysisBoundsToPreview(bounds, isFrontCamera))
    }

    val currentFaceResultHandler by rememberUpdatedState<(List<NormalizedFaceBounds>) -> Unit>(
        newValue = { bounds -> handleAnalyzedFaces(bounds) }
    )
    val currentSceneResultHandler by rememberUpdatedState<(SceneDetectionResult) -> Unit>(
        newValue = { result ->
            if (!firebaseSceneDetectionEnabled) return@rememberUpdatedState
            val now = System.currentTimeMillis()
            if (lastPhotoFaceSeenMs > 0L && now - lastPhotoFaceSeenMs < 900L) {
                publishSceneDetection(
                    result = sceneDetectionResult("face", 0.92),
                    autoAdjustment = "Face metering"
                )
            } else {
                publishSceneDetection(result)
            }
        }
    )
    val currentPreviewFaceResultHandler by rememberUpdatedState<(List<NormalizedFaceBounds>) -> Unit>(
        newValue = { bounds -> handlePreviewFaces(faceBoundsMapper.mapPreviewBounds(bounds)) }
    )

    LaunchedEffect(photoFaceBoxToken) {
        if (photoFaceBoxToken == 0L) return@LaunchedEffect
        delay(1500L)
        photoFaceBoxVisible = false
        publishFaceDetectionOverlay(detected = false, force = true)
    }

    LaunchedEffect(isStreaming, cameraAnalysisActive, isPortraitMode, lensFacing) {
        while (isStreaming && isActive) {
            val detectedBounds = detectPreviewBitmapFace()
            currentPreviewFaceResultHandler(detectedBounds)
            delay(300L)
        }
    }

    LaunchedEffect(offerSdp, firebaseRtcSessionId, isStreaming, webRtcSourceReady) {
        val currentOfferSdp = offerSdp
        val currentRtcSessionId = firebaseRtcSessionId
        if (
            isStreaming &&
                !isEndingSession &&
                webRtcSourceReady &&
                currentOfferSdp != null &&
                currentRtcSessionId != null &&
                currentRtcSessionId != lastHandledOfferSessionId
        ) {
            isRemoteDescriptionSet = false
            pendingCandidates.clear()
            val answerStarted = createSharedAnswer(
                context = context,
                roomCode = roomCode,
                offerSdp = currentOfferSdp,
                rtcSessionId = currentRtcSessionId,
                repository = repository,
                onRemoteDescriptionSet = {
                    if (isEndingSession || roomStatus != "connected") return@createSharedAnswer
                    isRemoteDescriptionSet = true
                    val pc = WebRtcSessionManager.cameraPeerConnection
                    if (pc != null) {
                        pendingCandidates.forEach { candidate ->
                            runCatching { pc.addIceCandidate(candidate) }
                                .onFailure { Log.w("WEBRTC_LOG", "Camera ignored buffered ICE candidate", it) }
                        }
                        pendingCandidates.clear()
                    }
                }
            )
            if (answerStarted) {
                answerCreated = true
                lastHandledOfferSessionId = currentRtcSessionId
            }
        }
    }

    LaunchedEffect(roomStatus) {
        if (roomStatus == "connected") {
            hasSeenConnectedState = true
        } else {
            focusPoint = null
            focusSucceeded = null
            focusLocked = false
        }

        if (roomStatus == "ended") {
            showStatusPopup(
                context = context,
                title = "Session ended",
                detail = "Controller closed the session",
                badge = "END",
                accentColor = AndroidColor.rgb(255, 122, 69)
            )
            hostCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                exitScreen = true
            )
        } else if (hasSeenConnectedState && roomStatus == "waiting") {
            hostCoordinator.shutdownSession(
                isEndingSession = isEndingSession,
                setIsEndingSession = { isEndingSession = it },
                exitScreen = false
            )
        }
    }

    LaunchedEffect(firebaseLensFacing) {
        lensFacing = if (firebaseLensFacing == "front") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    LaunchedEffect(firebaseLensFacing) {
        if (hasSeenLensTransitionKey) {
            lensFadeAlpha.snapTo(0.58f)
            lensFadeAlpha.animateTo(0f, animationSpec = tween(durationMillis = 260))
        } else {
            hasSeenLensTransitionKey = true
        }
    }

    LaunchedEffect(firebaseCameraMode) {
        if (lastModeTransitionKey != firebaseCameraMode) {
            val previousIndex = cameraModeTransitionIndex(lastModeTransitionKey)
            val nextIndex = cameraModeTransitionIndex(firebaseCameraMode)
            modeSlideDirection = if (nextIndex >= previousIndex) 1f else -1f
            lastModeTransitionKey = firebaseCameraMode
            modeSlideProgress.snapTo(1f)
            modeSlideProgress.animateTo(0f, animationSpec = tween(durationMillis = 280))
        }
    }

    fun shouldUseFrontScreenFlash(): Boolean {
        if (!isFrontCamera) return false

        return when (firebaseFlashMode) {
            "on" -> true
            "auto" -> isPreviewSceneDark(previewView)
            else -> false
        }
    }

    fun setScreenBrightness(brightness: Float) {
        activity?.window?.let { window ->
            val attributes = window.attributes
            attributes.screenBrightness = brightness
            window.attributes = attributes
        }
    }

    fun takePhotoWithCameraX(
        useFrontScreenFlash: Boolean,
        onCaptureFinished: () -> Unit = {},
        playShutterSound: Boolean = true,
        forcedCaptureFlashMode: Int? = null
    ): Boolean {
        val currentCapture = imageCapture ?: run {
            Log.e("AICameraAssistant", "ImageCapture is not initialized yet")
            return false
        }

        val resolvedCaptureFlashMode = when {
            useFrontScreenFlash -> ImageCapture.FLASH_MODE_SCREEN
            isFrontCamera -> ImageCapture.FLASH_MODE_OFF
            !hasLedFlash -> ImageCapture.FLASH_MODE_OFF
            firebaseFlashMode == "on" -> ImageCapture.FLASH_MODE_ON
            firebaseFlashMode == "auto" -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        currentCapture.screenFlash = if (useFrontScreenFlash) {
            previewView.screenFlash
        } else {
            null
        }
        currentCapture.flashMode = forcedCaptureFlashMode ?: resolvedCaptureFlashMode

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
                override fun onCaptureStarted() {
                    if (playShutterSound) {
                        playShutterClick()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("AICameraAssistant", "Photo saved: ${output.savedUri}")
                    onCaptureFinished()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("AICameraAssistant", "Photo capture failed", exception)
                    onCaptureFinished()
                }
            }
        )
        return true
    }

    suspend fun captureJpegBytes(
        capture: ImageCapture,
        useFrontScreenFlash: Boolean,
        playShutterSound: Boolean = true,
        forcedCaptureFlashMode: Int? = null
    ): ByteArray? = suspendCancellableCoroutine { continuation ->
        capture.screenFlash = if (useFrontScreenFlash) {
            previewView.screenFlash
        } else {
            null
        }
        capture.flashMode = forcedCaptureFlashMode ?: when {
            useFrontScreenFlash -> ImageCapture.FLASH_MODE_SCREEN
            isFrontCamera -> ImageCapture.FLASH_MODE_OFF
            !hasLedFlash -> ImageCapture.FLASH_MODE_OFF
            firebaseFlashMode == "on" -> ImageCapture.FLASH_MODE_ON
            firebaseFlashMode == "auto" -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureStarted() {
                    if (playShutterSound) {
                        playShutterClick()
                    }
                }
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = runCatching {
                        val buffer = image.planes.first().buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    }.onFailure {
                        Log.e("AICameraAssistant", "Night Assist image read failed", it)
                    }.getOrNull()
                    image.close()
                    if (continuation.isActive) {
                        continuation.resume(bytes)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("AICameraAssistant", "Night Assist capture failed", exception)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        )
    }

    fun scoreNightAssistFrame(bytes: ByteArray): Double {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 8
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return 0.0
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < 2 || height < 2) return 0.0

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            var brightnessTotal = 0.0
            var sharpnessTotal = 0.0
            var sampleCount = 0
            val step = 3
            var y = step
            while (y < height) {
                var x = step
                while (x < width) {
                    val index = y * width + x
                    val pixel = pixels[index]
                    val luma = (
                        (android.graphics.Color.red(pixel) * 0.299) +
                            (android.graphics.Color.green(pixel) * 0.587) +
                            (android.graphics.Color.blue(pixel) * 0.114)
                        )
                    val leftPixel = pixels[index - step]
                    val topPixel = pixels[index - (step * width)]
                    val leftLuma = (
                        (android.graphics.Color.red(leftPixel) * 0.299) +
                            (android.graphics.Color.green(leftPixel) * 0.587) +
                            (android.graphics.Color.blue(leftPixel) * 0.114)
                        )
                    val topLuma = (
                        (android.graphics.Color.red(topPixel) * 0.299) +
                            (android.graphics.Color.green(topPixel) * 0.587) +
                            (android.graphics.Color.blue(topPixel) * 0.114)
                        )

                    brightnessTotal += luma
                    sharpnessTotal += kotlin.math.abs(luma - leftLuma) + kotlin.math.abs(luma - topLuma)
                    sampleCount++
                    x += step
                }
                y += step
            }

            if (sampleCount == 0) {
                0.0
            } else {
                val brightnessScore = brightnessTotal / sampleCount
                val sharpnessScore = sharpnessTotal / sampleCount
                brightnessScore + (sharpnessScore * 1.8)
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun saveJpegBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val name = "IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AICameraAssistant")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return@withContext false

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: return@withContext false
        }.onFailure {
            Log.e("AICameraAssistant", "Night Assist save failed", it)
        }.isSuccess
    }

    suspend fun captureNightAssistPhoto(): Boolean {
        val currentCapture = imageCapture ?: run {
            Log.e("AICameraAssistant", "ImageCapture is not initialized yet")
            return false
        }
        val currentCamera = camera
        val originalExposureIndex = exposureIndex
        val useFrontScreenFlash = false

        return try {
            nightAssistInProgress = true

            if (currentCamera != null && exposureUiState.supported) {
                val boostedExposure = exposureMaxIndex.coerceIn(exposureMinIndex, exposureMaxIndex)
                exposureIndex = boostedExposure
                runCatching {
                    currentCamera.cameraControl.setExposureCompensationIndex(boostedExposure)
                }.onFailure {
                    Log.w("CAMERA_EXPOSURE", "Night Assist exposure boost failed", it)
                }
            }

            val useLedTorch = currentCamera != null && hasLedFlash && !isFrontCamera
            if (useLedTorch) {
                runCatching { currentCamera?.cameraControl?.enableTorch(true) }
                delay(300)
                playShutterClick()
            }

            var bestScore = Double.NEGATIVE_INFINITY
            var bestBytes: ByteArray? = null
            repeat(3) { index ->
                val bytes = captureJpegBytes(
                    capture = currentCapture,
                    useFrontScreenFlash = useFrontScreenFlash,
                    playShutterSound = !useLedTorch && index == 0,
                    forcedCaptureFlashMode = if (useLedTorch) ImageCapture.FLASH_MODE_OFF else null
                )
                if (bytes != null) {
                    val score = withContext(Dispatchers.Default) {
                        scoreNightAssistFrame(bytes)
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestBytes = bytes
                    }
                }
                if (index < 2) {
                    delay(120)
                }
            }

            bestBytes?.let { saveJpegBytes(it) } == true
        } finally {
            nightAssistInProgress = false

            if (currentCamera != null && hasLedFlash && !isFrontCamera) {
                runCatching { currentCamera.cameraControl.enableTorch(false) }
            }

            if (currentCamera != null && exposureUiState.supported && !firebaseNightModeEnabled) {
                runCatching {
                    currentCamera.cameraControl.setExposureCompensationIndex(originalExposureIndex)
                }
            }
        }
    }

    fun updateVideoRecordingState(state: VideoRecordingState) {
        videoRecordingState = state
    }

    fun handleVideoRequest(requestType: String, onRequestHandled: () -> Unit): Boolean {
        return when (requestType) {
            "video_start" -> videoRecorder.start(
                videoCapture = videoCapture,
                onRecordingStateChanged = ::updateVideoRecordingState,
                onRequestHandled = onRequestHandled
            )

            "video_stop" -> videoRecorder.stop(
                onRecordingStateChanged = ::updateVideoRecordingState,
                onRequestHandled = onRequestHandled
            )

            "video_pause" -> videoRecorder.pause(
                onRecordingStateChanged = ::updateVideoRecordingState,
                onRequestHandled = onRequestHandled
            )

            "video_resume" -> videoRecorder.resume(
                onRecordingStateChanged = ::updateVideoRecordingState,
                onRequestHandled = onRequestHandled
            )

            else -> if (videoRecorder.isRecording) {
                videoRecorder.stop(
                    onRecordingStateChanged = ::updateVideoRecordingState,
                    onRequestHandled = onRequestHandled
                )
            } else {
                videoRecorder.start(
                    videoCapture = videoCapture,
                    onRecordingStateChanged = ::updateVideoRecordingState,
                    onRequestHandled = onRequestHandled
                )
            }
        }
    }

    suspend fun handleCaptureRequest(requestId: Long, requestType: String) {
        Log.d("AICameraAssistant", "CAPTURE_REQUEST")
        Log.d("AICameraAssistant", "Handling capture request type=$requestType id=$requestId")

        if (requestType.startsWith("video")) {
            val handled = handleVideoRequest(requestType) {
                lastHandledCaptureRequestId = requestId
                launchRoomWrite("capture reset") {
                    repository.resetCaptureRequest(roomCode)
                }
            }
            if (!handled) {
                Toast.makeText(context, "Video is not ready", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (requestType == "boomerang") {
            if (boomerangInProgress) {
                lastHandledCaptureRequestId = requestId
                launchRoomWrite("capture reset") {
                    repository.resetCaptureRequest(roomCode)
                }
                return
            }

            boomerangInProgress = true
            val saved = BoomerangRecorder(context, previewView).record()
            boomerangInProgress = false
            lastHandledCaptureRequestId = requestId
            launchRoomWrite("capture reset") {
                repository.resetCaptureRequest(roomCode)
            }
            if (saved) {
                showStatusPopup(
                    context = context,
                    title = "Boomerang saved",
                    detail = "Saved to gallery",
                    badge = "∞",
                    accentColor = AndroidColor.rgb(255, 213, 79)
                )
            } else {
                Toast.makeText(context, "Boomerang failed", Toast.LENGTH_SHORT).show()
                Log.w("AICameraAssistant", "Boomerang request failed")
            }
            return
        }

        if (firebaseNightModeEnabled && isFrontCamera) {
            val previousBrightness = activity?.window?.attributes?.screenBrightness
            selfieLightVisible = true
            Log.d("AICameraAssistant", "FRONT_SCREEN_LIGHT_ON")
            setScreenBrightness(1f)
            Log.d("AICameraAssistant", "SCREEN_BRIGHTNESS_MAX")
            delay(400)
            Log.d("AICameraAssistant", "WAIT_BEFORE_CAPTURE_DONE")
            Log.d("AICameraAssistant", "TAKE_PICTURE_NOW")

            var saved = false
            try {
                saved = captureNightAssistPhoto()
                if (saved) {
                    Log.d("AICameraAssistant", "IMAGE_SAVED")
                }
            } finally {
                selfieLightVisible = false
                previousBrightness?.let { setScreenBrightness(it) }
                Log.d("AICameraAssistant", "FRONT_SCREEN_LIGHT_OFF")
            }
            lastHandledCaptureRequestId = requestId
            launchRoomWrite("capture reset") { repository.resetCaptureRequest(roomCode) }
            if (!saved) {
                Toast.makeText(context, "Night Assist capture failed", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val useFrontScreenFlash = shouldUseFrontScreenFlash()
        val useLedTorchFlash =
            !useFrontScreenFlash &&
                !isFrontCamera &&
                hasLedFlash &&
                (firebaseFlashMode == "on" || (firebaseFlashMode == "auto" && isPreviewSceneDark(previewView)))

        if (useLedTorchFlash) {
            flashAlpha = 0.85f
            runCatching { camera?.cameraControl?.enableTorch(true) }
            delay(400)
            playShutterClick()
        } else if (useFrontScreenFlash) {
            val previousBrightness = activity?.window?.attributes?.screenBrightness
            selfieLightVisible = true
            setScreenBrightness(1f)
            delay(250)
            playShutterClick()
            val captureStarted = takePhotoWithCameraX(
                useFrontScreenFlash = false,
                playShutterSound = false,
                forcedCaptureFlashMode = ImageCapture.FLASH_MODE_OFF,
                onCaptureFinished = {
                    selfieLightVisible = false
                    previousBrightness?.let { setScreenBrightness(it) }
                }
            )
            if (!captureStarted) {
                selfieLightVisible = false
                previousBrightness?.let { setScreenBrightness(it) }
            }
            if (captureStarted) {
                lastHandledCaptureRequestId = requestId
                launchRoomWrite("capture reset") {
                    repository.resetCaptureRequest(roomCode)
                }
            }
            return
        }

        val captureStarted = takePhotoWithCameraX(
            useFrontScreenFlash = useFrontScreenFlash,
            playShutterSound = !useLedTorchFlash,
            forcedCaptureFlashMode = if (useLedTorchFlash) ImageCapture.FLASH_MODE_OFF else null,
            onCaptureFinished = {
                if (useLedTorchFlash) {
                    runCatching { camera?.cameraControl?.enableTorch(false) }
                    flashAlpha = 0f
                }
            }
        )
        if (!captureStarted && useLedTorchFlash) {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            flashAlpha = 0f
        }
        if (captureStarted) {
            lastHandledCaptureRequestId = requestId
            launchRoomWrite("capture reset") {
                repository.resetCaptureRequest(roomCode)
            }
        }
    }

    LaunchedEffect(
        firebaseCaptureRequestId,
        firebaseCaptureRequestType,
        imageCapture,
        videoCapture,
        firebaseCameraMode
    ) {
        if (firebaseCaptureRequestId <= 0L || firebaseCaptureRequestId == lastHandledCaptureRequestId) {
            return@LaunchedEffect
        }

        handleCaptureRequest(
            requestId = firebaseCaptureRequestId,
            requestType = firebaseCaptureRequestType
        )
    }

    LaunchedEffect(activity) {
        previewView.setScreenFlashOverlayColor(AndroidColor.WHITE)
        previewView.setScreenFlashWindow(activity?.window)
    }


    LaunchedEffect(lensFacing, isStreaming, firebaseCameraMode, firebaseVideoHdrEnabled, firebaseSceneDetectionEnabled) {
        cameraPreviewReady = false
        cameraStartupFailed = false
        val shouldRestartRecordingAfterBind =
            videoRecorder.isRecording && firebaseCameraMode == "video"
        restartRecordingAfterCameraBind = shouldRestartRecordingAfterBind
        if (shouldRestartRecordingAfterBind) {
            videoRecorder.stopForCameraSwitch(onRecordingStateChanged = ::updateVideoRecordingState)
        } else {
            videoRecorder.stop(onRecordingStateChanged = ::updateVideoRecordingState)
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = try {
            cameraProviderFuture.get()
        } catch (_: Exception) {
            cameraStartupFailed = true
            restartRecordingAfterCameraBind = false
            if (shouldRestartRecordingAfterBind) {
                updateVideoRecordingState(VideoRecordingState.Idle)
            }
            return@LaunchedEffect
        }

        val targetSize = if (firebaseCameraMode == "video") {
            Size(720, 1280)
        } else {
            Size(1080, 1920)
        }
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
            publishExposureState(firstCamera)

            val supportedDynamicRanges = Recorder.getVideoCapabilities(firstCamera.cameraInfo)
                .supportedDynamicRanges
            val videoHdrSupportedForLens = supportedDynamicRanges.any { dynamicRange ->
                dynamicRange != DynamicRange.SDR &&
                    dynamicRange.bitDepth == DynamicRange.BIT_DEPTH_10_BIT
            }
            val enableVideoHdrForBind =
                firebaseCameraMode == "video" && firebaseVideoHdrEnabled && videoHdrSupportedForLens
            launchRoomWrite("video HDR support publish") {
                repository.updateVideoHdrSupported(roomCode, videoHdrSupportedForLens)
                if (!videoHdrSupportedForLens && firebaseVideoHdrEnabled) {
                    repository.updateVideoHdrEnabled(roomCode, false)
                }
            }

            fun buildVideoCapture(dynamicRange: DynamicRange): VideoCapture<Recorder> {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                return VideoCapture.Builder(recorder)
                    .setDynamicRange(dynamicRange)
                    .setTargetRotation(targetRotation)
                    .build()
            }
            var activeVideoCapture = buildVideoCapture(
                if (enableVideoHdrForBind) {
                    DynamicRange.HDR_UNSPECIFIED_10_BIT
                } else {
                    DynamicRange.SDR
                }
            )

            launchRoomWrite("flash support publish") {
                repository.updateFlashSupported(
                    roomCode = roomCode,
                    flashSupported = firstCamera.cameraInfo.hasFlashUnit() ||
                        lensFacing == CameraSelector.LENS_FACING_FRONT
                )
            }

            firstCamera.cameraInfo.zoomState.value?.let { zoomState ->
                launchRoomWrite("zoom range publish") {
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

            launchRoomWrite("preview size publish") {
                repository.updatePreviewSize(roomCode, resolvedWidth, resolvedHeight)
            }

            cameraProvider.unbindAll()
            cameraAnalysisActive = false
            lastCameraAnalysisResultMs = 0L

            val finalUseCases = mutableListOf<UseCase>(localPreview)
            val analysisSize = Size(320, 240)
            val analysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        analysisSize,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()
            val faceAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(targetRotation)
                .build()
                .apply {
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    setAnalyzer(
                        faceAnalysisExecutor,
                        MlKitFaceDetectionAnalyzer(
                            detector = faceDetector,
                            minProcessIntervalMs = 300L,
                            sceneAnalyzer = if (firebaseSceneDetectionEnabled) sceneAnalyzer else null,
                            onSceneResult = { result ->
                                mainExecutor.execute {
                                    currentSceneResultHandler(result)
                                }
                            },
                            onFaceResult = { bounds ->
                                mainExecutor.execute {
                                    lastCameraAnalysisResultMs = System.currentTimeMillis()
                                    currentFaceResultHandler(bounds)
                                }
                            }
                        )
                    )
                }
            if (!isStreaming) {
                finalUseCases.add(faceAnalysis)
            } else {
                faceAnalysis.clearAnalyzer()
                cameraAnalysisActive = false
                lastCameraAnalysisResultMs = 0L
            }

            if (!isPortraitMode) {
                publishPortraitSubjectState(
                    status = "Finding subject...",
                    bounds = PortraitFaceBounds(),
                    force = true
                )
            }

            var streamingPreview: Preview? = null
            var videoStreamAnalysis: ImageAnalysis? = null
            if (isStreaming) {
                webRtcSourceReady = false
                val streamMaxLongEdge = if (firebaseCameraMode == "video") 1280 else 1920
                val streamScale =
                    (streamMaxLongEdge.toFloat() / maxOf(rawSize.width, rawSize.height)).coerceAtMost(1f)
                val streamWidth = (rawSize.width * streamScale).toInt().coerceAtLeast(1)
                val streamHeight = (rawSize.height * streamScale).toInt().coerceAtLeast(1)

                val webRtcSurface = if (firebaseCameraMode == "video") {
                    if (
                        videoFrameSource.start(
                            context = context,
                            width = streamWidth,
                            height = streamHeight
                        )
                    ) {
                        val videoStreamResolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(streamWidth, streamHeight),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                        videoStreamAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(videoStreamResolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(targetRotation)
                            .build()
                            .apply {
                                val streamAnalyzer = videoFrameSource.buildAnalyzer(mirrorHorizontally = isFrontCamera)
                                val mainExecutor = ContextCompat.getMainExecutor(context)
                                setAnalyzer(
                                    faceAnalysisExecutor,
                                    ImageAnalysis.Analyzer { imageProxy ->
                                        if (firebaseSceneDetectionEnabled) {
                                            runCatching {
                                                val result = sceneAnalyzer.detect(imageProxy)
                                                mainExecutor.execute {
                                                    currentSceneResultHandler(result)
                                                }
                                            }.onFailure {
                                                Log.w("SCENE_DETECTION", "Video scene detection failed", it)
                                            }
                                        }
                                        streamAnalyzer.analyze(imageProxy)
                                    }
                                )
                            }
                        videoStreamAnalysis?.let { finalUseCases.add(it) }
                        webRtcSourceReady = true
                    }
                    null
                } else {
                    WebRtcSessionManager.startWebRtcCameraSource(
                        context = context,
                        width = streamWidth,
                        height = streamHeight,
                        rotationDegrees = rotationDegrees
                    )
                }

                if (webRtcSurface != null) {
                    streamingPreview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(targetRotation)
                        .build()

                    streamingPreview?.setSurfaceProvider { request ->
                        request.provideSurface(
                            webRtcSurface,
                            ContextCompat.getMainExecutor(context)
                        ) {}
                    }

                    streamingPreview?.let { finalUseCases.add(it) }
                    webRtcSourceReady = true
                }
            } else {
                webRtcSourceReady = false
                WebRtcSessionManager.stopLocalCamera()
            }

            if (firebaseCameraMode != "video") {
                finalUseCases.add(newImageCapture)
            }
            if (firebaseCameraMode == "video") {
                finalUseCases.add(activeVideoCapture)
            }

            fun bindFinalUseCases(useCases: List<UseCase>): Camera =
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )

            val finalCamera = try {
                bindFinalUseCases(finalUseCases).also {
                    cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                }
            } catch (bindWithAnalysisError: Exception) {
                Log.w(
                    "FACE_DETECTION",
                    "Camera bind with face analysis failed; retrying without analysis",
                    bindWithAnalysisError
                )
                faceAnalysis.clearAnalyzer()
                finalUseCases.remove(faceAnalysis)
                cameraAnalysisActive = false
                lastCameraAnalysisResultMs = 0L
                clearPhotoFaceDetection()
                publishPortraitSubjectState(
                    status = "Finding subject...",
                    bounds = PortraitFaceBounds(),
                    force = true
                )
                try {
                    bindFinalUseCases(finalUseCases).also {
                        cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                    }
                } catch (bindWithStreamingVideoError: Exception) {
                    val removedStreamingPreview =
                        firebaseCameraMode == "video" &&
                            streamingPreview != null &&
                            finalUseCases.remove(streamingPreview)
                    val removedVideoStreamAnalysis =
                        firebaseCameraMode == "video" &&
                            videoStreamAnalysis != null &&
                            finalUseCases.remove(videoStreamAnalysis)
                    if (removedStreamingPreview) {
                        Log.w(
                            "CAMERA_BIND",
                            "Camera bind with streaming preview and video failed; retrying without streaming preview",
                            bindWithStreamingVideoError
                        )
                        webRtcSourceReady = false
                        WebRtcSessionManager.stopLocalCamera()
                    }
                    if (removedVideoStreamAnalysis) {
                        Log.w(
                            "CAMERA_BIND",
                            "Camera bind with video analysis stream failed; retrying without controller stream",
                            bindWithStreamingVideoError
                        )
                        webRtcSourceReady = false
                        WebRtcSessionManager.stopLocalCamera()
                    }
                    try {
                        bindFinalUseCases(finalUseCases).also {
                            cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                        }
                    } catch (bindWithVideoError: Exception) {
                        if (enableVideoHdrForBind && finalUseCases.contains(activeVideoCapture)) {
                            Log.w(
                                "CAMERA_BIND",
                                "Camera bind with HDR video failed; retrying SDR video",
                                bindWithVideoError
                            )
                            finalUseCases.remove(activeVideoCapture)
                            activeVideoCapture = buildVideoCapture(DynamicRange.SDR)
                            finalUseCases.add(activeVideoCapture)
                            launchRoomWrite("video HDR disable") {
                                repository.updateVideoHdrEnabled(roomCode, false)
                            }
                            try {
                                bindFinalUseCases(finalUseCases).also {
                                    cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                                }
                            } catch (bindWithSdrVideoError: Exception) {
                                Log.w(
                                    "CAMERA_BIND",
                                    "Camera bind with SDR video capture failed; retrying without video",
                                    bindWithSdrVideoError
                                )
                                finalUseCases.remove(activeVideoCapture)
                                videoCapture = null
                                bindFinalUseCases(finalUseCases).also {
                                    cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                                }
                            }
                        } else {
                            Log.w(
                                "CAMERA_BIND",
                                "Camera bind with video capture failed; retrying without video",
                                bindWithVideoError
                            )
                            finalUseCases.remove(activeVideoCapture)
                            videoCapture = null
                            bindFinalUseCases(finalUseCases).also {
                                cameraAnalysisActive = finalUseCases.contains(faceAnalysis)
                            }
                        }
                    }
                }
            }

            camera = finalCamera
            imageCapture = if (finalUseCases.contains(newImageCapture)) newImageCapture else null
            videoCapture = if (finalUseCases.contains(activeVideoCapture)) activeVideoCapture else null
            publishExposureState(finalCamera)
            delay(120)
            cameraPreviewReady = true
            if (restartRecordingAfterCameraBind) {
                val restarted = videoRecorder.start(
                    videoCapture = videoCapture,
                    onRecordingStateChanged = ::updateVideoRecordingState,
                    onRequestHandled = {},
                    showStartToast = false
                )
                restartRecordingAfterCameraBind = false
                if (!restarted) {
                    updateVideoRecordingState(VideoRecordingState.Idle)
                    Toast.makeText(context, "Video is not ready", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            cameraAnalysisActive = false
            lastCameraAnalysisResultMs = 0L
            videoCapture = null
            cameraStartupFailed = true
            restartRecordingAfterCameraBind = false
            if (shouldRestartRecordingAfterBind) {
                updateVideoRecordingState(VideoRecordingState.Idle)
            }
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
            launchRoomWrite("zoom clamp publish") {
                repository.updateZoomLevel(roomCode, clampedZoom.toDouble())
            }
        }
    }

    LaunchedEffect(firebaseCameraMode, camera, resolvedWidth, resolvedHeight) {
        if (firebaseCameraMode != "video" && videoRecorder.isRecording) {
            videoRecorder.stop(onRecordingStateChanged = ::updateVideoRecordingState)
        }
        if (camera == null) {
            clearPhotoFaceDetection()
            publishPortraitSubjectState(
                status = "Finding subject...",
                bounds = PortraitFaceBounds(),
                force = true
            )
            return@LaunchedEffect
        }
        if (isPortraitMode) {
            clearPhotoFaceDetection()
        } else {
            publishPortraitSubjectState(
                status = "Finding subject...",
                bounds = PortraitFaceBounds(),
                force = true
            )
        }

        awaitCancellation()
    }

    LaunchedEffect(flashAlpha) {
        if (flashAlpha > 0f && !(isFrontCamera && firebaseFlashMode != "off")) {
            delay(220)
            flashAlpha = 0f
        }
    }

    LaunchedEffect(
        camera,
        firebaseExposureIndex,
        exposureMinIndex,
        exposureMaxIndex,
        firebaseNightModeEnabled
    ) {
        val currentCamera = camera ?: return@LaunchedEffect
        if (!exposureUiState.supported) return@LaunchedEffect

        val targetExposure = nightModeExposurePolicy.resolveTargetIndex(
            nightModeEnabled = firebaseNightModeEnabled,
            requestedIndex = firebaseExposureIndex,
            minIndex = exposureMinIndex,
            maxIndex = exposureMaxIndex
        )
        if (targetExposure != exposureIndex) {
            exposureIndex = targetExposure
            runCatching {
                currentCamera.cameraControl.setExposureCompensationIndex(targetExposure)
            }.onFailure {
                Log.w("CAMERA_EXPOSURE", "Exposure compensation failed", it)
            }
            if (!firebaseNightModeEnabled) {
                launchRoomWrite("exposure state publish") {
                    repository.updateExposureState(
                        roomCode = roomCode,
                        minIndex = exposureMinIndex,
                        maxIndex = exposureMaxIndex,
                        currentIndex = targetExposure
                    )
                }
            }
        }
    }

    LaunchedEffect(
        firebaseSceneDetectionEnabled,
        firebaseSceneDetection.key,
        firebaseSceneDetection.confidence,
        firebaseNightModeEnabled
    ) {
        if (
            firebaseSceneDetectionEnabled &&
            firebaseSceneDetection.key == "night" &&
                firebaseSceneDetection.confidence >= 0.62 &&
                !firebaseNightModeEnabled
        ) {
            val now = System.currentTimeMillis()
            if (now - lastNightAutoAppliedMs > 10_000L) {
                lastNightAutoAppliedMs = now
                runCatching {
                    repository.updateNightModeEnabled(roomCode, true)
                    repository.updateSceneDetectionState(
                        roomCode = roomCode,
                        state = firebaseSceneDetection.copy(
                            timestamp = now,
                            autoAdjustment = "Night mode enabled"
                        )
                    )
                }.onFailure {
                    Log.w("AICameraAssistant", "Room write failed during night auto adjustment", it)
                }
            }
        }
    }

    LaunchedEffect(focusUiToken) {
        if (focusUiToken == 0) return@LaunchedEffect
        if (focusLocked) return@LaunchedEffect
        delay(2600)
        focusPoint = null
        focusSucceeded = null
    }

    LaunchedEffect(
        firebaseFocusRequestId,
        firebaseFocusPointX,
        firebaseFocusPointY,
        firebaseFocusLockEnabled,
        camera
    ) {
        if (firebaseFocusRequestId <= 0L || firebaseFocusRequestId == lastAppliedRemoteFocusRequestId) {
            return@LaunchedEffect
        }
        lastAppliedRemoteFocusRequestId = firebaseFocusRequestId
        triggerRemoteTapToFocus(
            normalizedX = firebaseFocusPointX,
            normalizedY = firebaseFocusPointY,
            lockFocus = firebaseFocusLockEnabled
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoRecorder.stop(onRecordingStateChanged = ::updateVideoRecordingState) }
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            runCatching { faceAnalysisExecutor.shutdownNow() }
            runCatching { faceDetector.close() }
            runCatching { shutterSound.release() }
            runCatching { WebRtcSessionManager.stopLocalCamera() }
            runCatching { WebRtcSessionManager.clearConnections() }
        }
    }

    var previewContainerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { previewContainerSize = it }
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val boxMaxWidthPx = previewContainerSize.width.toFloat()
        val boxMaxHeightPx = previewContainerSize.height.toFloat()
        val previewFrameRect =
            remember(boxMaxWidthPx, boxMaxHeightPx, firebaseAspectRatioMode) {
                aspectRatioFrameRect(
                    containerWidth = boxMaxWidthPx,
                    containerHeight = boxMaxHeightPx,
                    aspectRatioKey = firebaseAspectRatioMode
                )
            }
        val previewContentRect =
            remember(previewFrameRect, resolvedWidth, resolvedHeight, firebaseAspectRatioMode) {
                if (AspectRatioMode.fromKey(firebaseAspectRatioMode) == AspectRatioMode.Full) {
                    fittedPreviewRectInFrame(
                        frameRect = previewFrameRect,
                        contentWidth = resolvedWidth.toFloat(),
                        contentHeight = resolvedHeight.toFloat()
                    )
                } else {
                    previewFrameRect
                }
        }

        val previewAlpha by animateFloatAsState(
            targetValue = if (cameraPreviewReady) 1f else 0f,
            animationSpec = tween(durationMillis = 260),
            label = "camera_preview_alpha"
        )
        val startupOverlayAlpha by animateFloatAsState(
            targetValue = if (cameraPreviewReady && !cameraStartupFailed) 0f else 1f,
            animationSpec = tween(durationMillis = 180),
            label = "camera_startup_overlay_alpha"
        )

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = (previewFrameRect?.left ?: 0f).roundToInt(),
                        y = (previewFrameRect?.top ?: 0f).roundToInt()
                    )
                }
                .size(
                    width = with(density) { (previewFrameRect?.width ?: boxMaxWidthPx).toDp() },
                    height = with(density) { (previewFrameRect?.height ?: boxMaxHeightPx).toDp() }
                )
                .graphicsLayer { alpha = previewAlpha }
        )

        val transitionPreviewRect =
            previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
        if (startupOverlayAlpha > 0.01f) {
            CameraStartupOverlay(
                failed = cameraStartupFailed,
                alpha = startupOverlayAlpha,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            transitionPreviewRect.left.roundToInt(),
                            transitionPreviewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { transitionPreviewRect.width.toDp() },
                        height = with(density) { transitionPreviewRect.height.toDp() }
                    )
            )
        }

        if (modeSlideProgress.value > 0.01f) {
            val slideDistancePx = with(density) { 56.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            transitionPreviewRect.left.roundToInt(),
                            transitionPreviewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { transitionPreviewRect.width.toDp() },
                        height = with(density) { transitionPreviewRect.height.toDp() }
                    )
                    .graphicsLayer {
                        alpha = 0.22f * modeSlideProgress.value
                        translationX = -modeSlideDirection * slideDistancePx * modeSlideProgress.value
                    }
                    .background(Color.Black)
            )
        }

        if (lensFadeAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            transitionPreviewRect.left.roundToInt(),
                            transitionPreviewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { transitionPreviewRect.width.toDp() },
                        height = with(density) { transitionPreviewRect.height.toDp() }
                    )
                    .background(Color.Black.copy(alpha = lensFadeAlpha.value))
            )
        }

        if (sessionIsActive) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
            val swipeThresholdPx = with(density) { 72.dp.toPx() }
            val focusTapSlopPx = with(density) { 8.dp.toPx() }
            val exposureDragSlopPx = with(density) { 26.dp.toPx() }
            val topControlGuardPx = with(density) { 128.dp.toPx() }
            val bottomControlGuardPx = with(density) { 188.dp.toPx() }
            val sideControlGuardPx = with(density) { 86.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            previewRect.left.roundToInt(),
                            previewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { previewRect.width.toDp() },
                        height = with(density) { previewRect.height.toDp() }
                    )
                    .pointerInput(
                        camera,
                        firebaseZoomLevel,
                        firebaseCameraMode,
                        exposureUiState.supported,
                        exposureUiState.progress
                    ) {
                        fun Offset.isNearCameraControls(): Boolean =
                            y < topControlGuardPx ||
                                y > size.height - bottomControlGuardPx ||
                                x > size.width - sideControlGuardPx

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val startsNearControls = start.isNearCameraControls()
                            var lastPosition = start
                            var maxPointerCount = 1
                            var gestureZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio
                                ?: firebaseZoomLevel.toFloat()
                            val startExposureProgress = exposureUiState.progress
                            var verticalExposureActive = false
                            var pinchActive = false
                            var consumedDrag = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.isEmpty()) {
                                    break
                                }

                                maxPointerCount = maxOf(maxPointerCount, pressedChanges.size)
                                if (pressedChanges.size > 1) {
                                    pinchActive = true
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange.isFinite() && zoomChange > 0f) {
                                        gestureZoom *= zoomChange
                                        applyGestureZoom(gestureZoom)
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                if (pinchActive) {
                                    continue
                                }

                                val change = pressedChanges.first()
                                val currentPosition = change.position
                                val totalDrag = currentPosition - start
                                lastPosition = currentPosition

                                if (
                                    !startsNearControls &&
                                    exposureUiState.supported &&
                                    !verticalExposureActive &&
                                    abs(totalDrag.y) > exposureDragSlopPx &&
                                    abs(totalDrag.y) > abs(totalDrag.x) * 1.55f
                                ) {
                                    verticalExposureActive = true
                                }

                                if (verticalExposureActive) {
                                    val exposureDelta = totalDrag.y / size.height.toFloat().coerceAtLeast(1f)
                                    updateExposureFromProgress(startExposureProgress + exposureDelta)
                                    change.consume()
                                    consumedDrag = true
                                }
                            }

                            if (pinchActive) {
                                applyGestureZoom(gestureZoom, forcePublish = true)
                                return@awaitEachGesture
                            }

                            val totalDrag = lastPosition - start
                            val movedHorizontally = abs(totalDrag.x) >= swipeThresholdPx &&
                                abs(totalDrag.x) > abs(totalDrag.y) * 1.25f
                            val tapped = !startsNearControls &&
                                !consumedDrag &&
                                maxPointerCount == 1 &&
                                abs(totalDrag.x) < focusTapSlopPx &&
                                abs(totalDrag.y) < focusTapSlopPx

                            when {
                                movedHorizontally -> updateCameraModeFromSwipe(totalDrag.x)
                                tapped -> triggerTapToFocus(
                                    Offset(
                                        x = previewRect.left + start.x,
                                        y = previewRect.top + start.y
                                    ).clampOffsetTo(previewRect)
                                )
                            }
                        }
                    }
            )
        }

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }

        if (isFrontCamera && exposureUiState.supported && exposureUiState.frontPreviewOverlay.alpha > 0f) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
            PreviewExposureOverlay(
                state = exposureUiState.frontPreviewOverlay,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            previewRect.left.roundToInt(),
                            previewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { previewRect.width.toDp() },
                        height = with(density) { previewRect.height.toDp() }
                    )
            )
        }

        if (sessionIsActive && hostTopOverlayUiState.status.warningText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = hostTopOverlayUiState.status.warningText,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    hostTopOverlayUiState.status.warningDetailText?.let { detailText ->
                        Text(
                            text = detailText,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        if (firebaseGridEnabled) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            previewRect.left.roundToInt(),
                            previewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { previewRect.width.toDp() },
                        height = with(density) { previewRect.height.toDp() }
                    )
            ) {
                CameraGridOverlay(modifier = Modifier.fillMaxSize())
            }
        }

        if (!isPortraitMode && photoFaceBounds.isNotEmpty()) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
            FaceDetectionFocusBoxes(
                bounds = photoFaceBounds.map { it.toNormalizedFaceBounds() },
                visible = photoFaceBoxVisible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            previewRect.left.roundToInt(),
                            previewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { previewRect.width.toDp() },
                        height = with(density) { previewRect.height.toDp() }
                    )
            )
        }

        if (isPortraitMode) {
            val previewRect =
                previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
            PortraitPreviewOverlay(
                effectKey = firebasePortraitEffect,
                strength = firebasePortraitStrength,
                status = firebasePortraitStatus,
                faceLeft = firebasePortraitFaceLeft,
                faceTop = firebasePortraitFaceTop,
                faceRight = firebasePortraitFaceRight,
                faceBottom = firebasePortraitFaceBottom,
                faceBoxes = firebaseFaceBoxes,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            previewRect.left.roundToInt(),
                            previewRect.top.roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { previewRect.width.toDp() },
                        height = with(density) { previewRect.height.toDp() }
                    )
            )
        }

        if (sessionIsActive) {
            focusPoint?.let { rawPoint ->
                val previewRect =
                    previewContentRect ?: Rect(0f, 0f, boxMaxWidthPx, boxMaxHeightPx)
                val point = rawPoint.clampOffsetTo(previewRect)
                SharedFocusReticleSamsung(
                    point = point,
                    success = focusSucceeded,
                    showExposureHandle = exposureUiState.supported,
                    isLocked = focusLocked,
                    exposureProgress = exposureUiState.progress,
                    onToggleLock = {
                        triggerTapToFocus(point, lockFocus = !focusLocked)
                    },
                    onExposureProgressChange = if (exposureUiState.supported) {
                        { progress -> updateExposureFromProgress(progress) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HostTopOverlay(state = hostTopOverlayUiState, actions = hostTopOverlayActions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (firebaseSceneDetectionEnabled) {
                    SceneDetectionChip(state = firebaseSceneDetection)
                }
            }
        }

        CameraToolRail(
            state = hostToolRailUiState,
            actions = hostToolRailActions,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )

        AnimatedVisibility(
            visible = exposureUiState.visible && exposureUiState.supported,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ManualExposurePanel(
                progress = exposureUiState.progress,
                exposureLabel = exposureUiState.label,
                onProgressChange = hostExposureUiActions.onProgressChange,
                onDismiss = hostExposureUiActions.onDismiss,
                onReset = hostExposureUiActions.onReset,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp)
            )
        }

        AnimatedContent(
            targetState = firebaseCameraMode,
            transitionSpec = {
                val direction =
                    if (cameraModeTransitionIndex(targetState) >= cameraModeTransitionIndex(initialState)) {
                        1
                    } else {
                        -1
                    }
                (
                    slideInHorizontally(animationSpec = tween(durationMillis = 230)) { direction * it / 3 } +
                        fadeIn(animationSpec = tween(durationMillis = 180))
                    ) togetherWith (
                    slideOutHorizontally(animationSpec = tween(durationMillis = 180)) { -direction * it / 3 } +
                        fadeOut(animationSpec = tween(durationMillis = 140))
                    )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            label = "host_camera_mode_strip_transition"
        ) { animatedMode ->
            HostCameraModeStrip(
                selectedMode = animatedMode,
                onModeSelected = { mode -> updateCameraMode(mode) }
            )
        }

        if (selfieLightAlpha > 0.01f) {
            NightModeAssistLight(
                intensity = selfieLightAlpha,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (nightAssistInProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Hold still...",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CameraStartupOverlay(
    failed: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Text(
                text = "Camera unavailable",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF111111),
                            Color(0xFF050505),
                            Color(0xFF171717)
                        )
                    )
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.055f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.32f, size.height * 0.28f)
                )
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = 0.05f),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.72f, size.height * 0.68f)
                )
                drawRect(color = Color.Black.copy(alpha = 0.34f))
            }
        }
    }
}

private fun cameraModeTransitionIndex(mode: String): Int =
    when (mode) {
        "video" -> 0
        "portrait" -> 1
        else -> 2
    }

@Composable
private fun HostCameraModeStatus(
    cameraMode: String,
    portraitEffect: String,
    portraitBlurLevel: String
) {
    val isPortrait = cameraMode == "portrait"
    val modeLabel = when (cameraMode) {
        "video" -> "Video"
        "portrait" -> "Portrait"
        else -> "Photo"
    }
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Mode: $modeLabel",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        if (isPortrait) {
            Text(
                text = "${portraitEffectLabel(portraitEffect)} / ${formatPortraitBlurLabel(portraitBlurLevel)}",
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private fun formatPortraitBlurLabel(value: String): String =
    when (value) {
        "natural" -> "Natural"
        "strong" -> "Strong"
        else -> "Blur"
    }

@Composable
private fun HostCameraModeStrip(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HostCameraModeItem(
            label = "VIDEO",
            mode = "video",
            selected = selectedMode == "video",
            onModeSelected = onModeSelected
        )
        HostCameraModeItem(
            label = "PORTRAIT",
            mode = "portrait",
            selected = selectedMode == "portrait",
            onModeSelected = onModeSelected
        )
        HostCameraModeItem(
            label = "PHOTO",
            mode = "photo",
            selected = selectedMode == "photo",
            onModeSelected = onModeSelected
        )
    }
}

@Composable
private fun HostCameraModeItem(
    label: String,
    mode: String,
    selected: Boolean,
    onModeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(enabled = !selected) { onModeSelected(mode) }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.54f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .size(width = if (selected) 22.dp else 4.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color(0xFFFFD54F) else Color.Transparent)
        )
    }
}

@Composable
fun GridToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 30.dp) {
        Surface(
            modifier = modifier.size(30.dp),
            shape = CircleShape,
            color = if (isActive) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.32f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isActive) {
                androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f))
            } else {
                null
            }
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val iconColor = Color.White.copy(alpha = if (isActive) 0.96f else 0.88f)
                    val strokeWidth = 1.2.dp.toPx()
                    val thirdWidth = size.width / 3f
                    val thirdHeight = size.height / 3f

                    repeat(2) { index ->
                        val verticalX = thirdWidth * (index + 1)
                        drawLine(
                            color = iconColor,
                            start = Offset(verticalX, 0f),
                            end = Offset(verticalX, size.height),
                            strokeWidth = strokeWidth
                        )

                        val horizontalY = thirdHeight * (index + 1)
                        drawLine(
                            color = iconColor,
                            start = Offset(0f, horizontalY),
                            end = Offset(size.width, horizontalY),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusReticle(
    point: Offset,
    success: Boolean?,
    showExposureHandle: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "focus_reticle_scale"
    )
    val ringColor = when {
        isLocked -> Color(0xFFFFC400)
        success == true -> Color(0xFFFFD54F)
        success == false -> Color.White.copy(alpha = 0.72f)
        else -> Color.White
    }

    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleRadius = 25.dp.toPx() * scale
            drawCircle(
                color = ringColor,
                radius = reticleRadius,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

        }

        if (isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (point.x - 8.dp.toPx()).roundToInt(),
                            y = (point.y - 38.dp.toPx()).roundToInt()
                        )
                    }
                    .size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (point.x - 18.dp.toPx()).roundToInt(),
                        y = (point.y - 44.dp.toPx()).roundToInt()
                    )
                }
                .size(36.dp)
                .pointerInput(isLocked) {
                    detectTapGestures(onTap = { onToggleLock() })
                }
        )

        if (showExposureHandle) {
            Row(
                modifier = Modifier.offset {
                    IntOffset(
                        x = (point.x - 16.dp.toPx()).roundToInt(),
                        y = (point.y + 10.dp.toPx()).roundToInt()
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "-",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = if (isLocked) ringColor else Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "+",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun ExposureSliderOverlay(
    progress: Float,
    neutralProgress: Float,
    modifier: Modifier = Modifier,
    onProgressChange: (Float) -> Unit
) {
    var dragProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress.coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .pointerInput(progress) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragProgress = progress.coerceIn(0f, 1f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val nextProgress =
                            (dragProgress + (dragAmount / size.height.toFloat())).coerceIn(0f, 1f)
                        dragProgress = nextProgress
                        onProgressChange(nextProgress)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 8.dp)
        ) {
            val trackX = size.width / 2f
            val trackTop = 10.dp.toPx()
            val trackBottom = size.height - 10.dp.toPx()
            val trackHeight = trackBottom - trackTop
            val clampedNeutralProgress = neutralProgress.coerceIn(0f, 1f)
            val thumbY = trackTop + (trackHeight * dragProgress.coerceIn(0f, 1f))
            val neutralY = trackTop + (trackHeight * clampedNeutralProgress)

            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(trackX, trackTop),
                end = Offset(trackX, trackBottom),
                strokeWidth = 1.4.dp.toPx()
            )
            drawLine(
                color = Color(0xFFFFC400),
                start = Offset(trackX, minOf(thumbY, neutralY)),
                end = Offset(trackX, maxOf(thumbY, neutralY)),
                strokeWidth = 1.8.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.48f),
                start = Offset(trackX - 4.dp.toPx(), neutralY),
                end = Offset(trackX + 4.dp.toPx(), neutralY),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = Color(0xFFFFC400),
                radius = 5.dp.toPx(),
                center = Offset(trackX, thumbY)
            )
        }
    }
}

@Composable
private fun PhotoFaceFocusBox(
    bounds: PortraitFaceBounds,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val paddingPx = 10.dp.toPx()
        val left = (bounds.left.toFloat() * size.width - paddingPx).coerceIn(0f, size.width)
        val top = (bounds.top.toFloat() * size.height - paddingPx).coerceIn(0f, size.height)
        val right = (bounds.right.toFloat() * size.width + paddingPx).coerceIn(left, size.width)
        val bottom = (bounds.bottom.toFloat() * size.height + paddingPx).coerceIn(top, size.height)
        val rectSize = androidx.compose.ui.geometry.Size(right - left, bottom - top)
        val cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

        drawRoundRect(
            color = Color(0xFFFFD54F).copy(alpha = 0.26f),
            topLeft = Offset(left, top),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 6.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.84f),
            topLeft = Offset(left, top),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(left, top),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 2.2.dp.toPx())
        )
    }
}

@Composable
private fun FocusExposureConnector(
    focusPoint: Offset,
    sliderTopLeft: Offset,
    sliderSize: androidx.compose.ui.geometry.Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sliderOnRight = sliderTopLeft.x > focusPoint.x
        val reticleHalf = 34.dp.toPx()
        val startX = if (sliderOnRight) focusPoint.x + reticleHalf else focusPoint.x - reticleHalf
        val endX = if (sliderOnRight) sliderTopLeft.x else sliderTopLeft.x + sliderSize.width
        val connectorY = focusPoint.y.coerceIn(
            sliderTopLeft.y + 18.dp.toPx(),
            sliderTopLeft.y + sliderSize.height - 18.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(startX, connectorY),
            end = Offset(endX, connectorY),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun FocusReticleSamsung(
    point: Offset,
    success: Boolean?,
    showExposureHandle: Boolean,
    isLocked: Boolean,
    exposureProgress: Float,
    onToggleLock: () -> Unit,
    onExposureProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.12f else 1f,
        label = "focus_reticle_samsung_scale"
    )
    val density = LocalDensity.current
    val ringColor = when {
        isLocked -> Color(0xFFFFC400)
        success == true -> Color(0xFFFFD54F)
        success == false -> Color.White.copy(alpha = 0.72f)
        else -> Color.White
    }

    Box(modifier = modifier) {
        val reticleRadiusPx = with(density) { 25.dp.toPx() * scale }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor,
                radius = reticleRadiusPx,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (point.x - 16.dp.toPx()).roundToInt(),
                        y = (point.y - reticleRadiusPx - 16.dp.toPx()).roundToInt()
                    )
                }
                .size(32.dp)
                .pointerInput(isLocked) {
                    detectTapGestures(onTap = { onToggleLock() })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (isLocked) ringColor else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (showExposureHandle) {
            FocusExposureHandleSamsung(
                center = point,
                ringRadiusPx = reticleRadiusPx,
                isLocked = isLocked,
                progress = exposureProgress,
                onProgressChange = onExposureProgressChange
            )
        }
    }
}

@Composable
private fun FocusExposureHandleSamsung(
    center: Offset,
    ringRadiusPx: Float,
    isLocked: Boolean,
    progress: Float,
    onProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val controlWidth = 42.dp
    val controlHeight = 14.dp
    val trackWidth = 32.dp
    var dragProgress by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress.coerceIn(0f, 1f)
        }
    }

    val controlWidthPx = with(density) { controlWidth.toPx() }
    val trackWidthPx = with(density) { trackWidth.toPx() }
    val trackStartX = (controlWidthPx - trackWidthPx) / 2f

    fun progressFromTouchX(x: Float): Float =
        ((x - trackStartX) / trackWidthPx).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (center.x - (controlWidthPx / 2f)).roundToInt(),
                        y = (center.y + ringRadiusPx + 2.dp.toPx()).roundToInt()
                    )
                }
                .width(controlWidth)
                .height(controlHeight)
                .pointerInput(onProgressChange) {
                    if (onProgressChange == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val nextProgress = progressFromTouchX(offset.x)
                            dragProgress = nextProgress
                            onProgressChange(nextProgress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nextProgress = progressFromTouchX(change.position.x)
                            dragProgress = nextProgress
                            onProgressChange(nextProgress)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(width = trackWidth, height = controlHeight)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val halfLine = trackWidthPx / 2f
                val lineGap = 5.dp.toPx()
                val iconCenterX =
                    (centerX - halfLine + ((halfLine * 2f) * dragProgress.coerceIn(0f, 1f)))
                        .coerceIn(4.5.dp.toPx(), size.width - 4.5.dp.toPx())
                val accentColor = if (isLocked) Color(0xFFFFC400) else Color(0xFFFFD54F)

                drawLine(
                    color = Color.White.copy(alpha = 0.68f),
                    start = Offset(centerX - halfLine, centerY),
                    end = Offset(iconCenterX - lineGap, centerY),
                    strokeWidth = 1.1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.68f),
                    start = Offset(iconCenterX + lineGap, centerY),
                    end = Offset(centerX + halfLine, centerY),
                    strokeWidth = 1.1.dp.toPx()
                )
                drawCircle(
                    color = accentColor,
                    radius = 2.3.dp.toPx(),
                    center = Offset(iconCenterX, centerY)
                )

                val rayStart = 4.6.dp.toPx()
                val rayLength = 2.8.dp.toPx()
                val rayStroke = 0.8.dp.toPx()
                repeat(8) { index ->
                    val angle = (index * 45f) * (Math.PI.toFloat() / 180f)
                    val dx = kotlin.math.cos(angle)
                    val dy = kotlin.math.sin(angle)
                    drawLine(
                        color = accentColor,
                        start = Offset(iconCenterX + (dx * rayStart), centerY + (dy * rayStart)),
                        end = Offset(
                            iconCenterX + (dx * (rayStart + rayLength)),
                            centerY + (dy * (rayStart + rayLength))
                        ),
                        strokeWidth = rayStroke
                    )
                }
            }
        }
    }
}
