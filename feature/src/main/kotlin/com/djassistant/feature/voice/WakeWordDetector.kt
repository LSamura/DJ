package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow

/**
 * Detects an activation phrase in a live audio stream. Deliberately
 * decoupled from any specific engine (Vosk, Porcupine, OpenWakeWord, ...) —
 * Wake Mode's activation mechanism can be swapped later by providing a new
 * implementation of this interface, without touching the voice pipeline
 * orchestration or Media Layer at all.
 */
interface WakeWordDetector {
    /**
     * Suspends until the wake phrase is detected, or the underlying audio
     * flow ends (e.g. the engine is stopping). Returns false in the latter
     * case.
     */
    suspend fun waitForWakeWord(audioFlow: Flow<ByteArray>): Boolean
}
