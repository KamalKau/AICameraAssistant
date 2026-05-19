package com.example.aicameraassistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ControllerTopActionBar(
    isEndingSession: Boolean,
    onEndSession: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingEndSessionButton(
            isEnding = isEndingSession,
            onClick = onEndSession,
            containerColor = Color(0xFFE53935)
        )
    }
}

@Composable
fun ControllerStatusOverlay(state: StatusUiState) {
    SessionStatusChip(
        text = state.text,
        dotColor = state.dotColor
    )

    if (state.warningText != null) {
        Spacer(modifier = Modifier.padding(top = 8.dp))
        SessionWarningChip(
            text = state.warningText,
            detailText = state.warningDetailText
        )
    }
}

@Composable
fun ControllerBottomControls(
    state: ControllerBottomControlsUiState,
    actions: ControllerBottomControlsActions
) {
    val straightZoomBarActive = state.showZoomRing

    if (state.showZoomRing) {
        StraightZoomBar(
            value = state.zoomUiValue.coerceIn(state.minZoom, state.maxZoom),
            minZoom = state.minZoom,
            maxZoom = state.maxZoom,
            onValueChange = actions.onZoomBarValueChange,
            onValueChangeFinished = actions.onZoomBarFinished
        )
    }

    if (state.portraitControlsVisible) {
        CompactPortraitControls(
            strength = state.portraitStrength,
            selectedEffect = state.portraitEffect,
            onStrengthSelected = actions.onPortraitStrengthSelected,
            onEffectSelected = actions.onPortraitEffectSelected
        )
    } else if (!straightZoomBarActive) {
        ZoomPresetSelector(
            options = state.commonZoomOptions,
            currentValue = state.zoomUiValue.coerceIn(state.minZoom, state.maxZoom),
            onOptionClick = actions.onZoomPresetClick,
            onLongPress = actions.onZoomPresetLongPress
        )
    }

    if (state.isVideoRecording) {
        RecordingStatusPill(isPaused = state.isVideoPaused)
    } else if (state.isBurstCapturing) {
        BurstStatusPill(count = state.burstCaptureCount)
    }

    if (state.isVideoRecording) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoPauseResumeButton(
                paused = state.isVideoPaused,
                onClick = actions.onVideoPauseToggle
            )

            VideoStopButton(onClick = actions.onVideoStop)

            Spacer(modifier = Modifier.width(32.dp))
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (straightZoomBarActive) 24.dp else 34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortraitFeatureButton(
                enabled = state.portraitControlsEnabled,
                selected = state.portraitControlsVisible,
                onClick = actions.onPortraitControlsClick
            )

            ControllerShutterButton(
                state = state,
                compact = straightZoomBarActive,
                onShutterPress = actions.onShutterPress
            )

            LensFlipButton(
                label = state.lensLabel,
                onClick = actions.onLensClick
            )
        }
    }
}

