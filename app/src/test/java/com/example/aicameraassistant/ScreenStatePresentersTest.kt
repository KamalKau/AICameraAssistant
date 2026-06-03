package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStatePresentersTest {
    @Test
    fun cameraToolRail_defaultsSceneDetectionOffAndMarksUnsupportedFlash() {
        val state = buildCameraToolRailUiState(
            flashSupported = false,
            flashMode = "on",
            lensFacing = "back",
            aspectRatioMode = "full",
            sceneDetectionEnabled = false,
            gridEnabled = false,
            nightModeEnabled = false,
            videoHdrSupported = false,
            videoHdrEnabled = false,
            cameraMode = "photo",
            toolbarExpanded = false,
            boomerangSelected = false,
            exposureSupported = true
        )

        assertFalse(state.sceneDetectionEnabled)
        assertFalse(state.flashEnabled)
        assertEquals("Unsupported", state.flashLabel)
        assertEquals("Rear", state.lensLabel)
        assertFalse(state.videoMode)
    }

    @Test
    fun cameraToolRail_reflectsEnabledAiVideoHdrAndFrontLens() {
        val state = buildCameraToolRailUiState(
            flashSupported = true,
            flashMode = "auto",
            lensFacing = "front",
            aspectRatioMode = "9_16",
            sceneDetectionEnabled = true,
            gridEnabled = true,
            nightModeEnabled = true,
            videoHdrSupported = true,
            videoHdrEnabled = true,
            cameraMode = "video",
            toolbarExpanded = true,
            boomerangSelected = true,
            exposureSupported = false
        )

        assertTrue(state.sceneDetectionEnabled)
        assertEquals("Auto", state.flashLabel)
        assertEquals("Front", state.lensLabel)
        assertEquals("9:16", state.aspectRatioLabel)
        assertTrue(state.videoMode)
        assertTrue(state.videoHdrEnabled)
        assertFalse(state.exposureSupported)
    }

    @Test
    fun hostStatusShowsApprovalPromptBeforeConnected() {
        val state = buildHostTopOverlayUiState(
            roomCode = "ABCDE",
            roomStatus = "request_received",
            connectionState = AppConnectionState.IDLE,
            sessionIsActive = false,
            requestReceived = true,
            controllerApproved = false,
            isEndingSession = false
        )

        assertEquals("Connection request received", state.status.text)
        assertTrue(state.showApprovalPrompt)
        assertFalse(state.sessionIsActive)
    }

    @Test
    fun controllerStatusReportsDisconnectedWarning() {
        val state = buildControllerStatusUiState(AppConnectionState.DISCONNECTED)

        assertEquals("Disconnected", state.text)
        assertEquals("Connection lost", state.warningText)
        assertEquals("Unable to reconnect", state.warningDetailText)
    }

    @Test
    fun exposureStateClampsManualProgressAndDetectsUnsupportedRange() {
        val unsupported = buildExposureUiState(
            minIndex = 0,
            maxIndex = 0,
            currentIndex = 0,
            manualProgressOverride = null,
            visible = false
        )
        val clamped = buildExposureUiState(
            minIndex = -4,
            maxIndex = 40,
            currentIndex = 0,
            manualProgressOverride = 1.7f,
            visible = true
        )

        assertFalse(unsupported.supported)
        assertTrue(clamped.supported)
        assertEquals(1f, clamped.progress, 0.0001f)
        assertEquals("EV +0.0", clamped.label)
    }

    @Test
    fun controllerCommonZoomOptionsFallsBackToMinimumWhenNoCommonOptionFits() {
        val options = buildControllerCommonZoomOptions(minZoom = 1.0, maxZoom = 1.2)

        assertEquals(listOf(1.0f), options)
    }

    @Test
    fun controllerCommonZoomOptionsKeepsOnlySupportedValues() {
        val options = buildControllerCommonZoomOptions(minZoom = 1.0, maxZoom = 2.0)

        assertEquals(listOf(1.0f, 2.0f), options)
    }

    @Test
    fun connectedHostStatusHasNoWarning() {
        val state = buildHostTopOverlayUiState(
            roomCode = "ABCDE",
            roomStatus = "connected",
            connectionState = AppConnectionState.CONNECTED,
            sessionIsActive = true,
            requestReceived = false,
            controllerApproved = true,
            isEndingSession = false
        )

        assertEquals("Controller connected", state.status.text)
        assertNull(state.status.warningText)
        assertFalse(state.showApprovalPrompt)
    }
}
