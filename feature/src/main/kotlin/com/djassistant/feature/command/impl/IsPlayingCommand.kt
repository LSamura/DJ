package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class IsPlayingCommand : DjCommand {
    override val intent: DjIntent = DjIntent.QueryIsPlaying

    override suspend fun execute(context: CommandContext): CommandResult {
        val isActive = context.audioManager.isMusicActive
        return CommandResult.SuccessWithInfo(
            if (isActive) "Музыка играет" else "Музыка остановлена"
        )
    }
}
