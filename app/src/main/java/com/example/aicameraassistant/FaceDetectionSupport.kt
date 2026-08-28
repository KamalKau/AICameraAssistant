package com.example.aicameraassistant

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PortraitFaceBounds(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
    val trackingId: Long = -1L,
    val confidence: Double = 1.0,
    val isPrimary: Boolean = false
) {
    fun isValid(): Boolean =
        right > left && bottom > top

    val width: Double
        get() = (right - left).coerceAtLeast(0.0)

    val height: Double
        get() = (bottom - top).coerceAtLeast(0.0)

    val area: Double
        get() = width * height

    val centerX: Double
        get() = (left + right) / 2.0

    val centerY: Double
        get() = (top + bottom) / 2.0

    fun nearlyEquals(other: PortraitFaceBounds): Boolean =
        kotlin.math.abs(left - other.left) < 0.015 &&
            kotlin.math.abs(top - other.top) < 0.015 &&
            kotlin.math.abs(right - other.right) < 0.015 &&
            kotlin.math.abs(bottom - other.bottom) < 0.015

    fun hasMovedSignificantlyFrom(other: PortraitFaceBounds): Boolean {
        if (!isValid() || !other.isValid()) return isValid() != other.isValid()
        val centerDistance =
            kotlin.math.abs(centerX - other.centerX) + kotlin.math.abs(centerY - other.centerY)
        val sizeDistance =
            kotlin.math.abs(width - other.width) + kotlin.math.abs(height - other.height)
        return centerDistance > 0.045 || sizeDistance > 0.070
    }

    fun toNormalizedFaceBounds(): NormalizedFaceBounds =
        NormalizedFaceBounds(left, top, right, bottom, trackingId, confidence, isPrimary)

    fun isStableCandidateAfter(other: PortraitFaceBounds): Boolean {
        if (!isValid() || !other.isValid()) return false
        val centerDistance =
            kotlin.math.abs(centerX - other.centerX) + kotlin.math.abs(centerY - other.centerY)
        val sizeDistance =
            kotlin.math.abs(width - other.width) + kotlin.math.abs(height - other.height)
        return centerDistance < 0.12 && sizeDistance < 0.16
    }

    fun isPlausiblePhotoFace(): Boolean {
        if (!isValid()) return false
        val aspect = width / height.coerceAtLeast(0.001)
        return area in 0.012..0.52 && aspect in 0.38..2.15
    }
}

data class StableFaceTrackingResult(
    val bounds: List<PortraitFaceBounds> = emptyList(),
    val hasLiveDetection: Boolean = false
)

enum class FaceDetectionState { NO_FACE, TRACKING_FACE, FACE_LOST }

enum class FaceOverlayState { HIDDEN, APPEARING, VISIBLE, FADING }

data class FaceOverlayEvent(
    val show: Boolean = false,
    val detectionState: FaceDetectionState = FaceDetectionState.NO_FACE,
    val eventId: Long = 0L
)

/**
 * Decides when the existing box may be shown. It never controls or pauses analysis.
 * Coordinate and primary-face updates continue regardless of [FaceOverlayEvent.show].
 */
