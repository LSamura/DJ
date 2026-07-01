package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class NextTrackCommand : DjCommand {
    override val intent: DjIntent = DjIntent.Next

    override suspend fun execute(context: CommandContext): CommandResult {
        context.mediaRemote.next()
        return CommandResult.Success
    }
}
