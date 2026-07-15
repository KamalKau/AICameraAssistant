package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFramingSupportTest {
    private val evaluator = SmartFramingEvaluator()

    @Test
    fun evaluator_usesStablePrimaryFace() {
        val secondary = PortraitFaceBounds(
            left = 0.05, top = 0.25, right = 0.25, bottom = 0.55,
            trackingId = 1L
        )
        val primary = PortraitFaceBounds(
            left = 0.40, top = 0.35, right = 0.60, bottom = 0.65,
            trackingId = 2L, isPrimary = true
        )

        assertEquals("Perfect Framing", evaluator.evaluate(listOf(secondary, primary)))
    }

    @Test
    fun evaluator_providesDirectionalAndDistanceGuidance() {
        assertEquals(
            "Move Right",
            evaluator.evaluate(listOf(PortraitFaceBounds(0.05, 0.35, 0.25, 0.65, isPrimary = true)))
        )
        assertEquals(
            "Move Closer",
            evaluator.evaluate(listOf(PortraitFaceBounds(0.45, 0.45, 0.55, 0.58, isPrimary = true)))
        )
        assertEquals("Find a subject", evaluator.evaluate(emptyList()))
    }
}
