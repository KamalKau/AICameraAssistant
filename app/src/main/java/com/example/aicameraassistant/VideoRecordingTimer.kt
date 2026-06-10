package com.example.aicameraassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun rememberVideoRecordingElapsedMillis(
    isRecording: Boolean,
    isPaused: Boolean
): Long {
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    val pausedState by rememberUpdatedState(isPaused)

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            elapsedMillis = 0L
            return@LaunchedEffect
        }

        var lastTickMillis = System.currentTimeMillis()
        while (true) {
            delay(250L)
            val nowMillis = System.currentTimeMillis()
            if (!pausedState) {
                elapsedMillis += nowMillis - lastTickMillis
            }
            lastTickMillis = nowMillis
        }
    }

    return elapsedMillis
}

@Composable
fun VideoRecordingTimerPill(
    elapsedMillis: Long,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isPaused) Color(0xFFFFD54F) else Color(0xFFFF3B30))
        )
        Text(
            text = if (isPaused) "PAUSED ${formatRecordingDuration(elapsedMillis)}" else formatRecordingDuration(elapsedMillis),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatRecordingDuration(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
