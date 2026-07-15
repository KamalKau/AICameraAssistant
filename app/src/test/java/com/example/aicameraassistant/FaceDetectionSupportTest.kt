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
        tracker.update(
            detections = listOf(
                PortraitFaceBounds(left = 0.10, top = 0.10, right = 0.25, bottom = 0.32),
                PortraitFaceBounds(left = 0.45, top = 0.18, right = 0.78, bottom = 0.68)
            ),
            nowMs = 1_050L
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
        tracker.update(
            detections = listOf(PortraitFaceBounds(left = 0.20, top = 0.20, right = 0.44, bottom = 0.52)),
            nowMs = 1_050L
        )

        val held = tracker.update(emptyList(), nowMs = 1_400L)
        val expired = tracker.update(emptyList(), nowMs = 1_800L)

        assertFalse(held.hasLiveDetection)
        assertEquals(1, held.bounds.size)
        assertTrue(expired.bounds.isEmpty())
    }

    @Test
    fun confidencePolicy_keepsProfilesButRejectsWeakTinyEdgeCandidates() {
        val policy = FaceConfidencePolicy(minimumConfidence = 0.58)
        val profile = policy.estimate(yawDegrees = 65f, visibleArea = 0.08, touchesFrameEdge = false)
        val weakEdge = policy.estimate(yawDegrees = 85f, visibleArea = 0.001, touchesFrameEdge = true)

        assertTrue(policy.accepts(profile))
        assertFalse(policy.accepts(weakEdge))
    }

    @Test
    fun overlayController_showsOnceForSameTrackedFaceAndIgnoresSmallMovement() {
        val controller = FaceOverlayEventController()
        val first = PortraitFaceBounds(
            left = 0.20, top = 0.20, right = 0.42, bottom = 0.52,
            trackingId = 7L, isPrimary = true
        )
        val smallMove = first.copy(left = 0.21, right = 0.43)

        assertTrue(controller.update(listOf(first), true, 1_000L).show)
        assertFalse(controller.update(listOf(smallMove), true, 1_100L).show)
        assertFalse(controller.update(listOf(smallMove), true, 1_300L).show)
    }

    @Test
    fun overlayController_reshowsForNewFacePrimaryChangeAndMeaningfulReacquisition() {
        val controller = FaceOverlayEventController(lostReacquisitionMs = 650L)
        val primary = PortraitFaceBounds(
            left = 0.15, top = 0.15, right = 0.40, bottom = 0.50,
            trackingId = 1L, isPrimary = true
        )
        val newcomer = PortraitFaceBounds(
            left = 0.55, top = 0.16, right = 0.82, bottom = 0.54,
            trackingId = 2L
        )

        controller.update(listOf(primary), true, 1_000L)
        assertTrue(controller.update(listOf(primary, newcomer), true, 1_100L).show)
        assertTrue(controller.update(listOf(newcomer.copy(isPrimary = true), primary.copy(isPrimary = false)), true, 1_200L).show)
        assertFalse(controller.update(listOf(newcomer.copy(isPrimary = true)), false, 1_500L).show)
        assertFalse(controller.update(emptyList(), false, 1_900L).show)
        assertTrue(controller.update(listOf(newcomer.copy(isPrimary = true)), true, 2_000L).show)
    }

}
