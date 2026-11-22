package com.quern.app.data.debrid

import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.DebridServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class RealDebridClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : DebridProvider {

    override val serviceType: DebridServiceType = DebridServiceType.REAL_DEBRID

    override suspend fun validateApiKey(apiKey: String): DebridAccountStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DebridAccountStatus(serviceType, false)
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/user")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext DebridAccountStatus(serviceType, false, error = "HTTP ${resp.code}: Invalid Real-Debrid key")
                }
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                DebridAccountStatus(
                    type = serviceType,
                    isConnected = true,
                    username = obj["username"]?.jsonPrimitive?.contentOrNull,
                    plan = obj["type"]?.jsonPrimitive?.contentOrNull?.replaceFirstChar { it.uppercase() },
                    expiresAt = obj["expiration"]?.jsonPrimitive?.contentOrNull
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
            val addBody = FormBody.Builder()
                .add("magnet", "magnet:?xt=urn:btih:$infoHash")
                .build()
            val addReq = Request.Builder()
                .url("$BASE_URL/torrents/addMagnet")
                .header("Authorization", "Bearer $apiKey")
                .post(addBody)
                .build()
            val torrentId = httpClient.newCall(addReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            } ?: return@withContext null

            val selectBody = FormBody.Builder()
                .add("files", fileIdx?.plus(1)?.toString() ?: "all")
                .build()
            val selectReq = Request.Builder()
                .url("$BASE_URL/torrents/selectFiles/$torrentId")
                .header("Authorization", "Bearer $apiKey")
                .post(selectBody)
                .build()
            httpClient.newCall(selectReq).execute().close()

            val infoReq = Request.Builder()
                .url("$BASE_URL/torrents/info/$torrentId")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val rawLink = httpClient.newCall(infoReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                obj["links"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            } ?: return@withContext null

            unrestrictLink(apiKey, rawLink)
        }.getOrNull()
    }

    override suspend fun unrestrictLink(apiKey: String, link: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || link.isBlank()) return@withContext null
        runCatching {
            val body = FormBody.Builder().add("link", link).build()
            val req = Request.Builder()
                .url("$BASE_URL/unrestrict/link")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["download"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    override suspend fun getCloudLibrary(apiKey: String): List<DebridCloudItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val items = mutableListOf<DebridCloudItem>()
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/downloads?limit=40")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray ?: return@use
                arr.forEach { elem ->
                    val obj = elem as? JsonObject ?: return@forEach
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val filename = obj["filename"]?.jsonPrimitive?.contentOrNull ?: "Real-Debrid File"
                    val download = obj["download"]?.jsonPrimitive?.contentOrNull
                    val size = obj["filesize"]?.jsonPrimitive?.longOrNull
                    items += DebridCloudItem(
                        id = id,
                        name = filename,
                        service = serviceType,
                        sizeBytes = size,
                        status = "downloaded",
                        downloadUrl = download
                    )
                }
            }
        }
        items
    }

    override suspend fun getCloudItemStreamUrl(apiKey: String, item: DebridCloudItem): String? =
        item.downloadUrl

    companion object {
        private const val BASE_URL = "https://api.real-debrid.com/rest/1.0"
    }
}
