package com.example.aicameraassistant

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppLoggerTest {
    private val events = mutableListOf<LogEvent>()

    @Before fun setUp() { AppLogger.installForTesting(events::add) }
    @After fun tearDown() { AppLogger.resetAfterTesting() }

    @Test fun importantFailuresHaveCorrectCategoriesAndErrorLevel() {
        AppLogger.error(LogCategory.CAMERA, "Camera bind failed")
        AppLogger.error(LogCategory.CAPTURE, "Capture failed")
        AppLogger.error(LogCategory.WEBRTC, "WebRTC initialization failed")
        AppLogger.error(LogCategory.WEBRTC, "ICE failed")
        AppLogger.error(LogCategory.FIREBASE, "Room update failed")

        assertEquals(
            listOf(LogCategory.CAMERA, LogCategory.CAPTURE, LogCategory.WEBRTC, LogCategory.WEBRTC, LogCategory.FIREBASE),
            events.map(LogEvent::category)
        )
        assertTrue(events.all { it.level == LogLevel.ERROR })
    }

    @Test fun releaseBuildSuppressesDebugButKeepsOtherLevels() {
        AppLogger.installForTesting(events::add, isDebugBuild = false)
        AppLogger.debug(LogCategory.UI, "debug")
        AppLogger.info(LogCategory.UI, "info")
        AppLogger.warning(LogCategory.UI, "warning")
        AppLogger.error(LogCategory.UI, "error")
        assertEquals(listOf(LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR), events.map(LogEvent::level))
    }

    @Test fun throwableIsPreservedAsStructuredMetadata() {
        val failure = IllegalStateException("failure")
        AppLogger.error(LogCategory.CAPTURE, "Capture failed", failure)
        assertSame(failure, events.single().throwable)
    }

    @Test fun sensitiveAssignmentsAreRedactedBeforeTheyReachSink() {
        AppLogger.info(LogCategory.SESSION, "room=123456 token=secret candidate=private-sdp")
        val message = events.single().message
        assertFalse(message.contains("123456")); assertFalse(message.contains("secret")); assertFalse(message.contains("private-sdp"))
    }
}
