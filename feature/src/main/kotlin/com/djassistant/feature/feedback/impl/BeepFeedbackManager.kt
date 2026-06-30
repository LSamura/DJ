package com.djassistant.feature.feedback.impl

import android.media.ToneGenerator
import android.media.AudioManager
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.feedback.FeedbackManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeepFeedbackManager @Inject constructor() : FeedbackManager {

    private val toneGenerator by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    }

    override fun onActivation() {
        DjLogger.service("feedback: activation")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    override fun onSuccess() {
        DjLogger.service("feedback: success")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    override fun onError() {
        DjLogger.service("feedback: error")
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }
}
