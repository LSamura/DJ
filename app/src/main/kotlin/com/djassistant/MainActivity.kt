package com.djassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.service.ServiceController
import com.djassistant.ui.navigation.DjNavGraph
import com.djassistant.ui.permissions.AppPermissions
import com.djassistant.ui.theme.DjTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serviceController: ServiceController
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        autoStartServiceIfNeeded()

        setContent {
            DjTheme {
                DjNavGraph(appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }

    private fun autoStartServiceIfNeeded() {
        val autoStart = runBlocking {
            settingsRepository.settings.first().autoStartService
        }
        if (!autoStart) return

        // Only auto-start when every required permission is already granted.
        // Without them the foreground service cannot enter the microphone
        // foreground type, so we skip silently and let the user press Start
        // (which triggers the permission flow) instead of failing on launch.
        val missing = AppPermissions.missing(this)
        if (missing.isEmpty()) {
            serviceController.start()
        } else {
            DjLogger.permission(
                "Auto-start skipped — missing permissions: " +
                    missing.joinToString { AppPermissions.displayName(it) }
            )
        }
    }
}
