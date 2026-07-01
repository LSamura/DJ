package com.djassistant.feature.media

/**
 * Diagnostic indicator of which mechanism the app is currently relying on to
 * interact with the active player. Surfaced on the Debug Screen so it is
 * obvious why a command worked (or didn't) without digging into logs.
 */
enum class PlaybackSource {
    /** An android.media.session.MediaController is available; commands and metadata are real. */
    MEDIA_SESSION,

    /** No MediaSession discovered, but system audio is active — commands fall back to media key events. */
    KEY_EVENT_FALLBACK,

    /** No MediaSession and no audio playing anywhere. */
    NO_ACTIVE_SESSION
}
