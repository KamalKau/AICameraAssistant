package com.example.aicameraassistant

/** Core room operations shared by production and deterministic test repositories. */
interface RoomRepository {
    suspend fun createRoom(roomCode: String)
    suspend fun sendConnectionRequest(roomCode: String): Boolean
    suspend fun updateApproval(roomCode: String, approved: Boolean)
    suspend fun updateLensFacing(roomCode: String, lensFacing: String)
    suspend fun updateZoomLevel(roomCode: String, zoomLevel: Double)
    suspend fun updateFlashMode(roomCode: String, flashMode: String)
    suspend fun resetCaptureRequest(roomCode: String)
    suspend fun sendCaptureRequest(roomCode: String, requestId: Long, requestType: String = "photo")
}
