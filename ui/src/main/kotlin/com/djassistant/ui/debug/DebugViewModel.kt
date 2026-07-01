package com.djassistant.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djassistant.core.logging.DjLogBuffer
import com.djassistant.data.log.UnknownCommandEntry
import com.djassistant.data.log.UnknownCommandLogger
import com.djassistant.feature.media.MediaPlaybackState
import com.djassistant.feature.media.MediaStateProvider
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
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
    private val unknownCommandLogger: UnknownCommandLogger
) : ViewModel() {

    val serviceMode: StateFlow<ServiceMode> = serviceStateHolder.mode

    val audioLevel: StateFlow<Float> = audioRecorder.audioLevel

    val lastRecognizedText: StateFlow<String> = serviceStateHolder.lastRecognizedText

    val lastCommandInfo: StateFlow<String?> = serviceStateHolder.lastCommandInfo

    val mediaState: StateFlow<MediaPlaybackState> = mediaStateProvider.state

    val lastError: StateFlow<DjLogBuffer.Entry?> = DjLogBuffer.lastError

    val recentLogs: StateFlow<List<DjLogBuffer.Entry>> = DjLogBuffer.entries

    val recentUnknownCommands: StateFlow<List<UnknownCommandEntry>> = flow {
        while (true) {
            emit(unknownCommandLogger.readRecent(10))
            delay(2_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logExportPath: String get() = unknownCommandLogger.exportPath()

    fun clearLog() = unknownCommandLogger.clear()
}
