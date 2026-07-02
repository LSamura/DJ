package com.djassistant.feature.voice

import kotlinx.coroutines.flow.Flow

/**
 * Detects an activation phrase in a live audio stream.
 *
 * Sprint 4: this replaces the Sprint 3.x approach of (ab)using Vosk with a
 * restricted grammar for wake-word spotting. That approach required a full
 * Vosk `Recognizer` to stay alive for as long as Wake Mode was idle, which
 * kept the microphone indicator lit continuously and made the Bluetooth SCO
 * lifecycle harder to reason about. [WakeWordEngine] is a dedicated,
 * cheaper detector (see [com.djassistant.feature.voice.impl.PorcupineWakeWordEngine])
 * — Vosk is now used exclusively for command recognition inside an already-open
 * Listening Window, never for idle wake-word spotting.
 *
 * Deliberately decoupled from any specific engine so the detection mechanism
 * can be swapped (a different vendor, an on-device custom model, ...)
 * without touching [com.djassistant.service.voice.VoiceEngine] or the Media
 * Layer at all — see [com.djassistant.feature.voice.impl.MockWakeWordEngine]
 * for the test double used to prove that substitutability.
 */
interface WakeWordEngine {
    /**
     * True once the engine has everything it needs to actually detect the
     * currently configured [WakeWordPhrase] (e.g. a valid Porcupine access
     * key and the trained keyword/model files for that phrase). When false,
     * [waitForWakeWord] will never detect anything — callers should treat
     * this the same way [com.djassistant.feature.voice.SpeechRecognizer.isReady]
     * being false is treated for Vosk: log it, surface it on Debug Screen,
     * back off instead of busy-looping.
     */
    val isReady: Boolean

    /**
     * Suspends until the currently configured wake phrase is detected, or
     * the underlying audio flow ends (e.g. the engine is stopping). Returns
     * false in the latter case, or immediately if [isReady] is false.
     */
    suspend fun waitForWakeWord(audioFlow: Flow<ByteArray>): Boolean

    /** Releases any native resources held by the engine. Safe to call repeatedly, including when nothing is loaded. */
    fun release()
}