@Composable
private fun StraightZoomBar(
    value: Float,
    minZoom: Float,
    maxZoom: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val clampedValue = value.coerceIn(minZoom, maxZoom)
    val normalizedValue =
        ((clampedValue - minZoom) / (maxZoom - minZoom).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)

    fun updateFromX(x: Float, widthPx: Float) {
        val horizontalPadding = 22f
        val trackWidth = (widthPx - (horizontalPadding * 2f)).coerceAtLeast(1f)
        val normalized = ((x - horizontalPadding) / trackWidth).coerceIn(0f, 1f)
        onValueChange(minZoom + ((maxZoom - minZoom) * normalized))
    }

    Box(
        modifier = modifier
            .width(272.dp)
            .height(64.dp)
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .pointerInput(minZoom, maxZoom) {
                detectDragGestures(
                    onDragStart = { point -> updateFromX(point.x, size.width.toFloat()) },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x, size.width.toFloat())
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            val trackStart = 6.dp.toPx()
            val trackEnd = size.width - 6.dp.toPx()
            val centerY = size.height / 2f
            val trackWidth = trackEnd - trackStart
            val thumbX = trackStart + (trackWidth * normalizedValue)

            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = Offset(trackStart, centerY),
                end = Offset(trackEnd, centerY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.95f),
                start = Offset(trackStart, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            repeat(9) { index ->
                val tickX = trackStart + (trackWidth * (index / 8f))
                val tickHeight = if (index == 0 || index == 8 || index == 4) 10.dp.toPx() else 6.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.38f),
                    start = Offset(tickX, centerY - 16.dp.toPx()),
                    end = Offset(tickX, centerY - 16.dp.toPx() - tickHeight),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = 3.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }

        Text(
            text = "${formatZoomLabel(clampedValue)}x",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        Text(
            text = "${formatZoomLabel(minZoom)}x",
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        Text(
            text = "${formatZoomLabel(maxZoom)}x",
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun RecordingStatusPill(isPaused: Boolean) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF2D2D))
        )
        Text(
            text = if (isPaused) "PAUSED" else "REC",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun VideoPauseResumeButton(
    paused: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
    ) {
        Icon(
            imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = if (paused) "Resume video" else "Pause video",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun VideoStopButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, Color.White.copy(alpha = 0.82f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop video",
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun BurstStatusPill(count: Int) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.44f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ControllerShutterButton(
    state: ControllerBottomControlsUiState,
    compact: Boolean = false,
    onShutterPress: suspend androidx.compose.foundation.gestures.PressGestureScope.(Offset) -> Unit
) {
    val recordingRed = Color(0xFFD32F2F)
    val outerColor = when {
        state.isVideoRecording -> Color.White
        state.isVideoMode -> recordingRed
        else -> Color.White
    }
    val coreColor = when {
        state.isVideoRecording -> recordingRed
        state.isVideoMode -> recordingRed
        else -> Color.White
    }
    val coreShape = when {
        state.isVideoRecording -> RoundedCornerShape(8.dp)
        state.isBurstCapturing -> RoundedCornerShape(18.dp)
        else -> CircleShape
    }
    val buttonSize = if (compact && !state.isVideoRecording) 64.dp else 80.dp
    val borderWidth = if (compact && !state.isVideoRecording) 3.dp else 4.dp
    val buttonPadding = if (compact && !state.isVideoRecording) 5.dp else 6.dp
    val coreSize = when {
        state.isVideoRecording -> 30.dp
        compact -> 50.dp
        else -> 64.dp
    }

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = state.shutterScale
                scaleY = state.shutterScale
            }
            .border(borderWidth, Color.White, CircleShape)
            .padding(buttonPadding)
            .clip(CircleShape)
            .background(outerColor)
            .pointerInput(state.roomCode, state.isVideoMode, state.isVideoRecording) {
                detectTapGestures(onPress = onShutterPress)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .graphicsLayer {
                    scaleX = state.shutterCoreScale
                    scaleY = state.shutterCoreScale
                }
                .clip(coreShape)
                .background(coreColor)
        )
    }
}

@Composable
private fun LensFlipButton(
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.SwitchCamera,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CompactPortraitControls(
    strength: Int,
    selectedEffect: String,
    onStrengthSelected: (Int) -> Unit,
    onEffectSelected: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PortraitStrengthBar(
            strength = strength,
            onStrengthChange = onStrengthSelected
        )

        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .width(202.dp)
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PortraitEffect.selectable.forEach { effect ->
                    CompactPortraitEffectChip(
                        value = effect.key,
                        selectedValue = selectedEffect,
                        onSelected = onEffectSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitStrengthBar(
    strength: Int,
    onStrengthChange: (Int) -> Unit
) {
    val clampedStrength = strength.coerceIn(1, 7)
    val progress = (clampedStrength - 1).toFloat() / 6f

    fun strengthFromX(x: Float, width: Int): Int {
        if (width <= 0) return clampedStrength
        val progressFromLeft = (x / width.toFloat()).coerceIn(0f, 1f)
        return (1 + (progressFromLeft * 6f)).roundToInt().coerceIn(1, 7)
    }

    Row(
        modifier = Modifier
            .background(Color(0xFF2E2E2E).copy(alpha = 0.95f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "S",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = clampedStrength.toString(),
            color = Color(0xFFFFD54F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .width(134.dp)
                .height(24.dp)
                .pointerInput(clampedStrength) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val nextStrength = strengthFromX(offset.x, size.width)
                            if (nextStrength != clampedStrength) onStrengthChange(nextStrength)
                        },
                        onDrag = { change, _ ->
                            val nextStrength = strengthFromX(change.position.x, size.width)
                            if (nextStrength != clampedStrength) onStrengthChange(nextStrength)
                            change.consume()
                        }
                    )
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val startX = 5.dp.toPx()
                val endX = size.width - 5.dp.toPx()
                val trackWidth = endX - startX
                val indicatorX = startX + (trackWidth * progress.coerceIn(0f, 1f))

                repeat(11) { index ->
                    val tickX = startX + (trackWidth * (index / 10f))
                    val distanceFromCenter = kotlin.math.abs(index - 5)
                    val tickHeight = when (distanceFromCenter) {
                        0 -> 14.dp.toPx()
                        1, 2 -> 9.dp.toPx()
                        else -> 6.dp.toPx()
                    }
                    drawLine(
                        color = if (index == 5) {
                            Color.White.copy(alpha = 0.64f)
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        },
                        start = Offset(tickX, centerY - (tickHeight / 2f)),
                        end = Offset(tickX, centerY + (tickHeight / 2f)),
                        strokeWidth = if (index == 5) 1.25.dp.toPx() else 0.95.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                drawLine(
                    color = Color(0xFFFFD54F),
                    start = Offset(indicatorX, centerY - 11.dp.toPx()),
                    end = Offset(indicatorX, centerY + 11.dp.toPx()),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = 2.4.dp.toPx(),
                    center = Offset(indicatorX, centerY)
                )
            }
        }
    }
}

@Composable
private fun CompactPortraitEffectChip(
    value: String,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    val selected = value == selectedValue
    Box(
        modifier = Modifier
            .size(if (selected) 38.dp else 34.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFFFFD54F).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
            .border(
                if (selected) 1.4.dp else 1.dp,
                if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.10f),
                CircleShape
            )
            .clickable(enabled = !selected) { onSelected(value) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = portraitEffectSymbol(value),
            color = if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.82f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PortraitFeatureButton(
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Black.copy(alpha = 0.22f)
                    selected -> Color(0xFFFFD54F).copy(alpha = 0.22f)
                    else -> Color.Black.copy(alpha = 0.42f)
                }
            )
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.10f)
                    selected -> Color(0xFFFFD54F)
                    else -> Color.White.copy(alpha = 0.18f)
                },
                shape = CircleShape
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "P",
            color = when {
                !enabled -> Color.White.copy(alpha = 0.28f)
                selected -> Color(0xFFFFD54F)
                else -> Color.White.copy(alpha = 0.84f)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomPresetSelector(
    options: List<Float>,
    currentValue: Float,
    modifier: Modifier = Modifier,
    onOptionClick: (Float) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = abs(currentValue - option) < 0.16f
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { onOptionClick(option) },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${formatZoomLabel(option)}x",
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AndroidZoomBar(
    value: Float,
    minZoom: Float,
    maxZoom: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val clampedValue = value.coerceIn(minZoom, maxZoom)
    val normalizedValue =
        ((clampedValue - minZoom) / (maxZoom - minZoom).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    val startAngle = 135f
    val sweepAngle = 270f

    fun updateFromPoint(point: Offset, sizePx: Float) {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val rawAngle = ((Math.toDegrees(
            atan2(
                (point.y - center.y).toDouble(),
                (point.x - center.x).toDouble()
            )
        ) + 360.0) % 360.0).toFloat()
        val extendedAngle = if (rawAngle < startAngle) rawAngle + 360f else rawAngle
        val normalized =
            ((extendedAngle.coerceIn(startAngle, startAngle + sweepAngle) - startAngle) /
                sweepAngle)
                .coerceIn(0f, 1f)
        onValueChange(minZoom + ((maxZoom - minZoom) * normalized))
    }

    Box(
        modifier = modifier
            .size(196.dp)
            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            .border(0.8.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .pointerInput(minZoom, maxZoom) {
                detectDragGestures(
                    onDragStart = { point -> updateFromPoint(point, size.width.toFloat()) },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromPoint(change.position, size.width.toFloat())
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val ringInset = 24.dp.toPx()
            val ringStroke = 8.dp.toPx()
            val ringSize = androidx.compose.ui.geometry.Size(
                width = size.width - (ringInset * 2f),
                height = size.height - (ringInset * 2f)
            )
            val ringTopLeft = Offset(ringInset, ringInset)
            val ringRadius = ringSize.width / 2f

            drawArc(
                color = Color.White.copy(alpha = 0.13f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.96f),
                startAngle = startAngle,
                sweepAngle = sweepAngle * normalizedValue,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            val centerPoint = Offset(size.width / 2f, size.height / 2f)
            repeat(19) { index ->
                val tickNormalized = index / 18f
                val angleRadians =
                    Math.toRadians((startAngle + (sweepAngle * tickNormalized)).toDouble())
                val outerRadius = ringRadius + (ringStroke / 2f) + 8.dp.toPx()
                val innerRadius = outerRadius - 6.dp.toPx()
                val outer = Offset(
                    x = centerPoint.x + (cos(angleRadians).toFloat() * outerRadius),
                    y = centerPoint.y + (sin(angleRadians).toFloat() * outerRadius)
                )
                val inner = Offset(
                    x = centerPoint.x + (cos(angleRadians).toFloat() * innerRadius),
                    y = centerPoint.y + (sin(angleRadians).toFloat() * innerRadius)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = inner,
                    end = outer,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val thumbAngleRadians =
                Math.toRadians((startAngle + (sweepAngle * normalizedValue)).toDouble())
            val thumbCenter = Offset(
                x = centerPoint.x + (cos(thumbAngleRadians).toFloat() * ringRadius),
                y = centerPoint.y + (sin(thumbAngleRadians).toFloat() * ringRadius)
            )
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = thumbCenter)
            drawCircle(
                color = Color.Black.copy(alpha = 0.46f),
                radius = 2.2.dp.toPx(),
                center = thumbCenter
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${formatZoomLabel(clampedValue)}x",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Zoom",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatZoomLabel(minZoom)}x",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${formatZoomLabel(maxZoom)}x",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun formatZoomLabel(value: Float): String =
    if (value % 1f == 0f) {
        value.roundToInt().toString()
    } else {
        String.format("%.1f", value)
    }
