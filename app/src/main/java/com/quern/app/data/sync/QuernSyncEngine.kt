package com.quern.app.data.sync

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.quern.app.BuildConfig
import com.quern.app.data.addons.AddonRepository
import com.quern.app.data.local.AddonDao
import com.quern.app.data.local.SecureVault
import com.quern.app.data.local.SettingsStore
import com.quern.app.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest

data class SyncStatus(
    val running: Boolean = false,
    /** Wall-clock time of the last fully successful sync, 0 = never. */
    val lastSyncAt: Long = 0L,
    val lastError: AccountError? = null,
    /**
     * An encrypted section on the server can't be decrypted with this device's key (the password
     * was reset elsewhere). Encrypted sections stop uploading until the user signs in again.
     */
    val keyMismatch: Boolean = false
)

/**
 * Account sync engine (docs/account-sync-format.md). Step C1 covers the `addons`, `settings`,
 * `profiles` and `device_<id>` sections.
 *
 * Per section it remembers the last server revision it saw and a hash of the local data as it was
 * right after the last successful pull or push. A section is uploaded only when the local hash
 * changes, so data applied from the server is never echoed back.
 */
@OptIn(FlowPreview::class)
class QuernSyncEngine(
    private val context: Context,
    private val account: QuernAccount,
    private val settingsStore: SettingsStore,
    private val addonDao: AddonDao,
    private val addonRepository: AddonRepository,
    private val vault: SecureVault,
    private val json: Json
) {
    private enum class Section(val key: String, val encrypted: Boolean) {
        ADDONS("addons", true),
        SETTINGS("settings", false),
        PROFILES("profiles", false)
    }

    private class RemoteRow(val section: String, val revision: Long, val encrypted: Boolean, val payload: String?)

    private class ConflictException : Exception("Section changed on the server")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val baseUrl = QuernCloudConfig.SUPABASE_URL

    private val _status = MutableStateFlow(SyncStatus(lastSyncAt = vault.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _addonsApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits after addons or hidden catalogs changed because of the server, so Home can reload. */
    val addonsApplied: SharedFlow<Unit> = _addonsApplied.asSharedFlow()

    private var started = false

    /** Call once at app start. Follows the account: syncs while signed in, idles while signed out. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            account.state
                .map { if (it.isSignedIn) it.userId else "" }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId.isBlank()) {
                        _status.value = SyncStatus()
                        return@collectLatest
                    }
                    if (vault.getString(KEY_SYNC_USER) != userId) resetSyncState(userId)
                    syncNow()
                    coroutineScope {
                        launch {
                            while (true) {
                                delay(PERIODIC_PULL_MS)
                                syncNow()
                            }
                        }
                        launch {
                            merge(
                                settingsStore.settings.map { Unit },
                                addonDao.observeAll().map { Unit }
                            )
                                .drop(1)
                                .debounce(PUSH_DEBOUNCE_MS)
                                .collect { pushChangesNow() }
                        }
                    }
                }
        }
    }

    /** App came back to the foreground. */
    fun onForeground() {
        if (account.state.value.isSignedIn) scope.launch { syncNow() }
    }

    /** Pull everything newer from the server, then upload local changes and refresh this device's row. */
    suspend fun syncNow() {
        if (!account.state.value.isSignedIn) return
        lock.withLock {
            _status.value = _status.value.copy(running = true)
            val error = runStep {
                pullAll()
                Section.entries.forEach { pushIfChanged(it) }
                writeDeviceRow()
            }
            finish(error)
        }
    }

    private suspend fun pushChangesNow() {
        if (!account.state.value.isSignedIn) return
        lock.withLock {
            val error = runStep { Section.entries.forEach { pushIfChanged(it) } }
            if (error != null) _status.value = _status.value.copy(lastError = error)
        }
    }

    private suspend fun runStep(block: suspend () -> Unit): AccountError? = try {
        block()
        null
    } catch (e: AccountException) {
        Log.w(TAG, "Sync failed: ${e.reason}")
        e.reason
    } catch (e: IOException) {
        Log.w(TAG, "Sync network error: ${e.javaClass.simpleName}")
        AccountError.NETWORK
    } catch (e: Exception) {
        Log.w(TAG, "Sync error: ${e.javaClass.simpleName}")
        AccountError.SERVER
    }

    private fun finish(error: AccountError?) {
        val now = System.currentTimeMillis()
        if (error == null) vault.putString(KEY_LAST_SYNC, now.toString())
        _status.value = _status.value.copy(
            running = false,
            lastError = error,
            lastSyncAt = if (error == null) now else _status.value.lastSyncAt
        )
    }

    // ---------------------------------------------------------------- pull

    private suspend fun pullAll() {
        val revisions = fetchRows(select = "section,revision,encrypted", sections = null)
            .associateBy { it.section }
        val stale = Section.entries.filter { s ->
            val remote = revisions[s.key] ?: return@filter false
            remote.revision > lastRevision(s)
        }
        if (stale.isEmpty()) return
        val rows = fetchRows(select = "section,revision,encrypted,payload", sections = stale.map { it.key })
            .associateBy { it.section }
        stale.forEach { s -> rows[s.key]?.let { applyRow(s, it) } }
    }

    private suspend fun pullSection(s: Section) {
        val row = fetchRows(select = "section,revision,encrypted,payload", sections = listOf(s.key)).firstOrNull()
        if (row == null) {
            // Row is gone on the server (e.g. account data deleted): upload again from scratch.
            setLastRevision(s, 0L)
            setLocalHash(s, "")
            return
        }
        applyRow(s, row)
    }

    private suspend fun applyRow(s: Section, row: RemoteRow) {
        val text = row.payload ?: return
        val plain = if (row.encrypted) {
            val key = account.encryptionKey() ?: return
            val decrypted = AccountCrypto.decrypt(text, key)
            key.fill(0)
            if (decrypted == null) {
                _status.value = _status.value.copy(keyMismatch = true)
                return
            }
            decrypted
        } else {
            text
        }
        val obj = runCatching { json.parseToJsonElement(plain).jsonObject }.getOrNull() ?: return
        // First time this device meets the account: keep what it already has next to the server copy.
        val firstLink = lastRevision(s) == 0L && localHash(s).isBlank()
        runCatching {
            when (s) {
                Section.ADDONS -> {
                    applyAddons(obj, union = firstLink)
                    _addonsApplied.tryEmit(Unit)
                }
                Section.SETTINGS -> obj["values"]?.jsonObject?.let(settingsStore::applySyncSettingsValues)
                Section.PROFILES -> applyProfiles(obj, union = firstLink)
            }
        }.onFailure { Log.w(TAG, "Could not apply ${s.key}: ${it.javaClass.simpleName}") }

        setLastRevision(s, row.revision)
        Log.i(TAG, "Applied ${s.key} revision ${row.revision}")
        // After a first-link union the local copy may hold extra items: leave the hash empty so they upload.
        val unioned = firstLink && s != Section.SETTINGS
        setLocalHash(s, if (unioned) "" else hash(buildLocal(s)))
    }

    private suspend fun applyAddons(obj: JsonObject, union: Boolean) {
        val remote = obj["addons"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { a ->
                val url = a["manifestUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Triple(url, a["enabled"]?.jsonPrimitive?.booleanOrNull ?: true, a["order"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE)
            }
            .sortedBy { it.third }
        val hidden = obj["hiddenCatalogKeys"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .toSet()

        val installed = addonDao.getAll().map { it.manifestUrl }.toMutableSet()
        // installAddon may normalize the URL (stremio:// -> https://), so remember what it stored.
        val resolved = LinkedHashMap<String, Boolean>()
        for ((url, enabled, _) in remote) {
            val stored = if (url in installed) {
                url
            } else {
                addonRepository.installAddon(url).getOrNull()?.manifestUrl?.also { installed += it }
            }
            if (stored != null) resolved[stored] = enabled
        }
        val local = addonDao.getAll()
        if (!union) {
            local.filter { it.manifestUrl !in resolved }.forEach { addonDao.delete(it.manifestUrl) }
        }
        val order = resolved.keys.toList() +
            if (union) local.map { it.manifestUrl }.filter { it !in resolved } else emptyList()
        order.forEachIndexed { index, url ->
            addonDao.setPriority(url, index)
            resolved[url]?.let { addonDao.setEnabled(url, it) }
        }
        settingsStore.update { cur ->
            cur.copy(hiddenCatalogKeys = if (union) cur.hiddenCatalogKeys + hidden else hidden)
        }
    }

    private fun applyProfiles(obj: JsonObject, union: Boolean) {
        val remote = obj["profiles"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { p ->
                val id = p["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = p["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                UserProfile(
                    id = id,
                    name = name.take(40),
                    avatarColorHex = p["avatarColorHex"]?.jsonPrimitive?.longOrNull ?: 0xFF345372L,
                    avatarKey = p["avatarKey"]?.jsonPrimitive?.contentOrNull
                )
            }
            .distinctBy { it.id }
        if (remote.isEmpty()) return
        settingsStore.update { cur ->
            val localById = cur.profiles.associateBy { it.id }
            // PIN hashes never travel in this section: keep this device's PIN for known profiles.
            val merged = remote.map { r ->
                localById[r.id]?.copy(name = r.name, avatarColorHex = r.avatarColorHex, avatarKey = r.avatarKey) ?: r
            }
            val remoteIds = merged.map { it.id }.toSet()
            val profiles = if (union) merged + cur.profiles.filter { it.id !in remoteIds } else merged
            val active = if (profiles.any { it.id == cur.activeProfileId }) cur.activeProfileId else profiles.first().id
            cur.copy(profiles = profiles, activeProfileId = active)
        }
    }

    // ---------------------------------------------------------------- push

    private suspend fun pushIfChanged(s: Section) {
        if (s.encrypted && _status.value.keyMismatch) return
        val local = buildLocal(s)
        val h = hash(local)
        if (h == localHash(s)) return
        try {
            val newRevision = upload(s, local)
            setLastRevision(s, newRevision)
            setLocalHash(s, h)
            Log.i(TAG, "Uploaded ${s.key} -> revision $newRevision")
        } catch (_: ConflictException) {
            // Another device wrote first. Last writer wins per section, and theirs is newer.
            pullSection(s)
        }
    }

    private suspend fun upload(s: Section, local: JsonObject): Long {
        val payload = if (s.encrypted) {
            val key = account.encryptionKey() ?: throw AccountException(AccountError.WRONG_CREDENTIALS, "No key")
            AccountCrypto.encrypt(local.toString(), key).also { key.fill(0) }
        } else {
            local.toString()
        }
        val userId = account.state.value.userId
        val lastRev = lastRevision(s)
        val insert = lastRev == 0L
        val body = buildJsonObject {
            if (insert) {
                put("user_id", userId)
                put("section", s.key)
            }
            put("payload", payload)
            put("encrypted", s.encrypted)
            put("updated_by", account.deviceId)
        }.toString().toRequestBody(JSON_MEDIA)
        // Insert a new row, or update only if nobody else changed it since our last revision.
        val url = if (insert) {
            "$baseUrl/rest/v1/$TABLE".toHttpUrl()
        } else {
            "$baseUrl/rest/v1/$TABLE".toHttpUrl().newBuilder()
                .addQueryParameter("user_id", "eq.$userId")
                .addQueryParameter("section", "eq.${s.key}")
                .addQueryParameter("revision", "eq.$lastRev")
                .build()
        }
        account.authorizedCall { token ->
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Prefer", "return=representation")
            if (insert) builder.post(body).build() else builder.patch(body).build()
        }.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 409) throw ConflictException()
            if (!resp.isSuccessful) throw httpError(resp.code)
            val row = runCatching { json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject }.getOrNull()
                ?: throw ConflictException() // PATCH matched nothing: the revision moved on
            return row["revision"]?.jsonPrimitive?.longOrNull ?: (lastRev + 1)
        }
    }

    private suspend fun writeDeviceRow() {
        val body = buildJsonObject {
            put("user_id", account.state.value.userId)
            put("section", "device_${account.deviceId}")
            put("payload", buildJsonObject {
                put("formatVersion", 1)
                put("name", deviceName())
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("platform", if (isTv()) "android-tv" else "android")
                put("appVersion", BuildConfig.VERSION_NAME)
                put("lastSeen", System.currentTimeMillis())
            }.toString())
            put("encrypted", false)
            put("updated_by", account.deviceId)
        }.toString().toRequestBody(JSON_MEDIA)
        account.authorizedCall { token ->
            Request.Builder()
                .url("$baseUrl/rest/v1/$TABLE?on_conflict=user_id,section")
                .header("Authorization", "Bearer $token")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
        }.use { resp -> if (!resp.isSuccessful) throw httpError(resp.code) }
    }

    // ---------------------------------------------------------------- local snapshots

    private suspend fun buildLocal(s: Section): JsonObject = when (s) {
        Section.ADDONS -> {
            val addons = addonDao.getAll()
            buildJsonObject {
                put("formatVersion", 1)
                put("addons", buildJsonArray {
                    addons.forEachIndexed { index, a ->
                        add(buildJsonObject {
                            put("manifestUrl", a.manifestUrl)
                            put("name", a.name)
                            put("enabled", a.enabled)
                            put("order", index)
                        })
                    }
                })
                put("hiddenCatalogKeys", JsonArray(settingsStore.settings.value.hiddenCatalogKeys.sorted().map { JsonPrimitive(it) }))
            }
        }
        Section.SETTINGS -> buildJsonObject {
            put("formatVersion", 1)
            put("values", settingsStore.syncSettingsValues())
        }
        Section.PROFILES -> buildJsonObject {
            put("formatVersion", 1)
            put("profiles", buildJsonArray {
                settingsStore.settings.value.profiles.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                        put("avatarColorHex", p.avatarColorHex)
                        put("avatarKey", p.avatarKey?.let { JsonPrimitive(it) } ?: JsonNull)
                    })
                }
            })
        }
    }

    // ---------------------------------------------------------------- server rows

    private suspend fun fetchRows(select: String, sections: List<String>?): List<RemoteRow> {
        val url = "$baseUrl/rest/v1/$TABLE".toHttpUrl().newBuilder()
            .addQueryParameter("select", select)
            .addQueryParameter("user_id", "eq.${account.state.value.userId}")
            .apply {
                if (sections != null) addQueryParameter("section", "in.(${sections.joinToString(",")})")
            }
            .build()
        return account.authorizedCall { token ->
            Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        }.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code)
            json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                RemoteRow(
                    section = o["section"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    revision = o["revision"]?.jsonPrimitive?.longOrNull ?: 0L,
                    encrypted = o["encrypted"]?.jsonPrimitive?.booleanOrNull ?: false,
                    payload = o["payload"]?.jsonPrimitive?.contentOrNull
                )
            }
        }
    }

    private fun httpError(code: Int) = AccountException(
        when (code) {
            401, 403 -> AccountError.WRONG_CREDENTIALS
            429 -> AccountError.RATE_LIMITED
            else -> AccountError.SERVER
        },
        "HTTP $code"
    )

    // ---------------------------------------------------------------- per-section bookkeeping

    private fun resetSyncState(userId: String) {
        Section.entries.forEach {
            vault.remove(KEY_REV + it.key)
            vault.remove(KEY_HASH + it.key)
        }
        vault.remove(KEY_LAST_SYNC)
        vault.putString(KEY_SYNC_USER, userId)
        _status.value = SyncStatus()
    }

    private fun lastRevision(s: Section): Long = vault.getString(KEY_REV + s.key).toLongOrNull() ?: 0L
    private fun setLastRevision(s: Section, rev: Long) = vault.putString(KEY_REV + s.key, rev.toString())
    private fun localHash(s: Section): String = vault.getString(KEY_HASH + s.key)
    private fun setLocalHash(s: Section, h: String) = vault.putString(KEY_HASH + s.key, h)

    private fun hash(obj: JsonObject): String =
        MessageDigest.getInstance("SHA-256").digest(obj.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun deviceName(): String =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty()

    private fun isTv(): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    companion object {
        private const val TAG = "QuernSync"
        private const val TABLE = "quern_sync_sections"
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val PERIODIC_PULL_MS = 10 * 60 * 1000L
        private const val PUSH_DEBOUNCE_MS = 4_000L

        private const val KEY_REV = "sync_rev_"
        private const val KEY_HASH = "sync_hash_"
        private const val KEY_SYNC_USER = "sync_user"
        private const val KEY_LAST_SYNC = "sync_last_at"
    }
}
