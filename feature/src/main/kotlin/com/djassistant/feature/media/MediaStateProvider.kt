package com.djassistant.feature.media

import kotlinx.coroutines.flow.StateFlow

interface MediaStateProvider {
    val state: StateFlow<MediaPlaybackState>
    suspend fun getSnapshot(): MediaPlaybackState
}
