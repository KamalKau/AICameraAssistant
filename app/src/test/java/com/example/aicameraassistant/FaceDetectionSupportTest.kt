package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDetectionSupportTest {
    private val mapper = FaceBoundsMapper()

    @Test
    fun mapAnalysisBoundsToPreview_keepsBoundsForBackCamera() {
        val mapped = mapper.mapAnalysisBoundsToPreview(
            NormalizedFaceBounds(left = 0.20, top = 0.30, right = 0.50, bottom = 0.70),
            isFrontCamera = false
        )

        assertEquals(0.20, mapped.left, 0.0001)
        assertEquals(0.30, mapped.top, 0.0001)
        assertEquals(0.50, mapped.right, 0.0001)
        assertEquals(0.70, mapped.bottom, 0.0001)
    }

    @Test
    fun mapAnalysisBoundsToPreview_mirrorsFrontCameraHorizontally() {
        val mapped = mapper.mapAnalysisBoundsToPreview(
            NormalizedFaceBounds(left = 0.20, top = 0.30, right = 0.50, bottom = 0.70),
            isFrontCamera = true
        )

        assertEquals(0.50, mapped.left, 0.0001)
        assertEquals(0.30, mapped.top, 0.0001)
        assertEquals(0.80, mapped.right, 0.0001)
        assertEquals(0.70, mapped.bottom, 0.0001)
    }

    @Test
    fun faceMovementThreshold_detectsMeaningfulMotion() {
        val previous = PortraitFaceBounds(left = 0.20, top = 0.20, right = 0.40, bottom = 0.45)
        val movedSlightly = PortraitFaceBounds(left = 0.22, top = 0.21, right = 0.42, bottom = 0.46)
        val movedFar = PortraitFaceBounds(left = 0.30, top = 0.28, right = 0.52, bottom = 0.56)

        assertFalse(movedSlightly.hasMovedSignificantlyFrom(previous))
        assertTrue(movedFar.hasMovedSignificantlyFrom(previous))
    }

    @Test
    fun faceStabilityThreshold_acceptsNearbyBounds() {
        val previous = PortraitFaceBounds(left = 0.20, top = 0.20, right = 0.40, bottom = 0.45)
        val nearby = PortraitFaceBounds(left = 0.23, top = 0.22, right = 0.43, bottom = 0.47)
        val far = PortraitFaceBounds(left = 0.35, top = 0.30, right = 0.58, bottom = 0.60)

        assertTrue(nearby.isStableCandidateAfter(previous))
        assertFalse(far.isStableCandidateAfter(previous))
    }

    @Test
    fun listHelpers_usePrimaryFaceAndDetectSizeChanges() {
        val previous = listOf(
            PortraitFaceBounds(left = 0.10, top = 0.10, right = 0.30, bottom = 0.35),
            PortraitFaceBounds(left = 0.60, top = 0.10, right = 0.80, bottom = 0.35)
        )
        val similar = listOf(
            PortraitFaceBounds(left = 0.11, top = 0.11, right = 0.31, bottom = 0.36),
            PortraitFaceBounds(left = 0.61, top = 0.11, right = 0.81, bottom = 0.36)
        )
        val resized = listOf(
            PortraitFaceBounds(left = 0.07, top = 0.07, right = 0.42, bottom = 0.49),
            PortraitFaceBounds(left = 0.60, top = 0.10, right = 0.80, bottom = 0.35)
        )

        assertTrue(similar.isStableCandidateAfter(previous))
        assertFalse(resized.isStableCandidateAfter(previous))
        assertFalse(similar.haveMovedSignificantlyFrom(previous))
        assertTrue(resized.haveMovedSignificantlyFrom(previous))
    }

    @Test
    fun stableFaceTracker_smoothsSmallMovementAndKeepsLargestFaceFirst() {
        val tracker = StableFaceTracker()
        val first = tracker.update(
            detections = listOf(
                PortraitFaceBounds(left = 0.10, top = 0.10, right = 0.25, bottom = 0.32),
                PortraitFaceBounds(left = 0.45, top = 0.18, right = 0.78, bottom = 0.68)
            ),
            nowMs = 1_000L
        )
        val second = tracker.update(
            detections = listOf(
                PortraitFaceBounds(left = 0.47, top = 0.19, right = 0.80, bottom = 0.69),
                PortraitFaceBounds(left = 0.11, top = 0.11, right = 0.26, bottom = 0.33)
            ),
            nowMs = 1_100L
        )

        assertTrue(first.hasLiveDetection)
        assertEquals(2, second.bounds.size)
        assertTrue(second.bounds.first().area > second.bounds.last().area)
        assertTrue(second.bounds.first().left in 0.45..0.48)
    }

    @Test
    fun stableFaceTracker_holdsBriefMissesThenExpires() {
        val tracker = StableFaceTracker(holdDurationMs = 650L)
        tracker.update(
            detections = listOf(PortraitFaceBounds(left = 0.20, top = 0.20, right = 0.44, bottom = 0.52)),
            nowMs = 1_000L
        )

        val held = tracker.update(emptyList(), nowMs = 1_400L)
        val expired = tracker.update(emptyList(), nowMs = 1_800L)

        assertFalse(held.hasLiveDetection)
        assertEquals(1, held.bounds.size)
        assertTrue(expired.bounds.isEmpty())
    }
}
