package com.djassistant.feature.settings.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dj_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val AUTO_START_SERVICE = booleanPreferencesKey("auto_start_service")
        val SHOW_DEBUG_SCREEN = booleanPreferencesKey("show_debug_screen")
        val VOSK_CONFIDENCE_THRESHOLD = floatPreferencesKey("vosk_confidence_threshold")
    }

    override val settings: Flow<DjSettings> = context.dataStore.data.map { prefs ->
        DjSettings(
            autoStartService = prefs[Keys.AUTO_START_SERVICE] ?: false,
            showDebugScreen = prefs[Keys.SHOW_DEBUG_SCREEN] ?: false,
            voskConfidenceThreshold = prefs[Keys.VOSK_CONFIDENCE_THRESHOLD] ?: 0.5f
        )
    }

    override suspend fun setAutoStartService(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_SERVICE] = enabled }
    }

    override suspend fun setShowDebugScreen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DEBUG_SCREEN] = enabled }
    }

    override suspend fun setVoskConfidenceThreshold(value: Float) {
        context.dataStore.edit { it[Keys.VOSK_CONFIDENCE_THRESHOLD] = value }
    }
}
