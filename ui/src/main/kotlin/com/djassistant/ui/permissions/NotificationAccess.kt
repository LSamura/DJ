package com.djassistant.ui.permissions

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * "Notification access" is a special app access, not a runtime permission —
 * it can only be toggled by the user in system settings
 * (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS). The Media Layer needs it
 * to discover active MediaSession instances via
 * MediaSessionManager.getActiveSessions().
 */
object NotificationAccess {

    fun isEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
