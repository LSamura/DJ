package com.djassistant.feature.command.impl

import android.media.AudioManager
import com.djassistant.feature.command.CommandContext
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.command.DjCommand
import com.djassistant.feature.intent.DjIntent

/**
 * The only parameterized MVP command — the percentage comes from the
 * actually-matched [DjIntent.SetVolumePercent] instance carried on
 * [CommandContext.intent], not from this class's own `intent` template
 * (used only for type-based lookup in [com.djassistant.feature.command.CommandRegistry]).
 */
class SetVolumePercentCommand : DjCommand {
    override val intent: DjIntent = DjIntent.SetVolumePercent(0)

    override suspend fun execute(context: CommandContext): CommandResult {
        val percent = (context.intent as? DjIntent.SetVolumePercent)?.percent
            ?: return CommandResult.Failure("Не удалось определить процент громкости")

        return try {
            val max = context.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * percent / 100).coerceIn(0, max)
            context.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            CommandResult.Success
        } catch (e: Exception) {
            CommandResult.Failure(e.message ?: "не удалось изменить громкость")
        }
    }
}
