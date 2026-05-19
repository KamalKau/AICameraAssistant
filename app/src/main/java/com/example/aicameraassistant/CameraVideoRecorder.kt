package com.example.aicameraassistant

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VideoRecordingState {
    Idle,
    Recording,
    Paused
}

class CameraVideoRecorder(private val context: Context) {
    private data class Segment(
        val order: Int,
        val file: File,
        var finalized: Boolean = false,
        var failed: Boolean = false
    )

    private var activeRecording: Recording? = null
    private var recordingState: VideoRecordingState = VideoRecordingState.Idle
    private val segments = mutableListOf<Segment>()
    private var pendingSegmentCount = 0
    private var nextSegmentOrder = 0
    private var finalStopRequested = false
    private var mergeInProgress = false
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val isRecording: Boolean
        get() = activeRecording != null || recordingState != VideoRecordingState.Idle

    val state: VideoRecordingState
        get() = recordingState

    @SuppressLint("MissingPermission")
    fun start(
        videoCapture: VideoCapture<Recorder>?,
        onRecordingStateChanged: (VideoRecordingState) -> Unit,
        onRequestHandled: () -> Unit,
        showStartToast: Boolean = true
    ): Boolean {
        if (activeRecording != null) {
            onRequestHandled()
            return true
        }

        if (mergeInProgress) {
            Log.w("AICameraAssistant", "Video save is still in progress")
            Toast.makeText(context, "Saving video...", Toast.LENGTH_SHORT).show()
            return false
        }

        val capture = videoCapture ?: run {
            Log.e("AICameraAssistant", "VideoCapture is not initialized yet")
            return false
        }

        if (recordingState == VideoRecordingState.Idle) {
            resetSession()
        }

        val segment = createSegmentFile() ?: return false
        val outputOptions = FileOutputOptions.Builder(segment.file).build()
        val pendingRecording: PendingRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()

        segments += segment
        pendingSegmentCount += 1

        var startedRecording: Recording? = null
        startedRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            val isCurrentRecording = activeRecording === startedRecording
            when (event) {
                is VideoRecordEvent.Start -> {
                    if (!isCurrentRecording) return@start
                    recordingState = VideoRecordingState.Recording
                    onRecordingStateChanged(recordingState)
                    onRequestHandled()
                    if (showStartToast) {
                        showStatusPopup(
                            context = context,
                            title = "Recording started",
                            detail = "Capturing video",
                            badge = "REC",
                            accentColor = Color.rgb(255, 45, 45)
                        )
                    }
                }

                is VideoRecordEvent.Pause -> {
                    if (!isCurrentRecording) return@start
                    recordingState = VideoRecordingState.Paused
                    onRecordingStateChanged(recordingState)
                }

                is VideoRecordEvent.Resume -> {
                    if (!isCurrentRecording) return@start
                    recordingState = VideoRecordingState.Recording
                    onRecordingStateChanged(recordingState)
                }

                is VideoRecordEvent.Finalize -> {
                    segment.finalized = true
                    pendingSegmentCount = (pendingSegmentCount - 1).coerceAtLeast(0)
                    if (event.hasError()) {
                        segment.failed = true
                        Log.e("AICameraAssistant", "Video segment failed: ${event.error}")
                        if (isCurrentRecording && !finalStopRequested) {
                            activeRecording = null
                            recordingState = VideoRecordingState.Idle
                            onRecordingStateChanged(recordingState)
                            Toast.makeText(context, "Video recording failed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.d("AICameraAssistant", "Video segment saved: ${event.outputResults.outputUri}")
                    }

                    if (isCurrentRecording) {
                        activeRecording = null
                    }
                    maybeMergeFinalVideo(onRecordingStateChanged)
                }
            }
        }
        activeRecording = startedRecording

        return true
    }

    fun pause(
        onRecordingStateChanged: (VideoRecordingState) -> Unit,
        onRequestHandled: () -> Unit
    ): Boolean {
        val recording = activeRecording ?: return false
        if (recordingState == VideoRecordingState.Paused) {
            onRequestHandled()
            return true
        }
        recording.pause()
        recordingState = VideoRecordingState.Paused
        onRecordingStateChanged(recordingState)
        onRequestHandled()
        Toast.makeText(context, "Video paused", Toast.LENGTH_SHORT).show()
        return true
    }

    fun resume(
        onRecordingStateChanged: (VideoRecordingState) -> Unit,
        onRequestHandled: () -> Unit
    ): Boolean {
        val recording = activeRecording ?: return false
        if (recordingState == VideoRecordingState.Recording) {
            onRequestHandled()
            return true
        }
        recording.resume()
        recordingState = VideoRecordingState.Recording
        onRecordingStateChanged(recordingState)
        onRequestHandled()
        Toast.makeText(context, "Video resumed", Toast.LENGTH_SHORT).show()
        return true
    }

    fun stopForCameraSwitch(onRecordingStateChanged: (VideoRecordingState) -> Unit = {}): Boolean {
        val recording = activeRecording ?: return recordingState == VideoRecordingState.Recording
        recording.stop()
        activeRecording = null
        recordingState = VideoRecordingState.Recording
        onRecordingStateChanged(recordingState)
        return true
    }

    fun stop(
        onRecordingStateChanged: (VideoRecordingState) -> Unit = {},
        onRequestHandled: () -> Unit = {}
    ): Boolean {
        if (
            activeRecording == null &&
            recordingState == VideoRecordingState.Idle &&
            segments.isEmpty() &&
            pendingSegmentCount == 0
        ) {
            onRequestHandled()
            return true
        }

        finalStopRequested = true
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            activeRecording = null
        }
        recordingState = VideoRecordingState.Idle
        onRecordingStateChanged(recordingState)
        onRequestHandled()
        maybeMergeFinalVideo(onRecordingStateChanged)
        return true
    }

