package com.example.aicameraassistant

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

enum class AppConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    WEAK_NETWORK,
    RETRYING,
    DISCONNECTED
}

data class WebRtcDiagnostics(
    val connectionState: AppConnectionState = AppConnectionState.IDLE,
    val iceState: String = "NEW",
    val signalingState: String = "STABLE",
    val reconnectAttempts: Int = 0,
    val averageLatencyMs: Double = 0.0,
    val packetLossPercent: Double = 0.0,
    val bitrateBps: Long = 0L,
    val framesPerSecond: Double = 0.0,
    val availableOutgoingBitrateBps: Long = 0L,
    val jitterMs: Double = 0.0,
    val framesDropped: Long = 0L,
    val selectedCandidateType: String = "unknown",
    val usingRelay: Boolean = false
)

object WebRtcSessionManager {
    private data class ControllerSession(
        val roomCode: String,
        val generation: Long
    )
    private data class ConnectionHealth(
        var hasEverConnected: Boolean = false,
        var lastConnectedAtMs: Long = 0L,
        var lastDisconnectedAtMs: Long = 0L
    )

    private enum class ConnectionSide {
        CAMERA,
        CONTROLLER
    }

    private enum class PreviewQuality { EXCELLENT, GOOD, WEAK, VERY_WEAK }

    private const val DISCONNECT_TIMEOUT_MS = 5_000L
    private const val WEAK_NETWORK_HOLD_MS = 2_500L
    private const val UNSTABLE_RECOVERY_WINDOW_MS = 5_000L
    private const val VIDEO_MIN_BITRATE_BPS = 900_000
    private const val VIDEO_MAX_BITRATE_BPS = 5_000_000
    private const val VIDEO_MAX_FRAMERATE = 30

    private var initialized = false
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var _eglBase: EglBase? = null
    val eglBase: EglBase
        get() = _eglBase ?: throw IllegalStateException("WebRtcSessionManager not initialized")

    private var _factory: PeerConnectionFactory? = null
    val factory: PeerConnectionFactory?
        get() = _factory

    var controllerPeerConnection: PeerConnection? = null
    var cameraPeerConnection: PeerConnection? = null

    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    var remoteVideoTrack: VideoTrack? = null

    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var cachedSurface: Surface? = null
    private var imageFrameSourceActive = false

    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var captureRotationDegrees: Int = 0

    private val _cameraConnectionState = MutableStateFlow(AppConnectionState.IDLE)
    val cameraConnectionState: StateFlow<AppConnectionState> = _cameraConnectionState.asStateFlow()

    private val _controllerConnectionState = MutableStateFlow(AppConnectionState.IDLE)
    val controllerConnectionState: StateFlow<AppConnectionState> =
        _controllerConnectionState.asStateFlow()

    private var cameraDisconnectJob: Job? = null
    private var controllerDisconnectJob: Job? = null
    private var cameraWeakNetworkJob: Job? = null
    private var controllerWeakNetworkJob: Job? = null
    private val cameraConnectionHealth = ConnectionHealth()
    private val controllerConnectionHealth = ConnectionHealth()
    private var cameraIceCandidateHandler: (IceCandidate) -> Unit = {}
    private var controllerIceCandidateHandler: (IceCandidate) -> Unit = {}
    private var controllerRemoteTrackHandler: (VideoTrack) -> Unit = {}
    private var cameraPeerGeneration = 0L
    private var controllerPeerGeneration = 0L
    private var cameraRtcSessionId: String? = null
    private var controllerRtcSessionId: String? = null
    private val cameraRemoteCandidateKeys = mutableSetOf<String>()
    private val controllerRemoteCandidateKeys = mutableSetOf<String>()
    private var cameraSessionOwner: String? = null
    private var controllerSessionOwner: String? = null
    private var controllerSessionCounter = 0L
    private var activeControllerSession: ControllerSession? = null
    private var controllerPeerOwner: ControllerSession? = null
    private val _diagnostics = MutableStateFlow(WebRtcDiagnostics())
    val diagnostics: StateFlow<WebRtcDiagnostics> = _diagnostics.asStateFlow()
    private var statsJob: Job? = null
    private var previousStatsAtMs = 0L
    private var previousBytesSent = 0L
    private var previousFramesSent = 0L
    private var configuredIceServers: List<IceServerCredential> = emptyList()
    private var previewQuality = PreviewQuality.GOOD
    private var qualityUpgradeSamples = 0
    private val diagnosticLogTimes = mutableMapOf<String, Long>()

    @Synchronized
    fun updateIceServers(credentials: List<IceServerCredential>) {
        configuredIceServers = credentials.filter { it.isUsable() }
    }

