package com.example.aicameraassistant

class NightModeExposurePolicy {
    fun resolveTargetIndex(
        nightModeEnabled: Boolean,
        requestedIndex: Int,
        minIndex: Int,
        maxIndex: Int
    ): Int {
        if (minIndex == maxIndex) return requestedIndex

        val clampedRequestedIndex = requestedIndex.coerceIn(minIndex, maxIndex)
        if (!nightModeEnabled) return clampedRequestedIndex

        val positiveHeadroom = (maxIndex - clampedRequestedIndex).coerceAtLeast(0)
        if (positiveHeadroom == 0) return clampedRequestedIndex

        // A moderate compensation boost lifts shadows without forcing the slowest shutter/ISO
        // combination. The OEM Night Extension performs its own metering when it is active.
        val boost = ((positiveHeadroom * 0.4f).toInt()).coerceAtLeast(1)
        return (clampedRequestedIndex + boost).coerceAtMost(maxIndex)
    }

    fun resolveCaptureIndex(
        currentIndex: Int,
        minIndex: Int,
        maxIndex: Int,
        motionDetected: Boolean
    ): Int {
        val current = currentIndex.coerceIn(minIndex, maxIndex)
        val headroom = (maxIndex - current).coerceAtLeast(0)
        if (headroom == 0) return current
        val fraction = if (motionDetected) 0.2f else 0.5f
        val boost = (headroom * fraction).toInt().coerceAtLeast(1)
        return (current + boost).coerceAtMost(maxIndex)
    }
}
