package com.djassistant.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ServiceStateHolder @Inject constructor() {

    private val _mode = MutableStateFlow<ServiceMode>(ServiceMode.Stopped)
    val mode: StateFlow<ServiceMode> = _mode.asStateFlow()

    private val _lastCommandInfo = MutableStateFlow<String?>(null)
    val lastCommandInfo: StateFlow<String?> = _lastCommandInfo.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    fun updateMode(mode: ServiceMode) {
        _mode.value = mode
    }

    fun updateLastCommandInfo(info: String?) {
        _lastCommandInfo.value = info
    }

    fun updateLastRecognizedText(text: String) {
        _lastRecognizedText.value = text
    }
}
