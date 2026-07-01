package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class VolumeDownCommand : DjCommand {
    override val intent: DjIntent = DjIntent.VolumeDown

    override suspend fun execute(context: CommandContext): CommandResult {
        context.audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        return CommandResult.Success
    }
}
