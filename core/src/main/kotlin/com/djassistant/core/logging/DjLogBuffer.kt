package com.djassistant.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ring buffer of recent log records + the most recent error.
 *
 * Purely a diagnostic aid for the Debug screen — it holds no persistent state
 * and never touches disk. [DjLogger] feeds every record here in addition to
 * Timber so that the UI can surface "last error" / "last log entry" without a
 * logcat connection.
 */
object DjLogBuffer {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val level: Level,
        val tag: String,
        val message: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private const val MAX_ENTRIES = 100

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _lastError = MutableStateFlow<Entry?>(null)
    val lastError: StateFlow<Entry?> = _lastError.asStateFlow()

    @Synchronized
    fun record(level: Level, tag: String, message: String) {
        val entry = Entry(level, tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        if (level == Level.ERROR) {
            _lastError.value = entry
        }
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        _lastError.value = null
    }
}
