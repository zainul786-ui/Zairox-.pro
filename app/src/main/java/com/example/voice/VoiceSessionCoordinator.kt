package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.ai.AIProvider
import com.example.ai.AIResult
import com.example.ai.GeminiLiveRealtimeSession
import com.example.ai.GeminiLiveSessionListener
import com.example.ai.OpenAICompatibleProvider
import com.example.data.AIProviderConfig
import com.example.data.ConversationRepository
import com.example.data.JarvisPreferences
import com.example.data.ProviderPreset
import com.example.data.VoiceState
import com.example.security.SecureKeyStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Universal Full-Duplex Voice Session Coordinator.
 *
 * Implements genuine continuous voice interaction modeled after Gemini Live and ChatGPT Voice:
 * 1. The user taps the voice button ONCE to start a live session.
 * 2. A single persistent AudioRecord instance (ContinuousAudioCaptureEngine) stays active continuously.
 *    The microphone is NEVER shut down and restarted between conversation turns, sentences, or AI responses.
 * 3. Supports native Gemini Multimodal Live API via WebSockets for true bidirectional audio streaming
 *    (16kHz PCM in -> WebSocket -> 24kHz PCM out) with server-side barge-in.
 * 4. Also supports full-duplex continuous capture with local barge-in for text-completion providers (Groq, OpenAI, DeepSeek).
 * 5. Instant Interruption (Barge-in): When the user begins speaking while JARVIS is responding,
 *    audio playback halts within milliseconds, active jobs are cancelled, and the user's new utterance is prioritized.
 * 6. The session ends ONLY when the user explicitly ends it or an unrecoverable failure occurs.
 */
