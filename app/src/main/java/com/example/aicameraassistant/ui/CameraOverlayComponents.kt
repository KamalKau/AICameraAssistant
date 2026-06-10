package com.example.aicameraassistant

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun NightModeAssistLight(
    intensity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val alpha = intensity.coerceIn(0f, 1f)
        if (alpha <= 0f) return@Canvas

        val minDimension = minOf(size.width, size.height)
        if (minDimension <= 0f) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = (minDimension * 0.42f).coerceIn(160.dp.toPx(), 340.dp.toPx())
        val circleRadius = glowRadius * 0.68f

        drawRect(color = Color(0xFFFFE8B0).copy(alpha = 0.86f * alpha))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF0B3).copy(alpha = 0.78f * alpha),
                    Color(0xFFFFE28A).copy(alpha = 0.56f * alpha),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF0B3).copy(alpha = 0.94f * alpha),
                    Color(0xFFFFE28A).copy(alpha = 0.76f * alpha),
                    Color(0xFFFFD166).copy(alpha = 0.48f * alpha)
                ),
                center = center,
                radius = circleRadius
            ),
            radius = circleRadius,
            center = center
        )
        drawCircle(
            color = Color(0xFFFFE28A).copy(alpha = 0.58f * alpha),
            radius = circleRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}

@Composable
fun FaceDetectionFocusBox(
    bounds: NormalizedFaceBounds,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    FaceDetectionFocusBoxes(
        bounds = if (bounds.isValid()) listOf(bounds) else emptyList(),
        visible = visible,
        modifier = modifier
    )
}

@Composable
fun FaceDetectionFocusBoxes(
    bounds: List<NormalizedFaceBounds>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible && bounds.any { it.isValid() }) 1f else 0f,
        label = "face_detection_box_alpha"
    )
    if (alpha <= 0.01f) return

    Canvas(modifier = modifier) {
        val cornerRadius = CornerRadius(34.dp.toPx(), 34.dp.toPx())
        val minBoxWidth = 96.dp.toPx()
        val minBoxHeight = 128.dp.toPx()
        bounds.filter { it.isValid() }.forEach { box ->
            val desiredLeft = box.left.toFloat() * size.width
            val desiredTop = box.top.toFloat() * size.height
            val boxWidth = ((box.right - box.left).toFloat() * size.width)
                .coerceAtLeast(minBoxWidth)
                .coerceAtMost(size.width)
            val boxHeight = ((box.bottom - box.top).toFloat() * size.height)
                .coerceAtLeast(minBoxHeight)
                .coerceAtMost(size.height)
            val left = desiredLeft.coerceIn(0f, size.width - boxWidth)
            val top = desiredTop.coerceIn(0f, size.height - boxHeight)
            val rectSize = androidx.compose.ui.geometry.Size(boxWidth, boxHeight)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.035f * alpha),
                topLeft = Offset(left, top),
                size = rectSize,
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.58f * alpha),
                topLeft = Offset(left, top),
                size = rectSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SharedFocusReticleSamsung(
    point: Offset,
    success: Boolean?,
    showExposureHandle: Boolean,
    isLocked: Boolean,
    exposureProgress: Float,
    onToggleLock: () -> Unit,
    onExposureProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
    animationLabel: String = "focus_reticle_samsung_scale"
) {
    var fadeReticle by remember(point, isLocked) { mutableStateOf(false) }
    LaunchedEffect(success, isLocked) {
        fadeReticle = false
        if (success == true && !isLocked) {
            kotlinx.coroutines.delay(520L)
            fadeReticle = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (success == null) 1.2f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = animationLabel
    )
    val reticleAlpha by animateFloatAsState(
        targetValue = if (fadeReticle && !isLocked) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "focus_reticle_samsung_alpha"
    )
    val density = LocalDensity.current
    val ringColor = Color(0xFFFFD54F)

    Box(modifier = modifier) {
        val reticleSizePx = with(density) { 64.dp.toPx() * scale }
        val reticleHalfPx = reticleSizePx / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.16f * reticleAlpha),
                radius = reticleHalfPx,
                center = point,
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = reticleAlpha),
                radius = reticleHalfPx,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (point.x - with(density) { 32.dp.toPx() }).roundToInt(),
                        y = (point.y - with(density) { 32.dp.toPx() }).roundToInt()
                    )
                }
                .size(64.dp)
                .pointerInput(isLocked) {
                    detectTapGestures(onTap = { onToggleLock() })
                }
        ) {}

        if (isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (point.x - with(density) { 8.dp.toPx() }).roundToInt(),
                            y = (point.y - with(density) { 8.dp.toPx() }).roundToInt()
                        )
                    }
                    .size(16.dp)
            )
        }

        if (showExposureHandle) {
            SharedFocusExposureHandleSamsung(
                center = point,
                ringRadiusPx = reticleHalfPx,
                isLocked = isLocked,
                progress = exposureProgress,
                onProgressChange = onExposureProgressChange
            )
        }
    }
}

