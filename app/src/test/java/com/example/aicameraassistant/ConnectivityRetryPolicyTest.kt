package com.example.aicameraassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityRetryPolicyTest {
    @Test
    fun backoffIsBoundedAndExponentialWithoutJitter() {
        assertEquals(1_000L, ConnectivityRetryPolicy.delayMs(0, 0.0))
        assertEquals(2_000L, ConnectivityRetryPolicy.delayMs(1, 0.0))
        assertEquals(4_000L, ConnectivityRetryPolicy.delayMs(2, 0.0))
        assertEquals(8_000L, ConnectivityRetryPolicy.delayMs(3, 0.0))
        assertEquals(8_000L, ConnectivityRetryPolicy.delayMs(20, 0.0))
    }

    @Test
    fun jitterNeverExceedsCap() {
        assertTrue(ConnectivityRetryPolicy.delayMs(20, 1.0) <= 10_000L)
    }
}
