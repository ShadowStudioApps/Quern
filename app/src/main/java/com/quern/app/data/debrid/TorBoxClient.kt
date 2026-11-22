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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Full TorBox v1 API client (https://api.torbox.app/v1/api).
 * Supports account validation, instant cache checking, magnet/infoHash resolution to direct HTTPS stream,
 * and browsing personal TorBox torrents, usenet, and web downloads.
 */
class TorBoxClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) : DebridProvider {

    override val serviceType: DebridServiceType = DebridServiceType.TORBOX

    override suspend fun validateApiKey(apiKey: String): DebridAccountStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext DebridAccountStatus(serviceType, isConnected = false)
        }
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/user/me?settings=false")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext DebridAccountStatus(
                        serviceType,
                        isConnected = false,
                        error = "HTTP ${resp.code}: Invalid TorBox API key"
                    )
                }
                val root = json.parseToJsonElement(body).jsonObject
                val success = root["success"]?.jsonPrimitive?.booleanOrNull == true
                if (!success) {
                    val detail = root["detail"]?.jsonPrimitive?.contentOrNull ?: "Authentication failed"
                    return@withContext DebridAccountStatus(serviceType, isConnected = false, error = detail)
                }
                val data = root["data"]?.jsonObject
                val email = data?.get("email")?.jsonPrimitive?.contentOrNull ?: "TorBox User"
                val planCode = data?.get("plan")?.jsonPrimitive?.intOrNull ?: 0
                val planName = when (planCode) {
                    1 -> "Essential"
                    2 -> "Pro"
                    3 -> "Standard"
                    else -> "Free / Active"
                }
                val expires = data?.get("premium_expires_at")?.jsonPrimitive?.contentOrNull
                DebridAccountStatus(
                    type = serviceType,
                    isConnected = true,
                    username = email,
                    plan = planName,
                    expiresAt = expires
                )
            }
        }.getOrElse { e ->
            DebridAccountStatus(serviceType, isConnected = false, error = e.message ?: "Connection failed")
        }
    }

    override suspend fun checkCachedHashes(apiKey: String, hashes: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || hashes.isEmpty()) return@withContext emptySet()
            runCatching {
                val joined = hashes.take(40).joinToString(",")
                val req = Request.Builder()
                    .url("$BASE_URL/torrents/checkcached?hash=$joined&format=list&list_files=false")
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptySet()
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val data = root["data"]
                    when (data) {
                        is JsonArray -> data.mapNotNull {
                            it.jsonObject["hash"]?.jsonPrimitive?.contentOrNull?.lowercase()
                        }.toSet()
                        is JsonObject -> data.keys.map { it.lowercase() }.toSet()
                        else -> emptySet()
                    }
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
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("magnet", magnet)
                .addFormDataPart("seed", "1")
                .addFormDataPart("allow_zip", "false")
                .build()

            val createReq = Request.Builder()
                .url("$BASE_URL/torrents/createtorrent")
                .header("Authorization", "Bearer $apiKey")
                .post(formBody)
                .build()

            val torrentId = httpClient.newCall(createReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonObject?.get("torrent_id")?.jsonPrimitive?.intOrNull
            } ?: return@withContext null

            val listReq = Request.Builder()
                .url("$BASE_URL/torrents/mylist?id=$torrentId&bypass_cache=true")
                .header("Authorization", "Bearer $apiKey")
                .build()

            val chosenFileId = httpClient.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val dataObj = when (val data = root["data"]) {
                    is JsonObject -> data
                    is JsonArray -> data.firstOrNull()?.jsonObject
                    else -> null
                } ?: return@withContext null

                val files = dataObj["files"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                if (files.isEmpty()) return@withContext null

                val targetFile = if (fileIdx != null && fileIdx in files.indices) {
                    files[fileIdx]
                } else {
                    files.filter { isVideoName(it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
                        .maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                        ?: files.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                }
                targetFile?.get("id")?.jsonPrimitive?.intOrNull
            } ?: return@withContext null

            requestDownloadLink(apiKey, torrentId.toString(), chosenFileId.toString())
        }.getOrNull()
    }

    override suspend fun unrestrictLink(apiKey: String, link: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || link.isBlank()) return@withContext null
        runCatching {
            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("link", link)
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/webdl/createwebdownload")
                .header("Authorization", "Bearer $apiKey")
                .post(formBody)
                .build()
            val webId = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonObject?.get("webdownload_id")?.jsonPrimitive?.intOrNull
            } ?: return@withContext null

            val dlReq = Request.Builder()
                .url("$BASE_URL/webdl/requestdl?token=$apiKey&web_id=$webId&zip_link=false")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root["data"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    override suspend fun getCloudLibrary(apiKey: String): List<DebridCloudItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val items = mutableListOf<DebridCloudItem>()
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/torrents/mylist?bypass_cache=true")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val arr = root["data"] as? JsonArray ?: return@use
                arr.forEach { elem ->
                    val obj = elem.jsonObject
                    val torrentId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "TorBox Item"
                    val size = obj["size"]?.jsonPrimitive?.longOrNull
                    val state = obj["download_state"]?.jsonPrimitive?.contentOrNull ?: "ready"
                    val files = obj["files"] as? JsonArray
                    val mainFile = files?.mapNotNull { it as? JsonObject }
                        ?.filter { isVideoName(it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
                        ?.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
                        ?: files?.firstOrNull()?.jsonObject
                    val fileId = mainFile?.get("id")?.jsonPrimitive?.contentOrNull
                    items += DebridCloudItem(
                        id = torrentId,
                        name = name,
                        service = serviceType,
                        sizeBytes = size,
                        status = state,
                        fileId = fileId
                    )
                }
            }
        }
        items
    }

    override suspend fun getCloudItemStreamUrl(apiKey: String, item: DebridCloudItem): String? {
        if (!item.downloadUrl.isNullOrBlank()) return item.downloadUrl
        val fileId = item.fileId ?: return null
        return requestDownloadLink(apiKey, item.id, fileId)
    }

    private suspend fun requestDownloadLink(apiKey: String, torrentId: String, fileId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dlReq = Request.Builder()
                    .url("$BASE_URL/torrents/requestdl?token=$apiKey&torrent_id=$torrentId&file_id=$fileId&zip_link=false")
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                httpClient.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["data"]?.jsonPrimitive?.contentOrNull
                }
            }.getOrNull()
        }

    companion object {
        private const val BASE_URL = "https://api.torbox.app/v1/api"

        fun isVideoName(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".m4v") ||
                lower.endsWith(".ts") || lower.endsWith(".m3u8")
        }
    }
}
