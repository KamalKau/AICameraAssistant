package com.example.aicameraassistant

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import org.webrtc.JavaI420Buffer

object ImageProxyI420Converter {
    fun convert(
        image: ImageProxy,
        mirrorHorizontally: Boolean
    ): JavaI420Buffer {
        val width = image.width
        val height = image.height
        val buffer = JavaI420Buffer.allocate(width, height)

        copyPlane(
            source = image.planes[0].buffer,
            sourceRowStride = image.planes[0].rowStride,
            sourcePixelStride = image.planes[0].pixelStride,
            dest = buffer.dataY,
            destRowStride = buffer.strideY,
            width = width,
            height = height,
            mirrorHorizontally = mirrorHorizontally
        )
        copyPlane(
            source = image.planes[1].buffer,
            sourceRowStride = image.planes[1].rowStride,
            sourcePixelStride = image.planes[1].pixelStride,
            dest = buffer.dataU,
            destRowStride = buffer.strideU,
            width = (width + 1) / 2,
            height = (height + 1) / 2,
            mirrorHorizontally = mirrorHorizontally
        )
        copyPlane(
            source = image.planes[2].buffer,
            sourceRowStride = image.planes[2].rowStride,
            sourcePixelStride = image.planes[2].pixelStride,
            dest = buffer.dataV,
            destRowStride = buffer.strideV,
            width = (width + 1) / 2,
            height = (height + 1) / 2,
            mirrorHorizontally = mirrorHorizontally
        )

        return buffer
    }

    private fun copyPlane(
        source: ByteBuffer,
        sourceRowStride: Int,
        sourcePixelStride: Int,
        dest: ByteBuffer,
        destRowStride: Int,
        width: Int,
        height: Int,
        mirrorHorizontally: Boolean
    ) {
        val sourceBuffer = source.duplicate()
        for (row in 0 until height) {
            val sourceRowOffset = row * sourceRowStride
            val destRowOffset = row * destRowStride
            for (col in 0 until width) {
                val destCol = if (mirrorHorizontally) width - 1 - col else col
                dest.put(
                    destRowOffset + destCol,
                    sourceBuffer.get(sourceRowOffset + col * sourcePixelStride)
                )
            }
        }
    }
}
