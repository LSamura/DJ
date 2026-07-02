package com.djassistant.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * "Draw over other apps" is a special app access (SYSTEM_ALERT_WINDOW), not a
 * runtime permission — granted only via system settings. Required for the
 * compact Listening-window overlay (Sprint 3.2).
 */
object OverlayAccess {

    fun isEnabled(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
}
