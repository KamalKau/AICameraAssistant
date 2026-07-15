package com.example.aicameraassistant

data class SmartFramingState(
    val guidance: String = "",
    val timestamp: Long = 0L,
    val sessionId: String = ""
)

class SmartFramingEvaluator {
    fun evaluate(faces: List<PortraitFaceBounds>): String {
        val primary = faces.firstOrNull { it.isPrimary } ?: faces.firstOrNull()
            ?: return "Find a subject"
        return when {
            primary.centerX < 0.38 -> "Move Right"
            primary.centerX > 0.62 -> "Move Left"
            primary.centerY < 0.34 -> "Move Down"
            primary.centerY > 0.66 -> "Move Up"
            primary.area < 0.035 -> "Move Closer"
            primary.area > 0.24 -> "Move Back"
            else -> "Perfect Framing"
        }
    }
}

fun SmartFramingState.toSceneDetectionState(): SceneDetectionState = SceneDetectionState(
    key = "face",
    label = "Smart Framing",
    suggestion = guidance.ifBlank { "Find a subject" },
    confidence = if (guidance == "Perfect Framing") 1.0 else 0.8,
    timestamp = timestamp
)
