package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RemoteStateDefaultsTest {
    @Test
    fun cameraRemoteStateKeepsSceneDetectionOffByDefault() {
        val state = CameraRemoteUiState()

        assertFalse(state.sceneDetectionEnabled)
        assertEquals(SceneDetectionState(), state.sceneDetection)
        assertEquals("waiting", state.roomStatus)
    }

    @Test
    fun controllerRemoteStateKeepsSceneDetectionOffByDefault() {
        val state = ControllerRemoteUiState()

        assertFalse(state.sceneDetectionEnabled)
        assertEquals(SceneDetectionState(), state.sceneDetection)
        assertEquals("waiting", state.roomStatus)
    }

    @Test
    fun missingRoomUpdateClassifierMatchesFirestoreNotFoundFailures() {
        assertEquals(
            true,
            FirestoreRoomUpdateFailureClassifier.isMissingRoomUpdate(
                RuntimeException("NOT_FOUND: No document to update: rooms/ABCDE")
            )
        )
        assertEquals(
            false,
            FirestoreRoomUpdateFailureClassifier.isMissingRoomUpdate(
                RuntimeException("PERMISSION_DENIED")
            )
        )
    }
}
