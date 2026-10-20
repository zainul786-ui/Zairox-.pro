package com.example

import android.content.Context
import com.example.ai.OpenAICompatibleProvider
import com.example.data.ConversationRepository
import com.example.data.JarvisPreferences
import com.example.security.SecureKeyStorage
import com.example.voice.AndroidSpeechInputEngine
import com.example.voice.AndroidTextToSpeechEngine
import com.example.voice.ContinuousAudioCaptureEngine
import com.example.voice.RealtimeAudioPlayer
import com.example.voice.VoiceSessionCoordinator

/**
 * Dependency container ensuring MainActivity and JarvisVoiceService share
 * unified instances of the voice coordinator, persistent AudioRecord capture engine,
 * low-latency audio player, secure key vault, and conversation context.
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

    lateinit var continuousCaptureEngine: ContinuousAudioCaptureEngine
        private set

    lateinit var realtimeAudioPlayer: RealtimeAudioPlayer
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

        // 1. Persistent single AudioRecord engine with hardware AEC & adaptive VAD
        continuousCaptureEngine = ContinuousAudioCaptureEngine()

        // 2. Dedicated low-latency streaming PCM player for real-time live synthesis
        realtimeAudioPlayer = RealtimeAudioPlayer()

        // 3. Fallback engines
        speechInputEngine = AndroidSpeechInputEngine(appContext)
        textToSpeechEngine = AndroidTextToSpeechEngine(appContext)
        val aiProvider = OpenAICompatibleProvider()

        // 4. Universal Voice Session Coordinator
        voiceCoordinator = VoiceSessionCoordinator(
            context = appContext,
            continuousCaptureEngine = continuousCaptureEngine,
            realtimeAudioPlayer = realtimeAudioPlayer,
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
