package com.quern.app.data.local

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 PIN hasher using only standard Android JCA APIs (zero extra dependencies).
 * Format: "pbkdf2v1:<iterations>:<base64Salt>:<base64DerivedKey>"
 * Never logs any PIN or hash material. Fails closed on any missing, corrupt, or wrong-format stored value.
 */
object PinHasher {
    private const val PREFIX = "pbkdf2v1"
    private const val ITERATIONS = 100_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val KEY_BYTES = KEY_BITS / 8
    private val rng = SecureRandom()

    fun isHashed(stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iter = parts[1].toIntOrNull() ?: return false
        return iter in MIN_ITERATIONS..MAX_ITERATIONS &&
            parts[2].isNotBlank() &&
            parts[3].isNotBlank()
    }

    fun hashPin(rawPin: String): String {
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val derived = derive(rawPin.toCharArray(), salt, ITERATIONS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val keyB64 = Base64.encodeToString(derived, Base64.NO_WRAP)
        return "$PREFIX:$ITERATIONS:$saltB64:$keyB64"
    }

    suspend fun hashPinAsync(rawPin: String): String = withContext(Dispatchers.Default) {
        hashPin(rawPin)
    }

    /**
     * Fails closed on any missing, empty, unhashed, corrupt, or wrong-format stored hash.
     */
    fun verifyPin(rawPin: String, stored: String?): Boolean {
        if (rawPin.isBlank() || !isHashed(stored)) return false
        val parts = stored!!.split(":")
        val iterations = parts[1].toIntOrNull()?.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS } ?: return false
        val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
        val expectedKey = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
        if (salt.size != SALT_BYTES || expectedKey.size != KEY_BYTES) return false
        val candidateKey = runCatching { derive(rawPin.toCharArray(), salt, iterations) }.getOrNull() ?: return false
        return MessageDigest.isEqual(candidateKey, expectedKey)
    }

    suspend fun verifyPinAsync(rawPin: String, stored: String?): Boolean = withContext(Dispatchers.Default) {
        verifyPin(rawPin, stored)
    }

    private fun derive(chars: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
