package com.example

import com.example.data.AIProviderConfig
import com.example.data.ChatMessage
import com.example.data.ConversationRepository
import com.example.data.DEFAULT_JARVIS_PROMPT
import com.example.data.ProviderPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCoreLogicTest {

    @Test
    fun conversationRepository_preservesContextAndTrimsSafely() {
        val repo = ConversationRepository()

        // Initially empty
        assertTrue(repo.messages.value.isEmpty())

        // Add user and assistant messages
        repo.addMessage(role = "user", content = "Hello JARVIS")
        repo.addMessage(role = "assistant", content = "Greetings, sir.")

        assertEquals(2, repo.messages.value.size)
        assertEquals("user", repo.messages.value[0].role)
        assertEquals("Hello JARVIS", repo.messages.value[0].content)

        val context = repo.getValidContextForAI(DEFAULT_JARVIS_PROMPT)
        assertEquals(3, context.size) // 1 system + 2 conversation messages
        assertEquals("system", context[0].role)
        assertEquals(DEFAULT_JARVIS_PROMPT, context[0].content)

        // Clear conversation
        repo.clearConversation()
        assertTrue(repo.messages.value.isEmpty())
    }

    @Test
    fun providerPresets_haveCorrectUrlsAndModels() {
        val groq = ProviderPreset.GROQ
        assertEquals("https://api.groq.com/openai/v1", groq.defaultBaseUrl)
        assertEquals("llama-3.3-70b-versatile", groq.defaultModel)

        val openai = ProviderPreset.OPENAI
        assertEquals("https://api.openai.com/v1", openai.defaultBaseUrl)
        assertEquals("gpt-4o-mini", openai.defaultModel)

        val gemini = ProviderPreset.GEMINI_OPENAI
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", gemini.defaultBaseUrl)
        assertEquals("gemini-2.5-flash", gemini.defaultModel)

        val geminiLive = ProviderPreset.GEMINI_LIVE
        assertEquals("wss://generativelanguage.googleapis.com", geminiLive.defaultBaseUrl)
        assertTrue(geminiLive.isRealtimeLive)
    }

    @Test
    fun defaultAIProviderConfig_isConfiguredSensibly() {
        val config = AIProviderConfig()
        assertTrue(config.baseUrl.isNotBlank())
        assertTrue(config.modelName.isNotBlank())
        assertTrue(config.temperature in 0f..1f)
        assertTrue(config.maxTokens >= 512)
        assertNotNull(config.systemPrompt)
        assertTrue(config.systemPrompt.contains("JARVIS"))
    }
}
