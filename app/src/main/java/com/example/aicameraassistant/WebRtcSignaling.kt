package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
    onRemoteDescriptionSet: () -> Unit
): Boolean {
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createCameraPeerConnection { candidate ->
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.addCameraIceCandidate(roomCode, candidate, rtcSessionId) }
                .onFailure { Log.w("WEBRTC_LOG", "Unable to publish camera ICE candidate", it) }
        }
    } ?: return false

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
    rtcSessionId: String,
    repository: FirebaseRoomRepository,
    onRemoteTrackReady: (VideoTrack) -> Unit
): Boolean {
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createControllerPeerConnection(
        onIceCandidate = { candidate ->
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch {
                runCatching { repository.addControllerIceCandidate(roomCode, candidate, rtcSessionId) }
                    .onFailure { Log.w("WEBRTC_LOG", "Unable to publish controller ICE candidate", it) }
            }
        },
        onRemoteTrack = { videoTrack ->
            onRemoteTrackReady(videoTrack)
        }
    ) ?: return false

    runCatching {
        pc.createOffer(
            WebRtcSessionManager.sessionDescriptionObserver(
                onCreateSuccess = { desc ->
                    runCatching {
                        pc.setLocalDescription(
                            WebRtcSessionManager.sessionDescriptionObserver(
                                onSetSuccess = {
                                    @Suppress("OPT_IN_USAGE")
                                    GlobalScope.launch {
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
            MediaConstraints()
        )
    }.onFailure {
        return false
    }
    return true
}
