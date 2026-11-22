package com.rumput.vision.core.logging

import android.util.Log
import com.rumput.vision.BuildConfig

/**
 * Logcat wrapper: debug builds only, every message passed through [Redactor]. Warnings and errors are
 * also kept in the in-memory [DiagnosticsLog] (all builds) for the diagnostics screen.
 */
object AppLogger {
    private const val TAG = "RumputVision"

    fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, Redactor.redact(message()))
    }

    fun w(message: () -> String) {
        val text = Redactor.redact(message())
        DiagnosticsLog.record(DiagnosticsLog.Level.WARN, text)
        if (BuildConfig.DEBUG) Log.w(TAG, text)
    }

    /** Logs only the throwable's class name, never its message, which may embed URLs or payloads. */
    fun e(throwable: Throwable? = null, message: () -> String) {
        val suffix = throwable?.let { " (${it::class.java.simpleName})" }.orEmpty()
        val text = Redactor.redact(message()) + suffix
        DiagnosticsLog.record(DiagnosticsLog.Level.ERROR, text)
        if (BuildConfig.DEBUG) Log.e(TAG, text)
    }
}
