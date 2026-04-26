package com.example.aicameraassistant

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate

class FirebaseRoomRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun createRoom(roomCode: String) {
        val docRef = db.collection("rooms").document(roomCode)
        docRef.set(defaultRoomData(roomCode)).await()
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

    suspend fun updateZoomRange(roomCode: String, minZoom: Double, maxZoom: Double) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "minZoom" to minZoom,
                    "maxZoom" to maxZoom
                )
            )
            .await()
    }

    suspend fun updateFlashMode(roomCode: String, flashMode: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("flashMode", flashMode)
            .await()
    }

    suspend fun updateFlashSupported(roomCode: String, flashSupported: Boolean) {
        db.collection("rooms")
            .document(roomCode)
            .update("flashSupported", flashSupported)
            .await()
    }

    suspend fun updateGridEnabled(roomCode: String, gridEnabled: Boolean) {
        db.collection("rooms")
            .document(roomCode)
            .update("gridEnabled", gridEnabled)
            .await()
    }

    suspend fun updatePreviewSize(roomCode: String, width: Int, height: Int) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "previewWidth" to width,
                    "previewHeight" to height
                )
            )
            .await()
    }

    suspend fun updateFocusRequest(
        roomCode: String,
        normalizedX: Double,
        normalizedY: Double,
        requestId: Long,
        lockEnabled: Boolean
    ) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "focusPointX" to normalizedX.coerceIn(0.0, 1.0),
                    "focusPointY" to normalizedY.coerceIn(0.0, 1.0),
                    "focusRequestId" to requestId,
                    "focusLockEnabled" to lockEnabled
                )
            )
            .await()
    }

    suspend fun updateExposureState(
        roomCode: String,
        minIndex: Int,
        maxIndex: Int,
        currentIndex: Int
    ) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "exposureMinIndex" to minIndex.toLong(),
                    "exposureMaxIndex" to maxIndex.toLong(),
                    "exposureIndex" to currentIndex.toLong()
                )
            )
            .await()
    }

    suspend fun updateExposureIndex(roomCode: String, exposureIndex: Int) {
        db.collection("rooms")
            .document(roomCode)
            .update("exposureIndex", exposureIndex.toLong())
            .await()
    }

    suspend fun resetCaptureRequest(roomCode: String) {
        db.collection("rooms")
            .document(roomCode)
            .update("captureRequest", false)
            .await()
    }

    suspend fun sendCaptureRequest(roomCode: String, requestId: Long) {
        db.collection("rooms")
            .document(roomCode)
            .update(
                mapOf(
                    "captureRequest" to true,
                    "captureRequestId" to requestId
                )
            )
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

    suspend fun endSession(roomCode: String) {
        val roomRef = db.collection("rooms").document(roomCode)

        clearIceCandidates(roomCode)

        roomRef.set(
            defaultRoomData(roomCode) + ("status" to "ended")
        ).await()
    }

    private fun defaultRoomData(roomCode: String): Map<String, Any?> =
        mapOf(
            "roomCode" to roomCode,
            "status" to "waiting",
            "requestReceived" to false,
            "controllerApproved" to false,
            "captureRequest" to false,
            "captureRequestId" to 0L,
            "lensFacing" to "back",
            "zoomLevel" to 1.0,
            "minZoom" to 1.0,
            "maxZoom" to 1.0,
            "flashMode" to "off",
            "flashSupported" to false,
            "gridEnabled" to false,
            "focusRequestId" to 0L,
            "focusLockEnabled" to false,
            "focusPointX" to 0.5,
            "focusPointY" to 0.5,
            "exposureMinIndex" to 0L,
            "exposureMaxIndex" to 0L,
            "exposureIndex" to 0L,
            "offer" to null,
            "answer" to null,
            "previewWidth" to 0L,
            "previewHeight" to 0L,
            "createdAt" to System.currentTimeMillis()
        )

    suspend fun clearIceCandidates(roomCode: String) {
        val roomRef = db.collection("rooms").document(roomCode)
        clearCollection(roomRef.collection("iceCandidatesController"))
        clearCollection(roomRef.collection("iceCandidatesCamera"))
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

    fun getRoomStatus(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("status")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getLensFacing(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("lensFacing")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getZoomLevel(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getDouble("zoomLevel")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getMinZoom(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getDouble("minZoom") ?: 1.0)
            }
        awaitClose { listener.remove() }
    }

    fun getMaxZoom(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getDouble("maxZoom") ?: 1.0)
            }
        awaitClose { listener.remove() }
    }

    fun getFlashMode(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("flashMode") ?: "off")
            }
        awaitClose { listener.remove() }
    }

    fun getFlashSupported(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("flashSupported") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getGridEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("gridEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getPreviewWidth(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val width = snapshot?.getLong("previewWidth")?.toInt() ?: 0
                trySend(width)
            }
        awaitClose { listener.remove() }
    }

    fun getPreviewHeight(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val height = snapshot?.getLong("previewHeight")?.toInt() ?: 0
                trySend(height)
            }
        awaitClose { listener.remove() }
    }

    fun getFocusRequestId(roomCode: String): Flow<Long> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("focusRequestId") ?: 0L)
            }
        awaitClose { listener.remove() }
    }

    fun getFocusPointX(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getDouble("focusPointX") ?: 0.5)
            }
        awaitClose { listener.remove() }
    }

    fun getFocusLockEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("focusLockEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getFocusPointY(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getDouble("focusPointY") ?: 0.5)
            }
        awaitClose { listener.remove() }
    }

    fun getExposureMinIndex(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("exposureMinIndex")?.toInt() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    fun getExposureMaxIndex(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("exposureMaxIndex")?.toInt() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    fun getExposureIndex(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("exposureIndex")?.toInt() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    fun getCaptureRequest(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getBoolean("captureRequest")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getCaptureRequestId(roomCode: String): Flow<Long> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("captureRequestId") ?: 0L)
            }
        awaitClose { listener.remove() }
    }

    fun getRequestReceived(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getBoolean("requestReceived")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getControllerApproved(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getBoolean("controllerApproved")?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun getOfferSdp(roomCode: String): Flow<String?> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("offer"))
            }
        awaitClose { listener.remove() }
    }

    fun getAnswerSdp(roomCode: String): Flow<String?> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("answer"))
            }
        awaitClose { listener.remove() }
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

    private suspend fun clearCollection(
        collection: com.google.firebase.firestore.CollectionReference
    ) {
        val snapshot = collection.get().await()
        snapshot.documents.forEach { document ->
            document.reference.delete().await()
        }
    }
}
