package com.example.aicameraassistant

import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import java.util.concurrent.atomic.AtomicLong

data class SceneDetectionResult(
    val key: String,
    val label: String,
    val suggestion: String,
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toState(autoAdjustment: String = ""): SceneDetectionState =
        SceneDetectionState(
            key = key,
            label = label,
            suggestion = suggestion,
            confidence = confidence,
            timestamp = timestamp,
            autoAdjustment = autoAdjustment
        )
}

val configurableSceneCategories: Set<String> = linkedSetOf(
    "person", "group", "landscape", "food", "pet", "document", "sunset", "night",
    "indoor", "outdoor", "macro", "backlit", "snow_beach", "unknown"
)

class SceneAnalysisThrottle(private val intervalMs: Long) {
    private val lastRunMs = AtomicLong(0L)

    fun tryAcquire(nowMs: Long): Boolean {
        while (true) {
            val previous = lastRunMs.get()
            if (nowMs - previous < intervalMs) return false
            if (lastRunMs.compareAndSet(previous, nowMs)) return true
        }
    }
}

class StableSceneTracker(
    private val confirmationMs: Long = 650L,
    private val unknownDelayMs: Long = 1_500L,
    private val minimumConfidence: Double = 0.46
) {
    private var current: SceneDetectionResult? = null
    private var candidate: SceneDetectionResult? = null
    private var candidateStartedMs = 0L

    fun update(result: SceneDetectionResult, nowMs: Long = result.timestamp): SceneDetectionResult? {
        val normalized = result.takeIf {
            it.key in configurableSceneCategories && it.confidence >= minimumConfidence
        } ?: sceneDetectionResult("unknown", result.confidence, nowMs)
        val stable = current
        if (stable?.key == normalized.key) {
            candidate = null
            return stable.copy(
                confidence = stable.confidence * 0.65 + normalized.confidence * 0.35,
                timestamp = nowMs
            ).also { current = it }
        }
        if (candidate?.key != normalized.key) {
            candidate = normalized
            candidateStartedMs = nowMs
            return stable
        }
        val requiredMs = if (normalized.key == "unknown") unknownDelayMs else confirmationMs
        if (nowMs - candidateStartedMs < requiredMs) return stable
        return normalized.copy(timestamp = nowMs).also {
            current = it
            candidate = null
        }
    }

    fun reset() {
        current = null
        candidate = null
        candidateStartedMs = 0L
    }
}

interface OnDeviceSceneClassifier {
    val modelVersion: String
    fun detect(imageProxy: ImageProxy): SceneDetectionResult
}

