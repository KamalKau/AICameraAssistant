package com.example.aicameraassistant

import android.content.Context
import android.util.Log
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

object WebRtcSessionManager {
    private enum class ConnectionSide {
        CAMERA,
        CONTROLLER
    }

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

    private var captureWidth: Int = 0
    private var captureHeight: Int = 0

    private val _cameraConnectionState = MutableStateFlow(AppConnectionState.IDLE)
    val cameraConnectionState: StateFlow<AppConnectionState> = _cameraConnectionState.asStateFlow()

    private val _controllerConnectionState = MutableStateFlow(AppConnectionState.IDLE)
    val controllerConnectionState: StateFlow<AppConnectionState> =
        _controllerConnectionState.asStateFlow()

    private var cameraDisconnectJob: Job? = null
    private var controllerDisconnectJob: Job? = null
    private var cameraWeakNetworkJob: Job? = null
    private var controllerWeakNetworkJob: Job? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized && _factory != null) return

        Log.d("WEBRTC_LOG", "Initializing WebRtcSessionManager...")
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
            Log.d("WEBRTC_LOG", "PeerConnectionFactory initialized successfully")
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Critical failure during WebRTC initialization", t)
        }
    }

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=udp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

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

        if (
            surfaceTextureHelper != null &&
            cachedSurface != null &&
            captureWidth == safeWidth &&
            captureHeight == safeHeight
        ) {
            return cachedSurface
        }

        if (
            surfaceTextureHelper != null &&
            (captureWidth != safeWidth || captureHeight != safeHeight)
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

            captureWidth = safeWidth
            captureHeight = safeHeight

            val surface = Surface(helper.surfaceTexture)
            cachedSurface = surface

            Log.d(
                "WEBRTC_LOG",
                "WebRTC source started with size: ${captureWidth}x${captureHeight}, rotation=$rotationDegrees"
            )

            attachLocalTracksToCameraPeer()
            surface
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Failed to start camera source", t)
            null
        }
    }

    @Synchronized
    fun stopLocalCamera() {
        Log.d("WEBRTC_LOG", "Stopping local camera...")
        try {
            surfaceTextureHelper?.stopListening()
            cachedSurface?.release()
            cachedSurface = null
            videoSource?.capturerObserver?.onCapturerStopped()

            localVideoTrack = null
            localAudioTrack = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            videoSource?.dispose()
            videoSource = null

            audioSource?.dispose()
            audioSource = null

            captureWidth = 0
            captureHeight = 0
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Error during cleanup", t)
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
                Log.d("WEBRTC_LOG", "Video track attached to peer connection")
            }

            val params = sender.parameters
            params.degradationPreference =
                RtpParameters.DegradationPreference.BALANCED

            if (params.encodings.isNotEmpty()) {
                params.encodings[0].minBitrateBps = 1_500_000
                params.encodings[0].maxBitrateBps = 6_000_000
                params.encodings[0].maxFramerate = 30
            }
            sender.parameters = params
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Failed to attach tracks", t)
        }
    }

    @Synchronized
    fun createCameraPeerConnection(onIceCandidate: (IceCandidate) -> Unit): PeerConnection? {
        val f = factory ?: return null
        cameraPeerConnection?.dispose()
        cameraPeerConnection = null
        cancelConnectionJobs(ConnectionSide.CAMERA)
        updateConnectionState(ConnectionSide.CAMERA, AppConnectionState.CONNECTING)

        try {
            cameraPeerConnection = f.createPeerConnection(
                buildRtcConfig(),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) = onIceCandidate(candidate)
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                        Log.d("WEBRTC_LOG", "Camera ICE state: $s")
                        handleIceConnectionChange(ConnectionSide.CAMERA, s)
                    }
                    override fun onTrack(transceiver: RtpTransceiver) {}
                    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
                    override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionReceivingChange(b: Boolean) {}
                    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                    override fun onAddStream(s: MediaStream?) {}
                    override fun onRemoveStream(s: MediaStream?) {}
                    override fun onDataChannel(d: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(r: RtpReceiver?, ms: Array<out MediaStream>?) {}
                }
            )
            attachLocalTracksToCameraPeer()
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Failed to create camera peer connection", t)
        }
        return cameraPeerConnection
    }

    @Synchronized
    fun createControllerPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrack: (VideoTrack) -> Unit
    ): PeerConnection? {
        val f = factory ?: return null
        controllerPeerConnection?.dispose()
        controllerPeerConnection = null
        cancelConnectionJobs(ConnectionSide.CONTROLLER)
        updateConnectionState(ConnectionSide.CONTROLLER, AppConnectionState.CONNECTING)

        try {
            controllerPeerConnection = f.createPeerConnection(
                buildRtcConfig(),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) = onIceCandidate(candidate)

                    override fun onTrack(transceiver: RtpTransceiver) {
                        val track = transceiver.receiver.track()
                        if (track is VideoTrack) {
                            remoteVideoTrack = track
                            onRemoteTrack(track)
                        }
                    }

                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                        Log.d("WEBRTC_LOG", "Controller ICE state: $s")
                        handleIceConnectionChange(ConnectionSide.CONTROLLER, s)
                    }

                    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
                    override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionReceivingChange(b: Boolean) {
                        Log.d("WEBRTC_LOG", "Controller ICE Receiving Change: $b")
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
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Failed to create controller peer connection", t)
        }

        return controllerPeerConnection
    }

    fun sessionDescriptionObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = { Log.e("WEBRTC_LOG", "SDP Error: $it") }
    ) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = onCreateSuccess(desc)
        override fun onSetSuccess() = onSetSuccess()
        override fun onCreateFailure(error: String?) = onFailure(error ?: "Unknown error")
        override fun onSetFailure(error: String?) = onFailure(error ?: "Unknown error")
    }

    @Synchronized
    fun clearConnections() {
        try {
            cancelConnectionJobs(ConnectionSide.CAMERA)
            cancelConnectionJobs(ConnectionSide.CONTROLLER)
            controllerPeerConnection?.dispose()
            controllerPeerConnection = null
            cameraPeerConnection?.dispose()
            cameraPeerConnection = null
            remoteVideoTrack = null
            updateConnectionState(ConnectionSide.CAMERA, AppConnectionState.DISCONNECTED)
            updateConnectionState(ConnectionSide.CONTROLLER, AppConnectionState.DISCONNECTED)
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Error clearing connections", t)
        }
    }

    private fun handleIceConnectionChange(
        side: ConnectionSide,
        state: PeerConnection.IceConnectionState?
    ) {
        when (state) {
            PeerConnection.IceConnectionState.NEW,
            PeerConnection.IceConnectionState.CHECKING -> {
                if (getConnectionStateFlow(side).value != AppConnectionState.CONNECTED) {
                    updateConnectionState(side, AppConnectionState.CONNECTING)
                }
            }

            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                val wasRetrying =
                    getConnectionStateFlow(side).value == AppConnectionState.RETRYING
                cancelDisconnectJob(side)
                if (wasRetrying) {
                    updateConnectionState(side, AppConnectionState.WEAK_NETWORK)
                    scheduleWeakNetworkReset(side)
                } else {
                    cancelWeakNetworkJob(side)
                    updateConnectionState(side, AppConnectionState.CONNECTED)
                }
            }

            PeerConnection.IceConnectionState.DISCONNECTED -> {
                cancelWeakNetworkJob(side)
                updateConnectionState(side, AppConnectionState.RETRYING)
                scheduleDisconnectTimeout(side)
            }

            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED -> {
                cancelConnectionJobs(side)
                updateConnectionState(side, AppConnectionState.DISCONNECTED)
            }

            null -> Unit
        }
    }

    private fun scheduleDisconnectTimeout(side: ConnectionSide) {
        if (getDisconnectJob(side)?.isActive == true) return

        val job = connectionScope.launch {
            delay(5_000)
            updateConnectionState(side, AppConnectionState.DISCONNECTED)
        }
        setDisconnectJob(side, job)
    }

    private fun scheduleWeakNetworkReset(side: ConnectionSide) {
        cancelWeakNetworkJob(side)
        val job = connectionScope.launch {
            delay(2_500)
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
        }
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
}
