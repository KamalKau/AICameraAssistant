package com.example.aicameraassistant

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class BoomerangRecorder(
    private val context: Context,
    private val previewView: PreviewView
) {
    suspend fun record(): Boolean {
        val frames = capturePreviewFrames()
        if (frames.size < MIN_FRAME_COUNT) {
            frames.forEach { it.recycle() }
            return false
        }

        val outputFile = File(context.cacheDir, "boomerang_${System.currentTimeMillis()}.mp4")
        return runCatching {
            withContext(Dispatchers.IO) {
                BoomerangMp4Encoder.encode(
                    frames = frames,
                    outputFile = outputFile,
                    frameDelayMs = FRAME_DELAY_MS
                )
                saveVideoToGallery(outputFile)
            }
        }.onFailure {
            Log.e("AICameraAssistant", "Boomerang capture failed", it)
        }.also {
            frames.forEach { frame -> frame.recycle() }
            outputFile.delete()
        }.isSuccess
    }

    private suspend fun capturePreviewFrames(): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        var attempts = 0
        var lastSignature: Long? = null
        while (frames.size < FRAME_COUNT && attempts < MAX_CAPTURE_ATTEMPTS) {
            previewView.bitmap?.scaleForBoomerang()?.let { bitmap ->
                val signature = bitmap.motionSignature()
                if (lastSignature == null || kotlin.math.abs(signature - lastSignature!!) > FRAME_DIFFERENCE_THRESHOLD) {
                    frames.add(bitmap)
                    lastSignature = signature
                } else {
                    bitmap.recycle()
                }
            }
            attempts += 1
            delay(FRAME_DELAY_MS)
        }
        return frames
    }

    private fun saveVideoToGallery(file: File) {
        val name = "BOOMERANG_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AICameraAssistant")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create MediaStore video")

        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open MediaStore video output")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun Bitmap.scaleForBoomerang(): Bitmap {
        val maxLongEdge = 720
        val scale = (maxLongEdge.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val targetWidth = ((width * scale).toInt().coerceAtLeast(2) / 2) * 2
        val targetHeight = ((height * scale).toInt().coerceAtLeast(2) / 2) * 2
        if (targetWidth == width && targetHeight == height) return copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.motionSignature(): Long {
        val sampleWidth = 8
        val sampleHeight = 8
        var signature = 0L
        val stepX = (width / sampleWidth).coerceAtLeast(1)
        val stepY = (height / sampleHeight).coerceAtLeast(1)
        var index = 1
        var y = stepY / 2
        while (y < height) {
            var x = stepX / 2
            while (x < width) {
                val pixel = getPixel(x, y)
                val r = pixel shr 16 and 0xff
                val g = pixel shr 8 and 0xff
                val b = pixel and 0xff
                signature += ((r * 3L) + (g * 5L) + (b * 7L)) * index
                index += 1
                x += stepX
            }
            y += stepY
        }
        return signature
    }

    private companion object {
        const val FRAME_COUNT = 24
        const val MIN_FRAME_COUNT = 10
        const val FRAME_DELAY_MS = 65L
        const val MAX_CAPTURE_ATTEMPTS = 48
        const val FRAME_DIFFERENCE_THRESHOLD = 950L
    }
}

private object BoomerangMp4Encoder {
    private const val MIME_TYPE = "video/avc"
    private const val FRAME_RATE = 24
    private const val I_FRAME_INTERVAL = 1
    private const val LOOP_COUNT = 2
    private const val TIMEOUT_US = 10_000L

    fun encode(
        frames: List<Bitmap>,
        outputFile: File,
        frameDelayMs: Long
    ) {
        val width = frames.first().width
        val height = frames.first().height
        val bitrate = (width * height * 4).coerceAtLeast(1_500_000)
        val colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            boomerangFrames(frames).forEachIndexed { index, frame ->
                queueFrame(
                    codec = codec,
                    frame = frame,
                    width = width,
                    height = height,
                    presentationTimeUs = index * frameDelayMs * 1_000L
                )
                while (true) {
                    val state = drain(codec, muxer, muxerStarted, trackIndex)
                    muxerStarted = state.muxerStarted
                    trackIndex = state.trackIndex
                    if (!state.outputDrained) break
                }
            }

            queueEndOfStream(codec, boomerangFrames(frames).size * frameDelayMs * 1_000L)
            while (true) {
                val state = drain(codec, muxer, muxerStarted, trackIndex, endOfStream = true)
                muxerStarted = state.muxerStarted
                trackIndex = state.trackIndex
                if (state.endReached) break
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun boomerangFrames(frames: List<Bitmap>): List<Bitmap> {
        val cycle = frames + frames.dropLast(1).drop(1).asReversed()
        return buildList {
            repeat(LOOP_COUNT) {
                addAll(cycle)
            }
        }
    }

    private fun queueFrame(
        codec: MediaCodec,
        frame: Bitmap,
        width: Int,
        height: Int,
        presentationTimeUs: Long
    ) {
        val inputBufferId = waitForInputBuffer(codec)
        val inputBuffer = codec.getInputBuffer(inputBufferId)
            ?: error("Encoder input buffer was null")
        inputBuffer.clear()
        inputBuffer.put(frame.toNv12(width, height))
        codec.queueInputBuffer(
            inputBufferId,
            0,
            width * height * 3 / 2,
            presentationTimeUs,
            0
        )
    }

    private fun queueEndOfStream(codec: MediaCodec, presentationTimeUs: Long) {
        val inputBufferId = waitForInputBuffer(codec)
        codec.queueInputBuffer(
            inputBufferId,
            0,
            0,
            presentationTimeUs,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
        )
    }

    private fun waitForInputBuffer(codec: MediaCodec): Int {
        while (true) {
            val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferId >= 0) return inputBufferId
        }
    }

    private fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        muxerStarted: Boolean,
        trackIndex: Int,
        endOfStream: Boolean = false
    ): EncoderState {
        val bufferInfo = MediaCodec.BufferInfo()
        var currentMuxerStarted = muxerStarted
        var currentTrackIndex = trackIndex

        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return EncoderState(currentMuxerStarted, currentTrackIndex, outputDrained = false)
                }

                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!currentMuxerStarted) { "Encoder format changed after muxer start" }
                    currentTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    currentMuxerStarted = true
                }

                outputBufferId >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputBufferId)
                        ?: error("Encoder output buffer was null")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        check(currentMuxerStarted) { "Muxer has not started" }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                    }

                    val endReached = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputBufferId, false)
                    if (endReached) {
                        return EncoderState(
                            currentMuxerStarted,
                            currentTrackIndex,
                            outputDrained = true,
                            endReached = true
                        )
                    }
                    return EncoderState(
                        currentMuxerStarted,
                        currentTrackIndex,
                        outputDrained = true
                    )
                }
            }

            if (!endOfStream) {
                return EncoderState(
                    currentMuxerStarted,
                    currentTrackIndex,
                    outputDrained = false
                )
            }
        }
    }

    private fun Bitmap.toNv12(width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = color shr 16 and 0xff
                val g = color shr 8 and 0xff
                val b = color and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv.size) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }

        return yuv
    }

    private data class EncoderState(
        val muxerStarted: Boolean,
        val trackIndex: Int,
        val outputDrained: Boolean = false,
        val endReached: Boolean = false
    )
}
