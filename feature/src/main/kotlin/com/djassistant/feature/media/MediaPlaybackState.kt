package com.djassistant.feature.media

data class MediaPlaybackState(
    val isPlaying: Boolean = false,
    val trackTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val hasAlbumArt: Boolean = false,
    val volumePercent: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeAppPackage: String? = null,
    val activeAppName: String? = null,
    val playbackSource: PlaybackSource = PlaybackSource.NO_ACTIVE_SESSION
)
