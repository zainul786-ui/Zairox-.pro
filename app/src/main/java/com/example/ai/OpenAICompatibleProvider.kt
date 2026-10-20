package com.example.ai

import com.example.data.AIProviderConfig
import com.example.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OpenAICompatibleProvider : AIProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getClient(timeoutSeconds: Int): OkHttpClient {
        val timeout = if (timeoutSeconds in 5..120) timeoutSeconds.toLong() else 30L
        return OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun testConnection(config: AIProviderConfig): AIResult<String> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AIResult.Failure("API key is missing. Please configure your key in Settings.")
        }
        if (config.baseUrl.isBlank()) {
            return@withContext AIResult.Failure("Base URL is empty.")
        }

        val testMessages = listOf(
            ChatMessage(role = "user", content = "Ping")
        )

        val startTime = System.currentTimeMillis()
        when (val result = executeRequest(testMessages, config, maxTokensOverride = 5)) {
            is AIResult.Success -> {
                val latency = System.currentTimeMillis() - startTime
                AIResult.Success("Connected successfully to ${config.modelName} (${latency}ms)", latency)
            }
            is AIResult.Failure -> result
        }
    }

    override suspend fun generateChatCompletion(
        messages: List<ChatMessage>,
        config: AIProviderConfig
    ): AIResult<String> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext AIResult.Failure("API key is not configured. Please open Settings and enter your API key.")
        }
        if (config.baseUrl.isBlank()) {
            return@withContext AIResult.Failure("Provider API base URL is not set.")
        }

        executeRequest(messages, config)
    }

    private fun executeRequest(
        messages: List<ChatMessage>,
        config: AIProviderConfig,
        maxTokensOverride: Int? = null
    ): AIResult<String> {
        val client = getClient(config.timeoutSeconds)
        val endpoint = resolveEndpoint(config.baseUrl)

        return try {
            val jsonPayload = JSONObject().apply {
                put("model", config.modelName)
                put("temperature", config.temperature.toDouble())
                put("max_tokens", maxTokensOverride ?: config.maxTokens)

                val msgArray = JSONArray()
                for (m in messages) {
                    val obj = JSONObject()
                    obj.put("role", m.role)
                    obj.put("content", m.content)
                    msgArray.put(obj)
                }
                put("messages", msgArray)
            }

            val requestBody = jsonPayload.toString().toRequestBody(jsonMediaType)

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")

            // Add any custom headers configured by the user
            for ((key, value) in config.customHeaders) {
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            val startTime = System.currentTimeMillis()
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            val latency = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                val errorMessage = extractApiErrorMessage(response.code, responseBody, config)
                return AIResult.Failure(errorMessage, response.code)
            }

            val parsedContent = parseCompletionResponse(responseBody)
            if (parsedContent.isBlank()) {
                AIResult.Failure("The AI provider returned an empty response.")
            } else {
                AIResult.Success(parsedContent, latency)
            }

        } catch (e: SocketTimeoutException) {
            AIResult.Failure("Connection timed out after ${config.timeoutSeconds}s. Please check your network or increase the timeout in Settings.")
        } catch (e: IOException) {
            AIResult.Failure("Network error: Unable to reach '${sanitizeUrl(endpoint)}'. Please verify your connection and base URL.")
        } catch (e: Exception) {
            AIResult.Failure("Unexpected error communicating with AI provider: ${e.message ?: "Unknown error"}")
        }
    }

    private fun resolveEndpoint(rawBaseUrl: String): String {
        val trimmed = rawBaseUrl.trim().removeSuffix("/")
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun parseCompletionResponse(responseJson: String): String {
        return try {
            val json = JSONObject(responseJson)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                message?.optString("content") ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractApiErrorMessage(statusCode: Int, body: String, config: AIProviderConfig): String {
        val providerMessage = try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            errObj?.optString("message") ?: json.optString("message", "")
        } catch (_: Exception) {
            ""
        }

        val baseDetail = if (providerMessage.isNotBlank()) ": $providerMessage" else ""

        return when (statusCode) {
            401 -> "Authentication failed (401)$baseDetail. Please verify your API key in Settings."
            403 -> "Access forbidden (403)$baseDetail. Check your account permissions or region restrictions."
            404 -> "Endpoint or Model not found (404)$baseDetail. Verify that model '${config.modelName}' exists at the configured base URL."
            429 -> "Rate limit or quota exceeded (429)$baseDetail. Please check your provider account credits/billing."
            in 500..599 -> "AI Provider Server Error ($statusCode)$baseDetail. The remote server is experiencing issues. Please try again shortly."
            else -> "AI Request failed with status $statusCode$baseDetail"
        }
    }

    private fun sanitizeUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${uri.path}"
        } catch (_: Exception) {
            url
        }
    }
}
