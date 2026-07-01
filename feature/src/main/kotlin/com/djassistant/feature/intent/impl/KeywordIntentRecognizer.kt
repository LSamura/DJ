package com.djassistant.feature.intent.impl

import com.djassistant.core.extensions.stripWakeWord
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.intent.IntentRecognizer
import com.djassistant.feature.voice.impl.VoiceCommandConfigLoader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dictionary-based Intent Parser: matches normalized text against the
 * phrase lists loaded from `assets/commands.json` by [VoiceCommandConfigLoader].
 * No wake word is required yet (Sprint 3 uses continuous listening) — the
 * text is passed through [stripWakeWord] regardless, so it is a no-op when
 * no prefix is present and matching keeps working unchanged once a future
 * sprint reintroduces an activation phrase gate in front of this recognizer.
 */
@Singleton
class KeywordIntentRecognizer @Inject constructor(
    private val configLoader: VoiceCommandConfigLoader
) : IntentRecognizer {

    override fun recognize(commandText: String): DjIntent {
        val normalized = commandText.stripWakeWord()
        DjLogger.intent("Recognizing: \"$normalized\"")

        if (normalized.isBlank()) {
            return DjIntent.Unknown(commandText)
        }

        val matchedKey = configLoader.phrasesByIntent().entries.firstOrNull { (_, phrases) ->
            phrases.any { phrase -> normalized == phrase || normalized.contains(phrase) }
        }?.key

        val result = matchedKey?.let { toDjIntent(it, commandText) } ?: DjIntent.Unknown(commandText)
        DjLogger.intent("Result: ${result::class.simpleName}")
        return result
    }

    // Adding a new command = one JSON entry in commands.json + one branch here.
    private fun toDjIntent(key: String, rawText: String): DjIntent = when (key) {
        "PAUSE" -> DjIntent.Pause
        "PLAY" -> DjIntent.Play
        "NEXT" -> DjIntent.Next
        "PREVIOUS" -> DjIntent.Previous
        "QUERY_NOW_PLAYING" -> DjIntent.QueryNowPlaying
        else -> DjIntent.Unknown(rawText)
    }
}
