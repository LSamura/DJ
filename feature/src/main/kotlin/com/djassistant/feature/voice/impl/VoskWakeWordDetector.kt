package com.djassistant.feature.voice.impl

import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.SpeechRecognizer
import com.djassistant.feature.voice.WakeWordDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private val WAKE_PHRASES = listOf("диджей", "джей", "dj")

/**
 * Reuses the existing Vosk engine with a tiny restricted grammar (just the
 * wake phrases) instead of the full command vocabulary — much cheaper to
 * run continuously than full command recognition, and the practical middle
 * ground until a dedicated engine (Porcupine / OpenWakeWord) is wired in
 * behind the same [WakeWordDetector] interface.
 */
@Singleton
class VoskWakeWordDetector @Inject constructor(
    private val speechRecognizer: SpeechRecognizer
) : WakeWordDetector {

    override suspend fun waitForWakeWord(audioFlow: Flow<ByteArray>): Boolean {
        DjLogger.voice("Listening for wake word...")
        val result = try {
            speechRecognizer.startListening(audioFlow, vocabulary = WAKE_PHRASES)
                .first { it.isFinal && it.text.isNotBlank() }
        } catch (e: NoSuchElementException) {
            // Audio flow ended without a match (e.g. engine stopping).
            return false
        }
        val detected = WAKE_PHRASES.any { result.text.contains(it) }
        if (detected) DjLogger.voice("Wake word detected: \"${result.text}\"")
        return detected
    }
}