    private fun createSegmentFile(): Segment? {
        val segmentDir = File(context.cacheDir, "video_segments").apply { mkdirs() }
        return runCatching {
            val order = nextSegmentOrder++
            Segment(
                order = order,
                file = File.createTempFile("segment_${order}_", ".mp4", segmentDir)
            )
        }.onFailure {
            Log.e("AICameraAssistant", "Unable to create video segment file", it)
        }.getOrNull()
    }

    private fun maybeMergeFinalVideo(onRecordingStateChanged: (VideoRecordingState) -> Unit) {
        if (!finalStopRequested || pendingSegmentCount > 0 || mergeInProgress) return

        mergeInProgress = true
        val completedSegments = segments
            .filter { it.finalized && !it.failed && it.file.length() > 0L }
            .sortedBy { it.order }

        recorderScope.launch {
            val saved = withContext(Dispatchers.IO) {
                if (completedSegments.isEmpty()) {
                    false
                } else {
                    mergeSegmentsToGallery(completedSegments)
                }
            }

            completedSegments.forEach { it.file.delete() }
            resetSession()
            recordingState = VideoRecordingState.Idle
            onRecordingStateChanged(recordingState)
            Toast.makeText(
                context,
                if (saved) "Video saved" else "Video recording failed",
                Toast.LENGTH_SHORT
            ).takeUnless { saved }?.show()
            if (saved) {
                showStatusPopup(
                    context = context,
                    title = "Video saved",
                    detail = "Saved to gallery",
                    badge = "OK",
                    accentColor = Color.rgb(56, 217, 145)
                )
            }
        }
    }

    private fun mergeSegmentsToGallery(segmentsToMerge: List<Segment>): Boolean {
        if (segmentsToMerge.size == 1) {
            return saveFileToGallery(segmentsToMerge.first().file)
        }

        val outputFile = File(context.cacheDir, "VID_${System.currentTimeMillis()}.mp4")
        val merged = runCatching {
            mergeMp4Files(
                inputFiles = segmentsToMerge.map { it.file },
                outputFile = outputFile
            )
            saveFileToGallery(outputFile)
        }.onFailure {
            Log.e("AICameraAssistant", "Unable to merge video segments", it)
        }.also {
            outputFile.delete()
        }.getOrDefault(false)

        if (merged) return true

        Log.w("AICameraAssistant", "Merged video save failed; saving segments as fallback")
        return segmentsToMerge
            .map { saveFileToGallery(it.file) }
            .any { it }
    }

