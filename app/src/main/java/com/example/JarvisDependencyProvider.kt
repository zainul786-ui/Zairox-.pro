package com.example

import android.content.Context
import com.example.ai.OpenAICompatibleProvider
import com.example.data.ConversationRepository
import com.example.data.JarvisPreferences
import com.example.security.SecureKeyStorage
import com.example.voice.AndroidSpeechInputEngine
import com.example.voice.AndroidTextToSpeechEngine
import com.example.voice.VoiceSessionCoordinator

/**
 * Dependency container ensuring MainActivity and JarvisVoiceService share
 * unified instances of the voice coordinator, secure key vault, and conversation context.
 */
object JarvisDependencyProvider {

    @Volatile
    private var isInitialized = false

    lateinit var secureKeyStorage: SecureKeyStorage
        private set

    lateinit var preferences: JarvisPreferences
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var voiceCoordinator: VoiceSessionCoordinator
        private set

    lateinit var textToSpeechEngine: AndroidTextToSpeechEngine
        private set

    lateinit var speechInputEngine: AndroidSpeechInputEngine
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        secureKeyStorage = SecureKeyStorage(appContext)
        preferences = JarvisPreferences(appContext)
        conversationRepository = ConversationRepository()

        speechInputEngine = AndroidSpeechInputEngine(appContext)
        textToSpeechEngine = AndroidTextToSpeechEngine(appContext)
        val aiProvider = OpenAICompatibleProvider()

        voiceCoordinator = VoiceSessionCoordinator(
            context = appContext,
            voiceInputEngine = speechInputEngine,
            voiceOutputEngine = textToSpeechEngine,
            conversationRepository = conversationRepository,
            preferences = preferences,
            secureKeyStorage = secureKeyStorage,
            aiProvider = aiProvider
        )

        isInitialized = true
    }
}
