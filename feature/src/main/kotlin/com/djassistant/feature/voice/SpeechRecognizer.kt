package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow

interface SpeechRecognizer {
    val isReady: Boolean

    /**
     * @param vocabulary Optional restricted word list (Grammar Mode) to use
     * instead of the default full command grammar — e.g. a wake-word-only
     * vocabulary, much cheaper to run continuously than full command
     * recognition (see [WakeWordDetector]). Null uses the default grammar.
     */
    fun startListening(audioFlow: Flow<ByteArray>, vocabulary: List<String>? = null): Flow<RecognitionResult>
    fun release()
}
