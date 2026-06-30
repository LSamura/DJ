package com.djassistant.feature.intent.impl

import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.command.CommandRegistry
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.intent.IntentRecognizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordIntentRecognizer @Inject constructor(
    private val registry: CommandRegistry
) : IntentRecognizer {

    override fun recognize(commandText: String): DjIntent {
        val normalized = commandText.lowercase().trim()
        DjLogger.intent("Recognizing: \"$normalized\"")

        if (normalized == "[unk]" || normalized.isBlank()) {
            return DjIntent.Unknown(commandText)
        }

        val command = registry.findByTrigger(normalized)
        val result = command?.intent ?: DjIntent.Unknown(commandText)
        DjLogger.intent("Result: ${result::class.simpleName}")
        return result
    }
}
