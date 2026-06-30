package com.djassistant.service

import android.content.Context
import android.content.Intent
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DjServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) : ServiceController {

    override fun start() {
        DjLogger.service("Starting DjForegroundService")
        val intent = Intent(context, DjForegroundService::class.java).apply {
            action = DjForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    override fun stop() {
        DjLogger.service("Stopping DjForegroundService")
        val intent = Intent(context, DjForegroundService::class.java).apply {
            action = DjForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
