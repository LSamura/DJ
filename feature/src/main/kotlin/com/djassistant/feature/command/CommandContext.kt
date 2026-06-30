package com.djassistant.feature.command

import android.media.AudioManager
import com.djassistant.feature.media.MediaRemote
import com.djassistant.feature.media.MediaStateProvider

data class CommandContext(
    val mediaRemote: MediaRemote,
    val stateProvider: MediaStateProvider,
    val audioManager: AudioManager,
    val rawText: String
)
