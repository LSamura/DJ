package com.djassistant.service.voice

sealed class VoiceEngineState {
    object Stopped : VoiceEngineState()
    object Initializing : VoiceEngineState()        // loading the Vosk model
    object WaitingForWakeWord : VoiceEngineState()   // Wake Mode: light listening, no full recognition
    object Listening : VoiceEngineState()            // Continuous mode, or an open dialog window in Wake Mode
    data class Error(val message: String) : VoiceEngineState()

    fun displayName(): String = when (this) {
        is Stopped -> "Остановлен"
        is Initializing -> "Загрузка модели..."
        is WaitingForWakeWord -> "Ждёт активацию (\"Диджей\")"
        is Listening -> "Слушает"
        is Error -> "Ошибка: $message"
    }
}
