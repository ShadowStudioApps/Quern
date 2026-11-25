package com.quern.app.data.addons

import com.quern.app.domain.model.AddonManifest
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.StremioCatalogResponse
import com.quern.app.domain.model.StremioMetaResponse
import com.quern.app.domain.model.StremioStreamResponse
import com.quern.app.domain.model.StremioSubtitlesResponse
import com.quern.app.domain.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Implements the open Stremio Addon Protocol v3 (manifest, catalog, meta, stream, subtitles).
 */
class StremioAddonClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) {

    fun normalizeManifestUrl(rawUrl: String): Pair<String, String> {
        var cleaned = rawUrl.trim()
        if (cleaned.startsWith("stremio://", ignoreCase = true)) {
            cleaned = "https://" + cleaned.removePrefix("stremio://").removePrefix("STREMIO://")
        }
        if (!cleaned.startsWith("http://", ignoreCase = true) && !cleaned.startsWith("https://", ignoreCase = true)) {
            cleaned = "https://$cleaned"
        }
        val manifestUrl = if (cleaned.endsWith("/manifest.json", ignoreCase = true)) {
            cleaned
        } else {
            cleaned.trimEnd('/') + "/manifest.json"
        }
        val baseUrl = manifestUrl.removeSuffix("/manifest.json")
        return manifestUrl to baseUrl
    }

    suspend fun fetchManifest(rawUrl: String, timeoutMs: Long? = null): Pair<InstalledAddon, String> = withContext(Dispatchers.IO) {
        val (manifestUrl, baseUrl) = normalizeManifestUrl(rawUrl)
        val req = Request.Builder().url(manifestUrl).build()
        val call = httpClient.newCall(req)
        if (timeoutMs != null) call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        call.execute().use { resp ->
            if (!resp.isSuccessful) error("Failed to fetch addon manifest (HTTP ${resp.code})")
            val body = resp.body?.string().orEmpty()
            val manifest = json.decodeFromString<AddonManifest>(body)
            val installed = InstalledAddon(
                manifestUrl = manifestUrl,
                baseUrl = baseUrl,
                manifest = manifest,
                enabled = true
            )
            installed to body
        }
    }

