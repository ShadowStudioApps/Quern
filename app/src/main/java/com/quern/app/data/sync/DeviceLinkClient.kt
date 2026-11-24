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
     * Parses either:
     * - a `quern://link?host=...&pk=...&s=...&n=...` native deep link, OR
     * - a `http://<lan-ip>:<port>/link#pk=...&s=...&n=...` URL encoded directly in the TV's QR.
     * Returns null if invalid or non-LAN.
     */
    fun parseDeepLink(uriString: String?): PendingDeviceLink? = parseQrOrLink(uriString)

    fun parseQrOrLink(rawString: String?): PendingDeviceLink? {
        if (rawString.isNullOrBlank()) return null
        val trimmed = rawString.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()

        val host: String
        val pk: String
        val sessionId: String
        val deviceName: String

        when {
            scheme == "quern" && uri.host.equals("link", ignoreCase = true) -> {
                host = uri.getQueryParameter("host")?.trim().orEmpty().trimEnd('/')
                pk = uri.getQueryParameter("pk")?.trim().orEmpty()
                sessionId = uri.getQueryParameter("s")?.trim().orEmpty()
                deviceName = uri.getQueryParameter("n")?.trim().orEmpty()
                    .take(60)
                    .ifBlank { "Quern TV" }
            }
            (scheme == "http" || scheme == "https") &&
                uri.path.orEmpty().trimEnd('/').equals("/link", ignoreCase = true) &&
                !uri.fragment.isNullOrBlank() -> {
                val authority = uri.encodedAuthority?.takeIf { it.isNotBlank() } ?: return null
                host = "$scheme://$authority"
                val fragUri = runCatching { Uri.parse("quern://frag?${uri.encodedFragment}") }.getOrNull()
                    ?: return null
                pk = fragUri.getQueryParameter("pk")?.trim().orEmpty()
                sessionId = fragUri.getQueryParameter("s")?.trim().orEmpty()
                deviceName = fragUri.getQueryParameter("n")?.trim().orEmpty()
                    .take(60)
                    .ifBlank { "Quern TV" }
            }
            else -> return null
        }

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

    /**
     * Normalizes a user-entered TV address (`192.168.1.10`, `192.168.1.10:8787`, or
     * `http://192.168.1.10:8787`) to `http://<host>:<port>` and enforces [DeviceLinkCrypto.isAllowedLanOrigin].
     */
    fun normalizeManualTvOrigin(rawInput: String?): String? {
        val cleaned = rawInput?.trim().orEmpty()
        if (cleaned.isBlank()) return null
        val withScheme = if (cleaned.startsWith("http://", ignoreCase = true) ||
            cleaned.startsWith("https://", ignoreCase = true)
        ) {
            cleaned
        } else {
            "http://$cleaned"
        }
        val parsed = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val rawHost = parsed.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (parsed.port == -1) 8787 else parsed.port
        if (port !in 1..65535) return null
        val formattedHost = if (':' in rawHost && !rawHost.startsWith("[")) "[$rawHost]" else rawHost
        val origin = "$scheme://$formattedHost:$port"
        return origin.takeIf { DeviceLinkCrypto.isAllowedLanOrigin(it) }
    }

    /**
     * Resolves a manual TV address (or pasted QR URL) into a [PendingDeviceLink] by querying
     * `GET <origin>/api/link/discover` on the LAN. Fails if non-LAN, unreachable, or QR inactive.
     */
    suspend fun resolveTvAddress(
        httpClient: OkHttpClient,
        rawInput: String
    ): Result<PendingDeviceLink> = withContext(Dispatchers.IO) {
        parseQrOrLink(rawInput)?.let { return@withContext Result.success(it) }

        val origin = normalizeManualTvOrigin(rawInput)
            ?: return@withContext Result.failure(IllegalArgumentException("invalid_address"))

        val req = Request.Builder()
            .url("$origin/api/link/discover")
            .get()
            .build()
        try {
            httpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val active = resp.isSuccessful && obj?.get("active")?.jsonPrimitive?.booleanOrNull == true
                if (!active || obj == null) {
                    return@use Result.failure(IllegalStateException("inactive"))
                }
                val pk = obj["pk"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val sessionId = obj["s"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val deviceName = obj["deviceName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    .take(60)
                    .ifBlank { "Quern TV" }
                if (sessionId.length !in 12..64 ||
                    runCatching { DeviceLinkCrypto.decodePublicKey(pk) }.isFailure
                ) {
                    return@use Result.failure(IllegalStateException("invalid_manifest"))
                }
                Result.success(
                    PendingDeviceLink(
                        tvOrigin = origin,
                        tvPublicKeyB64 = pk,
                        sessionId = sessionId,
                        deviceName = deviceName
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(IOException("network", e))
        }
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
