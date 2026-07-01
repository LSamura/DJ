package com.djassistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class DjForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.djassistant.action.START"
        const val ACTION_STOP = "com.djassistant.action.STOP"
    }

    @Inject lateinit var serviceStateHolder: ServiceStateHolder
    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Tracks whether startForeground() has already succeeded, so a repeated
    // ACTION_START does not attempt to enter the foreground twice.
    private var isForegroundActive = false

    override fun onCreate() {
        super.onCreate()
        DjLogger.service("onCreate()")
        notificationHelper.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DjLogger.service("onStartCommand action=${intent?.action}")
        return when (intent?.action) {
            ACTION_START -> {
                startForegroundSafely()
                START_STICKY
            }
            ACTION_STOP -> {
                stopServiceCleanly()
                START_NOT_STICKY
            }
            else -> {
                // START_STICKY redelivery after the process was killed comes
                // in with a null intent by contract — that is exactly the
                // "please resume" signal, not a reason to stop. Treat it the
                // same as ACTION_START so the voice pipeline it starts also
                // comes back automatically (Sprint 3 requirement).
                DjLogger.service("Null-action onStartCommand — resuming after restart")
                startForegroundSafely()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        DjLogger.service("onDestroy()")
        if (isForegroundActive) {
            isForegroundActive = false
            serviceStateHolder.updateMode(ServiceMode.Stopped)
        }
        stopVoiceService()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely() {
        if (isForegroundActive) {
            DjLogger.service("startForeground ignored — already in foreground")
            serviceStateHolder.updateMode(ServiceMode.Running)
            return
        }
        serviceStateHolder.updateMode(ServiceMode.Starting)
        try {
            val notification = notificationHelper.buildServiceNotification()
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            isForegroundActive = true
            serviceStateHolder.updateMode(ServiceMode.Running)
            DjLogger.service("Foreground service started")
            startVoiceService()
        } catch (e: Exception) {
            // On Android 14+ startForeground with a microphone type throws if
            // RECORD_AUDIO is missing. Never let that crash the process.
            DjLogger.serviceError("Failed to start foreground service", e)
            isForegroundActive = false
            serviceStateHolder.updateMode(
                ServiceMode.Error(e.message ?: "не удалось запустить сервис")
            )
            stopSelf()
        }
    }

    private fun stopServiceCleanly() {
        DjLogger.service("Stopping foreground service")
        isForegroundActive = false
        serviceStateHolder.updateMode(ServiceMode.Stopped)
        stopVoiceService()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { DjLogger.serviceError("stopForeground failed", it) }
        stopSelf()
    }

    private fun startVoiceService() {
        runCatching { startService(Intent(this, DjVoiceService::class.java)) }
            .onFailure { DjLogger.serviceError("Failed to start DjVoiceService", it) }
    }

    private fun stopVoiceService() {
        runCatching { stopService(Intent(this, DjVoiceService::class.java)) }
            .onFailure { DjLogger.serviceError("Failed to stop DjVoiceService", it) }
    }
}
