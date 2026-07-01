package com.djassistant.service.voice

/** One row in the "last 10 recognized commands" journal on the Debug Screen. */
data class VoiceCommandLogEntry(
    val timestampMs: Long,
    val recognizedText: String,
    val intentLabel: String,
    val actionLabel: String,
    val processingTimeMs: Long
)
