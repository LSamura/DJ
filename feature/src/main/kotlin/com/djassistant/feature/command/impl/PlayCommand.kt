package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class PlayCommand : DjCommand {
    override val intent: DjIntent = DjIntent.Play
    override val triggers = listOf("играй", "продолжи", "воспроизведи", "play", "resume")

    override suspend fun execute(context: CommandContext): CommandResult {
        context.mediaRemote.play()
        return CommandResult.Success
    }
}
