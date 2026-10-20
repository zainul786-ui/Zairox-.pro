package com.example.voice

import android.content.Context
import com.example.ai.AIProvider
import com.example.ai.AIResult
import com.example.ai.OpenAICompatibleProvider
import com.example.data.ConversationRepository
import com.example.data.JarvisPreferences
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

class VoiceSessionCoordinator(
    private val context: Context,
    private val voiceInputEngine: VoiceInputEngine,
    private val voiceOutputEngine: VoiceOutputEngine,
    private val conversationRepository: ConversationRepository,
    private val preferences: JarvisPreferences,
    private val secureKeyStorage: SecureKeyStorage,
    private val aiProvider: AIProvider = OpenAICompatibleProvider()
) {
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

    // Generation token counter to immediately invalidate interrupted AI responses
    private val activeGenerationId = AtomicLong(0)
    private var aiJob: Job? = null
    private var activeLanguage: String? = null

    // Track speech currently being articulated by JARVIS for software echo rejection
    @Volatile private var currentlySpokenAiText: String? = null

    // Duplicate utterance filter
    @Volatile private var lastProcessedUtterance: String = ""
    @Volatile private var lastProcessedTime: Long = 0L

    init {
        voiceOutputEngine.init { /* initialized */ }
    }

    fun startSession(preferredLanguage: String? = null) {
        activeLanguage = preferredLanguage
        _isSessionActive.value = true
        _lastErrorMessage.value = null
        activeGenerationId.incrementAndGet()
        startContinuousListening()
    }

    /**
     * Arms the microphone for continuous listening.
     * In a full-duplex session, this stays active continuously across speaking and thinking states.
     */
    fun startListening(preferredLanguage: String? = null) {
        if (preferredLanguage != null) {
            activeLanguage = preferredLanguage
        }
        startContinuousListening()
    }

    private fun startContinuousListening() {
        if (!_isSessionActive.value) return

        if (_voiceState.value != VoiceState.USER_SPEAKING &&
            _voiceState.value != VoiceState.JARVIS_SPEAKING &&
            _voiceState.value != VoiceState.THINKING
        ) {
            _voiceState.value = VoiceState.LISTENING
        }

        voiceInputEngine.startListening(object : VoiceInputListener {
            override fun onReady() {
                if (_isSessionActive.value &&
                    _voiceState.value != VoiceState.USER_SPEAKING &&
                    _voiceState.value != VoiceState.JARVIS_SPEAKING &&
                    _voiceState.value != VoiceState.THINKING
                ) {
                    _voiceState.value = VoiceState.LISTENING
                }
            }

            override fun onSpeechStarted() {
                // Immediate interruption trigger when user speech energy is detected
                handleUserSpeechDetected()
            }

            override fun onRmsChanged(rmsdB: Float) {
                val clamped = rmsdB.coerceAtLeast(0f)
                _audioRmsDb.value = clamped

                // If user speech volume spikes during LISTENING, transition immediately to USER_SPEAKING
                if (_isSessionActive.value && clamped > 6f && _voiceState.value == VoiceState.LISTENING) {
                    _voiceState.value = VoiceState.USER_SPEAKING
                }
            }

            override fun onPartialResult(text: String) {
                if (_isSessionActive.value) {
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank()) {
                        // Check if it's an echo of what JARVIS is saying
                        if (isAcousticEcho(trimmed)) {
                            return
                        }
                        _partialTranscript.value = trimmed
                        if (_voiceState.value != VoiceState.USER_SPEAKING) {
                            handleUserSpeechDetected()
                        }
                    }
                }
            }

            override fun onFinalResult(text: String) {
                _partialTranscript.value = ""
                _audioRmsDb.value = 0f
                handleUserSpeechCompleted(text)
            }

            override fun onError(errorMessage: String, isTransient: Boolean) {
                _audioRmsDb.value = 0f

                scope.launch {
                    val settings = preferences.voiceSettingsFlow.first()
                    if (_isSessionActive.value && settings.continuousMode && isTransient) {
                        // In full-duplex continuous mode, silence or recognizer timeout immediately loops back
                        delay(60)
                        if (_isSessionActive.value) {
                            startContinuousListening()
                        }
                    } else if (_isSessionActive.value && !isTransient) {
                        _voiceState.value = VoiceState.ERROR
                        _lastErrorMessage.value = errorMessage
                    } else if (!_isSessionActive.value) {
                        _voiceState.value = VoiceState.READY
                    }
                }
            }
        }, activeLanguage)
    }

    /**
     * CRITICAL: Called the exact instant user speech activity is detected.
     * Interrupts playing speech immediately and cancels any in-flight AI generation.
     */
    fun handleUserSpeechDetected() {
        scope.launch {
            val settings = preferences.voiceSettingsFlow.first()
            if (settings.interruptJarvisOnSpeech) {
                val currentState = _voiceState.value
                if (currentState == VoiceState.JARVIS_SPEAKING || currentState == VoiceState.THINKING) {
                    // 1. Immediately invalidate current generation
                    activeGenerationId.incrementAndGet()
                    // 2. Cancel in-flight network request
                    aiJob?.cancel()
                    aiJob = null
                    // 3. Cut off audio output instantly
                    currentlySpokenAiText = null
                    voiceOutputEngine.stop()
                    // 4. Mark interrupted state briefly for reactive UI then transition to user speaking
                    _voiceState.value = VoiceState.INTERRUPTED
                    delay(40)
                }
            }
            if (_isSessionActive.value) {
                _voiceState.value = VoiceState.USER_SPEAKING
            }
        }
    }

    private fun handleUserSpeechCompleted(userText: String) {
        if (!_isSessionActive.value) return

        val trimmed = userText.trim()
        if (trimmed.isBlank()) {
            if (_voiceState.value == VoiceState.USER_SPEAKING) {
                _voiceState.value = VoiceState.LISTENING
            }
            rearmListening()
            return
        }

        // Echo rejection check: did the microphone pick up JARVIS's own spoken output?
        if (isAcousticEcho(trimmed)) {
            if (_voiceState.value == VoiceState.USER_SPEAKING) {
                _voiceState.value = if (voiceOutputEngine.isSpeaking()) VoiceState.JARVIS_SPEAKING else VoiceState.LISTENING
            }
            rearmListening()
            return
        }

        // Duplicate utterance check: ignore identical input received within 1.5 seconds
        val now = System.currentTimeMillis()
        if (trimmed.equals(lastProcessedUtterance, ignoreCase = true) && (now - lastProcessedTime) < 1500) {
            rearmListening()
            return
        }
        lastProcessedUtterance = trimmed
        lastProcessedTime = now

        // Add user command to conversation
        conversationRepository.addMessage(role = "user", content = trimmed)

        // Kick off AI completion
        processWithAI()

        // Keep microphone listening actively during thinking and speaking!
        rearmListening()
    }

    private fun isAcousticEcho(candidateText: String): Boolean {
        val spoken = currentlySpokenAiText ?: return false
        val cleanCandidate = candidateText.lowercase().trim()
        val cleanSpoken = spoken.lowercase().trim()

        if (cleanCandidate.length < 4) return false

        // If candidate is a substring of what JARVIS is currently speaking, it is acoustic leakage
        return cleanSpoken.contains(cleanCandidate)
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        stopListening()
        stopSpeaking()
        _lastErrorMessage.value = null
        _isSessionActive.value = true
        conversationRepository.addMessage(role = "user", content = text.trim())
        processWithAI()
        rearmListening()
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
                        // User already interrupted; discard stale response
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
                    rearmListening()
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
                        handleSpeechComplete()
                    }
                }

                override fun onSpeechError(errorMessage: String) {
                    if (generationId == activeGenerationId.get()) {
                        currentlySpokenAiText = null
                        _lastErrorMessage.value = errorMessage
                        handleSpeechComplete()
                    }
                }
            })
        }
    }

    private fun handleSpeechComplete() {
        scope.launch {
            val settings = preferences.voiceSettingsFlow.first()
            if (_isSessionActive.value && settings.continuousMode) {
                // In continuous conversation, smoothly transition to LISTENING
                if (_voiceState.value == VoiceState.JARVIS_SPEAKING) {
                    _voiceState.value = VoiceState.LISTENING
                }
                rearmListening()
            } else if (_isSessionActive.value) {
                _voiceState.value = VoiceState.READY
            } else {
                _voiceState.value = VoiceState.SESSION_ENDED
            }
        }
    }

    private fun rearmListening() {
        scope.launch {
            if (_isSessionActive.value) {
                delay(60)
                if (_isSessionActive.value) {
                    startContinuousListening()
                }
            }
        }
    }

    fun stopSpeaking() {
        activeGenerationId.incrementAndGet()
        currentlySpokenAiText = null
        voiceOutputEngine.stop()
        if (_voiceState.value == VoiceState.JARVIS_SPEAKING) {
            _voiceState.value = VoiceState.LISTENING
            rearmListening()
        }
    }

    fun stopListening() {
        voiceInputEngine.stopListening()
        if (_voiceState.value == VoiceState.LISTENING || _voiceState.value == VoiceState.USER_SPEAKING) {
            _voiceState.value = VoiceState.READY
        }
    }

    fun endSession() {
        _isSessionActive.value = false
        activeGenerationId.incrementAndGet()
        currentlySpokenAiText = null
        aiJob?.cancel()
        aiJob = null
        voiceInputEngine.cancel()
        voiceOutputEngine.stop()
        _voiceState.value = VoiceState.SESSION_ENDED
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""

        scope.launch {
            delay(1200)
            if (!_isSessionActive.value && _voiceState.value == VoiceState.SESSION_ENDED) {
                _voiceState.value = VoiceState.READY
            }
        }
    }

    fun clearError() {
        _lastErrorMessage.value = null
        if (_voiceState.value == VoiceState.ERROR) {
            _voiceState.value = if (_isSessionActive.value) VoiceState.LISTENING else VoiceState.READY
        }
    }

    fun shutdown() {
        endSession()
        voiceInputEngine.destroy()
        voiceOutputEngine.shutdown()
    }
}
