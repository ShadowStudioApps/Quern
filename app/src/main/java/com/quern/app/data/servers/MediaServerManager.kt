package com.quern.app.data.servers

import com.quern.app.data.local.MediaServerDao
import com.quern.app.data.local.MediaServerEntity
import com.quern.app.data.local.SecureVault
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.domain.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MediaServerManager(
    private val dao: MediaServerDao,
    private val vault: SecureVault,
    private val jellyfinEmbyClient: JellyfinEmbyClient,
    private val plexClient: PlexClient,
    private val webDavClient: WebDavClient
) {
    val servers: Flow<List<MediaServerConfig>> = dao.observeAll().map { entities ->
        entities.map { it.toConfig() }
    }

    suspend fun getAllServers(): List<MediaServerConfig> = dao.getAll().map { it.toConfig() }

    suspend fun getServerById(id: String): MediaServerConfig? =
        getAllServers().firstOrNull { it.id == id }

    fun clientFor(type: MediaServerType): MediaServerClient = when (type) {
        MediaServerType.JELLYFIN, MediaServerType.EMBY -> jellyfinEmbyClient
        MediaServerType.PLEX -> plexClient
        MediaServerType.WEBDAV -> webDavClient
    }

    suspend fun testAndSaveServer(input: MediaServerConfig): ServerTestResult {
        val id = input.id.ifBlank { UUID.randomUUID().toString() }
        val result = clientFor(input.type).testConnection(input)
        val finalConfig = input.copy(
            id = id,
            userId = result.resolvedUserId?.takeIf { it.isNotBlank() } ?: input.userId,
            apiKeyOrToken = result.resolvedToken?.takeIf { it.isNotBlank() } ?: input.apiKeyOrToken
        )
        if (result.success) {
            saveServer(finalConfig)
        }
        return result
    }

    suspend fun saveServer(config: MediaServerConfig) {
        val id = config.id.ifBlank { UUID.randomUUID().toString() }
        vault.putString(tokenKey(id), config.apiKeyOrToken)
        vault.putString(passKey(id), config.password)
        dao.upsert(
            MediaServerEntity(
                id = id,
                name = config.name.ifBlank { config.type.displayName },
                type = config.type.name,
                baseUrl = config.baseUrl.trim(),
                username = config.username.trim(),
                userId = config.userId.trim(),
                enabled = config.enabled
            )
        )
    }

    suspend fun deleteServer(id: String) {
        vault.remove(tokenKey(id))
        vault.remove(passKey(id))
        dao.delete(id)
    }

    suspend fun getAllCatalogRows(): List<CatalogRow> = coroutineScope {
        getAllServers().filter { it.enabled }.map { srv ->
            async {
                runCatching { clientFor(srv.type).getCatalogRows(srv) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    suspend fun searchAll(query: String): List<MediaItem> = coroutineScope {
        getAllServers().filter { it.enabled }.map { srv ->
            async {
                runCatching { clientFor(srv.type).search(srv, query) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    suspend fun getDetail(serverId: String, itemId: String): MediaDetail? {
        val srv = getServerById(serverId) ?: return null
        return clientFor(srv.type).getDetail(srv, itemId)
    }

    suspend fun getStreams(serverId: String, itemId: String): List<StreamSource> {
        val srv = getServerById(serverId) ?: return emptyList()
        return clientFor(srv.type).getStreams(srv, itemId)
    }

    suspend fun browseWebDav(serverId: String, directoryUrl: String? = null): List<WebDavEntry> {
        val srv = getServerById(serverId) ?: return emptyList()
        if (srv.type != MediaServerType.WEBDAV) return emptyList()
        return webDavClient.listDirectory(srv, directoryUrl ?: srv.baseUrl)
    }

    fun getWebDavAuthHeaders(config: MediaServerConfig): Map<String, String> =
        webDavClient.buildAuthHeaders(config)

    private fun MediaServerEntity.toConfig(): MediaServerConfig = MediaServerConfig(
        id = id,
        name = name,
        type = runCatching { MediaServerType.valueOf(type) }.getOrDefault(MediaServerType.JELLYFIN),
        baseUrl = baseUrl,
        apiKeyOrToken = vault.getString(tokenKey(id)),
        username = username,
        password = vault.getString(passKey(id)),
        userId = userId,
        enabled = enabled
    )

    private fun tokenKey(id: String) = "srv_token_$id"
    private fun passKey(id: String) = "srv_pass_$id"
}
