package com.example.aicameraassistant

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView

fun getTargetRotation(context: Context, previewView: PreviewView? = null): Int {
    previewView?.display?.rotation?.let { rotation ->
        if (rotation.isSurfaceRotation()) return rotation
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation?.let { rotation ->
            if (rotation.isSurfaceRotation()) return rotation
        }
    }

    @Suppress("DEPRECATION")
    val windowRotation = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
        ?.defaultDisplay
        ?.rotation

    return windowRotation?.takeIf { it.isSurfaceRotation() } ?: Surface.ROTATION_0
}

fun isLandscape(context: Context): Boolean =
    context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

fun getResolutionForCurrentOrientation(context: Context): Size =
    if (isLandscape(context)) LANDSCAPE_PHOTO_SIZE else PORTRAIT_PHOTO_SIZE

fun getVideoResolutionForCurrentOrientation(context: Context): Size =
    if (isLandscape(context)) LANDSCAPE_VIDEO_SIZE else PORTRAIT_VIDEO_SIZE

fun getAnalysisResolutionForCurrentOrientation(context: Context): Size =
    if (isLandscape(context)) LANDSCAPE_ANALYSIS_SIZE else PORTRAIT_ANALYSIS_SIZE

fun shouldMirrorPreview(lensFacing: Int): Boolean =
    lensFacing == CameraSelector.LENS_FACING_FRONT

fun shouldMirrorPreview(lensFacing: String): Boolean =
    lensFacing.equals("front", ignoreCase = true)

private fun Int.isSurfaceRotation(): Boolean =
    this == Surface.ROTATION_0 ||
        this == Surface.ROTATION_90 ||
        this == Surface.ROTATION_180 ||
        this == Surface.ROTATION_270

private val PORTRAIT_PHOTO_SIZE = Size(1080, 1920)
private val LANDSCAPE_PHOTO_SIZE = Size(1920, 1080)
private val PORTRAIT_VIDEO_SIZE = Size(720, 1280)
private val LANDSCAPE_VIDEO_SIZE = Size(1280, 720)
private val PORTRAIT_ANALYSIS_SIZE = Size(240, 320)
private val LANDSCAPE_ANALYSIS_SIZE = Size(320, 240)
