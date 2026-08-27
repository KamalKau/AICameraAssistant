package com.example.aicameraassistant

/**
 * Pure signaling state. Keeping this independent of native WebRTC makes session reset and
 * candidate ordering deterministic and unit-testable.
 */
class WebRtcSessionState<T> {
    var controllerPeerConnection: Any? = null
        private set
    var cameraPeerConnection: Any? = null
        private set
    var remoteVideoTrack: Any? = null
        private set
    var offerCreated: Boolean = false
        private set
    var answerCreated: Boolean = false
        private set
    var remoteDescriptionSet: Boolean = false
        private set
    private val pendingCandidates = mutableListOf<T>()

    val pendingCandidateCount: Int get() = pendingCandidates.size

    fun attachControllerPeer(value: Any) { controllerPeerConnection = value }
    fun attachCameraPeer(value: Any) { cameraPeerConnection = value }
    fun attachRemoteVideoTrack(value: Any) { remoteVideoTrack = value }

    fun markOfferCreated(): Boolean {
        if (offerCreated) return false
        offerCreated = true
        return true
    }

    fun markAnswerCreated() {
        answerCreated = true
    }

    fun receiveCandidate(candidate: T, apply: (T) -> Unit) {
        if (remoteDescriptionSet) apply(candidate) else pendingCandidates += candidate
    }

    fun applyRemoteDescription(applyCandidate: (T) -> Unit) {
        remoteDescriptionSet = true
        pendingCandidates.forEach(applyCandidate)
        pendingCandidates.clear()
    }

    fun clear() {
        controllerPeerConnection = null
        cameraPeerConnection = null
        remoteVideoTrack = null
        offerCreated = false
        answerCreated = false
        remoteDescriptionSet = false
        pendingCandidates.clear()
    }
}
