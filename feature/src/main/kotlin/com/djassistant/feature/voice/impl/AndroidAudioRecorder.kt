package com.djassistant.feature.voice.impl

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.djassistant.core.logging.DjLogger
import com.djassistant.feature.voice.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val SAMPLE_RATE_HZ = 16_000
private const val SCO_CONNECT_TIMEOUT_MS = 2_000L

/**
 * Captures 16 kHz mono PCM16 audio via [AudioRecord] — the format Vosk
 * expects. The recording loop is cooperative: it exits as soon as either
 * [stop] flips [isRecording] to false or the collecting coroutine is
 * cancelled, and always releases the microphone in its `finally` block.
 *
 * Bluetooth SCO is never a standing/background state here — it is only
 * ever requested for the duration of a single [start] call made with
 * `allowBluetooth = true`, and is torn down the moment that session's flow
 * collection ends (normally or via cancellation), restoring A2DP playback
 * as quickly as the platform allows. See ADR on Bluetooth mic lifecycle in
 * DECISIONS.md.
 */
@Singleton
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording

    private val _activeInputSource = MutableStateFlow("—")
    override val activeInputSource: StateFlow<String> = _activeInputSource

    override fun start(allowBluetooth: Boolean): Flow<ByteArray> = flow {
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

        val scoStarted = if (allowBluetooth) {
            tryRouteToBluetooth(record)
        } else {
            _activeInputSource.value = "Встроенный микрофон"
            false
        }

        val buffer = ByteArray(minBufferSize)
        try {
            record.startRecording()
            _isRecording.value = true
            DjLogger.voice("AudioRecord started ($SAMPLE_RATE_HZ Hz mono PCM16, source=${_activeInputSource.value})")

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
            if (scoStarted) stopBluetoothSco()
            _isRecording.value = false
            _audioLevel.value = 0f
            _activeInputSource.value = "—"
            DjLogger.voice("AudioRecord released")
        }
    }

    override fun stop() {
        DjLogger.voice("AndroidAudioRecorder.stop()")
        _isRecording.value = false
    }

    /**
     * Activates Bluetooth SCO and waits (bounded) for the actual connected
     * broadcast before routing input to it — starting to record before SCO
     * is really up would just capture silence over the wrong path. Falls
     * back to the phone microphone if no Bluetooth device is present or the
     * handshake doesn't complete in time.
     */
    private suspend fun tryRouteToBluetooth(record: AudioRecord): Boolean {
        val audioManager = runCatching {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }.getOrNull() ?: run {
            _activeInputSource.value = "Встроенный микрофон"
            return false
        }

        val bluetoothDevice = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }.getOrNull()

        if (bluetoothDevice == null) {
            _activeInputSource.value = "Встроенный микрофон"
            return false
        }

        val connected = awaitBluetoothScoConnected(audioManager)
        if (!connected) {
            DjLogger.voiceError("Bluetooth SCO did not connect in time — using phone microphone instead")
            runCatching { stopBluetoothSco() }
            _activeInputSource.value = "Встроенный микрофон"
            return false
        }

        runCatching { record.preferredDevice = bluetoothDevice }
            .onFailure { DjLogger.voiceError("Failed to set preferred input device", it) }
        _activeInputSource.value = "Bluetooth"
        return true
    }

    private suspend fun awaitBluetoothScoConnected(audioManager: AudioManager): Boolean =
        withTimeoutOrNull(SCO_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        val state = intent.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_ERROR
                        )
                        when (state) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                runCatching { context.unregisterReceiver(this) }
                                if (cont.isActive) cont.resume(true)
                            }
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED, AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                runCatching { context.unregisterReceiver(this) }
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                    }
                }
                context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

                runCatching {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }.onFailure {
                    DjLogger.voiceError("Failed to start Bluetooth SCO", it)
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false

    private fun stopBluetoothSco() {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }.onFailure { DjLogger.voiceError("Failed to stop Bluetooth SCO", it) }
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
