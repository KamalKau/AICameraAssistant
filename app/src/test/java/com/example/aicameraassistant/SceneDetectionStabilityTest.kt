package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDetectionStabilityTest {
    @Test
    fun categories_coverInitialConfigurableSceneSet() {
        assertTrue(
            configurableSceneCategories.containsAll(
                setOf(
                    "person", "group", "landscape", "food", "pet", "document", "sunset",
                    "night", "indoor", "outdoor", "macro", "backlit", "snow_beach", "unknown"
                )
            )
        )
    }

    @Test
    fun tracker_requiresConfirmationAndDoesNotSwitchOnOneFrame() {
        val tracker = StableSceneTracker(confirmationMs = 600L)
        assertNull(tracker.update(sceneDetectionResult("landscape", 0.8, 1_000L), 1_000L))
        assertNull(tracker.update(sceneDetectionResult("landscape", 0.82, 1_400L), 1_400L))
        assertEquals(
            "landscape",
            tracker.update(sceneDetectionResult("landscape", 0.84, 1_650L), 1_650L)?.key
        )

        assertEquals(
            "landscape",
            tracker.update(sceneDetectionResult("food", 0.9, 1_800L), 1_800L)?.key
        )
    }

    @Test
    fun tracker_delaysUnknownLongerThanRecognizedScenes() {
        val tracker = StableSceneTracker(confirmationMs = 500L, unknownDelayMs = 1_500L)
        tracker.update(sceneDetectionResult("night", 0.8, 1_000L), 1_000L)
        tracker.update(sceneDetectionResult("night", 0.8, 1_600L), 1_600L)

        assertEquals(
            "night",
            tracker.update(sceneDetectionResult("unknown", 0.1, 1_700L), 1_700L)?.key
        )
        assertEquals(
            "night",
            tracker.update(sceneDetectionResult("unknown", 0.1, 2_700L), 2_700L)?.key
        )
        assertEquals(
            "unknown",
            tracker.update(sceneDetectionResult("unknown", 0.1, 3_250L), 3_250L)?.key
        )
    }
}
