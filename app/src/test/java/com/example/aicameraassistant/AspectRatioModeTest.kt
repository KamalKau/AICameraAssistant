package com.example.aicameraassistant

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AspectRatioModeTest {
    @Test
    fun fromKeyFallsBackToFullForUnknownKeys() {
        assertEquals(AspectRatioMode.Full, AspectRatioMode.fromKey("bad_value"))
    }

    @Test
    fun nextCyclesThroughModes() {
        assertEquals(AspectRatioMode.NineSixteen, AspectRatioMode.next("full"))
        assertEquals(AspectRatioMode.ThreeFour, AspectRatioMode.next("9_16"))
        assertEquals(AspectRatioMode.OneOne, AspectRatioMode.next("3_4"))
        assertEquals(AspectRatioMode.Full, AspectRatioMode.next("1_1"))
    }

    @Test
    fun fullAspectRatioKeepsBaseRect() {
        val base = Rect(0f, 0f, 100f, 200f)

        assertEquals(base, aspectRatioFrameRect(base, "full"))
    }

    @Test
    fun nineSixteenCropsWideFrameHorizontally() {
        val frame = aspectRatioFrameRect(
            baseRect = Rect(0f, 0f, 200f, 200f),
            aspectRatioKey = "9_16"
        )

        assertEquals(43.75f, frame!!.left, 0.001f)
        assertEquals(156.25f, frame.right, 0.001f)
        assertEquals(0f, frame.top, 0.001f)
        assertEquals(200f, frame.bottom,0.001f)
    }

    @Test
    fun invalidContainerReturnsNullFrame() {
        assertNull(aspectRatioFrameRect(0f, 200f, "1_1"))
    }
}
