package com.djassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.service.ServiceController
import com.djassistant.ui.navigation.DjNavGraph
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
        if (autoStart) {
            serviceController.start()
        }
    }
}
