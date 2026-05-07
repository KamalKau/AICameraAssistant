package com.example.aicameraassistant

import android.content.Context
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
            repository.addCameraIceCandidate(roomCode, candidate, rtcSessionId)
        }
    } ?: return false

    pc.setRemoteDescription(
        WebRtcSessionManager.sessionDescriptionObserver(
            onSetSuccess = {
                onRemoteDescriptionSet()
                pc.createAnswer(
                    WebRtcSessionManager.sessionDescriptionObserver(
                        onCreateSuccess = { desc ->
                            pc.setLocalDescription(
                                WebRtcSessionManager.sessionDescriptionObserver(
                                    onSetSuccess = {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            repository.saveAnswer(
                                                roomCode = roomCode,
                                                answerSdp = desc.description,
                                                rtcSessionId = rtcSessionId
                                            )
                                        }
                                    }
                                ),
                                desc
                            )
                        }
                    ),
                    MediaConstraints()
                )
            }
        ),
        SessionDescription(SessionDescription.Type.OFFER, offerSdp)
    )
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
                repository.addControllerIceCandidate(roomCode, candidate, rtcSessionId)
            }
        },
        onRemoteTrack = { videoTrack ->
            onRemoteTrackReady(videoTrack)
        }
    ) ?: return false

    pc.createOffer(
        WebRtcSessionManager.sessionDescriptionObserver(
            onCreateSuccess = { desc ->
                pc.setLocalDescription(
                    WebRtcSessionManager.sessionDescriptionObserver(
                        onSetSuccess = {
                            @Suppress("OPT_IN_USAGE")
                            GlobalScope.launch {
                                repository.saveOffer(
                                    roomCode = roomCode,
                                    offerSdp = desc.description,
                                    rtcSessionId = rtcSessionId
                                )
                            }
                        }
                    ),
                    desc
                )
            }
        ),
        MediaConstraints()
    )
    return true
}
