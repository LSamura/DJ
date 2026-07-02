package com.djassistant.feature.voice.impl

import com.djassistant.feature.voice.WakeWordEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Test double for [WakeWordEngine] — proves that
 * [com.djassistant.service.voice.VoiceEngine] genuinely depends only on the
 * interface and can run against a completely different detection mechanism
 * without any changes. Does no real audio processing: it drains the audio
 * flow (so callers behave the same as with a real engine attached) and only
 * "detects" the wake word when a test explicitly calls [triggerDetection].
 *
 * Not bound in [com.djassistant.di.AppModule] for production use —
 * intended for unit/instrumented tests and Compose previews that need a
 * deterministic, controllable [WakeWordEngine] without Porcupine's native
 * dependency, access key, or trained keyword files.
 */
@Singleton
class MockWakeWordEngine @Inject constructor() : WakeWordEngine {

    override val isReady: Boolean = true

    private val detectionSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Test hook — simulates the configured wake phrase being spoken right now. */
    fun triggerDetection() {
        detectionSignal.tryEmit(Unit)
    }

    override suspend fun waitForWakeWord(audioFlow: Flow<ByteArray>): Boolean = coroutineScope {
        val drainJob = launch { audioFlow.collect { /* mock: audio is intentionally ignored */ } }
        try {
            detectionSignal.first()
            true
        } finally {
            drainJob.cancel()
        }
    }

    override fun release() = Unit
}
