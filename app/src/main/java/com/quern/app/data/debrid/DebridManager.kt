package com.quern.app.data.debrid

import com.quern.app.data.local.SecureVault
import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebridManager(
    private val vault: SecureVault,
    torBoxClient: TorBoxClient,
    realDebridClient: RealDebridClient,
    allDebridClient: AllDebridClient,
    premiumizeClient: PremiumizeClient
) {
    private val providers: Map<DebridServiceType, DebridProvider> = mapOf(
        DebridServiceType.TORBOX to torBoxClient,
        DebridServiceType.REAL_DEBRID to realDebridClient,
        DebridServiceType.ALL_DEBRID to allDebridClient,
        DebridServiceType.PREMIUMIZE to premiumizeClient
    )

    private val _statuses = MutableStateFlow(
        DebridServiceType.entries.associateWith { DebridAccountStatus(it, getApiKey(it).isNotBlank()) }
    )
    val statuses: StateFlow<Map<DebridServiceType, DebridAccountStatus>> = _statuses.asStateFlow()

    fun getApiKey(type: DebridServiceType): String = vault.getString(keyFor(type))

    suspend fun saveAndValidate(type: DebridServiceType, apiKey: String): DebridAccountStatus {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            vault.remove(keyFor(type))
            val disconnected = DebridAccountStatus(type, isConnected = false)
            _statuses.value = _statuses.value + (type to disconnected)
            return disconnected
        }
        val provider = providers.getValue(type)
        val status = provider.validateApiKey(trimmed)
        if (status.isConnected) {
            vault.putString(keyFor(type), trimmed)
        }
        _statuses.value = _statuses.value + (type to status)
        return status
    }

    suspend fun refreshAllStatuses() = coroutineScope {
        val results = DebridServiceType.entries.map { type ->
            async {
                val key = getApiKey(type)
                if (key.isBlank()) {
                    type to DebridAccountStatus(type, isConnected = false)
                } else {
                    type to providers.getValue(type).validateApiKey(key)
                }
            }
        }.awaitAll().toMap()
        _statuses.value = results
    }

    fun getConfiguredServices(): List<DebridServiceType> =
        DebridServiceType.entries.filter { getApiKey(it).isNotBlank() }

    /**
     * Checks cache status for addon infoHash streams across all linked Debrid services
     * and annotates streams with instant badges (e.g. "⚡ TorBox").
     */
    suspend fun enrichAddonStreams(streams: List<StreamSource>): List<StreamSource> = coroutineScope {
        val configured = getConfiguredServices()
        if (configured.isEmpty()) return@coroutineScope streams

        val hashes = streams.mapNotNull { it.infoHash?.lowercase() }.distinct()
        if (hashes.isEmpty()) return@coroutineScope streams

        val cachedByService = configured.map { service ->
            async {
                service to providers.getValue(service).checkCachedHashes(getApiKey(service), hashes)
            }
        }.awaitAll().toMap()

        streams.map { stream ->
            val hash = stream.infoHash?.lowercase() ?: return@map stream
            val matchedService = configured.firstOrNull { cachedByService[it]?.contains(hash) == true }
            if (matchedService != null) {
                stream.copy(
                    isCached = true,
                    origin = matchedService.toSourceOrigin(),
                    qualityBadges = (listOf("⚡ ${matchedService.displayName}") + stream.qualityBadges).distinct()
                )
            } else {
                stream
            }
        }.sortedByDescending { it.isCached }
    }

    /**
     * Resolves an infoHash stream into a playable HTTPS URL using the preferred connected Debrid service
     * (prioritizing TorBox first if connected, then Real-Debrid, AllDebrid, Premiumize).
     */
    suspend fun resolveStream(stream: StreamSource): StreamSource? {
        if (stream.isDirectlyPlayable) return stream
        val hash = stream.infoHash ?: return null

        val preferredOrder = buildList {
            val originService = stream.origin.toDebridService()
            if (originService != null && getApiKey(originService).isNotBlank()) add(originService)
            getConfiguredServices().forEach { if (it !in this) add(it) }
        }

        for (service in preferredOrder) {
            val apiKey = getApiKey(service)
            val resolvedUrl = providers.getValue(service)
                .resolveInfoHashToStreamUrl(apiKey, hash, stream.fileIdx)
            if (!resolvedUrl.isNullOrBlank()) {
                return stream.copy(
                    url = resolvedUrl,
                    origin = service.toSourceOrigin(),
                    isCached = true
                )
            }
        }
        return null
    }

    suspend fun getAllCloudItems(): List<DebridCloudItem> = coroutineScope {
        getConfiguredServices().map { service ->
            async {
                providers.getValue(service).getCloudLibrary(getApiKey(service))
            }
        }.awaitAll().flatten()
    }

    suspend fun resolveCloudItemUrl(item: DebridCloudItem): String? {
        val key = getApiKey(item.service)
        if (key.isBlank()) return null
        return providers.getValue(item.service).getCloudItemStreamUrl(key, item)
    }

    private fun keyFor(type: DebridServiceType): String = "debrid_key_${type.name.lowercase()}"

    private fun DebridServiceType.toSourceOrigin(): SourceOrigin = when (this) {
        DebridServiceType.TORBOX -> SourceOrigin.TORBOX
        DebridServiceType.REAL_DEBRID -> SourceOrigin.REAL_DEBRID
        DebridServiceType.ALL_DEBRID -> SourceOrigin.ALL_DEBRID
        DebridServiceType.PREMIUMIZE -> SourceOrigin.PREMIUMIZE
    }

    private fun SourceOrigin.toDebridService(): DebridServiceType? = when (this) {
        SourceOrigin.TORBOX -> DebridServiceType.TORBOX
        SourceOrigin.REAL_DEBRID -> DebridServiceType.REAL_DEBRID
        SourceOrigin.ALL_DEBRID -> DebridServiceType.ALL_DEBRID
        SourceOrigin.PREMIUMIZE -> DebridServiceType.PREMIUMIZE
        else -> null
    }
}
