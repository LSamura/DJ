package com.djassistant.core.logging

import timber.log.Timber

object DjLogger {

    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(tag: String, message: String) = Timber.tag(tag).d(message)
    fun i(tag: String, message: String) = Timber.tag(tag).i(message)
    fun w(tag: String, message: String) = Timber.tag(tag).w(message)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).e(throwable, message)

    fun voice(message: String) = d("DJ/Voice", message)
    fun intent(message: String) = d("DJ/Intent", message)
    fun command(message: String) = d("DJ/Command", message)
    fun media(message: String) = d("DJ/Media", message)
    fun service(message: String) = d("DJ/Service", message)
}
