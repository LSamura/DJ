package com.djassistant.service.voice

sealed class VoiceEngineState {
    object Idle : VoiceEngineState()             // engine stopped
    object Initializing : VoiceEngineState()     // loading the Vosk model
    object WaitingWakeWord : VoiceEngineState()  // Wake Mode: light listening, no full recognition
    object Listening : VoiceEngineState()        // Continuous mode, or an open dialog window in Wake Mode
    object Processing : VoiceEngineState()       // recognized speech is being parsed into an intent
    object Executing : VoiceEngineState()        // dispatching the recognized command to the Media Layer
    object Sleep : VoiceEngineState()            // dialog window just closed, mic/SCO released, returning to wake-word wait
    data class Error(val message: String) : VoiceEngineState()

    fun displayName(): String = when (this) {
        is Idle -> "Остановлен"
        is Initializing -> "Загрузка модели..."
        is WaitingWakeWord -> "Ждёт активацию (\"Диджей\")"
        is Listening -> "Слушает"
        is Processing -> "Распознаёт..."
        is Executing -> "Выполняет команду..."
        is Sleep -> "Засыпает"
        is Error -> "Ошибка: $message"
    }
}
