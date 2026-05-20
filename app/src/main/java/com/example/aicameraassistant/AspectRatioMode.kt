package com.example.aicameraassistant

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

enum class AspectRatioMode(
    val key: String,
    val label: String,
    val ratio: Float?
) {
    Full("full", "Full", null),
    NineSixteen("9_16", "9:16", 9f / 16f),
    ThreeFour("3_4", "3:4", 3f / 4f),
    OneOne("1_1", "1:1", 1f);

    companion object {
        fun fromKey(key: String): AspectRatioMode =
            entries.firstOrNull { it.key == key } ?: Full

        fun next(currentKey: String): AspectRatioMode {
            val modes = entries.toList()
            val currentIndex = modes.indexOf(fromKey(currentKey)).coerceAtLeast(0)
            return modes[(currentIndex + 1) % modes.size]
        }
    }
}

fun aspectRatioFrameRect(baseRect: Rect?, aspectRatioKey: String): Rect? {
    val rect = baseRect ?: return null
    val ratio = AspectRatioMode.fromKey(aspectRatioKey).ratio ?: return rect
    if (rect.width <= 0f || rect.height <= 0f) return rect

    val baseRatio = rect.width / rect.height
    return if (baseRatio > ratio) {
        val width = rect.height * ratio
        val left = rect.left + ((rect.width - width) / 2f)
        Rect(left, rect.top, left + width, rect.bottom)
    } else {
        val height = rect.width / ratio
        val top = rect.top + ((rect.height - height) / 2f)
        Rect(rect.left, top, rect.right, top + height)
    }
}

fun aspectRatioFrameRect(
    containerWidth: Float,
    containerHeight: Float,
    aspectRatioKey: String
): Rect? {
    if (containerWidth <= 0f || containerHeight <= 0f) return null
    return aspectRatioFrameRect(
        baseRect = Rect(0f, 0f, containerWidth, containerHeight),
        aspectRatioKey = aspectRatioKey
    )
}

fun fittedPreviewRectInFrame(
    frameRect: Rect?,
    contentWidth: Float,
    contentHeight: Float
): Rect? {
    val frame = frameRect ?: return null
    val fitted = fittedPreviewRect(
        containerWidth = frame.width,
        containerHeight = frame.height,
        contentWidth = contentWidth,
        contentHeight = contentHeight
    ) ?: return frame
    return Rect(
        left = frame.left + fitted.left,
        top = frame.top + fitted.top,
        right = frame.left + fitted.right,
        bottom = frame.top + fitted.bottom
    )
}

@Composable
fun AspectRatioFrameOverlay(
    baseRect: Rect?,
    frameRect: Rect?,
    modifier: Modifier = Modifier
) {
    val base = baseRect ?: return
    val frame = frameRect ?: return
    if (base == frame) return

    Canvas(modifier = modifier) {
        val shade = Color.Black.copy(alpha = 0.42f)
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(base.left, base.top),
            size = androidx.compose.ui.geometry.Size(base.width, (frame.top - base.top).coerceAtLeast(0f))
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(base.left, frame.bottom),
            size = androidx.compose.ui.geometry.Size(base.width, (base.bottom - frame.bottom).coerceAtLeast(0f))
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(base.left, frame.top),
            size = androidx.compose.ui.geometry.Size((frame.left - base.left).coerceAtLeast(0f), frame.height)
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(frame.right, frame.top),
            size = androidx.compose.ui.geometry.Size((base.right - frame.right).coerceAtLeast(0f), frame.height)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = androidx.compose.ui.geometry.Offset(frame.left, frame.top),
            size = androidx.compose.ui.geometry.Size(frame.width, 1.2f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = androidx.compose.ui.geometry.Offset(frame.left, frame.bottom - 1.2f),
            size = androidx.compose.ui.geometry.Size(frame.width, 1.2f)
        )
    }
}
