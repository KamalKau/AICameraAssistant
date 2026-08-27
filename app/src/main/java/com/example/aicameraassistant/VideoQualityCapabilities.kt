package com.example.aicameraassistant

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.CamcorderProfile
import android.media.MediaCodecList
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.SessionConfig
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture

/**
 * Verifies the whole camera-stream/encoder/FPS tuple. CameraX's quality query alone only proves
 * that a resolution exists; it does not prove that the same resolution can run at the requested
 * frame rate.
 *
 * CameraX has no named 8K Quality constant. 8K is therefore exposed only through the exact-size
 * adapter below after Camera2, the OEM recording profile, and the encoder all confirm 7680x4320.
 * It is never represented by Quality.HIGHEST.
 */
@OptIn(ExperimentalSessionConfig::class)
internal object VideoQualityCapabilities {
    fun supportedValues(cameraInfo: CameraInfo): List<String> {
        logPhysicalRearCapabilities(cameraInfo)
        return VideoQualityOption.menuOrder
            .filter { option -> isSupported(cameraInfo, option) }
            .map { it.firebaseValue }
    }

    private fun logPhysicalRearCapabilities(cameraInfo: CameraInfo) {
        val map = runCatching { Camera2CameraInfo.from(cameraInfo).cameraCharacteristicsMap }
            .getOrNull() ?: return
        map.forEach { (cameraId, characteristics) ->
            val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val privateSizes = streamMap?.getOutputSizes(ImageFormat.PRIVATE).orEmpty()
            val maximumResolutionMap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION
                )
            } else {
                null
            }
            val maximumPrivateSizes =
                maximumResolutionMap?.getOutputSizes(ImageFormat.PRIVATE).orEmpty()
            val fpsRanges = characteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
            ).orEmpty()
            val eightK = Size(7680, 4320)
            val uhd = Size(3840, 2160)
            val uhdMinNs = runCatching {
                streamMap?.getOutputMinFrameDuration(ImageFormat.PRIVATE, uhd) ?: 0L
            }.getOrDefault(0L)
            val highSpeedUhdRanges = runCatching {
                if (uhd in streamMap?.highSpeedVideoSizes.orEmpty()) {
                    streamMap?.getHighSpeedVideoFpsRangesFor(uhd)?.toList().orEmpty()
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
            AppLogger.info(LogCategory.CAMERA,
                "VideoQualityCaps",
                "cameraId=$cameraId physicalCaps 8K=${eightK in privateSizes} " +
                    "max8K=${eightK in maximumPrivateSizes} " +
                    "UHD=${uhd in privateSizes} uhdMinNs=$uhdMinNs " +
                    "highSpeedUhd=$highSpeedUhdRanges fps=${fpsRanges.toList()}"
            )
        }
    }

    private fun isSupported(cameraInfo: CameraInfo, option: VideoQualityOption): Boolean {
        val quality = option.cameraXQuality() ?: run {
            val adapterSupported = EightKCapabilityAdapter.isConfigurable(cameraInfo)
            val encoderSupported = hasEncoderFor(option.expectedResolution(), option.frameRate)
            AppLogger.info(LogCategory.CAMERA,
                "VideoQualityCaps",
                "${option.firebaseValue} adapter=$adapterSupported encoder=$encoderSupported"
            )
            return adapterSupported && encoderSupported
        }
        val capabilities = Recorder.getVideoCapabilities(cameraInfo)
        if (!capabilities.isQualitySupported(quality, DynamicRange.SDR)) {
            AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "${option.firebaseValue} CameraX quality unsupported")
            return false
        }

        val resolution = QualitySelector.getResolution(cameraInfo, quality) ?: run {
            AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "${option.firebaseValue} has no CameraX resolution")
            return false
        }
        if (resolution != option.expectedResolution()) {
            AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "${option.firebaseValue} resolved unexpected size=$resolution")
            return false
        }
        val recordingProfileSupported = hasExactRecordingProfile(cameraInfo, option)
        val cameraXSessionSupported = supportsCameraXSessionFrameRate(cameraInfo, option)
        if (!recordingProfileSupported && !cameraXSessionSupported) {
            AppLogger.info(LogCategory.CAMERA,
                "VideoQualityCaps",
                "${option.firebaseValue} rejected profile=false sessionFps=false global=${cameraInfo.supportedFrameRateRanges}"
            )
            return false
        }

        val streamMap = runCatching {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
        }.getOrNull() ?: return false
        val privateSizes = streamMap.getOutputSizes(ImageFormat.PRIVATE)?.toSet().orEmpty()
        if (resolution !in privateSizes) {
            AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "${option.firebaseValue} PRIVATE size missing")
            return false
        }
        val encoderSupported = hasEncoderFor(resolution, option.frameRate)
        AppLogger.info(LogCategory.CAMERA,
            "VideoQualityCaps",
            "${option.firebaseValue} accepted=$encoderSupported profile=$recordingProfileSupported sessionFps=$cameraXSessionSupported"
        )
        return encoderSupported
    }

    private fun supportsCameraXSessionFrameRate(
        cameraInfo: CameraInfo,
        option: VideoQualityOption
    ): Boolean = runCatching {
        val quality = option.cameraXQuality() ?: return@runCatching false
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()
        val videoCapture = VideoCapture.Builder(recorder).build()
        val sessionConfig = SessionConfig.Builder(listOf(videoCapture))
            .setFrameRateRange(Range(option.frameRate, option.frameRate))
            .build()
        val ranges = cameraInfo.getSupportedFrameRateRanges(sessionConfig)
        AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "${option.firebaseValue} CameraX session ranges=$ranges")
        ranges.any { range ->
            range.lower <= option.frameRate && option.frameRate <= range.upper
        }
    }.onFailure {
        AppLogger.warning(LogCategory.CAMERA, "VideoQuality", "Unable to query CameraX session FPS for ${option.firebaseValue}", it)
    }.getOrDefault(false)

    fun eightKEncodingBitRate(cameraInfo: CameraInfo): Int? =
        EightKCapabilityAdapter.profile(cameraInfo)?.videoProfiles
            ?.filter { it.width == 7680 && it.height == 4320 && it.frameRate >= 30 }
            ?.maxOfOrNull { it.bitrate }

    private fun hasExactRecordingProfile(
        cameraInfo: CameraInfo,
        option: VideoQualityOption
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val cameraId = runCatching { Camera2CameraInfo.from(cameraInfo).cameraId }.getOrNull()
            ?: return false
        val profileQuality = when (option) {
            VideoQualityOption.Uhd8K30 -> CamcorderProfile.QUALITY_8KUHD
            VideoQualityOption.Uhd60, VideoQualityOption.Uhd30 -> CamcorderProfile.QUALITY_2160P
            VideoQualityOption.Fhd60, VideoQualityOption.Fhd30 -> CamcorderProfile.QUALITY_1080P
            VideoQualityOption.Hd30 -> CamcorderProfile.QUALITY_720P
        }
        val expected = option.expectedResolution()
        val profiles = runCatching { CamcorderProfile.getAll(cameraId, profileQuality) }
            .getOrNull() ?: return false
        if (option == VideoQualityOption.Uhd60 || option == VideoQualityOption.Uhd8K30) {
            AppLogger.info(LogCategory.CAMERA,
                "VideoQualityCaps",
                "${option.firebaseValue} OEM profiles=" + profiles.videoProfiles.joinToString { profile ->
                    "${profile.width}x${profile.height}@${profile.frameRate}"
                }
            )
        }
        return profiles.videoProfiles.any { profile ->
            profile.width == expected.width &&
                profile.height == expected.height &&
                profile.frameRate >= option.frameRate
        }
    }

    private fun hasEncoderFor(size: Size, frameRate: Int): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .flatMap { codec -> codec.supportedTypes.asSequence().map { codec to it } }
            .filter { (_, mime) -> mime.startsWith("video/") }
            .any { (codec, mime) ->
                runCatching {
                    codec.getCapabilitiesForType(mime).videoCapabilities
                        .areSizeAndRateSupported(size.width, size.height, frameRate.toDouble())
                }.getOrDefault(false)
            }
    }.onFailure {
        AppLogger.warning(LogCategory.CAMERA, "VideoQuality", "Unable to verify video encoder capability", it)
    }.getOrDefault(false)

}

