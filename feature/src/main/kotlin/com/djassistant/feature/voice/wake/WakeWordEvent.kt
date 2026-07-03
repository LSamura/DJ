package com.djassistant.feature.voice.wake

/** Something a [WakeWordEngine] wants to tell its caller about. No command/intent vocabulary here on purpose — see [WakeWordEngine] doc. */
sealed class WakeWordEvent {
    /** The configured phrase was heard. [com.djassistant.service.voice.VoiceEngine] reacts to this by opening a Listening Window and starting Vosk. */
    data class Detected(val phrase: WakeWordPhrase) : WakeWordEvent()

    /** [WakeWordEngine.start] could not begin listening, or listening failed while active (e.g. native engine error). Never crashes the caller — always delivered as an event instead. */
    data class Error(val message: String, val throwable: Throwable? = null) : WakeWordEvent()
}
