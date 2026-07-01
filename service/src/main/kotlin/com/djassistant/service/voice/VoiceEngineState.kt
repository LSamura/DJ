package com.djassistant.service.voice

sealed class VoiceEngineState {
    object Stopped : VoiceEngineState()
    object Initializing : VoiceEngineState()   // loading the Vosk model
    object Listening : VoiceEngineState()      // continuously capturing/recognizing
    data class Error(val message: String) : VoiceEngineState()

    fun displayName(): String = when (this) {
        is Stopped -> "Остановлен"
        is Initializing -> "Загрузка модели..."
        is Listening -> "Слушает"
        is Error -> "Ошибка: $message"
    }
}
