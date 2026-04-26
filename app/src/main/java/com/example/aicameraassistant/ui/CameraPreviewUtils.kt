package com.example.aicameraassistant

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.camera.view.PreviewView
import kotlin.math.max

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun isPreviewSceneDark(previewView: PreviewView): Boolean {
    val bitmap = previewView.bitmap ?: return true
    return try {
        val startX = bitmap.width / 4
        val endX = bitmap.width - startX
        val startY = bitmap.height / 4
        val endY = bitmap.height - startY
        val sampleX = max(1, (endX - startX) / 18)
        val sampleY = max(1, (endY - startY) / 18)
        var luminanceSum = 0.0
        var samples = 0

        for (x in startX until endX step sampleX) {
            for (y in startY until endY step sampleY) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                luminanceSum += (0.299 * red) + (0.587 * green) + (0.114 * blue)
                samples++
            }
        }

        val averageLuminance = if (samples == 0) 255.0 else luminanceSum / samples
        averageLuminance < 60.0
    } finally {
        bitmap.recycle()
    }
}
