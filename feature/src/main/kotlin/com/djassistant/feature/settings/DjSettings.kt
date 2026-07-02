package com.djassistant.feature.settings

import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.VoiceListeningMode
import com.djassistant.feature.voice.WakeWordPhrases

data class DjSettings(
    val autoStartService: Boolean = false,
    val showDebugScreen: Boolean = false,
    /** Recognitions below this confidence are rejected without executing (default 80%). */
    val voskConfidenceThreshold: Float = 0.8f,
    val listeningMode: VoiceListeningMode = VoiceListeningMode.CONTINUOUS,
    /** How long (seconds) the dialog window stays open after wake-word activation. Range 3-15. */
    val dialogWindowSeconds: Int = 6,
    /** AUTO/PHONE never touch Bluetooth SCO; only BLUETOOTH opts in. */
    val microphoneSource: MicrophoneSource = MicrophoneSource.AUTO,
    /** Short activation/success/error beeps; can be muted entirely. */
    val soundFeedbackEnabled: Boolean = true,
    /** Which [com.djassistant.feature.voice.WakeWordPhrase] Porcupine listens for in Wake Mode (Sprint 4). */
    val wakeWordPhraseId: String = WakeWordPhrases.DEFAULT.id,
    /**
     * User-supplied Picovoice Access Key (from https://console.picovoice.ai)
     * required for Porcupine to initialize at all. Never bundled with the
     * app — Porcupine access keys are per-account and free-tier keys are
     * rate/device limited, so shipping one in the repo would be both a
     * secret leak and a shared-quota liability for every install.
     */
    val porcupineAccessKey: String = ""
)
