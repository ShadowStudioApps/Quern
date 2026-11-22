package com.quern.app.data.servers

import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Supports both Jellyfin and Emby personal media servers using either API Key or Username/Password login.
 */
class JellyfinEmbyClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : MediaServerClient {

    override suspend fun testConnection(config: MediaServerConfig): ServerTestResult = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        if (base.isBlank()) return@withContext ServerTestResult(false, "Server URL is required")

        runCatching {
            // If username + password provided without token, authenticate first
            if (config.apiKeyOrToken.isBlank() && config.username.isNotBlank()) {
                val payload = buildJsonObject {
                    put("Username", config.username)
                    put("Pw", config.password)
                }.toString().toRequestBody("application/json".toMediaType())

                val authReq = Request.Builder()
                    .url("$base/Users/AuthenticateByName")
                    .header("X-Emby-Authorization", authHeader(null))
                    .post(payload)
                    .build()

                httpClient.newCall(authReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext ServerTestResult(false, "Login failed (HTTP ${resp.code})")
                    }
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val token = root["AccessToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val userId = root["User"]?.jsonObject?.get("Id")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val serverName = config.name.ifBlank { config.type.displayName }
                    return@withContext ServerTestResult(
                        success = true,
                        message = "Connected to $serverName",
                        resolvedUserId = userId,
                        resolvedToken = token
                    )
                }
            }

            // Otherwise verify via System/Info and fetch first user if userId missing
            val req = Request.Builder()
                .url("$base/System/Info")
                .header("X-Emby-Token", config.apiKeyOrToken)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ServerTestResult(false, "HTTP ${resp.code}: Check server URL & API key")
                }
                val info = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val serverName = info["ServerName"]?.jsonPrimitive?.contentOrNull ?: config.type.displayName
                val userId = config.userId.ifBlank { fetchFirstUserId(base, config.apiKeyOrToken).orEmpty() }
                ServerTestResult(
                    success = true,
                    message = "Connected to $serverName",
                    resolvedUserId = userId
                )
            }
        }.getOrElse { ServerTestResult(false, it.message ?: "Connection failed") }
    }

    override suspend fun getCatalogRows(config: MediaServerConfig): List<CatalogRow> = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        val userId = config.userId.ifBlank { fetchFirstUserId(base, token).orEmpty() }
        if (base.isBlank() || token.isBlank() || userId.isBlank()) return@withContext emptyList()

        val origin = if (config.type == MediaServerType.EMBY) SourceOrigin.EMBY else SourceOrigin.JELLYFIN
        val rows = mutableListOf<CatalogRow>()

        runCatching {
            val req = Request.Builder()
                .url("$base/Users/$userId/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=25&Fields=Overview,ProviderIds,Genres")
                .header("X-Emby-Token", token)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val items = parseItems(root["Items"] as? JsonArray, base, token, origin, config.id)
                if (items.isNotEmpty()) {
                    rows += CatalogRow(
                        id = "server_${config.id}_latest",
                        title = "${config.name} • Recently Added",
                        origin = origin,
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
        val userId = config.userId.ifBlank { fetchFirstUserId(base, token).orEmpty() }
        if (base.isBlank() || token.isBlank() || userId.isBlank() || query.isBlank()) return@withContext emptyList()

        val origin = if (config.type == MediaServerType.EMBY) SourceOrigin.EMBY else SourceOrigin.JELLYFIN
        val encoded = URLEncoder.encode(query, "UTF-8")
        runCatching {
            val req = Request.Builder()
                .url("$base/Users/$userId/Items?SearchTerm=$encoded&IncludeItemTypes=Movie,Series&Recursive=true&Limit=20&Fields=Overview,ProviderIds")
                .header("X-Emby-Token", token)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                parseItems(root["Items"] as? JsonArray, base, token, origin, config.id)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getDetail(config: MediaServerConfig, itemId: String): MediaDetail? = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        val userId = config.userId.ifBlank { fetchFirstUserId(base, token).orEmpty() }
        val rawId = itemId.substringAfterLast(":")
        val origin = if (config.type == MediaServerType.EMBY) SourceOrigin.EMBY else SourceOrigin.JELLYFIN

        runCatching {
            val req = Request.Builder()
                .url("$base/Users/$userId/Items/$rawId")
                .header("X-Emby-Token", token)
                .build()
            val itemObj = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }
            val mediaItem = parseSingleItem(itemObj, base, token, origin, config.id) ?: return@withContext null
            val episodes = if (mediaItem.type == MediaType.SERIES) {
                fetchEpisodes(base, token, userId, rawId, config.id)
            } else {
                emptyList()
            }
            MediaDetail(item = mediaItem, episodes = episodes)
        }.getOrNull()
    }

    override suspend fun getStreams(config: MediaServerConfig, itemId: String): List<StreamSource> {
        val base = config.baseUrl.trimEnd('/')
        val token = config.apiKeyOrToken
        val rawId = itemId.substringAfterLast(":")
        if (base.isBlank() || token.isBlank() || rawId.isBlank()) return emptyList()

        val origin = if (config.type == MediaServerType.EMBY) SourceOrigin.EMBY else SourceOrigin.JELLYFIN
        val directUrl = "$base/Videos/$rawId/stream?static=true&api_key=$token"
        val hlsUrl = "$base/Videos/$rawId/master.m3u8?api_key=$token&MediaSourceId=$rawId"

        return listOf(
            StreamSource(
                id = "${config.id}_direct_$rawId",
                title = "${config.name} • Direct Play (Original Quality)",
                subtitle = "Zero-transcode native ExoPlayer stream",
                url = directUrl,
                headers = mapOf("X-Emby-Token" to token),
                providerName = config.name,
                origin = origin,
                qualityBadges = listOf(config.type.displayName, "Direct Play"),
                isCached = true
            ),
            StreamSource(
                id = "${config.id}_hls_$rawId",
                title = "${config.name} • Adaptive HLS",
                subtitle = "Adaptive bitrate stream from your server",
                url = hlsUrl,
                headers = mapOf("X-Emby-Token" to token),
                providerName = config.name,
                origin = origin,
                qualityBadges = listOf(config.type.displayName, "HLS"),
                isCached = true
            )
        )
    }

    private fun fetchFirstUserId(base: String, token: String): String? = runCatching {
        val req = Request.Builder()
            .url("$base/Users")
            .header("X-Emby-Token", token)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray ?: return null
            arr.firstOrNull()?.jsonObject?.get("Id")?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    private fun fetchEpisodes(
        base: String,
        token: String,
        userId: String,
        seriesId: String,
        serverId: String
    ): List<Episode> = runCatching {
        val req = Request.Builder()
            .url("$base/Shows/$seriesId/Episodes?UserId=$userId&Fields=Overview")
            .header("X-Emby-Token", token)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["Items"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val epId = obj["Id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Episode(
                    id = "srv:$serverId:$epId",
                    seasonNumber = obj["ParentIndexNumber"]?.jsonPrimitive?.intOrNull ?: 1,
                    episodeNumber = obj["IndexNumber"]?.jsonPrimitive?.intOrNull ?: 1,
                    title = obj["Name"]?.jsonPrimitive?.contentOrNull ?: "Episode",
                    overview = obj["Overview"]?.jsonPrimitive?.contentOrNull,
                    thumbnailUrl = "$base/Items/$epId/Images/Primary?api_key=$token"
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun parseItems(
        arr: JsonArray?,
        base: String,
        token: String,
        origin: SourceOrigin,
        serverId: String
    ): List<MediaItem> = arr?.mapNotNull { elem ->
        (elem as? JsonObject)?.let { parseSingleItem(it, base, token, origin, serverId) }
    }.orEmpty()

    private fun parseSingleItem(
        obj: JsonObject,
        base: String,
        token: String,
        origin: SourceOrigin,
        serverId: String
    ): MediaItem? {
        val rawId = obj["Id"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["Name"]?.jsonPrimitive?.contentOrNull ?: return null
        val typeRaw = obj["Type"]?.jsonPrimitive?.contentOrNull
        val type = if (typeRaw.equals("Series", ignoreCase = true)) MediaType.SERIES else MediaType.MOVIE
        val imdbId = obj["ProviderIds"]?.jsonObject?.get("Imdb")?.jsonPrimitive?.contentOrNull
        val year = obj["ProductionYear"]?.jsonPrimitive?.contentOrNull
        val rating = obj["CommunityRating"]?.jsonPrimitive?.doubleOrNull?.let { String.format("%.1f", it) }

        return MediaItem(
            id = "srv:$serverId:$rawId",
            title = name,
            type = type,
            posterUrl = "$base/Items/$rawId/Images/Primary?api_key=$token",
            backdropUrl = "$base/Items/$rawId/Images/Backdrop?api_key=$token",
            overview = obj["Overview"]?.jsonPrimitive?.contentOrNull,
            year = year,
            rating = rating,
            imdbId = imdbId,
            origin = origin,
            sourceId = serverId
        )
    }

    private fun authHeader(token: String?): String {
        val base = """MediaBrowser Client="Quern", Device="Android", DeviceId="quern-android-client", Version="1.0.0""""
        return if (token.isNullOrBlank()) base else """$base, Token="$token""""
    }
}
