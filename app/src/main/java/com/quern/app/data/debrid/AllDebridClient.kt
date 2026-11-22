package com.quern.app.data.debrid

import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.DebridServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class AllDebridClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : DebridProvider {

    override val serviceType: DebridServiceType = DebridServiceType.ALL_DEBRID

    override suspend fun validateApiKey(apiKey: String): DebridAccountStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DebridAccountStatus(serviceType, false)
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/user?agent=$AGENT&apikey=$apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext DebridAccountStatus(serviceType, false, error = "HTTP ${resp.code}")
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                if (root["status"]?.jsonPrimitive?.contentOrNull != "success") {
                    val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Invalid AllDebrid key"
                    return@withContext DebridAccountStatus(serviceType, false, error = msg)
                }
                val user = root["data"]?.jsonObject?.get("user")?.jsonObject
                val isPremium = user?.get("isPremium")?.jsonPrimitive?.booleanOrNull == true
                DebridAccountStatus(
                    type = serviceType,
                    isConnected = true,
                    username = user?.get("username")?.jsonPrimitive?.contentOrNull,
                    plan = if (isPremium) "Premium" else "Free"
                )
            }
        }.getOrElse { DebridAccountStatus(serviceType, false, error = it.message) }
    }

    override suspend fun checkCachedHashes(apiKey: String, hashes: List<String>): Set<String> = emptySet()

    override suspend fun resolveInfoHashToStreamUrl(
        apiKey: String,
        infoHash: String,
        fileIdx: Int?
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || infoHash.isBlank()) return@withContext null
        runCatching {
            val uploadUrl = "$BASE_URL/magnet/upload".toHttpUrl().newBuilder()
                .addQueryParameter("agent", AGENT)
                .addQueryParameter("apikey", apiKey)
                .addQueryParameter("magnets[]", "magnet:?xt=urn:btih:$infoHash")
                .build()
            val magnetId = httpClient.newCall(Request.Builder().url(uploadUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonObject?.get("magnets")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            } ?: return@withContext null

            val statusReq = Request.Builder()
                .url("$BASE_URL/magnet/status?agent=$AGENT&apikey=$apiKey&id=$magnetId")
                .build()
            val lockedLink = httpClient.newCall(statusReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val links = root["data"]?.jsonObject?.get("magnets")?.jsonObject?.get("links")?.jsonArray
                    ?.mapNotNull { it as? JsonObject }.orEmpty()
                val chosen = if (fileIdx != null && fileIdx in links.indices) {
                    links[fileIdx]
                } else {
                    links.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                }
                chosen?.get("link")?.jsonPrimitive?.contentOrNull
            } ?: return@withContext null

            unrestrictLink(apiKey, lockedLink)
        }.getOrNull()
    }

    override suspend fun unrestrictLink(apiKey: String, link: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || link.isBlank()) return@withContext null
        runCatching {
            val url = "$BASE_URL/link/unlock".toHttpUrl().newBuilder()
                .addQueryParameter("agent", AGENT)
                .addQueryParameter("apikey", apiKey)
                .addQueryParameter("link", link)
                .build()
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonObject?.get("link")?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    override suspend fun getCloudLibrary(apiKey: String): List<DebridCloudItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val items = mutableListOf<DebridCloudItem>()
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/user/links?agent=$AGENT&apikey=$apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val arr = root["data"]?.jsonObject?.get("links") as? JsonArray ?: return@use
                arr.forEachIndexed { idx, elem ->
                    val obj = elem as? JsonObject ?: return@forEachIndexed
                    val link = obj["link"]?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
                    val name = obj["filename"]?.jsonPrimitive?.contentOrNull ?: "AllDebrid Link"
                    val size = obj["size"]?.jsonPrimitive?.longOrNull
                    items += DebridCloudItem(
                        id = "ad_$idx",
                        name = name,
                        service = serviceType,
                        sizeBytes = size,
                        status = "saved",
                        downloadUrl = link
                    )
                }
            }
        }
        items
    }

    override suspend fun getCloudItemStreamUrl(apiKey: String, item: DebridCloudItem): String? {
        val raw = item.downloadUrl ?: return null
        return unrestrictLink(apiKey, raw) ?: raw
    }

    companion object {
        private const val BASE_URL = "https://api.alldebrid.com/v4"
        private const val AGENT = "QuernAndroid"
    }
}
