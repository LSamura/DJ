package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudioRecorder {
    val audioLevel: StateFlow<Float>
    val isRecording: StateFlow<Boolean>

    fun start(): Flow<ByteArray>
    fun stop()
}
