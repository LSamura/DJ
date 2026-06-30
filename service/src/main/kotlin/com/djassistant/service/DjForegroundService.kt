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

    override fun onCreate() {
        super.onCreate()
        DjLogger.service("onCreate()")
        notificationHelper.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DjLogger.service("onStartCommand action=${intent?.action}")
        return when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                serviceStateHolder.updateMode(ServiceMode.Running)
                START_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        DjLogger.service("onDestroy()")
        serviceStateHolder.updateMode(ServiceMode.Stopped)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        DjLogger.service("Foreground service started")
    }
}
