package com.example.aicameraassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingEndSessionButton(
            isEnding = state.isEndingSession,
            onClick = actions.onEndSession
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        SessionStatusChip(
            text = state.status.text,
            dotColor = state.status.dotColor
        )
    }

    if (!state.sessionIsActive) {
        RoomCodeBadge(roomCode = state.roomCode)
    }

    if (state.showApprovalPrompt) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = actions.onAllowController,
                modifier = Modifier.weight(1f)
            ) {
                Text("Allow")
            }

            Button(
                onClick = actions.onDenyController,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Deny")
            }
        }
    }
}
