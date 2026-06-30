package com.djassistant.feature.voice

data class RecognitionResult(
    val text: String,
    val confidence: Float = 1.0f,
    val isFinal: Boolean = true
)
