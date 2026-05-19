package com.example.aicameraassistant

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt

@Composable
fun SessionStatusChip(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "status_pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_pulse"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.38f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = pulse))
            )

            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SessionWarningChip(
    text: String,
    detailText: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            detailText?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun RoomCodeBadge(
    roomCode: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.36f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "ROOM",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = roomCode,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CompactRoomCodeChip(
    roomCode: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.36f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "ROOM",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = roomCode,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FloatingEndSessionButton(
    isEnding: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.background(containerColor, CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.CallEnd,
            contentDescription = if (isEnding) "Ending session" else "End session",
            tint = Color.White
        )
    }
}

@Composable
fun CameraToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(if (showLabel) 104.dp else 34.dp)
    ) {
        if (showLabel) {
            Text(
                text = label,
                color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(66.dp)
            )
        }

        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (selected) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.3f),
                    CircleShape
                )
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) Color.White.copy(alpha = 0.42f) else Color.Transparent,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun CameraToolRail(
    state: CameraToolRailUiState,
    actions: CameraToolRailActions,
    modifier: Modifier = Modifier
) {
    val labelsExpanded = state.toolbarExpanded

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(if (labelsExpanded) 9.dp else 7.dp)
    ) {
        CameraToolButton(
            icon = state.flashIcon,
            label = state.flashLabel,
            enabled = state.flashEnabled,
            showLabel = labelsExpanded,
            onClick = actions.onFlashClick
        )
        CameraToolButton(
            icon = Icons.Default.AllInclusive,
            label = if (state.boomerangSelected) "Boomerang On" else "Boomerang",
            showLabel = labelsExpanded,
            selected = state.boomerangSelected,
            onClick = actions.onBoomerangClick
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CameraToolButton(
                icon = Icons.Default.WbSunny,
                label = "Exposure",
                enabled = state.exposureSupported,
                showLabel = labelsExpanded,
                onClick = actions.onExposureClick
            )
        }
        if (labelsExpanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grid",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(66.dp)
                )
                GridToggleButton(
                    isActive = state.gridEnabled,
                    onClick = actions.onGridClick
                )
            }
            CameraToolButton(
                icon = Icons.Default.DarkMode,
                label = if (state.nightModeEnabled) "Night On" else "Night",
                showLabel = true,
                selected = state.nightModeEnabled,
                onClick = actions.onNightModeClick
            )
        }
        CameraToolButton(
            icon = if (labelsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            label = if (labelsExpanded) "Hide" else "Show",
            showLabel = labelsExpanded,
            onClick = { actions.onToolbarExpandedChange(!labelsExpanded) }
        )
    }
}

