package com.example.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface GeminiLiveSessionListener {
    fun onConnected()
    fun onAudioChunkReceived(pcmChunk: ByteArray)
    fun onTranscriptReceived(text: String, isUser: Boolean)
    fun onInterrupted()
    fun onTurnComplete()
    fun onError(message: String)
    fun onClosed()
}

/**
 * True Realtime Live Voice Session over WebSockets for Gemini Multimodal Live API.
 * Uses the official Google GenerativeService.BidiGenerateContent protocol.
 * Streams 16kHz PCM audio up, receives 24kHz PCM audio down, and supports native server-side barge-in.
 */
class GeminiLiveRealtimeSession(
    private val scope: CoroutineScope,
    private val apiKey: String,
    private val model: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
    private val systemPrompt: String,
    private val voiceName: String = "Puck",
    private val listener: GeminiLiveSessionListener
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val WS_HOST = "generativelanguage.googleapis.com"
        private const val WS_PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private var reconnectAttempts = 0
    private var pingJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive WebSocket stream
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (apiKey.isBlank()) {
            listener.onError("Gemini API key is required for Live Realtime Voice.")
            return
        }

        isSessionActive.set(true)
        val wsUrl = "wss://$WS_HOST$WS_PATH?key=${apiKey.trim()}"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket connection established.")
                isConnected.set(true)
                reconnectAttempts = 0
                sendSetupMessage(ws)
                startKeepAlive()
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket failure: ${t.message}", t)
                isConnected.set(false)
                if (isSessionActive.get()) {
                    handleReconnect("Connection interrupted: ${t.message ?: "Network error"}")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini Live WebSocket closed: $reason (code $code)")
                isConnected.set(false)
                listener.onClosed()
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", model)

                    val generationConfig = JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", voiceName)
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)

                    if (systemPrompt.isNotBlank()) {
                        val sysInstruction = JSONObject().apply {
                            val parts = JSONArray().put(JSONObject().put("text", systemPrompt))
                            put("parts", parts)
                        }
                        put("systemInstruction", sysInstruction)
                    }
                }
                put("setup", setup)
            }

            ws.send(setupObj.toString())
            Log.d(TAG, "Sent setup message for model $model with voice $voiceName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message: ${e.message}", e)
        }
    }

    /**
     * Streams 16kHz PCM audio chunk to the Gemini Multimodal Live API.
     */
    fun sendRealtimeAudio(pcmBytes: ByteArray) {
        if (!isConnected.get() || pcmBytes.isEmpty()) return

        val ws = webSocket ?: return
        val base64Chunk = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)

        try {
            val message = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Chunk)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            ws.send(message.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stream audio chunk: ${e.message}")
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                // 1. Check for server-side user interruption (Barge-in)
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server detected user barge-in interruption.")
                    listener.onInterrupted()
                    return
                }

                // 2. Parse modelTurn audio stream and transcripts
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio PCM chunk
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotBlank()) {
                                    val decodedPcm = Base64.decode(base64Data, Base64.DEFAULT)
                                    listener.onAudioChunkReceived(decodedPcm)
                                }
                            }

                            // Text transcript
                            val text = part.optString("text", "")
                            if (text.isNotBlank()) {
                                listener.onTranscriptReceived(text, isUser = false)
                            }
                        }
                    }
                }

                // 3. Check for turn completion
                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    private fun handleReconnect(errorMsg: String) {
        if (!isSessionActive.get()) return

        if (reconnectAttempts < 3) {
            reconnectAttempts++
            val backoffMs = (reconnectAttempts * 1000L).coerceAtMost(4000L)
            Log.d(TAG, "Reconnecting Gemini Live session in ${backoffMs}ms (attempt $reconnectAttempts)")
            scope.launch(Dispatchers.IO) {
                delay(backoffMs)
                if (isSessionActive.get()) {
                    connect()
                }
            }
        } else {
            listener.onError(errorMsg)
        }
    }

    private fun startKeepAlive() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isConnected.get()) {
                delay(15000)
                // Connection is kept active by OkHttp's pingInterval and incoming frames
            }
        }
    }

    fun isLive(): Boolean = isConnected.get()

    fun close() {
        isSessionActive.set(false)
        isConnected.set(false)
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.close(1000, "User ended live voice session")
        } catch (_: Exception) {}
        webSocket = null
    }
}
