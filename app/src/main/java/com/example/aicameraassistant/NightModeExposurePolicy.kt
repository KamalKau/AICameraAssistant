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

        return maxIndex
    }
}
