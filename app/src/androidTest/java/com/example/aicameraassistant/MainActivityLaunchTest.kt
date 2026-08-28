package com.example.aicameraassistant

import android.view.ViewGroup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityLaunchTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesIntoOnboarding() {
        activityRule.scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue("Activity content was not attached", content.childCount > 0)
        }
    }
}
