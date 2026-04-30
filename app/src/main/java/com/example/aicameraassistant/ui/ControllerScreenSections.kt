package com.example.aicameraassistant

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
    if (state.showZoomRing) {
        AndroidZoomBar(
            value = state.zoomUiValue.coerceIn(state.minZoom, state.maxZoom),
            minZoom = state.minZoom,
            maxZoom = state.maxZoom,
            onValueChange = actions.onZoomBarValueChange,
            onValueChangeFinished = actions.onZoomBarFinished
        )
    }

    ZoomPresetSelector(
        options = state.commonZoomOptions,
        currentValue = state.zoomUiValue.coerceIn(state.minZoom, state.maxZoom),
        onOptionClick = actions.onZoomPresetClick,
        onLongPress = actions.onZoomPresetLongPress
    )

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
