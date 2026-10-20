package com.example.voice

import android.content.Context
import com.example.ai.AIProvider
import com.example.ai.AIResult
import com.example.ai.OpenAICompatibleProvider
import com.example.data.AIProviderConfig
import com.example.data.ConversationRepository
import com.example.data.JarvisPreferences
import com.example.data.VoiceSettings
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private var aiJob: Job? = null
    private var silenceRetryCount = 0

    init {
        // Initialize TTS engine in advance
        voiceOutputEngine.init { /* initialized */ }
    }

    fun startSession(preferredLanguage: String? = null) {
        _isSessionActive.value = true
        _lastErrorMessage.value = null
        silenceRetryCount = 0
        startListening(preferredLanguage)
    }

    fun startListening(preferredLanguage: String? = null) {
        if (voiceOutputEngine.isSpeaking()) {
            voiceOutputEngine.stop()
        }

        _voiceState.value = VoiceState.LISTENING
        _partialTranscript.value = ""

        voiceInputEngine.startListening(object : VoiceInputListener {
            override fun onReady() {
                _voiceState.value = VoiceState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize and smooth RMS dB value for visualizer (0.0 to 10.0+ dB)
                _audioRmsDb.value = rmsdB.coerceAtLeast(0f)
            }

            override fun onPartialResult(text: String) {
                _partialTranscript.value = text
            }

            override fun onFinalResult(text: String) {
                _partialTranscript.value = ""
                _audioRmsDb.value = 0f
                silenceRetryCount = 0
                handleUserSpeech(text)
            }

            override fun onError(errorMessage: String, isTransient: Boolean) {
                _audioRmsDb.value = 0f

                scope.launch {
                    val settings = preferences.voiceSettingsFlow.first()
                    // If continuous mode is on and silence or no speech was detected, gracefully retry listening up to 2 times
                    if (_isSessionActive.value && settings.continuousMode && isTransient && silenceRetryCount < 2) {
                        silenceRetryCount++
                        delay(600)
                        if (_isSessionActive.value) {
                            startListening(preferredLanguage)
                        }
                    } else {
                        silenceRetryCount = 0
                        if (!isTransient) {
                            _voiceState.value = VoiceState.ERROR
                            _lastErrorMessage.value = errorMessage
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }
                }
            }
        }, preferredLanguage)
    }

    fun handleUserSpeech(userText: String) {
        if (userText.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            return
        }

        // Add user message to session history
        conversationRepository.addMessage(role = "user", content = userText)
        processWithAI()
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        stopListening()
        stopSpeaking()
        _lastErrorMessage.value = null
        _isSessionActive.value = true
        conversationRepository.addMessage(role = "user", content = text.trim())
        processWithAI()
    }

    private fun processWithAI() {
        aiJob?.cancel()
        _voiceState.value = VoiceState.THINKING

        aiJob = scope.launch {
            val baseConfig = preferences.aiConfigFlow.first()
            val apiKey = secureKeyStorage.getApiKey()
            val config = baseConfig.copy(apiKey = apiKey)

            val contextMessages = conversationRepository.getValidContextForAI(config.systemPrompt)

            when (val result = aiProvider.generateChatCompletion(contextMessages, config)) {
                is AIResult.Success -> {
                    val aiResponse = result.data
                    conversationRepository.addMessage(role = "assistant", content = aiResponse)
                    speakAIResponse(aiResponse)
                }
                is AIResult.Failure -> {
                    _voiceState.value = VoiceState.ERROR
                    _lastErrorMessage.value = result.message
                    conversationRepository.addMessage(
                        role = "assistant",
                        content = "I encountered an issue connecting to the AI core: ${result.message}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun speakAIResponse(text: String) {
        _voiceState.value = VoiceState.SPEAKING

        scope.launch {
            val voiceSettings = preferences.voiceSettingsFlow.first()
            voiceOutputEngine.setRate(voiceSettings.speechRate)
            voiceOutputEngine.setPitch(voiceSettings.speechPitch)
            voiceOutputEngine.setVoice(voiceSettings.voiceName)

            voiceOutputEngine.speak(text, object : VoiceOutputListener {
                override fun onSpeechStart() {
                    _voiceState.value = VoiceState.SPEAKING
                }

                override fun onSpeechDone() {
                    handleSpeechComplete()
                }

                override fun onSpeechError(errorMessage: String) {
                    _lastErrorMessage.value = errorMessage
                    handleSpeechComplete()
                }
            })
        }
    }

    private fun handleSpeechComplete() {
        scope.launch {
            val settings = preferences.voiceSettingsFlow.first()
            if (_isSessionActive.value && settings.continuousMode) {
                // Resume listening smoothly after voice completes
                delay(300)
                if (_isSessionActive.value) {
                    startListening()
                }
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopSpeaking() {
        voiceOutputEngine.stop()
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun stopListening() {
        voiceInputEngine.stopListening()
        if (_voiceState.value == VoiceState.LISTENING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun endSession() {
        _isSessionActive.value = false
        aiJob?.cancel()
        voiceInputEngine.stopListening()
        voiceOutputEngine.stop()
        _voiceState.value = VoiceState.IDLE
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""
        silenceRetryCount = 0
    }

    fun clearError() {
        _lastErrorMessage.value = null
        if (_voiceState.value == VoiceState.ERROR) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun shutdown() {
        endSession()
        voiceInputEngine.destroy()
        voiceOutputEngine.shutdown()
    }
}
