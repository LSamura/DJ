package com.djassistant.core.logging

import timber.log.Timber

object DjLogger {

    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
        DjLogBuffer.record(DjLogBuffer.Level.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
        DjLogBuffer.record(DjLogBuffer.Level.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
        DjLogBuffer.record(DjLogBuffer.Level.WARN, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
        val detail = if (throwable != null) "$message — ${throwable.javaClass.simpleName}: ${throwable.message}" else message
        DjLogBuffer.record(DjLogBuffer.Level.ERROR, tag, detail)
    }

    fun voice(message: String) = d("DJ/Voice", message)
    fun intent(message: String) = d("DJ/Intent", message)
    fun command(message: String) = d("DJ/Command", message)
    fun media(message: String) = d("DJ/Media", message)
    fun service(message: String) = d("DJ/Service", message)

    fun permission(message: String) = d("DJ/Permission", message)
    fun permissionError(message: String, throwable: Throwable? = null) =
        e("DJ/Permission", message, throwable)
    fun serviceError(message: String, throwable: Throwable? = null) =
        e("DJ/Service", message, throwable)
    fun voiceError(message: String, throwable: Throwable? = null) =
        e("DJ/Voice", message, throwable)
}
