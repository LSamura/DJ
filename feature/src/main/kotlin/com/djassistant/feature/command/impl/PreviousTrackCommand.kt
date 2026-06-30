package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class PreviousTrackCommand : DjCommand {
    override val intent: DjIntent = DjIntent.Previous
    override val triggers = listOf("предыдущий", "предыдущая", "назад", "previous", "back", "предыдущий трек")

    override suspend fun execute(context: CommandContext): CommandResult {
        context.mediaRemote.previous()
        return CommandResult.Success
    }
}