@Composable
fun SharedFocusExposureHandleSamsung(
    center: Offset,
    ringRadiusPx: Float,
    isLocked: Boolean,
    progress: Float,
    onProgressChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val controlWidth = 30.dp
    val controlHeight = 108.dp
    val trackHeight = 76.dp
    var dragProgress by remember(progress) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)

    LaunchedEffect(progress) {
        if (!isDragging) {
            dragProgress = progress.coerceIn(0f, 1f)
        }
    }

    val controlWidthPx = with(density) { controlWidth.toPx() }
    val controlHeightPx = with(density) { controlHeight.toPx() }
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val trackTopY = (controlHeightPx - trackHeightPx) / 2f
    val animatedProgress by animateFloatAsState(
        targetValue = dragProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "focus_exposure_slider_progress"
    )
    val displayedProgress = if (isDragging) {
        dragProgress.coerceIn(0f, 1f)
    } else {
        animatedProgress
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { parentSize = it }
    ) {
        val horizontalGapPx = with(density) { 14.dp.toPx() }
        val edgePaddingPx = with(density) { 12.dp.toPx() }
        val rightX = center.x + ringRadiusPx + horizontalGapPx
        val leftX = center.x - ringRadiusPx - horizontalGapPx - controlWidthPx
        val parentWidth = parentSize.width.toFloat()
        val parentHeight = parentSize.height.toFloat()
        val desiredX = if (parentWidth <= 0f || rightX + controlWidthPx <= parentWidth - edgePaddingPx) {
            rightX
        } else {
            leftX
        }
        val desiredY = center.y - (controlHeightPx / 2f)
        val maxX = (parentWidth - controlWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
        val maxY = (parentHeight - controlHeightPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = desiredX.coerceIn(edgePaddingPx, maxX).roundToInt(),
                        y = desiredY.coerceIn(edgePaddingPx, maxY).roundToInt()
                    )
                }
                .size(width = controlWidth, height = controlHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            val progressChange = currentOnProgressChange ?: return@detectDragGestures
                            change.consume()
                            val nextProgress =
                                (dragProgress + (dragAmount.y / (trackHeightPx * 6.5f)))
                                    .coerceIn(0f, 1f)
                            if (kotlin.math.abs(nextProgress - dragProgress) < 0.0025f) {
                                return@detectDragGestures
                            }
                            dragProgress = nextProgress
                            progressChange(nextProgress)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val trackTop = trackTopY
                val trackBottom = trackTop + trackHeightPx
                val iconCenterY =
                    (trackTop + (trackHeightPx * displayedProgress))
                        .coerceIn(trackTop, trackBottom)
                val accentColor = if (isLocked) Color(0xFFFFC400) else Color(0xFFFFD54F)
                val pillRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.28f),
                    size = size,
                    cornerRadius = pillRadius
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.38f),
                    start = Offset(centerX, trackTop),
                    end = Offset(centerX, trackBottom),
                    strokeWidth = 1.6.dp.toPx()
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.86f),
                    start = Offset(centerX, iconCenterY),
                    end = Offset(centerX, trackBottom),
                    strokeWidth = 2.2.dp.toPx()
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.42f),
                    radius = 10.dp.toPx(),
                    center = Offset(centerX, iconCenterY)
                )
                drawCircle(
                    color = accentColor,
                    radius = 6.5.dp.toPx(),
                    center = Offset(centerX, iconCenterY)
                )

                val rayStart = 9.dp.toPx()
                val rayLength = 3.dp.toPx()
                val rayStroke = 0.9.dp.toPx()
                repeat(8) { index ->
                    val angle = (index * 45f) * (Math.PI.toFloat() / 180f)
                    val dx = kotlin.math.cos(angle)
                    val dy = kotlin.math.sin(angle)
                    drawLine(
                        color = accentColor,
                        start = Offset(centerX + (dx * rayStart), iconCenterY + (dy * rayStart)),
                        end = Offset(
                            centerX + (dx * (rayStart + rayLength)),
                            iconCenterY + (dy * (rayStart + rayLength))
                        ),
                        strokeWidth = rayStroke
                    )
                }
            }
        }
    }
}