class FaceOverlayEventController(
    private val lostReacquisitionMs: Long = 650L
) {
    private var knownTrackIds = emptySet<Long>()
    private var primaryTrackId: Long? = null
    private var lastFocusBounds = PortraitFaceBounds()
    private var lastLiveDetectionMs = 0L
    private var hadMeaningfulLoss = false
    private var nextEventId = 1L

    fun update(
        faces: List<PortraitFaceBounds>,
        hasLiveDetection: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): FaceOverlayEvent {
        if (!hasLiveDetection) {
            if (lastLiveDetectionMs == 0L) {
                return FaceOverlayEvent(detectionState = FaceDetectionState.NO_FACE)
            }
            if (nowMs - lastLiveDetectionMs >= lostReacquisitionMs) hadMeaningfulLoss = true
            return FaceOverlayEvent(detectionState = FaceDetectionState.FACE_LOST)
        }

        val validFaces = faces.filter { it.isValid() }
        if (validFaces.isEmpty()) return FaceOverlayEvent(detectionState = FaceDetectionState.NO_FACE)

        val currentIds = validFaces.map { it.trackingId }.filter { it >= 0 }.toSet()
        val currentPrimary = validFaces.firstOrNull { it.isPrimary } ?: validFaces.first()
        val newFaceEntered = knownTrackIds.isNotEmpty() && currentIds.any { it !in knownTrackIds }
        val firstAcquisition = knownTrackIds.isEmpty()
        val primaryChanged = primaryTrackId != null && currentPrimary.trackingId != primaryTrackId
        val focusReacquisition = lastFocusBounds.isValid() &&
            currentPrimary.hasMovedSignificantlyFrom(lastFocusBounds)
        val shouldShow = firstAcquisition || newFaceEntered || primaryChanged ||
            hadMeaningfulLoss || focusReacquisition

        lastLiveDetectionMs = nowMs
        knownTrackIds = currentIds
        primaryTrackId = currentPrimary.trackingId
        if (shouldShow) {
            lastFocusBounds = currentPrimary
            hadMeaningfulLoss = false
        }
        return FaceOverlayEvent(
            show = shouldShow,
            detectionState = FaceDetectionState.TRACKING_FACE,
            eventId = if (shouldShow) nextEventId++ else 0L
        )
    }

    fun forceNextDetection() {
        knownTrackIds = emptySet()
        primaryTrackId = null
        lastFocusBounds = PortraitFaceBounds()
        lastLiveDetectionMs = 0L
        hadMeaningfulLoss = false
    }
}

class StableFaceTracker(
    private val maxFaces: Int = 5,
    private val holdDurationMs: Long = 650L,
    private val acquisitionHits: Int = 2
) {
    private data class Track(
        val id: Long,
        var bounds: PortraitFaceBounds,
        var lastSeenMs: Long,
        var hits: Int = 1
    )

    private val tracks = mutableListOf<Track>()
    private var nextTrackId = 1L
    private var primaryTrackId: Long? = null

    fun update(
        detections: List<PortraitFaceBounds>,
        nowMs: Long = System.currentTimeMillis()
    ): StableFaceTrackingResult {
        val plausibleDetections = detections
            .filter { it.isPlausiblePhotoFace() }
            .sortedByDescending { it.area }
            .take(maxFaces)

        if (plausibleDetections.isEmpty()) {
            pruneExpired(nowMs)
            return StableFaceTrackingResult(
                bounds = sortedVisibleTracks(),
                hasLiveDetection = false
            )
        }

        val matchedTrackIds = mutableSetOf<Long>()
        plausibleDetections.forEach { detection ->
            val detectorIdMatch = detection.trackingId.takeIf { it >= 0 }?.let { detectorId ->
                tracks.firstOrNull { it.bounds.trackingId == detectorId && it.id !in matchedTrackIds }
            }
            val match = detectorIdMatch ?: tracks
                .filterNot { it.id in matchedTrackIds }
                .minByOrNull { it.bounds.trackingDistanceTo(detection) }
                ?.takeIf { it.bounds.trackingDistanceTo(detection) < 0.34 }

            if (match == null) {
                val newTrackId = nextTrackId++
                tracks += Track(
                    id = newTrackId,
                    bounds = detection.copy(
                        trackingId = detection.trackingId.takeIf { it >= 0 } ?: newTrackId
                    ),
                    lastSeenMs = nowMs
                )
                matchedTrackIds += newTrackId
            } else {
                val distance = match.bounds.trackingDistanceTo(detection)
                val smoothing = when {
                    distance > 0.18 -> 0.55
                    distance > 0.08 -> 0.38
                    else -> 0.24
                }
                match.bounds = match.bounds.interpolateTo(detection, smoothing)
                match.lastSeenMs = nowMs
                match.hits += 1
                matchedTrackIds += match.id
            }
        }

        pruneExpired(nowMs)
        return StableFaceTrackingResult(
            bounds = sortedVisibleTracks(),
            hasLiveDetection = true
        )
    }

    fun reset() {
        tracks.clear()
        primaryTrackId = null
    }

    private fun pruneExpired(nowMs: Long) {
        tracks.removeAll { nowMs - it.lastSeenMs > holdDurationMs }
        if (tracks.size > maxFaces) {
            val keepIds = tracks
                .sortedByDescending { it.bounds.area }
                .take(maxFaces)
                .map { it.id }
                .toSet()
            tracks.removeAll { it.id !in keepIds }
        }
    }

    private fun sortedVisibleTracks(): List<PortraitFaceBounds> =
        tracks.filter { it.hits >= acquisitionHits }
            .let { visible ->
                val existingPrimary = visible.firstOrNull { it.id == primaryTrackId }
                val primary = existingPrimary ?: visible.maxWithOrNull(
                    compareBy<Track> { it.bounds.area }
                        .thenBy { -kotlin.math.abs(it.bounds.centerX - 0.5) - kotlin.math.abs(it.bounds.centerY - 0.5) }
                )
                primaryTrackId = primary?.id
                visible.sortedWith(compareByDescending<Track> { it.id == primaryTrackId }.thenByDescending { it.bounds.area })
            }
            .map { it.bounds.copy(isPrimary = it.id == primaryTrackId) }
            .filter { it.isValid() }
}

