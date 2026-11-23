package com.quern.app.data.sync

import android.util.Log
import com.quern.app.data.local.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom

data class QuernAccountState(
    val isSignedIn: Boolean = false,
    val email: String = "",
    val userId: String = "",
    /** Signed up but the confirmation link in the email hasn't been opened yet. */
    val awaitingEmailConfirmation: String? = null
)

/** Why an account action failed, so the UI can show a clear, translated message. */
enum class AccountError {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    WRONG_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    RATE_LIMITED,
    NETWORK,
    SERVER
}

class AccountException(val reason: AccountError, message: String) : Exception(message)

/** Result of the last account action, shown (translated) on the account screen. */
sealed interface AccountUiMessage {
    data object SignedIn : AccountUiMessage
    data object SignedUp : AccountUiMessage
    data object ConfirmationResent : AccountUiMessage
    data object SignedOut : AccountUiMessage
    data object Deleted : AccountUiMessage
    data class Failed(val reason: AccountError) : AccountUiMessage
}

/**
 * Quern account on Shadow Studio's server (Supabase Auth), as specified in
 * docs/account-sync-format.md. Holds the session and the encryption key; the sync engine
 * uses [authorizedCall] to talk to the data API.
 */
class QuernAccount(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val vault: SecureVault
) {
    private val baseUrl = QuernCloudConfig.SUPABASE_URL
    private val apiKey = QuernCloudConfig.PUBLISHABLE_KEY
    private val refreshLock = Mutex()

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<QuernAccountState> = _state.asStateFlow()

    /** Random per-install id used for `device_<id>` rows; not linked to any personal data. */
    val deviceId: String by lazy {
        vault.getString(KEY_DEVICE_ID).ifBlank {
            val letters = "abcdefghijklmnopqrstuvwxyz"
            val rnd = SecureRandom()
            val id = (1..12).map { letters[rnd.nextInt(letters.length)] }.joinToString("")
            vault.putString(KEY_DEVICE_ID, id)
            id
        }
    }

    /** AES key for encrypted sections, or null when signed out. */
    fun encryptionKey(): ByteArray? =
        vault.getString(KEY_ENC_KEY).takeIf { it.isNotBlank() }?.let { AccountCrypto.unb64url(it) }

    /** When this device signed in, by the server's clock (epoch ms), or 0 if unknown. */
    fun signedInAtMs(): Long = parseServerTime(vault.getString(KEY_SIGNED_IN_AT))

    suspend fun signUp(email: String, password: String): Result<Unit> = runAccount {
        val cleanEmail = validate(email, password)
        val keys = withContext(Dispatchers.Default) { AccountCrypto.derive(cleanEmail, password) }
        val body = buildJsonObject {
            put("email", cleanEmail)
            put("password", keys.authSecret)
        }
        val root = post(
            "$baseUrl/auth/v1/signup?redirect_to=${QuernCloudConfig.WEBSITE_URL}/confirmed",
            body
        )
        if (root["access_token"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
            // Email confirmation is on: no session until the link in the email is opened.
            vault.putString(KEY_PENDING_EMAIL, cleanEmail)
            _state.value = loadState()
        } else {
            saveSession(root, cleanEmail, keys.encKey)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runAccount {
        val cleanEmail = validate(email, password)
        val keys = withContext(Dispatchers.Default) { AccountCrypto.derive(cleanEmail, password) }
        val body = buildJsonObject {
            put("email", cleanEmail)
            put("password", keys.authSecret)
        }
        val root = post("$baseUrl/auth/v1/token?grant_type=password", body)
        saveSession(root, cleanEmail, keys.encKey)
    }

    suspend fun resendConfirmation(email: String): Result<Unit> = runAccount {
        val body = buildJsonObject {
            put("type", "signup")
            put("email", AccountCrypto.normalizeEmail(email))
        }
        post("$baseUrl/auth/v1/resend", body)
    }

    /** Sends the reset email. The new password is set on the website, which re-derives the keys. */
    suspend fun requestPasswordReset(email: String): Result<Unit> = runAccount {
        val body = buildJsonObject { put("email", AccountCrypto.normalizeEmail(email)) }
        post("$baseUrl/auth/v1/recover?redirect_to=${QuernCloudConfig.WEBSITE_URL}/reset", body)
    }

    /** Ends the session on the server (best effort) and forgets it locally. Local app data stays. */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            runCatching {
                authorizedCall { token ->
                    Request.Builder()
                        .url("$baseUrl/auth/v1/logout")
                        .header("Authorization", "Bearer $token")
                        .post(EMPTY_BODY)
                        .build()
                }.close()
            }
        }
        clearSession()
    }

    /** Deletes the account and every synced row on the server. Local app data stays. */
    suspend fun deleteAccount(): Result<Unit> = runAccount {
        authorizedCall { token ->
            Request.Builder()
                .url("$baseUrl/rest/v1/rpc/quern_delete_my_account")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
        }.use { resp ->
            if (!resp.isSuccessful) throw serverError(resp.code, resp.body?.string().orEmpty())
        }
        clearSession()
    }

    /**
     * Runs a request with the current access token. On 401 it refreshes the session once and
     * retries. Signs out locally if the refresh token is no longer valid. Caller closes the response.
     */
    suspend fun authorizedCall(build: (accessToken: String) -> Request): okhttp3.Response =
        withContext(Dispatchers.IO) {
            val token = vault.getString(KEY_ACCESS_TOKEN)
            if (token.isBlank()) throw AccountException(AccountError.WRONG_CREDENTIALS, "Not signed in")
            val first = httpClient.newCall(withApiKey(build(token))).execute()
            if (first.code != 401) return@withContext first
            first.close()
            val refreshed = refreshSession(staleToken = token)
            httpClient.newCall(withApiKey(build(refreshed))).execute()
        }

    private suspend fun refreshSession(staleToken: String): String = refreshLock.withLock {
        // Another caller may already have refreshed while we waited for the lock.
        val current = vault.getString(KEY_ACCESS_TOKEN)
        if (current.isNotBlank() && current != staleToken) return current

        val refreshToken = vault.getString(KEY_REFRESH_TOKEN)
        if (refreshToken.isBlank()) {
            clearSession()
            throw AccountException(AccountError.WRONG_CREDENTIALS, "Session expired")
        }
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val root = try {
            post("$baseUrl/auth/v1/token?grant_type=refresh_token", body)
        } catch (e: AccountException) {
            if (e.reason == AccountError.WRONG_CREDENTIALS) clearSession()
            throw e
        }
        val newToken = root["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val newRefresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (newToken.isBlank()) throw AccountException(AccountError.SERVER, "Refresh returned no token")
        vault.putString(KEY_ACCESS_TOKEN, newToken)
        if (newRefresh.isNotBlank()) vault.putString(KEY_REFRESH_TOKEN, newRefresh)
        newToken
    }

    private fun saveSession(root: JsonObject, email: String, encKey: ByteArray) {
        val token = root["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val userId = root["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (token.isBlank() || userId.isBlank()) throw AccountException(AccountError.SERVER, "Incomplete session")
        vault.putString(KEY_ACCESS_TOKEN, token)
        vault.putString(KEY_REFRESH_TOKEN, refresh)
        vault.putString(KEY_USER_ID, userId)
        vault.putString(KEY_EMAIL, email)
        vault.putString(KEY_ENC_KEY, AccountCrypto.b64url(encKey))
        // Server clock, so it can be compared with row timestamps (see QuernSyncEngine key mismatch).
        val signedInAt = root["user"]?.jsonObject?.get("last_sign_in_at")?.jsonPrimitive?.contentOrNull
        vault.putString(KEY_SIGNED_IN_AT, signedInAt.orEmpty())
        vault.remove(KEY_PENDING_EMAIL)
        encKey.fill(0)
        _state.value = loadState()
    }

    private fun clearSession() {
        listOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_USER_ID, KEY_EMAIL, KEY_ENC_KEY, KEY_PENDING_EMAIL, KEY_SIGNED_IN_AT)
            .forEach(vault::remove)
        _state.value = loadState()
    }

    private fun loadState() = QuernAccountState(
        isSignedIn = vault.getString(KEY_ACCESS_TOKEN).isNotBlank() && vault.getString(KEY_ENC_KEY).isNotBlank(),
        email = vault.getString(KEY_EMAIL),
        userId = vault.getString(KEY_USER_ID),
        awaitingEmailConfirmation = vault.getString(KEY_PENDING_EMAIL).takeIf { it.isNotBlank() }
    )

    private fun validate(email: String, password: String): String {
        val clean = AccountCrypto.normalizeEmail(email)
        if (!EMAIL_REGEX.matches(clean)) throw AccountException(AccountError.INVALID_EMAIL, "Invalid email")
        if (password.length < AccountCrypto.MIN_PASSWORD_LENGTH) {
            throw AccountException(AccountError.WEAK_PASSWORD, "Password too short")
        }
        return clean
    }

    private suspend fun post(url: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val req = withApiKey(
            Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA)).build()
        )
        val resp = try {
            httpClient.newCall(req).execute()
        } catch (e: IOException) {
            throw AccountException(AccountError.NETWORK, e.message ?: "Network error")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw serverError(it.code, text)
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }
    }

    private fun serverError(code: Int, body: String): AccountException {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val errorCode = obj?.get("error_code")?.jsonPrimitive?.contentOrNull.orEmpty()
        val message = obj?.get("msg")?.jsonPrimitive?.contentOrNull
            ?: obj?.get("error_description")?.jsonPrimitive?.contentOrNull
            ?: obj?.get("message")?.jsonPrimitive?.contentOrNull
            ?: "HTTP $code"
        val reason = when {
            code == 429 || errorCode.contains("rate_limit") -> AccountError.RATE_LIMITED
            errorCode == "email_not_confirmed" -> AccountError.EMAIL_NOT_CONFIRMED
            errorCode == "weak_password" -> AccountError.WEAK_PASSWORD
            errorCode == "invalid_credentials" || errorCode == "refresh_token_not_found" ||
                errorCode == "refresh_token_already_used" || code == 401 -> AccountError.WRONG_CREDENTIALS
            errorCode == "email_address_invalid" -> AccountError.INVALID_EMAIL
            else -> AccountError.SERVER
        }
        Log.w(TAG, "Account request failed: HTTP $code $errorCode")
        return AccountException(reason, message)
    }

    private fun withApiKey(req: Request): Request =
        req.newBuilder().header("apikey", apiKey).header("Content-Type", "application/json").build()

    private suspend fun runAccount(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: AccountException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(AccountException(AccountError.NETWORK, e.message ?: "Network error"))
        }

    companion object {
        private const val TAG = "QuernAccount"
        private val JSON_MEDIA = "application/json".toMediaType()
        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        private const val KEY_ACCESS_TOKEN = "acct_access_token"
        private const val KEY_REFRESH_TOKEN = "acct_refresh_token"
        private const val KEY_USER_ID = "acct_user_id"
        private const val KEY_EMAIL = "acct_email"
        private const val KEY_ENC_KEY = "acct_enc_key"
        private const val KEY_PENDING_EMAIL = "acct_pending_email"
        private const val KEY_DEVICE_ID = "acct_device_id"
        private const val KEY_SIGNED_IN_AT = "acct_signed_in_at"

        /** Parses Supabase/PostgreSQL timestamps such as `2026-11-23T10:15:30.123456+00:00`; 0 when invalid. */
        fun parseServerTime(text: String?): Long {
            if (text.isNullOrBlank()) return 0L
            val normalized = text.trim()
                .replace(' ', 'T')
                .replace(Regex("\\.\\d+"), "")
                .replace(Regex("Z$"), "+00:00")
            return runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(normalized)?.time
            }.getOrNull() ?: 0L
        }
    }
}
