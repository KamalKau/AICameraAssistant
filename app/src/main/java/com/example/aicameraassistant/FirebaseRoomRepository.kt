package com.example.aicameraassistant

import android.util.Log
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
        clearIceCandidates(roomCode)
        docRef.set(defaultRoomData(roomCode)).await()
    }

    suspend fun sendConnectionRequest(roomCode: String): Boolean {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()

        if (!snapshot.exists()) return false

        clearIceCandidates(roomCode)
        updateRoomSafely(
            roomCode,
            mapOf(
                "requestReceived" to true,
                "controllerApproved" to false,
                "status" to "request_received",
                "offer" to null,
                "answer" to null,
                "rtcSessionId" to null
            )
        )

        return true
    }

    suspend fun updateApproval(roomCode: String, approved: Boolean) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "requestReceived" to false,
                "controllerApproved" to approved,
                "status" to if (approved) "connected" else "denied"
            )
        )
    }

    suspend fun updateLensFacing(roomCode: String, lensFacing: String) {
        updateRoomSafely(roomCode, "lensFacing", lensFacing)
    }

    suspend fun updateZoomLevel(roomCode: String, zoomLevel: Double) {
        updateRoomSafely(roomCode, "zoomLevel", zoomLevel)
    }

    suspend fun updateZoomRange(roomCode: String, minZoom: Double, maxZoom: Double) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "minZoom" to minZoom,
                "maxZoom" to maxZoom
            )
        )
    }

    suspend fun updateFlashMode(roomCode: String, flashMode: String) {
        updateRoomSafely(roomCode, "flashMode", flashMode)
    }

    suspend fun updateCameraMode(roomCode: String, mode: String) {
        val cameraMode = when (mode) {
            "portrait", "video" -> mode
            else -> "photo"
        }
        updateStringIfChanged(roomCode, "cameraMode", cameraMode)
    }

    suspend fun updateAspectRatioMode(roomCode: String, aspectRatioMode: String) {
        val safeMode = AspectRatioMode.fromKey(aspectRatioMode).key
        updateStringIfChanged(roomCode, "aspectRatioMode", safeMode)
    }

    suspend fun updatePortraitBlurLevel(roomCode: String, blurLevel: String) {
        val portraitBlurLevel = when (blurLevel) {
            "natural", "strong" -> blurLevel
            else -> "blur"
        }
        updateStringIfChanged(roomCode, "portraitBlurLevel", portraitBlurLevel)
    }

    suspend fun updatePortraitStrength(roomCode: String, strength: Int) {
        val portraitStrength = strength.coerceIn(1, 7)
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()
        if ((snapshot.getLong("portraitStrength") ?: 5L).toInt() == portraitStrength) return
        updateRoomSafely(roomCode, "portraitStrength", portraitStrength.toLong())
    }

    suspend fun updatePortraitEffect(roomCode: String, effect: String) {
        val portraitEffect = when (effect) {
            "studio", "mono", "backdrop", "low_key_mono", "high_key_mono", "color_point" -> effect
            else -> "blur"
        }
        updateStringIfChanged(roomCode, "portraitEffect", portraitEffect)
    }

    suspend fun updatePortraitSubjectState(
        roomCode: String,
        status: String,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ) {
        val safeStatus = when (status) {
            "Portrait ready", "Move closer" -> status
            else -> "Finding subject..."
        }
        val safeLeft = left.coerceIn(0.0, 1.0)
        val safeTop = top.coerceIn(0.0, 1.0)
        val safeRight = right.coerceIn(0.0, 1.0)
        val safeBottom = bottom.coerceIn(0.0, 1.0)
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()
        val unchanged =
            snapshot.getString("portraitStatus") == safeStatus &&
                (snapshot.getDouble("portraitFaceLeft") ?: 0.0) == safeLeft &&
                (snapshot.getDouble("portraitFaceTop") ?: 0.0) == safeTop &&
                (snapshot.getDouble("portraitFaceRight") ?: 0.0) == safeRight &&
                (snapshot.getDouble("portraitFaceBottom") ?: 0.0) == safeBottom
        if (unchanged) return

        updateRoomSafely(
            roomCode,
            mapOf(
                "portraitStatus" to safeStatus,
                "portraitFaceLeft" to safeLeft,
                "portraitFaceTop" to safeTop,
                "portraitFaceRight" to safeRight,
                "portraitFaceBottom" to safeBottom
            )
        )
    }

    suspend fun updateFaceDetectionOverlay(
        roomCode: String,
        faceDetected: Boolean,
        faceBox: NormalizedFaceBounds,
        faceBoxes: List<NormalizedFaceBounds>,
        timestamp: Long
    ) {
        val safeBox = mapOf(
            "left" to faceBox.left.coerceIn(0.0, 1.0),
            "top" to faceBox.top.coerceIn(0.0, 1.0),
            "right" to faceBox.right.coerceIn(0.0, 1.0),
            "bottom" to faceBox.bottom.coerceIn(0.0, 1.0)
        )
        val safeBoxes = faceBoxes
            .filter { it.isValid() }
            .map { box ->
                mapOf(
                    "left" to box.left.coerceIn(0.0, 1.0),
                    "top" to box.top.coerceIn(0.0, 1.0),
                    "right" to box.right.coerceIn(0.0, 1.0),
                    "bottom" to box.bottom.coerceIn(0.0, 1.0)
                )
            }
        updateRoomSafely(
            roomCode,
            mapOf(
                "faceBox" to safeBox,
                "faceBoxes" to safeBoxes,
                "faceDetected" to faceDetected,
                "faceDetectionTimestamp" to timestamp
            )
        )
    }

    suspend fun updateSceneDetectionState(
        roomCode: String,
        state: SceneDetectionState
    ) {
        val safeKey = when (state.key) {
            "food", "night", "face", "text", "landscape" -> state.key
            else -> "auto"
        }
        updateRoomSafely(
            roomCode,
            mapOf(
                "sceneDetectionKey" to safeKey,
                "sceneDetectionLabel" to state.label.take(24),
                "sceneDetectionSuggestion" to state.suggestion.take(80),
                "sceneDetectionConfidence" to state.confidence.coerceIn(0.0, 1.0),
                "sceneDetectionTimestamp" to state.timestamp,
                "sceneDetectionAutoAdjustment" to state.autoAdjustment.take(48)
            )
        )
    }

    suspend fun updateSceneDetectionEnabled(roomCode: String, sceneDetectionEnabled: Boolean) {
        updateRoomSafely(roomCode, "sceneDetectionEnabled", sceneDetectionEnabled)
    }

    suspend fun updateFlashSupported(roomCode: String, flashSupported: Boolean) {
        updateRoomSafely(roomCode, "flashSupported", flashSupported)
    }

    suspend fun updateGridEnabled(roomCode: String, gridEnabled: Boolean) {
        updateRoomSafely(roomCode, "gridEnabled", gridEnabled)
    }

    suspend fun updateNightModeEnabled(roomCode: String, nightModeEnabled: Boolean) {
        updateRoomSafely(roomCode, "nightModeEnabled", nightModeEnabled)
    }

    suspend fun updateVideoHdrSupported(roomCode: String, videoHdrSupported: Boolean) {
        updateRoomSafely(roomCode, "videoHdrSupported", videoHdrSupported)
    }

    suspend fun updateVideoHdrEnabled(roomCode: String, videoHdrEnabled: Boolean) {
        updateRoomSafely(roomCode, "videoHdrEnabled", videoHdrEnabled)
    }

    suspend fun updateToolbarExpanded(roomCode: String, toolbarExpanded: Boolean) {
        updateRoomSafely(roomCode, "toolbarExpanded", toolbarExpanded)
    }

    suspend fun updatePreviewSize(roomCode: String, width: Int, height: Int) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "previewWidth" to width,
                "previewHeight" to height
            )
        )
    }

    suspend fun updateFocusRequest(
        roomCode: String,
        normalizedX: Double,
        normalizedY: Double,
        requestId: Long,
        lockEnabled: Boolean
    ) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "focusPointX" to normalizedX.coerceIn(0.0, 1.0),
                "focusPointY" to normalizedY.coerceIn(0.0, 1.0),
                "focusRequestId" to requestId,
                "focusLockEnabled" to lockEnabled
            )
        )
    }

    suspend fun updateExposureState(
        roomCode: String,
        minIndex: Int,
        maxIndex: Int,
        currentIndex: Int
    ) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "exposureMinIndex" to minIndex.toLong(),
                "exposureMaxIndex" to maxIndex.toLong(),
                "exposureIndex" to currentIndex.toLong()
            )
        )
    }

    suspend fun updateExposureIndex(roomCode: String, exposureIndex: Int) {
        updateRoomSafely(roomCode, "exposureIndex", exposureIndex.toLong())
    }

    suspend fun resetCaptureRequest(roomCode: String) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "captureRequest" to false,
                "captureRequestType" to "photo"
            )
        )
    }

    suspend fun sendCaptureRequest(
        roomCode: String,
        requestId: Long,
        requestType: String = "photo"
    ) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "captureRequest" to true,
                "captureRequestId" to requestId,
                "captureRequestType" to requestType
            )
        )
    }

    suspend fun saveOffer(roomCode: String, offerSdp: String, rtcSessionId: String) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "offer" to offerSdp,
                "answer" to null,
                "rtcSessionId" to rtcSessionId
            )
        )
    }

    suspend fun saveAnswer(roomCode: String, answerSdp: String, rtcSessionId: String) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "answer" to answerSdp,
                "rtcSessionId" to rtcSessionId
            )
        )
    }

    suspend fun endSession(roomCode: String, expectedSessionVersion: Long? = null) {
        val roomRef = db.collection("rooms").document(roomCode)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val currentVersion = snapshot.getLong("sessionVersion") ?: expectedSessionVersion ?: 0L

            transaction.set(
                roomRef,
                defaultRoomData(roomCode) +
                    ("status" to "ended") +
                    ("sessionVersion" to currentVersion)
            )
        }.await()

        clearIceCandidates(roomCode)
    }

    private fun defaultRoomData(roomCode: String): Map<String, Any?> =
        mapOf(
            "roomCode" to roomCode,
            "status" to "waiting",
            "requestReceived" to false,
            "controllerApproved" to false,
            "captureRequest" to false,
            "captureRequestId" to 0L,
            "captureRequestType" to "photo",
            "cameraMode" to "photo",
            "aspectRatioMode" to "full",
            "portraitBlurLevel" to "blur",
            "portraitStrength" to 5L,
            "portraitEffect" to "blur",
            "portraitStatus" to "Finding subject...",
            "portraitFaceLeft" to 0.0,
            "portraitFaceTop" to 0.0,
            "portraitFaceRight" to 0.0,
            "portraitFaceBottom" to 0.0,
            "faceBox" to mapOf(
                "left" to 0.0,
                "top" to 0.0,
                "right" to 0.0,
                "bottom" to 0.0
            ),
            "faceBoxes" to emptyList<Map<String, Double>>(),
            "faceDetected" to false,
            "faceDetectionTimestamp" to 0L,
            "sceneDetectionKey" to "auto",
            "sceneDetectionLabel" to "Auto",
            "sceneDetectionSuggestion" to "Scene detection ready",
            "sceneDetectionConfidence" to 0.0,
            "sceneDetectionTimestamp" to 0L,
            "sceneDetectionAutoAdjustment" to "",
            "sceneDetectionEnabled" to false,
            "lensFacing" to "back",
            "zoomLevel" to 1.0,
            "minZoom" to 1.0,
            "maxZoom" to 1.0,
            "flashMode" to "off",
            "flashSupported" to false,
            "gridEnabled" to false,
            "nightModeEnabled" to false,
            "videoHdrSupported" to false,
            "videoHdrEnabled" to false,
            "toolbarExpanded" to false,
            "focusRequestId" to 0L,
            "focusLockEnabled" to false,
            "focusPointX" to 0.5,
            "focusPointY" to 0.5,
            "exposureMinIndex" to 0L,
            "exposureMaxIndex" to 0L,
            "exposureIndex" to 0L,
            "offer" to null,
            "answer" to null,
            "rtcSessionId" to null,
            "previewWidth" to 0L,
            "previewHeight" to 0L,
            "sessionVersion" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis()
        )

    suspend fun clearIceCandidates(roomCode: String) {
        val roomRef = db.collection("rooms").document(roomCode)
        clearCollection(roomRef.collection("iceCandidatesController"))
        clearCollection(roomRef.collection("iceCandidatesCamera"))
    }

    suspend fun addControllerIceCandidate(
        roomCode: String,
        candidate: IceCandidate,
        rtcSessionId: String
    ) {
        db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesController")
            .add(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp,
                    "rtcSessionId" to rtcSessionId
                )
            )
            .await()
    }

    suspend fun addCameraIceCandidate(
        roomCode: String,
        candidate: IceCandidate,
        rtcSessionId: String
    ) {
        db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesCamera")
            .add(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp,
                    "rtcSessionId" to rtcSessionId
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

    fun getCameraMode(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val cameraMode = snapshot?.getString("cameraMode")
                trySend(
                    when (cameraMode) {
                        "portrait", "video" -> cameraMode
                        else -> "photo"
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    fun getAspectRatioMode(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(AspectRatioMode.fromKey(snapshot?.getString("aspectRatioMode") ?: "full").key)
            }
        awaitClose { listener.remove() }
    }

    fun getPortraitBlurLevel(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val blurLevel = snapshot?.getString("portraitBlurLevel")
                trySend(
                    when (blurLevel) {
                        "natural", "strong" -> blurLevel
                        else -> "blur"
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    fun getPortraitStrength(roomCode: String): Flow<Int> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend((snapshot?.getLong("portraitStrength") ?: 5L).toInt().coerceIn(1, 7))
            }
        awaitClose { listener.remove() }
    }

    fun getPortraitEffect(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val effect = snapshot?.getString("portraitEffect")
                trySend(
                    when (effect) {
                        "studio", "mono", "backdrop", "low_key_mono", "high_key_mono", "color_point" -> effect
                        else -> "blur"
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    fun getPortraitSubjectState(roomCode: String): Flow<PortraitSubjectState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("portraitStatus") ?: "Finding subject..."
                trySend(
                    PortraitSubjectState(
                        status = when (status) {
                            "Portrait ready", "Move closer" -> status
                            else -> "Finding subject..."
                        },
                        left = snapshot?.getDouble("portraitFaceLeft") ?: 0.0,
                        top = snapshot?.getDouble("portraitFaceTop") ?: 0.0,
                        right = snapshot?.getDouble("portraitFaceRight") ?: 0.0,
                        bottom = snapshot?.getDouble("portraitFaceBottom") ?: 0.0
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getFaceDetectionOverlayState(roomCode: String): Flow<FaceDetectionOverlayState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val box = snapshot?.get("faceBox") as? Map<*, *>
                val boxes = (snapshot?.get("faceBoxes") as? List<*>)
                    ?.mapNotNull { entry ->
                        val faceBox = entry as? Map<*, *> ?: return@mapNotNull null
                        NormalizedFaceBounds(
                            left = (faceBox["left"] as? Number)?.toDouble() ?: 0.0,
                            top = (faceBox["top"] as? Number)?.toDouble() ?: 0.0,
                            right = (faceBox["right"] as? Number)?.toDouble() ?: 0.0,
                            bottom = (faceBox["bottom"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    .orEmpty()
                trySend(
                    FaceDetectionOverlayState(
                        faceDetected = snapshot?.getBoolean("faceDetected") ?: false,
                        faceBox = NormalizedFaceBounds(
                            left = (box?.get("left") as? Number)?.toDouble() ?: 0.0,
                            top = (box?.get("top") as? Number)?.toDouble() ?: 0.0,
                            right = (box?.get("right") as? Number)?.toDouble() ?: 0.0,
                            bottom = (box?.get("bottom") as? Number)?.toDouble() ?: 0.0
                        ),
                        faceBoxes = boxes,
                        timestamp = snapshot?.getLong("faceDetectionTimestamp") ?: 0L
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getSceneDetectionState(roomCode: String): Flow<SceneDetectionState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val key = when (snapshot?.getString("sceneDetectionKey")) {
                    "food", "night", "face", "text", "landscape" -> snapshot.getString("sceneDetectionKey")
                    else -> "auto"
                } ?: "auto"
                trySend(
                    SceneDetectionState(
                        key = key,
                        label = snapshot?.getString("sceneDetectionLabel") ?: sceneLabelForKey(key),
                        suggestion = snapshot?.getString("sceneDetectionSuggestion")
                            ?: "Scene detection ready",
                        confidence = (snapshot?.getDouble("sceneDetectionConfidence") ?: 0.0)
                            .coerceIn(0.0, 1.0),
                        timestamp = snapshot?.getLong("sceneDetectionTimestamp") ?: 0L,
                        autoAdjustment = snapshot?.getString("sceneDetectionAutoAdjustment") ?: ""
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getSceneDetectionEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("sceneDetectionEnabled") ?: false)
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

    fun getNightModeEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("nightModeEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getVideoHdrSupported(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("videoHdrSupported") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getVideoHdrEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("videoHdrEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getToolbarExpanded(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("toolbarExpanded") ?: false)
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

    fun getCaptureRequestType(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("captureRequestType") ?: "photo")
            }
        awaitClose { listener.remove() }
    }

    fun getCaptureRequestState(roomCode: String): Flow<CaptureRequestState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(
                    CaptureRequestState(
                        requestId = snapshot?.getLong("captureRequestId") ?: 0L,
                        requestType = snapshot?.getString("captureRequestType") ?: "photo"
                    )
                )
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

    fun getRtcSessionId(roomCode: String): Flow<String?> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("rtcSessionId"))
            }
        awaitClose { listener.remove() }
    }

    fun getSessionVersion(roomCode: String): Flow<Long> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getLong("sessionVersion") ?: 0L)
            }
        awaitClose { listener.remove() }
    }

    fun listenToControllerIceCandidates(
        roomCode: String,
        rtcSessionId: String,
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
                        val candidateSessionId = data.getString("rtcSessionId")
                        if (candidateSessionId != rtcSessionId) return@forEach

                        onCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
                }
            }
    }

    fun listenToCameraIceCandidates(
        roomCode: String,
        rtcSessionId: String,
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
                        val candidateSessionId = data.getString("rtcSessionId")
                        if (candidateSessionId != rtcSessionId) return@forEach

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

    private suspend fun updateStringIfChanged(roomCode: String, field: String, value: String) {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return
        if (snapshot.getString(field) == value) return
        updateRoomSafely(roomCode, field, value)
    }

    private suspend fun updateRoomSafely(roomCode: String, field: String, value: Any?) {
        updateRoomSafely(roomCode, mapOf(field to value))
    }

    private suspend fun updateRoomSafely(roomCode: String, values: Map<String, Any?>) {
        runCatching {
            db.collection("rooms")
                .document(roomCode)
                .update(values)
                .await()
        }.onFailure { throwable ->
            if (FirestoreRoomUpdateFailureClassifier.isMissingRoomUpdate(throwable)) {
                Log.w("FirebaseRoomRepository", "Ignoring update for missing room $roomCode")
            } else {
                throw throwable
            }
        }
    }
}

internal object FirestoreRoomUpdateFailureClassifier {
    fun isMissingRoomUpdate(throwable: Throwable): Boolean {
        val message = throwable.message ?: return false
        return message.contains("No document to update", ignoreCase = true) ||
            message.contains("NOT_FOUND", ignoreCase = true)
    }
}
