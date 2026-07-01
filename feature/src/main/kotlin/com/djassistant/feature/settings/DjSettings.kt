package com.djassistant.feature.settings

import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.VoiceListeningMode

data class DjSettings(
    val autoStartService: Boolean = false,
    val showDebugScreen: Boolean = false,
    /** Recognitions below this confidence are rejected without executing (default 80%). */
    val voskConfidenceThreshold: Float = 0.8f,
    val listeningMode: VoiceListeningMode = VoiceListeningMode.CONTINUOUS,
    /** How long (seconds) the dialog window stays open after wake-word activation. Range 3-15. */
    val dialogWindowSeconds: Int = 6,
    /** AUTO/PHONE never touch Bluetooth SCO; only BLUETOOTH opts in. */
    val microphoneSource: MicrophoneSource = MicrophoneSource.AUTO
)
