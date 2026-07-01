package com.djassistant.service.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.djassistant.core.extensions.stripWakeWord
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.command.CommandDispatcher
import com.djassistant.feature.command.CommandResult
import com.djassistant.feature.intent.DjIntent
import com.djassistant.feature.intent.IntentRecognizer
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.RecognitionResult
import com.djassistant.feature.voice.SpeechRecognizer
import com.djassistant.feature.voice.TextNormalizer
import com.djassistant.feature.voice.VoiceListeningMode
import com.djassistant.feature.voice.WakeWordDetector
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
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
 *
 * Supports two listening modes (Sprint 3.1):
 *  - [VoiceListeningMode.CONTINUOUS] — full command grammar always running
 *    (Sprint 3 behavior).
 *  - [VoiceListeningMode.WAKE_WORD] — a cheap [WakeWordDetector] listens for
 *    the activation phrase; once triggered, full command recognition opens
 *    for a configurable dialog window and resets on every recognized
 *    utterance, then falls back to wake-word listening.
 *
 * Mode-switch intents (SetContinuousMode/SetWakeMode) are handled here and
 * never reach [CommandDispatcher] — they are a Voice Layer concern, not a
 * Media Layer one.
 *
 * Bluetooth microphone lifecycle (Sprint 3.1 follow-up): the wake-word
 * waiting phase is a genuinely idle state and always uses the phone
 * microphone — it never opens a Bluetooth SCO connection. Only the "actually
 * recording a command" phase (the dialog window in Wake Mode, or the whole
 * session in Continuous Mode) opens SCO, and only when the user explicitly
 * selected [MicrophoneSource.BLUETOOTH]; it is closed the moment that
 * phase ends. See ADR in DECISIONS.md.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val speechRecognizer: SpeechRecognizer,
    private val wakeWordDetector: WakeWordDetector,
    private val intentRecognizer: IntentRecognizer,
    private val commandDispatcher: CommandDispatcher,
    private val settingsRepository: SettingsRepository,
    private val serviceStateHolder: ServiceStateHolder,
    private val voiceStateHolder: VoiceStateHolder
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile private var currentSettings = DjSettings()
    @Volatile private var currentListeningMode = VoiceListeningMode.CONTINUOUS

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

        currentSettings = settingsRepository.settings.first()
        currentListeningMode = currentSettings.listeningMode
        engineScope.launch {
            settingsRepository.settings.collect { currentSettings = it }
        }

        while (engineScope.isActive) {
            when (currentListeningMode) {
                VoiceListeningMode.CONTINUOUS -> runContinuousSession()
                VoiceListeningMode.WAKE_WORD -> runWakeWordSession()
            }
        }
    }

    /** Only an explicit Bluetooth selection ever opens SCO — AUTO/PHONE always use the phone mic. */
    private fun resolveAllowBluetooth(): Boolean =
        currentSettings.microphoneSource == MicrophoneSource.BLUETOOTH

    private suspend fun runContinuousSession() {
        voiceStateHolder.updateEngineState(VoiceEngineState.Listening)
        serviceStateHolder.updateMode(ServiceMode.Listening)

        // Continuous mode has no idle/active split — the whole session IS
        // "recording a voice command", so the configured mic source (which
        // may open Bluetooth SCO) applies for its entire duration.
        val audioFlow = audioRecorder.start(allowBluetooth = resolveAllowBluetooth())

        var switchRequested = false
        speechRecognizer.startListening(audioFlow)
            .takeWhile { !switchRequested }
            .collect { result ->
                voiceStateHolder.setModelLoaded(true)
                if (!result.isFinal || result.text.isBlank()) return@collect
                val modeSwitch = handleRecognition(result)
                if (modeSwitch == VoiceListeningMode.WAKE_WORD) {
                    currentListeningMode = VoiceListeningMode.WAKE_WORD
                    switchRequested = true
                }
            }
        audioRecorder.stop()

        reportIfModelMissing()
        if (!speechRecognizer.isReady) delay(2_000)
    }

    private suspend fun runWakeWordSession() {
        voiceStateHolder.updateEngineState(VoiceEngineState.WaitingForWakeWord)
        serviceStateHolder.updateMode(ServiceMode.Running)

        // Idle: waiting for the activation phrase is never a reason to touch
        // Bluetooth — always the phone microphone here, regardless of the
        // configured MicrophoneSource.
        val wakeAudioFlow = audioRecorder.start(allowBluetooth = false)
        val detected = wakeWordDetector.waitForWakeWord(wakeAudioFlow)
        audioRecorder.stop()

        if (!detected) {
            reportIfModelMissing()
            if (!speechRecognizer.isReady) delay(2_000)
            return
        }

        DjLogger.voice("Wake word detected — opening dialog window")
        voiceStateHolder.updateEngineState(VoiceEngineState.Listening)
        serviceStateHolder.updateMode(ServiceMode.Listening)

        // Active: this is "recording a voice command" — the configured mic
        // source applies, opening Bluetooth SCO only if explicitly selected,
        // and only for the duration of this dialog window.
        val commandAudioFlow = audioRecorder.start(allowBluetooth = resolveAllowBluetooth())
        val windowMs = currentSettings.dialogWindowSeconds * 1000L
        var lastActivityAt = SystemClock.elapsedRealtime()
        var switchRequested = false

        coroutineScope {
            val collectJob = launch {
                speechRecognizer.startListening(commandAudioFlow)
                    .takeWhile { !switchRequested }
                    .collect { result ->
                        voiceStateHolder.setModelLoaded(true)
                        if (!result.isFinal || result.text.isBlank()) return@collect
                        lastActivityAt = SystemClock.elapsedRealtime()
                        val modeSwitch = handleRecognition(result)
                        if (modeSwitch == VoiceListeningMode.CONTINUOUS) {
                            currentListeningMode = VoiceListeningMode.CONTINUOUS
                            switchRequested = true
                        }
                    }
            }
            while (collectJob.isActive) {
                delay(300)
                if (SystemClock.elapsedRealtime() - lastActivityAt > windowMs) {
                    collectJob.cancel()
                }
            }
        }
        // "Disable SCO immediately after recognition finishes" — this ends
        // the dialog window's recording session (and any Bluetooth SCO it
        // opened) right away; the next loop iteration goes back to
        // wake-word waiting, which never touches Bluetooth.
        audioRecorder.stop()
    }

    /** If the flow ended without ever emitting, the model likely failed to load. */
    private fun reportIfModelMissing() {
        if (!speechRecognizer.isReady) {
            DjLogger.voiceError("Voice pipeline ended without a loaded model")
            voiceStateHolder.setModelLoaded(false)
            voiceStateHolder.updateEngineState(VoiceEngineState.Error("Модель Vosk не найдена"))
            serviceStateHolder.updateMode(ServiceMode.Error("Vosk модель не найдена"))
        }
    }

    /** Returns the new listening mode if this recognition switched modes, else null. */
    private suspend fun handleRecognition(result: RecognitionResult): VoiceListeningMode? {
        serviceStateHolder.updateMode(ServiceMode.Recognizing)
        val startTime = SystemClock.elapsedRealtime()

        val rawText = result.text
        val normalizedText = TextNormalizer.normalize(rawText.stripWakeWord())
        val intent = intentRecognizer.recognize(rawText)
        val threshold = currentSettings.voskConfidenceThreshold
        val confidence = result.confidence

        if (confidence != null && confidence < threshold) {
            DjLogger.voice("Rejected (confidence $confidence < $threshold): \"$rawText\"")
            recordAndFinish(
                rawText = rawText,
                normalizedText = normalizedText,
                confidence = confidence,
                intent = intent,
                actionLabel = "None",
                executionResult = "Rejected",
                rejectReason = "Low confidence (${formatPercent(confidence)} < ${formatPercent(threshold)})",
                startTime = startTime
            )
            return null
        }

        if (intent is DjIntent.SetContinuousMode || intent is DjIntent.SetWakeMode) {
            val newMode = if (intent is DjIntent.SetContinuousMode) {
                VoiceListeningMode.CONTINUOUS
            } else {
                VoiceListeningMode.WAKE_WORD
            }
            settingsRepository.setListeningMode(newMode)
            recordAndFinish(
                rawText = rawText,
                normalizedText = normalizedText,
                confidence = confidence,
                intent = intent,
                actionLabel = intent.label(),
                executionResult = "Mode switched to ${newMode.name}",
                rejectReason = null,
                startTime = startTime
            )
            return newMode
        }

        serviceStateHolder.updateMode(ServiceMode.Processing)
        val commandResult = commandDispatcher.dispatch(intent, rawText)

        recordAndFinish(
            rawText = rawText,
            normalizedText = normalizedText,
            confidence = confidence,
            intent = intent,
            actionLabel = commandResult.actionLabel(intent),
            executionResult = commandResult.executionResultLabel(),
            rejectReason = (commandResult as? CommandResult.Failure)?.reason,
            startTime = startTime
        )
        serviceStateHolder.updateLastRecognizedText(rawText)
        return null
    }

    private fun recordAndFinish(
        rawText: String,
        normalizedText: String,
        confidence: Float?,
        intent: DjIntent,
        actionLabel: String,
        executionResult: String,
        rejectReason: String?,
        startTime: Long
    ) {
        val elapsedMs = SystemClock.elapsedRealtime() - startTime
        voiceStateHolder.recordRecognition(
            rawText = rawText,
            normalizedText = normalizedText,
            confidence = confidence,
            intentLabel = intent.label(),
            actionLabel = actionLabel,
            executionResult = executionResult,
            rejectReason = rejectReason,
            processingTimeMs = elapsedMs
        )
        serviceStateHolder.updateMode(ServiceMode.Listening)
    }

    private fun formatPercent(value: Float): String = "${(value * 100).toInt()}%"

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
        is DjIntent.SetVolumeMax -> "VOLUME_MAX"
        is DjIntent.SetVolumeMin -> "VOLUME_MIN"
        is DjIntent.SetVolumePercent -> "SET_VOLUME_PERCENT($percent)"
        is DjIntent.QueryNowPlaying -> "QUERY_NOW_PLAYING"
        is DjIntent.QueryArtist -> "QUERY_ARTIST"
        is DjIntent.QueryIsPlaying -> "QUERY_IS_PLAYING"
        is DjIntent.QueryVolume -> "QUERY_VOLUME"
        is DjIntent.SetContinuousMode -> "MODE_CONTINUOUS"
        is DjIntent.SetWakeMode -> "MODE_WAKE"
        is DjIntent.Unknown -> "UNKNOWN"
    }

    private fun CommandResult.actionLabel(intent: DjIntent): String = when (this) {
        is CommandResult.Success -> intent.label()
        is CommandResult.SuccessWithInfo -> intent.label()
        is CommandResult.Failure -> "None"
        is CommandResult.NotSupported -> "None"
    }

    private fun CommandResult.executionResultLabel(): String = when (this) {
        is CommandResult.Success -> "Success"
        is CommandResult.SuccessWithInfo -> message
        is CommandResult.Failure -> "Failed: $reason"
        is CommandResult.NotSupported -> "Not Supported"
    }
}
