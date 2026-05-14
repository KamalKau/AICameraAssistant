package com.example.aicameraassistant

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.webrtc.VideoFrame
import org.webrtc.VideoSource

class WebRtcImageFrameSource(
    private val minFrameIntervalMs: Long = 66L,
    private val frameConverter: ImageProxyI420Converter = ImageProxyI420Converter
) {
    private var videoSource: VideoSource? = null
    private var lastFrameMs = 0L

    fun start(
        context: Context,
        width: Int,
        height: Int
    ): Boolean {
        videoSource = WebRtcSessionManager.startImageFrameSource(
            context = context,
            width = width,
            height = height
        )
        lastFrameMs = 0L
        return videoSource != null
    }

    fun buildAnalyzer(mirrorHorizontally: Boolean): ImageAnalysis.Analyzer =
        ImageAnalysis.Analyzer { imageProxy ->
            try {
                pushFrame(imageProxy, mirrorHorizontally)
            } finally {
                imageProxy.close()
            }
        }

    private fun pushFrame(
        image: ImageProxy,
        mirrorHorizontally: Boolean
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFrameMs < minFrameIntervalMs) return false

        val source = videoSource ?: return false
        lastFrameMs = now

        val buffer = frameConverter.convert(
            image = image,
            mirrorHorizontally = mirrorHorizontally,
            rotationDegrees = image.imageInfo.rotationDegrees
        )
        val frame = VideoFrame(buffer, 0, System.nanoTime())
        return try {
            source.capturerObserver.onFrameCaptured(frame)
            true
        } catch (t: Throwable) {
            Log.e("WEBRTC_LOG", "Failed to push image frame", t)
            false
        } finally {
            frame.release()
        }
    }
}