fun fittedPreviewRect(
    containerWidth: Float,
    containerHeight: Float,
    contentWidth: Float,
    contentHeight: Float
): Rect? {
    if (containerWidth <= 0f || containerHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) {
        return null
    }

    val scale = minOf(containerWidth / contentWidth, containerHeight / contentHeight)
    val fittedWidth = contentWidth * scale
    val fittedHeight = contentHeight * scale
    val left = (containerWidth - fittedWidth) / 2f
    val top = (containerHeight - fittedHeight) / 2f
    return Rect(left, top, left + fittedWidth, top + fittedHeight)
}

fun Offset.clampOffsetTo(rect: Rect): Offset =
    Offset(
        x = x.coerceIn(rect.left, rect.right),
        y = y.coerceIn(rect.top, rect.bottom)
    )

fun focusUiBounds(
    previewRect: Rect,
    topInsetPx: Float,
    rightInsetPx: Float,
    edgePaddingPx: Float,
    reticleRadiusPx: Float
): Rect {
    val left = previewRect.left + edgePaddingPx + reticleRadiusPx
    val top = previewRect.top + topInsetPx + reticleRadiusPx
    val right = previewRect.right - rightInsetPx - edgePaddingPx
    val bottom = previewRect.bottom - edgePaddingPx - reticleRadiusPx

    return if (right > left && bottom > top) {
        Rect(left, top, right, bottom)
    } else {
        previewRect
    }
}

fun exposureSliderOffset(
    point: Offset,
    previewRect: Rect,
    safeBounds: Rect,
    sliderWidthPx: Float,
    sliderHeightPx: Float,
    reticleHalfPx: Float,
    horizontalGapPx: Float
): IntOffset {
    val rightAnchorX = point.x + reticleHalfPx + horizontalGapPx
    val leftAnchorX = point.x - reticleHalfPx - horizontalGapPx - sliderWidthPx
    val minX = safeBounds.left
    val maxX = safeBounds.right - sliderWidthPx
    val rightFits = rightAnchorX in minX..maxX
    val leftFits = leftAnchorX in minX..maxX
    val desiredX = when {
        point.x < previewRect.center.x && rightFits -> rightAnchorX
        point.x >= previewRect.center.x && leftFits -> leftAnchorX
        point.x < previewRect.center.x && leftFits -> leftAnchorX
        point.x >= previewRect.center.x && rightFits -> rightAnchorX
        else -> {
            val clampedRightX = rightAnchorX.coerceIn(minX, maxX)
            val clampedLeftX = leftAnchorX.coerceIn(minX, maxX)
            val rightDelta = kotlin.math.abs(clampedRightX - rightAnchorX)
            val leftDelta = kotlin.math.abs(clampedLeftX - leftAnchorX)
            if (rightDelta <= leftDelta) clampedRightX else clampedLeftX
        }
    }

    val minY = safeBounds.top
    val maxY = safeBounds.bottom - sliderHeightPx
    val centeredY = point.y - (sliderHeightPx / 2f)
    val attachedTopY = point.y - reticleHalfPx
    val attachedBottomY = point.y + reticleHalfPx - sliderHeightPx
    val desiredY = when {
        centeredY < minY -> attachedTopY.coerceAtLeast(minY)
        centeredY > maxY -> attachedBottomY.coerceAtMost(maxY)
        else -> centeredY
    }

    return IntOffset(
        x = desiredX.coerceIn(minX, maxX).roundToInt(),
        y = desiredY.coerceIn(minY, maxY).roundToInt()
    )
}
