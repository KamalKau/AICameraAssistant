package com.example.aicameraassistant

import org.junit.Assert.*
import org.junit.Test

class WebRtcSessionStateTest {
    @Test fun offerIsCreatedOnlyOncePerSession() {
        val state = WebRtcSessionState<String>()
        assertTrue(state.markOfferCreated()); assertFalse(state.markOfferCreated())
    }

    @Test fun answerAndRemoteDescriptionUpdateState() {
        val state = WebRtcSessionState<String>()
        state.markAnswerCreated(); state.applyRemoteDescription {}
        assertTrue(state.answerCreated); assertTrue(state.remoteDescriptionSet)
    }

    @Test fun candidatesBufferThenApplyAndClear() {
        val state = WebRtcSessionState<String>(); val applied = mutableListOf<String>()
        state.receiveCandidate("one", applied::add); state.receiveCandidate("two", applied::add)
        assertTrue(applied.isEmpty()); assertEquals(2, state.pendingCandidateCount)
        state.applyRemoteDescription(applied::add)
        assertEquals(listOf("one", "two"), applied); assertEquals(0, state.pendingCandidateCount)
    }

    @Test fun candidateAfterRemoteDescriptionAppliesImmediately() {
        val state = WebRtcSessionState<String>(); val applied = mutableListOf<String>()
        state.applyRemoteDescription(applied::add); state.receiveCandidate("fresh", applied::add)
        assertEquals(listOf("fresh"), applied)
    }

    @Test fun cleanupClearsEveryConnectionAndSignalingField() {
        val state = WebRtcSessionState<String>()
        state.attachControllerPeer(Any()); state.attachCameraPeer(Any()); state.attachRemoteVideoTrack(Any())
        state.markOfferCreated(); state.markAnswerCreated(); state.receiveCandidate("ice") {}; state.applyRemoteDescription {}
        state.clear()
        assertNull(state.controllerPeerConnection); assertNull(state.cameraPeerConnection); assertNull(state.remoteVideoTrack)
        assertFalse(state.offerCreated); assertFalse(state.answerCreated); assertFalse(state.remoteDescriptionSet)
        assertEquals(0, state.pendingCandidateCount)
    }

    @Test fun secondSessionHasFreshOfferAnswerAndIceState() {
        val state = WebRtcSessionState<String>(); val firstApplied = mutableListOf<String>()
        state.markOfferCreated(); state.markAnswerCreated(); state.applyRemoteDescription(firstApplied::add)
        state.receiveCandidate("session-a", firstApplied::add); state.clear()
        val secondApplied = mutableListOf<String>()
        assertTrue(state.markOfferCreated()); assertFalse(state.answerCreated); assertFalse(state.remoteDescriptionSet)
        state.receiveCandidate("session-b", secondApplied::add); assertTrue(secondApplied.isEmpty())
        state.markAnswerCreated(); state.applyRemoteDescription(secondApplied::add)
        assertEquals(listOf("session-b"), secondApplied); assertFalse(firstApplied.contains("session-b"))
    }

    @Test fun tenConnectionCyclesNeverReuseSignalingState() {
        val state = WebRtcSessionState<Int>()
        repeat(10) { cycle ->
            assertTrue(state.markOfferCreated()); state.receiveCandidate(cycle) {}; state.markAnswerCreated()
            val applied = mutableListOf<Int>(); state.applyRemoteDescription(applied::add)
            assertEquals(listOf(cycle), applied); state.clear()
            assertEquals(0, state.pendingCandidateCount); assertFalse(state.offerCreated)
        }
    }
}
