package com.djassistant.service

sealed class ServiceMode {
    object Stopped : ServiceMode()
    object Starting : ServiceMode()
    object Running : ServiceMode()      // Idle — waiting for wake word
    object Listening : ServiceMode()    // Capturing voice command
    object Recognizing : ServiceMode()  // ASR in progress
    object Processing : ServiceMode()   // Executing command
    data class Error(val message: String) : ServiceMode()

    fun displayName(): String = when (this) {
        is Stopped -> "Остановлен"
        is Starting -> "Запускается..."
        is Running -> "Активен — жду команду"
        is Listening -> "Слушаю..."
        is Recognizing -> "Распознавание..."
        is Processing -> "Выполнение..."
        is Error -> "Ошибка: $message"
    }
}
