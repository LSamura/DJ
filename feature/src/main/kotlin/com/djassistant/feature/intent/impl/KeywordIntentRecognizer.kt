package com.djassistant.feature.intent.impl

import com.djassistant.core.extensions.stripWakeWord
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.intent.IntentRecognizer
import com.djassistant.feature.voice.TextNormalizer
import com.djassistant.feature.voice.impl.VoiceCommandConfigLoader
import javax.inject.Inject
import javax.inject.Singleton

private val VOLUME_PERCENT_REGEX = Regex("громкост[а-я]*\\s+(?:на\\s+)?(\\d{1,3})\\s*процент")

/**
 * Dictionary-based Intent Parser: matches normalized text against the
 * phrase lists loaded from `assets/commands.json` by [VoiceCommandConfigLoader].
 * A small regex ([VOLUME_PERCENT_REGEX]) handles the one parameterized
 * command ("громкость 50 процентов") that a static phrase list can't
 * express — still no LLM/NN, matching the allowed "dictionary + regex +
 * normalization + synonyms" toolbox.
 *
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
        val normalized = TextNormalizer.normalize(commandText.stripWakeWord())
        DjLogger.intent("Recognizing: \"$normalized\"")

        if (normalized.isBlank()) {
            return DjIntent.Unknown(commandText)
        }

        VOLUME_PERCENT_REGEX.find(normalized)?.let { match ->
            val percent = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
            if (percent != null) {
                DjLogger.intent("Result: SetVolumePercent($percent)")
                return DjIntent.SetVolumePercent(percent)
            }
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
        "VOLUME_UP" -> DjIntent.VolumeUp
        "VOLUME_DOWN" -> DjIntent.VolumeDown
        "VOLUME_MAX" -> DjIntent.SetVolumeMax
        "VOLUME_MIN" -> DjIntent.SetVolumeMin
        "MODE_CONTINUOUS" -> DjIntent.SetContinuousMode
        "MODE_WAKE" -> DjIntent.SetWakeMode
        else -> DjIntent.Unknown(rawText)
    }
}
