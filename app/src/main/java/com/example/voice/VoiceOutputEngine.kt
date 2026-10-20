package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

interface VoiceOutputListener {
    fun onSpeechStart()
    fun onSpeechDone()
    fun onSpeechError(errorMessage: String)
}

interface VoiceOutputEngine {
    fun init(onReady: (Boolean) -> Unit)
    fun speak(text: String, listener: VoiceOutputListener? = null)
    fun stop()
    fun isSpeaking(): Boolean
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun setVoice(voiceName: String?)
    fun getAvailableVoices(): List<Voice>
    fun shutdown()
}

class AndroidTextToSpeechEngine(private val context: Context) : VoiceOutputEngine {

    private var tts: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private var currentListener: VoiceOutputListener? = null
    private var currentSpeechRate: Float = 1.0f
    private var currentSpeechPitch: Float = 1.0f
    private var currentVoiceName: String? = null

    override fun init(onReady: (Boolean) -> Unit) {
        if (isInitialized && tts != null) {
            onReady(true)
            return
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                applySettings()
                setupProgressListener()
                onReady(true)
            } else {
                isInitialized = false
                onReady(false)
            }
        }
    }

    private fun applySettings() {
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentSpeechPitch)
        if (!currentVoiceName.isNullOrBlank()) {
            val matchingVoice = tts?.voices?.firstOrNull { it.name == currentVoiceName }
            if (matchingVoice != null) {
                tts?.voice = matchingVoice
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentListener?.onSpeechStart()
            }

            override fun onDone(utteranceId: String?) {
                currentListener?.onSpeechDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                currentListener?.onSpeechError("Text-to-speech rendering failed.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                currentListener?.onSpeechError("Text-to-speech error code: $errorCode")
            }
        })
    }

    override fun speak(text: String, listener: VoiceOutputListener?) {
        this.currentListener = listener
        if (!isInitialized || tts == null) {
            init { ready ->
                if (ready) {
                    executeSpeak(text)
                } else {
                    listener?.onSpeechError("Text-to-speech engine failed to initialize.")
                }
            }
            return
        }

        executeSpeak(text)
    }

    private fun executeSpeak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    override fun setRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    override fun setPitch(pitch: Float) {
        currentSpeechPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentSpeechPitch)
    }

    override fun setVoice(voiceName: String?) {
        currentVoiceName = voiceName
        if (!voiceName.isNullOrBlank()) {
            val matching = tts?.voices?.firstOrNull { it.name == voiceName }
            if (matching != null) {
                tts?.voice = matching
            }
        }
    }

    override fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
        currentListener = null
    }
}
