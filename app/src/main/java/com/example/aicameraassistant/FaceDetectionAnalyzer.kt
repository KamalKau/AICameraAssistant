package com.example.aicameraassistant

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.atomic.AtomicBoolean

data class NormalizedFaceBounds(
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
}

data class FaceDetectionOverlayState(
    val faceDetected: Boolean = false,
    val faceBox: NormalizedFaceBounds = NormalizedFaceBounds(),
    val faceBoxes: List<NormalizedFaceBounds> = emptyList(),
    val timestamp: Long = 0L,
    val sessionId: String = "",
    val overlayEventId: Long = 0L
)

/** Device-aware filtering kept separate from ML Kit so thresholds can be tuned safely. */
class FaceConfidencePolicy(
    private val minimumConfidence: Double = 0.58
) {
    fun estimate(
        yawDegrees: Float,
        visibleArea: Double,
        touchesFrameEdge: Boolean
    ): Double {
        val profilePenalty = (kotlin.math.abs(yawDegrees) / 180.0).coerceIn(0.0, 0.22)
        val sizeScore = (visibleArea / 0.025).coerceIn(0.0, 1.0)
        val edgePenalty = if (touchesFrameEdge) 0.08 else 0.0
        return (0.64 + 0.28 * sizeScore - profilePenalty - edgePenalty).coerceIn(0.0, 1.0)
    }

    fun accepts(confidence: Double): Boolean = confidence >= minimumConfidence
}

class MlKitFaceDetectionAnalyzer(
    private val detector: FaceDetector,
    private val minProcessIntervalMs: Long = 800L,
    private val sceneAnalyzer: SceneDetectionAnalyzer? = null,
    private val shouldAnalyzeScene: () -> Boolean = { false },
    private val onSceneResult: (SceneDetectionResult) -> Unit = {},
    private val confidencePolicy: FaceConfidencePolicy = FaceConfidencePolicy(),
    private val onFaceResult: (List<NormalizedFaceBounds>) -> Unit
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    private var lastProcessStartedMs = 0L
    private var lastSceneProcessMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessStartedMs < minProcessIntervalMs) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastProcessStartedMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        sceneAnalyzer?.takeIf { shouldAnalyzeScene() && now - lastSceneProcessMs >= 300L }?.let { analyzer ->
            runCatching {
                lastSceneProcessMs = now
                onSceneResult(analyzer.detect(imageProxy))
            }.onFailure {
                AppLogger.warning(LogCategory.CAMERA, it)
            }
        }
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val uprightWidth =
            if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val uprightHeight =
            if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        try {
            detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty() || uprightWidth <= 0 || uprightHeight <= 0) {
                    onFaceResult(emptyList())
                    return@addOnSuccessListener
                }

                onFaceResult(
                    faces
                        .sortedByDescending { face ->
                            val box = face.boundingBox
                            box.width() * box.height()
                        }
                        .mapNotNull { face ->
                            val box = face.boundingBox
                            val normalized = NormalizedFaceBounds(
                                left = (box.left.toDouble() / uprightWidth).coerceIn(0.0, 1.0),
                                top = (box.top.toDouble() / uprightHeight).coerceIn(0.0, 1.0),
                                right = (box.right.toDouble() / uprightWidth).coerceIn(0.0, 1.0),
                                bottom = (box.bottom.toDouble() / uprightHeight).coerceIn(0.0, 1.0),
                                trackingId = face.trackingId?.toLong() ?: -1L
                            )
                            val confidence = confidencePolicy.estimate(
                                yawDegrees = face.headEulerAngleY,
                                visibleArea = (normalized.right - normalized.left) *
                                    (normalized.bottom - normalized.top),
                                touchesFrameEdge = box.left <= 1 || box.top <= 1 ||
                                    box.right >= uprightWidth - 1 || box.bottom >= uprightHeight - 1
                            )
                            normalized.copy(confidence = confidence)
                                .takeIf { confidencePolicy.accepts(confidence) }
                        }
                )
            }
            .addOnFailureListener {
                AppLogger.warning(LogCategory.CAMERA, it)
                onFaceResult(emptyList())
            }
            .addOnCompleteListener {
                try {
                    isProcessing.set(false)
                } finally {
                    imageProxy.close()
                }
            }
        } catch (t: Throwable) {
            AppLogger.warning(LogCategory.CAMERA, t)
            onFaceResult(emptyList())
            try {
                isProcessing.set(false)
            } finally {
                imageProxy.close()
            }
        }
    }
}
