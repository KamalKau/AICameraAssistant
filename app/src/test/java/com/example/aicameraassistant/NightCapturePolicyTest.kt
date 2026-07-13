package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCapturePolicyTest {
    @Test
    fun normalLightingDoesNotTriggerLowLightProcessing() {
        val sample = analyzePreviewLuma(IntArray(400) { 132 })

        assertFalse(sample!!.isLowLight)
    }

    @Test
    fun darkShadowsWithStreetlightsTriggerLowLightProcessing() {
        val values = IntArray(400) { index ->
            when {
                index < 230 -> 24
                index < 370 -> 82
                else -> 238
            }
        }

        assertTrue(analyzePreviewLuma(values)!!.isLowLight)
    }

    @Test
    fun globalBrightnessChangeIsNotMistakenForMotion() {
        val first = analyzePreviewLuma(IntArray(400) { 42 })
        val second = analyzePreviewLuma(IntArray(400) { 58 })

        assertEquals(0.0, previewMotionScore(first, second), 0.001)
    }

    @Test
    fun localSceneChangeIsDetectedAsMotion() {
        val first = analyzePreviewLuma(IntArray(400) { 48 })
        val second = analyzePreviewLuma(IntArray(400) { index -> if (index % 2 == 0) 20 else 90 })

        assertTrue(previewMotionScore(first, second) >= 9.0)
    }

    @Test
    fun exposureBoostIsModerateAndMotionUsesLessBoost() {
        val policy = NightModeExposurePolicy()

        assertEquals(2, policy.resolveTargetIndex(true, 0, -4, 6))
        assertEquals(3, policy.resolveCaptureIndex(0, -4, 6, motionDetected = false))
        assertEquals(1, policy.resolveCaptureIndex(0, -4, 6, motionDetected = true))
    }
}
