package com.example.data

import java.util.UUID

enum class VoiceState {
    READY,
    LISTENING,
    USER_SPEAKING,
    THINKING,
    JARVIS_SPEAKING,
    INTERRUPTED,
    RECONNECTING,
    ERROR,
    SESSION_ENDED
}

enum class ProviderPreset(val displayName: String, val defaultBaseUrl: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-2.5-flash"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    GEMINI_OPENAI("Gemini (OpenAI API)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
    CUSTOM("Custom Provider", "https://api.openai.com/v1", "gpt-4o-mini")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "system", "user", "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSpoken: Boolean = false,
    val isError: Boolean = false
)

data class AIProviderConfig(
    val preset: ProviderPreset = ProviderPreset.GROQ,
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = "",
    val modelName: String = "llama-3.3-70b-versatile",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val timeoutSeconds: Int = 30,
    val customHeaders: Map<String, String> = emptyMap(),
    val systemPrompt: String = DEFAULT_JARVIS_PROMPT
)

data class VoiceSettings(
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val voiceName: String? = null,
    val continuousMode: Boolean = true,
    val pushToTalkMode: Boolean = false,
    val backgroundSessionEnabled: Boolean = false,
    val interruptJarvisOnSpeech: Boolean = true,
    val vadSensitivity: Float = 0.65f, // 0.1 (low) to 1.0 (high)
    val endOfSpeechSilenceMs: Int = 700, // silence interval (ms)
    val hardwareEchoCancellation: Boolean = true,
    val hardwareNoiseSuppression: Boolean = true,
    val realtimeApiMode: Boolean = false
)

const val DEFAULT_JARVIS_PROMPT = """You are JARVIS, a sophisticated personal AI assistant. You are intelligent, composed, observant, confident, articulate, and subtly witty. You communicate naturally, with a polished futuristic-assistant personality.

You are helpful without being submissive, confident without being arrogant, and humorous without becoming annoying. You can be concise for simple requests and detailed for complex tasks.

You speak like a capable personal assistant, not a generic customer-support chatbot. You understand conversational context, follow the user's language, and can naturally switch between Hindi, Hinglish, and English.

When speaking Hindi or Hinglish, use natural everyday language. Avoid robotic translations and unnecessarily formal sentences.

Do not repeatedly call the user by name. Do not overuse catchphrases. Do not pretend to have physical senses, access to the phone, internet access, memories, or abilities that you do not actually have.

Never claim an action was completed unless it really was completed.

When information is uncertain, say so honestly.

Your current capabilities are conversation and voice interaction. Do not claim to control phone settings or other apps unless those capabilities are implemented in a future version."""
