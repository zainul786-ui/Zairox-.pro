package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.JarvisDependencyProvider
import com.example.ai.AIResult
import com.example.ai.OpenAICompatibleProvider
import com.example.data.AIProviderConfig
import com.example.data.ChatMessage
import com.example.data.VoiceSettings
import com.example.data.VoiceState
import com.example.voice.JarvisVoiceService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    init {
        JarvisDependencyProvider.initialize(application)
    }

    private val coordinator = JarvisDependencyProvider.voiceCoordinator
    private val preferences = JarvisDependencyProvider.preferences
    private val secureKeyStorage = JarvisDependencyProvider.secureKeyStorage
    private val conversationRepository = JarvisDependencyProvider.conversationRepository
    private val ttsEngine = JarvisDependencyProvider.textToSpeechEngine

    val voiceState: StateFlow<VoiceState> = coordinator.voiceState
    val audioRmsDb: StateFlow<Float> = coordinator.audioRmsDb
    val partialTranscript: StateFlow<String> = coordinator.partialTranscript
    val lastErrorMessage: StateFlow<String?> = coordinator.lastErrorMessage
    val isSessionActive: StateFlow<Boolean> = coordinator.isSessionActive

    val messages: StateFlow<List<ChatMessage>> = conversationRepository.messages

    val aiConfig: StateFlow<AIProviderConfig> = preferences.aiConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AIProviderConfig())

    val voiceSettings: StateFlow<VoiceSettings> = preferences.voiceSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())

    fun getStoredApiKey(): String {
        return secureKeyStorage.getApiKey()
    }

    fun hasApiKey(): Boolean {
        return secureKeyStorage.hasApiKey()
    }

    fun onOrbClick() {
        when (voiceState.value) {
            VoiceState.READY, VoiceState.ERROR, VoiceState.SESSION_ENDED -> startListening()
            VoiceState.LISTENING, VoiceState.USER_SPEAKING -> coordinator.stopListening()
            VoiceState.JARVIS_SPEAKING, VoiceState.THINKING -> coordinator.stopSpeaking()
            VoiceState.INTERRUPTED -> coordinator.stopListening()
            VoiceState.RECONNECTING -> coordinator.startListening()
        }
    }

    fun startListening() {
        coordinator.startSession()
        checkAndStartForegroundService()
    }

    fun stopSpeaking() {
        coordinator.stopSpeaking()
    }

    fun endSession() {
        coordinator.endSession()
        JarvisVoiceService.stop(getApplication())
    }

    fun sendTextMessage(text: String) {
        coordinator.sendTextMessage(text)
        checkAndStartForegroundService()
    }

    fun speakMessage(text: String) {
        ttsEngine.speak(text)
    }

    fun toggleContinuousMode() {
        viewModelScope.launch {
            val current = voiceSettings.value
            preferences.saveVoiceSettings(current.copy(continuousMode = !current.continuousMode))
        }
    }

    fun saveAIConfig(config: AIProviderConfig, apiKey: String) {
        viewModelScope.launch {
            secureKeyStorage.saveApiKey(apiKey)
            preferences.saveAIConfig(config)
        }
    }

    fun saveVoiceSettings(settings: VoiceSettings) {
        viewModelScope.launch {
            preferences.saveVoiceSettings(settings)
            ttsEngine.setRate(settings.speechRate)
            ttsEngine.setPitch(settings.speechPitch)
            ttsEngine.setVoice(settings.voiceName)
        }
    }

    fun testConnection(config: AIProviderConfig, apiKey: String, onResult: (AIResult<String>) -> Unit) {
        viewModelScope.launch {
            val provider = OpenAICompatibleProvider()
            val fullConfig = config.copy(apiKey = apiKey)
            val result = provider.testConnection(fullConfig)
            onResult(result)
        }
    }

    fun testVoice(sampleText: String, rate: Float, pitch: Float) {
        ttsEngine.setRate(rate)
        ttsEngine.setPitch(pitch)
        ttsEngine.speak(sampleText)
    }

    fun clearConversation() {
        conversationRepository.clearConversation()
    }

    fun clearApiKey() {
        secureKeyStorage.clearApiKey()
    }

    fun resetPersonality() {
        viewModelScope.launch {
            preferences.resetPersonality()
        }
    }

    fun clearError() {
        coordinator.clearError()
    }

    private fun checkAndStartForegroundService() {
        if (voiceSettings.value.backgroundSessionEnabled) {
            JarvisVoiceService.start(getApplication())
        }
    }
}
