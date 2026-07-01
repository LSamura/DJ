package com.djassistant.service

import android.content.Context
import android.content.Intent
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DjServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceStateHolder: ServiceStateHolder
) : ServiceController {

    override fun start() {
        val mode = serviceStateHolder.mode.value
        if (mode !is ServiceMode.Stopped && mode !is ServiceMode.Error) {
            DjLogger.service("start() ignored — service already active ($mode)")
            return
        }
        DjLogger.service("Starting DjForegroundService")
        val intent = Intent(context, DjForegroundService::class.java).apply {
            action = DjForegroundService.ACTION_START
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            DjLogger.serviceError("Failed to request foreground service start", e)
            serviceStateHolder.updateMode(
                ServiceMode.Error(e.message ?: "не удалось запустить сервис")
            )
        }
    }

    override fun stop() {
        if (serviceStateHolder.mode.value is ServiceMode.Stopped) {
            DjLogger.service("stop() ignored — service already stopped")
            return
        }
        DjLogger.service("Stopping DjForegroundService")
        val intent = Intent(context, DjForegroundService::class.java).apply {
            action = DjForegroundService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            DjLogger.serviceError("Failed to request service stop", e)
            // Reflect the intended state anyway so the UI is not stuck.
            serviceStateHolder.updateMode(ServiceMode.Stopped)
        }
    }
}