    @Synchronized
    fun logSessionDiagnostics(
        role: String,
        roomCode: String,
        sessionId: String?,
        signalingGeneration: Long,
        reconnectReason: String,
        reconnectAttempt: Int,
        lastFrameAtMs: Long
    ) {
        if (!BuildConfig.DEBUG) return
        val key = role
        val now = SystemClock.elapsedRealtime()
        if (now - (diagnosticLogTimes[key] ?: 0L) < 5_000L) return
        diagnosticLogTimes[key] = now
        val value = diagnostics.value
        AppLogger.debug(LogCategory.WEBRTC, "role=$role generation=$signalingGeneration " +
                "connection=${value.connectionState} ice=${value.iceState} " +
                "signaling=${value.signalingState} reconnectReason=$reconnectReason " +
                "attempt=$reconnectAttempt lastFrame=$lastFrameAtMs rttMs=${value.averageLatencyMs} " +
                "loss=${value.packetLossPercent} bitrate=${value.bitrateBps} " +
                "available=${value.availableOutgoingBitrateBps} fps=${value.framesPerSecond} " +
                "jitterMs=${value.jitterMs} dropped=${value.framesDropped} " +
                "candidate=${value.selectedCandidateType} relay=${value.usingRelay}"
        )
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized && _factory != null) return

