package com.quern.app.data.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.quern.app.BuildConfig
import com.quern.app.domain.model.UpdateChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Serializable
data class AvailableUpdate(
    val version: String,
    val tagName: String,
    val publishedAtIso: String,
    val publishedDateText: String,
    val releaseNotes: String,
    val assetName: String,
    val downloadUrl: String,
    val assetSizeBytes: Long,
    val isPrerelease: Boolean
)

enum class ManualCheckResult {
    UP_TO_DATE,
    UPDATE_FOUND,
    FAILED_RATE_LIMIT,
    FAILED_NETWORK
}

enum class UpdateErrorKind {
    DOWNLOAD_FAILED,
    INSTALLER_UNAVAILABLE
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState {
        val progress: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data class ReadyToInstall(val apkPath: String) : UpdateDownloadState
    data object UnknownSourcesBlocked : UpdateDownloadState
    data class Error(val kind: UpdateErrorKind) : UpdateDownloadState
}

data class UpdateUiState(
    val checking: Boolean = false,
    val available: AvailableUpdate? = null,
    val dismissedVersion: String = "",
    val lastCheckedAtMs: Long = 0L,
    val manualCheckResult: ManualCheckResult? = null,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle
) {
    val hasUpdate: Boolean get() = available != null
    val showHomeBanner: Boolean get() = available != null && dismissedVersion != available.version
    val showSettingsDot: Boolean get() = available != null && dismissedVersion != available.version
}

/**
 * Checks official GitHub Releases for newer Quern APK releases (at most once every 12h
 * automatically, or on manual request), downloads verified release APKs to app cache, and
 * launches Android's system package installer via [FileProvider]. Never auto-installs.
 */
class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        cleanupStaleApks()
    }

    /** Automatic check gated by a 12-hour interval (silent on rate limits / network failure). */
    fun checkIfDue(channel: UpdateChannel) {
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val lastChannel = prefs.getString(KEY_LAST_CHANNEL, "").orEmpty()
        if (lastChannel == channel.name && now - lastChecked < CHECK_INTERVAL_MS) {
            return
        }
        startCheck(channel = channel, manual = false)
    }

    /** Manual check from Settings > System; bypasses the 12-hour throttle and reports status. */
    fun checkNow(channel: UpdateChannel) {
        startCheck(channel = channel, manual = true)
    }

    /** Hides the Home banner & Settings dot for the current available version until a newer one ships. */
    fun dismissCurrentUpdate() {
        val version = _state.value.available?.version ?: return
        prefs.edit().putString(KEY_DISMISSED_VERSION, version).apply()
        _state.value = _state.value.copy(dismissedVersion = version)
    }

    fun clearManualResult() {
        _state.value = _state.value.copy(manualCheckResult = null)
    }

    /**
     * Downloads the release APK into `cacheDir/updates/` with live progress, then opens Android's
     * system installer via [FileProvider]. Never installs silently or in the background.
     */
    fun downloadAndInstall(activityContext: Context) {
        val release = _state.value.available ?: return
        if (_state.value.downloadState is UpdateDownloadState.Downloading) return

        val updatesDir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        val targetFile = File(updatesDir, release.assetName)

        // Already downloaded with matching size -> go straight to installer prompt.
        if (targetFile.isFile && targetFile.length() > 0L &&
            (release.assetSizeBytes <= 0L || targetFile.length() == release.assetSizeBytes)
        ) {
            launchInstallerOrExplain(activityContext, targetFile)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.Downloading(0L, release.assetSizeBytes)
            )
            // Remove older cached APKs before downloading the new one.
            updatesDir.listFiles()?.forEach { f ->
                if (f.name != release.assetName) runCatching { f.delete() }
            }
            val tempFile = File(updatesDir, "${release.assetName}.part")
            runCatching { tempFile.delete() }

            val ok = try {
                val req = Request.Builder()
                    .url(release.downloadUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "APK download failed: HTTP ${resp.code}")
                        false
                    } else {
                        val body = resp.body ?: return@use false
                        val total = body.contentLength().takeIf { it > 0L } ?: release.assetSizeBytes
                        var downloaded = 0L
                        var lastEmit = 0L
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { out ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    downloaded += n
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 120L || downloaded == total) {
                                        lastEmit = now
                                        _state.value = _state.value.copy(
                                            downloadState = UpdateDownloadState.Downloading(downloaded, total)
                                        )
                                    }
                                }
                                out.flush()
                            }
                        }
                        if (downloaded <= 0L || (total > 0L && downloaded != total)) {
                            runCatching { tempFile.delete() }
                            false
                        } else {
                            if (targetFile.exists()) targetFile.delete()
                            tempFile.renameTo(targetFile)
                        }
                    }
                }
            } catch (e: CancellationException) {
                runCatching { tempFile.delete() }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "APK download error: ${e.javaClass.simpleName}")
                runCatching { tempFile.delete() }
                false
            }

            if (!ok || !targetFile.isFile) {
                _state.value = _state.value.copy(
                    downloadState = UpdateDownloadState.Error(UpdateErrorKind.DOWNLOAD_FAILED)
                )
                return@launch
            }

            withContext(Dispatchers.Main) {
                launchInstallerOrExplain(activityContext, targetFile)
            }
        }
    }

    /** Opens Android's "Install unknown apps" settings screen for Quern (if supported on this TV/phone). */
    fun openUnknownSourcesSettings(activityContext: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            activityContext.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "Device blocked ACTION_MANAGE_UNKNOWN_APP_SOURCES: ${it.javaClass.simpleName}")
            false
        }
    }

    private fun launchInstallerOrExplain(activityContext: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = _state.value.copy(downloadState = UpdateDownloadState.UnknownSourcesBlocked)
            return
        }
        val launched = runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "Failed to launch system installer: ${it.javaClass.simpleName}")
            false
        }

        _state.value = _state.value.copy(
            downloadState = if (launched) {
                UpdateDownloadState.ReadyToInstall(apkFile.absolutePath)
            } else {
                UpdateDownloadState.Error(UpdateErrorKind.INSTALLER_UNAVAILABLE)
            }
        )
    }

    private fun startCheck(channel: UpdateChannel, manual: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _state.value = _state.value.copy(
                checking = true,
                manualCheckResult = if (manual) null else _state.value.manualCheckResult
            )
            val req = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 403 || resp.code == 429) {
                        Log.i(TAG, "GitHub releases rate-limited (HTTP ${resp.code})")
                        _state.value = _state.value.copy(
                            checking = false,
                            manualCheckResult = if (manual) ManualCheckResult.FAILED_RATE_LIMIT else null
                        )
                        return@launch
                    }
                    if (!resp.isSuccessful) {
                        Log.i(TAG, "GitHub releases check failed: HTTP ${resp.code}")
                        _state.value = _state.value.copy(
                            checking = false,
                            manualCheckResult = if (manual) ManualCheckResult.FAILED_NETWORK else null
                        )
                        return@launch
                    }

                    val body = resp.body?.string().orEmpty()
                    val best = parseLatestRelease(body, channel, BuildConfig.VERSION_NAME)
                    val now = System.currentTimeMillis()
                    persistCheckResult(now, channel, best)

                    val prevVersion = _state.value.available?.version
                    val nextDownloadState = if (best != null && best.version == prevVersion) {
                        _state.value.downloadState
                    } else {
                        UpdateDownloadState.Idle
                    }

                    _state.value = _state.value.copy(
                        checking = false,
                        available = best,
                        lastCheckedAtMs = now,
                        manualCheckResult = if (manual) {
                            if (best != null) ManualCheckResult.UPDATE_FOUND else ManualCheckResult.UP_TO_DATE
                        } else null,
                        downloadState = nextDownloadState
                    )
                }
            } catch (_: IOException) {
                // Silent on background check; concise status only when user clicked Check for Updates.
                _state.value = _state.value.copy(
                    checking = false,
                    manualCheckResult = if (manual) ManualCheckResult.FAILED_NETWORK else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check error: ${e.javaClass.simpleName}")
                _state.value = _state.value.copy(
                    checking = false,
                    manualCheckResult = if (manual) ManualCheckResult.FAILED_NETWORK else null
                )
            }
        }
    }

    private fun parseLatestRelease(
        rawJson: String,
        channel: UpdateChannel,
        currentVersionName: String
    ): AvailableUpdate? {
        val currentSemVer = SemVer.parse(currentVersionName) ?: return null
        val arr = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull() ?: return null

        var best: Pair<SemVer, AvailableUpdate>? = null
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val draft = obj["draft"]?.jsonPrimitive?.booleanOrNull == true
            if (draft) continue
            val prerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true
            if (channel == UpdateChannel.STABLE && prerelease) continue

            val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val releaseSemVer = SemVer.parse(tagName) ?: continue
            if (releaseSemVer <= currentSemVer) continue

            // Strictly match official release APK asset: Quern-vX.Y.Z-release.apk (never CI artifacts).
            val matchedAsset = obj["assets"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstNotNullOfOrNull { asset ->
                    val name = asset["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val match = ASSET_NAME_REGEX.matchEntire(name) ?: return@firstNotNullOfOrNull null
                    val assetCore = match.groupValues[1]
                    if (assetCore != "${releaseSemVer.major}.${releaseSemVer.minor}.${releaseSemVer.patch}") {
                        return@firstNotNullOfOrNull null
                    }
                    val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (!url.startsWith(OFFICIAL_DOWNLOAD_PREFIX)) return@firstNotNullOfOrNull null
                    val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
                    Triple(name, url, size)
                } ?: continue

            val publishedIso = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val dateText = publishedIso.substringBefore("T").ifBlank { publishedIso }
            val notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val normalizedVersion = tagName.removePrefix("v").removePrefix("V")

            val candidate = AvailableUpdate(
                version = normalizedVersion,
                tagName = tagName,
                publishedAtIso = publishedIso,
                publishedDateText = dateText,
                releaseNotes = notes,
                assetName = matchedAsset.first,
                downloadUrl = matchedAsset.second,
                assetSizeBytes = matchedAsset.third,
                isPrerelease = prerelease
            )
            if (best == null || releaseSemVer > best.first) {
                best = releaseSemVer to candidate
            }
        }
        return best?.second
    }

    private fun loadInitialState(): UpdateUiState {
        val lastChecked = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val dismissed = prefs.getString(KEY_DISMISSED_VERSION, "").orEmpty()
        val cachedJson = prefs.getString(KEY_CACHED_UPDATE, null)
        val cached = cachedJson?.let {
            runCatching { json.decodeFromString<AvailableUpdate>(it) }.getOrNull()
        }?.takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) }

        if (cachedJson != null && cached == null) {
            prefs.edit().remove(KEY_CACHED_UPDATE).apply()
        }
        return UpdateUiState(
            available = cached,
            dismissedVersion = dismissed,
            lastCheckedAtMs = lastChecked
        )
    }

    private fun persistCheckResult(nowMs: Long, channel: UpdateChannel, update: AvailableUpdate?) {
        prefs.edit().apply {
            putLong(KEY_LAST_CHECK_AT, nowMs)
            putString(KEY_LAST_CHANNEL, channel.name)
            if (update != null) {
                putString(KEY_CACHED_UPDATE, json.encodeToString(update))
            } else {
                remove(KEY_CACHED_UPDATE)
            }
            apply()
        }
    }

    private fun cleanupStaleApks() {
        val dir = File(context.cacheDir, UPDATES_DIR)
        if (!dir.isDirectory) return
        val currentSemVer = SemVer.parse(BuildConfig.VERSION_NAME) ?: return
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part")) {
                runCatching { f.delete() }
                return@forEach
            }
            val match = ASSET_NAME_REGEX.matchEntire(f.name)
            val fileSemVer = match?.groupValues?.getOrNull(1)?.let(SemVer::parse)
            if (fileSemVer == null || fileSemVer <= currentSemVer) {
                runCatching { f.delete() }
            }
        }
    }

    internal data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: String? = null
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            val a = prerelease
            val b = other.prerelease
            if (a == null && b == null) return 0
            if (a == null) return 1   // stable > prerelease
            if (b == null) return -1  // prerelease < stable
            val partsA = a.split('.', '-')
            val partsB = b.split('.', '-')
            val len = maxOf(partsA.size, partsB.size)
            for (i in 0 until len) {
                val pa = partsA.getOrNull(i) ?: return -1
                val pb = partsB.getOrNull(i) ?: return 1
                val na = pa.toIntOrNull()
                val nb = pb.toIntOrNull()
                val cmp = if (na != null && nb != null) na.compareTo(nb) else pa.compareTo(pb)
                if (cmp != 0) return cmp
            }
            return 0
        }

        companion object {
            private val REGEX = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+.*)?$""")

            fun parse(raw: String): SemVer? {
                val m = REGEX.matchEntire(raw.trim()) ?: return null
                val maj = m.groupValues[1].toIntOrNull() ?: return null
                val min = m.groupValues[2].toIntOrNull() ?: return null
                val pat = m.groupValues[3].toIntOrNull() ?: return null
                val pre = m.groupValues[4].takeIf { it.isNotBlank() }
                return SemVer(maj, min, pat, pre)
            }
        }
    }

    companion object {
        private const val TAG = "QuernUpdate"
        private const val PREFS_NAME = "quern_app_updates"
        private const val KEY_LAST_CHECK_AT = "last_check_at_ms"
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_CACHED_UPDATE = "cached_update_json"
        private const val UPDATES_DIR = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val USER_AGENT = "Quern-Android"
        private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

        private const val RELEASES_API_URL =
            "https://api.github.com/repos/ShadowStudioApps/Quern/releases"
        private const val OFFICIAL_DOWNLOAD_PREFIX =
            "https://github.com/ShadowStudioApps/Quern/releases/download/"
        private val ASSET_NAME_REGEX = Regex("""^Quern-v(\d+\.\d+\.\d+)-release\.apk$""")

        fun isNewerVersion(candidate: String, current: String = BuildConfig.VERSION_NAME): Boolean {
            val c = SemVer.parse(candidate) ?: return false
            val cur = SemVer.parse(current) ?: return false
            return c > cur
        }
    }
}
