package com.quern.app.data.sync

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

data class PendingDeviceLink(
    val tvOrigin: String,
    val tvPublicKeyB64: String,
    val sessionId: String,
    val deviceName: String
)

data class TvLinkInfo(
    val active: Boolean,
    val deviceName: String,
    val expiresInSec: Long,
    val locked: Boolean,
    val completed: Boolean
)

sealed interface DeviceLinkSubmitResult {
    data class Success(val email: String) : DeviceLinkSubmitResult
    data class WrongCode(val attemptsLeft: Int, val locked: Boolean) : DeviceLinkSubmitResult
    data object Expired : DeviceLinkSubmitResult
    data object AlreadyUsed : DeviceLinkSubmitResult
    data object Locked : DeviceLinkSubmitResult
    data object Inactive : DeviceLinkSubmitResult
    data class Failed(val detail: String) : DeviceLinkSubmitResult
}

/**
 * Phone-side client for approving a TV QR sign-in request over the local LAN.
 * Talks only to private LAN origins (validated by [DeviceLinkCrypto.isAllowedLanOrigin]) and
 * sends only ECDH + HKDF-SHA256 + AES-256-GCM sealed credential bundles.
 */
object DeviceLinkClient {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a `quern://link?host=...&pk=...&s=...&n=...` deep link launched by the TV's
     * companion `/link` page via Android `intent://`. Returns null if invalid or non-LAN.
     */
    fun parseDeepLink(uriString: String?): PendingDeviceLink? {
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (!uri.scheme.equals("quern", ignoreCase = true)) return null
        if (!uri.host.equals("link", ignoreCase = true)) return null

        val host = uri.getQueryParameter("host")?.trim().orEmpty().trimEnd('/')
        val pk = uri.getQueryParameter("pk")?.trim().orEmpty()
        val sessionId = uri.getQueryParameter("s")?.trim().orEmpty()
        val deviceName = uri.getQueryParameter("n")?.trim().orEmpty()
            .take(60)
            .ifBlank { "Quern TV" }

        if (!DeviceLinkCrypto.isAllowedLanOrigin(host)) return null
        if (sessionId.length !in 12..64) return null
        if (runCatching { DeviceLinkCrypto.decodePublicKey(pk) }.isFailure) return null

        return PendingDeviceLink(
            tvOrigin = host,
            tvPublicKeyB64 = pk,
            sessionId = sessionId,
            deviceName = deviceName
        )
    }

    suspend fun fetchTvInfo(
        httpClient: OkHttpClient,
        request: PendingDeviceLink
    ): TvLinkInfo? = withContext(Dispatchers.IO) {
        if (!DeviceLinkCrypto.isAllowedLanOrigin(request.tvOrigin)) return@withContext null
        val encodedSession = URLEncoder.encode(request.sessionId, "UTF-8")
        val req = Request.Builder()
            .url("${request.tvOrigin}/api/link/info?s=$encodedSession")
            .get()
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: return@use TvLinkInfo(
                        active = false,
                        deviceName = request.deviceName,
                        expiresInSec = 0L,
                        locked = false,
                        completed = false
                    )
                TvLinkInfo(
                    active = obj["active"]?.jsonPrimitive?.booleanOrNull == true && resp.isSuccessful,
                    deviceName = obj["deviceName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: request.deviceName,
                    expiresInSec = obj["expiresInSec"]?.jsonPrimitive?.longOrNull ?: 0L,
                    locked = obj["locked"]?.jsonPrimitive?.booleanOrNull == true,
                    completed = obj["completed"]?.jsonPrimitive?.booleanOrNull == true
                )
            }
        }.getOrNull()
    }

    suspend fun approveTvLink(
        httpClient: OkHttpClient,
        request: PendingDeviceLink,
        code6: String,
        bundle: DeviceLinkCrypto.LinkCredentialBundle
    ): DeviceLinkSubmitResult {
        if (!DeviceLinkCrypto.isAllowedLanOrigin(request.tvOrigin)) {
            return DeviceLinkSubmitResult.Failed("Untrusted host")
        }
        val cleanCode = DeviceLinkCrypto.normalizeCode6(code6)
        if (cleanCode.length != 6) {
            return DeviceLinkSubmitResult.WrongCode(attemptsLeft = 5, locked = false)
        }

        val envelope = withContext(Dispatchers.Default) {
            runCatching {
                DeviceLinkCrypto.sealForTv(
                    tvPublicKeyB64 = request.tvPublicKeyB64,
                    sessionId = request.sessionId,
                    code6 = cleanCode,
                    bundle = bundle
                )
            }.getOrNull()
        } ?: return DeviceLinkSubmitResult.Failed("Encryption failed")

        val bodyJson = buildJsonObject {
            put("s", envelope.sessionId)
            put("pk", envelope.phonePublicKeyB64)
            put("iv", envelope.ivB64)
            put("ct", envelope.cipherTextB64)
        }.toString()

        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${request.tvOrigin}/api/link/complete")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    val errorCode = obj?.get("error")?.jsonPrimitive?.contentOrNull.orEmpty()
                    when {
                        resp.isSuccessful && obj?.get("ok")?.jsonPrimitive?.booleanOrNull == true ->
                            DeviceLinkSubmitResult.Success(
                                obj["email"]?.jsonPrimitive?.contentOrNull ?: bundle.email
                            )
                        errorCode == "wrong_code" -> DeviceLinkSubmitResult.WrongCode(
                            attemptsLeft = obj?.get("attemptsLeft")?.jsonPrimitive?.intOrNull ?: 0,
                            locked = obj?.get("locked")?.jsonPrimitive?.booleanOrNull == true
                        )
                        errorCode == "locked" || resp.code == 423 -> DeviceLinkSubmitResult.Locked
                        errorCode == "expired" || resp.code == 410 -> DeviceLinkSubmitResult.Expired
                        errorCode == "already_used" || resp.code == 409 -> DeviceLinkSubmitResult.AlreadyUsed
                        errorCode == "inactive" || errorCode == "invalid_session" -> DeviceLinkSubmitResult.Inactive
                        else -> DeviceLinkSubmitResult.Failed(errorCode.ifBlank { "HTTP ${resp.code}" })
                    }
                }
            } catch (e: IOException) {
                DeviceLinkSubmitResult.Failed("network")
            }
        }
    }
}
