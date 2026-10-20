package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

interface ContinuousAudioListener {
    /** Called immediately when genuine user speech begins (for instant barge-in interruption) */
    fun onUserSpeechStarted()
    /** Real-time microphone RMS volume level in dB (0..90) */
    fun onAudioLevel(rmsDb: Float)
    /** Raw PCM 16kHz audio frame streamed in real time (for WebSocket streaming) */
    fun onRawAudioChunk(pcmBytes: ByteArray)
    /** Called when user completes an utterance in turn-fallback mode */
    fun onUserSpeechEnded(speechBuffer: ShortArray)
    /** Critical capture error (e.g. mic permission revoked or hardware error) */
    fun onCaptureError(message: String)
}

/**
 * Dedicated persistent audio capture engine.
 * Maintains a single, continuous AudioRecord session that NEVER turns off and on
 * between conversation turns, sentences, or AI responses.
 *
 * Utilizes AudioSource.VOICE_COMMUNICATION to activate hardware Acoustic Echo Cancellation (AEC)
 * and Noise Suppression (NS).
 */
class ContinuousAudioCaptureEngine(
    val sampleRate: Int = 16000,
    private val frameSizeSamples: Int = 320 // 20 ms at 16kHz
) {
    companion object {
        private const val TAG = "ContinuousAudioEngine"
        private const val PRE_ROLL_FRAMES = 15 // ~300 ms pre-roll to prevent first syllable clipping
        private const val MIN_SPEECH_DURATION_FRAMES = 10 // ~200 ms minimum speech duration (filters clicks/coughs)
        private const val SPEECH_START_CONSECUTIVE_FRAMES = 3 // 60 ms continuous speech energy to trigger onset
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var captureJob: Job? = null
    @Volatile private var isCapturing = false

    // Configurable VAD and audio processing parameters
    @Volatile var vadSensitivity: Float = 0.65f // 0.1 (low) to 1.0 (high)
    @Volatile var endOfSpeechSilenceMs: Int = 700
    @Volatile var isHardwareEchoCancellationEnabled: Boolean = true
    @Volatile var isHardwareNoiseSuppressionEnabled: Boolean = true

    // Circular pre-roll buffer
    private val preRollBuffer = ArrayDeque<ShortArray>(PRE_ROLL_FRAMES)
    // Utterance accumulator for fallback speech recognition
    private val speechAccumulator = ArrayList<ShortArray>()

    fun isAcousticEchoCancelerSupported(): Boolean = AcousticEchoCanceler.isAvailable()
    fun isNoiseSuppressorSupported(): Boolean = NoiseSuppressor.isAvailable()
    fun isCurrentlyCapturing(): Boolean = isCapturing

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope, listener: ContinuousAudioListener) {
        if (isCapturing) {
            Log.d(TAG, "Audio capture is already running continuously.")
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufSize, frameSizeSamples * 4)

        try {
            // Priority 1: AudioSource.VOICE_COMMUNICATION for hardware AEC + NS
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Priority 2: Fallback to MIC if VOICE_COMMUNICATION is blocked on certain hardware
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener.onCaptureError("AudioRecord initialization failed. State not initialized.")
                return
            }

            // Enable hardware audio effects on this recording session
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                if (isHardwareEchoCancellationEnabled && isAcousticEchoCancelerSupported()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
                    }
                }

                if (isHardwareNoiseSuppressionEnabled && isNoiseSuppressorSupported()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                            enabled = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                    }
                }

                try {
                    if (AutomaticGainControl.isAvailable()) {
                        gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                            enabled = true
                        }
                    }
                } catch (_: Exception) {}
            }

            audioRecord?.startRecording()
            isCapturing = true

        } catch (e: Exception) {
            isCapturing = false
            listener.onCaptureError("Failed to start continuous audio capture: ${e.message}")
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(frameSizeSamples)
            val byteBuffer = ByteArray(frameSizeSamples * 2)
            var ambientNoiseFloorDb = 35.0
            var consecutiveSpeechFrames = 0
            var silenceFramesCount = 0
            var isUserCurrentlySpeaking = false

            synchronized(preRollBuffer) {
                preRollBuffer.clear()
            }
            speechAccumulator.clear()

            while (isActive && isCapturing) {
                val record = audioRecord ?: break
                val readSamples = record.read(audioBuffer, 0, frameSizeSamples)
                if (readSamples <= 0) continue

                // Convert 16-bit PCM shorts to little-endian bytes for streaming transmission
                val byteBufferWrap = ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN)
                byteBufferWrap.clear()
                for (i in 0 until readSamples) {
                    byteBufferWrap.putShort(audioBuffer[i])
                }
                val rawBytes = byteBuffer.copyOf(readSamples * 2)

                // 1. Emit raw PCM byte chunk to real-time streaming pipeline
                listener.onRawAudioChunk(rawBytes)

                // 2. Compute Frame RMS Energy in decibels
                var sumSquare = 0.0
                for (i in 0 until readSamples) {
                    val s = audioBuffer[i].toDouble()
                    sumSquare += s * s
                }
                val rms = sqrt(sumSquare / readSamples)
                val currentRmsDb = if (rms > 1.0) 20.0 * log10(rms) else 0.0
                val normalizedDb = currentRmsDb.toFloat().coerceIn(0f, 90f)

                // Emit audio level for real-time visualizer
                listener.onAudioLevel(normalizedDb)

                // Dynamic Speech Energy Threshold Calculation
                val sensitivityMargin = 22.0 - (vadSensitivity * 16.0).coerceIn(4.0, 20.0)
                val speechThresholdDb = max(ambientNoiseFloorDb + sensitivityMargin, 42.0)
                val isSpeechFrame = currentRmsDb >= speechThresholdDb

                if (!isUserCurrentlySpeaking) {
                    // Update ambient noise floor smoothly during non-speech periods
                    if (currentRmsDb < speechThresholdDb - 4.0) {
                        ambientNoiseFloorDb = ambientNoiseFloorDb * 0.95 + currentRmsDb * 0.05
                    }

                    // Save frame into pre-roll circular buffer
                    val frameCopy = audioBuffer.copyOf(readSamples)
                    synchronized(preRollBuffer) {
                        if (preRollBuffer.size >= PRE_ROLL_FRAMES) {
                            preRollBuffer.removeFirst()
                        }
                        preRollBuffer.addLast(frameCopy)
                    }

                    if (isSpeechFrame) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= SPEECH_START_CONSECUTIVE_FRAMES) {
                            // User speech onset confirmed!
                            isUserCurrentlySpeaking = true
                            consecutiveSpeechFrames = 0
                            silenceFramesCount = 0

                            // Fire immediate interruption callback
                            listener.onUserSpeechStarted()

                            // Prepend pre-roll buffer to prevent first syllable clipping
                            speechAccumulator.clear()
                            synchronized(preRollBuffer) {
                                speechAccumulator.addAll(preRollBuffer)
                                preRollBuffer.clear()
                            }
                            speechAccumulator.add(frameCopy)
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                } else {
                    // In-speech state
                    speechAccumulator.add(audioBuffer.copyOf(readSamples))

                    if (isSpeechFrame) {
                        silenceFramesCount = 0
                    } else {
                        silenceFramesCount++
                        val requiredSilenceFrames = (endOfSpeechSilenceMs / 20).coerceAtLeast(15)

                        if (silenceFramesCount >= requiredSilenceFrames) {
                            // User completed utterance
                            isUserCurrentlySpeaking = false
                            silenceFramesCount = 0

                            if (speechAccumulator.size >= MIN_SPEECH_DURATION_FRAMES) {
                                val totalSamples = speechAccumulator.sumOf { it.size }
                                val fullUtterance = ShortArray(totalSamples)
                                var destPos = 0
                                for (frame in speechAccumulator) {
                                    System.arraycopy(frame, 0, fullUtterance, destPos, frame.size)
                                    destPos += frame.size
                                }
                                listener.onUserSpeechEnded(fullUtterance)
                            }
                            speechAccumulator.clear()
                        }
                    }
                }
            }
        }
    }

    /**
     * Stops continuous capture and releases all hardware microphone resources.
     * ONLY called when the user explicitly ends the live session.
     */
    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            gainControl?.release()
        } catch (_: Exception) {}
        gainControl = null

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        synchronized(preRollBuffer) {
            preRollBuffer.clear()
        }
        speechAccumulator.clear()
    }
}