        AppLogger.info(LogCategory.WEBRTC, "WEBRTC_INITIALIZING")
        try {
            val appContext = context.applicationContext

            if (_eglBase == null) {
                _eglBase = EglBase.create()
            }

            val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            _factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                )
                .setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(eglBase.eglBaseContext)
                )
                .createPeerConnectionFactory()

            initialized = true
            AppLogger.info(LogCategory.WEBRTC, "WEBRTC_INITIALIZED")
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "PeerConnectionFactory initialization failed", t)
        }
    }

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val stunServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val provisionedTurn = configuredIceServers.mapNotNull { credential ->
            if (!credential.isUsable()) return@mapNotNull null
            PeerConnection.IceServer.builder(credential.urls)
                .setUsername(credential.username)
                .setPassword(credential.password)
                .createIceServer()
        }
        val debugTurn = if (BuildConfig.DEBUG && provisionedTurn.isEmpty()) {
            listOf(
                PeerConnection.IceServer.builder(
                    listOf(
                        "turn:openrelay.metered.ca:80?transport=udp",
                        "turn:openrelay.metered.ca:443?transport=tcp"
                    )
                )
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        } else emptyList()
        val iceServers = stunServers + provisionedTurn + debugTurn

        return PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
    }

    @Synchronized
    fun startWebRtcCameraSource(
        context: Context,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0
    ): Surface? {
        initialize(context)
        val f = factory ?: return null

        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val safeRotation = rotationDegrees.normalizedRotationDegrees()
        imageFrameSourceActive = false

        if (
            surfaceTextureHelper != null &&
            cachedSurface != null &&
            captureWidth == safeWidth &&
            captureHeight == safeHeight &&
            captureRotationDegrees == safeRotation
        ) {
            return cachedSurface
        }

        if (
            surfaceTextureHelper != null &&
            (
                captureWidth != safeWidth ||
                    captureHeight != safeHeight ||
                    captureRotationDegrees != safeRotation
                )
        ) {
            stopLocalCamera()
        }

        return try {
            val helper = SurfaceTextureHelper.create(
                "WebRtcCaptureThread",
                eglBase.eglBaseContext
            )
            helper.setTextureSize(safeWidth, safeHeight)

            val vSource = f.createVideoSource(false)
            val aSource = f.createAudioSource(MediaConstraints())

            vSource.capturerObserver.onCapturerStarted(true)

            helper.startListening { frame ->
                videoSource?.capturerObserver?.onFrameCaptured(frame)
            }

            videoSource = vSource
            audioSource = aSource
            surfaceTextureHelper = helper

            localVideoTrack = f.createVideoTrack("LOCAL_VIDEO", vSource)
            localAudioTrack = f.createAudioTrack("LOCAL_AUDIO", aSource)
            AppLogger.info(LogCategory.WEBRTC, "LOCAL_VIDEO_TRACK_CREATED")

            captureWidth = safeWidth
            captureHeight = safeHeight
            captureRotationDegrees = safeRotation

            val surface = Surface(helper.surfaceTexture)
            cachedSurface = surface

            AppLogger.debug(LogCategory.WEBRTC, "WebRTC source started with size: ${captureWidth}x${captureHeight}, rotation=$safeRotation"
            )

            attachLocalTracksToCameraPeer()
            surface
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "SurfaceTextureHelper or VideoSource creation failed", t)
            null
        }
    }

    @Synchronized
    fun stopLocalCamera() {
        AppLogger.debug(LogCategory.WEBRTC, "Stopping local camera...")
        try {
            val helper = surfaceTextureHelper
            val surface = cachedSurface
            val currentVideoSource = videoSource
            val currentAudioSource = audioSource

            surfaceTextureHelper = null
            cachedSurface = null
            videoSource = null
            audioSource = null

            localVideoTrack = null
            localAudioTrack = null

            runCatching { helper?.stopListening() }
            runCatching { surface?.release() }
            runCatching { currentVideoSource?.capturerObserver?.onCapturerStopped() }
            runCatching { helper?.dispose() }
            runCatching { currentVideoSource?.dispose() }
            runCatching { currentAudioSource?.dispose() }

            imageFrameSourceActive = false
            captureWidth = 0
            captureHeight = 0
            captureRotationDegrees = 0
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "WebRTC cleanup failed", t)
        }
    }

    @Synchronized
    fun startImageFrameSource(context: Context, width: Int, height: Int): VideoSource? {
        initialize(context)
        val f = factory ?: return null

        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (
            imageFrameSourceActive &&
            videoSource != null &&
            localVideoTrack != null &&
            captureWidth == safeWidth &&
            captureHeight == safeHeight
        ) {
            return videoSource
        }

        stopLocalCamera()

        return try {
            val vSource = f.createVideoSource(false)
            vSource.capturerObserver.onCapturerStarted(true)
            videoSource = vSource
            localVideoTrack = f.createVideoTrack("LOCAL_VIDEO", vSource)
            captureWidth = safeWidth
            captureHeight = safeHeight
            captureRotationDegrees = 0
            imageFrameSourceActive = true
            attachLocalTracksToCameraPeer()
            startStatsMonitoring()
            AppLogger.debug(LogCategory.WEBRTC, "Image WebRTC source started with size: ${captureWidth}x${captureHeight}")
            vSource
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "VideoSource creation failed", t)
            null
        }
    }

    @Synchronized
    fun renderLocalTrack(track: VideoTrack, renderer: SurfaceViewRenderer) {
        track.addSink(renderer)
    }

    @Synchronized
    fun renderRemoteTrack(track: VideoTrack, renderer: SurfaceViewRenderer) {
        track.addSink(renderer)
    }

    @Synchronized
    fun attachLocalTracksToCameraPeer() {
        val pc = cameraPeerConnection ?: return
        val video = localVideoTrack ?: return

        try {
            var sender = pc.senders.find { it.track()?.id() == video.id() }
            if (sender == null) {
                sender = pc.addTrack(video, listOf("STREAM"))
                AppLogger.info(LogCategory.WEBRTC, "LOCAL_TRACK_ATTACHED")
            } else if (sender.track() !== video) {
                sender.setTrack(video, false)
                AppLogger.debug(LogCategory.WEBRTC, "Video track replaced on peer connection")
            }

            val params = sender.parameters
            params.degradationPreference =
                RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION

            if (params.encodings.isNotEmpty()) {
                params.encodings[0].minBitrateBps = VIDEO_MIN_BITRATE_BPS
                params.encodings[0].maxBitrateBps = VIDEO_MAX_BITRATE_BPS
                params.encodings[0].maxFramerate = VIDEO_MAX_FRAMERATE
            }
            sender.parameters = params
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "Local track attachment failed", t)
        }
    }


    @Synchronized
    fun createCameraPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        reuseExisting: Boolean = false
    ): PeerConnection? {
        val f = factory ?: return null
        cameraIceCandidateHandler = onIceCandidate
        if (reuseExisting) {
            cameraPeerConnection?.let { existing ->
                updateConnectionState(ConnectionSide.CAMERA, AppConnectionState.CONNECTING)
                return existing
            }
        }
        val oldConnection = cameraPeerConnection
        cameraPeerConnection = null
        disposePeerConnection(oldConnection)
        val generation = ++cameraPeerGeneration
        resetConnectionHealth(ConnectionSide.CAMERA)
        cancelConnectionJobs(ConnectionSide.CAMERA)
        updateConnectionState(ConnectionSide.CAMERA, AppConnectionState.CONNECTING)

        try {
            cameraPeerConnection = f.createPeerConnection(
                buildRtcConfig(),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        if (generation == cameraPeerGeneration) cameraIceCandidateHandler(candidate)
                    }
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                        if (generation != cameraPeerGeneration) return
                        AppLogger.debug(LogCategory.WEBRTC, "Camera ICE state: $s")
                        handleIceConnectionChange(ConnectionSide.CAMERA, s)
                    }
                    override fun onIceConnectionReceivingChange(b: Boolean) {
                        AppLogger.debug(LogCategory.WEBRTC, "Camera ICE receiving change: $b")
                        handleIceReceivingChange(ConnectionSide.CAMERA, b)
                    }
                    override fun onTrack(transceiver: RtpTransceiver) {}
                    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
                    override fun onSignalingChange(s: PeerConnection.SignalingState?) {
                        updateDiagnostics(signalingState = s?.name ?: "UNKNOWN")
                    }
                    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                    override fun onAddStream(s: MediaStream?) {}
                    override fun onRemoveStream(s: MediaStream?) {}
                    override fun onDataChannel(d: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(r: RtpReceiver?, ms: Array<out MediaStream>?) {}
                }
            )
            if (cameraPeerConnection != null) AppLogger.info(LogCategory.WEBRTC, "CAMERA_PEER_CREATED")
            attachLocalTracksToCameraPeer()
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "Camera PeerConnection creation failed", t)
        }
        return cameraPeerConnection
    }

    @Synchronized
    fun createControllerPeerConnection(
        roomCode: String,
        sessionGeneration: Long,
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrack: (VideoTrack) -> Unit,
        reuseExisting: Boolean = false
    ): PeerConnection? {
        val f = factory ?: return null
        if (!isControllerSessionActive(roomCode, sessionGeneration)) {
            AppLogger.warning(LogCategory.WEBRTC,
                "Ignoring stale peer-connection creation"
            )
            return null
        }
        controllerIceCandidateHandler = onIceCandidate
        controllerRemoteTrackHandler = onRemoteTrack
        if (reuseExisting) {
            if (controllerPeerOwner == ControllerSession(roomCode, sessionGeneration)) {
                controllerPeerConnection?.let { existing ->
                    updateConnectionState(ConnectionSide.CONTROLLER, AppConnectionState.CONNECTING)
                    return existing
                }
            } else if (controllerPeerConnection != null) {
                AppLogger.debug(LogCategory.WEBRTC,
                    "Ignoring peer reuse from an inactive session"
                )
            }
        }
        controllerPeerOwner?.let { owner -> disposeControllerPeerLocked(owner) }
        val generation = ++controllerPeerGeneration
        resetConnectionHealth(ConnectionSide.CONTROLLER)
        cancelConnectionJobs(ConnectionSide.CONTROLLER)
        updateConnectionState(ConnectionSide.CONTROLLER, AppConnectionState.CONNECTING)

        try {
            controllerPeerConnection = f.createPeerConnection(
                buildRtcConfig(),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        if (
                            generation == controllerPeerGeneration &&
                            isControllerSessionActive(roomCode, sessionGeneration)
                        ) controllerIceCandidateHandler(candidate)
                    }

                    override fun onTrack(transceiver: RtpTransceiver) {
                        if (!isControllerSessionActive(roomCode, sessionGeneration)) return
                        val track = transceiver.receiver.track()
                        if (track is VideoTrack) {
                            remoteVideoTrack = track
                            AppLogger.info(LogCategory.WEBRTC, "REMOTE_VIDEO_TRACK_RECEIVED")
                            controllerRemoteTrackHandler(track)
                        }
                    }

                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                        if (
                            generation != controllerPeerGeneration ||
                            !isControllerSessionActive(roomCode, sessionGeneration)
                        ) return
                        AppLogger.debug(LogCategory.WEBRTC, "Controller ICE state: $s")
                        handleIceConnectionChange(ConnectionSide.CONTROLLER, s)
                    }

                    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
                    override fun onSignalingChange(s: PeerConnection.SignalingState?) {
                        updateDiagnostics(signalingState = s?.name ?: "UNKNOWN")
                    }
                    override fun onIceConnectionReceivingChange(b: Boolean) {
                        AppLogger.debug(LogCategory.WEBRTC, "Controller ICE Receiving Change: $b")
                        handleIceReceivingChange(ConnectionSide.CONTROLLER, b)
                    }
                    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                    override fun onAddStream(s: MediaStream?) {}
                    override fun onRemoveStream(s: MediaStream?) {}
                    override fun onDataChannel(d: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(r: RtpReceiver?, ms: Array<out MediaStream>?) {}
                }
            )

            controllerPeerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                )
            )
            if (controllerPeerConnection != null) {
                controllerPeerOwner = ControllerSession(roomCode, sessionGeneration)
                AppLogger.info(LogCategory.WEBRTC, "CONTROLLER_PEER_CREATED")
            }
        } catch (t: Throwable) {
            controllerPeerOwner = null
            AppLogger.error(LogCategory.WEBRTC, "Controller PeerConnection creation failed", t)
        }

        return controllerPeerConnection
    }

    @Synchronized
    fun beginControllerSession(roomCode: String): Long {
        require(roomCode.isNotBlank()) { "roomCode must not be blank" }
        val previous = activeControllerSession
        val generation = ++controllerSessionCounter
        if (previous != null) {
            disposeControllerPeerLocked(previous)
        }
        activeControllerSession = ControllerSession(roomCode, generation)
        controllerSessionOwner = roomCode
        controllerRtcSessionId = null
        controllerRemoteCandidateKeys.clear()
        remoteVideoTrack = null
        AppLogger.debug(LogCategory.WEBRTC, "Beginning controller session generation=$generation")
        return generation
    }

    @Synchronized
    fun isControllerSessionActive(roomCode: String, sessionGeneration: Long): Boolean =
        activeControllerSession == ControllerSession(roomCode, sessionGeneration)

    @Synchronized
    fun clearControllerConnection(roomCode: String, sessionGeneration: Long): Boolean {
        val active = activeControllerSession
        if (active != ControllerSession(roomCode, sessionGeneration)) {
            val activeDescription = active?.let { "generation=${it.generation}" } ?: "none"
            AppLogger.debug(LogCategory.WEBRTC, "Ignoring cleanup because active=$activeDescription cleanupGeneration=$sessionGeneration"
            )
            return false
        }
        disposeControllerPeerLocked(active)
        activeControllerSession = null
        controllerSessionOwner = null
        return true
    }

    @Synchronized
    fun resetControllerPeer(roomCode: String, sessionGeneration: Long): Boolean {
        if (!isControllerSessionActive(roomCode, sessionGeneration)) return false
        disposeControllerPeerLocked(ControllerSession(roomCode, sessionGeneration))
        return true
    }

    private fun disposeControllerPeerLocked(owner: ControllerSession) {
        if (controllerPeerOwner != null && controllerPeerOwner != owner) {
            val activeOwner = controllerPeerOwner
            AppLogger.debug(LogCategory.WEBRTC, "Ignoring cleanup because active generation=${activeOwner?.generation} " +
                    "cleanupGeneration=${owner.generation}"
            )
            return
        }
        AppLogger.debug(LogCategory.WEBRTC, "Disposing controller session generation=${owner.generation}")
        cancelConnectionJobs(ConnectionSide.CONTROLLER)
        val connection = controllerPeerConnection
        controllerPeerConnection = null
        controllerPeerOwner = null
        controllerPeerGeneration += 1L
        controllerRemoteCandidateKeys.clear()
        controllerRtcSessionId = null
        remoteVideoTrack = null
        disposePeerConnection(connection)
        resetConnectionState(ConnectionSide.CONTROLLER, AppConnectionState.DISCONNECTED)
    }

    @Synchronized
    fun claimSession(cameraSide: Boolean, owner: String) {
        if (owner.isBlank()) return
        if (cameraSide) cameraSessionOwner = owner else controllerSessionOwner = owner
        AppLogger.debug(LogCategory.WEBRTC, "Session claimed for ${if (cameraSide) "camera" else "controller"}")
    }

    @Synchronized
    fun isSessionOwner(cameraSide: Boolean, owner: String): Boolean =
        owner.isNotBlank() &&
            (if (cameraSide) cameraSessionOwner == owner else controllerSessionOwner == owner)

    @Synchronized
    fun clearConnectionsIfOwned(cameraSide: Boolean, owner: String): Boolean {
        if (!isSessionOwner(cameraSide, owner)) {
            AppLogger.debug(LogCategory.WEBRTC, "Ignoring stale cleanup for ${if (cameraSide) "camera" else "controller"}"
            )
            return false
        }
        clearConnections()
        if (cameraSide) cameraSessionOwner = null else controllerSessionOwner = null
        return true
    }

    @Synchronized
    fun beginRemoteIceSession(cameraSide: Boolean, rtcSessionId: String) {
        if (cameraSide) {
            if (cameraRtcSessionId != rtcSessionId) {
                cameraRtcSessionId = rtcSessionId
                cameraRemoteCandidateKeys.clear()
            }
        } else if (controllerRtcSessionId != rtcSessionId) {
            controllerRtcSessionId = rtcSessionId
            controllerRemoteCandidateKeys.clear()
        }
    }

    @Synchronized
    fun isRemoteIceSessionActive(cameraSide: Boolean, rtcSessionId: String): Boolean =
        (if (cameraSide) cameraRtcSessionId else controllerRtcSessionId) == rtcSessionId

    @Synchronized
    fun addRemoteIceCandidate(cameraSide: Boolean, candidate: IceCandidate): Boolean {
        val connection = (if (cameraSide) cameraPeerConnection else controllerPeerConnection)
            ?: return false
        val keys = if (cameraSide) cameraRemoteCandidateKeys else controllerRemoteCandidateKeys
        val key = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
        if (!keys.add(key)) return true
        val added = runCatching { connection.addIceCandidate(candidate) }.getOrDefault(false)
        if (!added) keys.remove(key)
        return added
    }

    @Synchronized
    fun restartControllerIce(): Boolean = runCatching {
        controllerPeerConnection?.restartIce() ?: return false
        true
    }.getOrDefault(false)

    fun sessionDescriptionObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = { AppLogger.error(LogCategory.WEBRTC, "SDP operation failed") }
    ) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = onCreateSuccess(desc)
        override fun onSetSuccess() = onSetSuccess()
        override fun onCreateFailure(error: String?) = onFailure(error ?: "Unknown error")
        override fun onSetFailure(error: String?) = onFailure(error ?: "Unknown error")
    }

    @Synchronized
    fun clearConnections() {
        AppLogger.info(LogCategory.SESSION, "SESSION_CLEANUP_STARTED")
        try {
            val cameraHadActiveSession =
                cameraPeerConnection != null ||
                    cameraConnectionHealth.hasEverConnected ||
                    _cameraConnectionState.value != AppConnectionState.IDLE
            val controllerHadActiveSession =
                controllerPeerConnection != null ||
                    controllerConnectionHealth.hasEverConnected ||
                    _controllerConnectionState.value != AppConnectionState.IDLE
            cancelConnectionJobs(ConnectionSide.CAMERA)
            cancelConnectionJobs(ConnectionSide.CONTROLLER)
            val controllerConnection = controllerPeerConnection
            val cameraConnection = cameraPeerConnection
            controllerPeerConnection = null
            cameraPeerConnection = null
            controllerPeerOwner = null
            cameraPeerGeneration += 1
            controllerPeerGeneration += 1
            cameraRemoteCandidateKeys.clear()
            controllerRemoteCandidateKeys.clear()
            statsJob?.cancel()
            statsJob = null
            disposePeerConnection(controllerConnection)
            disposePeerConnection(cameraConnection)
            remoteVideoTrack = null
            resetConnectionState(
                ConnectionSide.CAMERA,
                if (cameraHadActiveSession) AppConnectionState.DISCONNECTED else AppConnectionState.IDLE
            )
            resetConnectionState(
                ConnectionSide.CONTROLLER,
                if (controllerHadActiveSession) AppConnectionState.DISCONNECTED else AppConnectionState.IDLE
            )
            AppLogger.info(LogCategory.WEBRTC, "WEBRTC_CONNECTION_CLEARED")
            AppLogger.info(LogCategory.SESSION, "SESSION_CLEANUP_COMPLETED")
        } catch (t: Throwable) {
            AppLogger.error(LogCategory.WEBRTC, "WebRTC connection cleanup failed", t)
        }
    }

    private fun disposePeerConnection(peerConnection: PeerConnection?) {
        if (peerConnection == null) return
        runCatching { peerConnection.close() }
            .onFailure { AppLogger.warning(LogCategory.WEBRTC, "PeerConnection close failed", it) }
        connectionScope.launch {
            // Native WebRTC may still deliver a final observer callback immediately after close().
            // Let that callback drain before freeing the native peer.
            delay(300L)
            runCatching { peerConnection.dispose() }
                .onFailure { AppLogger.warning(LogCategory.WEBRTC, "PeerConnection dispose failed", it) }
        }
    }

    private fun handleIceConnectionChange(
        side: ConnectionSide,
        state: PeerConnection.IceConnectionState?
    ) {
        state?.let { iceState ->
            val event = "ICE_${iceState.name}"
            if (iceState == PeerConnection.IceConnectionState.FAILED) {
                AppLogger.error(LogCategory.WEBRTC, event)
            } else {
                AppLogger.info(LogCategory.WEBRTC, event)
            }
        }
        updateDiagnostics(iceState = state?.name ?: "UNKNOWN")
        val health = getConnectionHealth(side)
        val currentState = getConnectionStateFlow(side).value
        val now = SystemClock.elapsedRealtime()

        when (state) {
            PeerConnection.IceConnectionState.NEW,
            PeerConnection.IceConnectionState.CHECKING -> {
                if (!health.hasEverConnected || currentState == AppConnectionState.IDLE) {
                    updateConnectionState(side, AppConnectionState.CONNECTING)
                }
            }

            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                val recoveredFromDrop =
                    health.lastDisconnectedAtMs > 0L &&
                        now - health.lastDisconnectedAtMs <= UNSTABLE_RECOVERY_WINDOW_MS
                health.hasEverConnected = true
                health.lastConnectedAtMs = now
                cancelDisconnectJob(side)
                if (
                    currentState == AppConnectionState.RETRYING ||
                    currentState == AppConnectionState.WEAK_NETWORK ||
                    recoveredFromDrop
                ) {
                    updateConnectionState(side, AppConnectionState.WEAK_NETWORK)
                    scheduleWeakNetworkReset(side)
                } else {
                    cancelWeakNetworkJob(side)
                    updateConnectionState(side, AppConnectionState.CONNECTED)
                }
            }

            PeerConnection.IceConnectionState.DISCONNECTED -> {
                health.lastDisconnectedAtMs = now
                cancelWeakNetworkJob(side)
                updateConnectionState(side, AppConnectionState.RETRYING)
                scheduleDisconnectTimeout(side)
            }

            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED -> {
                cancelConnectionJobs(side)
                health.lastDisconnectedAtMs = now
                updateConnectionState(side, AppConnectionState.DISCONNECTED)
            }

            null -> Unit
        }
    }

    private fun handleIceReceivingChange(side: ConnectionSide, isReceiving: Boolean) {
        if (isReceiving) {
            if (
                getConnectionStateFlow(side).value == AppConnectionState.WEAK_NETWORK &&
                getDisconnectJob(side)?.isActive != true
            ) {
                scheduleWeakNetworkReset(side)
            }
        }
    }

    private fun scheduleDisconnectTimeout(side: ConnectionSide) {
        if (getDisconnectJob(side)?.isActive == true) return

        val job = connectionScope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (getConnectionStateFlow(side).value == AppConnectionState.RETRYING) {
                updateConnectionState(side, AppConnectionState.DISCONNECTED)
            }
        }
        setDisconnectJob(side, job)
    }

    private fun scheduleWeakNetworkReset(side: ConnectionSide) {
        cancelWeakNetworkJob(side)
        val job = connectionScope.launch {
            delay(WEAK_NETWORK_HOLD_MS)
            if (getConnectionStateFlow(side).value == AppConnectionState.WEAK_NETWORK) {
                updateConnectionState(side, AppConnectionState.CONNECTED)
            }
        }
        setWeakNetworkJob(side, job)
    }

    private fun cancelConnectionJobs(side: ConnectionSide) {
        cancelDisconnectJob(side)
        cancelWeakNetworkJob(side)
    }

    private fun cancelDisconnectJob(side: ConnectionSide) {
        getDisconnectJob(side)?.cancel()
        setDisconnectJob(side, null)
    }

    private fun cancelWeakNetworkJob(side: ConnectionSide) {
        getWeakNetworkJob(side)?.cancel()
        setWeakNetworkJob(side, null)
    }

    private fun updateConnectionState(side: ConnectionSide, state: AppConnectionState) {
        val flow = getConnectionStateFlow(side)
        if (flow.value != state) {
            flow.value = state
            if (side == ConnectionSide.CAMERA) applyAdaptiveSenderProfile(state)
            updateDiagnostics(
                connectionState = state,
                reconnectAttempts = _diagnostics.value.reconnectAttempts +
                    if (state == AppConnectionState.RETRYING) 1 else 0
            )
        }
    }

    @Synchronized
    private fun applyAdaptiveSenderProfile(state: AppConnectionState) {
        val sender = cameraPeerConnection?.senders?.firstOrNull { it.track() is VideoTrack } ?: return
        runCatching {
            val params = sender.parameters
            params.degradationPreference = RtpParameters.DegradationPreference.BALANCED
            params.encodings.firstOrNull()?.let { encoding ->
                when (state) {
                    AppConnectionState.CONNECTED -> {
                        encoding.minBitrateBps = VIDEO_MIN_BITRATE_BPS
                        encoding.maxBitrateBps = VIDEO_MAX_BITRATE_BPS
                        encoding.maxFramerate = VIDEO_MAX_FRAMERATE
                    }
                    AppConnectionState.WEAK_NETWORK -> {
                        encoding.minBitrateBps = 600_000
                        encoding.maxBitrateBps = 2_500_000
                        encoding.maxFramerate = 22
                    }
                    AppConnectionState.RETRYING -> {
                        encoding.minBitrateBps = 300_000
                        encoding.maxBitrateBps = 900_000
                        encoding.maxFramerate = 12
                    }
                    else -> Unit
                }
            }
            sender.parameters = params
        }.onFailure { AppLogger.warning(LogCategory.WEBRTC, "Unable to adapt sender profile", it) }
    }

    @Synchronized
    private fun updatePreviewQuality(desired: PreviewQuality) {
        if (desired == previewQuality) {
            qualityUpgradeSamples = 0
            return
        }
        if (desired.ordinal < previewQuality.ordinal) {
            qualityUpgradeSamples += 1
            if (qualityUpgradeSamples < 4) return
        }
        qualityUpgradeSamples = 0
        val sender = cameraPeerConnection?.senders?.firstOrNull { it.track() is VideoTrack } ?: return
        runCatching {
            val params = sender.parameters
            params.degradationPreference = RtpParameters.DegradationPreference.BALANCED
            params.encodings.firstOrNull()?.let { encoding ->
                val profile = when (desired) {
                    PreviewQuality.EXCELLENT -> Triple(1_200_000, 5_000_000, 30)
                    PreviewQuality.GOOD -> Triple(700_000, 3_000_000, 27)
                    PreviewQuality.WEAK -> Triple(350_000, 1_500_000, 20)
                    PreviewQuality.VERY_WEAK -> Triple(180_000, 650_000, 15)
                }
                encoding.minBitrateBps = profile.first
                encoding.maxBitrateBps = profile.second
                encoding.maxFramerate = profile.third
            }
            sender.parameters = params
            previewQuality = desired
        }.onFailure { AppLogger.warning(LogCategory.WEBRTC, "Unable to update preview quality", it) }
    }

    private fun updateDiagnostics(
        connectionState: AppConnectionState = _diagnostics.value.connectionState,
        iceState: String = _diagnostics.value.iceState,
        signalingState: String = _diagnostics.value.signalingState,
        reconnectAttempts: Int = _diagnostics.value.reconnectAttempts
    ) {
        _diagnostics.value = _diagnostics.value.copy(
            connectionState = connectionState,
            iceState = iceState,
            signalingState = signalingState,
            reconnectAttempts = reconnectAttempts
        )
        if (BuildConfig.DEBUG) {
            AppLogger.debug(LogCategory.WEBRTC, _diagnostics.value.toString())
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        previousStatsAtMs = 0L
        previousBytesSent = 0L
        previousFramesSent = 0L
        statsJob = connectionScope.launch {
            while (true) {
                delay(2_000L)
                val peer = cameraPeerConnection ?: continue
                peer.getStats { report ->
                    val stats = report.statsMap.values
                    val outbound = stats.firstOrNull {
                        it.type == "outbound-rtp" && it.members["kind"] == "video"
                    } ?: return@getStats
                    val remoteInbound = stats.firstOrNull {
                        it.type == "remote-inbound-rtp" && it.members["kind"] == "video"
                    }
                    val now = SystemClock.elapsedRealtime()
                    val elapsedMs = (now - previousStatsAtMs).coerceAtLeast(1L)
                    val bytesSent = (outbound.members["bytesSent"] as? Number)?.toLong() ?: 0L
                    val framesSent = (outbound.members["framesEncoded"] as? Number)?.toLong() ?: 0L
                    val bitrate = if (previousStatsAtMs == 0L) 0L else {
                        (bytesSent - previousBytesSent).coerceAtLeast(0L) * 8_000L / elapsedMs
                    }
                    val fps = if (previousStatsAtMs == 0L) 0.0 else {
                        (framesSent - previousFramesSent).coerceAtLeast(0L) * 1_000.0 / elapsedMs
                    }
                    val packetsLost =
                        (remoteInbound?.members?.get("packetsLost") as? Number)?.toDouble() ?: 0.0
                    val packetsReceived =
                        (remoteInbound?.members?.get("packetsReceived") as? Number)?.toDouble() ?: 0.0
                    val packetLoss = if (packetsLost + packetsReceived <= 0.0) 0.0 else {
                        (packetsLost * 100.0 / (packetsLost + packetsReceived)).coerceIn(0.0, 100.0)
                    }
                    val latencyMs =
                        ((remoteInbound?.members?.get("roundTripTime") as? Number)?.toDouble()
                            ?: 0.0) * 1_000.0
                    val candidatePair = stats.firstOrNull {
                        it.type == "candidate-pair" &&
                            (it.members["selected"] == true || it.members["nominated"] == true)
                    }
                    val availableOutgoing =
                        (candidatePair?.members?.get("availableOutgoingBitrate") as? Number)
                            ?.toLong() ?: 0L
                    val jitterMs =
                        ((remoteInbound?.members?.get("jitter") as? Number)?.toDouble() ?: 0.0) * 1_000.0
                    val framesDropped =
                        (outbound.members["framesDropped"] as? Number)?.toLong() ?: 0L
                    val localCandidateId = candidatePair?.members?.get("localCandidateId") as? String
                    val localCandidate = localCandidateId?.let { report.statsMap[it] }
                    val candidateType =
                        localCandidate?.members?.get("candidateType") as? String ?: "unknown"
                    previousStatsAtMs = now
                    previousBytesSent = bytesSent
                    previousFramesSent = framesSent
                    _diagnostics.value = _diagnostics.value.copy(
                        averageLatencyMs = latencyMs,
                        packetLossPercent = packetLoss,
                        bitrateBps = bitrate,
                        framesPerSecond = fps,
                        availableOutgoingBitrateBps = availableOutgoing,
                        jitterMs = jitterMs,
                        framesDropped = framesDropped,
                        selectedCandidateType = candidateType,
                        usingRelay = candidateType.equals("relay", ignoreCase = true)
                    )
                    val desiredQuality = when {
                        packetLoss >= 12.0 || latencyMs >= 600.0 ||
                            availableOutgoing in 1 until 500_000 -> PreviewQuality.VERY_WEAK
                        packetLoss >= 5.0 || latencyMs >= 300.0 ||
                            availableOutgoing in 1 until 1_500_000 -> PreviewQuality.WEAK
                        packetLoss >= 2.0 || latencyMs >= 180.0 ||
                            availableOutgoing in 1 until 3_000_000 -> PreviewQuality.GOOD
                        else -> PreviewQuality.EXCELLENT
                    }
                    updatePreviewQuality(desiredQuality)
                    if (
                        packetLoss >= 8.0 &&
                        _cameraConnectionState.value == AppConnectionState.CONNECTED
                    ) {
                        updateConnectionState(ConnectionSide.CAMERA, AppConnectionState.WEAK_NETWORK)
                    }
                }
            }
        }
    }

    private fun resetConnectionState(side: ConnectionSide, state: AppConnectionState) {
        resetConnectionHealth(side)
        updateConnectionState(side, state)
    }

    private fun getConnectionStateFlow(side: ConnectionSide): MutableStateFlow<AppConnectionState> =
        when (side) {
            ConnectionSide.CAMERA -> _cameraConnectionState
            ConnectionSide.CONTROLLER -> _controllerConnectionState
        }

    private fun getDisconnectJob(side: ConnectionSide): Job? =
        when (side) {
            ConnectionSide.CAMERA -> cameraDisconnectJob
            ConnectionSide.CONTROLLER -> controllerDisconnectJob
        }

    private fun setDisconnectJob(side: ConnectionSide, job: Job?) {
        when (side) {
            ConnectionSide.CAMERA -> cameraDisconnectJob = job
            ConnectionSide.CONTROLLER -> controllerDisconnectJob = job
        }
    }

    private fun getWeakNetworkJob(side: ConnectionSide): Job? =
        when (side) {
            ConnectionSide.CAMERA -> cameraWeakNetworkJob
            ConnectionSide.CONTROLLER -> controllerWeakNetworkJob
        }

    private fun setWeakNetworkJob(side: ConnectionSide, job: Job?) {
        when (side) {
            ConnectionSide.CAMERA -> cameraWeakNetworkJob = job
            ConnectionSide.CONTROLLER -> controllerWeakNetworkJob = job
        }
    }

    private fun getConnectionHealth(side: ConnectionSide): ConnectionHealth =
        when (side) {
            ConnectionSide.CAMERA -> cameraConnectionHealth
            ConnectionSide.CONTROLLER -> controllerConnectionHealth
        }

    private fun resetConnectionHealth(side: ConnectionSide) {
        val health = getConnectionHealth(side)
        health.hasEverConnected = false
        health.lastConnectedAtMs = 0L
        health.lastDisconnectedAtMs = 0L
    }

    private fun Int.normalizedRotationDegrees(): Int =
        when (((this % 360) + 360) % 360) {
            90 -> 90
            180 -> 180
            270 -> 270
            else -> 0
        }
}
