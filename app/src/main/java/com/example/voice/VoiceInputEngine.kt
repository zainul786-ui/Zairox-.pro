package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

interface VoiceInputListener {
    fun onReady()
    fun onRmsChanged(rmsdB: Float)
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(errorMessage: String, isTransient: Boolean)
}

interface VoiceInputEngine {
    fun isAvailable(): Boolean
    fun startListening(listener: VoiceInputListener, preferredLanguage: String? = null)
    fun stopListening()
    fun destroy()
}

class AndroidSpeechInputEngine(private val context: Context) : VoiceInputEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentListener: VoiceInputListener? = null
    private var isListening: Boolean = false

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(listener: VoiceInputListener, preferredLanguage: String?) {
        this.currentListener = listener

        if (!isAvailable()) {
            listener.onError("Speech recognition service is not available on this device.", isTransient = false)
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    currentListener?.onReady()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    currentListener?.onRmsChanged(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    val (message, isTransient) = mapErrorCode(error)
                    currentListener?.onError(message, isTransient)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognized = matches?.firstOrNull()?.trim() ?: ""
                    if (recognized.isNotBlank()) {
                        currentListener?.onFinalResult(recognized)
                    } else {
                        currentListener?.onError("No speech recognized", isTransient = true)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = partialMatches?.firstOrNull()?.trim() ?: ""
                    if (partial.isNotBlank()) {
                        currentListener?.onPartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val language = preferredLanguage ?: Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            isListening = false
            listener.onError("Failed to start voice input: ${e.message}", isTransient = false)
        }
    }

    override fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            isListening = false
        }
    }

    override fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        currentListener = null
        isListening = false
    }

    private fun mapErrorCode(error: Int): Pair<String, Boolean> {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error" to false
            SpeechRecognizer.ERROR_CLIENT -> "Client error in speech engine" to true
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required" to false
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition" to true
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout while recognizing speech" to true
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected" to true
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy" to true
            SpeechRecognizer.ERROR_SERVER -> "Speech server error" to true
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)" to true
            else -> "Speech recognition error ($error)" to true
        }
    }
}
