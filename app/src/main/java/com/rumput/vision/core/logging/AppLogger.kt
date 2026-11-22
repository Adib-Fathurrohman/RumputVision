package com.rumput.vision.core.logging

import android.util.Log
import com.rumput.vision.BuildConfig

/** Logcat wrapper: debug builds only, every message passed through [Redactor]. */
object AppLogger {
    private const val TAG = "RumputVision"

    fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, Redactor.redact(message()))
    }

    fun w(message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(TAG, Redactor.redact(message()))
    }

    /** Logs only the throwable's class name, never its message, which may embed URLs or payloads. */
    fun e(throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) {
            val suffix = throwable?.let { " (${it::class.java.simpleName})" }.orEmpty()
            Log.e(TAG, Redactor.redact(message()) + suffix)
        }
    }
}
