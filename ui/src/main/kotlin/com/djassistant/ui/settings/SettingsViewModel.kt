package com.djassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.VoiceListeningMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<DjSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DjSettings())

    fun setAutoStartService(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartService(enabled) }
    }

    fun setShowDebugScreen(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowDebugScreen(enabled) }
    }

    fun setVoskConfidenceThreshold(value: Float) {
        viewModelScope.launch { settingsRepository.setVoskConfidenceThreshold(value) }
    }

    fun setListeningMode(mode: VoiceListeningMode) {
        viewModelScope.launch { settingsRepository.setListeningMode(mode) }
    }

    fun setDialogWindowSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setDialogWindowSeconds(seconds) }
    }

    fun setMicrophoneSource(source: MicrophoneSource) {
        viewModelScope.launch { settingsRepository.setMicrophoneSource(source) }
    }

    fun setSoundFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundFeedbackEnabled(enabled) }
    }
}
