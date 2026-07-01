package com.djassistant.feature.media.impl

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.djassistant.feature.media.MediaPlaybackState

/**
 * Pure mapping from a cached (metadata, playbackState) snapshot to
 * [MediaPlaybackState]. Takes the raw values delivered by
 * MediaController.Callback rather than a live MediaController, so it never
 * triggers a MediaSession IPC call itself.
 */
internal object MediaMetadataMapper {

    fun map(
        packageName: String?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState?,
        appName: String?
    ): MediaPlaybackState = MediaPlaybackState(
        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
        trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
        hasAlbumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null,
        positionMs = playbackState?.position ?: 0L,
        durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        activeAppPackage = packageName,
        activeAppName = appName
    )
}
