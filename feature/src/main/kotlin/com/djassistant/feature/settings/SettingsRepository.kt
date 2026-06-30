package com.djassistant.feature.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<DjSettings>
    suspend fun setAutoStartService(enabled: Boolean)
    suspend fun setShowDebugScreen(enabled: Boolean)
    suspend fun setVoskConfidenceThreshold(value: Float)
}
