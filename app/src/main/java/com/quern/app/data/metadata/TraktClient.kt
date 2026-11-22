package com.quern.app.data.metadata

import com.quern.app.data.local.SecureVault
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.TraktAuthState
import com.quern.app.domain.model.TraktDeviceCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Trakt v2 API Client with TV-friendly OAuth Device Code Authentication & Playback Scrobbling.
 */
class TraktClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val vault: SecureVault
) {
    fun getState(): TraktAuthState = TraktAuthState(
        clientId = vault.getString(KEY_CLIENT_ID),
        clientSecret = vault.getString(KEY_CLIENT_SECRET),
        accessToken = vault.getString(KEY_ACCESS_TOKEN),
        refreshToken = vault.getString(KEY_REFRESH_TOKEN),
        username = vault.getString(KEY_USERNAME).takeIf { it.isNotBlank() }
    )

    fun saveClientCredentials(clientId: String, clientSecret: String) {
        vault.putString(KEY_CLIENT_ID, clientId.trim())
        vault.putString(KEY_CLIENT_SECRET, clientSecret.trim())
    }

    fun disconnect() {
        vault.remove(KEY_ACCESS_TOKEN)
        vault.remove(KEY_REFRESH_TOKEN)
        vault.remove(KEY_USERNAME)
    }

    suspend fun requestDeviceCode(clientId: String): Result<TraktDeviceCode> = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Trakt Client ID required"))
        runCatching {
            val payload = buildJsonObject { put("client_id", clientId.trim()) }
                .toString().toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url("$BASE_URL/oauth/device/code")
                .header("Content-Type", "application/json")
                .post(payload)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Trakt Device Code failed (HTTP ${resp.code})")
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                TraktDeviceCode(
                    deviceCode = obj["device_code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    userCode = obj["user_code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    verificationUrl = obj["verification_url"]?.jsonPrimitive?.contentOrNull ?: "https://trakt.tv/activate",
                    expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 600,
                    interval = obj["interval"]?.jsonPrimitive?.intOrNull ?: 5
                )
            }
        }
    }

    suspend fun pollDeviceToken(deviceCode: String, clientId: String, clientSecret: String): Result<TraktAuthState> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("code", deviceCode)
                    put("client_id", clientId.trim())
                    put("client_secret", clientSecret.trim())
                }.toString().toRequestBody(JSON_MEDIA)

                val req = Request.Builder()
                    .url("$BASE_URL/oauth/device/token")
                    .header("Content-Type", "application/json")
                    .post(payload)
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 400) error("Pending user activation on trakt.tv/activate...")
                    if (!resp.isSuccessful) error("Trakt token poll failed (HTTP ${resp.code})")
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val access = obj["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    vault.putString(KEY_CLIENT_ID, clientId.trim())
                    vault.putString(KEY_CLIENT_SECRET, clientSecret.trim())
                    vault.putString(KEY_ACCESS_TOKEN, access)
                    vault.putString(KEY_REFRESH_TOKEN, refresh)
                    val username = fetchUsername(clientId.trim(), access)
                    if (!username.isNullOrBlank()) vault.putString(KEY_USERNAME, username)
                    getState()
                }
            }
        }

    suspend fun scrobble(
        action: String, // "start", "pause", "stop"
        imdbId: String?,
        type: MediaType,
        progressPercent: Float
    ) = withContext(Dispatchers.IO) {
        val state = getState()
        if (!state.isAuthenticated || state.clientId.isBlank() || imdbId.isNullOrBlank()) return@withContext
        runCatching {
            val payload = buildJsonObject {
                put("progress", progressPercent.coerceIn(0f, 100f))
                if (type == MediaType.MOVIE) {
                    putJsonObject("movie") {
                        putJsonObject("ids") { put("imdb", imdbId) }
                    }
                } else {
                    putJsonObject("show") {
                        putJsonObject("ids") { put("imdb", imdbId) }
                    }
                }
            }.toString().toRequestBody(JSON_MEDIA)

            val req = Request.Builder()
                .url("$BASE_URL/scrobble/$action")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", state.clientId)
                .header("Authorization", "Bearer ${state.accessToken}")
                .post(payload)
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private fun fetchUsername(clientId: String, accessToken: String): String? = runCatching {
        val req = Request.Builder()
            .url("$BASE_URL/users/settings")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["user"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    companion object {
        private const val BASE_URL = "https://api.trakt.tv"
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val KEY_CLIENT_ID = "trakt_client_id"
        private const val KEY_CLIENT_SECRET = "trakt_client_secret"
        private const val KEY_ACCESS_TOKEN = "trakt_access_token"
        private const val KEY_REFRESH_TOKEN = "trakt_refresh_token"
        private const val KEY_USERNAME = "trakt_username"
    }
}
