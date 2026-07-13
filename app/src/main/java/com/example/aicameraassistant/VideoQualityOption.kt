package com.example.aicameraassistant

import android.util.Range
import androidx.camera.video.Quality

enum class VideoQualityOption(
    val firebaseValue: String,
    val compactLabel: String,
    val menuLabel: String,
    val frameRate: Int,
) {
    Uhd8K30("EIGHT_K_30", "8K 30", "8K 30 FPS", 30),
    Uhd60("UHD_60", "UHD 60", "UHD (4K) 60 FPS", 60),
    Uhd30("UHD_30", "UHD 30", "UHD (4K) 30 FPS", 30),
    Fhd60("FHD_60", "FHD 60", "FHD (1080p) 60 FPS", 60),
    Fhd30("FHD_30", "FHD 30", "FHD (1080p) 30 FPS", 30),
    Hd30("HD_30", "HD 30", "HD (720p) 30 FPS", 30);

    companion object {
        val menuOrder: List<VideoQualityOption> =
            listOf(Uhd8K30, Uhd60, Uhd30, Fhd60, Fhd30, Hd30)

        val default: VideoQualityOption = Fhd30

        fun fromFirebaseValue(value: String?): VideoQualityOption =
            fromFirebaseValueOrNull(value) ?: default

        fun fromFirebaseValueOrNull(value: String?): VideoQualityOption? =
            menuOrder.firstOrNull { it.firebaseValue == value } ?: when (value) {
                // Migrate rooms written by the incomplete selector implementation.
                "UHD8K_30" -> Uhd8K30
                else -> null
            }

        fun sanitizeFirebaseValue(value: String?): String =
            fromFirebaseValue(value).firebaseValue
    }
}

fun VideoQualityOption.targetFrameRateRange(): Range<Int> =
    Range(frameRate, frameRate)

fun VideoQualityOption.cameraXQuality(): Quality? =
    when (this) {
        VideoQualityOption.Uhd8K30 -> null
        VideoQualityOption.Uhd60,
        VideoQualityOption.Uhd30 -> Quality.UHD
        VideoQualityOption.Fhd60,
        VideoQualityOption.Fhd30 -> Quality.FHD
        VideoQualityOption.Hd30 -> Quality.HD
}

fun resolveVideoQuality(
    requestedValue: String?,
    supportedValues: List<String>
): VideoQualityOption? {
    val supported = supportedValues.mapNotNull(VideoQualityOption::fromFirebaseValueOrNull)
    val requested = VideoQualityOption.fromFirebaseValueOrNull(requestedValue)
    return when {
        requested != null && requested in supported -> requested
        VideoQualityOption.Fhd30 in supported -> VideoQualityOption.Fhd30
        VideoQualityOption.Hd30 in supported -> VideoQualityOption.Hd30
        else -> supported.firstOrNull()
    }
}
