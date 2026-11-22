package com.quern.app.data.servers

import android.util.Base64
import com.quern.app.data.debrid.TorBoxClient
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder

data class WebDavEntry(
    val name: String,
    val fullUrl: String,
    val isDirectory: Boolean
)

/**
 * WebDAV & Custom HTTP File Server client.
 * Supports PROPFIND directory listing (Depth: 1), Basic Auth / Bearer Token auth,
 * and passes authentication headers directly to Media3 ExoPlayer for seamless playback.
 */
class WebDavClient(
    private val httpClient: OkHttpClient
) : MediaServerClient {

    override suspend fun testConnection(config: MediaServerConfig): ServerTestResult = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trim()
        if (base.isBlank()) return@withContext ServerTestResult(false, "WebDAV / HTTP URL is required")
        runCatching {
            val entries = listDirectory(config, base)
            ServerTestResult(true, "Connected • Found ${entries.size} entries")
        }.getOrElse { e ->
            // Fallback: try simple GET/HEAD for custom HTTP servers without PROPFIND
            runCatching {
                val req = Request.Builder().url(base).applyAuth(config).head().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 405) {
                        ServerTestResult(true, "Connected to HTTP server (HTTP ${resp.code})")
                    } else {
                        ServerTestResult(false, "HTTP ${resp.code}: Check URL & credentials")
                    }
                }
            }.getOrElse { ServerTestResult(false, e.message ?: "Connection failed") }
        }
    }

    override suspend fun getCatalogRows(config: MediaServerConfig): List<CatalogRow> = withContext(Dispatchers.IO) {
        val entries = runCatching { listDirectory(config, config.baseUrl) }.getOrDefault(emptyList())
        val videos = entries.filter { !it.isDirectory && TorBoxClient.isVideoName(it.name) }
        if (videos.isEmpty()) return@withContext emptyList()

        listOf(
            CatalogRow(
                id = "webdav_${config.id}",
                title = "${config.name} • Cloud Files",
                origin = SourceOrigin.WEBDAV,
                items = videos.map { entry ->
                    MediaItem(
                        id = "dav:${config.id}:${encodeUrl(entry.fullUrl)}",
                        title = cleanMediaTitle(entry.name),
                        type = MediaType.MOVIE,
                        overview = entry.fullUrl,
                        origin = SourceOrigin.WEBDAV,
                        sourceId = config.id
                    )
                }
            )
        )
    }

    override suspend fun search(config: MediaServerConfig, query: String): List<MediaItem> {
        val rows = getCatalogRows(config)
        return rows.flatMap { it.items }.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun getDetail(config: MediaServerConfig, itemId: String): MediaDetail? {
        val url = decodeUrl(itemId.substringAfterLast(":"))
        val name = url.substringAfterLast('/').let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        return MediaDetail(
            item = MediaItem(
                id = itemId,
                title = cleanMediaTitle(name),
                type = MediaType.MOVIE,
                overview = "Direct WebDAV / HTTP Stream:\n$url",
                origin = SourceOrigin.WEBDAV,
                sourceId = config.id
            )
        )
    }

    override suspend fun getStreams(config: MediaServerConfig, itemId: String): List<StreamSource> {
        val url = decodeUrl(itemId.substringAfterLast(":"))
        if (url.isBlank()) return emptyList()
        return listOf(
            StreamSource(
                id = "${config.id}_dav_${url.hashCode()}",
                title = "${config.name} • Direct WebDAV Stream",
                subtitle = url.substringAfterLast('/'),
                url = url,
                headers = buildAuthHeaders(config),
                providerName = config.name,
                origin = SourceOrigin.WEBDAV,
                qualityBadges = listOf("WebDAV", "Direct"),
                isCached = true
            )
        )
    }

    suspend fun listDirectory(config: MediaServerConfig, dirUrl: String): List<WebDavEntry> =
        withContext(Dispatchers.IO) {
            val normalizedUrl = if (dirUrl.endsWith("/")) dirUrl else "$dirUrl/"
            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:displayname/>
                        <D:resourcetype/>
                    </D:prop>
                </D:propfind>
            """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

            val req = Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", propfindXml)
                .header("Depth", "1")
                .applyAuth(config)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 207) {
                    error("WebDAV PROPFIND failed: HTTP ${resp.code}")
                }
                val xml = resp.body?.string().orEmpty()
                parsePropfindXml(xml, normalizedUrl)
            }
        }

    fun buildAuthHeaders(config: MediaServerConfig): Map<String, String> {
        if (config.username.isNotBlank()) {
            val raw = "${config.username}:${config.password}"
            val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return mapOf("Authorization" to "Basic $encoded")
        }
        if (config.apiKeyOrToken.isNotBlank()) {
            return mapOf("Authorization" to "Bearer ${config.apiKeyOrToken}")
        }
        return emptyMap()
    }

    private fun Request.Builder.applyAuth(config: MediaServerConfig): Request.Builder {
        buildAuthHeaders(config).forEach { (k, v) -> header(k, v) }
        return this
    }

    private fun parsePropfindXml(xml: String, baseUrl: String): List<WebDavEntry> {
        val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
        val responseRegex = Regex("<(?:[a-zA-Z0-9]+:)?response[^>]*>(.*?)</(?:[a-zA-Z0-9]+:)?response>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val hrefRegex = Regex("<(?:[a-zA-Z0-9]+:)?href[^>]*>(.*?)</(?:[a-zA-Z0-9]+:)?href>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val collectionRegex = Regex("<(?:[a-zA-Z0-9]+:)?collection\\s*/?>", RegexOption.IGNORE_CASE)

        val results = mutableListOf<WebDavEntry>()
        responseRegex.findAll(xml).forEach { match ->
            val block = match.groupValues[1]
            val href = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
            val resolved = runCatching { baseUri.resolve(href).toString() }.getOrNull() ?: return@forEach
            if (resolved.trimEnd('/') == baseUrl.trimEnd('/')) return@forEach
            val isDir = collectionRegex.containsMatchIn(block) || href.endsWith("/")
            val rawName = resolved.trimEnd('/').substringAfterLast('/')
            val name = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
            if (name.isNotBlank()) {
                results += WebDavEntry(name = name, fullUrl = resolved, isDirectory = isDir)
            }
        }
        return results
    }

    private fun cleanMediaTitle(filename: String): String =
        filename.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim()

    companion object {
        fun encodeUrl(url: String): String =
            Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

        fun decodeUrl(encoded: String): String = runCatching {
            String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
