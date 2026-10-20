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
import java.util.ArrayDeque
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

interface ContinuousAudioListener {
    fun onUserSpeechStarted()
    fun onAudioLevel(rmsDb: Float)
    fun onUserSpeechEnded(speechBuffer: ShortArray)
    fun onCaptureError(message: String)
}

class ContinuousAudioCaptureEngine(
    private val sampleRate: Int = 16000,
    private val frameSizeSamples: Int = 320 // 20 ms at 16kHz
) {
    companion object {
        private const val TAG = "ContinuousAudioEngine"
        private const val PRE_ROLL_FRAMES = 15 // ~300 ms pre-roll
        private const val MIN_SPEECH_DURATION_FRAMES = 12 // ~240 ms minimum speech to ignore clicks/coughs
        private const val SPEECH_START_CONSECUTIVE_FRAMES = 3 // 60 ms continuous speech to trigger start
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var captureJob: Job? = null
    @Volatile private var isCapturing = false

    // VAD Parameters
    @Volatile var vadSensitivity: Float = 0.65f // 0.1 to 1.0
    @Volatile var endOfSpeechSilenceMs: Int = 700
    @Volatile var isHardwareEchoCancellationEnabled: Boolean = true
    @Volatile var isHardwareNoiseSuppressionEnabled: Boolean = true

    // Audio pre-roll circular buffer
    private val preRollBuffer = ArrayDeque<ShortArray>(PRE_ROLL_FRAMES)

    // Current utterance accumulator
    private val speechAccumulator = ArrayList<ShortArray>()

    fun isAcousticEchoCancelerSupported(): Boolean {
        return AcousticEchoCanceler.isAvailable()
    }

    fun isNoiseSuppressorSupported(): Boolean {
        return NoiseSuppressor.isAvailable()
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope, listener: ContinuousAudioListener) {
        if (isCapturing) return

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufSize, frameSizeSamples * 4)

        try {
            // Using VOICE_COMMUNICATION to activate hardware Acoustic Echo Cancellation (AEC)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_COMMUNICATION is restricted
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

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
            listener.onCaptureError("Failed to initialize audio capture: ${e.message}")
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(frameSizeSamples)
            var ambientNoiseFloorDb = 35.0 // Initial estimated ambient noise floor (dB)
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

                // Compute Frame RMS Energy in decibels
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

                // Dynamic Speech Threshold Calculation
                // Sensitivity scale: 0.1 (hard to trigger) to 1.0 (very sensitive)
                val sensitivityMargin = 22.0 - (vadSensitivity * 16.0).coerceIn(4.0, 20.0)
                val speechThresholdDb = max(ambientNoiseFloorDb + sensitivityMargin, 42.0)

                val isSpeechFrame = currentRmsDb >= speechThresholdDb

                if (!isUserCurrentlySpeaking) {
                    // Update ambient noise floor smoothly during non-speech periods
                    if (currentRmsDb < speechThresholdDb - 4.0) {
                        ambientNoiseFloorDb = ambientNoiseFloorDb * 0.95 + currentRmsDb * 0.05
                    }

                    // Store pre-roll frames in circular buffer
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
                            // User started speaking!
                            isUserCurrentlySpeaking = true
                            consecutiveSpeechFrames = 0
                            silenceFramesCount = 0

                            // Fire immediate interruption callback
                            listener.onUserSpeechStarted()

                            // Prepend pre-roll buffer to speech accumulator
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
                    // Currently in speech segment
                    speechAccumulator.add(audioBuffer.copyOf(readSamples))

                    if (isSpeechFrame) {
                        silenceFramesCount = 0
                    } else {
                        silenceFramesCount++
                        val requiredSilenceFrames = (endOfSpeechSilenceMs / 20).coerceAtLeast(15)

                        if (silenceFramesCount >= requiredSilenceFrames) {
                            // User finished speaking!
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

    fun stopCapture() {
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
