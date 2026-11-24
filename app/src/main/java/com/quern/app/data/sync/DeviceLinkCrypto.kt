package com.quern.app.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encrypted LAN device linking (docs/account-sync-format.md, "Device linking").
 *
 * Protocol summary:
 *  - TV generates an ephemeral EC P-256 key pair, a 128-bit random sessionId, and a 6-digit code.
 *  - QR encodes ONLY the TV's public key (SPKI base64url), sessionId, and friendly device name.
 *  - Phone generates its own ephemeral EC P-256 key pair, runs ECDH(phonePriv, tvPub), derives a
 *    32-byte AES-256-GCM key via HKDF-SHA256 with salt = "quern-link-v1:<sessionId>:<code6>" and
 *    info = "quern-link-aes256gcm-v1:<tvPubB64>:<phonePubB64>", and seals
 *    { v: 1, email, authSecret, encKey } with AAD = "quern-link-v1:<sessionId>".
 *  - Neither the raw password, authSecret, encKey, nor the 6-digit code ever travels in plaintext.
 *  - Uses only standard JCA primitives supported on Android API 24+ and WebCrypto (P-256 SPKI +
 *    ECDH + HKDF-SHA256 + AES-256-GCM) so quern.pages.dev can speak the exact same protocol later.
 */
object DeviceLinkCrypto {
    private const val CURVE_NAME = "secp256r1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PAYLOAD_VERSION = 1
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    data class LinkCredentialBundle(
        val email: String,
        val authSecret: String,
        val encKeyB64: String
    )

    data class SealedLinkEnvelope(
        val sessionId: String,
        val phonePublicKeyB64: String,
        val ivB64: String,
        val cipherTextB64: String
    )

