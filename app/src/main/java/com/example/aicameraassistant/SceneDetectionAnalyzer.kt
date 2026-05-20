package com.example.aicameraassistant

import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max

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

class SceneDetectionAnalyzer {
    fun detect(imageProxy: ImageProxy): SceneDetectionResult {
        val planes = imageProxy.planes
        if (planes.isEmpty() || imageProxy.width <= 0 || imageProxy.height <= 0) {
            return sceneDetectionResult("auto", 0.0)
        }

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        val width = imageProxy.width
        val height = imageProxy.height
        val stepX = max(8, width / 28)
        val stepY = max(8, height / 28)

        var samples = 0
        var luminanceSum = 0.0
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

        if (samples == 0) return sceneDetectionResult("auto", 0.0)

        val averageLuma = luminanceSum / samples
        val warmRatio = saturatedWarm.toDouble() / samples.toDouble()
        val landscapeRatio = (greenBlue + skyBlue).toDouble() / samples.toDouble()
        val edgeRatio = strongEdges.toDouble() / samples.toDouble()
        val textRatio = textLikeEdges.toDouble() / samples.toDouble()

        return when {
            averageLuma < 54.0 -> sceneDetectionResult("night", (1.0 - averageLuma / 72.0).coerceIn(0.45, 0.94))
            textRatio > 0.34 && edgeRatio > 0.26 -> sceneDetectionResult("text", textRatio.coerceIn(0.48, 0.9))
            warmRatio > 0.22 && averageLuma > 62.0 -> sceneDetectionResult("food", warmRatio.coerceIn(0.46, 0.88))
            landscapeRatio > 0.34 && averageLuma > 70.0 -> sceneDetectionResult("landscape", landscapeRatio.coerceIn(0.46, 0.88))
            else -> sceneDetectionResult("auto", 0.32)
        }
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
        "face" -> "Face"
        "text" -> "Text"
        "landscape" -> "Landscape"
        else -> "Auto"
    }

fun sceneDetectionResult(key: String, confidence: Double): SceneDetectionResult {
    val safeKey = when (key) {
        "food", "night", "face", "text", "landscape" -> key
        else -> "auto"
    }
    val suggestion = when (safeKey) {
        "food" -> "Boosting warm detail for food"
        "night" -> "Night mode suggested for low light"
        "face" -> "Focus and exposure adjusted for faces"
        "text" -> "Hold steady for sharper text"
        "landscape" -> "Grid helps keep the horizon level"
        else -> "Scene detection ready"
    }
    return SceneDetectionResult(
        key = safeKey,
        label = sceneLabelForKey(safeKey),
        suggestion = suggestion,
        confidence = confidence.coerceIn(0.0, 1.0)
    )
}
