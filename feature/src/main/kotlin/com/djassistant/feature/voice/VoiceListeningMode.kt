package com.djassistant.feature.voice

/**
 * The two listening modes Sprint 3.1 supports. [CONTINUOUS] is the Sprint 3
 * behavior (full command grammar running at all times). [WAKE_WORD] listens
 * for an activation phrase with a small grammar and only runs full command
 * recognition for a short dialog window afterward (see VoiceEngine).
 */
enum class VoiceListeningMode {
    CONTINUOUS,
    WAKE_WORD
}