/** Replaceable on-device classifier; this built-in v1 uses sampled YUV features without bitmaps. */
class SceneDetectionAnalyzer(
    override val modelVersion: String = "builtin-yuv-scenes-v1"
) : OnDeviceSceneClassifier {
    override fun detect(imageProxy: ImageProxy): SceneDetectionResult {
        val planes = imageProxy.planes
        if (planes.isEmpty() || imageProxy.width <= 0 || imageProxy.height <= 0) {
            return sceneDetectionResult("unknown", 0.0)
        }

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        val width = imageProxy.width
        val height = imageProxy.height
        val stepX = max(8, width / 28)
        val stepY = max(8, height / 28)

        var samples = 0
        var luminanceSum = 0.0
        val luminanceHistogram = IntArray(256)
        var saturatedWarm = 0
        var greenBlue = 0
        var skyBlue = 0
        var strongEdges = 0
        var textLikeEdges = 0

        var previousRowLuma = -1
        var previousLuma = -1

        var y = 0
        while (y < height) {
            previousLuma = -1
            var x = 0
            while (x < width) {
                val luma = readPlaneValue(yBuffer, yPlane.rowStride, yPlane.pixelStride, x, y)
                val rgb = readRgb(imageProxy, x, y, luma)
                val maxChannel = max(rgb.red, max(rgb.green, rgb.blue))
                val minChannel = minOf(rgb.red, rgb.green, rgb.blue)
                val saturation = maxChannel - minChannel

                luminanceSum += luma
                luminanceHistogram[luma.coerceIn(0, 255)] += 1
                samples += 1

                if (saturation > 42 && rgb.red > rgb.green * 0.95 && rgb.green > rgb.blue * 0.75) {
                    saturatedWarm += 1
                }
                if (rgb.green > rgb.red * 1.08 && rgb.green > rgb.blue * 0.72 && luma > 72) {
                    greenBlue += 1
                }
                if (rgb.blue > rgb.red * 1.12 && rgb.green > rgb.red * 1.04 && luma > 96) {
                    skyBlue += 1
                }

                if (previousLuma >= 0) {
                    val edge = abs(luma - previousLuma)
                    if (edge > 38) strongEdges += 1
                    if (edge > 54) textLikeEdges += 1
                }
                if (previousRowLuma >= 0) {
                    val edge = abs(luma - previousRowLuma)
                    if (edge > 54) textLikeEdges += 1
                }
                previousLuma = luma
                previousRowLuma = luma
                x += stepX
            }
            y += stepY
        }

        if (samples == 0) return sceneDetectionResult("unknown", 0.0)

        val averageLuma = luminanceSum / samples
        val shadowLuma = histogramPercentile(luminanceHistogram, samples, 0.25)
        val highlightLuma = histogramPercentile(luminanceHistogram, samples, 0.90)
        val clippedHighlights = luminanceHistogram.sliceArray(245..255).sum().toDouble() / samples
        val warmRatio = saturatedWarm.toDouble() / samples.toDouble()
        val landscapeRatio = (greenBlue + skyBlue).toDouble() / samples.toDouble()
        val edgeRatio = strongEdges.toDouble() / samples.toDouble()
        val textRatio = textLikeEdges.toDouble() / samples.toDouble()

        return when {
            (averageLuma < 60.0 && shadowLuma < 48) ||
                (shadowLuma < 30 && averageLuma < 102.0 && clippedHighlights < 0.32) -> {
                val shadowDarkness = (1.0 - shadowLuma / 65.0).coerceIn(0.0, 1.0)
                val sceneDarkness = (1.0 - averageLuma / 115.0).coerceIn(0.0, 1.0)
                val highlightProtection = if (highlightLuma > 225) 0.04 else 0.0
                sceneDetectionResult(
                    "night",
                    (0.68 * shadowDarkness + 0.32 * sceneDarkness - highlightProtection)
                        .coerceIn(0.48, 0.94)
                )
            }
            highlightLuma - shadowLuma > 190 && clippedHighlights > 0.12 ->
                sceneDetectionResult("backlit", (clippedHighlights + 0.55).coerceIn(0.5, 0.92))
            textRatio > 0.34 && edgeRatio > 0.26 -> sceneDetectionResult("document", textRatio.coerceIn(0.48, 0.9))
            warmRatio > 0.28 && skyBlue > samples * 0.08 -> sceneDetectionResult("sunset", warmRatio.coerceIn(0.5, 0.9))
            warmRatio > 0.22 && averageLuma > 62.0 -> sceneDetectionResult("food", warmRatio.coerceIn(0.46, 0.88))
            landscapeRatio > 0.34 && averageLuma > 70.0 -> sceneDetectionResult("landscape", landscapeRatio.coerceIn(0.46, 0.88))
            skyBlue > samples * 0.18 && averageLuma > 100 -> sceneDetectionResult("outdoor", 0.58)
            edgeRatio > 0.38 -> sceneDetectionResult("macro", edgeRatio.coerceIn(0.48, 0.82))
            averageLuma in 62.0..150.0 -> sceneDetectionResult("indoor", 0.5)
            else -> sceneDetectionResult("unknown", 0.32)
        }
    }

    private fun histogramPercentile(histogram: IntArray, samples: Int, percentile: Double): Int {
        val target = (samples * percentile).toInt().coerceIn(1, samples)
        var cumulative = 0
        histogram.forEachIndexed { value, count ->
            cumulative += count
            if (cumulative >= target) return value
        }
        return 255
    }

    private fun readRgb(imageProxy: ImageProxy, x: Int, y: Int, luma: Int): RgbSample {
        if (imageProxy.planes.size < 3) return RgbSample(luma, luma, luma)
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val uvX = x / 2
        val uvY = y / 2
        val u = readPlaneValue(uPlane.buffer.duplicate(), uPlane.rowStride, uPlane.pixelStride, uvX, uvY) - 128
        val v = readPlaneValue(vPlane.buffer.duplicate(), vPlane.rowStride, vPlane.pixelStride, uvX, uvY) - 128
        val red = (luma + 1.402f * v).toInt().coerceIn(0, 255)
        val green = (luma - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
        val blue = (luma + 1.772f * u).toInt().coerceIn(0, 255)
        return RgbSample(red, green, blue)
    }

    private fun readPlaneValue(
        buffer: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        x: Int,
        y: Int
    ): Int {
        val index = y * rowStride + x * pixelStride
        if (index < 0 || index >= buffer.limit()) return 0
        return buffer.get(index).toInt() and 0xFF
    }
}

private data class RgbSample(
    val red: Int,
    val green: Int,
    val blue: Int
)

fun sceneLabelForKey(key: String): String =
    when (key) {
        "food" -> "Food"
        "night" -> "Night"
        "person" -> "Portrait / Person"
        "group" -> "Group"
        "pet" -> "Pet"
        "document" -> "Document"
        "sunset" -> "Sunset"
        "indoor" -> "Indoor"
        "outdoor" -> "Outdoor"
        "macro" -> "Macro / Close-up"
        "backlit" -> "Backlit"
        "snow_beach" -> "Snow / Beach"
        "landscape" -> "Landscape"
        else -> "Unknown / General"
    }

fun sceneDetectionResult(
    key: String,
    confidence: Double,
    timestamp: Long = System.currentTimeMillis()
): SceneDetectionResult {
    val safeKey = key.takeIf { it in configurableSceneCategories } ?: "unknown"
    val suggestion = when (safeKey) {
        "food" -> "Boosting warm detail for food"
        "night" -> "Night mode suggested for low light"
        "person" -> "Prioritize face focus and exposure"
        "group" -> "Keep everyone inside the frame"
        "document" -> "Document mode recommended"
        "backlit" -> "HDR recommended for strong backlight"
        "macro" -> "Check close-focus distance"
        "landscape" -> "Grid helps keep the horizon level"
        else -> "Scene detection ready"
    }
    return SceneDetectionResult(
        key = safeKey,
        label = sceneLabelForKey(safeKey),
        suggestion = suggestion,
        confidence = confidence.coerceIn(0.0, 1.0),
        timestamp = timestamp
    )
}
