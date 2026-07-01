package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudioRecorder {
    val audioLevel: StateFlow<Float>
    val isRecording: StateFlow<Boolean>

    /** Human-readable name of the input device currently in use (e.g. "Bluetooth", "Встроенный микрофон", "—"). */
    val activeInputSource: StateFlow<String>

    fun start(): Flow<ByteArray>
    fun stop()
}