class VoiceSessionCoordinator(
    private val context: Context,
    private val continuousCaptureEngine: ContinuousAudioCaptureEngine,
    private val realtimeAudioPlayer: RealtimeAudioPlayer,
    private val voiceInputEngine: VoiceInputEngine,
    private val voiceOutputEngine: VoiceOutputEngine,
    private val conversationRepository: ConversationRepository,
    private val preferences: JarvisPreferences,
    private val secureKeyStorage: SecureKeyStorage,
    private val aiProvider: AIProvider = OpenAICompatibleProvider()
) {
    companion object {
        private const val TAG = "VoiceCoordinator"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _voiceState = MutableStateFlow(VoiceState.READY)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isRealtimeLiveMode = MutableStateFlow(false)
    val isRealtimeLiveMode: StateFlow<Boolean> = _isRealtimeLiveMode.asStateFlow()

    // Realtime Gemini Live WebSocket session
    private var geminiLiveSession: GeminiLiveRealtimeSession? = null

    // Generation token counter to immediately invalidate interrupted AI responses
    private val activeGenerationId = AtomicLong(0)
    private var aiJob: Job? = null
    private var activeLanguage: String? = null

    // Track speech currently being articulated by JARVIS for software echo rejection
    @Volatile private var currentlySpokenAiText: String? = null

    // Duplicate utterance filter
    @Volatile private var lastProcessedUtterance: String = ""
    @Volatile private var lastProcessedTime: Long = 0L

    // Audio Focus Manager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        voiceOutputEngine.init { /* initialized */ }

        // Setup real-time audio player callbacks
        realtimeAudioPlayer.onPlaybackStarted = {
            if (_isSessionActive.value && _voiceState.value != VoiceState.USER_SPEAKING && _voiceState.value != VoiceState.INTERRUPTED) {
                _voiceState.value = VoiceState.JARVIS_SPEAKING
            }
        }
        realtimeAudioPlayer.onPlaybackEnded = {
            if (_isSessionActive.value && _voiceState.value == VoiceState.JARVIS_SPEAKING) {
                _voiceState.value = VoiceState.LISTENING
            }
        }
    }

    /**
     * Starts a continuous full-duplex live session.
     * The microphone turns on ONCE and stays active for the entire duration of the session.
     */
    fun startSession(preferredLanguage: String? = null) {
        if (_isSessionActive.value) {
            Log.d(TAG, "Voice session is already active.")
            return
        }

        activeLanguage = preferredLanguage
        _isSessionActive.value = true
        _lastErrorMessage.value = null
        activeGenerationId.incrementAndGet()

        requestSystemAudioFocus()
        realtimeAudioPlayer.startPlaybackLoop(scope)

        scope.launch {
            val aiConfig = preferences.aiConfigFlow.first()
            val voiceSettings = preferences.voiceSettingsFlow.first()
            val apiKey = secureKeyStorage.getApiKey()

            // Configure audio engine parameters from preferences
            continuousCaptureEngine.vadSensitivity = voiceSettings.vadSensitivity
            continuousCaptureEngine.endOfSpeechSilenceMs = voiceSettings.endOfSpeechSilenceMs
            continuousCaptureEngine.isHardwareEchoCancellationEnabled = voiceSettings.hardwareEchoCancellation
            continuousCaptureEngine.isHardwareNoiseSuppressionEnabled = voiceSettings.hardwareNoiseSuppression

            // Determine if using native Gemini Live Realtime WebSocket or standard Fallback
            val isLiveEligible = (aiConfig.preset == ProviderPreset.GEMINI_LIVE || voiceSettings.realtimeApiMode) && apiKey.isNotBlank()

            if (isLiveEligible) {
                _isRealtimeLiveMode.value = true
                _voiceState.value = VoiceState.RECONNECTING // connecting
                initGeminiLiveSession(apiKey, aiConfig.systemPrompt, voiceSettings.voiceName ?: "Puck")
            } else {
                _isRealtimeLiveMode.value = false
                _voiceState.value = VoiceState.LISTENING
                startContinuousCapturePipeline()
            }
        }
    }

    /**
     * Initializes the native Gemini Live Realtime WebSocket session.
     */
    private fun initGeminiLiveSession(apiKey: String, systemPrompt: String, voiceName: String) {
        geminiLiveSession?.close()
        geminiLiveSession = GeminiLiveRealtimeSession(
            scope = scope,
            apiKey = apiKey,
            model = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            systemPrompt = systemPrompt,
            voiceName = voiceName,
            listener = object : GeminiLiveSessionListener {
                override fun onConnected() {
                    Log.d(TAG, "Gemini Live Realtime session connected.")
                    if (_isSessionActive.value) {
                        _voiceState.value = VoiceState.LISTENING
                        startContinuousCapturePipeline()
                    }
                }

                override fun onAudioChunkReceived(pcmChunk: ByteArray) {
                    if (!_isSessionActive.value) return
                    // Stream PCM chunk directly to low-latency speaker player
                    realtimeAudioPlayer.enqueueAudioChunk(pcmChunk)
                }

                override fun onTranscriptReceived(text: String, isUser: Boolean) {
                    if (text.isNotBlank()) {
                        _partialTranscript.value = text
                        if (!isUser) {
                            conversationRepository.addMessage(role = "assistant", content = text)
                        }
                    }
                }

                override fun onInterrupted() {
                    // Server-side barge-in detected!
                    handleImmediateInterruption("Server barge-in")
                }

                override fun onTurnComplete() {
                    _partialTranscript.value = ""
                    if (_voiceState.value == VoiceState.JARVIS_SPEAKING) {
                        _voiceState.value = VoiceState.LISTENING
                    }
                }

                override fun onError(message: String) {
                    Log.e(TAG, "Gemini Live error: $message")
                    _lastErrorMessage.value = message
                    // If live fails, fall back smoothly to local full-duplex capture pipeline
                    _isRealtimeLiveMode.value = false
                    _voiceState.value = VoiceState.LISTENING
                    startContinuousCapturePipeline()
                }

                override fun onClosed() {
                    if (_isSessionActive.value) {
                        _voiceState.value = VoiceState.RECONNECTING
                    }
                }
            }
        )
        geminiLiveSession?.connect()
    }

    /**
     * Starts the single persistent AudioRecord capture engine.
     * AudioRecord runs continuously and NEVER shuts down between utterances or responses.
     */
    private fun startContinuousCapturePipeline() {
        if (continuousCaptureEngine.isCurrentlyCapturing()) {
            return
        }

        continuousCaptureEngine.startCapture(scope, object : ContinuousAudioListener {
            override fun onUserSpeechStarted() {
                // INSTANT USER BARGE-IN INTERRUPT
                handleImmediateInterruption("VAD speech onset")
            }

            override fun onAudioLevel(rmsDb: Float) {
                _audioRmsDb.value = rmsDb

                // If user speaks loud and clear during LISTENING, transition visual state immediately
                if (_isSessionActive.value && rmsDb > 10f && _voiceState.value == VoiceState.LISTENING) {
                    _voiceState.value = VoiceState.USER_SPEAKING
                }
            }

            override fun onRawAudioChunk(pcmBytes: ByteArray) {
                // If in Gemini Live mode, stream raw PCM audio chunk to WebSocket
                if (_isSessionActive.value && _isRealtimeLiveMode.value) {
                    geminiLiveSession?.sendRealtimeAudio(pcmBytes)
                }
            }

            override fun onUserSpeechEnded(speechBuffer: ShortArray) {
                // In Fallback mode (Groq / OpenAI / DeepSeek), transcribe speech and submit completion
                if (_isSessionActive.value && !_isRealtimeLiveMode.value) {
                    handleFallbackSpeechUtterance(speechBuffer)
                }
            }

            override fun onCaptureError(message: String) {
                Log.e(TAG, "Continuous capture error: $message")
                _lastErrorMessage.value = message
                _voiceState.value = VoiceState.ERROR
            }
        })
    }

    /**
     * CRITICAL BARGE-IN: Instantly halts current AI speech and cancels in-flight requests.
     * Executed within <30ms when user speech activity is detected.
     */
    private fun handleImmediateInterruption(reason: String) {
        val currentState = _voiceState.value
        Log.d(TAG, "Triggering immediate barge-in interruption ($reason). Current state: $currentState")

        // 1. Immediately invalidate current generation ID so pending AI tokens are discarded
        activeGenerationId.incrementAndGet()

        // 2. Cancel any pending HTTP/AI coroutine
        aiJob?.cancel()
        aiJob = null

        // 3. Instantly flush speaker audio output
        currentlySpokenAiText = null
        realtimeAudioPlayer.stopAndFlush()
        voiceOutputEngine.stop()

        // 4. Update state to INTERRUPTED briefly, then USER_SPEAKING
        if (_isSessionActive.value) {
            _voiceState.value = VoiceState.INTERRUPTED
            scope.launch {
                delay(30)
                if (_isSessionActive.value) {
                    _voiceState.value = VoiceState.USER_SPEAKING
                }
            }
        }
    }

    /**
     * Handles speech recognition and response generation for Text-Chat providers (OpenAI, Groq, DeepSeek).
     * Microphone capture CONTINUES running during speech recognition, LLM thinking, and TTS vocalization.
     */
    private fun handleFallbackSpeechUtterance(speechBuffer: ShortArray) {
        if (!_isSessionActive.value) return

        // In fallback mode, use VoiceInputEngine to capture text while keeping capture engine active
        voiceInputEngine.startListening(object : VoiceInputListener {
            override fun onReady() {}
            override fun onSpeechStarted() {
                handleImmediateInterruption("SpeechRecognizer onset")
            }
            override fun onRmsChanged(rmsdB: Float) {
                _audioRmsDb.value = rmsdB.coerceAtLeast(0f)
            }
            override fun onPartialResult(text: String) {
                if (_isSessionActive.value && text.isNotBlank()) {
                    if (!isAcousticEcho(text)) {
                        _partialTranscript.value = text.trim()
                        _voiceState.value = VoiceState.USER_SPEAKING
                    }
                }
            }
            override fun onFinalResult(text: String) {
                _partialTranscript.value = ""
                processUserTextCommand(text)
            }
            override fun onError(errorMessage: String, isTransient: Boolean) {
                if (_isSessionActive.value && _voiceState.value == VoiceState.USER_SPEAKING) {
                    _voiceState.value = VoiceState.LISTENING
                }
            }
        }, activeLanguage)
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        stopSpeaking()
        _lastErrorMessage.value = null
        _isSessionActive.value = true
        processUserTextCommand(text)
    }

    private fun processUserTextCommand(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            if (_voiceState.value == VoiceState.USER_SPEAKING) {
                _voiceState.value = VoiceState.LISTENING
            }
            return
        }

        // Echo rejection: reject if microphone captured JARVIS's own spoken output
        if (isAcousticEcho(trimmed)) {
            Log.d(TAG, "Rejected acoustic echo: $trimmed")
            if (_voiceState.value == VoiceState.USER_SPEAKING) {
                _voiceState.value = if (voiceOutputEngine.isSpeaking() || realtimeAudioPlayer.isSpeaking()) {
                    VoiceState.JARVIS_SPEAKING
                } else {
                    VoiceState.LISTENING
                }
            }
            return
        }

        // Duplicate utterance filter: ignore identical command within 1.5 seconds
        val now = System.currentTimeMillis()
        if (trimmed.equals(lastProcessedUtterance, ignoreCase = true) && (now - lastProcessedTime) < 1500) {
            return
        }
        lastProcessedUtterance = trimmed
        lastProcessedTime = now

        conversationRepository.addMessage(role = "user", content = trimmed)

        // Generate AI response
        processWithAI()
    }

    private fun isAcousticEcho(candidateText: String): Boolean {
        val spoken = currentlySpokenAiText ?: return false
        val cleanCandidate = candidateText.lowercase().trim()
        val cleanSpoken = spoken.lowercase().trim()

        if (cleanCandidate.length < 4) return false
        return cleanSpoken.contains(cleanCandidate)
    }

    private fun processWithAI() {
        val currentGeneration = activeGenerationId.incrementAndGet()
        aiJob?.cancel()

        if (_voiceState.value != VoiceState.USER_SPEAKING) {
            _voiceState.value = VoiceState.THINKING
        }

        aiJob = scope.launch {
            val baseConfig = preferences.aiConfigFlow.first()
            val apiKey = secureKeyStorage.getApiKey()
            val config = baseConfig.copy(apiKey = apiKey)

            val contextMessages = conversationRepository.getValidContextForAI(config.systemPrompt)

            when (val result = aiProvider.generateChatCompletion(contextMessages, config)) {
                is AIResult.Success -> {
                    // Check if user interrupted while we were thinking
                    if (currentGeneration != activeGenerationId.get() || !_isSessionActive.value) {
                        return@launch
                    }

                    val aiResponse = result.data
                    conversationRepository.addMessage(role = "assistant", content = aiResponse)
                    speakAIResponse(aiResponse, currentGeneration)
                }
                is AIResult.Failure -> {
                    if (currentGeneration != activeGenerationId.get() || !_isSessionActive.value) {
                        return@launch
                    }
                    _voiceState.value = VoiceState.ERROR
                    _lastErrorMessage.value = result.message
                    conversationRepository.addMessage(
                        role = "assistant",
                        content = "Communication notice: ${result.message}",
                        isError = true
                    )
                    delay(1000)
                    if (_isSessionActive.value) {
                        _voiceState.value = VoiceState.LISTENING
                    }
                }
            }
        }
    }

    private fun speakAIResponse(text: String, generationId: Long) {
        if (generationId != activeGenerationId.get() || !_isSessionActive.value) return

        _voiceState.value = VoiceState.JARVIS_SPEAKING
        currentlySpokenAiText = text

        scope.launch {
            val voiceSettings = preferences.voiceSettingsFlow.first()
            voiceOutputEngine.setRate(voiceSettings.speechRate)
            voiceOutputEngine.setPitch(voiceSettings.speechPitch)
            voiceOutputEngine.setVoice(voiceSettings.voiceName)

            voiceOutputEngine.speak(text, object : VoiceOutputListener {
                override fun onSpeechStart() {
                    if (generationId == activeGenerationId.get()) {
                        _voiceState.value = VoiceState.JARVIS_SPEAKING
                    }
                }

                override fun onSpeechDone() {
                    if (generationId == activeGenerationId.get()) {
                        currentlySpokenAiText = null
                        if (_isSessionActive.value) {
                            _voiceState.value = VoiceState.LISTENING
                        }
                    }
                }

                override fun onSpeechError(errorMessage: String) {
                    if (generationId == activeGenerationId.get()) {
                        currentlySpokenAiText = null
                        _lastErrorMessage.value = errorMessage
                        if (_isSessionActive.value) {
                            _voiceState.value = VoiceState.LISTENING
                        }
                    }
                }
            })
        }
    }

    /**
     * Halts speech vocalization immediately.
     * Does NOT stop microphone capture. Capture continues continuously.
     */
    fun stopSpeaking() {
        activeGenerationId.incrementAndGet()
        currentlySpokenAiText = null
        realtimeAudioPlayer.stopAndFlush()
        voiceOutputEngine.stop()
        if (_voiceState.value == VoiceState.JARVIS_SPEAKING || _voiceState.value == VoiceState.THINKING) {
            _voiceState.value = VoiceState.LISTENING
        }
    }

    /**
     * Pauses listening without terminating the session structure.
     */
    fun stopListening() {
        if (_voiceState.value == VoiceState.LISTENING || _voiceState.value == VoiceState.USER_SPEAKING) {
            _voiceState.value = VoiceState.READY
        }
    }

    /**
     * Explicitly ends the live voice session.
     * Stops continuous AudioRecord capture, releases WebSocket, AudioTrack, and AudioFocus.
     */
    fun endSession() {
        _isSessionActive.value = false
        activeGenerationId.incrementAndGet()
        currentlySpokenAiText = null

        aiJob?.cancel()
        aiJob = null

        // Stop audio playback
        realtimeAudioPlayer.stopAndFlush()
        voiceOutputEngine.stop()

        // Stop continuous AudioRecord microphone capture
        continuousCaptureEngine.stopCapture()
        voiceInputEngine.cancel()

        // Close Gemini Live WebSocket
        geminiLiveSession?.close()
        geminiLiveSession = null

        abandonSystemAudioFocus()

        _voiceState.value = VoiceState.SESSION_ENDED
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""

        scope.launch {
            delay(1000)
            if (!_isSessionActive.value && _voiceState.value == VoiceState.SESSION_ENDED) {
                _voiceState.value = VoiceState.READY
            }
        }
    }

    private fun requestSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                endSession()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                stopSpeaking()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                // Regained focus
                            }
                        }
                    }
                    .build()

                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
        }
    }

    private fun abandonSystemAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    fun clearError() {
        _lastErrorMessage.value = null
        if (_voiceState.value == VoiceState.ERROR) {
            _voiceState.value = if (_isSessionActive.value) VoiceState.LISTENING else VoiceState.READY
        }
    }

    fun shutdown() {
        endSession()
        realtimeAudioPlayer.release()
        voiceInputEngine.destroy()
        voiceOutputEngine.shutdown()
    }
}
