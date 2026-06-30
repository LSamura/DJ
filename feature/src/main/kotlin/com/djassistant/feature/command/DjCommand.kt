package com.djassistant.feature.command

import com.djassistant.feature.intent.DjIntent

interface DjCommand {
    val intent: DjIntent
    val triggers: List<String>

    suspend fun execute(context: CommandContext): CommandResult
}
