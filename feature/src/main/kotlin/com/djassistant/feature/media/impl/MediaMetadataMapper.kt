package com.djassistant.feature.media.impl

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import com.djassistant.feature.media.MediaPlaybackState

/** Pure mapping from a framework [MediaController] snapshot to [MediaPlaybackState]. */
internal object MediaMetadataMapper {

    fun map(controller: MediaController, appName: String?): MediaPlaybackState {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        return MediaPlaybackState(
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
            trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            hasAlbumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null,
            positionMs = playbackState?.position ?: 0L,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            activeAppPackage = controller.packageName,
            activeAppName = appName
        )
    }
}
