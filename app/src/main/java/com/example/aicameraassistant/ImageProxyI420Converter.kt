package com.example.aicameraassistant

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import org.webrtc.JavaI420Buffer

object ImageProxyI420Converter {
    fun convert(
        image: ImageProxy,
        mirrorHorizontally: Boolean,
        rotationDegrees: Int = 0
    ): JavaI420Buffer {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val swapsDimensions = normalizedRotation == 90 || normalizedRotation == 270
        val outputWidth = if (swapsDimensions) sourceHeight else sourceWidth
        val outputHeight = if (swapsDimensions) sourceWidth else sourceHeight
        val buffer = JavaI420Buffer.allocate(outputWidth, outputHeight)

        copyPlaneTransformed(
            source = image.planes[0].buffer,
            sourceRowStride = image.planes[0].rowStride,
            sourcePixelStride = image.planes[0].pixelStride,
            dest = buffer.dataY,
            destRowStride = buffer.strideY,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            mirrorHorizontally = mirrorHorizontally,
            rotationDegrees = normalizedRotation
        )
        copyPlaneTransformed(
            source = image.planes[1].buffer,
            sourceRowStride = image.planes[1].rowStride,
            sourcePixelStride = image.planes[1].pixelStride,
            dest = buffer.dataU,
            destRowStride = buffer.strideU,
            sourceWidth = (sourceWidth + 1) / 2,
            sourceHeight = (sourceHeight + 1) / 2,
            outputWidth = (outputWidth + 1) / 2,
            outputHeight = (outputHeight + 1) / 2,
            mirrorHorizontally = mirrorHorizontally,
            rotationDegrees = normalizedRotation
        )
        copyPlaneTransformed(
            source = image.planes[2].buffer,
            sourceRowStride = image.planes[2].rowStride,
            sourcePixelStride = image.planes[2].pixelStride,
            dest = buffer.dataV,
            destRowStride = buffer.strideV,
            sourceWidth = (sourceWidth + 1) / 2,
            sourceHeight = (sourceHeight + 1) / 2,
            outputWidth = (outputWidth + 1) / 2,
            outputHeight = (outputHeight + 1) / 2,
            mirrorHorizontally = mirrorHorizontally,
            rotationDegrees = normalizedRotation
        )

        return buffer
    }

    private fun copyPlaneTransformed(
        source: ByteBuffer,
        sourceRowStride: Int,
        sourcePixelStride: Int,
        dest: ByteBuffer,
        destRowStride: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        mirrorHorizontally: Boolean,
        rotationDegrees: Int
    ) {
        val sourceBuffer = source.duplicate()
        for (row in 0 until outputHeight) {
            val destRowOffset = row * destRowStride
            for (col in 0 until outputWidth) {
                val orientedCol = if (mirrorHorizontally) outputWidth - 1 - col else col
                val sourceCol: Int
                val sourceRow: Int
                when (rotationDegrees) {
                    90 -> {
                        sourceCol = row
                        sourceRow = sourceHeight - 1 - orientedCol
                    }
                    180 -> {
                        sourceCol = sourceWidth - 1 - orientedCol
                        sourceRow = sourceHeight - 1 - row
                    }
                    270 -> {
                        sourceCol = sourceWidth - 1 - row
                        sourceRow = orientedCol
                    }
                    else -> {
                        sourceCol = orientedCol
                        sourceRow = row
                    }
                }
                dest.put(
                    destRowOffset + col,
                    sourceBuffer.get(sourceRow * sourceRowStride + sourceCol * sourcePixelStride)
                )
            }
        }
    }
}
