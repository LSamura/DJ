package com.djassistant.service.voice

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MAX_LOG_ENTRIES = 10

/**
 * Voice-pipeline diagnostics for the Debug Screen — engine/model status,
 * last recognition (raw + normalized text, confidence, intent, execution
 * result, reject reason) + timing, and a bounded journal of recent
 * commands. Analogous to [com.djassistant.service.ServiceStateHolder], but
 * scoped to the voice pipeline specifically rather than the foreground
 * service as a whole.
 */
@Singleton
class VoiceStateHolder @Inject constructor() {

    private val _engineState = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    /** Seconds left in the current dialog window, or null when no window is open (Continuous/Wake-wait). */
    private val _remainingWindowSeconds = MutableStateFlow<Int?>(null)
    val remainingWindowSeconds: StateFlow<Int?> = _remainingWindowSeconds.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _lastRawText = MutableStateFlow("")
    val lastRawText: StateFlow<String> = _lastRawText.asStateFlow()

    private val _lastNormalizedText = MutableStateFlow("")
    val lastNormalizedText: StateFlow<String> = _lastNormalizedText.asStateFlow()

    private val _lastConfidence = MutableStateFlow<Float?>(null)
    val lastConfidence: StateFlow<Float?> = _lastConfidence.asStateFlow()

    private val _lastIntent = MutableStateFlow("—")
    val lastIntent: StateFlow<String> = _lastIntent.asStateFlow()

    private val _lastAction = MutableStateFlow("—")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    private val _lastExecutionResult = MutableStateFlow("—")
    val lastExecutionResult: StateFlow<String> = _lastExecutionResult.asStateFlow()

    private val _lastRejectReason = MutableStateFlow<String?>(null)
    val lastRejectReason: StateFlow<String?> = _lastRejectReason.asStateFlow()

    private val _lastProcessingTimeMs = MutableStateFlow<Long?>(null)
    val lastProcessingTimeMs: StateFlow<Long?> = _lastProcessingTimeMs.asStateFlow()

    private val _recentCommands = MutableStateFlow<List<VoiceCommandLogEntry>>(emptyList())
    val recentCommands: StateFlow<List<VoiceCommandLogEntry>> = _recentCommands.asStateFlow()

    fun updateEngineState(state: VoiceEngineState) {
        _engineState.value = state
    }

    fun updateRemainingWindowSeconds(seconds: Int?) {
        _remainingWindowSeconds.value = seconds
    }

    fun setModelLoaded(loaded: Boolean) {
        _modelLoaded.value = loaded
    }

    fun recordRecognition(
        rawText: String,
        normalizedText: String,
        confidence: Float?,
        intentLabel: String,
        actionLabel: String,
        executionResult: String,
        rejectReason: String?,
        processingTimeMs: Long
    ) {
        _lastRawText.value = rawText
        _lastNormalizedText.value = normalizedText
        _lastConfidence.value = confidence
        _lastIntent.value = intentLabel
        _lastAction.value = actionLabel
        _lastExecutionResult.value = executionResult
        _lastRejectReason.value = rejectReason
        _lastProcessingTimeMs.value = processingTimeMs

        val entry = VoiceCommandLogEntry(
            timestampMs = System.currentTimeMillis(),
            rawText = rawText,
            normalizedText = normalizedText,
            confidence = confidence,
            intentLabel = intentLabel,
            actionLabel = actionLabel,
            executionResult = executionResult,
            rejectReason = rejectReason,
            processingTimeMs = processingTimeMs
        )
        _recentCommands.value = (_recentCommands.value + entry).takeLast(MAX_LOG_ENTRIES)
    }
}
