package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class PauseCommand : DjCommand {
    override val intent: DjIntent = DjIntent.Pause
    override val triggers = listOf("пауза", "стоп", "остановить", "pause", "stop")

    override suspend fun execute(context: CommandContext): CommandResult {
        context.mediaRemote.pause()
        return CommandResult.Success
    }
}
