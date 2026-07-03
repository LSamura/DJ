package com.djassistant.feature.voice.wake.impl

import com.djassistant.feature.voice.wake.WakeWordEngine
import com.djassistant.feature.voice.wake.WakeWordEngineState
import com.djassistant.feature.voice.wake.WakeWordEvent
import com.djassistant.feature.voice.wake.WakeWordPhrase
import com.djassistant.feature.voice.wake.WakeWordPhrases
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for [WakeWordEngine] — proves that
 * [com.djassistant.service.voice.VoiceEngine] genuinely depends only on the
 * interface and can run against a completely different detection mechanism
 * without any changes. Does no real audio processing or microphone access
 * at all: [start] just flips [state] to [WakeWordEngineState.LISTENING],
 * and detection only ever happens when a test explicitly calls
 * [triggerDetection].
 *
 * Not bound in [com.djassistant.di.AppModule] for production use —
 * intended for unit/instrumented tests and Compose previews that need a
 * deterministic, controllable [WakeWordEngine] without Porcupine's native
 * dependency, access key, or trained keyword files.
 */
@Singleton
class MockWakeWordEngine @Inject constructor() : WakeWordEngine {

    private val _state = MutableStateFlow(WakeWordEngineState.IDLE)
    override val state: StateFlow<WakeWordEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WakeWordEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<WakeWordEvent> = _events.asSharedFlow()

    override fun start() {
        _state.value = WakeWordEngineState.LISTENING
    }

    override fun stop() {
        _state.value = WakeWordEngineState.IDLE
    }

    override fun destroy() {
        stop()
    }

    /** Test hook — simulates the given wake phrase being spoken right now; no-op unless [start] was called first (mirrors the real engine only detecting while listening). */
    fun triggerDetection(phrase: WakeWordPhrase = WakeWordPhrases.DEFAULT) {
        if (_state.value != WakeWordEngineState.LISTENING) return
        _state.value = WakeWordEngineState.IDLE
        _events.tryEmit(WakeWordEvent.Detected(phrase))
    }

    /** Test hook — simulates the engine failing. */
    fun triggerError(message: String = "mock error") {
        _state.value = WakeWordEngineState.ERROR
        _events.tryEmit(WakeWordEvent.Error(message))
    }
}
