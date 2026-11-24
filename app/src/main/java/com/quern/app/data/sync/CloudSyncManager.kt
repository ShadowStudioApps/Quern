package com.quern.app.data.sync

import android.util.Base64
import com.quern.app.data.QuernRepository
import com.quern.app.data.local.SecureVault
import com.quern.app.data.local.SettingsStore
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class QuernSyncBundle(
    val version: Int = 1,
    val addonUrls: List<String> = emptyList(),
    val language: String = "SYSTEM",
    val theme: String = "GLOSSY_BLACK",
    val amoledPureBlack: Boolean = true,
    val subtitleLanguage: String = "ARABIC",
    val audioLanguage: String = "ORIGINAL",
    val navigationStyle: String = "SIDEBAR_LEFT"
)

/**
 * Portable Sync Code (`QUERN1:`), Stremio account importer, and JSON/backup importer.
 * Multi-device account sync lives in [QuernAccount] / [QuernSyncEngine].
 */
class CloudSyncManager(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val vault: SecureVault,
    private val repository: QuernRepository,
    private val settingsStore: SettingsStore
) {
    @Volatile
    var lastImportAt: String? = null
        private set

    suspend fun exportSyncCode(): String {
        val addons = repository.addonRepository.getEnabledAddons().map { it.manifestUrl }
        val s = settingsStore.settings.value
        val bundle = QuernSyncBundle(
            addonUrls = addons,
            language = s.language.name,
            theme = s.theme.name,
            amoledPureBlack = s.amoledPureBlack,
            subtitleLanguage = s.subtitleLanguage.name,
            audioLanguage = s.audioLanguage.name,
            navigationStyle = s.navigationStyle.name
        )
        val raw = json.encodeToString(bundle)
        return "QUERN1:" + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    suspend fun importSyncCode(code: String): Result<String> = runCatching {
        val cleaned = code.trim().removePrefix("QUERN1:")
        val decoded = String(Base64.decode(cleaned, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        val bundle = json.decodeFromString<QuernSyncBundle>(decoded)
        applyBundle(bundle)
        lastImportAt = "Sync code imported just now"
        "Synced ${bundle.addonUrls.size} addons & settings!"
    }

    private suspend fun applyBundle(bundle: QuernSyncBundle) {
        bundle.addonUrls.forEach { url ->
            runCatching { repository.addonRepository.installAddon(url) }
        }
        settingsStore.update { cur ->
            cur.copy(
                language = runCatching { com.quern.app.domain.model.AppLanguage.valueOf(bundle.language) }.getOrDefault(cur.language),
                theme = runCatching { com.quern.app.domain.model.AppThemePreset.valueOf(bundle.theme) }.getOrDefault(cur.theme),
                amoledPureBlack = bundle.amoledPureBlack,
                subtitleLanguage = runCatching { com.quern.app.domain.model.SubtitleLanguage.valueOf(bundle.subtitleLanguage) }.getOrDefault(cur.subtitleLanguage),
                audioLanguage = runCatching { com.quern.app.domain.model.AudioLanguage.valueOf(bundle.audioLanguage) }.getOrDefault(cur.audioLanguage),
                navigationStyle = runCatching { com.quern.app.domain.model.NavigationStyle.valueOf(bundle.navigationStyle) }.getOrDefault(cur.navigationStyle)
            )
        }
    }

    fun getSavedStremioEmail(): String = vault.getString(KEY_STREMIO_EMAIL)

    /**
     * Full official Stremio Cloud Sync:
     * 1. Logs into https://api.strem.io/api/login (or uses saved/provided authKey)
     * 2. Pulls ALL installed addons via addonCollectionGet
     * 3. Pulls Library & Watch Progress via datastoreGet (libraryItem)
     */
    suspend fun syncFromStremio(
        email: String = "",
        password: String = "",
        providedAuthKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var authKey = providedAuthKey.trim().ifBlank { vault.getString(KEY_STREMIO_AUTH) }
            val cleanEmail = email.trim()

            if (cleanEmail.isNotBlank() && password.isNotBlank()) {
                val loginPayload = buildJsonObject {
                    put("type", "Login")
                    put("email", cleanEmail)
                    put("password", password)
                }.toString().toRequestBody(JSON_MEDIA)

                val loginReq = Request.Builder()
                    .url("https://api.strem.io/api/login")
                    .header("Content-Type", "application/json")
                    .post(loginPayload)
                    .build()

                httpClient.newCall(loginReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    val err = root?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    if (!resp.isSuccessful || err != null) {
                        error(err ?: "Stremio login failed (HTTP ${resp.code})")
                    }
                    authKey = root?.get("result")?.jsonObject?.get("authKey")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (authKey.isBlank()) error("Stremio did not return an authKey.")
                    vault.putString(KEY_STREMIO_AUTH, authKey)
                    vault.putString(KEY_STREMIO_EMAIL, cleanEmail)
                }
            } else if (providedAuthKey.isNotBlank()) {
                vault.putString(KEY_STREMIO_AUTH, authKey)
            }

            if (authKey.isBlank()) {
                error("Enter your Stremio Email & Password (or Stremio AuthKey).")
            }

            // 1. Fetch Addon Collection
            var addonsImported = 0
            val addonsPayload = buildJsonObject {
                put("type", "AddonCollectionGet")
                put("authKey", authKey)
                put("update", true)
            }.toString().toRequestBody(JSON_MEDIA)

            val addonsReq = Request.Builder()
                .url("https://api.strem.io/api/addonCollectionGet")
                .header("Content-Type", "application/json")
                .post(addonsPayload)
                .build()

            httpClient.newCall(addonsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    val addonsArr = root?.get("result")?.jsonObject?.get("addons")?.jsonArray
                    addonsArr?.forEach { elem ->
                        val url = elem.jsonObject["transportUrl"]?.jsonPrimitive?.contentOrNull?.trim()
                        if (!url.isNullOrBlank() && (url.startsWith("http") || url.startsWith("stremio://"))) {
                            val res = runCatching { repository.addonRepository.installAddon(url) }.getOrNull()
                            if (res?.isSuccess == true) addonsImported++
                        }
                    }
                }
            }

            // 2. Fetch Stremio Library & Watch Progress
            var libraryImported = 0
            var progressImported = 0
            val libPayload = buildJsonObject {
                put("type", "DatastoreGet")
                put("authKey", authKey)
                put("collection", "libraryItem")
                put("all", true)
            }.toString().toRequestBody(JSON_MEDIA)

            val libReq = Request.Builder()
                .url("https://api.strem.io/api/datastoreGet")
                .header("Content-Type", "application/json")
                .post(libPayload)
                .build()

            httpClient.newCall(libReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    val items = root?.get("result")?.jsonArray
                    items?.take(200)?.forEach { elem ->
                        val obj = elem as? JsonObject ?: return@forEach
                        val removed = obj["removed"]?.jsonPrimitive?.booleanOrNull == true
                        val id = obj["_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (id.isBlank() || name.isBlank()) return@forEach
                        val mediaType = MediaType.fromStremio(typeStr)
                        val poster = obj["poster"]?.jsonPrimitive?.contentOrNull
                        val year = obj["year"]?.jsonPrimitive?.contentOrNull

                        if (!removed && (mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES)) {
                            repository.toggleFavorite(
                                MediaItem(
                                    id = id,
                                    title = name,
                                    type = mediaType,
                                    posterUrl = poster,
                                    year = year,
                                    origin = SourceOrigin.ADDON
                                ),
                                currentlyFavorite = false
                            )
                            libraryImported++
                        }

                        val stateObj = obj["state"] as? JsonObject
                        val offset = stateObj?.get("timeOffset")?.jsonPrimitive?.longOrNull ?: 0L
                        val dur = stateObj?.get("duration")?.jsonPrimitive?.longOrNull ?: 0L
                        val videoId = stateObj?.get("video_id")?.jsonPrimitive?.contentOrNull
                        if (offset > 10_000L && dur > 30_000L && offset < (dur * 0.96).toLong()) {
                            repository.saveWatchProgress(
                                WatchProgress(
                                    contentId = videoId?.takeIf { it.isNotBlank() } ?: id,
                                    title = name,
                                    type = mediaType,
                                    posterUrl = poster,
                                    backdropUrl = null,
                                    positionMs = offset,
                                    durationMs = dur,
                                    episodeId = videoId,
                                    episodeLabel = videoId?.takeIf { it.contains(":") }?.let {
                                        val parts = it.split(":")
                                        if (parts.size >= 3) "S${parts[1]} E${parts[2]}" else null
                                    }
                                )
                            )
                            progressImported++
                        }
                    }
                }
            }

            lastImportAt = "Stremio synced just now"
            "Stremio Synced! $addonsImported addons • $libraryImported library items • $progressImported progress items."
        }
    }

    /**
     * Universal Nuvio Sync & Backup Importer:
     * Accepts a Nuvio JSON backup/export, a URL pointing to a Nuvio JSON backup,
     * OR a list of Nuvio/Stremio manifest.json addon URLs.
     */
    suspend fun importFromNuvio(rawInput: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) error("Paste your backup JSON, backup URL, or addon manifest links.")

            // If user pasted a direct URL to a JSON backup (not a manifest.json), fetch it
            val payloadText = if (
                (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                !trimmed.contains("manifest.json", ignoreCase = true) &&
                !trimmed.contains("\n")
            ) {
                val req = Request.Builder().url(trimmed).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Failed to fetch backup URL (HTTP ${resp.code})")
                    resp.body?.string().orEmpty()
                }
            } else {
                trimmed
            }

            // 1. Extract every manifest.json or stremio:// URL from JSON or plain text
            val manifestRegex = Regex("""(https?://[^\s"'<>]+manifest\.json[^\s"'<>]*|stremio://[^\s"'<>]+)""", RegexOption.IGNORE_CASE)
            val discoveredUrls = linkedSetOf<String>()
            manifestRegex.findAll(payloadText).forEach { match ->
                discoveredUrls += match.value.trimEnd(',', ';', ')', ']')
            }

            var libraryImported = 0
            var progressImported = 0

            // 2. Parse structured Nuvio JSON if valid JSON
            val parsed = runCatching { json.parseToJsonElement(payloadText) }.getOrNull()
            if (parsed is JsonObject) {
                // Collect addon/plugin URLs from structured arrays
                listOf("addons", "installedAddons", "plugins", "addonUrls").forEach { key ->
                    val arr = parsed[key] as? JsonArray ?: return@forEach
                    arr.forEach { el ->
                        val url = when (el) {
                            is JsonObject -> {
                                el["manifestUrl"]?.jsonPrimitive?.contentOrNull
                                    ?: el["transportUrl"]?.jsonPrimitive?.contentOrNull
                                    ?: el["url"]?.jsonPrimitive?.contentOrNull
                            }
                            else -> runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()
                        }
                        if (!url.isNullOrBlank() && (url.startsWith("http") || url.startsWith("stremio://"))) {
                            discoveredUrls += url.trim()
                        }
                    }
                }

                // Import Nuvio Library / Watchlist / Favorites
                listOf("library", "watchlist", "favorites", "libraryItems").forEach { key ->
                    val arr = parsed[key] as? JsonArray ?: return@forEach
                    arr.take(200).forEach { el ->
                        val obj = el as? JsonObject ?: return@forEach
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull
                            ?: obj["imdbId"]?.jsonPrimitive?.contentOrNull
                            ?: obj["_id"]?.jsonPrimitive?.contentOrNull
                            ?: return@forEach
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                            ?: return@forEach
                        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: "movie"
                        val poster = obj["poster"]?.jsonPrimitive?.contentOrNull ?: obj["posterUrl"]?.jsonPrimitive?.contentOrNull
                        val year = obj["year"]?.jsonPrimitive?.contentOrNull
                        repository.toggleFavorite(
                            MediaItem(
                                id = id,
                                title = title,
                                type = MediaType.fromStremio(typeStr),
                                posterUrl = poster,
                                year = year,
                                origin = SourceOrigin.ADDON
                            ),
                            currentlyFavorite = false
                        )
                        libraryImported++
                    }
                }

                // Import Nuvio Watch Progress
                listOf("watchProgress", "continueWatching", "history").forEach { key ->
                    val arr = parsed[key] as? JsonArray ?: return@forEach
                    arr.take(100).forEach { el ->
                        val obj = el as? JsonObject ?: return@forEach
                        val id = obj["contentId"]?.jsonPrimitive?.contentOrNull
                            ?: obj["id"]?.jsonPrimitive?.contentOrNull
                            ?: return@forEach
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                            ?: return@forEach
                        val pos = obj["positionMs"]?.jsonPrimitive?.longOrNull
                            ?: obj["position"]?.jsonPrimitive?.longOrNull?.let { if (it < 50_000L) it * 1000L else it }
                            ?: 0L
                        val dur = obj["durationMs"]?.jsonPrimitive?.longOrNull
                            ?: obj["duration"]?.jsonPrimitive?.longOrNull?.let { if (it < 50_000L) it * 1000L else it }
                            ?: 0L
                        if (pos > 5_000L && dur > 30_000L) {
                            repository.saveWatchProgress(
                                WatchProgress(
                                    contentId = id,
                                    title = title,
                                    type = MediaType.fromStremio(obj["type"]?.jsonPrimitive?.contentOrNull ?: "movie"),
                                    posterUrl = obj["posterUrl"]?.jsonPrimitive?.contentOrNull ?: obj["poster"]?.jsonPrimitive?.contentOrNull,
                                    backdropUrl = obj["backdropUrl"]?.jsonPrimitive?.contentOrNull,
                                    positionMs = pos,
                                    durationMs = dur,
                                    episodeLabel = obj["episodeLabel"]?.jsonPrimitive?.contentOrNull
                                )
                            )
                            progressImported++
                        }
                    }
                }
            }

            var addonsImported = 0
            discoveredUrls.forEach { url ->
                val res = runCatching { repository.addonRepository.installAddon(url) }.getOrNull()
                if (res?.isSuccess == true) addonsImported++
            }

            if (addonsImported == 0 && libraryImported == 0 && progressImported == 0) {
                error("No addons or library items found in the pasted backup data.")
            }

            lastImportAt = "Backup imported just now"
            "Backup Imported! $addonsImported addons • $libraryImported library items • $progressImported progress items."
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val KEY_STREMIO_AUTH = "stremio_auth_key"
        private const val KEY_STREMIO_EMAIL = "stremio_email"
    }
}
