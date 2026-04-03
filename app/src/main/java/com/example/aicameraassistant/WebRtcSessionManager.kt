package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

object WebRtcSessionManager {

    private var initialized = false

    val eglBase: EglBase by lazy { EglBase.create() }

    lateinit var factory: PeerConnectionFactory
        private set

    var controllerPeerConnection: PeerConnection? = null
    var cameraPeerConnection: PeerConnection? = null

    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    var remoteVideoTrack: VideoTrack? = null

    private var videoCapturer: VideoCapturer? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    var isFrontCamera: Boolean = false
        private set

    fun initialize(context: Context) {
        if (initialized) return

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()

        initialized = true
        Log.d("WEBRTC_INIT", "PeerConnectionFactory initialized")
    }

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),

            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=udp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),

            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),

            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),

            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        return PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    fun startLocalCamera(context: Context, useFrontCamera: Boolean) {
        initialize(context)

        Log.d("WEBRTC_CAMERA", "startLocalCamera called, front=$useFrontCamera")

        if (localVideoTrack != null) {
            Log.d("WEBRTC_CAMERA", "localVideoTrack already exists, skipping camera restart")
            return
        }

        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        val selectedDevice = deviceNames.firstOrNull { name ->
            if (useFrontCamera) enumerator.isFrontFacing(name) else enumerator.isBackFacing(name)
        } ?: deviceNames.firstOrNull()

        Log.d("WEBRTC_CAMERA", "selectedDevice=$selectedDevice")

        val capturer = selectedDevice?.let { enumerator.createCapturer(it, null) }
            ?: error("No camera capturer available")

        videoCapturer = capturer
        cameraVideoCapturer = capturer as? CameraVideoCapturer
        isFrontCamera = useFrontCamera

        Log.d("WEBRTC_CAMERA", "capturer selected")

        surfaceTextureHelper =
            SurfaceTextureHelper.create("CameraCaptureThread", eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(false)
        audioSource = factory.createAudioSource(MediaConstraints())

        capturer.initialize(
            surfaceTextureHelper,
            context,
            videoSource!!.capturerObserver
        )

        capturer.startCapture(1280, 720, 30)
        Log.d("WEBRTC_CAMERA", "startCapture started")

        localVideoTrack = factory.createVideoTrack("LOCAL_VIDEO", videoSource)
        localAudioTrack = factory.createAudioTrack("LOCAL_AUDIO", audioSource)

        Log.d("WEBRTC_CAMERA", "local tracks created")
    }

    fun switchCamera(onDone: ((Boolean) -> Unit)? = null) {
        val capturer = cameraVideoCapturer
        if (capturer == null) {
            Log.d("WEBRTC_CAMERA", "switchCamera skipped, capturer is null")
            onDone?.invoke(isFrontCamera)
            return
        }

        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                Log.d("WEBRTC_CAMERA", "switchCamera done, front=$isFront")
                onDone?.invoke(isFront)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e("WEBRTC_CAMERA", "switchCamera error=$errorDescription")
                onDone?.invoke(isFrontCamera)
            }
        })
    }

    fun stopLocalCamera() {
        Log.d("WEBRTC_CAMERA", "stopLocalCamera called")

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e("WEBRTC_CAMERA", "stopCapture failed", e)
        }

        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_CAMERA", "capturer dispose failed", e)
        }
        videoCapturer = null
        cameraVideoCapturer = null

        try {
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_CAMERA", "surfaceTextureHelper dispose failed", e)
        }
        surfaceTextureHelper = null

        try {
            videoSource?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_CAMERA", "videoSource dispose failed", e)
        }
        videoSource = null

        try {
            audioSource?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_CAMERA", "audioSource dispose failed", e)
        }
        audioSource = null

        localVideoTrack = null
        localAudioTrack = null
    }

    fun addLocalTracksToCameraPeer() {
        val pc = cameraPeerConnection ?: run {
            Log.d("WEBRTC_CAMERA", "cameraPeerConnection is null, cannot add local tracks")
            return
        }

        val video = localVideoTrack ?: run {
            Log.d("WEBRTC_CAMERA", "localVideoTrack is null, cannot add local tracks")
            return
        }

        val audio = localAudioTrack

        Log.d("WEBRTC_CAMERA", "adding local tracks to peer")

        pc.addTrack(video, listOf("STREAM"))
        if (audio != null) {
            pc.addTrack(audio, listOf("STREAM"))
        }
    }

    fun createControllerPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrack: (VideoTrack) -> Unit
    ): PeerConnection? {
        try {
            controllerPeerConnection?.dispose()
        } catch (_: Exception) {
        }
        controllerPeerConnection = null
        remoteVideoTrack = null

        controllerPeerConnection = factory.createPeerConnection(
            buildRtcConfig(),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d("WEBRTC_CTRL", "ICE candidate generated")
                    onIceCandidate(candidate)
                }

                override fun onTrack(transceiver: RtpTransceiver) {
                    val track = transceiver.receiver.track()
                    Log.d("WEBRTC_CTRL", "onTrack called, track=$track")
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        onRemoteTrack(track)
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WEBRTC_CTRL", "ICE state=$state")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d("WEBRTC_CTRL", "ICE gathering state=$state")
                }
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {
                    Log.d("WEBRTC_CTRL", "onRenegotiationNeeded")
                }
                override fun onAddTrack(
                    receiver: RtpReceiver?,
                    mediaStreams: Array<out MediaStream>?
                ) {}
            }
        )

        controllerPeerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
            )
        )

        Log.d("WEBRTC_CTRL", "controllerPeerConnection created with RECV_ONLY video transceiver")
        return controllerPeerConnection
    }

    fun createCameraPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit
    ): PeerConnection? {
        try {
            cameraPeerConnection?.dispose()
        } catch (_: Exception) {
        }
        cameraPeerConnection = null

        cameraPeerConnection = factory.createPeerConnection(
            buildRtcConfig(),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d("WEBRTC_CAM", "ICE candidate generated")
                    onIceCandidate(candidate)
                }

                override fun onTrack(transceiver: RtpTransceiver) {
                    Log.d("WEBRTC_CAM", "onTrack called on camera side")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WEBRTC_CAM", "ICE state=$state")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d("WEBRTC_CAM", "ICE gathering state=$state")
                }
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {
                    Log.d("WEBRTC_CAM", "onRenegotiationNeeded")
                }
                override fun onAddTrack(
                    receiver: RtpReceiver?,
                    mediaStreams: Array<out MediaStream>?
                ) {}
            }
        )

        Log.d("WEBRTC_CAM", "cameraPeerConnection created")
        return cameraPeerConnection
    }

    fun renderRemoteTrack(videoTrack: VideoTrack, renderer: SurfaceViewRenderer) {
        Log.d("WEBRTC_CTRL", "Attaching remote video track to renderer")
        videoTrack.addSink(renderer)
    }

    fun renderLocalTrack(videoTrack: VideoTrack, renderer: SurfaceViewRenderer) {
        Log.d("WEBRTC_CAMERA", "Attaching local video track to renderer")
        videoTrack.addSink(renderer)
    }

    fun sessionDescriptionObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onCreateFailure: (String?) -> Unit = {},
        onSetFailure: (String?) -> Unit = {}
    ): SdpObserver {
        return object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) = onCreateSuccess(desc)
            override fun onSetSuccess() = onSetSuccess()
            override fun onCreateFailure(error: String?) = onCreateFailure(error)
            override fun onSetFailure(error: String?) = onSetFailure(error)
        }
    }

    fun clearConnections() {
        Log.d("WEBRTC_INIT", "clearConnections called")

        try {
            controllerPeerConnection?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_INIT", "controllerPeerConnection dispose failed", e)
        }
        controllerPeerConnection = null

        try {
            cameraPeerConnection?.dispose()
        } catch (e: Exception) {
            Log.e("WEBRTC_INIT", "cameraPeerConnection dispose failed", e)
        }
        cameraPeerConnection = null

        remoteVideoTrack = null
    }
}
