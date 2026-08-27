package com.example.aicameraassistant

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.webrtc.IceCandidate
import java.security.MessageDigest
import java.util.UUID

class FirebaseRoomRepository : RoomRepository {
    private val db = FirebaseFirestore.getInstance()
    private val localInstanceId = UUID.randomUUID().toString()

    override suspend fun createRoom(roomCode: String) {
        AppLogger.debug(LogCategory.FIREBASE, "SESSION_TRACE", "createRoom start room=$roomCode")
        val docRef = db.collection("rooms").document(roomCode)
        clearIceCandidates(roomCode)
        clearCollection(docRef.collection("commands"))
        docRef.set(defaultRoomData(roomCode)).await()
        AppLogger.info(LogCategory.FIREBASE, "Room created")
        AppLogger.info(LogCategory.SESSION, "SESSION_STARTED")
    }

    override suspend fun sendConnectionRequest(roomCode: String): Boolean {
        AppLogger.debug(LogCategory.FIREBASE, "SESSION_TRACE", "connectionRequest start room=$roomCode")
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = withTimeout(ROOM_LOOKUP_TIMEOUT_MS) { docRef.get().await() }

        if (!snapshot.exists()) {
            AppLogger.warning(LogCategory.FIREBASE, "SESSION_TRACE", "connectionRequest missing room=$roomCode")
            return false
        }
        val lastActivityAt = snapshot.getLong("lastActivityAt")
            ?: snapshot.getLong("createdAt")
            ?: 0L
        val abandoned =
            lastActivityAt > 0L &&
                System.currentTimeMillis() - lastActivityAt > ABANDONED_ROOM_TIMEOUT_MS &&
                snapshot.getString("status") != "connected"
        if (abandoned) {
            clearIceCandidates(roomCode)
            docRef.delete().await()
            return false
        }

        clearIceCandidates(roomCode)
        clearCollection(docRef.collection("commands"))
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

        AppLogger.info(LogCategory.FIREBASE, "Connection request sent")

        return true
    }