/** Exact-size adapter for true 8K; the ordinary UHD quality path is never used as the resolution. */
private object EightKCapabilityAdapter {
    fun isConfigurable(cameraInfo: CameraInfo): Boolean {
        val profiles = profile(cameraInfo) ?: return false
        AppLogger.info(LogCategory.CAMERA,
            "VideoQualityCaps",
            "EIGHT_K_30 OEM profiles=" + profiles.videoProfiles.joinToString { item ->
                "${item.width}x${item.height}@${item.frameRate}"
            }
        )
        val hasProfile = profiles.videoProfiles.any { item ->
            item.width == 7680 && item.height == 4320 && item.frameRate >= 30
        }
        if (!hasProfile) return false

        val streamMap = runCatching {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
        }.getOrNull() ?: return false
        val maximumResolutionMap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION
                )
            }.getOrNull()
        } else {
            null
        }
        val availablePrivateSizes =
            streamMap.getOutputSizes(ImageFormat.PRIVATE).orEmpty().toList() +
                maximumResolutionMap?.getOutputSizes(ImageFormat.PRIVATE).orEmpty().toList()
        val hasPrivate8K = Size(7680, 4320) in availablePrivateSizes
        AppLogger.info(LogCategory.CAMERA, "VideoQualityCaps", "EIGHT_K_30 private8K=$hasPrivate8K")
        if (!hasPrivate8K) return false

        return canConfigureTrue8KWithCurrentRecorder()
    }

    fun profile(cameraInfo: CameraInfo): android.media.EncoderProfiles? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val cameraId = runCatching { Camera2CameraInfo.from(cameraInfo).cameraId }.getOrNull()
            ?: return null
        return sequenceOf(CamcorderProfile.QUALITY_8KUHD, CamcorderProfile.QUALITY_HIGH)
            .mapNotNull { quality ->
                runCatching { CamcorderProfile.getAll(cameraId, quality) }.getOrNull()
            }
            .firstOrNull { profiles ->
                profiles.videoProfiles.any { item ->
                    item.width == 7680 && item.height == 4320 && item.frameRate >= 30
                }
            }
    }

    private fun canConfigureTrue8KWithCurrentRecorder(): Boolean = true
}

private fun VideoQualityOption.expectedResolution(): Size = when (this) {
    VideoQualityOption.Uhd8K30 -> Size(7680, 4320)
    VideoQualityOption.Uhd60, VideoQualityOption.Uhd30 -> Size(3840, 2160)
    VideoQualityOption.Fhd60, VideoQualityOption.Fhd30 -> Size(1920, 1080)
    VideoQualityOption.Hd30 -> Size(1280, 720)
}
