package com.quern.app.data.addons

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.quern.app.data.debrid.DebridManager
import com.quern.app.data.local.AddonDao
import com.quern.app.data.local.AddonEntity
import com.quern.app.domain.model.AddonManifest
import com.quern.app.domain.model.CatalogDefinition
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

data class AddonHealthResult(
    val manifestUrl: String,
    val reachable: Boolean,
    val manifestValid: Boolean,
    val latencyMs: Long,
    val error: String? = null
)

data class HomeCatalogSpec(
    val rowId: String,
    val visibilityKey: String,
    val addon: InstalledAddon,
    val catalog: CatalogDefinition,
    val extraArgs: Map<String, String>
)

data class AddonManifestRefreshResult(
    val totalCount: Int,
    val succeededCount: Int,
    val updatedCount: Int,
    val anyChanged: Boolean,
    val refreshedAddonIds: Set<String>,
    val allValidCatalogKeys: Set<String>
)

class AddonRepository(
    private val dao: AddonDao,
    private val client: StremioAddonClient,
    private val debridManager: DebridManager,
    private val json: Json,
    context: Context? = null
) {
    private val prefs: SharedPreferences? =
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()

    val addons: Flow<List<InstalledAddon>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toInstalledAddon() }
    }

    suspend fun getEnabledAddons(): List<InstalledAddon> =
        dao.getAll().filter { it.enabled }.mapNotNull { it.toInstalledAddon() }

    suspend fun installAddon(rawManifestUrl: String): Result<InstalledAddon> = runCatching {
        storeFetchedAddon(fetchAddonManifest(rawManifestUrl))
    }

    internal suspend fun fetchAddonManifest(rawUrl: String, timeoutMs: Long? = null) =
        client.fetchManifest(rawUrl, timeoutMs)

    internal suspend fun storeFetchedAddon(manifest: Pair<InstalledAddon, String>): InstalledAddon {
        val (installed, rawJson) = manifest
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
        return installed.copy(priority = nextPriority)
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

    /**
     * Re-fetches each installed addon's manifest URL and updates stored `manifestJson`, `name`,
     * `addonId`, and `baseUrl` in place while preserving priority and enabled state.
     * When [force] is false, runs at most once every 6 hours; returns `null` when skipped by cooldown.
     */
    suspend fun refreshInstalledManifests(force: Boolean = false): AddonManifestRefreshResult? =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val now = System.currentTimeMillis()
                if (!force) {
                    val lastRefresh = prefs?.getLong(KEY_LAST_MANIFEST_REFRESH_MS, 0L) ?: 0L
                    if (now - lastRefresh < MANIFEST_REFRESH_INTERVAL_MS) {
                        return@withLock null
                    }
                }
                val entities = dao.getAll()
                if (entities.isEmpty()) {
                    return@withLock AddonManifestRefreshResult(
                        totalCount = 0,
                        succeededCount = 0,
                        updatedCount = 0,
                        anyChanged = false,
                        refreshedAddonIds = emptySet(),
                        allValidCatalogKeys = emptySet()
                    )
                }

                val fetchedList = coroutineScope {
                    entities.map { entity ->
                        async {
                            entity to withTimeoutOrNull(MANIFEST_REFRESH_TIMEOUT_MS) {
                                runCatching { client.fetchManifest(entity.manifestUrl) }.getOrNull()
                            }
                        }
                    }.awaitAll()
                }

                var succeeded = 0
                var updated = 0
                val refreshedAddonIds = LinkedHashSet<String>()

                for ((entity, fetchedPair) in fetchedList) {
                    if (fetchedPair == null) continue
                    val (installed, rawJson) = fetchedPair
                    if (installed.manifest.id.isBlank() || installed.manifest.name.isBlank()) continue
                    succeeded++
                    refreshedAddonIds += entity.addonId
                    refreshedAddonIds += installed.manifest.id

                    val changed = rawJson != entity.manifestJson ||
                        installed.manifest.name != entity.name ||
                        installed.manifest.id != entity.addonId ||
                        installed.baseUrl != entity.baseUrl

                    if (changed) {
                        updated++
                        dao.upsert(
                            entity.copy(
                                baseUrl = installed.baseUrl,
                                addonId = installed.manifest.id,
                                name = installed.manifest.name,
                                manifestJson = rawJson
                            )
                        )
                    }
                }

                if (succeeded > 0) {
                    prefs?.edit()?.putLong(KEY_LAST_MANIFEST_REFRESH_MS, System.currentTimeMillis())?.apply()
                }

                val allValidKeys = dao.getAll()
                    .mapNotNull { it.toInstalledAddon() }
                    .flatMap { addon ->
                        addon.manifest.catalogs.map { cat -> cat.visibilityKey(addon.manifest.id) }
                    }
                    .toSet()

                Log.i(
                    TAG,
                    "Manifest refresh (force=$force): succeeded=$succeeded/${entities.size}, updated=$updated"
                )

                AddonManifestRefreshResult(
                    totalCount = entities.size,
                    succeededCount = succeeded,
                    updatedCount = updated,
                    anyChanged = updated > 0,
                    refreshedAddonIds = refreshedAddonIds,
                    allValidCatalogKeys = allValidKeys
                )
            }
        }

    /**
     * Builds the ordered list of all Home-eligible catalogs across enabled addons:
     * 1. Addon priority order (Settings > Addons order)
     * 2. Manifest catalog order inside each addon (configure-page order)
     * No per-addon or total cap.
     */
    suspend fun planHomeCatalogSpecs(hiddenCatalogKeys: Set<String> = emptySet()): List<HomeCatalogSpec> {
        val enabled = getEnabledAddons()
        val usedRowIds = HashSet<String>()
        val specs = ArrayList<HomeCatalogSpec>()

        for (addon in enabled) {
            for (catalog in addon.manifest.catalogs) {
                val extraArgs = catalog.defaultHomeExtraArgs() ?: continue
                val visKey = catalog.visibilityKey(addon.manifest.id)
                if (visKey in hiddenCatalogKeys) continue

                var rowId = visKey
                if (!usedRowIds.add(rowId)) {
                    var suffix = 2
                    while (!usedRowIds.add("${visKey}#$suffix")) suffix++
                    rowId = "${visKey}#$suffix"
                }
                specs += HomeCatalogSpec(
                    rowId = rowId,
                    visibilityKey = visKey,
                    addon = addon,
                    catalog = catalog,
                    extraArgs = extraArgs
                )
            }
        }

        Log.i(TAG, "Planned ${specs.size} Home catalog(s)")
        return specs
    }

    suspend fun fetchCatalogSpecRow(spec: HomeCatalogSpec): CatalogRow? {
        val items = withTimeoutOrNull(CATALOG_FETCH_TIMEOUT_MS) {
            client.fetchCatalog(
                addon = spec.addon,
                type = spec.catalog.type,
                catalogId = spec.catalog.id,
                extraParams = spec.extraArgs
            )
        }.orEmpty()
        if (items.isEmpty()) return null
        val firstOptionLabel = spec.extraArgs.values.firstOrNull { it.isNotBlank() }
        val title = formatCatalogTitle(
            rawName = spec.catalog.name?.takeIf { it.isNotBlank() } ?: spec.catalog.id,
            type = spec.catalog.type,
            addonName = spec.addon.manifest.name,
            optionLabel = firstOptionLabel
        )
        return CatalogRow(
            id = spec.rowId,
            title = title,
            origin = SourceOrigin.ADDON,
            items = items
        )
    }

    suspend fun fetchCatalogBatch(specs: List<HomeCatalogSpec>): List<CatalogRow> = coroutineScope {
        if (specs.isEmpty()) return@coroutineScope emptyList()
        specs.map { spec -> async { fetchCatalogSpecRow(spec) } }
            .awaitAll()
            .filterNotNull()
    }

    suspend fun fetchHomeCatalogRows(hiddenCatalogKeys: Set<String> = emptySet()): List<CatalogRow> {
        val specs = planHomeCatalogSpecs(hiddenCatalogKeys)
        if (specs.isEmpty()) return emptyList()
        val results = ArrayList<CatalogRow>(specs.size)
        results += fetchCatalogBatch(specs.take(INITIAL_HOME_BATCH_SIZE))
        for (chunk in specs.drop(INITIAL_HOME_BATCH_SIZE).chunked(BACKGROUND_HOME_BATCH_SIZE)) {
            results += fetchCatalogBatch(chunk)
        }
        return results
    }

    private fun formatCatalogTitle(
        rawName: String,
        type: String,
        addonName: String,
        optionLabel: String? = null
    ): String {
        val cleanType = when (type.lowercase()) {
            "movie", "movies" -> "Movies"
            "series", "tv", "show", "shows" -> "Series"
            else -> type.replaceFirstChar { it.uppercase() }
        }
        val normalized = rawName.trim().lowercase()
        val catalogTitle = when {
            normalized == "top" || normalized == "popular" -> "Popular $cleanType"
            normalized == "featured" -> "Featured $cleanType"
            normalized == "imdbrating" || normalized.contains("rating") -> "Top Rated $cleanType"
            normalized == "new" || normalized == "year" -> "New $cleanType"
            normalized.contains("movie") || normalized.contains("series") -> rawName.trim()
            else -> "${rawName.trim().replaceFirstChar { it.uppercase() }} $cleanType"
        }
        val withOption = if (!optionLabel.isNullOrBlank()) {
            "$catalogTitle · $optionLabel"
        } else {
            catalogTitle
        }
        val isCoreMeta = addonName.contains("cinemeta", ignoreCase = true)
        return if (isCoreMeta) withOption else "$withOption • $addonName"
    }

    suspend fun searchCatalogs(query: String): List<MediaItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val enabled = getEnabledAddons()
        val tasks = enabled.flatMap { addon ->
            addon.manifest.catalogs
                .filter { it.supportsSearch }
                .take(6)
                .map { catalog ->
                    async {
                        withTimeoutOrNull(CATALOG_FETCH_TIMEOUT_MS) {
                            client.fetchCatalog(addon, catalog.type, catalog.id, search = query)
                        }.orEmpty()
                    }
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

    companion object {
        private const val TAG = "QuernCatalogs"
        private const val PREFS_NAME = "quern_addon_prefs"
        private const val KEY_LAST_MANIFEST_REFRESH_MS = "last_manifest_refresh_ms"
        const val MANIFEST_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val MANIFEST_REFRESH_TIMEOUT_MS = 10_000L
        private const val CATALOG_FETCH_TIMEOUT_MS = 10_000L
        const val INITIAL_HOME_BATCH_SIZE = 8
        const val BACKGROUND_HOME_BATCH_SIZE = 6
    }
}
