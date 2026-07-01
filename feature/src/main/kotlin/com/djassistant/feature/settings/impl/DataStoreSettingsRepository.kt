package com.djassistant.feature.settings.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.djassistant.feature.settings.DjSettings
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.voice.VoiceListeningMode
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
        val LISTENING_MODE = stringPreferencesKey("listening_mode")
        val DIALOG_WINDOW_SECONDS = intPreferencesKey("dialog_window_seconds")
    }

    override val settings: Flow<DjSettings> = context.dataStore.data.map { prefs ->
        DjSettings(
            autoStartService = prefs[Keys.AUTO_START_SERVICE] ?: false,
            showDebugScreen = prefs[Keys.SHOW_DEBUG_SCREEN] ?: false,
            voskConfidenceThreshold = prefs[Keys.VOSK_CONFIDENCE_THRESHOLD] ?: 0.8f,
            listeningMode = prefs[Keys.LISTENING_MODE]?.let { raw ->
                runCatching { VoiceListeningMode.valueOf(raw) }.getOrNull()
            } ?: VoiceListeningMode.CONTINUOUS,
            dialogWindowSeconds = prefs[Keys.DIALOG_WINDOW_SECONDS] ?: 6
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

    override suspend fun setListeningMode(mode: VoiceListeningMode) {
        context.dataStore.edit { it[Keys.LISTENING_MODE] = mode.name }
    }

    override suspend fun setDialogWindowSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.DIALOG_WINDOW_SECONDS] = seconds.coerceIn(3, 15) }
    }
}
