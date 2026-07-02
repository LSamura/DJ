package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow

interface SpeechRecognizer {
    val isReady: Boolean

    /**
     * Recognizes command speech using the full command grammar built from
     * `commands.json` (see [GrammarBuilder]). Sprint 4: wake-word spotting
     * moved to a dedicated [WakeWordEngine] (Porcupine), so this is now the
     * only grammar Vosk ever runs — there is no restricted-vocabulary mode
     * to select here anymore.
     */
    fun startListening(audioFlow: Flow<ByteArray>): Flow<RecognitionResult>
    fun release()
}
