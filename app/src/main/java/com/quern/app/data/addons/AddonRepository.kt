package com.quern.app.data.addons

import com.quern.app.data.debrid.DebridManager
import com.quern.app.data.local.AddonDao
import com.quern.app.data.local.AddonEntity
import com.quern.app.domain.model.AddonManifest
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

data class AddonHealthResult(
    val manifestUrl: String,
    val reachable: Boolean,
    val manifestValid: Boolean,
    val latencyMs: Long,
    val error: String? = null
)

class AddonRepository(
    private val dao: AddonDao,
    private val client: StremioAddonClient,
    private val debridManager: DebridManager,
    private val json: Json
) {

    val addons: Flow<List<InstalledAddon>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toInstalledAddon() }
    }

    suspend fun getEnabledAddons(): List<InstalledAddon> =
        dao.getAll().filter { it.enabled }.mapNotNull { it.toInstalledAddon() }

    suspend fun installAddon(rawManifestUrl: String): Result<InstalledAddon> = runCatching {
        val (installed, rawJson) = client.fetchManifest(rawManifestUrl)
        val nextPriority = dao.getAll().size
        dao.upsert(
            AddonEntity(
                manifestUrl = installed.manifestUrl,
                baseUrl = installed.baseUrl,
                addonId = installed.manifest.id,
                name = installed.manifest.name,
                manifestJson = rawJson,
                enabled = true,
                priority = nextPriority
            )
        )
        installed.copy(priority = nextPriority)
    }

    suspend fun checkAddonHealth(manifestUrl: String): AddonHealthResult {
        val start = System.currentTimeMillis()
        return try {
            val (installed, _) = client.fetchManifest(manifestUrl)
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val valid = installed.manifest.id.isNotBlank() && installed.manifest.name.isNotBlank()
            AddonHealthResult(
                manifestUrl = manifestUrl,
                reachable = true,
                manifestValid = valid,
                latencyMs = elapsed,
                error = if (valid) null else "Invalid manifest fields"
            )
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            AddonHealthResult(
                manifestUrl = manifestUrl,
                reachable = false,
                manifestValid = false,
                latencyMs = elapsed,
                error = e.message ?: "Connection failed"
            )
        }
    }

    suspend fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        dao.setEnabled(manifestUrl, enabled)
    }

    suspend fun removeAddon(manifestUrl: String) {
        dao.delete(manifestUrl)
    }

    suspend fun moveAddonUp(manifestUrl: String) {
        val list = dao.getAll()
        val idx = list.indexOfFirst { it.manifestUrl == manifestUrl }
        if (idx <= 0) return
        val prev = list[idx - 1]
        val curr = list[idx]
        dao.setPriority(curr.manifestUrl, idx - 1)
        dao.setPriority(prev.manifestUrl, idx)
    }

    suspend fun moveAddonDown(manifestUrl: String) {
        val list = dao.getAll()
        val idx = list.indexOfFirst { it.manifestUrl == manifestUrl }
        if (idx < 0 || idx >= list.lastIndex) return
        val next = list[idx + 1]
        val curr = list[idx]
        dao.setPriority(curr.manifestUrl, idx + 1)
        dao.setPriority(next.manifestUrl, idx)
    }

    suspend fun fetchHomeCatalogRows(hiddenCatalogKeys: Set<String> = emptySet()): List<CatalogRow> = coroutineScope {
        val enabled = getEnabledAddons()
        val tasks = enabled.flatMap { addon ->
            addon.manifest.catalogs
                .filter { !it.requiresExtra }
                .filter { catalog ->
                    val key = "${addon.manifest.id}_${catalog.type}_${catalog.id}"
                    key !in hiddenCatalogKeys
                }
                .take(6)
                .map { catalog ->
                    async {
                        val items = client.fetchCatalog(addon, catalog.type, catalog.id)
                        if (items.isEmpty()) null else {
                            val title = formatCatalogTitle(
                                rawName = catalog.name?.takeIf { it.isNotBlank() } ?: catalog.id,
                                type = catalog.type,
                                addonName = addon.manifest.name
                            )
                            val sortWeight = catalogSortWeight(catalog.name ?: catalog.id, catalog.type)
                            sortWeight to CatalogRow(
                                id = "${addon.manifest.id}_${catalog.type}_${catalog.id}",
                                title = title,
                                origin = SourceOrigin.ADDON,
                                items = items
                            )
                        }
                    }
                }
        }
        tasks.awaitAll()
            .filterNotNull()
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun formatCatalogTitle(rawName: String, type: String, addonName: String): String {
        val cleanType = when (type.lowercase()) {
            "movie", "movies" -> "Movies"
            "series", "tv", "show", "shows" -> "Series"
            else -> type.replaceFirstChar { it.uppercase() }
        }
        val normalized = rawName.trim().lowercase()
        val baseTitle = when {
            normalized == "top" || normalized == "popular" -> "Popular $cleanType"
            normalized == "featured" -> "Featured $cleanType"
            normalized == "imdbRating".lowercase() || normalized.contains("rating") -> "Top Rated $cleanType"
            normalized.contains("new") || normalized.contains("year") -> "New $cleanType"
            normalized.contains("movie") || normalized.contains("series") -> rawName.trim()
            else -> "${rawName.trim().replaceFirstChar { it.uppercase() }} $cleanType"
        }
        val isCoreMeta = addonName.contains("cinemeta", ignoreCase = true)
        return if (isCoreMeta) baseTitle else "$baseTitle • $addonName"
    }

    private fun catalogSortWeight(rawName: String, type: String): Int {
        val n = rawName.trim().lowercase()
        val isMovie = type.equals("movie", ignoreCase = true)
        val isSeries = type.equals("series", ignoreCase = true)
        return when {
            (n == "top" || n == "popular") && isMovie -> 1
            (n == "top" || n == "popular") && isSeries -> 2
            n.contains("featured") && isMovie -> 3
            n.contains("featured") && isSeries -> 4
            n.contains("rating") && isMovie -> 5
            n.contains("rating") && isSeries -> 6
            isMovie -> 10
            isSeries -> 11
            else -> 20
        }
    }

    suspend fun searchCatalogs(query: String): List<MediaItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val enabled = getEnabledAddons()
        val tasks = enabled.flatMap { addon ->
            addon.manifest.catalogs
                .filter { it.supportsSearch }
                .take(2)
                .map { catalog ->
                    async { client.fetchCatalog(addon, catalog.type, catalog.id, search = query) }
                }
        }
        tasks.awaitAll().flatten().distinctBy { "${it.type}_${it.id}" }
    }

    suspend fun fetchMeta(type: MediaType, id: String): MediaDetail? = coroutineScope {
        val enabled = getEnabledAddons()
        for (addon in enabled) {
            val detail = client.fetchMeta(addon, type, id)
            if (detail != null) return@coroutineScope detail
        }
        null
    }

    suspend fun fetchAggregatedStreams(type: MediaType, id: String): List<StreamSource> = coroutineScope {
        val enabled = getEnabledAddons()
        val rawStreams = enabled.map { addon ->
            async { withTimeoutOrNull(7_000L) { client.fetchStreams(addon, type, id) }.orEmpty() }
        }.awaitAll().flatten()

        withTimeoutOrNull(3_500L) { debridManager.enrichAddonStreams(rawStreams) } ?: rawStreams
    }

    /**
     * Streams addon results progressively as each addon responds (capped at 7s per addon),
     * then enriches with Debrid cache status without blocking initial display.
     */
    suspend fun fetchStreamsProgressive(
        type: MediaType,
        id: String,
        onStreamsUpdated: suspend (List<StreamSource>) -> Unit
    ): List<StreamSource> = coroutineScope {
        val enabled = getEnabledAddons()
        if (enabled.isEmpty()) return@coroutineScope emptyList()
        val mutex = Mutex()
        val collected = mutableListOf<StreamSource>()

        val jobs = enabled.map { addon ->
            launch {
                val batch = withTimeoutOrNull(7_000L) { client.fetchStreams(addon, type, id) }.orEmpty()
                if (batch.isNotEmpty()) {
                    val snapshot = mutex.withLock {
                        collected.addAll(batch)
                        collected.toList()
                    }
                    onStreamsUpdated(snapshot)
                }
            }
        }
        jobs.forEach { it.join() }

        val raw = mutex.withLock { collected.toList() }
        if (raw.isEmpty()) return@coroutineScope emptyList()
        val enriched = withTimeoutOrNull(3_500L) { debridManager.enrichAddonStreams(raw) } ?: raw
        onStreamsUpdated(enriched)
        enriched
    }

    suspend fun fetchAggregatedSubtitles(type: MediaType, id: String): List<SubtitleTrack> = coroutineScope {
        val enabled = getEnabledAddons()
        enabled.map { addon ->
            async { withTimeoutOrNull(6_000L) { client.fetchSubtitles(addon, type, id) }.orEmpty() }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    private fun AddonEntity.toInstalledAddon(): InstalledAddon? = runCatching {
        InstalledAddon(
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            manifest = json.decodeFromString<AddonManifest>(manifestJson),
            enabled = enabled,
            priority = priority
        )
    }.getOrNull()
}
