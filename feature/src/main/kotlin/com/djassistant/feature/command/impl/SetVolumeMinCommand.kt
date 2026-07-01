package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

class SetVolumeMinCommand : DjCommand {
    override val intent: DjIntent = DjIntent.SetVolumeMin

    override suspend fun execute(context: CommandContext): CommandResult = try {
        context.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        CommandResult.Success
    } catch (e: Exception) {
        CommandResult.Failure(e.message ?: "не удалось изменить громкость")
    }
}
