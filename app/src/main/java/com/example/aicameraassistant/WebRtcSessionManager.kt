package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import android.view.Surface
import org.webrtc.*

object WebRtcSessionManager {

    private var initialized = false

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

    @Synchronized
    fun initialize(context: Context) {
        if (initialized && _factory != null) return
        
        Log.d("WEBRTC_INIT", "Initializing WebRtcSessionManager...")
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
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()

            initialized = true
            Log.d("WEBRTC_INIT", "PeerConnectionFactory initialized successfully")
        } catch (t: Throwable) {
            Log.e("WEBRTC_INIT", "Critical failure during WebRTC initialization", t)
        }
    }

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=udp")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )
        return PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    @Synchronized
    fun startWebRtcCameraSource(context: Context): Surface? {
        initialize(context)
        val f = factory ?: return null
        if (surfaceTextureHelper != null) return cachedSurface

        return try {
            val helper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)
            // Force high texture resolution
            helper.setTextureSize(1920, 1080)
            
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
            
            val surface = Surface(helper.surfaceTexture)
            cachedSurface = surface
            
            attachLocalTracksToCameraPeer()
            surface
        } catch (t: Throwable) {
            Log.e("WEBRTC_CAMERA", "Failed to start camera source", t)
            null
        }
    }

    @Synchronized
    fun stopLocalCamera() {
        Log.d("WEBRTC_CAMERA", "Stopping local camera...")
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
        } catch (t: Throwable) {
            Log.e("WEBRTC_CAMERA", "Error during cleanup", t)
        }
    }

    @Synchronized
    fun renderLocalTrack(track: VideoTrack, renderer: SurfaceViewRenderer) {
        try {
            track.addSink(renderer)
        } catch (t: Throwable) {
            Log.e("WEBRTC_RENDER", "Failed to add sink to local track", t)
        }
    }

    @Synchronized
    fun removeLocalSink(renderer: SurfaceViewRenderer) {
        localVideoTrack?.removeSink(renderer)
    }

    @Synchronized
    fun renderRemoteTrack(track: VideoTrack, renderer: SurfaceViewRenderer) {
        try {
            track.addSink(renderer)
        } catch (t: Throwable) {
            Log.e("WEBRTC_RENDER", "Failed to add sink to remote track", t)
        }
    }

    @Synchronized
    fun removeRemoteSink(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(renderer)
    }

    @Synchronized
    fun attachLocalTracksToCameraPeer() {
        val pc = cameraPeerConnection ?: return
        val video = localVideoTrack ?: return

        try {
            val alreadyHasVideo = pc.senders.any { it.track()?.id() == video.id() }
            if (!alreadyHasVideo) {
                pc.addTrack(video, listOf("STREAM"))
            }

            localAudioTrack?.let { audio ->
                val alreadyHasAudio = pc.senders.any { it.track()?.id() == audio.id() }
                if (!alreadyHasAudio) pc.addTrack(audio, listOf("STREAM"))
            }
        } catch (t: Throwable) {
            Log.e("WEBRTC_ATTACH", "Failed to attach tracks", t)
        }
    }

    @Synchronized
    fun createCameraPeerConnection(onIceCandidate: (IceCandidate) -> Unit): PeerConnection? {
        val f = factory ?: return null
        if (cameraPeerConnection != null) return cameraPeerConnection

        try {
            cameraPeerConnection = f.createPeerConnection(
                buildRtcConfig(),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) = onIceCandidate(candidate)
                    override fun onTrack(transceiver: RtpTransceiver) {}
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                        Log.d("WEBRTC_CAM", "ICE state: $s")
                    }
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
            Log.e("WEBRTC_PC", "Failed to create camera peer connection", t)
        }
        return cameraPeerConnection
    }

    @Synchronized
    fun createControllerPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrack: (VideoTrack) -> Unit
    ): PeerConnection? {
        val f = factory ?: return null
        if (controllerPeerConnection != null) return controllerPeerConnection

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
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
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
            controllerPeerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
        } catch (t: Throwable) {
            Log.e("WEBRTC_PC", "Failed to create controller peer connection", t)
        }
        return controllerPeerConnection
    }

    fun sessionDescriptionObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = { Log.e("WEBRTC_SDP", it) }
    ) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = onCreateSuccess(desc)
        override fun onSetSuccess() = onSetSuccess()
        override fun onCreateFailure(error: String?) = onFailure(error ?: "Unknown error")
        override fun onSetFailure(error: String?) = onFailure(error ?: "Unknown error")
    }

    @Synchronized
    fun clearConnections() {
        try {
            controllerPeerConnection?.dispose()
            controllerPeerConnection = null
            cameraPeerConnection?.dispose()
            cameraPeerConnection = null
            remoteVideoTrack = null
        } catch (t: Throwable) {
            Log.e("WEBRTC_INIT", "Error clearing connections", t)
        }
    }
}
