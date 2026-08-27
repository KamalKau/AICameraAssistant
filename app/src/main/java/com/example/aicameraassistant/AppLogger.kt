package com.example.aicameraassistant

import android.util.Log

enum class LogCategory {
    CAMERA,
    CAPTURE,
    WEBRTC,
    FIREBASE,
    NETWORK,
    SESSION,
    UI
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class LogEvent(
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
    val throwable: Throwable? = null
)

fun interface LogEventSink {
    fun log(event: LogEvent)
}

object AppLogger {
    private const val TAG_PREFIX = "AICAMERA"

    @Volatile
    private var sink: LogEventSink = AndroidLogEventSink

    @Volatile
    private var debugBuildProvider: () -> Boolean = { BuildConfig.DEBUG }

    fun debug(category: LogCategory, message: String) =
        emit(LogEvent(category, LogLevel.DEBUG, message))

    fun info(category: LogCategory, message: String) =
        emit(LogEvent(category, LogLevel.INFO, message))

    fun warning(category: LogCategory, message: String) =
        emit(LogEvent(category, LogLevel.WARNING, message))

    fun error(category: LogCategory, message: String, throwable: Throwable? = null) =
        emit(LogEvent(category, LogLevel.ERROR, message, throwable))

    // These overloads keep migrations from legacy Android Log calls mechanical. The
    // former tag is deliberately discarded: category tags are the single filter key.
    fun debug(category: LogCategory, legacyTag: String, message: String) = debug(category, message)
    fun info(category: LogCategory, legacyTag: String, message: String) = info(category, message)
    fun warning(category: LogCategory, legacyTag: String, message: String) = warning(category, message)
    fun warning(category: LogCategory, legacyTag: String, message: String, throwable: Throwable?) =
        emit(LogEvent(category, LogLevel.WARNING, message, throwable))
    fun error(category: LogCategory, legacyTag: String, message: String) = error(category, message)
    fun error(category: LogCategory, legacyTag: String, message: String, throwable: Throwable?) =
        error(category, message, throwable)

    private fun emit(event: LogEvent) {
        if (event.level == LogLevel.DEBUG && !debugBuildProvider()) return
        sink.log(event.copy(message = SensitiveLogSanitizer.sanitize(event.message)))
    }

    internal fun installForTesting(
        testSink: LogEventSink,
        isDebugBuild: Boolean = true
    ) {
        sink = testSink
        debugBuildProvider = { isDebugBuild }
    }

    internal fun resetAfterTesting() {
        sink = AndroidLogEventSink
        debugBuildProvider = { BuildConfig.DEBUG }
    }

    private object AndroidLogEventSink : LogEventSink {
        override fun log(event: LogEvent) {
            val tag = "$TAG_PREFIX-${event.category.name}"
            val message = "[$TAG_PREFIX][${event.category.name}] ${event.message}"
            when (event.level) {
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARNING -> Log.w(tag, message, event.throwable)
                LogLevel.ERROR -> Log.e(tag, message, event.throwable)
            }
        }
    }
}

internal object SensitiveLogSanitizer {
    private val sensitiveAssignments = Regex(
        pattern = "(?i)\\b(room|roomCode|rtc|rtcSessionId|session|token|username|password|candidate|sdp)\\s*[=:]\\s*[^\\s,]+"
    )

    fun sanitize(message: String): String = sensitiveAssignments.replace(message) { match ->
        "${match.groupValues[1]}=[REDACTED]"
    }
}
