package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRecordingTimerTest {
    @Test
    fun formatRecordingDuration_formatsMinutesAndSeconds() {
        assertEquals("00:00", formatRecordingDuration(0L))
        assertEquals("00:09", formatRecordingDuration(9_999L))
        assertEquals("01:05", formatRecordingDuration(65_000L))
    }

    @Test
    fun formatRecordingDuration_formatsHoursWhenNeeded() {
        assertEquals("1:00:00", formatRecordingDuration(3_600_000L))
        assertEquals("2:03:04", formatRecordingDuration(7_384_000L))
    }

    @Test
    fun formatRecordingDuration_clampsNegativeValues() {
        assertEquals("00:00", formatRecordingDuration(-1_000L))
    }
}
