package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed AES-256-GCM secure storage for API credentials.
 * Ensures API keys are encrypted at hardware/OS level and never stored in plaintext.
 */
class SecureKeyStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "JarvisApiKeyStorageKey"
        private const val PREFS_NAME = "jarvis_secure_vault"
        private const val PREF_ENCRYPTED_API_KEY = "encrypted_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("KeyStore entry missing")
        return entry.secretKey
    }

    @Synchronized
    fun saveApiKey(apiKey: String): Boolean {
        return try {
            if (apiKey.isBlank()) {
                clearApiKey()
                return true
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

            // Combine IV length (1 byte) + IV + encrypted data
            val combined = ByteArray(1 + iv.size + encryptedBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, 1 + iv.size, encryptedBytes.size)

            val base64Payload = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(PREF_ENCRYPTED_API_KEY, base64Payload).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    fun getApiKey(): String {
        return try {
            val base64Payload = prefs.getString(PREF_ENCRYPTED_API_KEY, null) ?: return ""
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.isEmpty()) return ""

            val ivSize = combined[0].toInt()
            if (combined.size <= 1 + ivSize) return ""

            val iv = ByteArray(ivSize)
            System.arraycopy(combined, 1, iv, 0, ivSize)

            val encryptedSize = combined.size - 1 - ivSize
            val encryptedBytes = ByteArray(encryptedSize)
            System.arraycopy(combined, 1 + ivSize, encryptedBytes, 0, encryptedSize)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (e.g. key invalidated or corrupt), return empty string gracefully
            ""
        }
    }

    @Synchronized
    fun clearApiKey() {
        prefs.edit().remove(PREF_ENCRYPTED_API_KEY).apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }
}
