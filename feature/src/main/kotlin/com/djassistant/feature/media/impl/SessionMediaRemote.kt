package com.djassistant.feature.media.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.media.MediaPlaybackState
import com.djassistant.feature.media.MediaRemote
import com.djassistant.feature.media.MediaStateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Full MediaSession integration will be added in Sprint 7.
// Currently delegates control to KeyEventMediaRemote and returns empty state.
@Singleton
class SessionMediaRemote @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyEventMediaRemote: KeyEventMediaRemote
) : MediaRemote by keyEventMediaRemote, MediaStateProvider {

    private val _state = MutableStateFlow(MediaPlaybackState())
    override val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    override suspend fun getSnapshot(): MediaPlaybackState {
        DjLogger.media("getSnapshot() — stub, Sprint 7")
        return _state.value
    }
}
