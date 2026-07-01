package com.djassistant.service.voice

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MAX_LOG_ENTRIES = 10

/**
 * Voice-pipeline diagnostics for the Debug Screen — engine/model status,
 * last recognition + intent + action + timing, and a bounded journal of
 * recent commands. Analogous to [com.djassistant.service.ServiceStateHolder],
 * but scoped to the voice pipeline specifically rather than the foreground
 * service as a whole.
 */
@Singleton
class VoiceStateHolder @Inject constructor() {

    private val _engineState = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Stopped)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _lastConfidence = MutableStateFlow<Float?>(null)
    val lastConfidence: StateFlow<Float?> = _lastConfidence.asStateFlow()

    private val _lastIntent = MutableStateFlow("—")
    val lastIntent: StateFlow<String> = _lastIntent.asStateFlow()

    private val _lastAction = MutableStateFlow("—")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    private val _lastProcessingTimeMs = MutableStateFlow<Long?>(null)
    val lastProcessingTimeMs: StateFlow<Long?> = _lastProcessingTimeMs.asStateFlow()

    private val _recentCommands = MutableStateFlow<List<VoiceCommandLogEntry>>(emptyList())
    val recentCommands: StateFlow<List<VoiceCommandLogEntry>> = _recentCommands.asStateFlow()

    fun updateEngineState(state: VoiceEngineState) {
        _engineState.value = state
    }

    fun setModelLoaded(loaded: Boolean) {
        _modelLoaded.value = loaded
    }

    fun recordRecognition(
        recognizedText: String,
        confidence: Float?,
        intentLabel: String,
        actionLabel: String,
        processingTimeMs: Long
    ) {
        _lastRecognizedText.value = recognizedText
        _lastConfidence.value = confidence
        _lastIntent.value = intentLabel
        _lastAction.value = actionLabel
        _lastProcessingTimeMs.value = processingTimeMs

        val entry = VoiceCommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            recognizedText = recognizedText,
            intentLabel = intentLabel,
            actionLabel = actionLabel,
            processingTimeMs = processingTimeMs
        )
        _recentCommands.value = (_recentCommands.value + entry).takeLast(MAX_LOG_ENTRIES)
    }
}
