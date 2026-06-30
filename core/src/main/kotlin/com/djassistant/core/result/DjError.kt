package com.djassistant.core.result

sealed class DjError {
    data class AsrError(val message: String) : DjError()
    data class MediaError(val message: String) : DjError()
    data class PermissionDenied(val permission: String) : DjError()
    data class ServiceNotRunning(val detail: String = "") : DjError()
    data class Unknown(val message: String) : DjError()
}
