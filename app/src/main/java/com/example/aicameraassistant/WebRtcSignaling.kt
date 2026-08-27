package com.example.aicameraassistant

import android.content.Context
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
    AppLogger.debug(LogCategory.WEBRTC, "SESSION_TRACE", "createAnswer room=$roomCode rtc=$rtcSessionId reuse=$reusePeerConnection")
    WebRtcSessionManager.initialize(context)

    val pc = WebRtcSessionManager.createCameraPeerConnection(
        onIceCandidate = { candidate ->
            CoroutineScope(Dispatchers.IO).launch {
                if (
                    !WebRtcSessionManager.isSessionOwner(cameraSide = true, owner = roomCode) ||
                    !WebRtcSessionManager.isRemoteIceSessionActive(true, rtcSessionId)
                ) return@launch
                runCatching { repository.addCameraIceCandidate(roomCode, candidate, rtcSessionId) }
                    .onFailure { AppLogger.warning(LogCategory.WEBRTC, "WEBRTC_LOG", "Unable to publish camera ICE candidate", it) }
            }
        },
        reuseExisting = reusePeerConnection
    ) ?: return false

    runCatching {
        pc.setRemoteDescription(
            WebRtcSessionManager.sessionDescriptionObserver(
                onSetSuccess = {
                    if (!WebRtcSessionManager.isRemoteIceSessionActive(true, rtcSessionId)) {
                        return@sessionDescriptionObserver
                    }
                    onRemoteDescriptionSet()
                    runCatching {
                        pc.createAnswer(
                            WebRtcSessionManager.sessionDescriptionObserver(
                                onCreateSuccess = { desc ->
                                    if (!WebRtcSessionManager.isRemoteIceSessionActive(true, rtcSessionId)) {
                                        return@sessionDescriptionObserver
                                    }
                                    runCatching {
                                        pc.setLocalDescription(
                                            WebRtcSessionManager.sessionDescriptionObserver(
                                                onSetSuccess = {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        if (!WebRtcSessionManager.isRemoteIceSessionActive(
                                                                true,
                                                                rtcSessionId
                                                            )
                                                        ) return@launch
                                                        runCatching {
                                                            repository.saveAnswer(
                                                                roomCode = roomCode,
                                                                answerSdp = desc.description,
                                                                rtcSessionId = rtcSessionId,
                                                                signalingGeneration = signalingGenerationFromRtcId(
                                                                    rtcSessionId
                                                                )
                                                            )
                                                        }.onFailure {
                                                            AppLogger.warning(LogCategory.WEBRTC, "WEBRTC_LOG", "Unable to save WebRTC answer", it)
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
    signalingGeneration: Long,
    rtcSessionId: String,
    repository: FirebaseRoomRepository,
    onRemoteTrackReady: (VideoTrack) -> Unit,
    iceRestart: Boolean = false
): Boolean {
    AppLogger.debug(LogCategory.WEBRTC,
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
                    .onFailure { AppLogger.warning(LogCategory.WEBRTC, "WEBRTC_LOG", "Unable to publish controller ICE candidate", it) }
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
                                                rtcSessionId = rtcSessionId,
                                                signalingGeneration = signalingGeneration
                                            )
                                        }.onFailure {
                                            AppLogger.warning(LogCategory.WEBRTC, "WEBRTC_LOG", "Unable to save WebRTC offer", it)
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

private fun signalingGenerationFromRtcId(rtcSessionId: String): Long =
    rtcSessionId.split('-').getOrNull(1)?.toLongOrNull() ?: 0L
