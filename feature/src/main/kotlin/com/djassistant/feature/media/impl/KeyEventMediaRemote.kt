package com.djassistant.feature.media.impl

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.media.MediaRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyEventMediaRemote @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRemote {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun play() {
        DjLogger.media("play()")
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    override fun pause() {
        DjLogger.media("pause()")
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    override fun next() {
        DjLogger.media("next()")
        dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    override fun previous() {
        DjLogger.media("previous()")
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    override fun volumeUp() {
        DjLogger.media("volumeUp()")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun volumeDown() {
        DjLogger.media("volumeDown()")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun dispatchKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
