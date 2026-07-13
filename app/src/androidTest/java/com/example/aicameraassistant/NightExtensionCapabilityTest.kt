package com.example.aicameraassistant

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightExtensionCapabilityTest {
    @Test
    fun nightCapabilityCanBeQueriedForFrontAndBackLenses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ProcessCameraProvider.getInstance(context).get()
        val manager = ExtensionsManager.getInstanceAsync(context, provider).get()

        listOf(
            CameraSelector.LENS_FACING_BACK,
            CameraSelector.LENS_FACING_FRONT
        ).forEach { lensFacing ->
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val available = manager.isExtensionAvailable(selector, ExtensionMode.NIGHT)
            Log.i("NIGHT_EXTENSION_TEST", "lens=$lensFacing available=$available")
        }
    }
}
