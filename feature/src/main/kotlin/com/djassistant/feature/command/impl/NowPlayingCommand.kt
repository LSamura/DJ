package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class NowPlayingCommand : DjCommand {
    override val intent: DjIntent = DjIntent.QueryNowPlaying
    override val triggers = listOf("что играет", "что за песня", "название", "что сейчас")

    override suspend fun execute(context: CommandContext): CommandResult {
        val state = context.stateProvider.getSnapshot()
        val title = state.trackTitle
        return if (title != null) {
            CommandResult.SuccessWithInfo("Играет: $title")
        } else {
            CommandResult.SuccessWithInfo("Название неизвестно — нет доступа к сессии")
        }
    }
}
