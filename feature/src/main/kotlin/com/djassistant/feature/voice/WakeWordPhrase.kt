package com.djassistant.feature.voice

/**
 * One selectable activation phrase for Wake Mode.
 *
 * Porcupine (unlike Vosk's open grammar) needs a keyword file trained
 * specifically for each phrase — there is no generic "list of words" the
 * engine can just match against. So "supporting a list of phrases" means:
 * a registry of [WakeWordPhrase] entries, each pointing at its own trained
 * asset, with [DjSettings.wakeWordPhraseId] selecting which one is active.
 * Only [DJ] ("Диджей") ships today, but adding a new phrase later —
 * "Музыка", "Ассистент", a user's own custom wake word trained via the
 * Picovoice Console — is just adding another entry here plus its asset
 * files; nothing in [com.djassistant.service.voice.VoiceEngine] or the
 * settings UI needs to change.
 */
data class WakeWordPhrase(
    val id: String,
    val displayName: String,
    /**
     * Path, relative to `assets/`, to the trained Porcupine `.ppn` keyword
     * file for this phrase. Keyword files are trained per-phrase via the
     * Picovoice Console (https://console.picovoice.ai) and are NOT bundled
     * in this repository — see PROJECT_STATE.md for what must be added
     * locally before Wake Mode can actually detect this phrase.
     */
    val keywordAssetPath: String,
    /**
     * Path, relative to `assets/`, to the Porcupine language model (`.pv`)
     * required for non-English keywords (e.g. `porcupine_params_ru.pv` for
     * Russian). Null only for phrases trained against Porcupine's default
     * English model.
     */
    val modelAssetPath: String?
)

object WakeWordPhrases {
    val DJ = WakeWordPhrase(
        id = "dj",
        displayName = "Диджей",
        keywordAssetPath = "porcupine/dj_ru_android.ppn",
        modelAssetPath = "porcupine/porcupine_params_ru.pv"
    )

    /** All phrases the user can pick from in Settings. Currently just [DJ] — see class doc for how to extend this. */
    val ALL: List<WakeWordPhrase> = listOf(DJ)

    val DEFAULT: WakeWordPhrase = DJ

    fun byId(id: String): WakeWordPhrase = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
