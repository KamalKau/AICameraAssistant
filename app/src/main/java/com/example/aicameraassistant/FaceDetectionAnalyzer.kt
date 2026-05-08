package com.example.aicameraassistant

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.atomic.AtomicBoolean

data class NormalizedFaceBounds(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0
) {
    fun isValid(): Boolean =
        right > left && bottom > top
}

data class FaceDetectionOverlayState(
    val faceDetected: Boolean = false,
    val faceBox: NormalizedFaceBounds = NormalizedFaceBounds(),
    val faceBoxes: List<NormalizedFaceBounds> = emptyList(),
    val timestamp: Long = 0L
)

class MlKitFaceDetectionAnalyzer(
    private val detector: FaceDetector,
    private val minProcessIntervalMs: Long = 800L,
    private val onFaceResult: (List<NormalizedFaceBounds>) -> Unit
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    private var lastProcessStartedMs = 0L

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
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val uprightWidth =
            if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val uprightHeight =
            if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

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
                        .map { face ->
                            val box = face.boundingBox
                            NormalizedFaceBounds(
                                left = (box.left.toDouble() / uprightWidth).coerceIn(0.0, 1.0),
                                top = (box.top.toDouble() / uprightHeight).coerceIn(0.0, 1.0),
                                right = (box.right.toDouble() / uprightWidth).coerceIn(0.0, 1.0),
                                bottom = (box.bottom.toDouble() / uprightHeight).coerceIn(0.0, 1.0)
                            )
                        }
                )
            }
            .addOnFailureListener {
                Log.w("FACE_DETECTION", "ML Kit face detection failed", it)
                onFaceResult(emptyList())
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
}
