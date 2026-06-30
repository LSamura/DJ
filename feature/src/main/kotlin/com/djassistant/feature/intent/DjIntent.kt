package com.djassistant.feature.intent

sealed class DjIntent {
    // Управление воспроизведением
    object Pause : DjIntent()
    object Play : DjIntent()
    object Next : DjIntent()
    object Previous : DjIntent()
    object Stop : DjIntent()

    // Управление громкостью
    object VolumeUp : DjIntent()
    object VolumeDown : DjIntent()

    // Информационные запросы
    object QueryNowPlaying : DjIntent()
    object QueryArtist : DjIntent()
    object QueryIsPlaying : DjIntent()
    object QueryVolume : DjIntent()

    // Нераспознанная команда (всегда содержит исходный текст для журнала)
    data class Unknown(val rawText: String) : DjIntent()
}
