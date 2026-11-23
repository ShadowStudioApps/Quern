package com.quern.app.data.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based keys for Quern accounts (docs/account-sync-format.md, "Accounts and keys").
 *
 * The raw password never leaves the device:
 *  - masterKey  = PBKDF2-HMAC-SHA256(password, "quern-v1:" + email, 600 000 iterations, 32 bytes)
 *  - authSecret = base64url(HMAC-SHA256(masterKey, "quern-auth-v1"))  -> sent to Supabase as the password
 *  - encKey     = HMAC-SHA256(masterKey, "quern-enc-v1")                -> AES-256-GCM key for private sections
 *
 * The website implements exactly the same steps with WebCrypto, so both must stay in sync.
 */
object AccountCrypto {
    const val KDF_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8
    private const val PAYLOAD_PREFIX = "v1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    class DerivedKeys(val authSecret: String, val encKey: ByteArray)

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    /** CPU-heavy (about 1–2 s on a TV). Call off the main thread. */
    fun derive(email: String, password: String): DerivedKeys {
        val salt = ("quern-v1:" + normalizeEmail(email)).toByteArray(Charsets.UTF_8)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val master = pbkdf2HmacSha256(passwordBytes, salt, KDF_ITERATIONS)
        passwordBytes.fill(0)
        try {
            val auth = hmacSha256(master, "quern-auth-v1".toByteArray(Charsets.UTF_8))
            val enc = hmacSha256(master, "quern-enc-v1".toByteArray(Charsets.UTF_8))
            return DerivedKeys(authSecret = b64url(auth), encKey = enc)
        } finally {
            master.fill(0)
        }
    }

    /** Encrypts UTF-8 text to `v1.<iv>.<ciphertext+tag>` (base64url, no padding). */
    fun encrypt(plainText: String, encKey: ByteArray): String {
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val sealed = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "$PAYLOAD_PREFIX.${b64url(iv)}.${b64url(sealed)}"
    }

    /** Returns null when the payload is malformed or the key is wrong (e.g. after a password reset). */
    fun decrypt(payload: String, encKey: ByteArray): String? = runCatching {
        val parts = payload.split('.')
        require(parts.size == 3 && parts[0] == PAYLOAD_PREFIX)
        val iv = unb64url(parts[1])
        val sealed = unb64url(parts[2])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(sealed), Charsets.UTF_8)
    }.getOrNull()

    /** PBKDF2 with a single 32-byte output block, implemented on HMAC so it runs on every API level. */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // HMAC accepts an empty key per RFC 2104, but SecretKeySpec doesn't; passwords are validated
        // to be non-empty before we get here.
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1)) // block index 1
        var u = mac.doFinal()
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun unb64url(text: String): ByteArray =
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
