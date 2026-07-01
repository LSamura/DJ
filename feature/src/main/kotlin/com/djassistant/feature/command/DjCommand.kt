package com.djassistant.feature.command

import com.djassistant.feature.intent.DjIntent

interface DjCommand {
    val intent: DjIntent

    suspend fun execute(context: CommandContext): CommandResult
}
