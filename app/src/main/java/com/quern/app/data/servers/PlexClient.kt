package com.quern.app.data.servers

import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
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

/**
 * Plex Media Server client using X-Plex-Token and Plex JSON API.
 */
class PlexClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : MediaServerClient {

    override suspend fun testConnection(config: MediaServerConfig): ServerTestResult = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        if (base.isBlank() || config.apiKeyOrToken.isBlank()) {
            return@withContext ServerTestResult(false, "Server URL and X-Plex-Token are required")
        }
        runCatching {
            val req = plexRequest("$base/", config.apiKeyOrToken)
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ServerTestResult(false, "HTTP ${resp.code}: Check Plex URL & Token")
                val container = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["MediaContainer"]?.jsonObject
                val friendlyName = container?.get("friendlyName")?.jsonPrimitive?.contentOrNull ?: config.name
                ServerTestResult(true, "Connected to Plex: $friendlyName")
            }
        }.getOrElse { ServerTestResult(false, it.message ?: "Connection failed") }
    }

    override suspend fun getCatalogRows(config: MediaServerConfig): List<CatalogRow> = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        if (base.isBlank() || token.isBlank()) return@withContext emptyList()

        val rows = mutableListOf<CatalogRow>()
        runCatching {
            val req = plexRequest("$base/library/recentlyAdded", token)
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["MediaContainer"]?.jsonObject?.get("Metadata") as? JsonArray
                val items = parseMetadataArray(arr, base, token, config.id)
                if (items.isNotEmpty()) {
                    rows += CatalogRow(
                        id = "plex_${config.id}_recent",
                        title = "${config.name} • Recently Added",
                        origin = SourceOrigin.PLEX,
                        items = items
                    )
                }
            }
        }
        rows
    }

    override suspend fun search(config: MediaServerConfig, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        if (base.isBlank() || token.isBlank() || query.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        runCatching {
            val req = plexRequest("$base/search?query=$encoded", token)
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["MediaContainer"]?.jsonObject?.get("Metadata") as? JsonArray
                parseMetadataArray(arr, base, token, config.id)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getDetail(config: MediaServerConfig, itemId: String): MediaDetail? = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        val ratingKey = itemId.substringAfterLast(":")
        runCatching {
            val req = plexRequest("$base/library/metadata/$ratingKey", token)
            val metaObj = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["MediaContainer"]?.jsonObject
                    ?.get("Metadata")?.jsonArray?.firstOrNull()?.jsonObject
            } ?: return@withContext null

            val item = parseMetadataObject(metaObj, base, token, config.id) ?: return@withContext null
            val episodes = if (item.type == MediaType.SERIES) {
                fetchAllLeaves(base, token, ratingKey, config.id)
            } else {
                emptyList()
            }
            MediaDetail(item = item, episodes = episodes)
        }.getOrNull()
    }

    override suspend fun getStreams(config: MediaServerConfig, itemId: String): List<StreamSource> =
        withContext(Dispatchers.IO) {
            val base = config.baseUrl.trimEnd('/')
            val token = config.apiKeyOrToken
            val ratingKey = itemId.substringAfterLast(":")
            runCatching {
                val req = plexRequest("$base/library/metadata/$ratingKey", token)
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val meta = json.parseToJsonElement(resp.body?.string().orEmpty())
                        .jsonObject["MediaContainer"]?.jsonObject
                        ?.get("Metadata")?.jsonArray?.firstOrNull()?.jsonObject
                        ?: return@withContext emptyList()
                    val mediaArr = meta["Media"] as? JsonArray ?: return@withContext emptyList()
                    mediaArr.mapIndexedNotNull { idx, elem ->
                        val mediaObj = elem as? JsonObject ?: return@mapIndexedNotNull null
                        val partKey = mediaObj["Part"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull
                            ?: return@mapIndexedNotNull null
                        val res = mediaObj["videoResolution"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val codec = mediaObj["videoCodec"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val badges = listOfNotNull("Plex", res, codec)
                        StreamSource(
                            id = "${config.id}_plex_${ratingKey}_$idx",
                            title = "${config.name} • Direct Stream ${res ?: ""}".trim(),
                            subtitle = "Original Plex media file",
                            url = "$base$partKey?X-Plex-Token=$token",
                            headers = mapOf("X-Plex-Token" to token),
                            providerName = config.name,
                            origin = SourceOrigin.PLEX,
                            qualityBadges = badges,
                            isCached = true
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    private fun fetchAllLeaves(base: String, token: String, ratingKey: String, serverId: String): List<Episode> =
        runCatching {
            val req = plexRequest("$base/library/metadata/$ratingKey/allLeaves", token)
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["MediaContainer"]?.jsonObject?.get("Metadata") as? JsonArray
                    ?: return emptyList()
                arr.mapNotNull { elem ->
                    val obj = elem as? JsonObject ?: return@mapNotNull null
                    val epKey = obj["ratingKey"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val thumb = obj["thumb"]?.jsonPrimitive?.contentOrNull?.let { "$base$it?X-Plex-Token=$token" }
                    Episode(
                        id = "srv:$serverId:$epKey",
                        seasonNumber = obj["parentIndex"]?.jsonPrimitive?.intOrNull ?: 1,
                        episodeNumber = obj["index"]?.jsonPrimitive?.intOrNull ?: 1,
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Episode",
                        overview = obj["summary"]?.jsonPrimitive?.contentOrNull,
                        thumbnailUrl = thumb
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun parseMetadataArray(arr: JsonArray?, base: String, token: String, serverId: String): List<MediaItem> =
        arr?.mapNotNull { (it as? JsonObject)?.let { obj -> parseMetadataObject(obj, base, token, serverId) } }.orEmpty()

    private fun parseMetadataObject(obj: JsonObject, base: String, token: String, serverId: String): MediaItem? {
        val ratingKey = obj["ratingKey"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val typeRaw = obj["type"]?.jsonPrimitive?.contentOrNull
        val type = when (typeRaw) {
            "show", "season", "episode" -> MediaType.SERIES
            else -> MediaType.MOVIE
        }
        val thumb = obj["thumb"]?.jsonPrimitive?.contentOrNull?.let { "$base$it?X-Plex-Token=$token" }
        val art = obj["art"]?.jsonPrimitive?.contentOrNull?.let { "$base$it?X-Plex-Token=$token" }
        return MediaItem(
            id = "srv:$serverId:$ratingKey",
            title = title,
            type = type,
            posterUrl = thumb,
            backdropUrl = art ?: thumb,
            overview = obj["summary"]?.jsonPrimitive?.contentOrNull,
            year = obj["year"]?.jsonPrimitive?.contentOrNull,
            rating = obj["rating"]?.jsonPrimitive?.doubleOrNull?.let { String.format("%.1f", it) },
            origin = SourceOrigin.PLEX,
            sourceId = serverId
        )
    }

    private fun plexRequest(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("X-Plex-Token", token)
            .header("Accept", "application/json")
            .build()
}