private fun PortraitFaceBounds.interpolateTo(
    target: PortraitFaceBounds,
    amount: Double
): PortraitFaceBounds {
    val t = amount.coerceIn(0.0, 1.0)
    fun lerp(start: Double, end: Double): Double = start + ((end - start) * t)
    return PortraitFaceBounds(
        left = lerp(left, target.left).coerceIn(0.0, 1.0),
        top = lerp(top, target.top).coerceIn(0.0, 1.0),
        right = lerp(right, target.right).coerceIn(0.0, 1.0),
        bottom = lerp(bottom, target.bottom).coerceIn(0.0, 1.0),
        trackingId = if (target.trackingId >= 0) target.trackingId else trackingId,
        confidence = lerp(confidence, target.confidence),
        isPrimary = isPrimary
    )
}

private fun PortraitFaceBounds.trackingDistanceTo(other: PortraitFaceBounds): Double {
    if (!isValid() || !other.isValid()) return Double.MAX_VALUE
    val centerDistance =
        kotlin.math.abs(centerX - other.centerX) + kotlin.math.abs(centerY - other.centerY)
    val sizeDistance =
        kotlin.math.abs(width - other.width) + kotlin.math.abs(height - other.height)
    return centerDistance + (sizeDistance * 0.55)
}

class FaceBoundsMapper {
    fun mapAnalysisBoundsToPreview(
        bounds: List<NormalizedFaceBounds>,
        isFrontCamera: Boolean
    ): List<PortraitFaceBounds> =
        bounds.map { mapAnalysisBoundsToPreview(it, isFrontCamera) }

    fun mapAnalysisBoundsToPreview(
        bounds: NormalizedFaceBounds,
        isFrontCamera: Boolean
    ): PortraitFaceBounds {
        val left = bounds.left.coerceIn(0.0, 1.0)
        val right = bounds.right.coerceIn(0.0, 1.0)
        return if (isFrontCamera) {
            PortraitFaceBounds(
                left = (1.0 - right).coerceIn(0.0, 1.0),
                top = bounds.top.coerceIn(0.0, 1.0),
                right = (1.0 - left).coerceIn(0.0, 1.0),
                bottom = bounds.bottom.coerceIn(0.0, 1.0),
                trackingId = bounds.trackingId,
                confidence = bounds.confidence,
                isPrimary = bounds.isPrimary
            )
        } else {
            mapPreviewBounds(bounds)
        }
    }

    fun mapPreviewBounds(bounds: NormalizedFaceBounds): PortraitFaceBounds =
        PortraitFaceBounds(
            left = bounds.left.coerceIn(0.0, 1.0),
            top = bounds.top.coerceIn(0.0, 1.0),
            right = bounds.right.coerceIn(0.0, 1.0),
            bottom = bounds.bottom.coerceIn(0.0, 1.0),
            trackingId = bounds.trackingId,
            confidence = bounds.confidence,
            isPrimary = bounds.isPrimary
        )