    suspend fun fetchCatalog(
        addon: InstalledAddon,
        type: String,
        catalogId: String,
        search: String? = null,
        genre: String? = null,
        skip: Int? = null,
        extraParams: Map<String, String> = emptyMap()
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        fun encodeSegmentPart(raw: String): String =
            URLEncoder.encode(raw, "UTF-8").replace("+", "%20")

        val extras = buildList {
            if (!search.isNullOrBlank()) add("search=${encodeSegmentPart(search)}")
            if (!genre.isNullOrBlank() && "genre" !in extraParams) add("genre=${encodeSegmentPart(genre)}")
            extraParams.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    add("${encodeSegmentPart(k)}=${encodeSegmentPart(v)}")
                }
            }
            if (skip != null && skip > 0) add("skip=$skip")
        }
        val extraSegment = if (extras.isEmpty()) "" else "/${extras.joinToString("&")}"
        val encodedCatalog = encodeSegmentPart(catalogId)
        val url = "${addon.baseUrl}/catalog/$type/$encodedCatalog$extraSegment.json"

        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val parsed = json.decodeFromString<StremioCatalogResponse>(resp.body?.string().orEmpty())
                parsed.metas.distinctBy { "${it.type}_${it.id}" }.map { dto ->
                    MediaItem(
                        id = dto.id,
                        title = dto.name,
                        type = MediaType.fromStremio(dto.type),
                        posterUrl = dto.poster,
                        backdropUrl = dto.background ?: dto.poster,
                        logoUrl = dto.logo?.takeIf { it.isNotBlank() },
                        overview = dto.description,
                        year = dto.releaseInfo,
                        releaseDate = Regex("""\d{4}-\d{2}-\d{2}""").find(dto.released ?: dto.releaseInfo.orEmpty())?.value,
                        runtime = dto.runtime?.takeIf { it.isNotBlank() },
                        rating = dto.imdbRating,
                        imdbId = dto.id.takeIf { it.startsWith("tt") },
                        origin = SourceOrigin.ADDON,
                        sourceId = addon.baseUrl,
                        genres = dto.genres
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchMeta(addon: InstalledAddon, type: MediaType, id: String): MediaDetail? =
        withContext(Dispatchers.IO) {
            if (!addon.manifest.supportsResource("meta", type.stremioValue, id)) return@withContext null
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val url = "${addon.baseUrl}/meta/${type.stremioValue}/$encodedId.json"
            runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val parsed = json.decodeFromString<StremioMetaResponse>(resp.body?.string().orEmpty())
                    val meta = parsed.meta ?: return@withContext null
                    val item = MediaItem(
                        id = meta.id,
                        title = meta.name,
                        type = MediaType.fromStremio(meta.type),
                        posterUrl = meta.poster,
                        backdropUrl = meta.background ?: meta.poster,
                        logoUrl = meta.logo?.takeIf { it.isNotBlank() },
                        overview = meta.description,
                        year = meta.releaseInfo,
                        runtime = meta.runtime?.takeIf { it.isNotBlank() },
                        rating = meta.imdbRating,
                        imdbId = meta.id.takeIf { it.startsWith("tt") },
                        origin = SourceOrigin.ADDON,
                        sourceId = addon.baseUrl,
                        genres = meta.genres
                    )
                    val episodes = meta.videos.mapNotNull { vid ->
                        val s = vid.season ?: return@mapNotNull null
                        val e = vid.episode ?: vid.number ?: return@mapNotNull null
                        Episode(
                            id = vid.id,
                            seasonNumber = s,
                            episodeNumber = e,
                            title = vid.title ?: vid.name ?: "Episode $e",
                            overview = vid.overview,
                            thumbnailUrl = vid.thumbnail,
                            airDate = vid.released?.take(10)
                        )
                    }.distinctBy { it.id }
                    MediaDetail(
                        item = item,
                        runtime = meta.runtime,
                        cast = meta.cast,
                        director = meta.director,
                        episodes = episodes
                    )
                }
            }.getOrNull()
        }

    suspend fun fetchStreams(addon: InstalledAddon, type: MediaType, id: String): List<StreamSource> =
        withContext(Dispatchers.IO) {
            if (!addon.manifest.supportsResource("stream", type.stremioValue, id)) return@withContext emptyList()
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val url = "${addon.baseUrl}/stream/${type.stremioValue}/$encodedId.json"
            runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val parsed = json.decodeFromString<StremioStreamResponse>(resp.body?.string().orEmpty())
                    parsed.streams.mapIndexedNotNull { idx, dto ->
                        val playableUrl = dto.url?.takeIf { it.isNotBlank() }
                        val hash = dto.infoHash?.takeIf { it.isNotBlank() }
                        if (playableUrl == null && hash == null) return@mapIndexedNotNull null

                        val rawTitle = dto.title ?: dto.description ?: dto.behaviorHints?.filename ?: addon.manifest.name
                        val headerName = dto.name ?: addon.manifest.name
                        val badges = extractQualityBadges("$headerName $rawTitle")
                        val subs = dto.subtitles.mapIndexed { sIdx, sub ->
                            SubtitleTrack(
                                id = sub.id ?: "${addon.manifest.id}_sub_$sIdx",
                                url = sub.url,
                                language = sub.lang,
                                label = sub.lang.uppercase()
                            )
                        }
                        StreamSource(
                            id = "${addon.manifest.id}_${id}_$idx",
                            title = headerName.replace('\n', ' ').trim(),
                            subtitle = rawTitle.trim(),
                            url = playableUrl,
                            infoHash = hash,
                            fileIdx = dto.fileIdx,
                            magnetUri = hash?.let { "magnet:?xt=urn:btih:$it" },
                            headers = dto.behaviorHints?.proxyHeaders?.request.orEmpty(),
                            providerName = addon.manifest.name,
                            origin = SourceOrigin.ADDON,
                            qualityBadges = badges,
                            isCached = playableUrl != null,
                            sizeBytes = dto.behaviorHints?.videoSize,
                            subtitles = subs,
                            addonId = addon.manifest.id
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun fetchSubtitles(addon: InstalledAddon, type: MediaType, id: String): List<SubtitleTrack> =
        withContext(Dispatchers.IO) {
            if (!addon.manifest.supportsResource("subtitles", type.stremioValue, id)) return@withContext emptyList()
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val url = "${addon.baseUrl}/subtitles/${type.stremioValue}/$encodedId.json"
            runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val parsed = json.decodeFromString<StremioSubtitlesResponse>(resp.body?.string().orEmpty())
                    parsed.subtitles.mapIndexed { idx, sub ->
                        SubtitleTrack(
                            id = sub.id ?: "${addon.manifest.id}_extsub_$idx",
                            url = sub.url,
                            language = sub.lang,
                            label = "${sub.lang.uppercase()} • ${addon.manifest.name}"
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    private fun extractQualityBadges(text: String): List<String> {
        val badges = mutableListOf<String>()
        val upper = text.uppercase()
        when {
            "2160P" in upper || "4K" in upper || "UHD" in upper -> badges += "4K"
            "1080P" in upper -> badges += "1080p"
            "720P" in upper -> badges += "720p"
        }
        if ("DV" in upper || "DOLBY VISION" in upper) badges += "Dolby Vision"
        if ("HDR" in upper) badges += "HDR"
        if ("ATMOS" in upper) badges += "Atmos"
        if ("HEVC" in upper || "X265" in upper || "H265" in upper) badges += "HEVC"
        if ("REMUX" in upper) badges += "REMUX"
        return badges.distinct()
    }
}
