package com.quern.app.data.debrid

import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.DebridServiceType

interface DebridProvider {
    val serviceType: DebridServiceType

    suspend fun validateApiKey(apiKey: String): DebridAccountStatus

    suspend fun checkCachedHashes(apiKey: String, hashes: List<String>): Set<String>

    suspend fun resolveInfoHashToStreamUrl(
        apiKey: String,
        infoHash: String,
        fileIdx: Int? = null
    ): String?

    suspend fun unrestrictLink(apiKey: String, link: String): String?

    suspend fun getCloudLibrary(apiKey: String): List<DebridCloudItem>

    suspend fun getCloudItemStreamUrl(apiKey: String, item: DebridCloudItem): String?
}
