package com.example.aicameraassistant

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.camera.view.PreviewView
import kotlin.math.max
import kotlin.math.abs

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun isPreviewSceneDark(previewView: PreviewView): Boolean {
    return capturePreviewLumaSample(previewView)?.isLowLight == true
}

data class PreviewLumaSample(
    val values: IntArray,
    val average: Double,
    val shadowPercentile: Int,
    val highlightPercentile: Int,
    val clippedHighlightRatio: Double
) {
    val isLowLight: Boolean
        get() =
            average < 62.0 ||
                (shadowPercentile < 38 && average < 102.0 && clippedHighlightRatio < 0.32)
}

fun capturePreviewLumaSample(previewView: PreviewView): PreviewLumaSample? {
    val bitmap = previewView.bitmap ?: return null
    return try {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val startX = bitmap.width / 12
        val endX = bitmap.width - startX
        val startY = bitmap.height / 12
        val endY = bitmap.height - startY
        val sampleX = max(1, (endX - startX) / 20)
        val sampleY = max(1, (endY - startY) / 20)
        val values = ArrayList<Int>(441)

        for (y in startY until endY step sampleY) {
            for (x in startX until endX step sampleX) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                values += ((0.299 * red) + (0.587 * green) + (0.114 * blue)).toInt()
            }
        }
        analyzePreviewLuma(values.toIntArray())
    } finally {
        bitmap.recycle()
    }
}

fun analyzePreviewLuma(values: IntArray): PreviewLumaSample? {
    if (values.isEmpty()) return null
    val safeValues = IntArray(values.size) { values[it].coerceIn(0, 255) }
    val sorted = safeValues.sortedArray()
    val average = safeValues.average()
    val shadow = sorted[((sorted.lastIndex) * 0.25f).toInt()]
    val highlight = sorted[((sorted.lastIndex) * 0.9f).toInt()]
    val clippedRatio = safeValues.count { it >= 245 }.toDouble() / safeValues.size
    return PreviewLumaSample(safeValues, average, shadow, highlight, clippedRatio)
}

fun previewMotionScore(first: PreviewLumaSample?, second: PreviewLumaSample?): Double {
    if (first == null || second == null || first.values.size != second.values.size) return 0.0
    val globalLumaShift = second.average - first.average
    return first.values.indices.map { index ->
        abs((second.values[index] - first.values[index]) - globalLumaShift)
    }.average()
}
