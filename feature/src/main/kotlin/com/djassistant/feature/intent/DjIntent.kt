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
    object SetVolumeMax : DjIntent()
    object SetVolumeMin : DjIntent()
    data class SetVolumePercent(val percent: Int) : DjIntent()

    // Информационные запросы
    object QueryNowPlaying : DjIntent()
    object QueryArtist : DjIntent()
    object QueryIsPlaying : DjIntent()
    object QueryVolume : DjIntent()

    // Управление режимом голосового движка (Voice Layer, не Media Layer —
    // перехватываются VoiceEngine до CommandDispatcher, см. ADR Sprint 3.1)
    object SetContinuousMode : DjIntent()
    object SetWakeMode : DjIntent()

    // Нераспознанная команда (всегда содержит исходный текст для журнала)
    data class Unknown(val rawText: String) : DjIntent()
}
