package com.djassistant.feature.command.impl

import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class ArtistCommand : DjCommand {
    override val intent: DjIntent = DjIntent.QueryArtist
    override val triggers = listOf("кто исполнитель", "чья песня", "кто поёт", "исполнитель")

    override suspend fun execute(context: CommandContext): CommandResult {
        val state = context.stateProvider.getSnapshot()
        val artist = state.artist
        return if (artist != null) {
            CommandResult.SuccessWithInfo("Исполнитель: $artist")
        } else {
            CommandResult.SuccessWithInfo("Исполнитель неизвестен — нет доступа к сессии")
        }
    }
}
