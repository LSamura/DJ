package com.djassistant.feature.settings

import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.VoiceListeningMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<DjSettings>
    suspend fun setAutoStartService(enabled: Boolean)
    suspend fun setShowDebugScreen(enabled: Boolean)
    suspend fun setVoskConfidenceThreshold(value: Float)
    suspend fun setListeningMode(mode: VoiceListeningMode)
    suspend fun setDialogWindowSeconds(seconds: Int)
    suspend fun setMicrophoneSource(source: MicrophoneSource)
    suspend fun setSoundFeedbackEnabled(enabled: Boolean)
    suspend fun setWakeWordPhraseId(id: String)
    suspend fun setPorcupineAccessKey(key: String)
}