    fun mapPreviewBounds(bounds: List<NormalizedFaceBounds>): List<PortraitFaceBounds> =
        bounds.map { mapPreviewBounds(it) }
}

fun List<PortraitFaceBounds>.isStableCandidateAfter(other: List<PortraitFaceBounds>): Boolean {
    if (isEmpty() || other.isEmpty()) return false
    return first().isStableCandidateAfter(other.first())
}

fun List<PortraitFaceBounds>.haveMovedSignificantlyFrom(other: List<PortraitFaceBounds>): Boolean {
    if (size != other.size) return true
    return zip(other).any { (current, previous) ->
        current.hasMovedSignificantlyFrom(previous)
    }
}

class PreviewBitmapFaceDetector(
    private val detector: FaceDetector,
    private val previewRectProvider: (Bitmap) -> Rect?
) {
    suspend fun detect(bitmap: Bitmap): List<NormalizedFaceBounds> {
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val previewRect = previewRectProvider(bitmap)
                ?: Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            faces
                .sortedByDescending { face ->
                    val box = face.boundingBox
                    box.width() * box.height()
                }
                .map { face ->
                    val box = face.boundingBox
                    NormalizedFaceBounds(
                        left = ((box.left - previewRect.left).toDouble() / previewRect.width)
                            .coerceIn(0.0, 1.0),
                        top = ((box.top - previewRect.top).toDouble() / previewRect.height)
                            .coerceIn(0.0, 1.0),
                        right = ((box.right - previewRect.left).toDouble() / previewRect.width)
                            .coerceIn(0.0, 1.0),
                        bottom = ((box.bottom - previewRect.top).toDouble() / previewRect.height)
                            .coerceIn(0.0, 1.0)
                    )
                }
        } catch (t: Throwable) {
            AppLogger.warning(LogCategory.CAMERA, t)
            emptyList()
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

class FaceOverlayPublisher(
    private val repository: FirebaseRoomRepository,
    private val roomCode: String,
    private val scope: CoroutineScope,
    private val sessionIdProvider: () -> String = { "" }
) {
    private var lastPublishMs = 0L
    private var lastDetected = false
    private var lastBounds = emptyList<PortraitFaceBounds>()
    private var overlayEventId = 0L

    fun publish(
        detected: Boolean,
        bounds: List<PortraitFaceBounds> = emptyList(),
        showOverlay: Boolean = false,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val stateChanged = detected != lastDetected
        val movedSignificantly = detected && bounds.haveMovedSignificantlyFrom(lastBounds)
        if (!force && now - lastPublishMs < 300L) return
        if (!force && !stateChanged && !movedSignificantly) return

        lastPublishMs = now
        lastDetected = detected
        lastBounds = if (detected) bounds else emptyList()
        if (showOverlay) overlayEventId += 1L
        val primaryFace = bounds.firstOrNull() ?: PortraitFaceBounds()
        val publishedOverlayEventId = overlayEventId
        val publishedSessionId = sessionIdProvider()
        scope.launch {
            repository.updateFaceDetectionOverlay(
                roomCode = roomCode,
                faceDetected = detected,
                faceBox = if (detected) primaryFace.toNormalizedFaceBounds() else NormalizedFaceBounds(),
                faceBoxes = if (detected) bounds.map { it.toNormalizedFaceBounds() } else emptyList(),
                timestamp = now,
                sessionId = publishedSessionId,
                overlayEventId = publishedOverlayEventId
            )
        }
    }

    fun publish(
        detected: Boolean,
        bounds: PortraitFaceBounds = PortraitFaceBounds(),
        showOverlay: Boolean = false,
        force: Boolean = false
    ) {
        publish(
            detected = detected,
            bounds = if (detected && bounds.isValid()) listOf(bounds) else emptyList(),
            showOverlay = showOverlay,
            force = force
        )
    }

}
