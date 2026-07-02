package com.djassistant.feature.voice.impl

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.WakeWordEngine
import com.djassistant.feature.voice.WakeWordPhrases
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Wake-word detection via Picovoice Porcupine (Sprint 4) — replaces the
 * Sprint 3.x approach of running a restricted Vosk grammar continuously
 * while idle. Porcupine is purpose-built for this: a tiny always-on
 * detector, cheap enough to run indefinitely without the microphone
 * indicator/Bluetooth SCO churn a full ASR engine caused.
 *
 * A fresh [Porcupine] native instance is created and destroyed for each
 * [waitForWakeWord] call (i.e. each time Wake Mode goes idle) rather than
 * held for the engine's whole lifetime — this mirrors how [AudioRecorder]
 * sessions are already scoped per phase (see ADR-031/033 in DECISIONS.md)
 * and means picking up an access-key or phrase change from Settings never
 * requires restarting the whole [com.djassistant.service.voice.VoiceEngine].
 */
@Singleton
class PorcupineWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val assetProvisioner: PorcupineAssetProvisioner
) : WakeWordEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentSettings = DjSettings()

    init {
        engineScope.launch {
            settingsRepository.settings.collect { currentSettings = it }
        }
    }

    override val isReady: Boolean
        get() = currentSettings.porcupineAccessKey.isNotBlank() &&
            assetProvisioner.hasAssets(WakeWordPhrases.byId(currentSettings.wakeWordPhraseId))

    override suspend fun waitForWakeWord(audioFlow: Flow<ByteArray>): Boolean {
        val accessKey = currentSettings.porcupineAccessKey
        val phrase = WakeWordPhrases.byId(currentSettings.wakeWordPhraseId)

        if (accessKey.isBlank()) {
            DjLogger.voiceError(
                "Porcupine Access Key не задан — укажите его в Настройках " +
                    "(получить можно бесплатно на console.picovoice.ai)"
            )
            return false
        }

        val assets = assetProvisioner.ensureAssets(phrase) ?: return false

        val porcupine = try {
            Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf(assets.keywordPath))
                .apply { assets.modelPath?.let { setModelPath(it) } }
                .build(context)
        } catch (e: PorcupineException) {
            DjLogger.voiceError("Failed to initialize Porcupine for \"${phrase.displayName}\"", e)
            return false
        } catch (e: Exception) {
            // Defensive: also catches any non-PorcupineException failure
            // from the native build step (e.g. a malformed asset file)
            // instead of letting it crash the whole app.
            DjLogger.voiceError("Unexpected error initializing Porcupine for \"${phrase.displayName}\"", e)
            return false
        }

        return try {
            collectUntilDetected(audioFlow, porcupine, phrase.displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DjLogger.voiceError("Porcupine processing error", e)
            false
        } finally {
            runCatching { porcupine.delete() }
        }
    }

    /** Own signal used purely to unwind [Flow.collect] the moment a keyword is detected, mirroring how `Flow.first` cancels its upstream internally. */
    private class WakeWordDetected : CancellationException("wake word detected")

    private suspend fun collectUntilDetected(
        audioFlow: Flow<ByteArray>,
        porcupine: Porcupine,
        phraseLabel: String
    ): Boolean {
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
                            DjLogger.voice("Porcupine detected wake word \"$phraseLabel\"")
                            throw WakeWordDetected()
                        }
                    }
                }
            }
            false
        } catch (e: WakeWordDetected) {
            true
        }
    }

    override fun release() {
        // Native Porcupine handles are scoped to a single waitForWakeWord()
        // call (created and .delete()'d there) rather than held across the
        // engine's lifetime, so there is nothing persistent to release here.
        // Present to satisfy the WakeWordEngine contract and for symmetry
        // with SpeechRecognizer.release().
    }
}