    override suspend fun updateApproval(roomCode: String, approved: Boolean) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "requestReceived" to false,
                "controllerApproved" to approved,
                "status" to if (approved) "connected" else "denied"
            )
        )
        AppLogger.info(LogCategory.FIREBASE, if (approved) "Connection approved" else "Connection denied")
        AppLogger.info(LogCategory.SESSION, if (approved) "SESSION_CONNECTED" else "SESSION_DISCONNECTED")
    }

    override suspend fun updateLensFacing(roomCode: String, lensFacing: String) {
        updateRoomSafely(roomCode, "lensFacing", lensFacing)
        AppLogger.info(LogCategory.FIREBASE, "Lens-facing command updated")
    }

    override suspend fun updateZoomLevel(roomCode: String, zoomLevel: Double) {
        updateRoomSafely(roomCode, "zoomLevel", zoomLevel)
        AppLogger.info(LogCategory.FIREBASE, "Zoom command updated")
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

    override suspend fun updateFlashMode(roomCode: String, flashMode: String) {
        updateRoomSafely(roomCode, "flashMode", flashMode)
        AppLogger.info(LogCategory.FIREBASE, "Flash command updated")
    }

    suspend fun updateCameraMode(roomCode: String, mode: String) {
        val cameraMode = when (mode) {
            "portrait", "video" -> mode
            else -> "photo"
        }
        updateStringIfChanged(roomCode, "cameraMode", cameraMode)
    }

    suspend fun updateBoomerangEnabled(roomCode: String, enabled: Boolean) {
        updateRoomSafely(roomCode, "boomerangEnabled", enabled)
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
        timestamp: Long,
        sessionId: String = "",
        overlayEventId: Long = 0L
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
                    "bottom" to box.bottom.coerceIn(0.0, 1.0),
                    "trackingId" to box.trackingId,
                    "confidence" to box.confidence.coerceIn(0.0, 1.0),
                    "isPrimary" to box.isPrimary
                )
            }
        updateRoomSafely(
            roomCode,
            mapOf(
                "faceBox" to safeBox,
                "faceBoxes" to safeBoxes,
                "faceDetected" to faceDetected,
                "faceDetectionTimestamp" to timestamp,
                "faceDetectionSessionId" to sessionId,
                "faceOverlayEventId" to overlayEventId
            )
        )
    }

    suspend fun updateSceneDetectionState(
        roomCode: String,
        state: SceneDetectionState
    ) {
        updateDetectedScene(
            roomCode = roomCode,
            scene = state.key,
            confidence = state.confidence,
            timestamp = state.timestamp,
            sessionId = state.sessionId,
            suggestion = state.suggestion,
            autoAdjustment = state.autoAdjustment
        )
    }

    suspend fun updateSceneDetectionEnabled(roomCode: String, sceneDetectionEnabled: Boolean) {
        updateAiSceneDetectionEnabled(roomCode, sceneDetectionEnabled)
    }

    suspend fun updateAiSceneDetectionEnabled(roomCode: String, enabled: Boolean) {
        updateBooleanIfChanged(roomCode, "aiSceneDetectionEnabled", enabled)
    }

    suspend fun updateDetectedScene(
        roomCode: String,
        scene: String,
        confidence: Double,
        timestamp: Long,
        sessionId: String,
        suggestion: String = "",
        autoAdjustment: String = ""
    ) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "detectedScene" to scene,
                "detectedSceneConfidence" to confidence.coerceIn(0.0, 1.0),
                "detectedSceneUpdatedAt" to timestamp,
                "detectedSceneSessionId" to sessionId,
                "detectedSceneSuggestion" to suggestion.take(80),
                "detectedSceneAutoAdjustment" to autoAdjustment.take(80)
            )
        )
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

    suspend fun updateGestureCaptureEnabled(roomCode: String, enabled: Boolean) {
        updateBooleanIfChanged(roomCode, "gestureCaptureEnabled", enabled)
    }

    suspend fun updateSmartFramingEnabled(roomCode: String, enabled: Boolean) {
        updateBooleanIfChanged(roomCode, "smartFramingEnabled", enabled)
    }

    suspend fun updateSmartFramingState(roomCode: String, state: SmartFramingState) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "smartFramingGuidance" to state.guidance.take(32),
                "smartFramingTimestamp" to state.timestamp,
                "smartFramingSessionId" to state.sessionId
            )
        )
    }

    suspend fun updateVideoHdrSupported(roomCode: String, videoHdrSupported: Boolean) {
        updateRoomSafely(roomCode, "videoHdrSupported", videoHdrSupported)
    }

    suspend fun updateVideoHdrEnabled(roomCode: String, videoHdrEnabled: Boolean) {
        updateRoomSafely(roomCode, "videoHdrEnabled", videoHdrEnabled)
    }

    suspend fun updateVideoQuality(roomCode: String, quality: String) {
        updateStringIfChanged(roomCode, "videoQuality", VideoQualityOption.sanitizeFirebaseValue(quality))
    }

    suspend fun updateSupportedVideoQualities(roomCode: String, supportedValues: List<String>) {
        updateStringListIfChanged(
            roomCode,
            "supportedVideoQualities",
            supportedValues.mapNotNull(VideoQualityOption::fromFirebaseValueOrNull)
                .map { it.firebaseValue }
                .distinct()
        )
    }

    suspend fun updateVideoRecordingState(roomCode: String, state: VideoRecordingState) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "videoRecordingState" to state.toFirebaseValue(),
                "videoRecordingUpdatedAt" to System.currentTimeMillis()
            )
        )
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
        val safeX = normalizedX.coerceIn(0.0, 1.0)
        val safeY = normalizedY.coerceIn(0.0, 1.0)
        updateRoomSafely(
            roomCode,
            mapOf(
                "focusX" to safeX,
                "focusY" to safeY,
                "focusPointX" to safeX,
                "focusPointY" to safeY,
                "focusRequestId" to requestId,
                "focusLockEnabled" to lockEnabled
            )
        )
    }

    suspend fun updateFocusPoint(roomCode: String, x: Double, y: Double, requestId: Long) {
        updateFocusRequest(
            roomCode = roomCode,
            normalizedX = x,
            normalizedY = y,
            requestId = requestId,
            lockEnabled = false
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

    override suspend fun resetCaptureRequest(roomCode: String) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "captureRequest" to false,
                "captureRequestType" to "photo"
            )
        )
        AppLogger.info(LogCategory.FIREBASE, "Capture request reset")
    }

    override suspend fun sendCaptureRequest(
        roomCode: String,
        requestId: Long,
        requestType: String
    ) {
        sendReliableCommand(
            roomCode = roomCode,
            type = requestType,
            values = mapOf(
                "captureRequest" to true,
                "captureRequestId" to requestId,
                "captureRequestType" to requestType
            )
        )
    }

    suspend fun saveOffer(
        roomCode: String,
        offerSdp: String,
        rtcSessionId: String,
        signalingGeneration: Long
    ) {
        AppLogger.info(LogCategory.FIREBASE, "Capture request received")
        updateRoomSafely(
            roomCode,
            mapOf(
                "offer" to offerSdp,
                "answer" to null,
                "rtcSessionId" to rtcSessionId,
                "signalingGeneration" to signalingGeneration,
                "offerCreatedAt" to System.currentTimeMillis(),
                "answerCreatedAt" to 0L,
                "lastActivityAt" to System.currentTimeMillis()
            )
        )
        AppLogger.info(LogCategory.FIREBASE, "Offer saved")
    }

    suspend fun saveAnswer(
        roomCode: String,
        answerSdp: String,
        rtcSessionId: String,
        signalingGeneration: Long
    ) {
        updateRoomSafely(
            roomCode,
            mapOf(
                "answer" to answerSdp,
                "rtcSessionId" to rtcSessionId,
                "answerGeneration" to signalingGeneration,
                "answerCreatedAt" to System.currentTimeMillis(),
                "lastActivityAt" to System.currentTimeMillis()
            )
        )
        AppLogger.info(LogCategory.FIREBASE, "Answer saved")
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
            "aiSceneDetectionEnabled" to false,
            "detectedScene" to "unknown",
            "detectedSceneConfidence" to 0.0,
            "detectedSceneUpdatedAt" to 0L,
            "detectedSceneSessionId" to "",
            "detectedSceneSuggestion" to "Scene detection ready",
            "detectedSceneAutoAdjustment" to "",
            "lensFacing" to "back",
            "zoomLevel" to 1.0,
            "minZoom" to 1.0,
            "maxZoom" to 1.0,
            "flashMode" to "off",
            "boomerangEnabled" to false,
            "flashSupported" to false,
            "gridEnabled" to false,
            "nightModeEnabled" to false,
            "gestureCaptureEnabled" to false,
            "smartFramingEnabled" to false,
            "smartFramingGuidance" to "",
            "smartFramingTimestamp" to 0L,
            "smartFramingSessionId" to "",
            "videoHdrSupported" to false,
            "videoHdrEnabled" to false,
            "videoQuality" to VideoQualityOption.default.firebaseValue,
            "supportedVideoQualities" to emptyList<String>(),
            "videoRecordingState" to "idle",
            "videoRecordingUpdatedAt" to 0L,
            "toolbarExpanded" to false,
            "focusRequestId" to 0L,
            "focusX" to null,
            "focusY" to null,
            "focusLockEnabled" to false,
            "focusPointX" to 0.5,
            "focusPointY" to 0.5,
            "exposureMinIndex" to 0L,
            "exposureMaxIndex" to 0L,
            "exposureIndex" to 0L,
            "offer" to null,
            "answer" to null,
            "rtcSessionId" to null,
            "signalingGeneration" to 0L,
            "answerGeneration" to 0L,
            "offerCreatedAt" to 0L,
            "answerCreatedAt" to 0L,
            "commandId" to null,
            "commandType" to null,
            "commandSequence" to 0L,
            "commandIssuedAt" to 0L,
            "commandAckId" to null,
            "commandAckSequence" to 0L,
            "previewWidth" to 0L,
            "previewHeight" to 0L,
            "sessionVersion" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "lastActivityAt" to System.currentTimeMillis(),
            "lastHeartbeatAt" to System.currentTimeMillis(),
            "expiresAt" to System.currentTimeMillis() + ROOM_EXPIRATION_MS
        )

    suspend fun updateHeartbeat(
        roomCode: String,
        role: String,
        sessionId: String?,
        signalingGeneration: Long,
        instanceId: String
    ) {
        val now = System.currentTimeMillis()
        updateRoomSafely(
            roomCode,
            mapOf(
                "${role}HeartbeatAt" to now,
                "lastHeartbeatAt" to now,
                "expiresAt" to now + ROOM_EXPIRATION_MS,
                "${role}InstanceId" to instanceId,
                "activeSessionId" to sessionId,
                "activeSignalingGeneration" to signalingGeneration
            )
        )
    }

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
            .document(candidateDocumentId(rtcSessionId, candidate))
            .set(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp,
                    "rtcSessionId" to rtcSessionId,
                    "signalingGeneration" to signalingGenerationFrom(rtcSessionId),
                    "createdAt" to System.currentTimeMillis()
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
            .document(candidateDocumentId(rtcSessionId, candidate))
            .set(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp,
                    "rtcSessionId" to rtcSessionId,
                    "signalingGeneration" to signalingGenerationFrom(rtcSessionId),
                    "createdAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun clearIceCandidatesForSession(roomCode: String, rtcSessionId: String?) {
        if (rtcSessionId.isNullOrBlank()) return
        val roomRef = db.collection("rooms").document(roomCode)
        listOf("iceCandidatesController", "iceCandidatesCamera").forEach { collectionName ->
            val snapshot = roomRef.collection(collectionName)
                .whereEqualTo("rtcSessionId", rtcSessionId)
                .get()
                .await()
            snapshot.documents.forEach { it.reference.delete().await() }
        }
    }

    suspend fun sendReliableCommand(
        roomCode: String,
        type: String,
        values: Map<String, Any?>
    ): String {
        val commandId = UUID.randomUUID().toString()
        val roomRef = db.collection("rooms").document(roomCode)
        val commandRef = roomRef.collection("commands").document(commandId)
        var sequence = 0L
        repeat(3) { attempt ->
            try {
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(roomRef)
                    sequence = (snapshot.getLong("commandSequence") ?: 0L) + 1L
                    transaction.update(
                        roomRef,
                        values + mapOf(
                            "commandId" to commandId,
                            "commandType" to type,
                            "commandSequence" to sequence,
                            "commandIssuedAt" to System.currentTimeMillis(),
                            "lastActivityAt" to System.currentTimeMillis()
                        )
                    )
                    transaction.set(
                        commandRef,
                        mapOf(
                            "commandId" to commandId,
                            "commandType" to type,
                            "payload" to values,
                            "commandSequence" to sequence,
                            "createdAt" to System.currentTimeMillis(),
                            "sessionId" to snapshot.getString("rtcSessionId"),
                            "generation" to (snapshot.getLong("signalingGeneration") ?: 0L),
                            "senderInstanceId" to localInstanceId,
                            "status" to "pending",
                            "retryCount" to attempt,
                            "expiresAt" to System.currentTimeMillis() + COMMAND_EXPIRATION_MS
                        )
                    )
                }.await()
                return commandId
            } catch (throwable: Throwable) {
                if (attempt == 2) {
                    AppLogger.error(LogCategory.FIREBASE, "Firestore room update failed", throwable)
                    throw throwable
                }
                delay(250L shl attempt)
            }
        }
        return commandId
    }

    fun listenToReliableCommands(
        roomCode: String,
        onCommand: (id: String, sequence: Long, sessionId: String?, generation: Long) -> Unit
    ): ListenerRegistration {
        if (roomCode.isBlank()) return ListenerRegistration { }
        return db.collection("rooms").document(roomCode).collection("commands")
            .orderBy("commandSequence")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    if (change.type == DocumentChange.Type.REMOVED) return@forEach
                    if (change.document.getString("status") != "pending") return@forEach
                    val id = change.document.getString("commandId") ?: return@forEach
                    val sequence = change.document.getLong("commandSequence") ?: return@forEach
                    onCommand(
                        id,
                        sequence,
                        change.document.getString("sessionId"),
                        change.document.getLong("generation") ?: 0L
                    )
                }
            }
    }

    suspend fun acknowledgeReliableCommand(roomCode: String, id: String, sequence: Long) {
        val roomRef = db.collection("rooms").document(roomCode)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val currentAck = snapshot.getLong("commandAckSequence") ?: 0L
            if (sequence > currentAck && snapshot.getString("commandId") == id) {
                transaction.update(
                    roomRef,
                    mapOf(
                        "commandAckId" to id,
                        "commandAckSequence" to sequence,
                        "commandAckAt" to System.currentTimeMillis()
                    )
                )
            }
        }.await()
        roomRef.collection("commands").document(id).update(
            mapOf(
                "status" to "acknowledged",
                "acknowledgedAt" to System.currentTimeMillis(),
                "receiverInstanceId" to localInstanceId
            )
        ).await()
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

    fun getBoomerangEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("boomerangEnabled") ?: false)
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
                            bottom = (faceBox["bottom"] as? Number)?.toDouble() ?: 0.0,
                            trackingId = (faceBox["trackingId"] as? Number)?.toLong() ?: -1L,
                            confidence = (faceBox["confidence"] as? Number)?.toDouble() ?: 1.0,
                            isPrimary = faceBox["isPrimary"] as? Boolean ?: false
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
                        timestamp = snapshot?.getLong("faceDetectionTimestamp") ?: 0L,
                        sessionId = snapshot?.getString("faceDetectionSessionId").orEmpty(),
                        overlayEventId = snapshot?.getLong("faceOverlayEventId") ?: 0L
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getSceneDetectionState(roomCode: String): Flow<SceneDetectionState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val key = snapshot?.getString("detectedScene")
                    ?: snapshot?.getString("sceneDetectionKey")
                    ?: "unknown"
                trySend(
                    SceneDetectionState(
                        key = key,
                        label = sceneLabelForKey(key),
                        suggestion = snapshot?.getString("detectedSceneSuggestion")
                            ?: snapshot?.getString("sceneDetectionSuggestion")
                            ?: "Scene detection ready",
                        confidence = (snapshot?.getDouble("detectedSceneConfidence")
                            ?: snapshot?.getDouble("sceneDetectionConfidence") ?: 0.0)
                            .coerceIn(0.0, 1.0),
                        timestamp = snapshot?.getLong("detectedSceneUpdatedAt")
                            ?: snapshot?.getLong("sceneDetectionTimestamp") ?: 0L,
                        autoAdjustment = snapshot?.getString("detectedSceneAutoAdjustment")
                            ?: snapshot?.getString("sceneDetectionAutoAdjustment") ?: "",
                        sessionId = snapshot?.getString("detectedSceneSessionId").orEmpty()
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getDetectedScene(roomCode: String): Flow<SceneDetectionState> = getSceneDetectionState(roomCode)

    fun getSceneDetectionEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("aiSceneDetectionEnabled")
                    ?: snapshot?.getBoolean("sceneDetectionEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun getAiSceneDetectionEnabled(roomCode: String): Flow<Boolean> = getSceneDetectionEnabled(roomCode)

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

    fun getGestureCaptureEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("gestureCaptureEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun observeSmartFramingEnabled(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getBoolean("smartFramingEnabled") ?: false)
            }
        awaitClose { listener.remove() }
    }

    fun observeSmartFramingState(roomCode: String): Flow<SmartFramingState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(
                    SmartFramingState(
                        guidance = snapshot?.getString("smartFramingGuidance").orEmpty(),
                        timestamp = snapshot?.getLong("smartFramingTimestamp") ?: 0L,
                        sessionId = snapshot?.getString("smartFramingSessionId").orEmpty()
                    )
                )
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

    fun getVideoQuality(roomCode: String): Flow<String> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(VideoQualityOption.sanitizeFirebaseValue(snapshot?.getString("videoQuality")))
            }
        awaitClose { listener.remove() }
    }

    fun getSupportedVideoQualities(roomCode: String): Flow<List<String>> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                val values = ((snapshot?.get("supportedVideoQualities")
                    ?: snapshot?.get("videoQualitySupportedValues")) as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.mapNotNull(VideoQualityOption::fromFirebaseValueOrNull)
                    ?.map { it.firebaseValue }
                    ?.distinct()
                    .orEmpty()
                trySend(values)
            }
        awaitClose { listener.remove() }
    }

    fun getVideoRecordingState(roomCode: String): Flow<VideoRecordingState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(
                    snapshot?.getString("videoRecordingState").toVideoRecordingState()
                )
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

    fun getFocusRequest(roomCode: String): Flow<FocusRequestState> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(
                    FocusRequestState(
                        requestId = snapshot?.getLong("focusRequestId") ?: 0L,
                        x = snapshot?.getDouble("focusX") ?: snapshot?.getDouble("focusPointX"),
                        y = snapshot?.getDouble("focusY") ?: snapshot?.getDouble("focusPointY")
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    fun getFocusPointX(roomCode: String): Flow<Double> = callbackFlow {
        val listener = db.collection("rooms").document(roomCode)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getDouble("focusX") ?: snapshot?.getDouble("focusPointX") ?: 0.5)
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
                trySend(snapshot?.getDouble("focusY") ?: snapshot?.getDouble("focusPointY") ?: 0.5)
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
        if (roomCode.isBlank() || rtcSessionId.isBlank()) return ListenerRegistration { }
        return db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesController")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    AppLogger.error(LogCategory.FIREBASE, "Controller ICE listener failed", error)
                    return@addSnapshotListener
                }
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document
                        val sdpMid = data.getString("sdpMid")
                        val sdpMLineIndex = data.getLong("sdpMLineIndex")?.toInt() ?: 0
                        val candidate = data.getString("candidate") ?: return@forEach
                        val candidateSessionId = data.getString("rtcSessionId")
                        if (candidateSessionId != rtcSessionId) return@forEach

                        AppLogger.info(LogCategory.FIREBASE, "Controller ICE candidate received")
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
        if (roomCode.isBlank() || rtcSessionId.isBlank()) return ListenerRegistration { }
        return db.collection("rooms")
            .document(roomCode)
            .collection("iceCandidatesCamera")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    AppLogger.error(LogCategory.FIREBASE, "Camera ICE listener failed", error)
                    return@addSnapshotListener
                }
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document
                        val sdpMid = data.getString("sdpMid")
                        val sdpMLineIndex = data.getLong("sdpMLineIndex")?.toInt() ?: 0
                        val candidate = data.getString("candidate") ?: return@forEach
                        val candidateSessionId = data.getString("rtcSessionId")
                        if (candidateSessionId != rtcSessionId) return@forEach

                        AppLogger.info(LogCategory.FIREBASE, "Camera ICE candidate received")
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

    private suspend fun updateBooleanIfChanged(roomCode: String, field: String, value: Boolean) {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return
        if (snapshot.getBoolean(field) == value) return
        updateRoomSafely(roomCode, field, value)
    }

    private suspend fun updateStringListIfChanged(
        roomCode: String,
        field: String,
        value: List<String>
    ) {
        val docRef = db.collection("rooms").document(roomCode)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return
        val current = (snapshot.get(field) as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        if (current == value) return
        updateRoomSafely(roomCode, field, value)
    }

    private suspend fun updateRoomSafely(roomCode: String, field: String, value: Any?) {
        updateRoomSafely(roomCode, mapOf(field to value))
    }

    private suspend fun updateRoomSafely(roomCode: String, values: Map<String, Any?>) {
        repeat(3) { attempt ->
            try {
                db.collection("rooms")
                    .document(roomCode)
                    .update(values + ("lastActivityAt" to System.currentTimeMillis()))
                    .await()
                return
            } catch (throwable: Throwable) {
                if (FirestoreRoomUpdateFailureClassifier.isMissingRoomUpdate(throwable)) {
                    AppLogger.warning(LogCategory.FIREBASE, "Ignoring update for missing room")
                    return
                }
                if (attempt == 2) throw throwable
                delay(200L shl attempt)
            }
        }
    }
}

