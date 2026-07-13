package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityOptionTest {
    @Test
    fun wireValuesAndDefaultMatchRoomContract() {
        assertEquals(
            listOf("EIGHT_K_30", "UHD_60", "UHD_30", "FHD_60", "FHD_30", "HD_30"),
            VideoQualityOption.menuOrder.map { it.firebaseValue }
        )
        assertEquals(VideoQualityOption.Fhd30, VideoQualityOption.default)
    }

    @Test
    fun unsupportedRequestFallsBackInRequiredOrder() {
        assertEquals(
            VideoQualityOption.Fhd30,
            resolveVideoQuality("UHD_60", listOf("HD_30", "FHD_30"))
        )
        assertEquals(
            VideoQualityOption.Hd30,
            resolveVideoQuality("UHD_60", listOf("HD_30", "FHD_60"))
        )
        assertEquals(
            VideoQualityOption.Uhd30,
            resolveVideoQuality("invalid", listOf("UHD_30", "FHD_60"))
        )
        assertNull(resolveVideoQuality("FHD_30", emptyList()))
    }

    @Test
    fun supportedRequestIsKept() {
        assertEquals(
            VideoQualityOption.Fhd60,
            resolveVideoQuality("FHD_60", listOf("FHD_30", "FHD_60", "HD_30"))
        )
    }
}
