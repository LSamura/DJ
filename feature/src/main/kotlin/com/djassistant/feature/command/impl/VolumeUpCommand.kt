package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class VolumeUpCommand : DjCommand {
    override val intent: DjIntent = DjIntent.VolumeUp

    override suspend fun execute(context: CommandContext): CommandResult {
        context.audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        return CommandResult.Success
    }
}
