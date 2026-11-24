package com.quern.app.data.metadata

import com.quern.app.data.local.SecureVault
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.TmdbConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB v3 API Client.
 * Supports both v3 API Key and v4 Bearer Read Access Token, Arabic/English metadata,
 * trending catalogs, search, full season/episode fetching, and IMDb external_id mapping
 * so TMDB items seamlessly fetch streams from Stremio addons!
 */
class TmdbClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val vault: SecureVault
) {
    private val logoCache = ConcurrentHashMap<String, String>()

    fun getConfig(): TmdbConfig = TmdbConfig(
        apiKey = vault.getString(KEY_API),
        language = vault.getString(KEY_LANG, "en-US")
    )

    /**
     * Fetches the transparent title logo PNG from TMDB `/images` when a user TMDB API key is
     * configured. Results (including misses) are cached in memory for the session.
     */
    suspend fun fetchLogoUrl(tmdbId: Int?, imdbId: String?, type: MediaType): String? = withContext(Dispatchers.IO) {
        val cfg = getConfig()
        if (!cfg.isConfigured) return@withContext null
        val cacheKey = "${type.stremioValue}_${tmdbId ?: imdbId.orEmpty()}"
        if (cacheKey.endsWith("_")) return@withContext null
        logoCache[cacheKey]?.let { return@withContext it.ifBlank { null } }

        val pathType = if (type == MediaType.SERIES) "tv" else "movie"
        val resolvedTmdbId = tmdbId ?: run {
            val id = imdbId?.takeIf { it.startsWith("tt") } ?: return@run null
            runCatching {
                val req = buildRequest("/find/$id?external_source=imdb_id", cfg.apiKey, cfg.language)
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val key = if (type == MediaType.SERIES) "tv_results" else "movie_results"
                    (obj[key] as? JsonArray)?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                }
            }.getOrNull()
        } ?: run {
            logoCache[cacheKey] = ""
            return@withContext null
        }

        val langPrefix = cfg.language.substringBefore("-").lowercase(Locale.US).ifBlank { "en" }
        val found = runCatching {
            val req = buildRequest(
                "/$pathType/$resolvedTmdbId/images?include_image_language=$langPrefix,en,null",
                cfg.apiKey,
                cfg.language
            )
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val logos = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["logos"] as? JsonArray ?: return@use null
                val candidates = logos.mapNotNull { it as? JsonObject }
                    .filter { !(it["file_path"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) }
                val chosen = candidates.firstOrNull {
                    it["iso_639_1"]?.jsonPrimitive?.contentOrNull.equals(langPrefix, ignoreCase = true)
                } ?: candidates.firstOrNull {
                    it["iso_639_1"]?.jsonPrimitive?.contentOrNull.equals("en", ignoreCase = true)
                } ?: candidates.firstOrNull()
                chosen?.get("file_path")?.jsonPrimitive?.contentOrNull?.let { "$IMG_LOGO$it" }
            }
        }.getOrNull()

        logoCache[cacheKey] = found.orEmpty()
        found
    }

    fun saveConfig(config: TmdbConfig) {
        vault.putString(KEY_API, config.apiKey.trim())
        vault.putString(KEY_LANG, config.language.ifBlank { "en-US" })
    }

    suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("TMDB API Key is required"))
        runCatching {
            val req = buildRequest("/configuration", trimmed, "en-US")
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: Invalid TMDB API key / token")
                "TMDB Connected Successfully"
            }
        }
    }

    suspend fun getHomeCatalogRows(): List<CatalogRow> = withContext(Dispatchers.IO) {
        val cfg = getConfig()
        if (!cfg.isConfigured) return@withContext emptyList()
        val rows = mutableListOf<CatalogRow>()

        fetchList("/trending/movie/week", MediaType.MOVIE, cfg).takeIf { it.isNotEmpty() }?.let {
            rows += CatalogRow("tmdb_trending_movies", "Trending Movies • TMDB", SourceOrigin.TMDB, it)
        }
        fetchList("/trending/tv/week", MediaType.SERIES, cfg).takeIf { it.isNotEmpty() }?.let {
            rows += CatalogRow("tmdb_trending_tv", "Trending Series • TMDB", SourceOrigin.TMDB, it)
        }
        fetchList("/movie/top_rated", MediaType.MOVIE, cfg).takeIf { it.isNotEmpty() }?.let {
            rows += CatalogRow("tmdb_top_movies", "Top Rated Movies • TMDB", SourceOrigin.TMDB, it)
        }
        rows
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val cfg = getConfig()
        if (!cfg.isConfigured || query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        runCatching {
            val req = buildRequest("/search/multi?query=$encoded&include_adult=false", cfg.apiKey, cfg.language)
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val results = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["results"] as? JsonArray ?: return@withContext emptyList()
                results.mapNotNull { elem ->
                    val obj = elem as? JsonObject ?: return@mapNotNull null
                    val mediaTypeRaw = obj["media_type"]?.jsonPrimitive?.contentOrNull
                    val type = when (mediaTypeRaw) {
                        "movie" -> MediaType.MOVIE
                        "tv" -> MediaType.SERIES
                        else -> return@mapNotNull null
                    }
                    parseTmdbItem(obj, type)
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getDetail(tmdbId: Int, type: MediaType): MediaDetail? = withContext(Dispatchers.IO) {
        val cfg = getConfig()
        if (!cfg.isConfigured) return@withContext null
        val pathType = if (type == MediaType.SERIES) "tv" else "movie"
        runCatching {
            val req = buildRequest("/$pathType/$tmdbId?append_to_response=external_ids,credits", cfg.apiKey, cfg.language)
            val obj = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }
            val imdbId = obj["external_ids"]?.jsonObject?.get("imdb_id")?.jsonPrimitive?.contentOrNull
                ?: obj["imdb_id"]?.jsonPrimitive?.contentOrNull
            val baseItem = parseTmdbItem(obj, type)?.copy(imdbId = imdbId) ?: return@withContext null

            val runtime = obj["runtime"]?.jsonPrimitive?.intOrNull?.let { "${it} min" }
            val cast = obj["credits"]?.jsonObject?.get("cast")?.jsonArray
                ?.take(10)
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()

            val episodes = if (type == MediaType.SERIES) {
                val seasonsCount = obj["number_of_seasons"]?.jsonPrimitive?.intOrNull ?: 0
                val epList = mutableListOf<Episode>()
                for (seasonNum in 1..minOf(seasonsCount, 15)) {
                    epList += fetchSeasonEpisodes(tmdbId, seasonNum, imdbId, cfg)
                }
                epList
            } else {
                emptyList()
            }

            MediaDetail(
                item = baseItem,
                runtime = runtime,
                cast = cast,
                episodes = episodes
            )
        }.getOrNull()
    }

    private fun fetchSeasonEpisodes(
        tmdbId: Int,
        seasonNumber: Int,
        imdbId: String?,
        cfg: TmdbConfig
    ): List<Episode> = runCatching {
        val req = buildRequest("/tv/$tmdbId/season/$seasonNumber", cfg.apiKey, cfg.language)
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["episodes"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val epNum = obj["episode_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val idPrefix = imdbId ?: "tmdb:$tmdbId"
                val stillPath = obj["still_path"]?.jsonPrimitive?.contentOrNull
                Episode(
                    id = "$idPrefix:$seasonNumber:$epNum",
                    seasonNumber = seasonNumber,
                    episodeNumber = epNum,
                    title = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Episode $epNum",
                    overview = obj["overview"]?.jsonPrimitive?.contentOrNull,
                    thumbnailUrl = stillPath?.let { "$IMG_BACKDROP$it" },
                    airDate = obj["air_date"]?.jsonPrimitive?.contentOrNull,
                    runtimeMinutes = obj["runtime"]?.jsonPrimitive?.intOrNull
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun fetchList(path: String, type: MediaType, cfg: TmdbConfig): List<MediaItem> = runCatching {
        val req = buildRequest(path, cfg.apiKey, cfg.language)
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["results"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { (it as? JsonObject)?.let { obj -> parseTmdbItem(obj, type) } }
        }
    }.getOrDefault(emptyList())

    private fun parseTmdbItem(obj: JsonObject, type: MediaType): MediaItem? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val poster = obj["poster_path"]?.jsonPrimitive?.contentOrNull?.let { "$IMG_POSTER$it" }
        val backdrop = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull?.let { "$IMG_BACKDROP$it" }
        val date = obj["release_date"]?.jsonPrimitive?.contentOrNull
            ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
        val vote = obj["vote_average"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 }
            ?.let { String.format(Locale.US, "%.1f", it) }

        return MediaItem(
            id = "tmdb:${type.stremioValue}:$id",
            title = title,
            type = type,
            posterUrl = poster,
            backdropUrl = backdrop ?: poster,
            overview = obj["overview"]?.jsonPrimitive?.contentOrNull,
            year = date?.take(4),
            releaseDate = date?.take(10)?.takeIf { it.length == 10 },
            rating = vote,
            tmdbId = id,
            origin = SourceOrigin.TMDB
        )
    }

    private fun buildRequest(path: String, apiKey: String, language: String): Request {
        val separator = if ('?' in path) '&' else '?'
        val isBearer = apiKey.length > 45
        val url = if (isBearer) {
            "$BASE_URL$path${separator}language=$language"
        } else {
            "$BASE_URL$path${separator}api_key=$apiKey&language=$language"
        }
        return Request.Builder().url(url).apply {
            if (isBearer) header("Authorization", "Bearer $apiKey")
            header("Accept", "application/json")
        }.build()
    }

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMG_POSTER = "https://image.tmdb.org/t/p/w500"
        private const val IMG_LOGO = "https://image.tmdb.org/t/p/w500"
        private const val IMG_BACKDROP = "https://image.tmdb.org/t/p/w1280"
        private const val KEY_API = "tmdb_api_key"
        private const val KEY_LANG = "tmdb_lang"
    }
}
