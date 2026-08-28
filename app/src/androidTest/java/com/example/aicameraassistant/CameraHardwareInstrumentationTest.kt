package com.example.aicameraassistant

import android.Manifest
import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** CameraX validation intended for a real camera-capable Android device. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CameraHardwareInstrumentationTest {
    private val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissionRule).around(activityRule)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val provider: ProcessCameraProvider
        get() = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)

    @After
    fun releaseCamera() {
        instrumentation.runOnMainSync { provider.unbindAll() }
    }

    @Test
    fun rearFrontControlsCaptureReleaseAndRestart() {
        assertTrue(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        assertTrue(provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))

        val rearCapture = ImageCapture.Builder().build()
        val rearCamera = bind(CameraSelector.DEFAULT_BACK_CAMERA, rearCapture)
        exerciseZoom(rearCamera)
        exerciseTorchWhenSupported(rearCamera)
        capturePhoto(rearCapture)

        val frontCamera = bind(CameraSelector.DEFAULT_FRONT_CAMERA, ImageCapture.Builder().build())
        assertNotNull(frontCamera)

        val returnedRear = bind(CameraSelector.DEFAULT_BACK_CAMERA, ImageCapture.Builder().build())
        assertNotNull(returnedRear)

        instrumentation.runOnMainSync { provider.unbindAll() }
        val restartedRear = bind(CameraSelector.DEFAULT_BACK_CAMERA, ImageCapture.Builder().build())
        assertNotNull(restartedRear)
    }

    private fun bind(selector: CameraSelector, capture: ImageCapture): Camera {
        val result = AtomicReference<Camera>()
        lateinit var activity: MainActivity
        activityRule.scenario.onActivity { activity = it }
        instrumentation.runOnMainSync {
            provider.unbindAll()
            result.set(provider.bindToLifecycle(activity, selector, capture))
        }
        return requireNotNull(result.get())
    }

    private fun exerciseZoom(camera: Camera) {
        val zoomState = requireNotNull(camera.cameraInfo.zoomState.value)
        val target = min(2f, zoomState.maxZoomRatio).coerceAtLeast(zoomState.minZoomRatio)
        camera.cameraControl.setZoomRatio(target).get(5, TimeUnit.SECONDS)
        val applied = requireNotNull(camera.cameraInfo.zoomState.value).zoomRatio
        assertTrue(kotlin.math.abs(applied - target) < 0.05f)
    }

    private fun exerciseTorchWhenSupported(camera: Camera) {
        if (!camera.cameraInfo.hasFlashUnit()) return
        camera.cameraControl.enableTorch(true).get(5, TimeUnit.SECONDS)
        assertTrue(camera.cameraInfo.torchState.value == 1)
        camera.cameraControl.enableTorch(false).get(5, TimeUnit.SECONDS)
        assertTrue(camera.cameraInfo.torchState.value == 0)
    }

    private fun capturePhoto(capture: ImageCapture) {
        val output = File.createTempFile("camera-instrumentation-", ".jpg", context.cacheDir)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<ImageCaptureException?>()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) = completed.countDown()
                override fun onError(exception: ImageCaptureException) {
                    failure.set(exception)
                    completed.countDown()
                }
            }
        )
        assertTrue("Capture timed out", completed.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("CameraX capture failed", it) }
        assertTrue("Captured JPEG is empty", output.length() > 0L)
        output.delete()
    }
}
