package com.djassistant.feature.voice.wake.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.wake.WakeWordPhrase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TARGET_DIR_NAME = "porcupine"

/**
 * Ensures a [WakeWordPhrase]'s Porcupine keyword (`.ppn`) and, if needed,
 * language model (`.pv`) files are available as plain files under internal
 * storage — the Porcupine SDK takes file paths, not asset streams, same
 * constraint `VoskModelProvisioner` works around for the Vosk model.
 *
 * IMPORTANT: unlike the Vosk model, these files are NOT bundled in this
 * repository and cannot be generated offline — a Porcupine keyword file is
 * trained per-phrase via the Picovoice Console (https://console.picovoice.ai),
 * which requires an internet connection and a (free-tier available) account.
 * Once trained, the resulting `.ppn`/`.pv` files are placed under
 * `feature/src/main/assets/porcupine/` and used fully offline at runtime —
 * this one-time training step is the same kind of one-time setup the Vosk
 * model itself needed, it just isn't automatable the way copying a
 * pre-built model directory is. See PROJECT_STATE.md for exact filenames.
 */
@Singleton
class PorcupineAssetProvisioner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AssetPaths(val keywordPath: String, val modelPath: String?)

    private val targetDir: File by lazy { File(context.filesDir, TARGET_DIR_NAME) }

    fun hasAssets(phrase: WakeWordPhrase): Boolean =
        assetExists(phrase.keywordAssetPath) &&
            (phrase.modelAssetPath == null || assetExists(phrase.modelAssetPath))

    /** Returns absolute file paths ready for [ai.picovoice.porcupine.Porcupine.Builder], or null if assets are missing/unreadable. */
    suspend fun ensureAssets(phrase: WakeWordPhrase): AssetPaths? = withContext(Dispatchers.IO) {
        if (!hasAssets(phrase)) {
            val missing = buildList {
                add(phrase.keywordAssetPath)
                phrase.modelAssetPath?.let { add(it) }
            }.joinToString(", ")
            DjLogger.voiceError(
                "Missing Porcupine assets for phrase \"${phrase.displayName}\" — " +
                    "add $missing under feature/src/main/assets/"
            )
            return@withContext null
        }
        try {
            val keywordFile = copyIfNeeded(phrase.keywordAssetPath)
            val modelFile = phrase.modelAssetPath?.let { copyIfNeeded(it) }
            AssetPaths(keywordFile.absolutePath, modelFile?.absolutePath)
        } catch (e: Exception) {
            DjLogger.voiceError("Failed to extract Porcupine assets for \"${phrase.displayName}\"", e)
            null
        }
    }

    private fun assetExists(assetPath: String): Boolean = runCatching {
        val parent = assetPath.substringBeforeLast('/', "")
        val name = assetPath.substringAfterLast('/')
        context.assets.list(parent)?.contains(name) == true
    }.getOrDefault(false)

    private fun copyIfNeeded(assetPath: String): File {
        val target = File(targetDir, assetPath.substringAfterLast('/'))
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }
}
