package com.example.aicameraassistant

import android.graphics.Bitmap
import android.util.Log
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
    val bottom: Double = 0.0
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
        NormalizedFaceBounds(left = left, top = top, right = right, bottom = bottom)

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
        return area in 0.018..0.45 && aspect in 0.55..1.8
    }
}

class FaceBoundsMapper {
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
                bottom = bounds.bottom.coerceIn(0.0, 1.0)
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
            bottom = bounds.bottom.coerceIn(0.0, 1.0)
        )
}

class PreviewBitmapFaceDetector(
    private val detector: FaceDetector,
    private val previewRectProvider: (Bitmap) -> Rect?
) {
    suspend fun detect(bitmap: Bitmap): NormalizedFaceBounds? {
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return null
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val selectedFace = faces.maxByOrNull { face ->
                val box = face.boundingBox
                val area = (box.width() * box.height()).coerceAtLeast(0)
                val centerX = (box.left + box.right).toDouble() / 2.0
                val centerY = (box.top + box.bottom).toDouble() / 2.0
                val centeredness =
                    1.0 - (
                        kotlin.math.abs((centerX / bitmap.width.coerceAtLeast(1)) - 0.5) +
                            kotlin.math.abs((centerY / bitmap.height.coerceAtLeast(1)) - 0.5)
                        ).coerceIn(0.0, 1.0)
                area.toDouble() * (0.75 + (0.25 * centeredness))
            } ?: return null

            val box = selectedFace.boundingBox
            val previewRect = previewRectProvider(bitmap)
                ?: Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
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
        } catch (t: Throwable) {
            Log.w("FACE_DETECTION", "Preview bitmap face detection failed", t)
            null
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
    private val scope: CoroutineScope
) {
    private var lastPublishMs = 0L
    private var lastDetected = false
    private var lastBounds = PortraitFaceBounds()

    fun publish(
        detected: Boolean,
        bounds: PortraitFaceBounds = PortraitFaceBounds(),
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val stateChanged = detected != lastDetected
        val movedSignificantly = detected && bounds.hasMovedSignificantlyFrom(lastBounds)
        if (!force && now - lastPublishMs < 300L) return
        if (!force && !stateChanged && !movedSignificantly) return

        lastPublishMs = now
        lastDetected = detected
        lastBounds = if (detected) bounds else PortraitFaceBounds()
        scope.launch {
            repository.updateFaceDetectionOverlay(
                roomCode = roomCode,
                faceDetected = detected,
                faceBox = if (detected) bounds.toNormalizedFaceBounds() else NormalizedFaceBounds(),
                timestamp = now
            )
        }
    }
}
