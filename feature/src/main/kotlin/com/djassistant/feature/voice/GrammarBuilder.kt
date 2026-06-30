package com.djassistant.feature.voice

import com.djassistant.feature.command.CommandRegistry
import javax.inject.Inject

class GrammarBuilder @Inject constructor(
    private val registry: CommandRegistry
) {
    fun build(): String {
        val phrases = registry.allCommands
            .flatMap { cmd -> cmd.triggers.map { trigger -> "диджей $trigger" } }
            .distinct()
        val items = (phrases + "[unk]").joinToString(",") { "\"$it\"" }
        return "[$items]"
    }
}
