package com.djassistant

import android.app.Application
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DjApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DjLogger.init(isDebug = BuildConfig.DEBUG)
        DjLogger.service("DjApplication.onCreate()")
    }
}
