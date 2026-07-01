package com.djassistant.feature.media.impl

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.media.MediaPlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single point of interaction with active android.media.session.MediaSession
 * instances exposed by other apps (Spotify, AIMP, YouTube Music, etc.).
 *
 * Fed exclusively by [com.djassistant.service.DjNotificationListenerService],
 * the only component allowed to call MediaSessionManager.getActiveSessions()
 * (that call requires notification-listener access). This repository owns the
 * resulting [MediaController] list, keeps a live [MediaPlaybackState] snapshot
 * and forwards transport commands to whichever session looks "active".
 */
@Singleton
class MediaControllerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val _state = MutableStateFlow(MediaPlaybackState())
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    /** Called by the notification listener whenever the active session set changes. */
    @Synchronized
    fun updateSessions(newControllers: List<MediaController>) {
        DjLogger.media("updateSessions: ${newControllers.size} active session(s)")
        unregisterAll()
        controllers = newControllers
        newControllers.forEach(::registerCallback)
        recomputeState()
    }

    /** Called when the notification listener disconnects (no sessions available). */
    @Synchronized
    fun clearSessions() {
        DjLogger.media("clearSessions()")
        unregisterAll()
        controllers = emptyList()
        _state.value = MediaPlaybackState()
    }

    fun dispatchPlay(): Boolean = withActiveController { it.transportControls.play() }
    fun dispatchPause(): Boolean = withActiveController { it.transportControls.pause() }
    fun dispatchNext(): Boolean = withActiveController { it.transportControls.skipToNext() }
    fun dispatchPrevious(): Boolean = withActiveController { it.transportControls.skipToPrevious() }

    private fun withActiveController(action: (MediaController) -> Unit): Boolean {
        val controller = activeController() ?: return false
        return try {
            action(controller)
            true
        } catch (e: Exception) {
            DjLogger.e("DJ/Media", "Transport command failed for ${controller.packageName}", e)
            false
        }
    }

    /** Prefers a session that is actually playing; falls back to the first known one. */
    private fun activeController(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

    private fun registerCallback(controller: MediaController) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = recomputeState()
            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = recomputeState()
            override fun onSessionDestroyed() {
                DjLogger.media("Session destroyed: ${controller.packageName}")
                controllers = controllers.filter { it != controller }
                callbacks.remove(controller)
                recomputeState()
            }
        }
        runCatching { controller.registerCallback(callback) }
            .onFailure { DjLogger.e("DJ/Media", "Failed to register callback for ${controller.packageName}", it) }
        callbacks[controller] = callback
    }

    private fun unregisterAll() {
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        callbacks.clear()
    }

    private fun recomputeState() {
        val controller = activeController()
        _state.value = if (controller == null) {
            MediaPlaybackState()
        } else {
            MediaMetadataMapper.map(controller, appNameFor(controller.packageName))
                .copy(volumePercent = currentVolumePercent())
        }
    }

    private fun currentVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) current * 100 / max else 0
    }

    private fun appNameFor(packageName: String?): String? {
        if (packageName == null) return null
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }
}
