package com.djassistant.feature.voice

/**
 * User-facing microphone source preference. [AUTO] and [PHONE] never touch
 * Bluetooth SCO at all; only an explicit [BLUETOOTH] selection causes a
 * recording session to attempt Bluetooth routing (see [AudioRecorder]).
 */
enum class MicrophoneSource {
    AUTO,
    PHONE,
    BLUETOOTH
}
