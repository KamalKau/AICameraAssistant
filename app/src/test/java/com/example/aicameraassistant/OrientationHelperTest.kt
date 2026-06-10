package com.example.aicameraassistant

import org.junit.Assert.assertFalse
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
}
