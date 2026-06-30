package com.djassistant.feature.voice.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

// Real implementation will be added in Sprint 3 (AudioRecord + PCM pipeline)
@Singleton
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording

    override fun start(): Flow<ByteArray> {
        DjLogger.voice("AndroidAudioRecorder.start() — stub, Sprint 3")
        _isRecording.value = true
        return emptyFlow()
    }

    override fun stop() {
        DjLogger.voice("AndroidAudioRecorder.stop()")
        _isRecording.value = false
        _audioLevel.value = 0f
    }
}
