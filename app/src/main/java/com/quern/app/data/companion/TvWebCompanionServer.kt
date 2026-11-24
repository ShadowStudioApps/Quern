package com.quern.app.data.companion

import android.util.Log
import com.quern.app.data.QuernRepository
import com.quern.app.data.local.PinHasher
import com.quern.app.data.local.SettingsStore
import com.quern.app.data.sync.AccountCrypto
import com.quern.app.data.sync.AccountError
import com.quern.app.data.sync.AccountException
import com.quern.app.data.sync.CloudSyncManager
import com.quern.app.data.sync.DeviceLinkCrypto
import com.quern.app.data.sync.QuernAccount
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppThemePreset
import com.quern.app.domain.model.AudioLanguage
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.domain.model.NavigationStyle
import com.quern.app.domain.model.PlayerEngine
import com.quern.app.domain.model.SubtitleLanguage
import com.quern.app.domain.model.TmdbConfig
import com.quern.app.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class DeviceLinkSessionState(
    val sessionId: String = "",
    val publicKeyB64: String = "",
    val code6: String = "",
    val deviceName: String = "",
    val expiresAtMs: Long = 0L,
    val isActive: Boolean = false,
    val failedAttempts: Int = 0,
    val maxFailedAttempts: Int = 5,
    val isLocked: Boolean = false,
    val isCompleted: Boolean = false,
    val isSigningIn: Boolean = false,
    val lastError: String? = null
) {
    fun remainingSeconds(nowMs: Long = System.currentTimeMillis()): Long =
        ((expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs == 0L || nowMs >= expiresAtMs
}

data class CompanionSessionState(
    val shortCode: String,
    val expiresAtMs: Long,
    val isConnected: Boolean = false,
    val isServerRunning: Boolean = false,
    val failedAttempts: Int = 0,
    val maxFailedAttempts: Int = 5,
    val lastClientIp: String? = null,
    val lastActivityAtMs: Long = 0L
) {
    fun remainingSeconds(nowMs: Long = System.currentTimeMillis()): Long =
        ((expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs >= expiresAtMs
}

private sealed interface PairCheckResult {
    data object Valid : PairCheckResult
    data object Missing : PairCheckResult
    data object Expired : PairCheckResult
    data class Invalid(val attempt: Int, val max: Int) : PairCheckResult
    data class IpLockedOut(val retryAfterSec: Long) : PairCheckResult
}

private data class IpThrottleState(
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0L,
    val lastAttemptAtMs: Long = 0L
)

class TvWebCompanionServer(
    private val repository: QuernRepository,
    private val settingsStore: SettingsStore,
    private val cloudSyncManager: CloudSyncManager,
    private val quernAccount: QuernAccount,
    private val onDataChanged: () -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    val port: Int = 8787

    private val rng = SecureRandom()
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val sessionTtlMs = 10 * 60 * 1000L
    private val maxWrongCodeAttempts = 5
    private val ipLockoutMs = 5 * 60 * 1000L
    private val maxTrackedIps = 64
    private val authLock = Any()
    private val ipThrottleMap = mutableMapOf<String, IpThrottleState>()

    // Ephemeral QR device-linking session (active ONLY while the TV account QR is on screen).
    private val linkSessionTtlMs = 5 * 60 * 1000L
    private val linkMaxFailedAttempts = 5
    private val linkLock = Any()
    private val linkJson = Json { ignoreUnknownKeys = true }
    private var linkKeyPair: KeyPair? = null
    private val _deviceLinkState = MutableStateFlow(DeviceLinkSessionState())
    val deviceLinkState: StateFlow<DeviceLinkSessionState> = _deviceLinkState.asStateFlow()

    private val _sessionState = MutableStateFlow(createFreshSession(isRunning = false))
    val sessionState: StateFlow<CompanionSessionState> = _sessionState.asStateFlow()

    private fun createFreshSession(isRunning: Boolean): CompanionSessionState {
        val part1 = (1..3).map { codeAlphabet[rng.nextInt(codeAlphabet.length)] }.joinToString("")
        val part2 = (1..3).map { codeAlphabet[rng.nextInt(codeAlphabet.length)] }.joinToString("")
        return CompanionSessionState(
            shortCode = "$part1-$part2",
            expiresAtMs = System.currentTimeMillis() + sessionTtlMs,
            isConnected = false,
            isServerRunning = isRunning,
            failedAttempts = 0,
            maxFailedAttempts = maxWrongCodeAttempts
        )
    }

    /** True while a paired phone has used the page within the session window. */
    fun hasActivePhone(nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = _sessionState.value
        return s.isConnected && !s.isExpired(nowMs) && nowMs - s.lastActivityAtMs < sessionTtlMs
    }

    fun refreshSession() {
        synchronized(authLock) {
            val running = serverJob?.isActive == true
            _sessionState.value = createFreshSession(isRunning = running)
        }
    }

    fun getLocalUrl(anchor: String = ""): String {
        val ip = findLocalIpv4() ?: "127.0.0.1"
        return if (anchor.isBlank()) "http://$ip:$port" else "http://$ip:$port/#$anchor"
    }

    /** Starts or reuses an active 5-minute ECDH device-linking session while the TV QR is shown. */
    fun startDeviceLinkSession(deviceName: String) {
        synchronized(linkLock) {
            val curr = _deviceLinkState.value
            if (curr.isActive && !curr.isExpired() && !curr.isLocked && !curr.isCompleted && linkKeyPair != null) {
                return
            }
        }
        refreshDeviceLinkSession(deviceName)
    }

    /** Generates a fresh ephemeral EC P-256 key pair, session id, and 6-digit code (5-minute TTL). */
    fun refreshDeviceLinkSession(deviceName: String) {
        val kp = DeviceLinkCrypto.generateEphemeralKeyPair()
        val pubB64 = DeviceLinkCrypto.encodePublicKey(kp.public)
        val sessionId = DeviceLinkCrypto.generateSessionId()
        val code6 = DeviceLinkCrypto.generateCode6()
        val cleanName = deviceName.trim().take(60).ifBlank { "Quern TV" }
        synchronized(linkLock) {
            linkKeyPair = kp
            _deviceLinkState.value = DeviceLinkSessionState(
                sessionId = sessionId,
                publicKeyB64 = pubB64,
                code6 = code6,
                deviceName = cleanName,
                expiresAtMs = System.currentTimeMillis() + linkSessionTtlMs,
                isActive = true,
                failedAttempts = 0,
                maxFailedAttempts = linkMaxFailedAttempts,
                isLocked = false,
                isCompleted = false,
                isSigningIn = false,
                lastError = null
            )
        }
        Log.i(TAG, "Device link session refreshed (5m TTL)")
    }

    /** Immediately closes the device-linking endpoint and wipes the ephemeral key pair from memory. */
    fun stopDeviceLinkSession() {
        synchronized(linkLock) {
            linkKeyPair = null
            _deviceLinkState.value = DeviceLinkSessionState(isActive = false)
        }
    }

    /**
     * Returns the public QR link `http://<tv-ip>:<port>/link#pk=<pub>&s=<session>&n=<name>`.
     * Contains ONLY the ephemeral public key, session id, and TV name — never anything secret.
     */
    fun getDeviceLinkUrl(): String {
        val st = _deviceLinkState.value
        if (!st.isActive || st.publicKeyB64.isBlank() || st.sessionId.isBlank()) return ""
        val ip = findLocalIpv4() ?: "127.0.0.1"
        val encodedName = URLEncoder.encode(st.deviceName, "UTF-8")
        return "http://$ip:$port/link#pk=${st.publicKeyB64}&s=${st.sessionId}&n=$encodedName"
    }

    fun start(scope: CoroutineScope) {
        synchronized(authLock) {
            if (serverJob?.isActive == true) return
            // Always start a fresh 10-minute session when the server transitions from stopped
            // to running, so opening the QR card minutes after launch/close doesn't show a stale timer.
            _sessionState.value = createFreshSession(isRunning = true)
        }
        serverJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = socket
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch(Dispatchers.IO) { handleClient(client) }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        stopDeviceLinkSession()
        synchronized(authLock) {
            // Rotate session code on close so any previously issued session cookie is immediately invalidated
            _sessionState.value = createFreshSession(isRunning = false)
            pruneExpiredIpEntries(System.currentTimeMillis())
        }
    }

    private fun pruneExpiredIpEntries(nowMs: Long) {
        val it = ipThrottleMap.entries.iterator()
        while (it.hasNext()) {
            val st = it.next().value
            val lockoutExpired = st.lockedUntilMs > 0L && nowMs >= st.lockedUntilMs
            val idleStale = st.lockedUntilMs == 0L && (nowMs - st.lastAttemptAtMs) > ipLockoutMs
            if (lockoutExpired || idleStale) {
                it.remove()
            }
        }
        while (ipThrottleMap.size >= maxTrackedIps) {
            val oldestKey = ipThrottleMap.minByOrNull { it.value.lastAttemptAtMs }?.key ?: break
            ipThrottleMap.remove(oldestKey)
        }
    }

    private fun verifyPairingCode(submittedRaw: String?, remoteIp: String?): PairCheckResult = synchronized(authLock) {
        val nowMs = System.currentTimeMillis()
        pruneExpiredIpEntries(nowMs)
        val ipKey = remoteIp?.takeIf { it.isNotBlank() } ?: "unknown"
        val ipState = ipThrottleMap[ipKey] ?: IpThrottleState()

        if (ipState.lockedUntilMs > nowMs) {
            val remainingSec = ((ipState.lockedUntilMs - nowMs + 999L) / 1000L).coerceAtLeast(1L)
            return PairCheckResult.IpLockedOut(remainingSec)
        }

        val current = _sessionState.value
        val cleaned = submittedRaw.orEmpty().trim().uppercase(Locale.US).replace("-", "").replace(" ", "")
        if (cleaned.isEmpty()) return PairCheckResult.Missing
        if (nowMs >= current.expiresAtMs) return PairCheckResult.Expired

        val expected = current.shortCode.uppercase(Locale.US).replace("-", "")
        val matches = MessageDigest.isEqual(
            cleaned.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
        if (matches) {
            ipThrottleMap.remove(ipKey)
            _sessionState.value = current.copy(
                isConnected = true,
                failedAttempts = 0,
                lastClientIp = remoteIp,
                lastActivityAtMs = nowMs,
                // Sliding window: the session stays valid while the phone keeps using it and
                // only expires after sessionTtlMs of inactivity.
                expiresAtMs = maxOf(current.expiresAtMs, nowMs + sessionTtlMs)
            )
            return PairCheckResult.Valid
        }

        val nextFails = ipState.failedAttempts + 1
        if (nextFails >= maxWrongCodeAttempts) {
            // Per-IP lockout ONLY: do not regenerate the shared TV code for legitimate clients
            ipThrottleMap[ipKey] = IpThrottleState(
                failedAttempts = 0,
                lockedUntilMs = nowMs + ipLockoutMs,
                lastAttemptAtMs = nowMs
            )
            return PairCheckResult.IpLockedOut(ipLockoutMs / 1000L)
        } else {
            ipThrottleMap[ipKey] = IpThrottleState(
                failedAttempts = nextFails,
                lockedUntilMs = 0L,
                lastAttemptAtMs = nowMs
            )
            return PairCheckResult.Invalid(nextFails, maxWrongCodeAttempts)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            runCatching { s.soTimeout = 10_000 }
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            val path = rawPath.substringBefore("?")
            val queryString = rawPath.substringAfter("?", "")

            if (path.equals("/favicon.ico", ignoreCase = true)) {
                writeHttpResponse(s, "404 Not Found", "")
                return
            }

            var contentLength = 0
            var headerCode = ""
            var cookieCode = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (line.startsWith("X-Quern-Code:", ignoreCase = true)) {
                    headerCode = line.substringAfter(":").trim()
                } else if (line.startsWith("Cookie:", ignoreCase = true)) {
                    val cookies = line.substringAfter(":").split(";")
                    cookieCode = cookies.firstNotNullOfOrNull { c ->
                        val kv = c.trim().split("=", limit = 2)
                        if (kv.size == 2 && kv[0] == "quern_pair") kv[1].trim() else null
                    }.orEmpty()
                }
            }

            val remoteIp = s.inetAddress?.hostAddress
            val rawBody = if (method.equals("POST", ignoreCase = true) && contentLength > 0 && contentLength <= 2_000_000) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                String(buf, 0, read)
            } else {
                ""
            }

            // Dedicated QR device-linking endpoints (active ONLY while TV QR is on screen; zero secrets exposed).
            when {
                path == "/link" && method.equals("GET", ignoreCase = true) -> {
                    val st = _deviceLinkState.value
                    val active = st.isActive && !st.isExpired() && !st.isLocked && !st.isCompleted
                    val html = buildDeviceLinkLandingHtml(
                        deviceName = st.deviceName.ifBlank { "Quern TV" },
                        isActive = active,
                        errorMsg = null,
                        successMsg = null
                    )
                    writeHttpResponse(s, "200 OK", html)
                    return
                }
                path == "/api/link/info" && method.equals("GET", ignoreCase = true) -> {
                    handleDeviceLinkInfo(s, parseForm(queryString)["s"].orEmpty())
                    return
                }
                path == "/api/link/complete" && method.equals("POST", ignoreCase = true) -> {
                    handleDeviceLinkComplete(s, rawBody)
                    return
                }
                path == "/link-fallback" && method.equals("POST", ignoreCase = true) -> {
                    handleDeviceLinkFallback(s, parseForm(rawBody))
                    return
                }
            }

            val form = if (rawBody.isNotEmpty()) parseForm(rawBody) else emptyMap()

            // Strictly prefer X-Quern-Code header or POST form field (or HttpOnly session cookie on GET); never read ?code= from URLs
            val submittedCode = headerCode.takeIf { it.isNotBlank() }
                ?: form["pair_code"]?.takeIf { it.isNotBlank() }
                ?: if (!method.equals("POST", ignoreCase = true)) cookieCode.takeIf { it.isNotBlank() } else null

            // Enforce pairing code on EVERY endpoint (both GET dashboard and ALL mutating POST endpoints)
            val authResult = verifyPairingCode(submittedCode, remoteIp)
            if (authResult != PairCheckResult.Valid) {
                val (httpStatus, gateMsg) = when (authResult) {
                    PairCheckResult.Missing -> {
                        val st = if (method.equals("POST", ignoreCase = true)) "403 Forbidden" else "200 OK"
                        val msg = if (method.equals("POST", ignoreCase = true)) {
                            "Rejected: Pairing code (X-Quern-Code header or pair_code field) is required."
                        } else ""
                        st to msg
                    }
                    PairCheckResult.Expired -> "403 Forbidden" to
                        "Pairing code expired. Press 'Refresh Code' on your TV and try again."
                    is PairCheckResult.Invalid -> "403 Forbidden" to
                        "Invalid pairing code (attempt ${authResult.attempt}/${authResult.max} from this device)."
                    is PairCheckResult.IpLockedOut -> "429 Too Many Requests" to
                        "Too many wrong attempts from your device. Locked out for ${authResult.retryAfterSec}s."
                    PairCheckResult.Valid -> "200 OK" to ""
                }
                val gateHtml = buildPairingGateHtml(gateMsg, isError = gateMsg.isNotBlank())
                val clearCookie = if (cookieCode.isNotBlank()) "quern_pair=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0" else null
                writeHttpResponse(s, httpStatus, gateHtml, setCookie = clearCookie)
                return
            }

            val currentSession = _sessionState.value
            val activeCode = currentSession.shortCode
            val remainingCookieSec = currentSession.remainingSeconds().coerceIn(1L, sessionTtlMs / 1000L)
            var message = ""
            var activeTab = "overview"
            if (method.equals("POST", ignoreCase = true) && path != "/pair") {
                activeTab = form["tab"].orEmpty().ifBlank { tabForPath(path) }
                message = handlePost(path, form)
                onDataChanged()
            }

            val html = buildDashboardHtml(message, activeTab, activeCode)
            val sessionCookie = "quern_pair=$activeCode; HttpOnly; SameSite=Strict; Path=/; Max-Age=$remainingCookieSec"
            writeHttpResponse(s, "200 OK", html, setCookie = sessionCookie)
        }
    }

    private fun writeHttpResponse(
        socket: Socket,
        status: String,
        htmlBody: String,
        setCookie: String? = null
    ) {
        val bodyBytes = htmlBody.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            if (!setCookie.isNullOrBlank()) {
                append("Set-Cookie: $setCookie\r\n")
            }
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(response.toByteArray(Charsets.UTF_8))
            write(bodyBytes)
            flush()
        }
    }

    private fun writeJsonResponse(socket: Socket, status: String, jsonBody: String) {
        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(response.toByteArray(Charsets.UTF_8))
            write(bodyBytes)
            flush()
        }
    }

    private fun handleDeviceLinkInfo(socket: Socket, submittedSession: String) {
        val (st, matches) = synchronized(linkLock) {
            val curr = _deviceLinkState.value
            val eq = curr.sessionId.isNotBlank() && submittedSession.isNotBlank() && MessageDigest.isEqual(
                curr.sessionId.toByteArray(Charsets.UTF_8),
                submittedSession.toByteArray(Charsets.UTF_8)
            )
            curr to eq
        }
        val now = System.currentTimeMillis()
        if (!matches || !st.isActive) {
            val body = buildJsonObject {
                put("active", false)
                put("locked", st.isLocked)
                put("completed", st.isCompleted)
                put("error", if (st.isCompleted) "already_used" else if (st.isLocked) "locked" else "inactive")
            }.toString()
            writeJsonResponse(socket, "404 Not Found", body)
            return
        }
        val expired = st.isExpired(now)
        val body = buildJsonObject {
            put("active", !expired && !st.isLocked && !st.isCompleted)
            put("deviceName", st.deviceName)
            put("expiresInSec", st.remainingSeconds(now))
            put("locked", st.isLocked)
            put("completed", st.isCompleted)
        }.toString()
        writeJsonResponse(socket, if (expired) "410 Gone" else "200 OK", body)
    }

    private suspend fun handleDeviceLinkComplete(socket: Socket, rawBody: String) {
        if (rawBody.length > 16_384) {
            writeJsonResponse(socket, "413 Payload Too Large", """{"ok":false,"error":"too_large"}""")
            return
        }
        val obj = runCatching { linkJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
        if (obj == null) {
            writeJsonResponse(socket, "400 Bad Request", """{"ok":false,"error":"bad_json"}""")
            return
        }
        val envelope = DeviceLinkCrypto.SealedLinkEnvelope(
            sessionId = obj["s"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            phonePublicKeyB64 = obj["pk"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ivB64 = obj["iv"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cipherTextB64 = obj["ct"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )

        var errorStatus = ""
        var errorJson = ""
        var decrypted: DeviceLinkCrypto.LinkCredentialBundle? = null

        synchronized(linkLock) {
            val now = System.currentTimeMillis()
            val curr = _deviceLinkState.value
            when {
                curr.isCompleted -> {
                    errorStatus = "409 Conflict"
                    errorJson = """{"ok":false,"error":"already_used"}"""
                }
                curr.isLocked || curr.failedAttempts >= curr.maxFailedAttempts -> {
                    errorStatus = "423 Locked"
                    errorJson = """{"ok":false,"error":"locked"}"""
                }
                !curr.isActive || linkKeyPair == null -> {
                    errorStatus = "403 Forbidden"
                    errorJson = """{"ok":false,"error":"inactive"}"""
                }
                curr.isExpired(now) -> {
                    linkKeyPair = null
                    _deviceLinkState.value = curr.copy(isActive = false)
                    errorStatus = "410 Gone"
                    errorJson = """{"ok":false,"error":"expired"}"""
                }
                !MessageDigest.isEqual(
                    curr.sessionId.toByteArray(Charsets.UTF_8),
                    envelope.sessionId.toByteArray(Charsets.UTF_8)
                ) -> {
                    errorStatus = "404 Not Found"
                    errorJson = """{"ok":false,"error":"invalid_session"}"""
                }
                else -> {
                    val kp = linkKeyPair!!
                    val bundle = DeviceLinkCrypto.openOnTv(
                        tvKeyPair = kp,
                        tvPublicKeyB64 = curr.publicKeyB64,
                        sessionId = curr.sessionId,
                        code6 = curr.code6,
                        envelope = envelope
                    )
                    if (bundle == null) {
                        val nextFails = curr.failedAttempts + 1
                        val locked = nextFails >= curr.maxFailedAttempts
                        val attemptsLeft = (curr.maxFailedAttempts - nextFails).coerceAtLeast(0)
                        if (locked) linkKeyPair = null
                        _deviceLinkState.value = curr.copy(
                            failedAttempts = nextFails,
                            isLocked = locked,
                            isActive = !locked
                        )
                        Log.w(TAG, "Device link rejected: wrong code ($nextFails/${curr.maxFailedAttempts})")
                        errorStatus = if (locked) "423 Locked" else "403 Forbidden"
                        errorJson = buildJsonObject {
                            put("ok", false)
                            put("error", "wrong_code")
                            put("attemptsLeft", attemptsLeft)
                            put("locked", locked)
                        }.toString()
                    } else {
                        // Single-use guarantee: consume session and wipe ephemeral private key BEFORE network I/O.
                        linkKeyPair = null
                        _deviceLinkState.value = curr.copy(
                            isCompleted = true,
                            isActive = false,
                            isSigningIn = true,
                            lastError = null
                        )
                        decrypted = bundle
                    }
                }
            }
        }

        val bundle = decrypted
        if (bundle == null) {
            writeJsonResponse(socket, errorStatus, errorJson)
            return
        }

        val signInRes = quernAccount.signInWithDerivedKeys(
            email = bundle.email,
            authSecret = bundle.authSecret,
            encKeyB64 = bundle.encKeyB64
        )
        signInRes.fold(
            onSuccess = {
                synchronized(linkLock) {
                    _deviceLinkState.value = _deviceLinkState.value.copy(isSigningIn = false, lastError = null)
                }
                Log.i(TAG, "Device link completed and TV signed in")
                onDataChanged()
                val okBody = buildJsonObject {
                    put("ok", true)
                    put("email", quernAccount.state.value.email)
                }.toString()
                writeJsonResponse(socket, "200 OK", okBody)
            },
            onFailure = { err ->
                val msg = accountErrorText(err)
                synchronized(linkLock) {
                    _deviceLinkState.value = _deviceLinkState.value.copy(isSigningIn = false, lastError = msg)
                }
                Log.w(TAG, "Device link auth grant failed")
                writeJsonResponse(socket, "502 Bad Gateway", """{"ok":false,"error":"auth_failed"}""")
            }
        )
    }

    private suspend fun handleDeviceLinkFallback(socket: Socket, form: Map<String, String>) {
        val submittedSession = form["s"].orEmpty().trim()
        val submittedCode = DeviceLinkCrypto.normalizeCode6(form["code6"].orEmpty())
        val email = form["email"].orEmpty().trim()
        val password = form["password"].orEmpty()

        var tvName = "Quern TV"
        var errMsg: String? = null
        var allowed = false

        synchronized(linkLock) {
            val now = System.currentTimeMillis()
            val curr = _deviceLinkState.value
            tvName = curr.deviceName.ifBlank { "Quern TV" }
            when {
                curr.isCompleted -> errMsg = "This QR code has already been used."
                curr.isLocked || curr.failedAttempts >= curr.maxFailedAttempts ->
                    errMsg = "Too many wrong codes. Refresh the QR code on your TV."
                !curr.isActive || linkKeyPair == null ->
                    errMsg = "Sign-in QR is no longer active on this TV."
                curr.isExpired(now) -> {
                    linkKeyPair = null
                    _deviceLinkState.value = curr.copy(isActive = false)
                    errMsg = "This QR code expired. Refresh it on your TV and scan again."
                }
                !MessageDigest.isEqual(
                    curr.sessionId.toByteArray(Charsets.UTF_8),
                    submittedSession.toByteArray(Charsets.UTF_8)
                ) -> errMsg = "Session mismatch. Scan the latest QR code on your TV."
                submittedCode.length != 6 || !MessageDigest.isEqual(
                    curr.code6.toByteArray(Charsets.UTF_8),
                    submittedCode.toByteArray(Charsets.UTF_8)
                ) -> {
                    val nextFails = curr.failedAttempts + 1
                    val locked = nextFails >= curr.maxFailedAttempts
                    if (locked) linkKeyPair = null
                    _deviceLinkState.value = curr.copy(
                        failedAttempts = nextFails,
                        isLocked = locked,
                        isActive = !locked
                    )
                    errMsg = if (locked) {
                        "Too many wrong codes. Refresh the QR code on your TV."
                    } else {
                        "Wrong 6-digit code (${curr.maxFailedAttempts - nextFails} attempts left)."
                    }
                }
                else -> {
                    linkKeyPair = null
                    _deviceLinkState.value = curr.copy(
                        isCompleted = true,
                        isActive = false,
                        isSigningIn = true,
                        lastError = null
                    )
                    allowed = true
                }
            }
        }

        if (!allowed) {
            val html = buildDeviceLinkLandingHtml(
                deviceName = tvName,
                isActive = _deviceLinkState.value.isActive,
                errorMsg = errMsg,
                successMsg = null,
                initialSessionId = submittedSession,
                expandFallback = true
            )
            writeHttpResponse(socket, "403 Forbidden", html)
            return
        }

        val res = quernAccount.signIn(email, password)
        res.fold(
            onSuccess = {
                synchronized(linkLock) {
                    _deviceLinkState.value = _deviceLinkState.value.copy(isSigningIn = false, lastError = null)
                }
                onDataChanged()
                val html = buildDeviceLinkLandingHtml(
                    deviceName = tvName,
                    isActive = false,
                    errorMsg = null,
                    successMsg = "Signed in as ${quernAccount.state.value.email}. Your TV is now syncing."
                )
                writeHttpResponse(socket, "200 OK", html)
            },
            onFailure = { err ->
                val msg = accountErrorText(err)
                synchronized(linkLock) {
                    _deviceLinkState.value = _deviceLinkState.value.copy(isSigningIn = false, lastError = msg)
                }
                val html = buildDeviceLinkLandingHtml(
                    deviceName = tvName,
                    isActive = false,
                    errorMsg = "$msg Refresh the QR code on your TV to try again.",
                    successMsg = null
                )
                writeHttpResponse(socket, "400 Bad Request", html)
            }
        )
    }

    private fun buildDeviceLinkLandingHtml(
        deviceName: String,
        isActive: Boolean,
        errorMsg: String?,
        successMsg: String?,
        initialSessionId: String = "",
        expandFallback: Boolean = false
    ): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>Sign in to ${escapeHtml(deviceName)} — Quern</title>
        <style>
          body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
            background:#000; color:#F8FAFC; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; padding:20px; }
          .box { width:100%; max-width:430px; background:#0A121B; border:1px solid #28425C; border-radius:18px; padding:26px; }
          h1 { margin:0 0 6px; font-size:21px; }
          .sub { color:#94A3B8; font-size:13.5px; margin-bottom:18px; line-height:1.5; }
          .err { background:rgba(239,68,68,.15); border:1px solid rgba(239,68,68,.45); color:#FCA5A5;
            padding:10px 12px; border-radius:10px; font-size:13px; margin-bottom:14px; }
          .ok { background:rgba(16,185,129,.16); border:1px solid rgba(16,185,129,.45); color:#6EE7B7;
            padding:12px 14px; border-radius:10px; font-size:13.5px; margin-bottom:14px; }
          .btn { display:block; width:100%; box-sizing:border-box; text-align:center; text-decoration:none;
            padding:13px 16px; border-radius:12px; border:none; background:#345372; color:#FFF;
            font-size:15px; font-weight:700; cursor:pointer; }
          .btn-sec { background:#132131; border:1px solid #28425C; margin-top:10px; font-size:13.5px; font-weight:600; }
          .divider { text-align:center; color:#64748B; font-size:12px; margin:18px 0 14px; }
          label { display:block; color:#94A3B8; font-size:12px; margin:10px 0 4px; }
          input { width:100%; box-sizing:border-box; padding:11px 12px; border-radius:10px; border:1px solid #28425C;
            background:#132131; color:#FFF; font-size:15px; }
          input.code { font-family:monospace; letter-spacing:4px; text-align:center; font-size:18px; }
          .note { color:#64748B; font-size:11.5px; margin-top:14px; line-height:1.45; }
          .hidden { display:none; }
        </style>
        </head>
        <body>
          <div class="box">
            <h1 id="tvTitle">Sign in to ${escapeHtml(deviceName)}</h1>
            ${if (!successMsg.isNullOrBlank()) """<div class="ok">${escapeHtml(successMsg)}</div>""" else ""}
            ${if (!errorMsg.isNullOrBlank()) """<div class="err">${escapeHtml(errorMsg)}</div>""" else ""}
            ${if (isActive && successMsg.isNullOrBlank()) """
            <div class="sub" id="introText">If Quern is installed and signed in on this phone, open it to approve this TV with end-to-end encryption — no password typing needed.</div>
            <a id="openAppBtn" class="btn" href="#">Open in Quern</a>
            <div class="divider">Don't have the Quern app on this phone?</div>
            <button type="button" id="toggleFallbackBtn" class="btn btn-sec" onclick="document.getElementById('fallbackForm').classList.toggle('hidden')">Sign in with email &amp; password</button>
            <form id="fallbackForm" class="${if (expandFallback) "" else "hidden"}" method="POST" action="/link-fallback">
              <input type="hidden" name="s" id="sessionField" value="${escapeHtml(initialSessionId)}"/>
              <label>6-digit code shown on TV</label>
              <input class="code" name="code6" inputmode="numeric" maxlength="7" placeholder="000 000" autocomplete="off" required/>
              <label>Quern account email</label>
              <input name="email" type="email" autocomplete="email" placeholder="you@example.com" required/>
              <label>Password</label>
              <input name="password" type="password" autocomplete="current-password" minlength="${AccountCrypto.MIN_PASSWORD_LENGTH}" required/>
              <button type="submit" class="btn" style="margin-top:14px">Sign In TV</button>
            </form>
            <div class="note">Local Wi-Fi connection. When using the Quern app, your credentials are encrypted end-to-end with the TV's ephemeral public key and the 6-digit code.</div>
            """ else if (successMsg.isNullOrBlank()) """
            <div class="sub">The sign-in QR code is no longer active on this TV. Open Settings &rarr; Accounts &amp; Sync on your TV to show a new QR code.</div>
            <a class="btn btn-sec" href="/#account">Open TV Companion</a>
            """ else ""}
          </div>
          <script>
            (function () {
              var hash = (location.hash || '').replace(/^#/, '');
              var params = new URLSearchParams(hash);
              var pk = params.get('pk') || '';
              var s = params.get('s') || '';
              var n = params.get('n') || '';
              if (n) {
                var titleEl = document.getElementById('tvTitle');
                if (titleEl) titleEl.textContent = 'Sign in to ' + n;
              }
              var sf = document.getElementById('sessionField');
              if (sf && s) sf.value = s;
              var isFallback = location.search.indexOf('fallback=1') !== -1;
              if (isFallback) {
                var ff0 = document.getElementById('fallbackForm');
                if (ff0) ff0.classList.remove('hidden');
                var intro = document.getElementById('introText');
                if (intro) intro.textContent = 'Quern app was not found on this phone. Enter the 6-digit code shown on your TV and your Quern account below.';
              }
              var btn = document.getElementById('openAppBtn');
              if (btn && pk && s) {
                var qs = 'host=' + encodeURIComponent(location.origin)
                  + '&pk=' + encodeURIComponent(pk)
                  + '&s=' + encodeURIComponent(s)
                  + '&n=' + encodeURIComponent(n || '${escapeHtml(deviceName)}');
                var fallbackUrl = location.origin + '/link?fallback=1' + location.hash;
                var isAndroid = /Android/i.test(navigator.userAgent || '');
                if (isAndroid) {
                  btn.href = 'intent://link?' + qs
                    + '#Intent;scheme=quern;package=com.quern.app;'
                    + 'S.browser_fallback_url=' + encodeURIComponent(fallbackUrl) + ';end';
                } else {
                  btn.href = 'quern://link?' + qs;
                }
              } else if (btn) {
                btn.classList.add('hidden');
                var ff = document.getElementById('fallbackForm');
                if (ff) ff.classList.remove('hidden');
              }
            })();
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun buildPairingGateHtml(errorMsg: String, isError: Boolean): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>Quern Companion — Pairing Required</title>
        <style>
          body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
            background:#000; color:#F8FAFC; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; padding:20px; }
          .box { width:100%; max-width:420px; background:#0A121B; border:1px solid #28425C; border-radius:18px; padding:28px; }
          h1 { margin:0 0 6px; font-size:22px; }
          .sub { color:#94A3B8; font-size:13px; margin-bottom:18px; line-height:1.5; }
          .err { background:rgba(239,68,68,.15); border:1px solid rgba(239,68,68,.45); color:#FCA5A5;
            padding:10px 12px; border-radius:10px; font-size:12.5px; margin-bottom:14px; }
          .warn { background:rgba(251,191,36,.12); border:1px solid rgba(251,191,36,.35); color:#FDE68A;
            padding:9px 12px; border-radius:10px; font-size:11.5px; margin-top:16px; line-height:1.45; }
          input { width:100%; box-sizing:border-box; padding:12px 14px; border-radius:10px; border:1px solid #28425C;
            background:#132131; color:#FFF; font-size:18px; font-family:monospace; letter-spacing:3px; text-align:center; text-transform:uppercase; }
          button { width:100%; margin-top:12px; padding:12px; border-radius:10px; border:none; background:#345372;
            color:#FFF; font-size:14px; font-weight:700; cursor:pointer; }
        </style>
        </head>
        <body>
          <div class="box">
            <h1>Quern TV Companion</h1>
            <div class="sub">Enter the 6-character pairing code shown on your TV to unlock this session.</div>
            ${if (isError && errorMsg.isNotBlank()) """<div class="err">${escapeHtml(errorMsg)}</div>""" else ""}
            <form method="POST" action="/pair">
              <input name="pair_code" placeholder="XXX-XXX" maxlength="8" autocomplete="off" required/>
              <button type="submit">Pair &amp; Continue</button>
            </form>
            <div class="warn">Local LAN plain HTTP connection (not HTTPS). Use only on your trusted home Wi-Fi network.</div>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun tabForPath(path: String): String = when (path) {
        "/addon", "/addon-remove" -> "addons"
        "/debrid", "/tmdb" -> "debrid"
        "/server", "/server-remove" -> "servers"
        "/profile-switch", "/profile-create" -> "profiles"
        "/tv-settings" -> "settings"
        "/stremio-sync", "/nuvio-import" -> "import"
        "/sync-account", "/sync-import" -> "account"
        else -> "overview"
    }

    private suspend fun handlePost(path: String, form: Map<String, String>): String = when (path) {
        "/addon" -> {
            val url = form["url"].orEmpty().trim()
            if (url.isBlank()) "Please enter a manifest.json URL."
            else repository.addonRepository.installAddon(url).fold(
                onSuccess = { "Installed addon: ${it.manifest.name}" },
                onFailure = { "Error: ${it.message}" }
            )
        }
        "/addon-remove" -> {
            val url = form["url"].orEmpty().trim()
            if (url.isNotBlank()) repository.addonRepository.removeAddon(url)
            "Addon removed from TV."
        }
        "/debrid" -> {
            val service = runCatching { DebridServiceType.valueOf(form["service"].orEmpty()) }.getOrNull()
                ?: return "Invalid service"
            val key = form["apikey"].orEmpty().trim()
            val status = repository.debridManager.saveAndValidate(service, key)
            if (status.isConnected) "${service.displayName} connected (${status.username ?: status.plan ?: "Active"})"
            else status.error ?: "${service.displayName} key updated"
        }
        "/server" -> {
            val type = runCatching { MediaServerType.valueOf(form["type"].orEmpty()) }.getOrDefault(MediaServerType.JELLYFIN)
            val res = repository.serverManager.testAndSaveServer(
                MediaServerConfig(
                    id = "",
                    name = form["name"].orEmpty().ifBlank { type.displayName },
                    type = type,
                    baseUrl = form["url"].orEmpty().trim(),
                    apiKeyOrToken = form["token"].orEmpty().trim(),
                    username = form["username"].orEmpty().trim(),
                    password = form["password"].orEmpty()
                )
            )
            res.message
        }
        "/server-remove" -> {
            val id = form["id"].orEmpty().trim()
            if (id.isNotBlank()) repository.serverManager.deleteServer(id)
            "Media server removed."
        }
        "/tmdb" -> {
            val key = form["apikey"].orEmpty().trim()
            val lang = form["lang"].orEmpty().ifBlank { "en-US" }
            repository.tmdbClient.saveConfig(TmdbConfig(key, lang))
            "TMDB metadata saved ($lang)."
        }
        "/profile-switch" -> {
            val id = form["id"].orEmpty()
            if (id.isNotBlank()) {
                settingsStore.update { it.copy(activeProfileId = id) }
            }
            "Switched active TV profile."
        }
        "/profile-create" -> {
            val name = form["name"].orEmpty().trim()
            val rawPin = form["pin"].orEmpty().filter { it.isDigit() }.take(4).takeIf { it.length == 4 }
            val hashedPin = rawPin?.let { PinHasher.hashPinAsync(it) }
            val colorHex = form["color"].orEmpty().toLongOrNull(16)?.or(0xFF000000L) ?: 0xFF345372L
            if (name.isBlank()) "Profile name is required."
            else {
                val p = UserProfile("prof_${UUID.randomUUID().toString().take(8)}", name, colorHex, hashedPin)
                settingsStore.update { it.copy(profiles = it.profiles + p, activeProfileId = p.id) }
                "Profile '$name' created and activated on TV."
            }
        }
        "/tv-settings" -> {
            settingsStore.update { curr ->
                curr.copy(
                    language = form["language"]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: curr.language,
                    theme = form["theme"]?.let { runCatching { AppThemePreset.valueOf(it) }.getOrNull() } ?: curr.theme,
                    navigationStyle = form["nav_style"]?.let { runCatching { NavigationStyle.valueOf(it) }.getOrNull() } ?: curr.navigationStyle,
                    playerEngine = form["player_engine"]?.let { runCatching { PlayerEngine.valueOf(it) }.getOrNull() } ?: curr.playerEngine,
                    subtitleLanguage = form["sub_lang"]?.let { runCatching { SubtitleLanguage.valueOf(it) }.getOrNull() } ?: curr.subtitleLanguage,
                    audioLanguage = form["audio_lang"]?.let { runCatching { AudioLanguage.valueOf(it) }.getOrNull() } ?: curr.audioLanguage,
                    amoledPureBlack = form["amoled"] == "on"
                )
            }
            "TV settings updated live."
        }
        "/sync-account" -> {
            // Quern account on Shadow Studio's server. The password is typed on the phone and used
            // by the TV only to derive keys; it is never stored or forwarded as-is.
            val action = form["action"].orEmpty()
            val email = form["email"].orEmpty()
            val password = form["password"].orEmpty()
            when (action) {
                "signin" -> quernAccount.signIn(email, password).fold(
                    { "Signed in as ${quernAccount.state.value.email}. This TV is now linked to your Quern account." },
                    { accountErrorText(it) }
                )
                "signup" -> quernAccount.signUp(email, password).fold(
                    {
                        if (quernAccount.state.value.isSignedIn) "Account created and signed in."
                        else "Account created. Open the confirmation link we emailed you, then sign in here."
                    },
                    { accountErrorText(it) }
                )
                "resend" -> quernAccount.resendConfirmation(email).fold(
                    { "Confirmation email sent again. Check your inbox and spam folder." },
                    { accountErrorText(it) }
                )
                "signout" -> {
                    quernAccount.signOut()
                    "Signed out on this TV. Your data on the TV stays."
                }
                else -> "Account updated."
            }
        }
        "/sync-import" -> {
            val code = form["code"].orEmpty()
            cloudSyncManager.importSyncCode(code).fold({ it }, { "Error: ${it.message}" })
        }
        "/stremio-sync" -> {
            val email = form["email"].orEmpty()
            val password = form["password"].orEmpty()
            val authKey = form["auth_key"].orEmpty()
            cloudSyncManager.syncFromStremio(email, password, authKey).fold({ it }, { "Error: ${it.message}" })
        }
        "/nuvio-import" -> {
            val data = form["data"].orEmpty()
            cloudSyncManager.importFromNuvio(data).fold({ it }, { "Error: ${it.message}" })
        }
        else -> "Saved."
    }

    private fun accountErrorText(error: Throwable): String = when ((error as? AccountException)?.reason) {
        AccountError.INVALID_EMAIL -> "Error: that email address doesn't look right."
        AccountError.WEAK_PASSWORD -> "Error: the password needs at least ${AccountCrypto.MIN_PASSWORD_LENGTH} characters."
        AccountError.WRONG_CREDENTIALS -> "Error: wrong email or password."
        AccountError.EMAIL_NOT_CONFIRMED -> "Error: confirm your email first (open the link we sent you), then sign in."
        AccountError.RATE_LIMITED -> "Error: too many attempts. Wait a few minutes and try again."
        AccountError.NETWORK -> "Error: the TV can't reach the Quern server. Check its internet connection."
        else -> "Error: ${error.message ?: "something went wrong"}"
    }

    private fun parseForm(raw: String): Map<String, String> =
        raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()

    private suspend fun buildDashboardHtml(statusMsg: String, initialTab: String, activeCode: String): String {
        val pairField = """<input type="hidden" name="pair_code" value="${escapeHtml(activeCode)}"/>"""
        val settings = settingsStore.settings.value
        val installedAddons = runCatching { repository.addonRepository.getEnabledAddons() }.getOrDefault(emptyList())
        val debridStatuses = repository.debridManager.statuses.value
        val connectedDebrid = debridStatuses.values.count { it.isConnected }
        val servers = runCatching { repository.serverManager.servers.firstOrNull() }.getOrNull().orEmpty()
        val favorites = runCatching { repository.favorites.firstOrNull() }.getOrNull().orEmpty()
        val progress = runCatching { repository.continueWatching.firstOrNull() }.getOrNull().orEmpty()
        val tmdb = repository.tmdbClient.getConfig()
        val syncCode = runCatching { cloudSyncManager.exportSyncCode() }.getOrDefault("")
        val account = quernAccount.state.value
        val nowFormatted = SimpleDateFormat("MM/dd/yyyy, h:mm:ss a", Locale.US).format(Date())

        val activeProfile = settings.activeProfile
        val activeAvatarColor = String.format(Locale.US, "#%06X", activeProfile.avatarColorHex and 0xFFFFFF)

        val profilesOverviewHtml = settings.profiles.mapIndexed { idx, p ->
            val hex = String.format(Locale.US, "#%06X", p.avatarColorHex and 0xFFFFFF)
            val isAct = p.id == settings.activeProfileId
            val lockIcon = if (p.isLocked) " 🔒" else ""
            """<div class="profile-card">
                <div class="avatar" style="background:$hex">${escapeHtml(p.name.take(2).uppercase())}</div>
                <div>
                  <div class="p-name">${escapeHtml(p.name)}$lockIcon</div>
                  <div class="p-sub">${if (isAct) "Primary — Active" else "Profile ${idx + 1}"}</div>
                </div>
              </div>"""
        }.joinToString("")

        val profilesManageHtml = settings.profiles.joinToString("") { p ->
            val hex = String.format(Locale.US, "#%06X", p.avatarColorHex and 0xFFFFFF)
            val isAct = p.id == settings.activeProfileId
            """<div class="list-row">
                <div style="display:flex;align-items:center;gap:12px">
                  <div class="avatar" style="background:$hex">${escapeHtml(p.name.take(2).uppercase())}</div>
                  <div>
                    <div class="p-name">${escapeHtml(p.name)} ${if (p.isLocked) "<span class='badge'>PIN Locked</span>" else ""}</div>
                    <div class="p-sub">${if (isAct) "Currently active on TV" else "Click switch to activate"}</div>
                  </div>
                </div>
                <form method="POST" action="/profile-switch" style="margin:0">
                  $pairField
                  <input type="hidden" name="tab" value="profiles"/>
                  <input type="hidden" name="id" value="${escapeHtml(p.id)}"/>
                  <button class="btn-sm ${if (isAct) "btn-muted" else ""}" ${if (isAct) "disabled" else ""}>${if (isAct) "Active" else "Switch"}</button>
                </form>
              </div>"""
        }

        val addonListHtml = if (installedAddons.isEmpty()) {
            "<p class='muted'>No addons installed yet. Paste any Stremio-compatible manifest.json URL above.</p>"
        } else {
            installedAddons.joinToString("") { a ->
                """<div class="list-row">
                    <div style="min-width:0">
                      <div class="p-name">${escapeHtml(a.manifest.name)} <span class="badge">v${escapeHtml(a.manifest.version)}</span></div>
                      <div class="p-sub mono">${escapeHtml(a.manifestUrl)}</div>
                    </div>
                    <form method="POST" action="/addon-remove" style="margin:0">
                      $pairField
                      <input type="hidden" name="tab" value="addons"/>
                      <input type="hidden" name="url" value="${escapeHtml(a.manifestUrl)}"/>
                      <button class="btn-sm btn-danger">Remove</button>
                    </form>
                  </div>"""
            }
        }

        val debridServices = listOf(
            Triple(DebridServiceType.TORBOX, "https://torbox.app/settings", "Get your TorBox API key"),
            Triple(DebridServiceType.REAL_DEBRID, "https://real-debrid.com/apitoken", "Get your Real-Debrid API key"),
            Triple(DebridServiceType.ALL_DEBRID, "https://alldebrid.com/apikeys/", "Get your AllDebrid API key"),
            Triple(DebridServiceType.PREMIUMIZE, "https://www.premiumize.me/account", "Get your Premiumize API key")
        )
        val debridCardsHtml = debridServices.joinToString("") { (svc, apiUrl, helperText) ->
            val st = debridStatuses[svc]
            val badgeHtml = if (st?.isConnected == true) {
                "<span class='badge badge-green'>Connected • ${escapeHtml(st.username ?: st.plan ?: "Active")}</span>"
            } else {
                "<span class='badge'>Not Connected</span>"
            }
            """<div class="card">
                <div class="card-head">
                  <h3>${escapeHtml(svc.displayName)}</h3>
                  $badgeHtml
                </div>
                <form method="POST" action="/debrid" class="inline-form">
                  $pairField
                  <input type="hidden" name="tab" value="debrid"/>
                  <input type="hidden" name="service" value="${svc.name}"/>
                  <input name="apikey" type="password" placeholder="Paste ${escapeHtml(svc.displayName)} API Key..." required/>
                  <button type="submit" class="btn-primary">Save &amp; Verify</button>
                </form>
                <div class="helper-link">
                  $helperText <a href="$apiUrl" target="_blank" rel="noopener noreferrer">Here ↗</a>
                </div>
              </div>"""
        }

        val serversListHtml = if (servers.isEmpty()) {
            "<p class='muted'>No personal media servers connected yet.</p>"
        } else {
            servers.joinToString("") { srv ->
                """<div class="list-row">
                    <div>
                      <div class="p-name">${escapeHtml(srv.name)} <span class="badge">${escapeHtml(srv.type.displayName)}</span></div>
                      <div class="p-sub mono">${escapeHtml(srv.baseUrl)}</div>
                    </div>
                    <form method="POST" action="/server-remove" style="margin:0">
                      $pairField
                      <input type="hidden" name="tab" value="servers"/>
                      <input type="hidden" name="id" value="${escapeHtml(srv.id)}"/>
                      <button class="btn-sm btn-danger">Remove</button>
                    </form>
                  </div>"""
            }
        }

        fun <T : Enum<T>> optionsFor(entries: List<T>, selected: T, label: (T) -> String): String =
            entries.joinToString("") { item ->
                val sel = if (item == selected) "selected" else ""
                "<option value='${item.name}' $sel>${escapeHtml(label(item))}</option>"
            }

        return """
        <!doctype html>
        <html lang="en">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>Quern — Dashboard</title>
        <style>
          :root {
            --bg: #0B0B0D;
            --sidebar: #0E0E11;
            --card: #141418;
            --card-hover: #1A1A20;
            --border: #23232A;
            --text: #F3F4F6;
            --muted: #8B8E98;
            --accent: #345372;
            --accent-hi: #70A4D6;
            --green: #10B981;
            --danger: #EF4444;
          }
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background: var(--bg);
            color: var(--text);
            font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, sans-serif;
            display: flex;
            min-height: 100vh;
          }
          .sidebar {
            width: 248px;
            flex-shrink: 0;
            background: var(--sidebar);
            border-right: 1px solid var(--border);
            padding: 20px 14px;
            display: flex;
            flex-direction: column;
            gap: 20px;
          }
          .brand {
            padding: 4px 10px 12px;
            border-bottom: 1px solid var(--border);
          }
          .brand-title { font-size: 18px; font-weight: 900; letter-spacing: 3px; color: #FFF; }
          .brand-sub { font-size: 11px; color: var(--muted); margin-top: 2px; }
          .nav-section { display: flex; flex-direction: column; gap: 3px; }
          .nav-label {
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 1px;
            color: #6B6E78;
            padding: 6px 12px;
            text-transform: uppercase;
          }
          .nav-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 10px 12px;
            border-radius: 10px;
            color: #B4B7C2;
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
            border: none;
            background: transparent;
            text-align: left;
            width: 100%;
            transition: background .15s, color .15s;
          }
          .nav-item:hover { background: #18181D; color: #FFF; }
          .nav-item.active { background: #1F1F26; color: #FFF; font-weight: 700; }
          .nav-count { font-size: 12px; color: #6E717C; font-weight: 600; }
          .main {
            flex: 1;
            padding: 32px 38px 64px;
            max-width: 1080px;
            width: 100%;
          }
          h1 { margin: 0 0 6px; font-size: 26px; font-weight: 800; letter-spacing: -.4px; }
          .page-sub { color: var(--muted); font-size: 14px; margin-bottom: 24px; }
          .section-head {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 1px;
            color: #6E717C;
            text-transform: uppercase;
            margin: 24px 0 12px;
          }
          .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
          .grid-6 { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
          .grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
          .stat-card {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 16px 18px;
          }
          .stat-label { font-size: 10px; font-weight: 700; letter-spacing: 1px; color: #6E717C; text-transform: uppercase; margin-bottom: 8px; }
          .stat-value { font-size: 15px; font-weight: 700; color: #FFF; display: flex; align-items: center; gap: 8px; }
          .tile {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 16px;
            cursor: pointer;
            transition: border-color .15s, background .15s;
          }
          .tile:hover { border-color: var(--accent-hi); background: var(--card-hover); }
          .tile-num { font-size: 24px; font-weight: 800; color: #FFF; }
          .tile-lbl { font-size: 12px; color: var(--muted); margin-top: 6px; }
          .profiles-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; }
          .profile-card {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 14px 16px;
            display: flex;
            align-items: center;
            gap: 12px;
          }
          .avatar {
            width: 36px; height: 36px; border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
            font-weight: 800; font-size: 13px; color: #FFF; flex-shrink: 0;
          }
          .avatar-sm { width: 22px; height: 22px; font-size: 10px; }
          .p-name { font-size: 14px; font-weight: 700; color: #FFF; }
          .p-sub { font-size: 12px; color: var(--muted); margin-top: 2px; }
          .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; word-break: break-all; }
          .card {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 14px;
          }
          .card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 10px; }
          h3 { margin: 0; font-size: 16px; font-weight: 700; color: #FFF; }
          input, select, textarea {
            width: 100%;
            padding: 11px 13px;
            border-radius: 10px;
            border: 1px solid var(--border);
            background: #0B0B0D;
            color: #FFF;
            font-size: 13.5px;
            outline: none;
            margin-bottom: 10px;
          }
          input:focus, select:focus, textarea:focus { border-color: var(--accent-hi); }
          .inline-form { display: flex; gap: 10px; align-items: center; }
          .inline-form input { margin-bottom: 0; flex: 1; }
          .btn-primary {
            padding: 11px 18px;
            border-radius: 10px;
            border: none;
            background: #FFF;
            color: #0B0B0D;
            font-weight: 700;
            font-size: 13px;
            cursor: pointer;
            white-space: nowrap;
          }
          .btn-primary:hover { background: #E5E7EB; }
          .btn-accent { background: var(--accent); color: #FFF; }
          .btn-accent:hover { background: #3E6388; }
          .btn-sm {
            padding: 7px 13px;
            border-radius: 8px;
            border: 1px solid var(--border);
            background: #1C1C22;
            color: #FFF;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
          }
          .btn-danger { border-color: rgba(239,68,68,.4); color: #FCA5A5; background: rgba(127,29,29,.25); }
          .btn-muted { opacity: .55; cursor: default; }
          .helper-link {
            margin-top: 10px;
            font-size: 12.5px;
            color: var(--muted);
          }
          .helper-link a {
            color: var(--accent-hi);
            font-weight: 700;
            text-decoration: none;
            border-bottom: 1px dashed var(--accent-hi);
            padding-bottom: 1px;
          }
          .helper-link a:hover { color: #FFF; border-color: #FFF; }
          .badge {
            display: inline-block;
            font-size: 11px;
            font-weight: 600;
            padding: 3px 9px;
            border-radius: 999px;
            background: #1E1E26;
            color: var(--muted);
            border: 1px solid var(--border);
          }
          .badge-green { background: rgba(16,185,129,.14); color: #6EE7B7; border-color: rgba(16,185,129,.35); }
          .list-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            padding: 12px 0;
            border-top: 1px solid var(--border);
          }
          .list-row:first-of-type { border-top: none; padding-top: 4px; }
          .toast {
            background: rgba(16,185,129,.14);
            border: 1px solid rgba(16,185,129,.45);
            color: #A7F3D0;
            padding: 12px 16px;
            border-radius: 12px;
            margin-bottom: 20px;
            font-size: 13.5px;
            font-weight: 600;
          }
          .muted { color: var(--muted); font-size: 13px; }
          .link-btn { color: var(--muted); font-size: 12px; cursor: pointer; text-transform: none; letter-spacing: 0; font-weight: 600; }
          .link-btn:hover { color: #FFF; }
          .panel { display: none; }
          .panel.active { display: block; }
          .field-label { display: block; font-size: 12px; color: var(--muted); margin-bottom: 5px; font-weight: 600; }
          .check-row { display: flex; align-items: center; gap: 10px; margin: 10px 0 14px; font-size: 13.5px; }
          .check-row input { width: auto; margin: 0; }
          @media (max-width: 860px) {
            body { flex-direction: column; }
            .sidebar { width: 100%; border-right: none; border-bottom: 1px solid var(--border); padding: 14px; gap: 10px; }
            .nav-section { flex-direction: row; flex-wrap: wrap; gap: 6px; }
            .nav-label { display: none; }
            .nav-item { width: auto; padding: 8px 12px; font-size: 13px; gap: 8px; }
            .main { padding: 20px 16px 56px; }
            .grid-3, .grid-2 { grid-template-columns: 1fr; }
            .grid-6 { grid-template-columns: repeat(2, 1fr); }
            .inline-form { flex-direction: column; align-items: stretch; }
          }
        </style>
        </head>
        <body>
          <aside class="sidebar">
            <div class="brand">
              <div class="brand-title">QUERN</div>
              <div class="brand-sub">TV Companion • By SHADOW STUDIO</div>
              <div style="margin-top:8px"><span class="badge badge-green">Code: ${escapeHtml(activeCode)}</span></div>
              <div class="brand-sub" style="margin-top:6px;color:#FBBF24">Plain HTTP (LAN only)</div>
            </div>

            <div class="nav-section">
              <div class="nav-label">General</div>
              <button class="nav-item" data-tab="overview" onclick="openTab('overview')"><span>Overview</span></button>
              <button class="nav-item" data-tab="profiles" onclick="openTab('profiles')"><span>Profiles</span><span class="nav-count">${settings.profiles.size}</span></button>
            </div>

            <div class="nav-section">
              <div class="nav-label">Sync Data</div>
              <button class="nav-item" data-tab="import" onclick="openTab('import')"><span>Stremio / Backup Sync</span></button>
              <button class="nav-item" data-tab="debrid" onclick="openTab('debrid')"><span>Debrid &amp; API Keys</span><span class="nav-count">$connectedDebrid</span></button>
              <button class="nav-item" data-tab="addons" onclick="openTab('addons')"><span>Addons &amp; Plugins</span><span class="nav-count">${installedAddons.size}</span></button>
              <button class="nav-item" data-tab="servers" onclick="openTab('servers')"><span>Media Servers</span><span class="nav-count">${servers.size}</span></button>
            </div>

            <div class="nav-section">
              <div class="nav-label">System</div>
              <button class="nav-item" data-tab="settings" onclick="openTab('settings')"><span>TV Settings</span></button>
              <button class="nav-item" data-tab="account" onclick="openTab('account')"><span>Account &amp; Sync</span></button>
            </div>
          </aside>

          <main class="main">
            ${if (statusMsg.isNotBlank()) """<div class="toast">${escapeHtml(statusMsg)}</div>""" else ""}

            <!-- OVERVIEW -->
            <section id="panel-overview" class="panel">
              <h1>Overview</h1>
              <div class="page-sub">Manage synced data across web, mobile, and TV.</div>

              <div class="grid-3">
                <div class="stat-card">
                  <div class="stat-label">Account</div>
                  <div class="stat-value">${if (account.isSignedIn) escapeHtml(account.email) else "Local TV Device"}</div>
                </div>
                <div class="stat-card">
                  <div class="stat-label">Active Profile</div>
                  <div class="stat-value">
                    <span class="avatar avatar-sm" style="background:$activeAvatarColor">${escapeHtml(activeProfile.name.take(2).uppercase())}</span>
                    ${escapeHtml(activeProfile.name)}
                  </div>
                </div>
                <div class="stat-card">
                  <div class="stat-label">Last Sync</div>
                  <div class="stat-value">${escapeHtml(cloudSyncManager.lastImportAt ?: nowFormatted)}</div>
                </div>
              </div>

              <div class="section-head"><span>Sync Data</span></div>
              <div class="grid-6">
                <div class="tile" onclick="openTab('addons')"><div class="tile-num">${installedAddons.size}</div><div class="tile-lbl">Addons</div></div>
                <div class="tile" onclick="openTab('debrid')"><div class="tile-num">$connectedDebrid</div><div class="tile-lbl">Debrid Services</div></div>
                <div class="tile" onclick="openTab('servers')"><div class="tile-num">${servers.size}</div><div class="tile-lbl">Media Servers</div></div>
                <div class="tile"><div class="tile-num">${progress.size}</div><div class="tile-lbl">Watch Progress</div></div>
                <div class="tile"><div class="tile-num">${favorites.size}</div><div class="tile-lbl">Library Items</div></div>
                <div class="tile" onclick="openTab('profiles')"><div class="tile-num">${settings.profiles.size}</div><div class="tile-lbl">Profiles</div></div>
              </div>

              <div class="section-head">
                <span>Profiles</span>
                <span class="link-btn" onclick="openTab('profiles')">Manage</span>
              </div>
              <div class="profiles-row">$profilesOverviewHtml</div>
            </section>

            <!-- STREMIO / BACKUP SYNC -->
            <section id="panel-import" class="panel">
              <h1>Stremio &amp; Backup Sync</h1>
              <div class="page-sub">Import your installed Addons, Library, and Watch Progress from Stremio or a JSON backup.</div>

              <div class="card">
                <div class="card-head">
                  <h3>Sync from Stremio Account</h3>
                  ${if (cloudSyncManager.getSavedStremioEmail().isNotBlank()) "<span class='badge badge-green'>${escapeHtml(cloudSyncManager.getSavedStremioEmail())}</span>" else "<span class='badge'>Official Stremio API</span>"}
                </div>
                <form method="POST" action="/stremio-sync">
                  $pairField
                  <input type="hidden" name="tab" value="import"/>
                  <div class="grid-2">
                    <div>
                      <label class="field-label">Stremio Email</label>
                      <input name="email" value="${escapeHtml(cloudSyncManager.getSavedStremioEmail())}" type="email" placeholder="you@example.com"/>
                    </div>
                    <div>
                      <label class="field-label">Stremio Password</label>
                      <input name="password" type="password" placeholder="Your Stremio password"/>
                    </div>
                  </div>
                  <label class="field-label">Or paste Stremio AuthKey directly (optional)</label>
                  <input name="auth_key" type="password" placeholder="Optional AuthKey instead of Email/Password"/>
                  <button type="submit" class="btn-primary">Sync Addons, Library &amp; Progress from Stremio</button>
                </form>
                <div class="helper-link">
                  Manage your Stremio account &amp; addons <a href="https://web.stremio.com/" target="_blank" rel="noopener noreferrer">Here ↗</a>
                </div>
              </div>

              <div class="card">
                <div class="card-head">
                  <h3>Bulk Import Addons &amp; JSON Backup</h3>
                  <span class="badge">JSON Backup / Manifest List</span>
                </div>
                <form method="POST" action="/nuvio-import">
                  $pairField
                  <input type="hidden" name="tab" value="import"/>
                  <label class="field-label">Paste an Exported JSON Backup, Backup URL, or Multiple manifest.json Links</label>
                  <textarea name="data" rows="6" placeholder="Paste a JSON backup or one or more https://.../manifest.json URLs (one per line)..." required></textarea>
                  <button type="submit" class="btn-primary btn-accent">Import Addons &amp; Backup</button>
                </form>
              </div>
            </section>

            <!-- DEBRID & API KEYS -->
            <section id="panel-debrid" class="panel">
              <h1>Debrid &amp; API Keys</h1>
              <div class="page-sub">Connect TorBox, Real-Debrid, AllDebrid, Premiumize, and TMDB directly to your TV.</div>

              $debridCardsHtml

              <div class="card">
                <div class="card-head">
                  <h3>TMDB Metadata</h3>
                  ${if (tmdb.isConfigured) "<span class='badge badge-green'>Configured (${escapeHtml(tmdb.language)})</span>" else "<span class='badge'>Optional</span>"}
                </div>
                <form method="POST" action="/tmdb">
                  $pairField
                  <input type="hidden" name="tab" value="debrid"/>
                  <div class="grid-2">
                    <input name="apikey" type="password" placeholder="Paste TMDB API Key or Read Access Token..." required/>
                    <select name="lang">
                      <option value="en-US" ${if (tmdb.language == "en-US") "selected" else ""}>English (en-US)</option>
                      <option value="ar-SA" ${if (tmdb.language == "ar-SA") "selected" else ""}>Arabic (ar-SA)</option>
                    </select>
                  </div>
                  <button type="submit" class="btn-primary">Save TMDB Key</button>
                </form>
                <div class="helper-link">
                  Get your free TMDB API key <a href="https://www.themoviedb.org/settings/api" target="_blank" rel="noopener noreferrer">Here ↗</a>
                </div>
              </div>
            </section>

            <!-- ADDONS & PLUGINS -->
            <section id="panel-addons" class="panel">
              <h1>Addons &amp; Plugins</h1>
              <div class="page-sub">Paste any Stremio v3 manifest.json URL to install instantly on your TV.</div>

              <div class="card">
                <div class="card-head"><h3>Install Addon URL</h3></div>
                <form method="POST" action="/addon" class="inline-form">
                  $pairField
                  <input type="hidden" name="tab" value="addons"/>
                  <input name="url" placeholder="https://.../manifest.json or stremio://..." required/>
                  <button type="submit" class="btn-primary">Install to TV</button>
                </form>
              </div>

              <div class="card">
                <div class="card-head"><h3>Installed Addons (${installedAddons.size})</h3></div>
                $addonListHtml
              </div>
            </section>

            <!-- MEDIA SERVERS -->
            <section id="panel-servers" class="panel">
              <h1>Media Servers</h1>
              <div class="page-sub">Connect your personal Jellyfin, Emby, Plex, or WebDAV servers for Direct Play.</div>

              <div class="card">
                <div class="card-head"><h3>Add Personal Server</h3></div>
                <form method="POST" action="/server">
                  $pairField
                  <input type="hidden" name="tab" value="servers"/>
                  <div class="grid-2">
                    <div>
                      <label class="field-label">Server Type</label>
                      <select name="type">
                        <option value="JELLYFIN">Jellyfin</option>
                        <option value="EMBY">Emby</option>
                        <option value="PLEX">Plex</option>
                        <option value="WEBDAV">WebDAV / HTTP</option>
                      </select>
                    </div>
                    <div>
                      <label class="field-label">Display Name</label>
                      <input name="name" placeholder="e.g. Home Server"/>
                    </div>
                  </div>
                  <label class="field-label">Server Address</label>
                  <input name="url" placeholder="http://192.168.1.10:8096" required/>
                  <label class="field-label">API Key / X-Plex-Token (optional)</label>
                  <input name="token" placeholder="API Key or X-Plex-Token"/>
                  <div class="grid-2">
                    <input name="username" placeholder="Username (optional)"/>
                    <input name="password" type="password" placeholder="Password (optional)"/>
                  </div>
                  <button type="submit" class="btn-primary">Test &amp; Connect Server</button>
                </form>
                <div class="helper-link">
                  Need help finding your Plex token? Guide <a href="https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/" target="_blank" rel="noopener noreferrer">Here ↗</a>
                </div>
              </div>

              <div class="card">
                <div class="card-head"><h3>Connected Servers (${servers.size})</h3></div>
                $serversListHtml
              </div>
            </section>

            <!-- PROFILES -->
            <section id="panel-profiles" class="panel">
              <h1>Profiles</h1>
              <div class="page-sub">Switch profiles or create a new profile with an optional 4-digit PIN lock.</div>

              <div class="card">
                <div class="card-head"><h3>Existing Profiles</h3></div>
                $profilesManageHtml
              </div>

              <div class="card">
                <div class="card-head"><h3>Create New Profile</h3></div>
                <form method="POST" action="/profile-create">
                  $pairField
                  <input type="hidden" name="tab" value="profiles"/>
                  <div class="grid-3">
                    <input name="name" placeholder="Profile Name" required/>
                    <select name="color">
                      <option value="D97736">Warm Orange</option>
                      <option value="8C3B5E">Berry Purple</option>
                      <option value="0E7C7B">Deep Teal</option>
                      <option value="345372">Slate Blue</option>
                      <option value="10B981">Emerald</option>
                    </select>
                    <input name="pin" maxlength="4" placeholder="4-Digit PIN (optional)"/>
                  </div>
                  <button type="submit" class="btn-primary">Create Profile</button>
                </form>
              </div>
            </section>

            <!-- TV SETTINGS -->
            <section id="panel-settings" class="panel">
              <h1>TV Settings</h1>
              <div class="page-sub">Configure Quern playback engine, appearance, and navigation remotely from your phone.</div>

              <div class="card">
                <form method="POST" action="/tv-settings">
                  $pairField
                  <input type="hidden" name="tab" value="settings"/>
                  <div class="grid-2">
                    <div>
                      <label class="field-label">Player Engine</label>
                      <select name="player_engine">${optionsFor(PlayerEngine.entries, settings.playerEngine) { it.labelEn }}</select>
                    </div>
                    <div>
                      <label class="field-label">App Language</label>
                      <select name="language">${optionsFor(AppLanguage.entries, settings.language) { it.displayName }}</select>
                    </div>
                    <div>
                      <label class="field-label">Theme</label>
                      <select name="theme">${optionsFor(AppThemePreset.entries, settings.theme) { it.displayNameEn }}</select>
                    </div>
                    <div>
                      <label class="field-label">Navigation Style</label>
                      <select name="nav_style">${optionsFor(NavigationStyle.entries, settings.navigationStyle) { it.labelEn }}</select>
                    </div>
                    <div>
                      <label class="field-label">Preferred Subtitle Language</label>
                      <select name="sub_lang">${optionsFor(SubtitleLanguage.entries, settings.subtitleLanguage) { it.displayNameEn }}</select>
                    </div>
                    <div>
                      <label class="field-label">Preferred Audio Language</label>
                      <select name="audio_lang">${optionsFor(AudioLanguage.entries, settings.audioLanguage) { it.displayNameEn }}</select>
                    </div>
                  </div>
                  <label class="check-row">
                    <input type="checkbox" name="amoled" ${if (settings.amoledPureBlack) "checked" else ""}/>
                    <span>True AMOLED Pure Black Background</span>
                  </label>
                  <button type="submit" class="btn-primary">Apply Settings to TV</button>
                </form>
              </div>
            </section>

            <!-- ACCOUNT & SYNC -->
            <section id="panel-account" class="panel">
              <h1>Account &amp; Sync</h1>
              <div class="page-sub">Sync addons, watch history, and settings across multiple TVs and phones.</div>

              <div class="card">
                <div class="card-head">
                  <h3>Quern Account</h3>
                  ${if (account.isSignedIn) "<span class='badge badge-green'>${escapeHtml(account.email)}</span>" else "<span class='badge'>Not Signed In</span>"}
                </div>
                ${if (account.isSignedIn) """
                <div class="page-sub">This TV is linked to <b>${escapeHtml(account.email)}</b>. Your addons, settings and watch history follow you to every TV signed in to this account.</div>
                <form method="POST" action="/sync-account">
                  $pairField
                  <input type="hidden" name="tab" value="account"/>
                  <button type="submit" name="action" value="signout" class="btn-sm">Sign Out on This TV</button>
                </form>
                """ else """
                <div class="page-sub">One free account for all your TVs. Sign in on each TV and everything stays in sync. Your password never leaves your devices, and private data is encrypted before it's uploaded.</div>
                ${account.awaitingEmailConfirmation?.let { pending -> "<div class=\"warn\">We sent a confirmation link to <b>${escapeHtml(pending)}</b>. Open it, then sign in below.</div>" }.orEmpty()}
                <form method="POST" action="/sync-account">
                  $pairField
                  <input type="hidden" name="tab" value="account"/>
                  <div class="grid-2">
                    <input name="email" value="${escapeHtml(account.awaitingEmailConfirmation ?: account.email)}" type="email" autocomplete="email" placeholder="Email address" required/>
                    <input name="password" type="password" autocomplete="current-password" minlength="${AccountCrypto.MIN_PASSWORD_LENGTH}" placeholder="Password (${AccountCrypto.MIN_PASSWORD_LENGTH}+ characters)"/>
                  </div>
                  <div style="display:flex;gap:10px;flex-wrap:wrap">
                    <button type="submit" name="action" value="signin" class="btn-primary">Sign In</button>
                    <button type="submit" name="action" value="signup" class="btn-primary btn-accent">Create Account</button>
                    ${if (account.awaitingEmailConfirmation != null) """<button type="submit" name="action" value="resend" class="btn-sm" formnovalidate>Resend Email</button>""" else ""}
                  </div>
                </form>
                """}
              </div>

              <div class="card">
                <div class="card-head"><h3>Instant Multi-TV Sync Code</h3></div>
                <label class="field-label">Export Code (copy from this TV)</label>
                <textarea rows="3" readonly>${escapeHtml(syncCode)}</textarea>
                <form method="POST" action="/sync-import" class="inline-form">
                  $pairField
                  <input type="hidden" name="tab" value="account"/>
                  <input name="code" placeholder="Paste QUERN1:... sync code from another TV" required/>
                  <button type="submit" class="btn-primary">Import Code</button>
                </form>
              </div>
            </section>
          </main>

          <script>
            function openTab(name) {
              document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
              document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
              const panel = document.getElementById('panel-' + name) || document.getElementById('panel-overview');
              panel.classList.add('active');
              const btn = document.querySelector('.nav-item[data-tab="' + name + '"]');
              if (btn) btn.classList.add('active');
              try { history.replaceState(null, '', '#' + name); } catch (e) {}
            }
            (function init() {
              const hash = (location.hash || '').replace('#', '').toLowerCase();
              const valid = ['overview','profiles','import','debrid','addons','servers','settings','account'];
              const serverTab = '${escapeHtml(initialTab)}';
              if (valid.includes(hash)) {
                openTab(hash);
              } else if (valid.includes(serverTab)) {
                openTab(serverTab);
              } else {
                openTab('overview');
              }
            })();
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun findLocalIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    companion object {
        private const val TAG = "TvCompanion"
    }
}
