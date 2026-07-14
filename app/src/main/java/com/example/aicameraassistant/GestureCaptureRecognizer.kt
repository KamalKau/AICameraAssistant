package com.example.aicameraassistant

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.hypot

class GestureCaptureRecognizer(
    private val requiredFrames: Int = 3,
    private val cooldownMs: Long = 3_000L
) {
    private var consecutivePalmFrames = 0
    private var lastTriggerMs = Long.MIN_VALUE

    fun observe(pose: Pose, nowMs: Long = System.currentTimeMillis()): Boolean {
        val palmVisible = isRaisedOpenPalm(pose, left = true) || isRaisedOpenPalm(pose, left = false)
        consecutivePalmFrames = if (palmVisible) consecutivePalmFrames + 1 else 0
        if (consecutivePalmFrames < requiredFrames || nowMs - lastTriggerMs < cooldownMs) return false
        consecutivePalmFrames = 0
        lastTriggerMs = nowMs
        return true
    }

    fun reset() {
        consecutivePalmFrames = 0
    }

    private fun isRaisedOpenPalm(pose: Pose, left: Boolean): Boolean {
        val wrist = pose.getPoseLandmark(if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST)
        val elbow = pose.getPoseLandmark(if (left) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW)
        val index = pose.getPoseLandmark(if (left) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX)
        val pinky = pose.getPoseLandmark(if (left) PoseLandmark.LEFT_PINKY else PoseLandmark.RIGHT_PINKY)
        val thumb = pose.getPoseLandmark(if (left) PoseLandmark.LEFT_THUMB else PoseLandmark.RIGHT_THUMB)
        val points = listOf(wrist, elbow, index, pinky, thumb)
        if (points.any { it == null || it.inFrameLikelihood < 0.65f }) return false

        val safeWrist = wrist ?: return false
        val safeElbow = elbow ?: return false
        val fingers = listOf(index ?: return false, pinky ?: return false, thumb ?: return false)
        val forearm = distance(
            safeWrist.position.x,
            safeWrist.position.y,
            safeElbow.position.x,
            safeElbow.position.y
        ).coerceAtLeast(1f)
        val raised = safeWrist.position.y < safeElbow.position.y - (forearm * 0.08f)
        val fingersExtended = fingers.all {
            distance(safeWrist.position.x, safeWrist.position.y, it.position.x, it.position.y) >
                forearm * 0.18f
        }
        val spread = distance(
            fingers[0].position.x,
            fingers[0].position.y,
            fingers[1].position.x,
            fingers[1].position.y
        ) > forearm * 0.16f
        return raised && fingersExtended && spread
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot(x2 - x1, y2 - y1)
}
