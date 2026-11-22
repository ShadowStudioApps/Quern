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
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class PremiumizeClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : DebridProvider {

    override val serviceType: DebridServiceType = DebridServiceType.PREMIUMIZE

    override suspend fun validateApiKey(apiKey: String): DebridAccountStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DebridAccountStatus(serviceType, false)
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/account/info?apikey=$apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext DebridAccountStatus(serviceType, false, error = "HTTP ${resp.code}")
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                if (obj["status"]?.jsonPrimitive?.contentOrNull != "success") {
                    val msg = obj["message"]?.jsonPrimitive?.contentOrNull ?: "Invalid Premiumize key"
                    return@withContext DebridAccountStatus(serviceType, false, error = msg)
                }
                DebridAccountStatus(
                    type = serviceType,
                    isConnected = true,
                    username = obj["customer_id"]?.jsonPrimitive?.contentOrNull ?: "Premiumize User",
                    plan = "Premium"
                )
            }
        }.getOrElse { DebridAccountStatus(serviceType, false, error = it.message) }
    }

    override suspend fun checkCachedHashes(apiKey: String, hashes: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || hashes.isEmpty()) return@withContext emptySet()
            runCatching {
                val urlBuilder = "$BASE_URL/cache/check".toHttpUrl().newBuilder()
                    .addQueryParameter("apikey", apiKey)
                val target = hashes.take(40)
                target.forEach { urlBuilder.addQueryParameter("items[]", "magnet:?xt=urn:btih:$it") }
                httpClient.newCall(Request.Builder().url(urlBuilder.build()).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptySet()
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val flags = obj["response"]?.jsonArray ?: return@withContext emptySet()
                    target.filterIndexed { index, _ ->
                        flags.getOrNull(index)?.jsonPrimitive?.booleanOrNull == true
                    }.map { it.lowercase() }.toSet()
                }
            }.getOrDefault(emptySet())
        }

    override suspend fun resolveInfoHashToStreamUrl(
        apiKey: String,
        infoHash: String,
        fileIdx: Int?
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || infoHash.isBlank()) return@withContext null
        runCatching {
            val body = FormBody.Builder()
                .add("apikey", apiKey)
                .add("src", "magnet:?xt=urn:btih:$infoHash")
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/transfer/directdl")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val content = root["content"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
                val chosen = if (fileIdx != null && fileIdx in content.indices) {
                    content[fileIdx]
                } else {
                    content.filter { TorBoxClient.isVideoName(it["path"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
                        .maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                        ?: content.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                }
                chosen?.get("stream_link")?.jsonPrimitive?.contentOrNull
                    ?: chosen?.get("link")?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    override suspend fun unrestrictLink(apiKey: String, link: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || link.isBlank()) return@withContext null
        runCatching {
            val body = FormBody.Builder()
                .add("apikey", apiKey)
                .add("src", link)
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/transfer/directdl")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["location"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    override suspend fun getCloudLibrary(apiKey: String): List<DebridCloudItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val items = mutableListOf<DebridCloudItem>()
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/item/listall?apikey=$apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val files = root["files"] as? JsonArray ?: return@use
                files.forEach { elem ->
                    val obj = elem as? JsonObject ?: return@forEach
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (!TorBoxClient.isVideoName(name)) return@forEach
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: name
                    items += DebridCloudItem(
                        id = id,
                        name = name,
                        service = serviceType,
                        sizeBytes = obj["size"]?.jsonPrimitive?.longOrNull,
                        status = "cloud"
                    )
                }
            }
        }
        items
    }

    override suspend fun getCloudItemStreamUrl(apiKey: String, item: DebridCloudItem): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE_URL/item/details?apikey=$apiKey&id=${item.id}")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    obj["stream_link"]?.jsonPrimitive?.contentOrNull
                        ?: obj["link"]?.jsonPrimitive?.contentOrNull
                }
            }.getOrNull()
        }

    companion object {
        private const val BASE_URL = "https://www.premiumize.me/api"
    }
}
