package com.djassistant.feature.voice.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

private const val SAMPLE_RATE_HZ = 16_000

/**
 * Captures 16 kHz mono PCM16 audio via [AudioRecord] — the format Vosk
 * expects. The recording loop is cooperative: it exits as soon as either
 * [stop] flips [isRecording] to false or the collecting coroutine is
 * cancelled, and always releases the microphone in its `finally` block.
 */
@Singleton
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording

    override fun start(): Flow<ByteArray> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            DjLogger.voiceError("start() called without RECORD_AUDIO permission")
            return@flow
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            DjLogger.voiceError("AudioRecord.getMinBufferSize() failed: $minBufferSize")
            return@flow
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
        } catch (e: SecurityException) {
            DjLogger.voiceError("AudioRecord creation denied", e)
            return@flow
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            DjLogger.voiceError("AudioRecord failed to initialize")
            record.release()
            return@flow
        }

        val buffer = ByteArray(minBufferSize)
        try {
            record.startRecording()
            _isRecording.value = true
            DjLogger.voice("AudioRecord started ($SAMPLE_RATE_HZ Hz mono PCM16)")

            while (_isRecording.value && currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    _audioLevel.value = computeLevel(buffer, read)
                    emit(buffer.copyOf(read))
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            _audioLevel.value = 0f
            DjLogger.voice("AudioRecord released")
        }
    }

    override fun stop() {
        DjLogger.voice("AndroidAudioRecorder.stop()")
        _isRecording.value = false
    }

    private fun computeLevel(buffer: ByteArray, length: Int): Float {
        var sumOfSquares = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sumOfSquares += sample * sample
            i += 2
        }
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f
        val rms = sqrt(sumOfSquares / sampleCount)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }
}
