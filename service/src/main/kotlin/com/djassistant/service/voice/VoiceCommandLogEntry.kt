package com.djassistant.service.voice

/** One row in the "last 10 recognized commands" journal on the Debug Screen. */
data class VoiceCommandLogEntry(
    val timestampMs: Long,
    val rawText: String,
    val normalizedText: String,
    val confidence: Float?,
    val intentLabel: String,
    val actionLabel: String,
    val executionResult: String,
    val rejectReason: String?,
    val processingTimeMs: Long
)
