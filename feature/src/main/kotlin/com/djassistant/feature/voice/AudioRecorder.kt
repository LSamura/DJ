package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudioRecorder {
    val audioLevel: StateFlow<Float>
    val isRecording: StateFlow<Boolean>

    /** Human-readable name of the input device currently in use (e.g. "Bluetooth", "Встроенный микрофон", "—"). */
    val activeInputSource: StateFlow<String>

    /**
     * @param allowBluetooth When false (the default), this session never
     * touches Bluetooth SCO at all — only the phone's built-in microphone is
     * used. When true, a connected Bluetooth headset's microphone is used if
     * available (SCO is activated right before recording starts and torn
     * down as soon as this flow's collection ends), falling back to the
     * phone microphone otherwise. Callers decide per-session whether
     * Bluetooth is actually wanted (e.g. only while actively recording a
     * voice command, never while idly waiting for a wake word).
     */
    fun start(allowBluetooth: Boolean = false): Flow<ByteArray>
    fun stop()
}
