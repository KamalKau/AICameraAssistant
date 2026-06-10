package com.example.aicameraassistant

import android.view.OrientationEventListener
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationHelperTest {
    @Test
    fun shouldMirrorPreview_onlyMirrorsFrontLensString() {
        assertTrue(shouldMirrorPreview("front"))
        assertTrue(shouldMirrorPreview("FRONT"))
        assertFalse(shouldMirrorPreview("back"))
        assertFalse(shouldMirrorPreview(""))
    }

    @Test
    fun orientationDegreesToSurfaceRotation_mapsDeviceOrientationForCameraX() {
        assertEquals(Surface.ROTATION_0, orientationDegreesToSurfaceRotation(0))
        assertEquals(Surface.ROTATION_0, orientationDegreesToSurfaceRotation(359))

        assertEquals(Surface.ROTATION_270, orientationDegreesToSurfaceRotation(90))
        assertEquals(Surface.ROTATION_180, orientationDegreesToSurfaceRotation(180))
        assertEquals(Surface.ROTATION_90, orientationDegreesToSurfaceRotation(270))
    }

    @Test
    fun orientationDegreesToSurfaceRotation_ignoresUnknownOrientation() {
        assertNull(
            orientationDegreesToSurfaceRotation(
                OrientationEventListener.ORIENTATION_UNKNOWN
            )
        )
    }
}
