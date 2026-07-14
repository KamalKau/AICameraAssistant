package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

fun createSharedAnswer(
    context: Context,
    roomCode: String,
    offerSdp: String,
    rtcSessionId: String,
    repository: FirebaseRoomRepository,
    onRemoteDescriptionSet: () -> Unit,
    reusePeerConnection: Boolean = false
): Boolean {
    Log.d("SESSION_TRACE", "createAnswer room=$roomCode rtc=$rtcSessionId reuse=$reusePeerConnection")
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createCameraPeerConnection(
        onIceCandidate = { candidate ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { repository.addCameraIceCandidate(roomCode, candidate, rtcSessionId) }
                    .onFailure { Log.w("WEBRTC_LOG", "Unable to publish camera ICE candidate", it) }
            }
        },
        reuseExisting = reusePeerConnection
    ) ?: return false

    runCatching {
        pc.setRemoteDescription(
            WebRtcSessionManager.sessionDescriptionObserver(
                onSetSuccess = {
                    onRemoteDescriptionSet()
                    runCatching {
                        pc.createAnswer(
                            WebRtcSessionManager.sessionDescriptionObserver(
                                onCreateSuccess = { desc ->
                                    runCatching {
                                        pc.setLocalDescription(
                                            WebRtcSessionManager.sessionDescriptionObserver(
                                                onSetSuccess = {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        runCatching {
                                                            repository.saveAnswer(
                                                                roomCode = roomCode,
                                                                answerSdp = desc.description,
                                                                rtcSessionId = rtcSessionId
                                                            )
                                                        }.onFailure {
                                                            Log.w("WEBRTC_LOG", "Unable to save WebRTC answer", it)
                                                        }
                                                    }
                                                }
                                            ),
                                            desc
                                        )
                                    }
                                }
                            ),
                            MediaConstraints()
                        )
                    }
                }
            ),
            SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        )
    }.onFailure {
        return false
    }
    return true
}

fun createSharedOffer(
    context: Context,
    roomCode: String,
    sessionGeneration: Long,
    rtcSessionId: String,
    repository: FirebaseRoomRepository,
    onRemoteTrackReady: (VideoTrack) -> Unit,
    iceRestart: Boolean = false
): Boolean {
    Log.d(
        "SESSION_TRACE",
        "Creating offer room=$roomCode generation=$sessionGeneration rtc=$rtcSessionId restart=$iceRestart"
    )
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createControllerPeerConnection(
        roomCode = roomCode,
        sessionGeneration = sessionGeneration,
        onIceCandidate = { candidate ->
            CoroutineScope(Dispatchers.IO).launch {
                if (!WebRtcSessionManager.isControllerSessionActive(roomCode, sessionGeneration)) {
                    return@launch
                }
                runCatching { repository.addControllerIceCandidate(roomCode, candidate, rtcSessionId) }
                    .onFailure { Log.w("WEBRTC_LOG", "Unable to publish controller ICE candidate", it) }
            }
        },
        onRemoteTrack = { videoTrack ->
            if (WebRtcSessionManager.isControllerSessionActive(roomCode, sessionGeneration)) {
                onRemoteTrackReady(videoTrack)
            }
        },
        reuseExisting = iceRestart
    ) ?: return false

    if (iceRestart && !WebRtcSessionManager.restartControllerIce()) return false

    runCatching {
        pc.createOffer(
            WebRtcSessionManager.sessionDescriptionObserver(
                onCreateSuccess = { desc ->
                    if (!WebRtcSessionManager.isControllerSessionActive(roomCode, sessionGeneration)) {
                        return@sessionDescriptionObserver
                    }
                    runCatching {
                        pc.setLocalDescription(
                            WebRtcSessionManager.sessionDescriptionObserver(
                                onSetSuccess = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        if (!WebRtcSessionManager.isControllerSessionActive(
                                                roomCode,
                                                sessionGeneration
                                            )
                                        ) return@launch
                                        runCatching {
                                            repository.saveOffer(
                                                roomCode = roomCode,
                                                offerSdp = desc.description,
                                                rtcSessionId = rtcSessionId
                                            )
                                        }.onFailure {
                                            Log.w("WEBRTC_LOG", "Unable to save WebRTC offer", it)
                                        }
                                    }
                                }
                            ),
                            desc
                        )
                    }
                }
            ),
            MediaConstraints().apply {
                if (iceRestart) {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                }
            }
        )
    }.onFailure {
        return false
    }
    return true
}
