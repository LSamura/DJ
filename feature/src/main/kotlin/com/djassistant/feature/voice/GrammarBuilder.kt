package com.djassistant.feature.voice

import com.djassistant.feature.voice.impl.VoiceCommandConfigLoader
import javax.inject.Inject

/**
 * Builds the Vosk Grammar Mode word list (ADR-004) from `commands.json`
 * phrases — no wake word prefix, since Sprint 3 defers activation-phrase
 * gating (see ADR on wake word deferral in DECISIONS.md).
 */
class GrammarBuilder @Inject constructor(
    private val configLoader: VoiceCommandConfigLoader
) {
    fun build(): String {
        val phrases = configLoader.phrasesByIntent().values.flatten().distinct()
        val items = (phrases + "[unk]").joinToString(",") { "\"$it\"" }
        return "[$items]"
    }
}
