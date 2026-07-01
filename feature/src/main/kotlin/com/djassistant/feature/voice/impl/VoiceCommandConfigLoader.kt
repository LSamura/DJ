package com.djassistant.feature.voice.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

private const val COMMANDS_ASSET_PATH = "commands.json"

/**
 * Loads the phrase → intent-key mapping from `assets/commands.json` instead
 * of hardcoding trigger lists inside command classes. Adding a synonym is a
 * JSON edit; swapping the matcher for a local language model later only
 * means replacing how this map is *consumed* (see [KeywordIntentRecognizer]),
 * not how it is produced.
 */
@Singleton
class VoiceCommandConfigLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val phrases: Map<String, List<String>> by lazy { loadConfig() }

    /** Intent key (e.g. "PAUSE") -> normalized phrases that trigger it. */
    fun phrasesByIntent(): Map<String, List<String>> = phrases

    private fun loadConfig(): Map<String, List<String>> = runCatching {
        val json = context.assets.open(COMMANDS_ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        root.keys().asSequence().associateWith { key ->
            val array = root.getJSONArray(key)
            (0 until array.length()).map { i -> array.getString(i).lowercase().trim() }
        }
    }.onFailure {
        DjLogger.voiceError("Failed to load commands.json", it)
    }.getOrDefault(emptyMap())
}
