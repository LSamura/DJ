package com.djassistant.service.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.command.CommandDispatcher
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.intent.IntentRecognizer
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.feature.voice.RecognitionResult
import com.djassistant.feature.voice.SpeechRecognizer
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates the continuous offline voice pipeline:
 *
 * Microphone (AudioRecorder) -> Vosk (SpeechRecognizer) -> Intent Parser
 * (IntentRecognizer) -> Media Layer (CommandDispatcher, existing since
 * Sprint 1/2).
 *
 * This class never touches Media Layer types directly — it only calls
 * [CommandDispatcher.dispatch], the same public entry point Sprint 1/2 code
 * already used. Runs entirely on [Dispatchers.IO] in its own supervised
 * scope so neither audio capture nor Vosk's blocking native calls ever
 * touch the main thread.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val speechRecognizer: SpeechRecognizer,
    private val intentRecognizer: IntentRecognizer,
    private val commandDispatcher: CommandDispatcher,
    private val serviceStateHolder: ServiceStateHolder,
    private val voiceStateHolder: VoiceStateHolder
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) {
            DjLogger.voice("VoiceEngine.start() ignored — already running")
            return
        }
        job = engineScope.launch { runPipeline() }
    }

    fun stop() {
        DjLogger.voice("VoiceEngine.stop()")
        job?.cancel()
        job = null
        audioRecorder.stop()
        speechRecognizer.release()
        voiceStateHolder.setModelLoaded(false)
        voiceStateHolder.updateEngineState(VoiceEngineState.Stopped)
    }

    private suspend fun runPipeline() {
        voiceStateHolder.updateEngineState(VoiceEngineState.Initializing)

        if (!hasRecordAudioPermission()) {
            DjLogger.voiceError("RECORD_AUDIO permission not granted — voice pipeline not started")
            voiceStateHolder.updateEngineState(VoiceEngineState.Error("Нет разрешения на микрофон"))
            return
        }

        val audioFlow = audioRecorder.start()
        voiceStateHolder.updateEngineState(VoiceEngineState.Listening)
        serviceStateHolder.updateMode(ServiceMode.Listening)

        speechRecognizer.startListening(audioFlow).collect { result ->
            voiceStateHolder.setModelLoaded(true)
            if (!result.isFinal || result.text.isBlank()) return@collect
            handleRecognition(result)
        }

        // The flow completed without ever emitting — most likely the model
        // could not be loaded (see VoskModelProvisioner logs for the reason).
        if (!speechRecognizer.isReady) {
            DjLogger.voiceError("Voice pipeline ended without a loaded model")
            voiceStateHolder.setModelLoaded(false)
            voiceStateHolder.updateEngineState(VoiceEngineState.Error("Модель Vosk не найдена"))
            serviceStateHolder.updateMode(ServiceMode.Error("Vosk модель не найдена"))
        }
    }

    private suspend fun handleRecognition(result: RecognitionResult) {
        serviceStateHolder.updateMode(ServiceMode.Recognizing)
        val startTime = SystemClock.elapsedRealtime()

        val intent = intentRecognizer.recognize(result.text)
        serviceStateHolder.updateMode(ServiceMode.Processing)
        val commandResult = commandDispatcher.dispatch(intent, result.text)

        val elapsedMs = SystemClock.elapsedRealtime() - startTime

        voiceStateHolder.recordRecognition(
            recognizedText = result.text,
            confidence = result.confidence,
            intentLabel = intent.label(),
            actionLabel = commandResult.actionLabel(intent),
            processingTimeMs = elapsedMs
        )
        serviceStateHolder.updateLastRecognizedText(result.text)
        serviceStateHolder.updateMode(ServiceMode.Listening)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun DjIntent.label(): String = when (this) {
        is DjIntent.Pause -> "PAUSE"
        is DjIntent.Play -> "PLAY"
        is DjIntent.Next -> "NEXT"
        is DjIntent.Previous -> "PREVIOUS"
        is DjIntent.Stop -> "STOP"
        is DjIntent.VolumeUp -> "VOLUME_UP"
        is DjIntent.VolumeDown -> "VOLUME_DOWN"
        is DjIntent.QueryNowPlaying -> "QUERY_NOW_PLAYING"
        is DjIntent.QueryArtist -> "QUERY_ARTIST"
        is DjIntent.QueryIsPlaying -> "QUERY_IS_PLAYING"
        is DjIntent.QueryVolume -> "QUERY_VOLUME"
        is DjIntent.Unknown -> "UNKNOWN"
    }

    private fun CommandResult.actionLabel(intent: DjIntent): String = when (this) {
        is CommandResult.Success -> intent.label()
        is CommandResult.SuccessWithInfo -> intent.label()
        is CommandResult.Failure -> "None"
        is CommandResult.NotSupported -> "None"
    }
}
