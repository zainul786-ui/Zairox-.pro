package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

class JarvisPreferences(private val context: Context) {

    companion object {
        val KEY_PRESET = stringPreferencesKey("provider_preset")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val KEY_CUSTOM_HEADERS = stringPreferencesKey("custom_headers")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")

        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val KEY_VOICE_NAME = stringPreferencesKey("voice_name")
        val KEY_CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
        val KEY_PUSH_TO_TALK = booleanPreferencesKey("push_to_talk")
        val KEY_BACKGROUND_SESSION = booleanPreferencesKey("background_session")

        // Full-duplex interruption & VAD keys
        val KEY_INTERRUPT_ON_SPEECH = booleanPreferencesKey("interrupt_on_speech")
        val KEY_VAD_SENSITIVITY = floatPreferencesKey("vad_sensitivity")
        val KEY_END_OF_SPEECH_SILENCE_MS = intPreferencesKey("end_of_speech_silence_ms")
        val KEY_ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val KEY_NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val KEY_REALTIME_API_MODE = booleanPreferencesKey("realtime_api_mode")
    }

    val aiConfigFlow: Flow<AIProviderConfig> = context.dataStore.data.map { prefs ->
        val presetStr = prefs[KEY_PRESET] ?: ProviderPreset.GROQ.name
        val preset = try {
            ProviderPreset.valueOf(presetStr)
        } catch (_: Exception) {
            ProviderPreset.GROQ
        }

        val baseUrl = prefs[KEY_BASE_URL] ?: preset.defaultBaseUrl
        val model = prefs[KEY_MODEL_NAME] ?: preset.defaultModel
        val temp = prefs[KEY_TEMPERATURE] ?: 0.7f
        val maxTokens = prefs[KEY_MAX_TOKENS] ?: 1024
        val timeout = prefs[KEY_TIMEOUT_SECONDS] ?: 30
        val sysPrompt = prefs[KEY_SYSTEM_PROMPT] ?: DEFAULT_JARVIS_PROMPT
        val headersStr = prefs[KEY_CUSTOM_HEADERS] ?: ""
        val headers = parseHeaders(headersStr)

        AIProviderConfig(
            preset = preset,
            baseUrl = baseUrl,
            apiKey = "", // API Key is read from SecureKeyStorage, not DataStore
            modelName = model,
            temperature = temp,
            maxTokens = maxTokens,
            timeoutSeconds = timeout,
            customHeaders = headers,
            systemPrompt = sysPrompt
        )
    }

    val voiceSettingsFlow: Flow<VoiceSettings> = context.dataStore.data.map { prefs ->
        VoiceSettings(
            speechRate = prefs[KEY_SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[KEY_SPEECH_PITCH] ?: 1.0f,
            voiceName = prefs[KEY_VOICE_NAME],
            continuousMode = prefs[KEY_CONTINUOUS_MODE] ?: true,
            pushToTalkMode = prefs[KEY_PUSH_TO_TALK] ?: false,
            backgroundSessionEnabled = prefs[KEY_BACKGROUND_SESSION] ?: false,
            interruptJarvisOnSpeech = prefs[KEY_INTERRUPT_ON_SPEECH] ?: true,
            vadSensitivity = prefs[KEY_VAD_SENSITIVITY] ?: 0.65f,
            endOfSpeechSilenceMs = prefs[KEY_END_OF_SPEECH_SILENCE_MS] ?: 700,
            hardwareEchoCancellation = prefs[KEY_ECHO_CANCELLATION] ?: true,
            hardwareNoiseSuppression = prefs[KEY_NOISE_SUPPRESSION] ?: true,
            realtimeApiMode = prefs[KEY_REALTIME_API_MODE] ?: false
        )
    }

    suspend fun saveAIConfig(config: AIProviderConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRESET] = config.preset.name
            prefs[KEY_BASE_URL] = config.baseUrl
            prefs[KEY_MODEL_NAME] = config.modelName
            prefs[KEY_TEMPERATURE] = config.temperature
            prefs[KEY_MAX_TOKENS] = config.maxTokens
            prefs[KEY_TIMEOUT_SECONDS] = config.timeoutSeconds
            prefs[KEY_SYSTEM_PROMPT] = config.systemPrompt
            prefs[KEY_CUSTOM_HEADERS] = serializeHeaders(config.customHeaders)
        }
    }

    suspend fun saveVoiceSettings(settings: VoiceSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEECH_RATE] = settings.speechRate
            prefs[KEY_SPEECH_PITCH] = settings.speechPitch
            if (settings.voiceName != null) {
                prefs[KEY_VOICE_NAME] = settings.voiceName
            } else {
                prefs.remove(KEY_VOICE_NAME)
            }
            prefs[KEY_CONTINUOUS_MODE] = settings.continuousMode
            prefs[KEY_PUSH_TO_TALK] = settings.pushToTalkMode
            prefs[KEY_BACKGROUND_SESSION] = settings.backgroundSessionEnabled
            prefs[KEY_INTERRUPT_ON_SPEECH] = settings.interruptJarvisOnSpeech
            prefs[KEY_VAD_SENSITIVITY] = settings.vadSensitivity
            prefs[KEY_END_OF_SPEECH_SILENCE_MS] = settings.endOfSpeechSilenceMs
            prefs[KEY_ECHO_CANCELLATION] = settings.hardwareEchoCancellation
            prefs[KEY_NOISE_SUPPRESSION] = settings.hardwareNoiseSuppression
            prefs[KEY_REALTIME_API_MODE] = settings.realtimeApiMode
        }
    }

    suspend fun resetPersonality() {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYSTEM_PROMPT] = DEFAULT_JARVIS_PROMPT
        }
    }

    suspend fun clearAllSettings() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        raw.split("\n").forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }

    private fun serializeHeaders(headers: Map<String, String>): String {
        return headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
