package com.djassistant.data.log

data class UnknownCommandEntry(
    val rawText: String,
    val recognizedAs: String,
    val timestampMs: Long = System.currentTimeMillis()
)
