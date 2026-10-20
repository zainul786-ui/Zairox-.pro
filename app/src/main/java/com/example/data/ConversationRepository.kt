package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConversationRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    companion object {
        const val MAX_CONTEXT_MESSAGES = 24
    }

    fun addMessage(role: String, content: String, isError: Boolean = false): ChatMessage {
        val msg = ChatMessage(role = role, content = content, isError = isError)
        _messages.update { current ->
            val updated = current + msg
            // Trim older messages to keep memory and token usage under control
            if (updated.size > MAX_CONTEXT_MESSAGES + 10) {
                updated.takeLast(MAX_CONTEXT_MESSAGES)
            } else {
                updated
            }
        }
        return msg
    }

    fun getValidContextForAI(systemPrompt: String): List<ChatMessage> {
        val current = _messages.value
        // Exclude error messages or empty messages
        val filtered = current.filter { !it.isError && it.content.isNotBlank() }
        val recent = filtered.takeLast(MAX_CONTEXT_MESSAGES)

        val fullHistory = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            fullHistory.add(ChatMessage(role = "system", content = systemPrompt))
        }
        fullHistory.addAll(recent)
        return fullHistory
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }
}
