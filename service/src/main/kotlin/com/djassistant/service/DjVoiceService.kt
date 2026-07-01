package com.djassistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.djassistant.core.logging.DjLogger
import com.djassistant.service.voice.VoiceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the continuous voice pipeline ([VoiceEngine]) as its own Android
 * component, so it can be started/stopped independently while its lifecycle
 * is still driven alongside [DjForegroundService] (which starts and stops
 * this service together with itself — see ADR-022).
 *
 * A plain (non-foreground) Service is enough here: it never needs its own
 * notification, and it only runs while the process is already kept alive by
 * DjForegroundService's own foreground promotion.
 */
@AndroidEntryPoint
class DjVoiceService : Service() {

    @Inject lateinit var voiceEngine: VoiceEngine

    override fun onCreate() {
        super.onCreate()
        DjLogger.service("DjVoiceService.onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DjLogger.service("DjVoiceService.onStartCommand()")
        voiceEngine.start()
        return START_STICKY
    }

    override fun onDestroy() {
        DjLogger.service("DjVoiceService.onDestroy()")
        voiceEngine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
