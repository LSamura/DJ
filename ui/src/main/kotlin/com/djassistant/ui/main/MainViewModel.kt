package com.djassistant.ui.main

import androidx.lifecycle.ViewModel
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.service.ServiceController
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(
    private val serviceStateHolder: ServiceStateHolder,
    private val serviceController: ServiceController,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    val serviceMode: StateFlow<ServiceMode> = serviceStateHolder.mode
    val lastCommandInfo: StateFlow<String?> = serviceStateHolder.lastCommandInfo

    fun startService() = serviceController.start()
    fun stopService() = serviceController.stop()
}
