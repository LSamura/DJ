package com.djassistant.feature.command

import android.content.Context
import android.media.AudioManager
import com.djassistant.core.logging.DjLogger
import com.djassistant.data.log.UnknownCommandEntry
import com.djassistant.data.log.UnknownCommandLogger
import com.djassistant.feature.feedback.FeedbackManager
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.media.MediaRemote
import com.djassistant.feature.media.MediaStateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CommandRegistry,
    private val mediaRemote: MediaRemote,
    private val stateProvider: MediaStateProvider,
    private val feedbackManager: FeedbackManager,
    private val unknownCommandLogger: UnknownCommandLogger
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun dispatch(intent: DjIntent, rawText: String): CommandResult {
        DjLogger.command("Dispatching intent=${intent::class.simpleName} text=\"$rawText\"")

        if (intent is DjIntent.Unknown) {
            unknownCommandLogger.log(UnknownCommandEntry(rawText, "[unk]"))
            feedbackManager.onError()
            return CommandResult.NotSupported(intent)
        }

        val command = registry.findByIntent(intent)
            ?: return CommandResult.NotSupported(intent).also {
                feedbackManager.onError()
                DjLogger.command("No handler for intent=${intent::class.simpleName}")
            }

        val ctx = CommandContext(
            mediaRemote = mediaRemote,
            stateProvider = stateProvider,
            audioManager = audioManager,
            rawText = rawText
        )

        return command.execute(ctx).also { result ->
            when (result) {
                is CommandResult.Success -> feedbackManager.onSuccess()
                is CommandResult.SuccessWithInfo -> feedbackManager.onSuccess()
                is CommandResult.Failure -> feedbackManager.onError()
                is CommandResult.NotSupported -> feedbackManager.onError()
            }
            DjLogger.command("Result: $result")
        }
    }
}
