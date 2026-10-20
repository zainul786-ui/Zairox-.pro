package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

/**
 * Dedicated low-latency streaming PCM audio player for real-time AI speech synthesis.
 * Uses AudioTrack in STREAM mode with USAGE_VOICE_COMMUNICATION for hardware Acoustic Echo Cancellation (AEC).
 * Supports instantaneous flush and halt for zero-latency user barge-in interruptions.
 */
class RealtimeAudioPlayer(
    private val sampleRate: Int = 24000 // Gemini Live outputs 24kHz 16-bit mono PCM
) {
    companion object {
        private const val TAG = "RealtimeAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    @Volatile private var isPlaying = false
    @Volatile private var isFlushing = false

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2 / 10) // ~200ms buffer

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
        }
    }

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val chunk = audioQueue.take()
                    if (isFlushing) continue

                    val track = audioTrack ?: continue
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }

                    if (!isPlaying) {
                        isPlaying = true
                        onPlaybackStarted?.invoke()
                    }

                    var offset = 0
                    while (offset < chunk.size && !isFlushing && isActive) {
                        val written = track.write(chunk, offset, chunk.size - offset)
                        if (written > 0) {
                            offset += written
                        } else {
                            break
                        }
                    }

                    if (audioQueue.isEmpty() && !isFlushing) {
                        isPlaying = false
                        onPlaybackEnded?.invoke()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack playback error: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Enqueue a PCM audio chunk (16-bit Mono 24kHz) received from live stream.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (isFlushing || pcmChunk.isEmpty()) return
        audioQueue.offer(pcmChunk)
    }

    /**
     * Instantly halt playback and flush all pending buffers within milliseconds.
     * CRITICAL for user barge-in / interruption.
     */
    fun stopAndFlush() {
        isFlushing = true
        audioQueue.clear()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        } finally {
            isPlaying = false
            isFlushing = false
            onPlaybackEnded?.invoke()
        }
    }

    fun isSpeaking(): Boolean {
        return isPlaying || !audioQueue.isEmpty()
    }

    fun release() {
        stopAndFlush()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
