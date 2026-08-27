package com.example.aicameraassistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteIntegrationTest {
    @Test fun controllerApprovalControlsAndSingleCaptureFlow() = runBlocking {
        val repository = FakeRoomRepository(); val room = "integration"
        repository.createRoom(room)
        assertTrue(repository.sendConnectionRequest(room))
        repository.updateApproval(room, true)
        repository.updateLensFacing(room, "front")
        repository.updateZoomLevel(room, 2.0)
        repository.updateFlashMode(room, "on")
        repository.sendCaptureRequest(room, requestId = 1L)

        val request = repository.state(room)
        assertEquals("connected", request.status); assertEquals("front", request.lensFacing)
        assertEquals(2.0, request.zoomLevel, 0.0); assertTrue(request.flashEnabled)
        assertTrue(request.captureRequested); assertEquals(1L, request.captureRequestId)

        repository.resetCaptureRequest(room)
        assertFalse(repository.state(room).captureRequested)
    }

    @Test fun tenConnectCaptureDisconnectCyclesHaveNoDuplicateCapture() = runBlocking {
        val repository = FakeRoomRepository(); val handled = mutableSetOf<Long>()
        repeat(10) { cycle ->
            val room = "cycle-$cycle"; repository.createRoom(room)
            repository.sendConnectionRequest(room); repository.updateApproval(room, true)
            val requestId = cycle.toLong() + 1
            repository.sendCaptureRequest(room, requestId)
            if (repository.state(room).captureRequested) handled += repository.state(room).captureRequestId
            repository.resetCaptureRequest(room); repository.updateApproval(room, false)
            assertFalse(repository.state(room).captureRequested)
        }
        assertEquals(10, handled.size)
    }
}
