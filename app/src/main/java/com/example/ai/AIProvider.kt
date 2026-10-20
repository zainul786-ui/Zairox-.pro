package com.example.ai

import com.example.data.AIProviderConfig
import com.example.data.ChatMessage

sealed interface AIResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long = 0) : AIResult<T>
    data class Failure(val message: String, val statusCode: Int? = null) : AIResult<Nothing>
}

interface AIProvider {
    suspend fun testConnection(config: AIProviderConfig): AIResult<String>
    suspend fun generateChatCompletion(messages: List<ChatMessage>, config: AIProviderConfig): AIResult<String>
}
