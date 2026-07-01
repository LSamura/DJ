package com.djassistant.feature.voice

data class RecognitionResult(
    val text: String,
    val confidence: Float? = null,
    val isFinal: Boolean = true
)
