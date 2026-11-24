package com.quern.app.data.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.quern.app.data.sync.DeviceLinkClient
import com.quern.app.data.sync.DeviceLinkCrypto
import com.quern.app.data.sync.PendingDeviceLink
import com.quern.app.ui.components.isTvHardware
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.coroutines.resume

const val QUERN_LINK_NSD_SERVICE_TYPE = "_quern-link._tcp"
private const val TAG = "QuernLinkNsd"

/**
 * TV-only LAN NSD advertiser (`_quern-link._tcp`).
 * Registers ONLY while the TV's signed-out QR code is actively displayed on screen and immediately
 * unregisters when closed, expired, locked, or completed. Publishes only non-secret fields:
 * protocol version (`v`), TV display name (`n`), random session id (`s`), and ephemeral P-256 SPKI (`pk`).
 */
class TvLinkNsdAdvertiser(
    context: Context,
    private val allowNonTvForTesting: Boolean = false
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var activeSession: RegistrationHandle? = null

    private class RegistrationHandle(val nsdManager: NsdManager) {
        var registered = false
        var stopRequested = false
        lateinit var listener: NsdManager.RegistrationListener
    }

    fun advertise(
        port: Int,
        deviceName: String,
        sessionId: String,
        publicKeyB64: String
    ) {
        if (!allowNonTvForTesting && !isTvHardware(appContext)) return
        if (port <= 0 || sessionId.isBlank() || publicKeyB64.isBlank()) return

        stop()

        val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val cleanName = deviceName.trim().take(60).ifBlank { "Quern TV" }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Quern TV"
            serviceType = QUERN_LINK_NSD_SERVICE_TYPE
            setPort(port)
            runCatching {
                setAttribute("v", "1")
                setAttribute("n", cleanName)
                setAttribute("s", sessionId)
                setAttribute("pk", publicKeyB64)
            }
        }

        val handle = RegistrationHandle(nsdManager)
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                var shouldUnregisterNow = false
                synchronized(lock) {
                    if (handle.stopRequested || activeSession !== handle) {
                        shouldUnregisterNow = true
                    } else {
                        handle.registered = true
                    }
                }
                if (shouldUnregisterNow) {
                    runCatching { nsdManager.unregisterService(this) }
                } else {
                    Log.i(TAG, "Advertising TV link session on LAN NSD (port=$port)")
                }
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) {
                    if (activeSession === handle) activeSession = null
                }
                Log.w(TAG, "NSD registration failed (code=$errorCode)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                synchronized(lock) {
                    if (activeSession === handle) activeSession = null
                }
                Log.i(TAG, "Stopped advertising TV link session on LAN NSD")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) {
                    if (activeSession === handle) activeSession = null
                }
            }
        }
        handle.listener = listener

        synchronized(lock) {
            activeSession = handle
        }
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            synchronized(lock) {
                if (activeSession === handle) activeSession = null
            }
        }
    }

    fun stop() {
        val toUnregister: RegistrationHandle?
        val wasRegistered: Boolean
        synchronized(lock) {
            toUnregister = activeSession
            wasRegistered = toUnregister?.registered == true
            toUnregister?.stopRequested = true
            activeSession = null
        }
        if (toUnregister != null && wasRegistered) {
            runCatching { toUnregister.nsdManager.unregisterService(toUnregister.listener) }
        }
    }
}

/**
 * Phone-only LAN NSD discovery (`_quern-link._tcp`).
 * Never runs on TV hardware. Sequentially resolves discovered services so older Android versions
 * never fail with `FAILURE_ALREADY_ACTIVE`, validates LAN origins + P-256 public keys, and exposes
 * active TVs ready for 6-digit code confirmation.
 */
