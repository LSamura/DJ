package com.djassistant.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djassistant.core.logging.DjLogBuffer
import com.djassistant.data.log.UnknownCommandEntry
import com.djassistant.data.log.UnknownCommandLogger
import com.djassistant.feature.media.MediaPlaybackState
import com.djassistant.feature.media.MediaStateProvider
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import com.djassistant.service.voice.VoiceCommandLogEntry
import com.djassistant.service.voice.VoiceEngineState
import com.djassistant.service.voice.VoiceStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val serviceStateHolder: ServiceStateHolder,
    private val audioRecorder: AudioRecorder,
    private val mediaStateProvider: MediaStateProvider,
    private val unknownCommandLogger: UnknownCommandLogger,
    private val voiceStateHolder: VoiceStateHolder,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val serviceMode: StateFlow<ServiceMode> = serviceStateHolder.mode

    val audioLevel: StateFlow<Float> = audioRecorder.audioLevel
    val activeInputSource: StateFlow<String> = audioRecorder.activeInputSource

    val mediaState: StateFlow<MediaPlaybackState> = mediaStateProvider.state

    val lastError: StateFlow<DjLogBuffer.Entry?> = DjLogBuffer.lastError

    val recentLogs: StateFlow<List<DjLogBuffer.Entry>> = DjLogBuffer.entries

    val voiceEngineState: StateFlow<VoiceEngineState> = voiceStateHolder.engineState
    val voiceModelLoaded: StateFlow<Boolean> = voiceStateHolder.modelLoaded
    val voiceLastRawText: StateFlow<String> = voiceStateHolder.lastRawText
    val voiceLastNormalizedText: StateFlow<String> = voiceStateHolder.lastNormalizedText
    val voiceLastConfidence: StateFlow<Float?> = voiceStateHolder.lastConfidence
    val voiceLastIntent: StateFlow<String> = voiceStateHolder.lastIntent
    val voiceLastAction: StateFlow<String> = voiceStateHolder.lastAction
    val voiceLastExecutionResult: StateFlow<String> = voiceStateHolder.lastExecutionResult
    val voiceLastRejectReason: StateFlow<String?> = voiceStateHolder.lastRejectReason
    val voiceLastProcessingTimeMs: StateFlow<Long?> = voiceStateHolder.lastProcessingTimeMs
    val voiceRecentCommands: StateFlow<List<VoiceCommandLogEntry>> = voiceStateHolder.recentCommands

    val settings: StateFlow<DjSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DjSettings())

    val recentUnknownCommands: StateFlow<List<UnknownCommandEntry>> = flow {
        while (true) {
            emit(unknownCommandLogger.readRecent(10))
            delay(2_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logExportPath: String get() = unknownCommandLogger.exportPath()

    fun clearLog() = unknownCommandLogger.clear()
}
