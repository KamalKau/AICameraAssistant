package com.example.aicameraassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlCameraScreen(
    onBack: () -> Unit,
    onConnect: (String) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onBack
        ) {
            Text(
                text = "Back",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        Text(
            text = "Control Camera",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Enter the room code from the camera phone.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = roomCode,
            onValueChange = {
                roomCode = it.uppercase()
            },
            label = { Text("Room Code") },
            placeholder = { Text("E.g. K8P4A") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                if (roomCode.isNotBlank()) {
                    onConnect(roomCode)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = roomCode.isNotBlank()
        ) {
            Text("Connect")
        }
    }
}