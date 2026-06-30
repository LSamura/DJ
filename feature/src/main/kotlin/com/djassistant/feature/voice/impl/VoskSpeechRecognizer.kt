package com.djassistant.feature.voice.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.RecognitionResult
import com.djassistant.feature.voice.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

// Real implementation will be added in Sprint 4 (Vosk SDK + grammar mode)
@Singleton
class VoskSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechRecognizer {

    override val isReady: Boolean = false

    override fun startListening(audioFlow: Flow<ByteArray>): Flow<RecognitionResult> {
        DjLogger.voice("VoskSpeechRecognizer.startListening() — stub, Sprint 4")
        return emptyFlow()
    }

    override fun release() {
        DjLogger.voice("VoskSpeechRecognizer.release()")
    }
}
