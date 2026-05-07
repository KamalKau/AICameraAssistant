package com.example.aicameraassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = 0.24f)
        val strokeWidth = 0.9.dp.toPx()
        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f

        repeat(2) { index ->
            val verticalX = thirdWidth * (index + 1)
            drawLine(
                color = lineColor,
                start = Offset(verticalX, 0f),
                end = Offset(verticalX, size.height),
                strokeWidth = strokeWidth
            )

            val horizontalY = thirdHeight * (index + 1)
            drawLine(
                color = lineColor,
                start = Offset(0f, horizontalY),
                end = Offset(size.width, horizontalY),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
fun HostTopOverlay(
    state: HostTopOverlayUiState,
    actions: HostTopOverlayActions
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SessionStatusChip(
            text = state.status.text,
            dotColor = state.status.dotColor,
            modifier = Modifier.align(Alignment.Center)
        )
        FloatingEndSessionButton(
            isEnding = state.isEndingSession,
            onClick = actions.onEndSession,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

    if (!state.sessionIsActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            RoomCodeBadge(roomCode = state.roomCode)
        }
    }

    if (state.showApprovalPrompt) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.42f),
            border = androidx.compose.foundation.BorderStroke(
                0.8.dp,
                Color.White.copy(alpha = 0.14f)
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApprovalActionButton(
                    text = "Allow",
                    icon = Icons.Default.Check,
                    colors = listOf(Color(0xFF1FA66A), Color(0xFF38D991)),
                    onClick = actions.onAllowController,
                    modifier = Modifier.weight(1f)
                )
                ApprovalActionButton(
                    text = "Deny",
                    icon = Icons.Default.Close,
                    colors = listOf(Color(0xFFB3262E), Color(0xFFFF5A66)),
                    onClick = actions.onDenyController,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ApprovalActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(colors))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
