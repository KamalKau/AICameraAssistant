package com.example.aicameraassistant

internal data class FakeRoomState(
    val status: String = "waiting",
    val lensFacing: String = "back",
    val zoomLevel: Double = 1.0,
    val flashEnabled: Boolean = false,
    val captureRequested: Boolean = false,
    val captureRequestId: Long = 0L
)

internal class FakeRoomRepository : RoomRepository {
    private val rooms = mutableMapOf<String, FakeRoomState>()

    fun state(roomCode: String): FakeRoomState = requireNotNull(rooms[roomCode])

    override suspend fun createRoom(roomCode: String) { rooms[roomCode] = FakeRoomState() }
    override suspend fun sendConnectionRequest(roomCode: String): Boolean {
        val state = rooms[roomCode] ?: return false
        rooms[roomCode] = state.copy(status = "request_received")
        return true
    }
    override suspend fun updateApproval(roomCode: String, approved: Boolean) {
        rooms[roomCode] = state(roomCode).copy(status = if (approved) "connected" else "denied")
    }
    override suspend fun updateLensFacing(roomCode: String, lensFacing: String) {
        rooms[roomCode] = state(roomCode).copy(lensFacing = lensFacing)
    }
    override suspend fun updateZoomLevel(roomCode: String, zoomLevel: Double) {
        rooms[roomCode] = state(roomCode).copy(zoomLevel = zoomLevel)
    }
    override suspend fun updateFlashMode(roomCode: String, flashMode: String) {
        rooms[roomCode] = state(roomCode).copy(flashEnabled = flashMode != "off")
    }
    override suspend fun resetCaptureRequest(roomCode: String) {
        rooms[roomCode] = state(roomCode).copy(captureRequested = false)
    }
    override suspend fun sendCaptureRequest(roomCode: String, requestId: Long, requestType: String) {
        rooms[roomCode] = state(roomCode).copy(captureRequested = true, captureRequestId = requestId)
    }
}
