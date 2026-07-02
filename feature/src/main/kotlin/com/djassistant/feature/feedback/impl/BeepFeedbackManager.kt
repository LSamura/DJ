package com.djassistant.feature.feedback.impl

import android.media.ToneGenerator
import android.media.AudioManager
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.feedback.FeedbackManager
import com.djassistant.feature.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class BeepFeedbackManager @Inject constructor(
    settingsRepository: SettingsRepository
) : FeedbackManager {

    private val toneGenerator by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    }

    @Volatile private var soundEnabled = true

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settingsRepository.settings.collect { soundEnabled = it.soundFeedbackEnabled }
        }
    }

    override fun onActivation() {
        if (!soundEnabled) return
        DjLogger.service("feedback: activation")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    override fun onSuccess() {
        if (!soundEnabled) return
        DjLogger.service("feedback: success")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    override fun onError() {
        if (!soundEnabled) return
        DjLogger.service("feedback: error")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }
}
