package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class VolumeQueryCommand : DjCommand {
    override val intent: DjIntent = DjIntent.QueryVolume

    override suspend fun execute(context: CommandContext): CommandResult {
        val current = context.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = context.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = if (max > 0) (current * 100 / max) else 0
        return CommandResult.SuccessWithInfo("Громкость: $percent%")
    }
}
