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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
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
    if (state.showZoomRing) {
        AndroidZoomBar(
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
    } else {
        ZoomPresetSelector(
            options = state.commonZoomOptions,
            currentValue = state.zoomUiValue.coerceIn(state.minZoom, state.maxZoom),
            onOptionClick = actions.onZoomPresetClick,
            onLongPress = actions.onZoomPresetLongPress
        )
    }

    if (state.isBurstCapturing) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.44f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = state.burstCaptureCount.toString(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PortraitFeatureButton(
            enabled = state.portraitControlsEnabled,
            selected = state.portraitControlsVisible,
            onClick = actions.onPortraitControlsClick
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = state.shutterScale
                    scaleY = state.shutterScale
                }
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(state.roomCode) {
                    detectTapGestures(onPress = actions.onShutterPress)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = state.shutterCoreScale
                        scaleY = state.shutterCoreScale
                    }
                    .clip(if (state.isBurstCapturing) RoundedCornerShape(18.dp) else CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(modifier = Modifier.width(36.dp))
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
