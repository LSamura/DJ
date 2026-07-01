package com.djassistant.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Snapshot of the runtime permissions the app cares about. */
data class PermissionStatus(
    val microphone: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean get() = microphone && notifications
}

/**
 * Central definition of the runtime permissions required before the voice
 * service may start. Kept deliberately small (Sprint 1 scope):
 *  - RECORD_AUDIO      — always required (microphone foreground service type)
 *  - POST_NOTIFICATIONS — Android 13+ only; implicitly granted below that
 */
object AppPermissions {

    fun required(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Permissions from [required] that are not yet granted. */
    fun missing(context: Context): List<String> =
        required().filter { !isGranted(context, it) }

    fun status(context: Context): PermissionStatus = PermissionStatus(
        microphone = isGranted(context, Manifest.permission.RECORD_AUDIO),
        notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    )

    /** Human-readable name for a permission, used in status messages. */
    fun displayName(permission: String): String = when (permission) {
        Manifest.permission.RECORD_AUDIO -> "Микрофон"
        Manifest.permission.POST_NOTIFICATIONS -> "Уведомления"
        else -> permission.substringAfterLast('.')
    }
}

/** Resolve the hosting [Activity] from a Compose [Context], if any. */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
