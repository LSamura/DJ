package com.djassistant.feature.intent

interface IntentRecognizer {
    fun recognize(commandText: String): DjIntent
}