@Composable
fun ManualExposurePanel(
    progress: Float,
    exposureLabel: String,
    onProgressChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    var dragProgress by remember { mutableFloatStateOf(clampedProgress) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(clampedProgress) {
        if (!isDragging) {
            dragProgress = clampedProgress
        }
    }
    val settledProgress by animateFloatAsState(
        targetValue = dragProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "manual_exposure_progress"
    )
    val displayProgress = if (isDragging) dragProgress.coerceIn(0f, 1f) else settledProgress

    Row(
        modifier = modifier
            .background(Color(0xFF2E2E2E).copy(alpha = 0.95f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close exposure",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = exposureLabel.removePrefix("EV "),
            color = Color(0xFFFFD54F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .width(134.dp)
                .height(24.dp)
                .pointerInput(onProgressChange) {
                    awaitEachGesture {
                        val downEvent = awaitPointerEvent()
                        val down = downEvent.changes.firstOrNull { it.pressed }
                            ?: downEvent.changes.firstOrNull()
                            ?: return@awaitEachGesture
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        fun progressFor(x: Float): Float = (x / width).coerceIn(0f, 1f)

                        isDragging = true
                        var nextProgress = progressFor(down.position.x)
                        dragProgress = nextProgress
                        onProgressChange(nextProgress)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                            if (!change.pressed) {
                                isDragging = false
                                break
                            }

                            nextProgress = progressFor(change.position.x)
                            if (kotlin.math.abs(nextProgress - dragProgress) > 0.001f) {
                                dragProgress = nextProgress
                                onProgressChange(nextProgress)
                            }
                            change.consume()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerY = size.height / 2f
                val startX = 5.dp.toPx()
                val endX = size.width - 5.dp.toPx()
                val trackWidth = endX - startX
                val indicatorX = startX + (trackWidth * displayProgress)

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

        IconButton(
            onClick = onReset,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset exposure",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ManualExposureSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .width(32.dp)
            .height(156.dp)
            .pointerInput(onProgressChange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val nextProgress =
                            (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        onProgressChange(nextProgress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val nextProgress =
                            (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                        onProgressChange(nextProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = size.width / 2f
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val accentColor = Color(0xFFFFD54F)
            val iconCenterY = bottom - ((bottom - top) * clampedProgress)
            val gap = 6.dp.toPx()
            val trackStroke = 1.25.dp.toPx()

            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(centerX, top),
                end = Offset(centerX, iconCenterY - gap),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(centerX, iconCenterY + gap),
                end = Offset(centerX, bottom),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = accentColor,
                radius = 2.4.dp.toPx(),
                center = Offset(centerX, iconCenterY)
            )

            val rayStart = 4.8.dp.toPx()
            val rayLength = 2.9.dp.toPx()
            val rayStroke = 0.8.dp.toPx()
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

            drawLine(
                color = Color.White.copy(alpha = 0.58f),
                start = Offset(centerX - 4.dp.toPx(), top),
                end = Offset(centerX + 4.dp.toPx(), top),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.58f),
                start = Offset(centerX - 4.dp.toPx(), bottom),
                end = Offset(centerX + 4.dp.toPx(), bottom),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Text(
            text = "+",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
        )
        Text(
            text = "-",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp)
        )
    }
}

fun defaultExposureProgress(minIndex: Int, maxIndex: Int): Float {
    if (minIndex == maxIndex) return 0.5f
    val clampedZero = 0.coerceIn(minIndex, maxIndex)
    return ((maxIndex - clampedZero).toFloat() / (maxIndex - minIndex).toFloat())
        .coerceIn(0f, 1f)
}

fun buildExposureLabel(currentIndex: Int, minIndex: Int, maxIndex: Int): String {
    if (minIndex == maxIndex) return "EV 0.0"
    val centeredProgress = defaultExposureProgress(minIndex, maxIndex)
    val currentProgress =
        ((maxIndex - currentIndex).toFloat() / (maxIndex - minIndex).toFloat()).coerceIn(0f, 1f)
    val normalizedEv = if (currentProgress <= centeredProgress) {
        val brightenSpan = centeredProgress.coerceAtLeast(0.001f)
        ((centeredProgress - currentProgress) / brightenSpan) * 2f
    } else {
        val darkenSpan = (1f - centeredProgress).coerceAtLeast(0.001f)
        -((currentProgress - centeredProgress) / darkenSpan) * 2f
    }.coerceIn(-2f, 2f)
    return "EV ${if (normalizedEv >= 0f) "+" else ""}${"%.1f".format(normalizedEv)}"
}

data class PreviewExposureOverlayState(
    val color: Color,
    val alpha: Float,
    val brighten: Boolean
)

fun buildPreviewExposureOverlay(
    currentProgress: Float,
    neutralProgress: Float
): PreviewExposureOverlayState {
    val clampedCurrent = currentProgress.coerceIn(0f, 1f)
    val clampedNeutral = neutralProgress.coerceIn(0f, 1f)

    return if (clampedCurrent <= clampedNeutral) {
        PreviewExposureOverlayState(
            color = Color.Transparent,
            alpha = 0f,
            brighten = true
        )
    } else {
        val darkenSpan = (1f - clampedNeutral).coerceAtLeast(0.001f)
        val alpha = ((clampedCurrent - clampedNeutral) / darkenSpan).coerceIn(0f, 1f) * 0.26f
        PreviewExposureOverlayState(
            color = Color.Black,
            alpha = alpha,
            brighten = false
        )
    }
}

@Composable
fun PreviewExposureOverlay(
    state: PreviewExposureOverlayState,
    modifier: Modifier = Modifier
) {
    if (state.alpha <= 0f) return

    Canvas(modifier = modifier) {
        drawRect(color = state.color.copy(alpha = state.alpha.coerceIn(0f, 1f)))
    }
}
