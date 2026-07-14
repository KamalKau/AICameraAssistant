package com.example.aicameraassistant

import kotlin.math.pow
import kotlin.random.Random

internal object ConnectivityRetryPolicy {
    const val maxReconnectAttempts = 4

    fun delayMs(attempt: Int, jitterFraction: Double = Random.nextDouble()): Long {
        val safeAttempt = attempt.coerceAtLeast(0)
        val base = (1_000.0 * 2.0.pow(safeAttempt.toDouble())).toLong().coerceAtMost(8_000L)
        val jitter = (base * 0.2 * jitterFraction.coerceIn(0.0, 1.0)).toLong()
        return (base + jitter).coerceAtMost(10_000L)
    }
}
