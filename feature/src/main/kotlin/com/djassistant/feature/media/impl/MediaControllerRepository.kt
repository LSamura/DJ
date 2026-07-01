package com.djassistant.feature.media.impl

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.media.MediaPlaybackState
import com.djassistant.feature.media.PlaybackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single point of interaction with active android.media.session.MediaSession
 * instances exposed by other apps (Spotify, AIMP, YouTube Music, etc.).
 *
 * Fed exclusively by [com.djassistant.service.DjNotificationListenerService],
 * the only component allowed to call MediaSessionManager.getActiveSessions()
 * (that call requires notification-listener access). Metadata/PlaybackState
 * snapshots arrive via [MediaController.Callback] and are cached in
 * [snapshots] — the displayed position is then advanced with a lightweight
 * local ticker (`position + elapsed * speed`, per [PlaybackState] semantics)
 * instead of re-querying the session, mirroring how Android Auto / Spotify
 * Connect animate their seek bar between actual state updates.
 */
@Singleton
class MediaControllerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private data class Snapshot(
        val metadata: MediaMetadata?,
        val playbackState: PlaybackState?
    )

    private companion object {
        const val TICK_INTERVAL_MS = 300L
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val _state = MutableStateFlow(MediaPlaybackState())
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    // MediaController delivers callbacks on the thread that registered them
    // (main thread here, since DjNotificationListenerService registers from
    // onListenerConnected()). The ticker runs on the same dispatcher so the
    // snapshot map is never touched concurrently.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private var controllers: List<MediaController> = emptyList()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val snapshots = mutableMapOf<MediaController, Snapshot>()

    /** Called by the notification listener whenever the active session set changes. */
    fun updateSessions(newControllers: List<MediaController>) {
        DjLogger.media("updateSessions: ${newControllers.size} active session(s)")
        unregisterAll()
        controllers = newControllers
        newControllers.forEach(::registerController)
        recomputeState()
    }

    /** Called when the notification listener disconnects (no sessions available). */
    fun clearSessions() {
        DjLogger.media("clearSessions()")
        unregisterAll()
        controllers = emptyList()
        stopTicker()
        _state.value = MediaPlaybackState(playbackSource = fallbackSource())
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
        controllers.firstOrNull { snapshots[it]?.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

    private fun registerController(controller: MediaController) {
        // Seed the cache once directly from the controller; every subsequent
        // update comes from the callback's own parameters below, so no
        // further MediaSession IPC is made per tick or per recompute.
        snapshots[controller] = Snapshot(
            metadata = runCatching { controller.metadata }.getOrNull(),
            playbackState = runCatching { controller.playbackState }.getOrNull()
        )

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                snapshots[controller] = (snapshots[controller] ?: Snapshot(null, null)).copy(playbackState = state)
                recomputeState()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                snapshots[controller] = (snapshots[controller] ?: Snapshot(null, null)).copy(metadata = metadata)
                recomputeState()
            }

            override fun onSessionDestroyed() {
                DjLogger.media("Session destroyed: ${controller.packageName}")
                controllers = controllers.filter { it != controller }
                callbacks.remove(controller)
                snapshots.remove(controller)
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
        snapshots.clear()
    }

    private fun recomputeState() {
        val controller = activeController()
        val newState = if (controller == null) {
            MediaPlaybackState(playbackSource = fallbackSource())
        } else {
            val snapshot = snapshots[controller]
            MediaMetadataMapper.map(
                packageName = controller.packageName,
                metadata = snapshot?.metadata,
                playbackState = snapshot?.playbackState,
                appName = appNameFor(controller.packageName)
            ).copy(volumePercent = currentVolumePercent(), playbackSource = PlaybackSource.MEDIA_SESSION)
        }
        _state.value = newState
        updateTicker(newState.isPlaying)
    }

    private fun fallbackSource(): PlaybackSource =
        if (runCatching { audioManager.isMusicActive }.getOrDefault(false)) {
            PlaybackSource.KEY_EVENT_FALLBACK
        } else {
            PlaybackSource.NO_ACTIVE_SESSION
        }

    private fun updateTicker(isPlaying: Boolean) {
        if (isPlaying) {
            if (tickerJob?.isActive != true) {
                tickerJob = repositoryScope.launch {
                    while (isActive) {
                        delay(TICK_INTERVAL_MS)
                        tickPosition()
                    }
                }
            }
        } else {
            stopTicker()
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Advances the displayed position locally — involves no MediaSession call. */
    private fun tickPosition() {
        val controller = activeController() ?: return
        val playbackState = snapshots[controller]?.playbackState ?: return
        if (playbackState.state != PlaybackState.STATE_PLAYING) return
        val computed = estimatePosition(playbackState)
        val current = _state.value
        if (current.positionMs != computed) {
            _state.value = current.copy(positionMs = computed)
        }
    }

    private fun estimatePosition(playbackState: PlaybackState): Long {
        val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
        val speed = playbackState.playbackSpeed.takeIf { it > 0f } ?: 1f
        val estimated = playbackState.position + (elapsed * speed).toLong()
        val duration = _state.value.durationMs
        return if (duration > 0) estimated.coerceIn(0L, duration) else estimated.coerceAtLeast(0L)
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
