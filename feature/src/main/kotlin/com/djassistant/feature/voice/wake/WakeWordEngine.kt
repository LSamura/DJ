package com.djassistant.feature.voice.wake

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Wake Layer: detects an activation phrase in the live microphone
 * signal, completely independently of command recognition.
 *
 * Layering (Sprint 4):
 * ```
 * Microphone -> WakeWordEngine -> VoiceEngine -> IntentParser -> Media Layer
 * ```
 * [WakeWordEngine] owns its own microphone session while active — unlike
 * the Sprint 3.1-3.3 design, callers do not hand it an audio [kotlinx.coroutines.flow.Flow];
 * it manages capture, native engine lifecycle, and teardown internally, and
 * only ever talks back through [state]/[events]. This is what lets it be
 * swapped for a different vendor (see [com.djassistant.feature.voice.wake.impl.MockWakeWordEngine]
 * for the test double proving it) without
 * [com.djassistant.service.voice.VoiceEngine] knowing anything changed:
 * `VoiceEngine` only calls [start]/[stop]/[destroy] and reacts to
 * [WakeWordEvent]s — it has no idea whether detection happens via
 * Porcupine, OpenWakeWord, Snowboy, or anything else.
 *
 * [WakeWordEngine] never knows about [com.djassistant.feature.intent.DjIntent]
 * or the Media Layer — its only vocabulary is "which [WakeWordPhrase] did I
 * hear, if any." Command recognition (Vosk) starts only after
 * [com.djassistant.service.voice.VoiceEngine] reacts to a
 * [WakeWordEvent.Detected].
 */
interface WakeWordEngine {
    /** Current lifecycle state — observe to know when it's safe to treat the engine as actively holding the microphone. */
    val state: StateFlow<WakeWordEngineState>

    /** Detection/error notifications. Hot — subscribe before calling [start] to not miss the first event. */
    val events: SharedFlow<WakeWordEvent>

    /**
     * Begins listening for the currently configured [WakeWordPhrase]. Opens
     * its own microphone session (never Bluetooth — wake-word waiting is
     * always a phone-mic-only idle state, see ADR-031). No-op if already
     * listening. Detecting the phrase automatically stops listening again
     * (transitions back to [WakeWordEngineState.IDLE]) and emits
     * [WakeWordEvent.Detected] — the caller decides if/when to [start]
     * again, [WakeWordEngine] does not restart itself.
     */
    fun start()

    /** Stops listening and releases the microphone/native resources for this session. Safe to call repeatedly, including when already idle. */
    fun stop()

    /** Fully tears down the engine (e.g. when the whole Voice Layer is stopping). Safe to call repeatedly. */
    fun destroy()
}
