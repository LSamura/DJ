package com.djassistant.feature.voice.impl

import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.GrammarBuilder
import com.djassistant.feature.voice.RecognitionResult
import com.djassistant.feature.voice.SpeechRecognizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

private const val SAMPLE_RATE_HZ = 16_000f

/**
 * Offline speech recognition via the Vosk Android SDK, running in Grammar
 * Mode (ADR-004): the recognizer is restricted to the phrase list built by
 * [GrammarBuilder] from `commands.json`, which improves accuracy and lowers
 * CPU cost compared to open-vocabulary recognition.
 *
 * The model itself is provisioned locally by [VoskModelProvisioner] — no
 * network access is ever performed here.
 */
@Singleton
class VoskSpeechRecognizer @Inject constructor(
    private val modelProvisioner: VoskModelProvisioner,
    private val grammarBuilder: GrammarBuilder
) : SpeechRecognizer {

    @Volatile private var model: Model? = null

    override val isReady: Boolean
        get() = model != null

    override fun startListening(audioFlow: Flow<ByteArray>, vocabulary: List<String>?): Flow<RecognitionResult> = flow {
        val modelDir = modelProvisioner.ensureModel()
        if (modelDir == null) {
            DjLogger.voiceError("Vosk model not available — see VoskModelProvisioner")
            return@flow
        }

        val loadedModel = try {
            model ?: Model(modelDir.absolutePath).also { model = it }
        } catch (e: Exception) {
            DjLogger.voiceError("Failed to load Vosk model", e)
            return@flow
        }

        val grammar = vocabulary?.let(::buildGrammarJson) ?: grammarBuilder.build()
        val recognizer = try {
            Recognizer(loadedModel, SAMPLE_RATE_HZ, grammar).apply { setWords(true) }
        } catch (e: Exception) {
            DjLogger.voiceError("Failed to create Vosk recognizer", e)
            return@flow
        }

        DjLogger.voice("Vosk recognizer ready (${if (vocabulary != null) "wake-word grammar" else "command grammar"})")
        try {
            audioFlow.collect { chunk ->
                val isFinal = recognizer.acceptWaveForm(chunk, chunk.size)
                val json = if (isFinal) recognizer.result else recognizer.partialResult
                parseRecognitionResult(json, isFinal)?.let { emit(it) }
            }
        } finally {
            runCatching { recognizer.close() }
        }
    }

    override fun release() {
        DjLogger.voice("VoskSpeechRecognizer.release()")
        runCatching { model?.close() }
        model = null
    }

    private fun buildGrammarJson(phrases: List<String>): String {
        val items = (phrases + "[unk]").joinToString(",") { "\"$it\"" }
        return "[$items]"
    }

    private fun parseRecognitionResult(json: String, isFinal: Boolean): RecognitionResult? = runCatching {
        val root = JSONObject(json)
        if (!isFinal) {
            val partial = root.optString("partial")
            return if (partial.isBlank()) null else RecognitionResult(partial, confidence = null, isFinal = false)
        }

        val text = root.optString("text")
        if (text.isBlank()) return null

        val confidence = root.optJSONArray("result")?.takeIf { it.length() > 0 }?.let { words ->
            var sum = 0.0
            for (i in 0 until words.length()) {
                sum += words.getJSONObject(i).optDouble("conf", 0.0)
            }
            (sum / words.length()).toFloat()
        }
        RecognitionResult(text, confidence = confidence, isFinal = true)
    }.getOrNull()
}