class TvLinkNsdDiscovery(
    context: Context,
    private val httpClient: OkHttpClient
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    private val _discoveredTvs = MutableStateFlow<List<PendingDeviceLink>>(emptyList())
    val discoveredTvs: StateFlow<List<PendingDeviceLink>> = _discoveredTvs.asStateFlow()

    private val byServiceName = LinkedHashMap<String, PendingDeviceLink>()
    private var activeDiscovery: DiscoveryHandle? = null
    private var resolveJob: Job? = null
    private var resolveChannel: Channel<NsdServiceInfo>? = null

    private class DiscoveryHandle(val nsdManager: NsdManager) {
        var started = false
        var stopRequested = false
        lateinit var listener: NsdManager.DiscoveryListener
    }

    fun start(scope: CoroutineScope) {
        if (isTvHardware(appContext)) return
        stop()

        val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val channel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        resolveChannel = channel

        resolveJob = scope.launch(Dispatchers.IO) {
            for (candidate in channel) {
                try {
                    val resolved = resolveWithRetry(nsdManager, candidate) ?: continue
                    val link = extractPendingLink(resolved) ?: continue
                    synchronized(lock) {
                        byServiceName[resolved.serviceName] = link
                        publishLocked()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }

        val handle = DiscoveryHandle(nsdManager)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                var stopNow = false
                synchronized(lock) {
                    if (handle.stopRequested || activeDiscovery !== handle) {
                        stopNow = true
                    } else {
                        handle.started = true
                    }
                }
                if (stopNow) {
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val type = service.serviceType.orEmpty().trimEnd('.')
                if (!type.equals(QUERN_LINK_NSD_SERVICE_TYPE, ignoreCase = true)) return
                channel.trySend(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(lock) {
                    if (byServiceName.remove(service.serviceName) != null) {
                        publishLocked()
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                synchronized(lock) {
                    if (activeDiscovery === handle) activeDiscovery = null
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                synchronized(lock) {
                    if (activeDiscovery === handle) activeDiscovery = null
                }
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                synchronized(lock) {
                    if (activeDiscovery === handle) activeDiscovery = null
                }
            }
        }
        handle.listener = listener

        synchronized(lock) {
            byServiceName.clear()
            _discoveredTvs.value = emptyList()
            activeDiscovery = handle
        }
        runCatching {
            nsdManager.discoverServices(QUERN_LINK_NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            synchronized(lock) {
                if (activeDiscovery === handle) activeDiscovery = null
            }
        }
    }

    fun stop() {
        val handle: DiscoveryHandle?
        val wasStarted: Boolean
        synchronized(lock) {
            handle = activeDiscovery
            wasStarted = handle?.started == true
            handle?.stopRequested = true
            activeDiscovery = null
            byServiceName.clear()
            _discoveredTvs.value = emptyList()
        }
        resolveChannel?.close()
        resolveChannel = null
        resolveJob?.cancel()
        resolveJob = null
        if (handle != null && wasStarted) {
            runCatching { handle.nsdManager.stopServiceDiscovery(handle.listener) }
        }
    }

    private fun publishLocked() {
        // Deduplicate by tvOrigin, keeping the freshest resolved entry last.
        val dedup = LinkedHashMap<String, PendingDeviceLink>()
        for (entry in byServiceName.values) {
            dedup[entry.tvOrigin] = entry
        }
        _discoveredTvs.value = dedup.values.toList()
    }

    private suspend fun resolveWithRetry(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo
    ): NsdServiceInfo? {
        repeat(3) { attempt ->
            val (resolved, errorCode) = resolveOnce(nsdManager, serviceInfo)
            if (resolved != null) return resolved
            if (errorCode != NsdManager.FAILURE_ALREADY_ACTIVE) return null
            delay(220L * (attempt + 1))
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveOnce(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo
    ): Pair<NsdServiceInfo?, Int> = suspendCancellableCoroutine { cont ->
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                if (done.compareAndSet(false, true)) {
                    cont.resume(null to errorCode)
                }
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                if (done.compareAndSet(false, true)) {
                    cont.resume(resolvedInfo to 0)
                }
            }
        }
        runCatching {
            nsdManager.resolveService(serviceInfo, listener)
        }.onFailure {
            if (done.compareAndSet(false, true)) {
                cont.resume(null to NsdManager.FAILURE_INTERNAL_ERROR)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun extractPendingLink(resolved: NsdServiceInfo): PendingDeviceLink? {
        val host: InetAddress = resolved.host ?: return null
        val port = resolved.port
        if (port !in 1..65535) return null

        val rawAddr = host.hostAddress?.substringBefore('%')?.trim().orEmpty()
        if (rawAddr.isBlank()) return null
        val origin = if (host is Inet6Address || ':' in rawAddr) {
            "http://[$rawAddr]:$port"
        } else {
            "http://$rawAddr:$port"
        }
        if (!DeviceLinkCrypto.isAllowedLanOrigin(origin)) return null

        val attrs = runCatching { resolved.attributes }.getOrNull().orEmpty()
        val pk = attrs["pk"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
        val sessionId = attrs["s"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
        val name = attrs["n"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
            .take(60)
            .ifBlank { resolved.serviceName?.take(60).orEmpty().ifBlank { "Quern TV" } }

        if (sessionId.length in 12..64 && pk.isNotBlank() &&
            runCatching { DeviceLinkCrypto.decodePublicKey(pk) }.isSuccess
        ) {
            return PendingDeviceLink(
                tvOrigin = origin,
                tvPublicKeyB64 = pk,
                sessionId = sessionId,
                deviceName = name
            )
        }

        // Fallback if a router stripped mDNS TXT attributes: query public /api/link/discover on LAN.
        return DeviceLinkClient.resolveTvAddress(httpClient, origin).getOrNull()
    }
}
