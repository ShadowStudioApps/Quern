package com.quern.app.data

import com.quern.app.data.addons.AddonRepository
import com.quern.app.data.debrid.DebridManager
import com.quern.app.data.local.FavoriteDao
import com.quern.app.data.local.FavoriteEntity
import com.quern.app.data.local.SourceMemoryDao
import com.quern.app.data.local.SourceMemoryEntity
import com.quern.app.data.local.TombstoneDao
import com.quern.app.data.local.TombstoneEntity
import com.quern.app.data.local.WatchHistoryDao
import com.quern.app.data.local.WatchHistoryEntity
import com.quern.app.data.metadata.TmdbClient
import com.quern.app.data.metadata.TraktClient
import com.quern.app.data.servers.MediaServerManager
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.SubtitleTrack
import com.quern.app.domain.model.WatchProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock
import android.util.Log

/** Tombstone kinds shared with the sync engine. */
const val KIND_FAVORITE = "favorite"
const val KIND_PROGRESS = "progress"

class QuernRepository(
    val addonRepository: AddonRepository,
    val debridManager: DebridManager,
    val serverManager: MediaServerManager,
    val tmdbClient: TmdbClient,
    val traktClient: TraktClient,
    private val historyDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val tombstoneDao: TombstoneDao,
    private val sourceMemoryDao: SourceMemoryDao,
    /** Active profile id; history and favorites are kept per profile. */
    activeProfileId: Flow<String>,
    /** Current active profile id, read at write time. */
    private val currentProfileId: () -> String
) {
    private val profileId: Flow<String> = activeProfileId.distinctUntilChanged()

    suspend fun rankPlaybackSources(
        title: MediaItem,
        sources: List<StreamSource>,
        preferredQuality: String? = null,
        preferredService: SourceOrigin? = null
    ): List<StreamSource> {
        val memory = sourceMemoryDao.forTitle(sourceMemoryTitleId(title))
            .associateBy { Triple(it.addonId, it.quality, it.service) }
        return sources
            .mapNotNull { source ->
                if (!source.isDirectlyPlayable && !source.needsDebridResolution) return@mapNotNull null
                val result = memory[sourceMemoryKey(source)]
                val score = if (result == null) 0.0 else {
                    (result.successes.toDouble() - result.failures) /
                        (result.successes.toDouble() + result.failures + 2.0)
                }
                source to score
            }
            .sortedWith(
                compareByDescending<Pair<StreamSource, Double>> { it.second }
                    .thenByDescending { preferredQuality != null && sourceQuality(it.first) == preferredQuality }
                    .thenByDescending { it.first.origin == preferredService }
                    .thenByDescending { it.first.isCached }
            )
            .map { it.first }
    }

    suspend fun recordPlaybackSourceResult(title: MediaItem, source: StreamSource, succeeded: Boolean) {
        if (source.addonId.isNullOrBlank()) return
        val (addonId, quality, service) = sourceMemoryKey(source)
        sourceMemoryDao.record(
            SourceMemoryEntity(
                titleId = sourceMemoryTitleId(title),
                addonId = addonId,
                quality = quality,
                service = service,
                successes = if (succeeded) 1 else 0,
                failures = if (succeeded) 0 else 1,
                lastResultAt = System.currentTimeMillis()
            )
        )
    }

    private fun sourceMemoryTitleId(title: MediaItem): String =
        hashSourceMemoryId("${title.type.name}:${title.imdbId ?: title.id}")

    private fun sourceMemoryKey(source: StreamSource): Triple<String, String, String> {
        val addonId = source.addonId?.let(::hashSourceMemoryId) ?: source.origin.name
        val service = when (source.origin) {
            SourceOrigin.TORBOX, SourceOrigin.REAL_DEBRID, SourceOrigin.ALL_DEBRID,
            SourceOrigin.PREMIUMIZE -> source.origin.name
            else -> ""
        }
        return Triple(addonId, sourceQuality(source), service)
    }

    private fun sourceQuality(source: StreamSource): String =
        source.qualityBadges.firstOrNull {
            it == "4K" || it == "1080p" || it == "720p" || it == "480p"
        }.orEmpty()

    private fun hashSourceMemoryId(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: Flow<List<WatchProgress>> = profileId.flatMapLatest { historyDao.observeRecent(it) }.map { list ->
        list.map {
            WatchProgress(
                contentId = it.contentId,
                title = it.title,
                type = MediaType.fromStremio(it.mediaType),
                posterUrl = it.posterUrl,
                backdropUrl = it.backdropUrl,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                episodeId = it.episodeId,
                episodeLabel = it.episodeLabel,
                lastStreamUrl = it.lastStreamUrl,
                updatedAt = it.updatedAt
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val favorites: Flow<List<MediaItem>> = profileId.flatMapLatest { favoriteDao.observeAll(it) }.map { list ->
        list.map {
            MediaItem(
                id = it.contentId,
                title = it.title,
                type = MediaType.fromStremio(it.mediaType),
                posterUrl = it.posterUrl,
                backdropUrl = it.backdropUrl,
                year = it.year,
                rating = it.rating,
                origin = runCatching { SourceOrigin.valueOf(it.origin) }.getOrDefault(SourceOrigin.ADDON),
                sourceId = it.sourceId
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeIsFavorite(contentId: String): Flow<Boolean> =
        profileId.flatMapLatest { favoriteDao.observeIsFavorite(it, contentId) }

    suspend fun toggleFavorite(item: MediaItem, currentlyFavorite: Boolean) {
        val pid = currentProfileId()
        if (currentlyFavorite) {
            favoriteDao.delete(pid, item.id)
            // Lets account sync remove it on the other devices too.
            tombstoneDao.upsert(TombstoneEntity(KIND_FAVORITE, pid, item.id, System.currentTimeMillis()))
        } else {
            tombstoneDao.delete(KIND_FAVORITE, pid, item.id)
            favoriteDao.upsert(
                FavoriteEntity(
                    profileId = pid,
                    contentId = item.id,
                    title = item.title,
                    mediaType = item.type.stremioValue,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    year = item.year,
                    rating = item.rating,
                    origin = item.origin.name,
                    sourceId = item.sourceId,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveWatchProgress(progress: WatchProgress) {
        if (progress.durationMs <= 0L) return
        historyDao.upsert(
            WatchHistoryEntity(
                profileId = currentProfileId(),
                contentId = progress.contentId,
                title = progress.title,
                mediaType = progress.type.stremioValue,
                posterUrl = progress.posterUrl,
                backdropUrl = progress.backdropUrl,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                episodeId = progress.episodeId,
                episodeLabel = progress.episodeLabel,
                lastStreamUrl = progress.lastStreamUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedPositionMs(contentId: String): Long =
        historyDao.getById(currentProfileId(), contentId)?.positionMs ?: 0L

    suspend fun getSavedWatchProgress(contentId: String): WatchProgress? =
        historyDao.getById(currentProfileId(), contentId)?.let {
            WatchProgress(
                contentId = it.contentId,
                title = it.title,
                type = MediaType.fromStremio(it.mediaType),
                posterUrl = it.posterUrl,
                backdropUrl = it.backdropUrl,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                episodeId = it.episodeId,
                episodeLabel = it.episodeLabel,
                lastStreamUrl = it.lastStreamUrl,
                updatedAt = it.updatedAt
            )
        }

    /** Removes a deleted profile's watch history and favorites. */
    suspend fun deleteProfileData(profileId: String) {
        historyDao.deleteAllForProfile(profileId)
        favoriteDao.deleteAllForProfile(profileId)
    }

    suspend fun loadHomeCatalogs(hiddenCatalogKeys: Set<String> = emptySet()): List<CatalogRow> {
        var latest = emptyList<CatalogRow>()
        loadHomeCatalogsProgressive(hiddenCatalogKeys) { rows, _ -> latest = rows }
        return latest
    }

    /**
     * Fetches the first ~8 addon catalogs (plus servers and TMDB) first so Home appears quickly,
     * then streams the remaining addon catalogs in background batches in exact user order
     * (addon priority -> manifest catalog order) with no total cap.
     */
    suspend fun loadHomeCatalogsProgressive(
        hiddenCatalogKeys: Set<String> = emptySet(),
        onRowsUpdated: (rows: List<CatalogRow>, isComplete: Boolean) -> Unit
    ) {
        val started = SystemClock.elapsedRealtime()
        val specs = addonRepository.planHomeCatalogSpecs(hiddenCatalogKeys)
        val firstSpecs = specs.take(AddonRepository.INITIAL_HOME_BATCH_SIZE)
        val remainingChunks = specs.drop(AddonRepository.INITIAL_HOME_BATCH_SIZE)
            .chunked(AddonRepository.BACKGROUND_HOME_BATCH_SIZE)

        coroutineScope {
            val tmdbDeferred = async { tmdbClient.getHomeCatalogRows() }
            val serversDeferred = async { serverManager.getAllCatalogRows() }
            val firstAddonsDeferred = async { addonRepository.fetchCatalogBatch(firstSpecs) }
            val allRows = (serversDeferred.await() + tmdbDeferred.await() + firstAddonsDeferred.await())
                .toMutableList()

            Log.i("QuernCatalogs", "First Home batch in ${SystemClock.elapsedRealtime() - started} ms (n=${allRows.size})")
            onRowsUpdated(allRows.toList(), remainingChunks.isEmpty())

            remainingChunks.forEachIndexed { index, chunk ->
                val batch = addonRepository.fetchCatalogBatch(chunk)
                val isComplete = index == remainingChunks.lastIndex
                if (batch.isNotEmpty() || isComplete) {
                    allRows.addAll(batch)
                    onRowsUpdated(allRows.toList(), isComplete)
                }
            }

            Log.i("QuernCatalogs", "All Home rows in ${SystemClock.elapsedRealtime() - started} ms (n=${allRows.size})")
        }
    }

    suspend fun searchEverywhere(query: String): List<MediaItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val serverTask = async { serverManager.searchAll(query) }
        val tmdbTask = async { tmdbClient.search(query) }
        val addonTask = async { addonRepository.searchCatalogs(query) }

        (serverTask.await() + tmdbTask.await() + addonTask.await())
            .distinctBy { "${it.origin}_${it.type}_${it.id}" }
    }

    private val heroMetaCache = ConcurrentHashMap<String, MediaItem>()

    /**
     * Enriches a hero item with its transparent title logo, runtime, and genres from Stremio meta
     * addons (or TMDB `/images` if the user configured a TMDB key). Cached per session.
     */
    suspend fun resolveHeroMeta(item: MediaItem): MediaItem {
        val key = "${item.type.stremioValue}_${item.id}"
        heroMetaCache[key]?.let { return it }
        if (!item.logoUrl.isNullOrBlank() && !item.runtime.isNullOrBlank() && item.genres.isNotEmpty()) {
            heroMetaCache[key] = item
            return item
        }
        var enriched = item
        // 1. Stremio meta resource (gives `logo`, `runtime`, `genres`, `description`, `background`)
        if (item.origin == SourceOrigin.ADDON || !item.imdbId.isNullOrBlank()) {
            val lookupId = item.imdbId ?: item.id
            val detail = runCatching { addonRepository.fetchMeta(item.type, lookupId) }.getOrNull()
            if (detail != null) {
                val m = detail.item
                enriched = enriched.copy(
                    logoUrl = enriched.logoUrl ?: m.logoUrl,
                    runtime = enriched.runtime ?: detail.runtime ?: m.runtime,
                    genres = if (enriched.genres.isNotEmpty()) enriched.genres else m.genres,
                    overview = enriched.overview?.takeIf { it.isNotBlank() } ?: m.overview,
                    backdropUrl = enriched.backdropUrl?.takeIf { it.isNotBlank() } ?: m.backdropUrl,
                    rating = enriched.rating?.takeIf { it.isNotBlank() } ?: m.rating,
                    year = enriched.year?.takeIf { it.isNotBlank() } ?: m.year
                )
            }
        }
        // 2. TMDB `/images` fallback when a user TMDB key is configured and logo is still missing
        if (enriched.logoUrl.isNullOrBlank()) {
            val tmdbLogo = tmdbClient.fetchLogoUrl(enriched.tmdbId, enriched.imdbId, enriched.type)
            if (!tmdbLogo.isNullOrBlank()) {
                enriched = enriched.copy(logoUrl = tmdbLogo)
            }
        }
        if (heroMetaCache.size > 120) heroMetaCache.clear()
        heroMetaCache[key] = enriched
        return enriched
    }

    suspend fun loadDetail(item: MediaItem): MediaDetail {
        // 1. Personal Media Server item (Jellyfin / Emby / Plex / WebDAV)
        if (!item.sourceId.isNullOrBlank() && item.origin in setOf(
                SourceOrigin.JELLYFIN, SourceOrigin.EMBY, SourceOrigin.PLEX, SourceOrigin.WEBDAV
            )
        ) {
            serverManager.getDetail(item.sourceId, item.id)?.let { return it }
        }

        // 2. TMDB item
        if (item.tmdbId != null) {
            tmdbClient.getDetail(item.tmdbId, item.type)?.let { return it }
        }

        // 3. Stremio Addon Protocol meta
        addonRepository.fetchMeta(item.type, item.id)?.let { return it }

        // Fallback to preview data
        return MediaDetail(item = item)
    }

    suspend fun loadStreamsAndSubtitles(
        detail: MediaDetail,
        episodeId: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> = coroutineScope {
        val item = detail.item
        val targetId = episodeId ?: item.imdbId ?: item.id

        val serverStreamsTask = async {
            if (!item.sourceId.isNullOrBlank() && item.origin in setOf(
                    SourceOrigin.JELLYFIN, SourceOrigin.EMBY, SourceOrigin.PLEX, SourceOrigin.WEBDAV
                )
            ) {
                serverManager.getStreams(item.sourceId, episodeId ?: item.id)
            } else {
                emptyList()
            }
        }

        val addonLookupId = when {
            episodeId != null && !episodeId.startsWith("srv:") -> episodeId
            !item.imdbId.isNullOrBlank() -> item.imdbId
            !item.id.startsWith("srv:") && !item.id.startsWith("dav:") -> item.id
            else -> null
        }

        val addonStreamsTask = async {
            if (addonLookupId != null) {
                addonRepository.fetchAggregatedStreams(item.type, addonLookupId)
            } else {
                emptyList()
            }
        }

        val subtitlesTask = async {
            if (addonLookupId != null) {
                addonRepository.fetchAggregatedSubtitles(item.type, addonLookupId)
            } else {
                emptyList()
            }
        }

        val allStreams = serverStreamsTask.await() + addonStreamsTask.await()
        val embeddedSubs = allStreams.flatMap { it.subtitles }
        val allSubtitles = (subtitlesTask.await() + embeddedSubs).distinctBy { it.url }

        allStreams to allSubtitles
    }

    /**
     * Loads streams progressively as each server/addon responds without blocking on subtitles
     * or the slowest addon.
     */
    suspend fun loadStreamsAndSubtitlesProgressive(
        detail: MediaDetail,
        episodeId: String? = null,
        onStreamsUpdated: (List<StreamSource>) -> Unit,
        onSubtitlesUpdated: (List<SubtitleTrack>) -> Unit
    ) = coroutineScope {
        val item = detail.item
        val mutex = Mutex()
        var serverStreams: List<StreamSource> = emptyList()
        var addonStreams: List<StreamSource> = emptyList()
        var externalSubs: List<SubtitleTrack> = emptyList()

        suspend fun emitMergedStreams() {
            val merged = mutex.withLock { serverStreams + addonStreams }
            onStreamsUpdated(merged)
            val embeddedSubs = merged.flatMap { it.subtitles }
            if (embeddedSubs.isNotEmpty()) {
                val mergedSubs = mutex.withLock { (externalSubs + embeddedSubs).distinctBy { it.url } }
                onSubtitlesUpdated(mergedSubs)
            }
        }

        val addonLookupId = when {
            episodeId != null && !episodeId.startsWith("srv:") -> episodeId
            !item.imdbId.isNullOrBlank() -> item.imdbId
            !item.id.startsWith("srv:") && !item.id.startsWith("dav:") -> item.id
            else -> null
        }

        val serverJob = launch {
            if (!item.sourceId.isNullOrBlank() && item.origin in setOf(
                    SourceOrigin.JELLYFIN, SourceOrigin.EMBY, SourceOrigin.PLEX, SourceOrigin.WEBDAV
                )
            ) {
                val res = withTimeoutOrNull(6_000L) {
                    runCatching { serverManager.getStreams(item.sourceId, episodeId ?: item.id) }.getOrDefault(emptyList())
                }.orEmpty()
                if (res.isNotEmpty()) {
                    mutex.withLock { serverStreams = res }
                    emitMergedStreams()
                }
            }
        }

        val addonJob = launch {
            if (addonLookupId != null) {
                addonRepository.fetchStreamsProgressive(item.type, addonLookupId) { latest ->
                    mutex.withLock { addonStreams = latest }
                    emitMergedStreams()
                }
            }
        }

        val subsJob = launch {
            if (addonLookupId != null) {
                val subs = runCatching { addonRepository.fetchAggregatedSubtitles(item.type, addonLookupId) }
                    .getOrDefault(emptyList())
                if (subs.isNotEmpty()) {
                    val mergedSubs = mutex.withLock {
                        externalSubs = subs
                        (externalSubs + (serverStreams + addonStreams).flatMap { it.subtitles }).distinctBy { it.url }
                    }
                    onSubtitlesUpdated(mergedSubs)
                }
            }
        }

        serverJob.join()
        addonJob.join()
        // Wait briefly for subtitles only if already finishing; streams are already shown
        subsJob.join()
    }
}
