package com.djassistant.feature.media.impl

import com.djassistant.feature.media.MediaPlaybackState
import com.djassistant.feature.media.MediaRemote
import com.djassistant.feature.media.MediaStateProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Single Media Layer entry point, bound as both [MediaRemote] and
 * [MediaStateProvider] in [com.djassistant.di.AppModule]. Transport commands
 * are routed through the active android.media.session.MediaController when
 * one is available (see [MediaControllerRepository]); when no session has
 * been discovered yet — e.g. notification-listener access has not been
 * granted, or no player is running — commands fall back to
 * [KeyEventMediaRemote], which most players still honor via media key
 * broadcasts even without a bound MediaSession.
 */
@Singleton
class SessionMediaRemote @Inject constructor(
    private val mediaControllerRepository: MediaControllerRepository,
    private val keyEventMediaRemote: KeyEventMediaRemote
) : MediaRemote, MediaStateProvider {

    override fun play() {
        if (!mediaControllerRepository.dispatchPlay()) keyEventMediaRemote.play()
    }

    override fun pause() {
        if (!mediaControllerRepository.dispatchPause()) keyEventMediaRemote.pause()
    }

    override fun next() {
        if (!mediaControllerRepository.dispatchNext()) keyEventMediaRemote.next()
    }

    override fun previous() {
        if (!mediaControllerRepository.dispatchPrevious()) keyEventMediaRemote.previous()
    }

    override fun volumeUp() = keyEventMediaRemote.volumeUp()

    override fun volumeDown() = keyEventMediaRemote.volumeDown()

    override val state: StateFlow<MediaPlaybackState> = mediaControllerRepository.state

    override suspend fun getSnapshot(): MediaPlaybackState = mediaControllerRepository.state.value
}
