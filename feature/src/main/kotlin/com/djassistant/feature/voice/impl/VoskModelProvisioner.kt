package com.djassistant.feature.voice.impl

import android.content.Context
import com.djassistant.core.logging.DjLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MODEL_ASSET_DIR = "model"
private const val MODEL_DIR_NAME = "vosk-model"
private const val MODEL_MARKER_FILE = "conf/model.conf"

/**
 * Ensures an offline Vosk model directory is available under the app's
 * internal storage.
 *
 * IMPORTANT: the model itself (e.g. `vosk-model-small-ru-*`, ~45 MB) is NOT
 * bundled in this repository — it must be added by the developer under
 * `feature/src/main/assets/model/` before building (see PROJECT_STATE.md for
 * the exact layout expected). Everything this class does is local file I/O;
 * no network access is ever involved, satisfying the "fully offline"
 * requirement even for first-run provisioning.
 */
@Singleton
class VoskModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val targetDir: File by lazy { File(context.filesDir, MODEL_DIR_NAME) }

    /** Returns the ready-to-use model directory, or null if no model is available. */
    suspend fun ensureModel(): File? = withContext(Dispatchers.IO) {
        if (isExtracted(targetDir)) return@withContext targetDir

        if (!assetModelExists()) {
            DjLogger.voiceError(
                "No Vosk model found — add one under feature/src/main/assets/$MODEL_ASSET_DIR"
            )
            return@withContext null
        }

        try {
            copyAssetPath(MODEL_ASSET_DIR, targetDir)
            if (isExtracted(targetDir)) targetDir else null
        } catch (e: Exception) {
            DjLogger.voiceError("Failed to extract Vosk model from assets", e)
            null
        }
    }

    private fun isExtracted(dir: File): Boolean = File(dir, MODEL_MARKER_FILE).exists()

    private fun assetModelExists(): Boolean = runCatching {
        context.assets.list(MODEL_ASSET_DIR)?.isNotEmpty() == true
    }.getOrDefault(false)

    private fun copyAssetPath(assetPath: String, target: File) {
        val children = context.assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            // Leaf file, not a directory.
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child -> copyAssetPath("$assetPath/$child", File(target, child)) }
    }
}
