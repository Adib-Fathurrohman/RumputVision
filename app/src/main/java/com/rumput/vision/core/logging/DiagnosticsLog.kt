package com.rumput.vision.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The most recent warnings and errors, kept in memory only (never written to disk or logcat in release
 * builds) so the diagnostics screen can show what went wrong. Messages arrive already redacted by
 * [AppLogger]; the diagnostics report masks hosts and addresses on top of that.
 */
object DiagnosticsLog {
    const val CAPACITY = 50

    enum class Level { WARN, ERROR }

    data class Entry(val epochMillis: Long, val level: Level, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(level: Level, message: String, epochMillis: Long = System.currentTimeMillis()) {
        _entries.update { (it + Entry(epochMillis, level, message.take(MAX_MESSAGE_LENGTH))).takeLast(CAPACITY) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private const val MAX_MESSAGE_LENGTH = 300
}