    fun generateEphemeralKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec(CURVE_NAME), random)
        return gen.generateKeyPair()
    }

    /** Standard X.509 SubjectPublicKeyInfo DER encoded as base64url (no padding). */
    fun encodePublicKey(publicKey: PublicKey): String = AccountCrypto.b64url(publicKey.encoded)

    fun decodePublicKey(publicKeyB64: String): PublicKey {
        val der = AccountCrypto.unb64url(publicKeyB64.trim())
        require(der.size in 32..256) { "Invalid public key length" }
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    /** 128-bit random session identifier (base64url, 22 chars). */
    fun generateSessionId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return AccountCrypto.b64url(bytes)
    }

    /** Cryptographically random 6-digit confirmation code (`000000`..`999999`). */
    fun generateCode6(): String = String.format(Locale.US, "%06d", random.nextInt(1_000_000))

    fun normalizeCode6(raw: String): String = raw.filter(Char::isDigit).take(6)

    fun formatCode6ForDisplay(code6: String): String {
        val clean = normalizeCode6(code6)
        return if (clean.length == 6) "${clean.substring(0, 3)} ${clean.substring(3, 6)}" else clean
    }

    /**
     * Phone side: encrypts [bundle] for the TV identified by [tvPublicKeyB64] and [sessionId],
     * authenticating [code6] inside HKDF salt so a wrong code fails AEAD decryption on the TV.
     */
    fun sealForTv(
        tvPublicKeyB64: String,
        sessionId: String,
        code6: String,
        bundle: LinkCredentialBundle
    ): SealedLinkEnvelope {
        val cleanCode = normalizeCode6(code6)
        require(cleanCode.length == 6) { "Confirmation code must be 6 digits" }
        require(sessionId.isNotBlank()) { "Session id is required" }

        val tvPub = decodePublicKey(tvPublicKeyB64)
        val phoneKeyPair = generateEphemeralKeyPair()
        val phonePubB64 = encodePublicKey(phoneKeyPair.public)

        val aesKey = deriveAesKey(
            localPrivateKey = phoneKeyPair.private,
            peerPublicKey = tvPub,
            tvPublicKeyB64 = tvPublicKeyB64.trim(),
            phonePublicKeyB64 = phonePubB64,
            sessionId = sessionId,
            code6 = cleanCode
        )
        val plainBytes = buildJsonObject {
            put("v", PAYLOAD_VERSION)
            put("email", AccountCrypto.normalizeEmail(bundle.email))
            put("authSecret", bundle.authSecret)
            put("encKey", bundle.encKeyB64)
        }.toString().toByteArray(Charsets.UTF_8)

        try {
            val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aadBytes(sessionId))
            val sealed = cipher.doFinal(plainBytes)
            return SealedLinkEnvelope(
                sessionId = sessionId,
                phonePublicKeyB64 = phonePubB64,
                ivB64 = AccountCrypto.b64url(iv),
                cipherTextB64 = AccountCrypto.b64url(sealed)
            )
        } finally {
            aesKey.fill(0)
            plainBytes.fill(0)
        }
    }

    /**
     * TV side: decrypts [envelope] using the TV's ephemeral [tvKeyPair] and its displayed [code6].
     * Returns null when the sessionId, public key, code6, or ciphertext is invalid.
     */
    fun openOnTv(
        tvKeyPair: KeyPair,
        tvPublicKeyB64: String,
        sessionId: String,
        code6: String,
        envelope: SealedLinkEnvelope
    ): LinkCredentialBundle? {
        if (!MessageDigest.isEqual(
                envelope.sessionId.toByteArray(Charsets.UTF_8),
                sessionId.toByteArray(Charsets.UTF_8)
            )
        ) return null

        val cleanCode = normalizeCode6(code6)
        if (cleanCode.length != 6) return null

        return runCatching {
            val phonePub = decodePublicKey(envelope.phonePublicKeyB64)
            val iv = AccountCrypto.unb64url(envelope.ivB64.trim())
            if (iv.size != GCM_IV_BYTES) return null
            val cipherBytes = AccountCrypto.unb64url(envelope.cipherTextB64.trim())
            if (cipherBytes.size <= 16 || cipherBytes.size > 8192) return null

            val aesKey = deriveAesKey(
                localPrivateKey = tvKeyPair.private,
                peerPublicKey = phonePub,
                tvPublicKeyB64 = tvPublicKeyB64.trim(),
                phonePublicKeyB64 = envelope.phonePublicKeyB64.trim(),
                sessionId = sessionId,
                code6 = cleanCode
            )
            val plainBytes = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(aadBytes(sessionId))
                cipher.doFinal(cipherBytes)
            } finally {
                aesKey.fill(0)
            }
            try {
                val obj = json.parseToJsonElement(String(plainBytes, Charsets.UTF_8)).jsonObject
                if (obj["v"]?.jsonPrimitive?.intOrNull != PAYLOAD_VERSION) return null
                val email = obj["email"]?.jsonPrimitive?.contentOrNull?.let(AccountCrypto::normalizeEmail).orEmpty()
                val authSecret = obj["authSecret"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val encKeyB64 = obj["encKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (email.isBlank() || authSecret.isBlank() || encKeyB64.isBlank()) return null
                val decodedEnc = AccountCrypto.unb64url(encKeyB64)
                val validEncLen = decodedEnc.size == 32
                decodedEnc.fill(0)
                if (!validEncLen) return null
                LinkCredentialBundle(email = email, authSecret = authSecret, encKeyB64 = encKeyB64)
            } finally {
                plainBytes.fill(0)
            }
        }.getOrNull()
    }

    /**
     * Restricts phone-side LAN link requests to private/local network hosts so a crafted link
     * cannot direct the phone to POST encrypted envelopes to an arbitrary internet server.
     */
    fun isAllowedLanOrigin(originUrl: String): Boolean {
        val url = originUrl.trim().toHttpUrlOrNull() ?: return false
        if (url.scheme != "http" && url.scheme != "https") return false
        val host = url.host.lowercase(Locale.US)
        if (host == "localhost" || host.endsWith(".local")) return true
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets[0] to octets[1]
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }

    /** ECDH shared secret -> HKDF-SHA256 (RFC 5869, 32-byte key). */
    private fun deriveAesKey(
        localPrivateKey: PrivateKey,
        peerPublicKey: PublicKey,
        tvPublicKeyB64: String,
        phonePublicKeyB64: String,
        sessionId: String,
        code6: String
    ): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPrivateKey)
        agreement.doPhase(peerPublicKey, true)
        val shared = agreement.generateSecret()
        val salt = "quern-link-v1:$sessionId:$code6".toByteArray(Charsets.UTF_8)
        val info = "quern-link-aes256gcm-v1:$tvPublicKeyB64:$phonePublicKeyB64".toByteArray(Charsets.UTF_8)
        var prk: ByteArray? = null
        return try {
            // HKDF-Extract(salt, IKM = shared)
            prk = hmacSha256(key = salt, data = shared)
            // HKDF-Expand(PRK, info, L = 32): single block T(1) = HMAC(PRK, info || 0x01)
            val expandInput = ByteArray(info.size + 1)
            System.arraycopy(info, 0, expandInput, 0, info.size)
            expandInput[info.size] = 0x01
            hmacSha256(key = prk, data = expandInput)
        } finally {
            shared.fill(0)
            prk?.fill(0)
        }
    }

    private fun aadBytes(sessionId: String): ByteArray =
        "quern-link-v1:$sessionId".toByteArray(Charsets.UTF_8)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
