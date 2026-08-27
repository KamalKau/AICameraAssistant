package com.example.aicameraassistant

import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device smoke coverage. Full lens/capture validation requires granted permissions. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CameraLifecycleInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun cameraCapableDeviceLaunchesAndSurvivesActivityRecreation() {
        val packageManager = composeRule.activity.packageManager
        assertTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        assertFalse(composeRule.activity.isFinishing)
        composeRule.activityRule.scenario.recreate()
        assertFalse(composeRule.activity.isFinishing)
    }
}
