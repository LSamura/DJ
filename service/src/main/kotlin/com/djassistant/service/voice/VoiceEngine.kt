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
import com.djassistant.feature.feedback.FeedbackManager
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
import com.djassistant.feature.voice.wake.WakeWordEngine
import com.djassistant.feature.voice.wake.WakeWordEvent
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import com.djassistant.service.overlay.VoiceOverlayController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
 * Supports two listening modes:
 *  - [VoiceListeningMode.CONTINUOUS] — full Vosk command grammar always
 *    running (Sprint 3 behavior, unchanged since).
 *  - [VoiceListeningMode.WAKE_WORD] — `Microphone -> WakeWordEngine ->
 *    VoiceEngine -> IntentParser -> Media Layer` (Sprint 4). A dedicated
 *    [WakeWordEngine] (Porcupine) owns the microphone and listens for the
 *    activation phrase entirely on its own — this class only calls
 *    [WakeWordEngine.start]/[WakeWordEngine.stop] and reacts to whatever
 *    [WakeWordEvent] comes back; it has no idea how detection happens (see
 *    ADR-037/039 in DECISIONS.md). Once a [WakeWordEvent.Detected] arrives,
 *    Vosk is started just for a configurable Listening Window, resets on
 *    every successfully executed command, then Vosk is torn down again and
 *    [WakeWordEngine] is asked to start listening again.
 *
 * Mode-switch intents (SetContinuousMode/SetWakeMode) are handled here and
 * never reach [CommandDispatcher] — they are a Voice Layer concern, not a
 * Media Layer one.
 *
 * Bluetooth microphone lifecycle: the wake-word waiting phase is a
 * genuinely idle state and always uses the phone microphone — [WakeWordEngine]
 * never opens a Bluetooth SCO connection (Sprint 4: this is now the Wake
 * Layer's own responsibility, not this class's — see ADR-037/039). Only
 * the "actually recording a command" phase (the dialog window in Wake
 * Mode, or the whole session in Continuous Mode) opens SCO, and only when
 * the user explicitly selected [MicrophoneSource.BLUETOOTH]; it is closed
 * the moment that phase ends. See ADR in DECISIONS.md.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val speechRecognizer: SpeechRecognizer,
    private val wakeWordEngine: WakeWordEngine,
    private val intentRecognizer: IntentRecognizer,
    private val commandDispatcher: CommandDispatcher,
    private val settingsRepository: SettingsRepository,
    private val serviceStateHolder: ServiceStateHolder,
    private val voiceStateHolder: VoiceStateHolder,
    private val feedbackManager: FeedbackManager,
    private val voiceOverlayController: VoiceOverlayController
) {

    // Last-resort safety net: if a bug slips past the per-stage try/catch below
    // (e.g. in the settings collector child coroutine), this stops it from
    // crashing the whole app with an uncaught exception — it gets logged and
    // surfaced as an Error engine state instead.
    private val engineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            stageError(throwable)
        }
    )
    private var job: Job? = null

    @Volatile private var currentSettings = DjSettings()
    @Volatile private var currentListeningMode = VoiceListeningMode.CONTINUOUS

    /** Human-readable marker of the last stage entered — used to tag errors so Debug Screen shows *where* things broke. */
    @Volatile private var currentStage: String = "idle"

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
        wakeWordEngine.destroy()
        voiceOverlayController.hideImmediately()
        voiceStateHolder.setModelLoaded(false)
        voiceStateHolder.updateRemainingWindowSeconds(null)
        voiceStateHolder.updateEngineState(VoiceEngineState.Idle)
    }

    private suspend fun runPipeline() {
        currentStage = "initializing"
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
            try {
                when (currentListeningMode) {
                    VoiceListeningMode.CONTINUOUS -> runContinuousSession()
                    VoiceListeningMode.WAKE_WORD -> runWakeWordSession()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failure at any stage must never crash the whole app — log
                // it with the stage it happened at, force every resource this
                // engine could be holding closed, and go around the loop
                // again instead of propagating.
                stageError(e)
                runCatching { audioRecorder.stop() }
                runCatching { voiceOverlayController.hideImmediately() }
                voiceStateHolder.updateRemainingWindowSeconds(null)
                delay(1_000)
            }
        }
    }

    /** Logs an exception tagged with [currentStage] and surfaces it as an Error engine state, visible on Debug Screen. */
    private fun stageError(throwable: Throwable) {
        val stage = currentStage
        DjLogger.voiceError("Ошибка на этапе '$stage'", throwable)
        voiceStateHolder.updateEngineState(
            VoiceEngineState.Error("[$stage] ${throwable.message ?: throwable::class.simpleName}")
        )
    }

    /** Only an explicit Bluetooth selection ever opens SCO — AUTO/PHONE always use the phone mic. */
    private fun resolveAllowBluetooth(): Boolean =
        currentSettings.microphoneSource == MicrophoneSource.BLUETOOTH

    private suspend fun runContinuousSession() {
        currentStage = "continuous_listening"
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
                val outcome = handleRecognition(result)
                if (outcome.modeSwitch == VoiceListeningMode.WAKE_WORD) {
                    currentListeningMode = VoiceListeningMode.WAKE_WORD
                    switchRequested = true
                }
            }
        audioRecorder.stop()

        reportIfModelMissing()
        if (!speechRecognizer.isReady) delay(2_000)
    }

    private suspend fun runWakeWordSession() {
        currentStage = "waiting_wake_word"
        voiceStateHolder.updateEngineState(VoiceEngineState.WaitingWakeWord)
        voiceStateHolder.updateRemainingWindowSeconds(null)
        serviceStateHolder.updateMode(ServiceMode.Running)

        // Microphone -> WakeWordEngine -> VoiceEngine: the Wake Layer owns
        // its own microphone session while listening (see WakeWordEngine
        // doc / ADR-037/039) — VoiceEngine no longer touches AudioRecorder
        // at all for this phase, it only starts the engine and reacts to
        // whatever WakeWordEvent comes back. Subscribing via `async(start =
        // CoroutineStart.UNDISPATCHED)` before calling start() guarantees
        // the events collector is registered before the engine could
        // possibly emit, since `events` is a hot SharedFlow with no replay.
        currentStage = "wake_word_detection"
        val event = coroutineScope {
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { wakeWordEngine.events.first() }
            wakeWordEngine.start()
            eventDeferred.await()
        }
        wakeWordEngine.stop()

        val phrase = when (event) {
            is WakeWordEvent.Error -> {
                DjLogger.voiceError("WakeWordEngine: ${event.message}", event.throwable)
                voiceStateHolder.updateEngineState(VoiceEngineState.Error("Wake Word: ${event.message}"))
                delay(2_000)
                return
            }
            is WakeWordEvent.Detected -> event.phrase
        }

        DjLogger.voice("Wake word detected (\"${phrase.displayName}\") — opening dialog window")
        currentStage = "wake_word_activation_feedback"
        feedbackManager.onActivation()
        // Wake Word UX: hearing "Диджей" only opens the dialog window — it
        // never itself executes a command.
        voiceStateHolder.updateEngineState(VoiceEngineState.Listening)
        serviceStateHolder.updateMode(ServiceMode.Listening)
        currentStage = "overlay_show"
        voiceOverlayController.show("🎧 DJ — Слушаю...")

        // Active: this is "recording a voice command" — the configured mic
        // source applies, opening Bluetooth SCO only if explicitly selected,
        // and only for the duration of this dialog window.
        currentStage = "dialog_window_audio_start"
        val commandAudioFlow = audioRecorder.start(allowBluetooth = resolveAllowBluetooth())
        val windowMs = currentSettings.dialogWindowSeconds * 1000L
        var windowDeadline = SystemClock.elapsedRealtime() + windowMs
        var switchRequested = false

        currentStage = "dialog_window"
        coroutineScope {
            val collectJob = launch {
                speechRecognizer.startListening(commandAudioFlow)
                    .takeWhile { !switchRequested }
                    .collect { result ->
                        voiceStateHolder.setModelLoaded(true)
                        if (!result.isFinal || result.text.isBlank()) return@collect
                        val outcome = handleRecognition(result)
                        if (outcome.extendWindow) {
                            // Only a successfully executed command re-opens the
                            // window — ambient noise or rejected/failed
                            // recognitions must not keep the mic open forever.
                            windowDeadline = SystemClock.elapsedRealtime() + windowMs
                        }
                        voiceOverlayController.updateStatus("🎧 DJ — Слушаю...")
                        if (outcome.modeSwitch == VoiceListeningMode.CONTINUOUS) {
                            currentListeningMode = VoiceListeningMode.CONTINUOUS
                            switchRequested = true
                        }
                    }
            }
            while (collectJob.isActive) {
                val secondsLeft = ((windowDeadline - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                voiceStateHolder.updateRemainingWindowSeconds(secondsLeft)
                voiceOverlayController.updateCountdown(secondsLeft)
                delay(300)
                if (SystemClock.elapsedRealtime() >= windowDeadline) {
                    collectJob.cancel()
                }
            }
        }
        voiceStateHolder.updateRemainingWindowSeconds(null)
        voiceStateHolder.updateEngineState(VoiceEngineState.Sleep)
        voiceOverlayController.hide()
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

    /** Result of handling one recognized utterance during a dialog window. */
    private data class RecognitionOutcome(
        val modeSwitch: VoiceListeningMode?,
        /** True only for a successfully executed command — noise/rejections/failures must not extend the dialog window. */
        val extendWindow: Boolean
    )

    /**
     * Always leaves the engine state back at [VoiceEngineState.Listening] on
     * the way out — every branch below used to reset it individually, and it
     * was easy to add a new branch (e.g. the confidence-rejection path) that
     * forgot to, leaving Debug Screen stuck showing "Processing"/"Executing"
     * for the rest of the dialog window even though the engine had already
     * moved on. A single `finally` makes that class of bug impossible.
     */
    private suspend fun handleRecognition(result: RecognitionResult): RecognitionOutcome {
        currentStage = "recognition_processing"
        voiceStateHolder.updateEngineState(VoiceEngineState.Processing)
        voiceOverlayController.updateStatus("🎧 DJ — Распознаю...")
        serviceStateHolder.updateMode(ServiceMode.Recognizing)
        val startTime = SystemClock.elapsedRealtime()
        try {
            return handleRecognitionInternal(result, startTime)
        } finally {
            currentStage = "recognition_processing"
            voiceStateHolder.updateEngineState(VoiceEngineState.Listening)
        }
    }

    private suspend fun handleRecognitionInternal(
        result: RecognitionResult,
        startTime: Long
    ): RecognitionOutcome {
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
            return RecognitionOutcome(modeSwitch = null, extendWindow = false)
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
            return RecognitionOutcome(modeSwitch = newMode, extendWindow = true)
        }

        currentStage = "command_dispatch"
        voiceStateHolder.updateEngineState(VoiceEngineState.Executing)
        voiceOverlayController.updateStatus("🎧 DJ — Выполняю...")
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
        val succeeded = commandResult is CommandResult.Success || commandResult is CommandResult.SuccessWithInfo
        return RecognitionOutcome(modeSwitch = null, extendWindow = succeeded)
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
