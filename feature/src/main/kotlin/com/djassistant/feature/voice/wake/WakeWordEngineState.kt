package com.djassistant.feature.voice.wake

/** Lifecycle state of a [WakeWordEngine]. Deliberately small — the Wake Layer has nothing to say about commands, only whether it's currently holding the microphone. */
enum class WakeWordEngineState {
    /** Not listening; microphone (if any was held) has been released. */
    IDLE,

    /** Actively listening for the configured [WakeWordPhrase]. */
    LISTENING,

    /** [start] was called but the engine could not begin listening (e.g. missing Access Key/keyword assets) — see the corresponding [WakeWordEvent.Error]. */
    ERROR
}