private const val ABANDONED_ROOM_TIMEOUT_MS = 30L * 60L * 1_000L
private const val ROOM_EXPIRATION_MS = 2L * 60L * 60L * 1_000L
private const val ROOM_LOOKUP_TIMEOUT_MS = 12_000L
private const val COMMAND_EXPIRATION_MS = 10L * 60L * 1_000L

private fun candidateDocumentId(rtcSessionId: String, candidate: IceCandidate): String {
    val value = "$rtcSessionId|${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun signalingGenerationFrom(rtcSessionId: String): Long =
    rtcSessionId.split('-').getOrNull(1)?.toLongOrNull() ?: 0L

internal object FirestoreRoomUpdateFailureClassifier {
    fun isMissingRoomUpdate(throwable: Throwable): Boolean {
        val message = throwable.message ?: return false
        return message.contains("No document to update", ignoreCase = true) ||
            message.contains("NOT_FOUND", ignoreCase = true)
    }
}

private fun VideoRecordingState.toFirebaseValue(): String =
    when (this) {
        VideoRecordingState.Idle -> "idle"
        VideoRecordingState.Recording -> "recording"
        VideoRecordingState.Paused -> "paused"
        VideoRecordingState.Finalizing -> "finalizing"
    }

private fun String?.toVideoRecordingState(): VideoRecordingState =
    when (this?.lowercase()) {
        "recording" -> VideoRecordingState.Recording
        "paused" -> VideoRecordingState.Paused
        "finalizing" -> VideoRecordingState.Finalizing
        else -> VideoRecordingState.Idle
    }
