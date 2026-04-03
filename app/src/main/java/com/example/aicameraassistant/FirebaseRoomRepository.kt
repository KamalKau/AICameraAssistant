package com.example.aicameraassistant

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate

class FirebaseRoomRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun createRoom(roomCode: String) {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()

        if (!snapshot.exists()) {
            val roomData = mapOf(
                "roomCode" to roomCode,
                "status" to "waiting",
                "requestReceived" to false,
                "controllerApproved" to false,
                "captureRequest" to false,
                "lensFacing" to "back",
                "zoomLevel" to 1.0,
                "flashEnabled" to false,
                "offer" to null,
                "answer" to null,
                "createdAt" to System.currentTimeMillis()
            )

            docRef.set(roomData).await()
        }
    }

    suspend fun sendConnectionRequest(roomCode: String): Boolean {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()

        if (!snapshot.exists()) return false

        docRef.update(
            mapOf(
                "requestReceived" to true,
                "controllerApproved" to false,
                "status" to "request_received"
            )
        ).await()

        return true
    }

    suspend fun updateApproval(roomCode: String, approved: Boolean) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "requestReceived" to false,
                    "controllerApproved" to approved,
                    "status" to if (approved) "connected" else "denied"
                )
            )
            .await()
    }

    suspend fun sendCaptureRequest(roomCode: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("captureRequest", true)
            .await()
    }

    suspend fun resetCaptureRequest(roomCode: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("captureRequest", false)
            .await()
    }

    suspend fun updateLensFacing(roomCode: String, lensFacing: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("lensFacing", lensFacing)
            .await()
    }

    suspend fun updateZoomLevel(roomCode: String, zoomLevel: Double) {
        db.collection("rooms")
            .document(roomCode)
            .update("zoomLevel", zoomLevel)
            .await()
    }

    suspend fun updateFlashEnabled(roomCode: String, flashEnabled: Boolean) {
        db.collection("rooms")
            .document(roomCode)
            .update("flashEnabled", flashEnabled)
            .await()
    }

    suspend fun saveOffer(roomCode: String, offerSdp: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("offer", offerSdp)
            .await()
    }

    suspend fun saveAnswer(roomCode: String, answerSdp: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("answer", answerSdp)
            .await()
    }

    suspend fun addControllerIceCandidate(roomCode: String, candidate: IceCandidate) {
        db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesController")
            .add(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                )
            )
            .await()
    }

    suspend fun addCameraIceCandidate(roomCode: String, candidate: IceCandidate) {
        db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesCamera")
            .add(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                )
            )
            .await()
    }

    fun listenToRoom(
        roomCode: String,
        onUpdate: (Boolean, Boolean, String, Boolean, String, Double, Boolean, String?, String?) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms")
            .document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val requestReceived = snapshot.getBoolean("requestReceived") ?: false
                    val controllerApproved = snapshot.getBoolean("controllerApproved") ?: false
                    val status = snapshot.getString("status") ?: "waiting"
                    val captureRequest = snapshot.getBoolean("captureRequest") ?: false
                    val lensFacing = snapshot.getString("lensFacing") ?: "back"
                    val zoomLevel = snapshot.getDouble("zoomLevel") ?: 1.0
                    val flashEnabled = snapshot.getBoolean("flashEnabled") ?: false
                    val offer = snapshot.getString("offer")
                    val answer = snapshot.getString("answer")

                    onUpdate(
                        requestReceived,
                        controllerApproved,
                        status,
                        captureRequest,
                        lensFacing,
                        zoomLevel,
                        flashEnabled,
                        offer,
                        answer
                    )
                }
            }
    }

    fun listenToControllerIceCandidates(
        roomCode: String,
        onCandidate: (IceCandidate) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesController")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document
                        val sdpMid = data.getString("sdpMid")
                        val sdpMLineIndex = data.getLong("sdpMLineIndex")?.toInt() ?: 0
                        val candidate = data.getString("candidate") ?: return@forEach

                        onCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
                }
            }
    }

    fun listenToCameraIceCandidates(
        roomCode: String,
        onCandidate: (IceCandidate) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesCamera")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document
                        val sdpMid = data.getString("sdpMid")
                        val sdpMLineIndex = data.getLong("sdpMLineIndex")?.toInt() ?: 0
                        val candidate = data.getString("candidate") ?: return@forEach

                        onCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
                }
            }
    }
}