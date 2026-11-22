package com.quern.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive API keys and server credentials using Android Keystore AES-256-GCM.
 */
class SecureVault(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quern_secure_vault", Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }.getOrNull()

    fun putString(key: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(key).apply()
            return
        }
        val secretKey = getOrCreateKey()
        val stored = if (secretKey != null) {
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                "ENC:$ivB64:$dataB64"
            }.getOrElse { "RAW:$value" }
        } else {
            "RAW:$value"
        }
        prefs.edit().putString(key, stored).apply()
    }

    fun getString(key: String, default: String = ""): String {
        val raw = prefs.getString(key, null) ?: return default
        if (raw.startsWith("RAW:")) return raw.removePrefix("RAW:")
        if (!raw.startsWith("ENC:")) return raw

        val parts = raw.split(":")
        if (parts.size != 3) return default
        val secretKey = getOrCreateKey() ?: return default
        return runCatching {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrDefault(default)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "quern_vault_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
