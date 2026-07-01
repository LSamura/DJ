package com.djassistant.service

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.media.impl.MediaControllerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Required solely to unlock MediaSessionManager.getActiveSessions(): the
 * platform only grants that call to a component holding notification-listener
 * access. This service does not read or act on notification content — its
 * only job is discovering active MediaSession instances for
 * [MediaControllerRepository], the single Media Layer entry point.
 */
@AndroidEntryPoint
class DjNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var mediaControllerRepository: MediaControllerRepository

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mediaControllerRepository.updateSessions(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DjLogger.service("NotificationListener connected")
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, DjNotificationListenerService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionListener, component)
            mediaControllerRepository.updateSessions(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            DjLogger.e("DJ/Media", "Notification listener access not granted", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DjLogger.service("NotificationListener disconnected")
        mediaControllerRepository.clearSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Not used — media session discovery relies on
        // OnActiveSessionsChangedListener, not notification content.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not used — see onNotificationPosted.
    }
}
