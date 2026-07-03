package com.djassistant.feature.voice.wake.impl

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.feature.voice.wake.WakeWordEngine
import com.djassistant.feature.voice.wake.WakeWordEngineState
import com.djassistant.feature.voice.wake.WakeWordEvent
import com.djassistant.feature.voice.wake.WakeWordPhrase
import com.djassistant.feature.voice.wake.WakeWordPhrases
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wake Layer implementation via Picovoice Porcupine (Sprint 4) — a purpose
 * -built, always-cheap-to-run detector, replacing the Sprint 3.x approach
 * of running a restricted Vosk grammar continuously while idle (see
 * ADR-037 in DECISIONS.md).
 *
 * Unlike [com.djassistant.feature.voice.SpeechRecognizer], this class owns
 * its own [AudioRecorder] session for as long as it is [start]ed — the
 * Voice Layer diagram is `Microphone -> WakeWordEngine -> VoiceEngine`, so
 * the microphone genuinely belongs to the Wake Layer while listening, not
 * to [com.djassistant.service.voice.VoiceEngine]. A fresh native
 * [Porcupine] instance and audio session are created per [start] call and
 * torn down on detection/[stop]/error — the same "scoped to the phase, not
 * the whole engine lifetime" pattern already used for `AudioRecorder`
 * sessions (ADR-031) and the overlay (ADR-033/034).
 */
@Singleton
class PorcupineWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val assetProvisioner: PorcupineAssetProvisioner
) : WakeWordEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null

    private val _state = MutableStateFlow(WakeWordEngineState.IDLE)
    override val state: StateFlow<WakeWordEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WakeWordEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<WakeWordEvent> = _events.asSharedFlow()

    @Volatile private var currentSettings = DjSettings()

    init {
        engineScope.launch {
            settingsRepository.settings.collect { currentSettings = it }
        }
    }

    override fun start() {
        if (listenJob?.isActive == true) {
            DjLogger.voice("PorcupineWakeWordEngine.start() ignored — already listening")
            return
        }
        listenJob = engineScope.launch { runListening() }
    }

    override fun stop() {
        listenJob?.cancel()
        listenJob = null
        audioRecorder.stop()
        _state.value = WakeWordEngineState.IDLE
    }

    override fun destroy() {
        stop()
        // No persistent native handle to release — each session creates and
        // .delete()s its own Porcupine instance in runListening()'s finally
        // block. Present to satisfy the WakeWordEngine contract.
    }

    private suspend fun runListening() {
        val accessKey = currentSettings.porcupineAccessKey
        val phrase = WakeWordPhrases.byId(currentSettings.wakeWordPhraseId)

        if (accessKey.isBlank()) {
            emitError("Porcupine Access Key не задан — укажите его в Настройках (получить можно бесплатно на console.picovoice.ai)")
            return
        }

        val assets = assetProvisioner.ensureAssets(phrase) ?: run {
            emitError("Ассеты Porcupine для \"${phrase.displayName}\" не найдены — см. feature/src/main/assets/porcupine/README.md")
            return
        }

        val porcupine = try {
            Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf(assets.keywordPath))
                .apply { assets.modelPath?.let { setModelPath(it) } }
                .build(context)
        } catch (e: PorcupineException) {
            emitError("Failed to initialize Porcupine for \"${phrase.displayName}\"", e)
            return
        } catch (e: Exception) {
            emitError("Unexpected error initializing Porcupine for \"${phrase.displayName}\"", e)
            return
        }

        _state.value = WakeWordEngineState.LISTENING
        // Wake-word waiting is always a genuinely idle state — the Wake
        // Layer never opens Bluetooth SCO, regardless of MicrophoneSource;
        // only VoiceEngine's Listening Window (actually recording a
        // command) may do that, and only if explicitly selected.
        val audioFlow = audioRecorder.start(allowBluetooth = false)

        try {
            val detected = collectUntilDetected(audioFlow, porcupine)
            if (detected) {
                DjLogger.voice("Porcupine detected wake word \"${phrase.displayName}\"")
                _events.emit(WakeWordEvent.Detected(phrase))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitError("Porcupine processing error", e)
        } finally {
            audioRecorder.stop()
            runCatching { porcupine.delete() }
            _state.value = WakeWordEngineState.IDLE
        }
    }

    /** Own signal used purely to unwind [Flow.collect] the moment a keyword is detected, mirroring how `Flow.first` cancels its upstream internally. */
    private class WakeWordDetectedSignal : CancellationException("wake word detected")

    private suspend fun collectUntilDetected(audioFlow: Flow<ByteArray>, porcupine: Porcupine): Boolean {
        val frameLength = porcupine.frameLength
        val frame = ShortArray(frameLength)
        var frameFill = 0

        return try {
            audioFlow.collect { chunk ->
                var offset = 0
                while (offset + 1 < chunk.size) {
                    val sample = ((chunk[offset + 1].toInt() shl 8) or (chunk[offset].toInt() and 0xFF)).toShort()
                    frame[frameFill] = sample
                    frameFill++
                    offset += 2
                    if (frameFill == frameLength) {
                        frameFill = 0
                        if (porcupine.process(frame) >= 0) {
                            throw WakeWordDetectedSignal()
                        }
                    }
                }
            }
            false
        } catch (e: WakeWordDetectedSignal) {
            true
        }
    }

    private suspend fun emitError(message: String, throwable: Throwable? = null) {
        DjLogger.voiceError(message, throwable)
        _state.value = WakeWordEngineState.ERROR
        _events.emit(WakeWordEvent.Error(message, throwable))
    }
}