    private fun mergeMp4Files(inputFiles: List<File>, outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        val outputTrackMap = buildOutputTrackMap(inputFiles, muxer)
        val trackOffsetsUs = mutableMapOf<String, Long>()
        outputTrackMap.keys.forEach { trackOffsetsUs[it] = 0L }

        try {
            readOutputRotationDegrees(inputFiles)?.let { rotationDegrees ->
                muxer.setOrientationHint(rotationDegrees)
                Log.d("AICameraAssistant", "Merged video rotation hint=$rotationDegrees")
            }
            muxer.start()
            muxerStarted = true
            inputFiles.forEach { file ->
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                try {
                    val selectedTracks = mutableListOf<Pair<Int, String>>()
                    for (trackIndex in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(trackIndex)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                        if (!outputTrackMap.containsKey(mime)) continue
                        selectedTracks += trackIndex to mime
                    }

                    selectedTracks.forEach { (trackIndex, mime) ->
                        writeTrackSamples(
                            file = file,
                            trackIndex = trackIndex,
                            outputTrackIndex = outputTrackMap.getValue(mime),
                            timeOffsetUs = trackOffsetsUs.getValue(mime),
                            muxer = muxer
                        ).also { durationUs ->
                            trackOffsetsUs[mime] = trackOffsetsUs.getValue(mime) + durationUs
                        }
                    }
                } finally {
                    extractor.release()
                }
            }
        } finally {
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }

    private fun readOutputRotationDegrees(inputFiles: List<File>): Int? {
        val rotations = inputFiles.mapNotNull { file ->
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull()
                        ?.let { ((it % 360) + 360) % 360 }
                } finally {
                    retriever.release()
                }
            }.onFailure {
                Log.w("AICameraAssistant", "Unable to read video rotation metadata", it)
            }.getOrNull()
        }

        if (rotations.isEmpty()) return null

        return rotations
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<Int, Int>> { it.value }
                    .thenByDescending { if (it.key == 0) 0 else 1 }
            )
            ?.key
    }

    private fun buildOutputTrackMap(
        inputFiles: List<File>,
        muxer: MediaMuxer
    ): MutableMap<String, Int> {
        val outputTrackMap = mutableMapOf<String, Int>()
        inputFiles.forEach { file ->
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            try {
                for (trackIndex in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                    if (!outputTrackMap.containsKey(mime)) {
                        outputTrackMap[mime] = muxer.addTrack(format)
                    }
                }
            } finally {
                extractor.release()
            }
        }
        return outputTrackMap
    }

    private fun writeTrackSamples(
        file: File,
        trackIndex: Int,
        outputTrackIndex: Int,
        timeOffsetUs: Long,
        muxer: MediaMuxer
    ): Long {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        extractor.selectTrack(trackIndex)

        val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_SIZE)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        var firstSampleTimeUs: Long? = null
        var lastSampleTimeUs = 0L

        try {
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0) break
                if (firstSampleTimeUs == null) {
                    firstSampleTimeUs = sampleTimeUs
                }
                lastSampleTimeUs = sampleTimeUs

                bufferInfo.set(
                    0,
                    sampleSize,
                    timeOffsetUs + (sampleTimeUs - firstSampleTimeUs),
                    extractor.sampleFlags
                )
                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            extractor.release()
        }

        val first = firstSampleTimeUs ?: return 0L
        return (lastSampleTimeUs - first).coerceAtLeast(0L) + SAMPLE_DURATION_FALLBACK_US
    }

    private fun saveFileToGallery(file: File): Boolean {
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AICameraAssistant")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
        outputStream.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
        return true
    }

    private fun resetSession() {
        segments.forEach { if (it.file.exists()) it.file.delete() }
        segments.clear()
        pendingSegmentCount = 0
        nextSegmentOrder = 0
        finalStopRequested = false
        mergeInProgress = false
    }

    private companion object {
        const val SAMPLE_BUFFER_SIZE = 2 * 1024 * 1024
        const val SAMPLE_DURATION_FALLBACK_US = 33_333L
    }
}
