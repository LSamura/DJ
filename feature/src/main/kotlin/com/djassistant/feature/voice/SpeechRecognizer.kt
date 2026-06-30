package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow

interface SpeechRecognizer {
    val isReady: Boolean

    fun startListening(audioFlow: Flow<ByteArray>): Flow<RecognitionResult>
    fun release()
}
