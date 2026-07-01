package com.djassistant.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.service.ServiceController
import com.djassistant.service.ServiceMode
import com.djassistant.service.ServiceStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val serviceStateHolder: ServiceStateHolder,
    private val serviceController: ServiceController,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val serviceMode: StateFlow<ServiceMode> = serviceStateHolder.mode
    val lastCommandInfo: StateFlow<String?> = serviceStateHolder.lastCommandInfo

    val showDebugScreen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showDebugScreen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun startService() = serviceController.start()

    fun stopService() = serviceController.stop()

    /** Called when the user has granted every required permission. */
    fun onPermissionsGranted() {
        DjLogger.permission("All required permissions granted — starting service")
        serviceController.start()
    }

    fun onPermissionsDenied(denied: List<String>) {
        DjLogger.permissionError("Permissions denied by user: ${denied.joinToString()}")
    }

    fun onPermissionsPermanentlyDenied(denied: List<String>) {
        DjLogger.permissionError("Permissions permanently denied: ${denied.joinToString()}")
    }
}
