package com.example.aicameraassistant

import android.content.pm.PackageManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test fun cameraCapableDeviceLaunchesAndSurvivesActivityRecreation() {
        activityRule.scenario.onActivity { activity ->
            assertTrue(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
            assertFalse(activity.isFinishing)
        }
        activityRule.scenario.recreate()
        activityRule.scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
    }
}
