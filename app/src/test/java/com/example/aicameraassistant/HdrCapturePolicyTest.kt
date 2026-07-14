package com.example.aicameraassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrCapturePolicyTest {
    @Test
    fun usesExtensionOnlyForSupportedPhotoWithFlashAndNightOff() {
        assertTrue(HdrCapturePolicy.shouldUsePhotoExtension(true, true, "photo", false, "off"))
        assertFalse(HdrCapturePolicy.shouldUsePhotoExtension(true, false, "photo", false, "off"))
        assertFalse(HdrCapturePolicy.shouldUsePhotoExtension(true, true, "video", false, "off"))
        assertFalse(HdrCapturePolicy.shouldUsePhotoExtension(true, true, "photo", true, "off"))
        assertFalse(HdrCapturePolicy.shouldUsePhotoExtension(true, true, "photo", false, "auto"))
        assertFalse(HdrCapturePolicy.shouldUsePhotoExtension(true, true, "photo", false, "on"))
    }
}
