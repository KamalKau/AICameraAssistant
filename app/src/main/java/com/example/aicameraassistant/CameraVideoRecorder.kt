package com.example.aicameraassistant

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat

class CameraVideoRecorder(private val context: Context) {
    private var activeRecording: Recording? = null

    val isRecording: Boolean
        get() = activeRecording != null

    @SuppressLint("MissingPermission")
    fun toggle(
        videoCapture: VideoCapture<Recorder>?,
        onRecordingStateChanged: (Boolean) -> Unit,
        onRequestHandled: () -> Unit
    ): Boolean {
        activeRecording?.let { recording ->
            recording.stop()
            activeRecording = null
            onRecordingStateChanged(false)
            onRequestHandled()
            return true
        }

        val capture = videoCapture ?: run {
            Log.e("AICameraAssistant", "VideoCapture is not initialized yet")
            return false
        }

        val outputOptions = buildOutputOptions()
        val pendingRecording: PendingRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    onRecordingStateChanged(true)
                    onRequestHandled()
                    Toast.makeText(context, "Video recording started", Toast.LENGTH_SHORT).show()
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    onRecordingStateChanged(false)
                    if (event.hasError()) {
                        Log.e("AICameraAssistant", "Video recording failed: ${event.error}")
                        Toast.makeText(context, "Video recording failed", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("AICameraAssistant", "Video saved: ${event.outputResults.outputUri}")
                        Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        return true
    }

    fun stop(onRecordingStateChanged: (Boolean) -> Unit = {}) {
        activeRecording?.stop()
        activeRecording = null
        onRecordingStateChanged(false)
    }

    private fun buildOutputOptions(): MediaStoreOutputOptions {
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AICameraAssistant")
        }

        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
    }
}
