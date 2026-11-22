package com.quern.app.data.servers

import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.StreamSource

data class ServerTestResult(
    val success: Boolean,
    val message: String,
    val resolvedUserId: String? = null,
    val resolvedToken: String? = null
)

interface MediaServerClient {
    suspend fun testConnection(config: MediaServerConfig): ServerTestResult
    suspend fun getCatalogRows(config: MediaServerConfig): List<CatalogRow>
    suspend fun search(config: MediaServerConfig, query: String): List<MediaItem>
    suspend fun getDetail(config: MediaServerConfig, itemId: String): MediaDetail?
    suspend fun getStreams(config: MediaServerConfig, itemId: String): List<StreamSource>
}
