package com.example.aicameraassistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoomRepositoryTest {
    private lateinit var repository: FakeRoomRepository
    private val room = "test-room"

    @Before fun setUp() = runBlocking { repository = FakeRoomRepository(); repository.createRoom(room) }

    @Test fun roomStartsWaiting() = assertEquals("waiting", repository.state(room).status)

    @Test fun connectionRequestTransitionsWaitingToRequestReceived() = runBlocking {
        assertTrue(repository.sendConnectionRequest(room))
        assertEquals("request_received", repository.state(room).status)
    }

    @Test fun approvalTransitionsRequestReceivedToConnected() = runBlocking {
        repository.sendConnectionRequest(room); repository.updateApproval(room, true)
        assertEquals("connected", repository.state(room).status)
    }

    @Test fun denialTransitionsRequestReceivedToDenied() = runBlocking {
        repository.sendConnectionRequest(room); repository.updateApproval(room, false)
        assertEquals("denied", repository.state(room).status)
    }

    @Test fun lensFacingCyclesBackFrontBack() = runBlocking {
        assertEquals("back", repository.state(room).lensFacing)
        repository.updateLensFacing(room, "front"); assertEquals("front", repository.state(room).lensFacing)
        repository.updateLensFacing(room, "back"); assertEquals("back", repository.state(room).lensFacing)
    }

    @Test fun zoomTransitionsOneToTwoToThree() = runBlocking {
        assertEquals(1.0, repository.state(room).zoomLevel, 0.0)
        repository.updateZoomLevel(room, 2.0); assertEquals(2.0, repository.state(room).zoomLevel, 0.0)
        repository.updateZoomLevel(room, 3.0); assertEquals(3.0, repository.state(room).zoomLevel, 0.0)
    }

    @Test fun flashTogglesOffOnOff() = runBlocking {
        assertFalse(repository.state(room).flashEnabled)
        repository.updateFlashMode(room, "on"); assertTrue(repository.state(room).flashEnabled)
        repository.updateFlashMode(room, "off"); assertFalse(repository.state(room).flashEnabled)
    }

    @Test fun captureRequestSetsAndResets() = runBlocking {
        repository.sendCaptureRequest(room, 1L); assertTrue(repository.state(room).captureRequested)
        repository.resetCaptureRequest(room); assertFalse(repository.state(room).captureRequested)
    }
}
