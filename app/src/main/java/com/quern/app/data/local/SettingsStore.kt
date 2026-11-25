package com.quern.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.quern.app.domain.model.AccentColorChoice
import com.quern.app.domain.model.AppFontChoice
import com.quern.app.domain.model.AppIconOption
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppSettings
import com.quern.app.domain.model.AppThemePreset
import com.quern.app.domain.model.AudioLanguage
import com.quern.app.domain.model.BufferProfile
import com.quern.app.domain.model.CardCornerPreset
import com.quern.app.domain.model.CardShapePreset
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.domain.model.ContinueWatchingAction
import com.quern.app.domain.model.ContinueWatchingSort
import com.quern.app.domain.model.DefaultAspectMode
import com.quern.app.domain.model.FontScalePreset
import com.quern.app.domain.model.NavigationStyle
import com.quern.app.domain.model.PlayerEngine
import com.quern.app.domain.model.QuernDevicePreset
import com.quern.app.domain.model.RowOrderPreset
import com.quern.app.domain.model.SubtitleLanguage
import com.quern.app.domain.model.SubtitleSize
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.domain.model.UpdateChannel
import com.quern.app.domain.model.UserProfile
import com.quern.app.ui.components.isTvHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Strictly non-secret settings snapshot for portable export/import.
 * Never includes API keys, tokens, credentials, server URLs, or profile PINs.
 */
@Serializable
private data class NonSecretSettingsSnapshot(
    val version: Int = 1,
    val language: String = AppLanguage.SYSTEM.name,
    val theme: String = AppThemePreset.GLOSSY_BLACK.name,
    val amoledPureBlack: Boolean = true,
    val accentColor: String = AccentColorChoice.THEME_DEFAULT.name,
    val fontChoice: String = AppFontChoice.DEFAULT.name,
    val fontScale: String = FontScalePreset.STANDARD.name,
    val navigationStyle: String = NavigationStyle.SIDEBAR_LEFT.name,
    val showHeroBanner: Boolean = true,
    val showPosterLabels: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val rowOrder: String = RowOrderPreset.DEFAULT.name,
    val showContinueWatching: Boolean = true,
    val continueWatchingSort: String = ContinueWatchingSort.RECENT.name,
    val continueWatchingAction: String = ContinueWatchingAction.OPEN_DETAILS.name,
    val cardSize: String = CardSizePreset.STANDARD.name,
    val cardShape: String = CardShapePreset.PORTRAIT.name,
    val cardCorner: String = CardCornerPreset.ROUNDED.name,
    val playerEngine: String = PlayerEngine.MEDIA3_EXOPLAYER.name,
    val preferExternalPlayer: Boolean = false,
    val preferredExternalPlayerPackage: String = "",
    val audioVolumeBoostEnabled: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f,
    val defaultAspectMode: String = DefaultAspectMode.FIT.name,
    val bufferProfile: String = BufferProfile.BALANCED.name,
    val subtitleLanguage: String = SubtitleLanguage.ARABIC.name,
    val subtitleSize: String = SubtitleSize.MEDIUM.name,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val audioLanguage: String = AudioLanguage.ORIGINAL.name,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val nextEpisodeCountdownSec: Int = 10,
    val hiddenCatalogKeys: List<String> = emptyList(),
    val updateChannel: String = UpdateChannel.STABLE.name,
    val showWhatsNewAfterUpdate: Boolean = true,
    val crashReportingOptIn: Boolean = false
)

enum class PinMigrationState {
    IDLE,
    MIGRATING,
    DONE,
    FAILED
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quern_app_settings_v4", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateLock = Any()

    private val defaultMatchFrameRate = isTvHardware(context)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pinMigrationState = MutableStateFlow(PinMigrationState.IDLE)
    val pinMigrationState: StateFlow<PinMigrationState> = _pinMigrationState.asStateFlow()

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        migrateLegacyPlaintextPinsAsync()
    }

    fun retryPinMigrationIfNeeded() {
        migrateLegacyPlaintextPinsAsync()
    }

    /**
     * Idempotent background migration of any legacy plaintext profile PINs to salted PBKDF2 hashes.
     * - Sets PinMigrationState.MIGRATING synchronously before returning so UI can gate locked profile selection.
     * - Runs PBKDF2 off the main thread on Dispatchers.Default.
     * - Builds and validates the complete new profiles JSON before overwriting profiles_json.
     * - Never throws uncaught and never overwrites original profiles_json on failure (exposes FAILED state).
     * - Profiles without a PIN or already hashed are untouched.
     */
    private fun migrateLegacyPlaintextPinsAsync() {
        val snapshotToMigrate: List<UserProfile>
        synchronized(stateLock) {
            if (_pinMigrationState.value == PinMigrationState.MIGRATING) return
            val currentProfiles = _settings.value.profiles
            val needsMigration = currentProfiles.any { !it.pinCode.isNullOrBlank() && !PinHasher.isHashed(it.pinCode) }
            if (!needsMigration) {
                _pinMigrationState.value = PinMigrationState.DONE
                return
            }
            snapshotToMigrate = currentProfiles
            _pinMigrationState.value = PinMigrationState.MIGRATING
        }

        migrationScope.launch {
            try {
                // Hash legacy plaintext PINs off the main thread using captured immutable snapshot
                val hashedById = mutableMapOf<String, String>()
                for (profile in snapshotToMigrate) {
                    val pin = profile.pinCode
                    if (!pin.isNullOrBlank() && !PinHasher.isHashed(pin)) {
                        hashedById[profile.id] = PinHasher.hashPin(pin)
                    }
                }

                // Atomically merge, pre-serialize, and verify the complete profiles JSON before committing
                synchronized(stateLock) {
                    val curr = _settings.value
                    val merged = curr.profiles.map { p ->
                        val pin = p.pinCode
                        if (!pin.isNullOrBlank() && !PinHasher.isHashed(pin)) {
                            val preHashed = hashedById[p.id] ?: PinHasher.hashPin(pin)
                            p.copy(pinCode = preHashed)
                        } else {
                            p
                        }
                    }
                    // Build & validate full JSON BEFORE touching SharedPreferences
                    val encodedProfilesJson = json.encodeToString(merged)
                    val verified = json.decodeFromString<List<UserProfile>>(encodedProfilesJson)
                    check(verified.size == merged.size) { "Profile migration JSON verification mismatch" }
                    check(verified.all { it.pinCode.isNullOrBlank() || PinHasher.isHashed(it.pinCode) }) {
                        "Profile migration verification failed"
                    }

                    val next = curr.copy(profiles = merged)
                    val committed = prefs.edit().putString("profiles_json", encodedProfilesJson).commit()
                    check(committed) { "Failed to persist migrated profiles_json" }
                    _settings.value = next
                    _pinMigrationState.value = PinMigrationState.DONE
                }
            } catch (_: Throwable) {
                // Preserve original profiles_json intact and surface FAILED so UI can offer retry/reset instead of locking out
                _pinMigrationState.value = PinMigrationState.FAILED
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(stateLock) {
            val next = transform(_settings.value)
            save(next)
            _settings.value = next
        }
    }

    fun reset() = synchronized(stateLock) {
        check(prefs.edit().clear().commit()) { "Could not reset settings" }
        _settings.value = AppSettings(matchFrameRate = defaultMatchFrameRate)
    }

    fun applyPreset(preset: QuernDevicePreset) {
        update { curr ->
            when (preset) {
                QuernDevicePreset.TV -> curr.copy(
                    uiModeOverride = UiModeOverride.TV,
                    navigationStyle = NavigationStyle.SIDEBAR_LEFT,
                    showHeroBanner = true,
                    showPosterLabels = true,
                    cardSize = CardSizePreset.STANDARD,
                    cardCorner = CardCornerPreset.ROUNDED,
                    fontScale = FontScalePreset.STANDARD,
                    bufferProfile = BufferProfile.BALANCED,
                    amoledPureBlack = true
                )
                QuernDevicePreset.PHONE -> curr.copy(
                    uiModeOverride = UiModeOverride.PHONE,
                    navigationStyle = NavigationStyle.TOP_BAR,
                    showHeroBanner = true,
                    showPosterLabels = true,
                    cardSize = CardSizePreset.COMPACT,
                    cardCorner = CardCornerPreset.ROUNDED,
                    fontScale = FontScalePreset.STANDARD,
                    bufferProfile = BufferProfile.BALANCED
                )
                QuernDevicePreset.LOW_END -> curr.copy(
                    showHeroBanner = false,
                    showPosterLabels = true,
                    cardSize = CardSizePreset.COMPACT,
                    cardCorner = CardCornerPreset.SHARP,
                    amoledPureBlack = true,
                    fontScale = FontScalePreset.STANDARD,
                    bufferProfile = BufferProfile.LOW
                )
            }
        }
    }

    /**
     * Values for the account `settings` section (docs/account-sync-format.md). Leaves out keys that
     * belong to one device (navigation, card size, buffer, player app) and hidden catalogs, which
     * travel with the `addons` section.
     */
    fun syncSettingsValues(): JsonObject {
        val all = syncJson.encodeToJsonElement(buildSnapshot()).jsonObject
        return JsonObject(all.filterKeys { it !in SYNC_EXCLUDED_KEYS })
    }

    /** Applies a remote `settings` section. Unknown keys are ignored; unknown enum values keep the current value. */
    fun applySyncSettingsValues(values: JsonObject) {
        val current = syncJson.encodeToJsonElement(buildSnapshot()).jsonObject
        val incoming = values.filterKeys { it in current && it !in SYNC_EXCLUDED_KEYS }
        val snap = syncJson.decodeFromJsonElement<NonSecretSettingsSnapshot>(JsonObject(current + incoming))
        applySnapshot(snap)
    }

    /**
     * Values for the per-device-class account section (`settings_tv` or `settings_phone`,
     * see docs/account-sync-format.md). Kept as a flat key/value object so future keys
     * (such as UI scale) stay forward-compatible.
     */
    fun syncDeviceSettingsValues(): JsonObject {
        val s = _settings.value
        return buildJsonObject {
            put("navigationStyle", s.navigationStyle.name)
            put("cardSize", s.cardSize.name)
            put("fontScale", s.fontScale.name)
            put("bufferProfile", s.bufferProfile.name)
            put("playerEngine", s.playerEngine.name)
            put("preferExternalPlayer", s.preferExternalPlayer)
            put("preferredExternalPlayerPackage", s.preferredExternalPlayerPackage)
            put("appIcon", s.appIcon.name)
            put("uiModeOverride", s.uiModeOverride.name)
            put("screenScalePercent", s.screenScalePercent)
            put("matchFrameRate", s.matchFrameRate)
        }
    }

    /**
     * Applies a remote `settings_tv` or `settings_phone` section. Unknown keys are ignored and
     * unknown enum values keep the current value.
     */
    fun applySyncDeviceSettingsValues(values: JsonObject) {
        fun str(key: String): String? = values[key]?.jsonPrimitive?.contentOrNull
        val rawEngine = str("playerEngine")
        update { curr ->
            val nextEngine = when {
                rawEngine == null -> curr.playerEngine
                rawEngine == "MPV" -> PlayerEngine.EXTERNAL
                else -> runCatching { PlayerEngine.valueOf(rawEngine) }.getOrDefault(curr.playerEngine)
            }
            val incomingPkg = str("preferredExternalPlayerPackage")
            val nextPkg = when {
                rawEngine == "MPV" && incomingPkg.isNullOrBlank() -> "is.xyz.mpv"
                incomingPkg != null -> incomingPkg
                else -> curr.preferredExternalPlayerPackage
            }
            curr.copy(
                navigationStyle = str("navigationStyle")
                    ?.let { runCatching { NavigationStyle.valueOf(it) }.getOrNull() }
                    ?: curr.navigationStyle,
                cardSize = str("cardSize")
                    ?.let { runCatching { CardSizePreset.valueOf(it) }.getOrNull() }
                    ?: curr.cardSize,
                fontScale = str("fontScale")
                    ?.let { runCatching { FontScalePreset.valueOf(it) }.getOrNull() }
                    ?: curr.fontScale,
                bufferProfile = str("bufferProfile")
                    ?.let { runCatching { BufferProfile.valueOf(it) }.getOrNull() }
                    ?: curr.bufferProfile,
                playerEngine = nextEngine,
                preferExternalPlayer = values["preferExternalPlayer"]?.jsonPrimitive?.booleanOrNull
                    ?: curr.preferExternalPlayer,
                preferredExternalPlayerPackage = nextPkg,
                appIcon = str("appIcon")
                    ?.let { runCatching { AppIconOption.valueOf(it) }.getOrNull() }
                    ?: curr.appIcon,
                uiModeOverride = str("uiModeOverride")
                    ?.let { runCatching { UiModeOverride.valueOf(it) }.getOrNull() }
                    ?: curr.uiModeOverride,
                screenScalePercent = values["screenScalePercent"]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(75, 100)
                    ?: curr.screenScalePercent,
                matchFrameRate = (values["matchFrameRate"] as? JsonPrimitive)?.booleanOrNull
                    ?: curr.matchFrameRate
            )
        }
    }

    fun exportNonSecretSettingsCode(): String {
        val rawJson = json.encodeToString(buildSnapshot())
        val encoded = Base64.encodeToString(rawJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "QUERN-CFG-1:$encoded"
    }

    private fun buildSnapshot(): NonSecretSettingsSnapshot {
        val s = _settings.value
        return NonSecretSettingsSnapshot(
            language = s.language.name,
            theme = s.theme.name,
            amoledPureBlack = s.amoledPureBlack,
            accentColor = s.accentColor.name,
            fontChoice = s.fontChoice.name,
            fontScale = s.fontScale.name,
            navigationStyle = s.navigationStyle.name,
            showHeroBanner = s.showHeroBanner,
            showPosterLabels = s.showPosterLabels,
            hideUnreleasedContent = s.hideUnreleasedContent,
            rowOrder = s.rowOrder.name,
            showContinueWatching = s.showContinueWatching,
            continueWatchingSort = s.continueWatchingSort.name,
            continueWatchingAction = s.continueWatchingAction.name,
            cardSize = s.cardSize.name,
            cardShape = s.cardShape.name,
            cardCorner = s.cardCorner.name,
            playerEngine = s.playerEngine.name,
            preferExternalPlayer = s.preferExternalPlayer,
            preferredExternalPlayerPackage = s.preferredExternalPlayerPackage,
            audioVolumeBoostEnabled = s.audioVolumeBoostEnabled,
            defaultPlaybackSpeed = s.defaultPlaybackSpeed,
            defaultAspectMode = s.defaultAspectMode.name,
            bufferProfile = s.bufferProfile.name,
            subtitleLanguage = s.subtitleLanguage.name,
            subtitleSize = s.subtitleSize.name,
            subtitleColorHex = s.subtitleColorHex,
            audioLanguage = s.audioLanguage.name,
            skipIntroEnabled = s.skipIntroEnabled,
            skipOutroEnabled = s.skipOutroEnabled,
            autoPlayNext = s.autoPlayNext,
            nextEpisodeCountdownSec = s.nextEpisodeCountdownSec,
            hiddenCatalogKeys = s.hiddenCatalogKeys.toList(),
            updateChannel = s.updateChannel.name,
            showWhatsNewAfterUpdate = s.showWhatsNewAfterUpdate,
            crashReportingOptIn = s.crashReportingOptIn
        )
    }

    fun importNonSecretSettingsCode(rawCode: String): Result<String> = runCatching {
        val cleaned = rawCode.trim()
        val payload = when {
            cleaned.startsWith("QUERN-CFG-1:") -> {
                val b64 = cleaned.removePrefix("QUERN-CFG-1:").trim()
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }
            cleaned.startsWith("{") -> cleaned
            else -> error("Invalid settings code format (expected QUERN-CFG-1:...)")
        }
        val snap = json.decodeFromString<NonSecretSettingsSnapshot>(payload)
        applySnapshot(snap)
        "Settings imported successfully (secrets untouched)."
    }

    private fun applySnapshot(snap: NonSecretSettingsSnapshot) {
        update { curr ->
            curr.copy(
                language = runCatching { AppLanguage.valueOf(snap.language) }.getOrDefault(curr.language),
                theme = runCatching { AppThemePreset.valueOf(snap.theme) }.getOrDefault(curr.theme),
                amoledPureBlack = snap.amoledPureBlack,
                accentColor = runCatching { AccentColorChoice.valueOf(snap.accentColor) }.getOrDefault(curr.accentColor),
                fontChoice = runCatching { AppFontChoice.valueOf(snap.fontChoice) }.getOrDefault(curr.fontChoice),
                fontScale = runCatching { FontScalePreset.valueOf(snap.fontScale) }.getOrDefault(curr.fontScale),
                navigationStyle = runCatching { NavigationStyle.valueOf(snap.navigationStyle) }.getOrDefault(curr.navigationStyle),
                showHeroBanner = snap.showHeroBanner,
                showPosterLabels = snap.showPosterLabels,
                hideUnreleasedContent = snap.hideUnreleasedContent,
                rowOrder = runCatching { RowOrderPreset.valueOf(snap.rowOrder) }.getOrDefault(curr.rowOrder),
                showContinueWatching = snap.showContinueWatching,
                continueWatchingSort = runCatching { ContinueWatchingSort.valueOf(snap.continueWatchingSort) }.getOrDefault(curr.continueWatchingSort),
                continueWatchingAction = runCatching { ContinueWatchingAction.valueOf(snap.continueWatchingAction) }.getOrDefault(curr.continueWatchingAction),
                cardSize = runCatching { CardSizePreset.valueOf(snap.cardSize) }.getOrDefault(curr.cardSize),
                cardShape = runCatching { CardShapePreset.valueOf(snap.cardShape) }.getOrDefault(curr.cardShape),
                cardCorner = runCatching { CardCornerPreset.valueOf(snap.cardCorner) }.getOrDefault(curr.cardCorner),
                playerEngine = if (snap.playerEngine == "MPV") PlayerEngine.EXTERNAL
                    else runCatching { PlayerEngine.valueOf(snap.playerEngine) }.getOrDefault(curr.playerEngine),
                preferExternalPlayer = snap.preferExternalPlayer,
                preferredExternalPlayerPackage = if (snap.playerEngine == "MPV" && snap.preferredExternalPlayerPackage.isBlank()) {
                    "is.xyz.mpv"
                } else {
                    snap.preferredExternalPlayerPackage
                },
                audioVolumeBoostEnabled = snap.audioVolumeBoostEnabled,
                defaultPlaybackSpeed = snap.defaultPlaybackSpeed.coerceIn(0.5f, 2.5f),
                defaultAspectMode = runCatching { DefaultAspectMode.valueOf(snap.defaultAspectMode) }.getOrDefault(curr.defaultAspectMode),
                bufferProfile = runCatching { BufferProfile.valueOf(snap.bufferProfile) }.getOrDefault(curr.bufferProfile),
                subtitleLanguage = runCatching { SubtitleLanguage.valueOf(snap.subtitleLanguage) }.getOrDefault(curr.subtitleLanguage),
                subtitleSize = runCatching { SubtitleSize.valueOf(snap.subtitleSize) }.getOrDefault(curr.subtitleSize),
                subtitleColorHex = snap.subtitleColorHex,
                audioLanguage = runCatching { AudioLanguage.valueOf(snap.audioLanguage) }.getOrDefault(curr.audioLanguage),
                skipIntroEnabled = snap.skipIntroEnabled,
                skipOutroEnabled = snap.skipOutroEnabled,
                autoPlayNext = snap.autoPlayNext,
                nextEpisodeCountdownSec = snap.nextEpisodeCountdownSec.coerceIn(3, 30),
                hiddenCatalogKeys = snap.hiddenCatalogKeys.toSet(),
                updateChannel = runCatching { UpdateChannel.valueOf(snap.updateChannel) }.getOrDefault(curr.updateChannel),
                showWhatsNewAfterUpdate = snap.showWhatsNewAfterUpdate,
                crashReportingOptIn = snap.crashReportingOptIn
            )
        }
    }

    fun exportNonSecretSettingsPrettyJson(): String {
        val code = exportNonSecretSettingsCode().removePrefix("QUERN-CFG-1:")
        return String(Base64.decode(code, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        val lang = prefs.getString("lang", defaults.language.name)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: defaults.language
        val theme = prefs.getString("theme", defaults.theme.name)
            ?.let { runCatching { AppThemePreset.valueOf(it) }.getOrNull() } ?: defaults.theme
        val amoled = prefs.getBoolean("amoled_black", defaults.amoledPureBlack)
        val accent = prefs.getString("accent_color", defaults.accentColor.name)
            ?.let { runCatching { AccentColorChoice.valueOf(it) }.getOrNull() } ?: defaults.accentColor
        val fontChoice = prefs.getString("font_choice", defaults.fontChoice.name)
            ?.let { runCatching { AppFontChoice.valueOf(it) }.getOrNull() } ?: defaults.fontChoice
        val fontScale = prefs.getString("font_scale", defaults.fontScale.name)
            ?.let { runCatching { FontScalePreset.valueOf(it) }.getOrNull() } ?: defaults.fontScale
        val icon = prefs.getString("app_icon", defaults.appIcon.name)
            ?.let { runCatching { AppIconOption.valueOf(it) }.getOrNull() } ?: defaults.appIcon
        val navStyle = prefs.getString("nav_style", defaults.navigationStyle.name)
            ?.let { runCatching { NavigationStyle.valueOf(it) }.getOrNull() } ?: defaults.navigationStyle
        val subLang = prefs.getString("sub_lang", defaults.subtitleLanguage.name)
            ?.let { runCatching { SubtitleLanguage.valueOf(it) }.getOrNull() } ?: defaults.subtitleLanguage
        val subSize = prefs.getString("sub_size", defaults.subtitleSize.name)
            ?.let { runCatching { SubtitleSize.valueOf(it) }.getOrNull() } ?: defaults.subtitleSize
        val subColor = prefs.getLong("sub_color", defaults.subtitleColorHex)
        val audioLang = prefs.getString("audio_lang", defaults.audioLanguage.name)
            ?.let { runCatching { AudioLanguage.valueOf(it) }.getOrNull() } ?: defaults.audioLanguage
        val skipIntro = prefs.getBoolean("skip_intro", defaults.skipIntroEnabled)
        val skipOutro = prefs.getBoolean("skip_outro", defaults.skipOutroEnabled)
        val autoPlayNext = prefs.getBoolean("auto_next", defaults.autoPlayNext)
        val countdown = prefs.getInt("next_countdown", defaults.nextEpisodeCountdownSec)
        val rawEngine = prefs.getString("player_engine", defaults.playerEngine.name)
        val playerEngine = if (rawEngine == "MPV") {
            PlayerEngine.EXTERNAL
        } else {
            rawEngine?.let { runCatching { PlayerEngine.valueOf(it) }.getOrNull() } ?: defaults.playerEngine
        }
        val externalPlayer = prefs.getBoolean("ext_player", defaults.preferExternalPlayer)
        val rawExternalPkg = prefs.getString("ext_player_pkg", defaults.preferredExternalPlayerPackage) ?: defaults.preferredExternalPlayerPackage
        val externalPkg = if (rawEngine == "MPV" && rawExternalPkg.isBlank()) "is.xyz.mpv" else rawExternalPkg
        val audioBoost = prefs.getBoolean("audio_vol_boost", defaults.audioVolumeBoostEnabled)
        val defaultSpeed = prefs.getFloat("default_speed", defaults.defaultPlaybackSpeed)
        val defaultAspect = prefs.getString("default_aspect", defaults.defaultAspectMode.name)
            ?.let { runCatching { DefaultAspectMode.valueOf(it) }.getOrNull() } ?: defaults.defaultAspectMode
        val bufferProfile = prefs.getString("buffer_profile", defaults.bufferProfile.name)
            ?.let { runCatching { BufferProfile.valueOf(it) }.getOrNull() } ?: defaults.bufferProfile
        val showHero = prefs.getBoolean("show_hero", defaults.showHeroBanner)
        val showPosterLabels = prefs.getBoolean("show_poster_labels", defaults.showPosterLabels)
        val hideUnreleased = prefs.getBoolean("hide_unreleased", defaults.hideUnreleasedContent)
        val rowOrder = prefs.getString("row_order", defaults.rowOrder.name)
            ?.let { runCatching { RowOrderPreset.valueOf(it) }.getOrNull() } ?: defaults.rowOrder
        val showCw = prefs.getBoolean("show_cw", defaults.showContinueWatching)
        val cwSort = prefs.getString("cw_sort", defaults.continueWatchingSort.name)
            ?.let { runCatching { ContinueWatchingSort.valueOf(it) }.getOrNull() } ?: defaults.continueWatchingSort
        val cwAction = prefs.getString("cw_action", defaults.continueWatchingAction.name)
            ?.let { runCatching { ContinueWatchingAction.valueOf(it) }.getOrNull() } ?: defaults.continueWatchingAction
        val cardSize = prefs.getString("card_size", defaults.cardSize.name)
            ?.let { runCatching { CardSizePreset.valueOf(it) }.getOrNull() } ?: defaults.cardSize
        val cardShape = prefs.getString("card_shape", defaults.cardShape.name)
            ?.let { runCatching { CardShapePreset.valueOf(it) }.getOrNull() } ?: defaults.cardShape
        val cardCorner = prefs.getString("card_corner", defaults.cardCorner.name)
            ?.let { runCatching { CardCornerPreset.valueOf(it) }.getOrNull() } ?: defaults.cardCorner
        val uiMode = prefs.getString("ui_mode", defaults.uiModeOverride.name)
            ?.let { runCatching { UiModeOverride.valueOf(it) }.getOrNull() } ?: defaults.uiModeOverride
        val screenScalePercent = prefs.getInt("screen_scale_pct", defaults.screenScalePercent).coerceIn(75, 100)
        val hiddenCatalogs = prefs.getStringSet("hidden_catalog_keys", emptySet())?.toSet() ?: emptySet()
        val updateChannel = prefs.getString("update_channel", defaults.updateChannel.name)
            ?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() } ?: defaults.updateChannel
        val showWhatsNewAfter = prefs.getBoolean("show_whats_new_after_update", defaults.showWhatsNewAfterUpdate)
        val crashOptIn = prefs.getBoolean("crash_reporting_opt_in", defaults.crashReportingOptIn)
        val profileId = prefs.getString("profile_id", defaults.activeProfileId) ?: defaults.activeProfileId
        val profiles = prefs.getString("profiles_json", null)
            ?.let { runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.profiles

        return AppSettings(
            language = lang,
            theme = theme,
            amoledPureBlack = amoled,
            accentColor = accent,
            fontChoice = fontChoice,
            fontScale = fontScale,
            appIcon = icon,
            navigationStyle = navStyle,
            subtitleLanguage = subLang,
            subtitleSize = subSize,
            subtitleColorHex = subColor,
            audioLanguage = audioLang,
            skipIntroEnabled = skipIntro,
            skipOutroEnabled = skipOutro,
            autoPlayNext = autoPlayNext,
            nextEpisodeCountdownSec = countdown,
            playerEngine = playerEngine,
            preferExternalPlayer = externalPlayer,
            preferredExternalPlayerPackage = externalPkg,
            audioVolumeBoostEnabled = audioBoost,
            defaultPlaybackSpeed = defaultSpeed,
            defaultAspectMode = defaultAspect,
            bufferProfile = bufferProfile,
            showHeroBanner = showHero,
            showPosterLabels = showPosterLabels,
            hideUnreleasedContent = hideUnreleased,
            rowOrder = rowOrder,
            showContinueWatching = showCw,
            continueWatchingSort = cwSort,
            continueWatchingAction = cwAction,
            cardSize = cardSize,
            cardShape = cardShape,
            cardCorner = cardCorner,
            uiModeOverride = uiMode,
            screenScalePercent = screenScalePercent,
            matchFrameRate = prefs.getBoolean("match_frame_rate", defaultMatchFrameRate),
            hiddenCatalogKeys = hiddenCatalogs,
            updateChannel = updateChannel,
            showWhatsNewAfterUpdate = showWhatsNewAfter,
            crashReportingOptIn = crashOptIn,
            activeProfileId = profileId,
            profiles = profiles
        )
    }

    private fun save(s: AppSettings) {
        val profilesJson = json.encodeToString(s.profiles)
        prefs.edit()
            .putString("lang", s.language.name)
            .putString("theme", s.theme.name)
            .putBoolean("amoled_black", s.amoledPureBlack)
            .putString("accent_color", s.accentColor.name)
            .putString("font_choice", s.fontChoice.name)
            .putString("font_scale", s.fontScale.name)
            .putString("app_icon", s.appIcon.name)
            .putString("nav_style", s.navigationStyle.name)
            .putString("sub_lang", s.subtitleLanguage.name)
            .putString("sub_size", s.subtitleSize.name)
            .putLong("sub_color", s.subtitleColorHex)
            .putString("audio_lang", s.audioLanguage.name)
            .putBoolean("skip_intro", s.skipIntroEnabled)
            .putBoolean("skip_outro", s.skipOutroEnabled)
            .putBoolean("auto_next", s.autoPlayNext)
            .putInt("next_countdown", s.nextEpisodeCountdownSec)
            .putString("player_engine", s.playerEngine.name)
            .putBoolean("ext_player", s.preferExternalPlayer)
            .putString("ext_player_pkg", s.preferredExternalPlayerPackage)
            .putBoolean("audio_vol_boost", s.audioVolumeBoostEnabled)
            .putFloat("default_speed", s.defaultPlaybackSpeed)
            .putString("default_aspect", s.defaultAspectMode.name)
            .putString("buffer_profile", s.bufferProfile.name)
            .putBoolean("show_hero", s.showHeroBanner)
            .putBoolean("show_poster_labels", s.showPosterLabels)
            .putBoolean("hide_unreleased", s.hideUnreleasedContent)
            .putString("row_order", s.rowOrder.name)
            .putBoolean("show_cw", s.showContinueWatching)
            .putString("cw_sort", s.continueWatchingSort.name)
            .putString("cw_action", s.continueWatchingAction.name)
            .putString("card_size", s.cardSize.name)
            .putString("card_shape", s.cardShape.name)
            .putString("card_corner", s.cardCorner.name)
            .putString("ui_mode", s.uiModeOverride.name)
            .putInt("screen_scale_pct", s.screenScalePercent)
            .putBoolean("match_frame_rate", s.matchFrameRate)
            .putStringSet("hidden_catalog_keys", s.hiddenCatalogKeys)
            .putString("update_channel", s.updateChannel.name)
            .putBoolean("show_whats_new_after_update", s.showWhatsNewAfterUpdate)
            .putBoolean("crash_reporting_opt_in", s.crashReportingOptIn)
            .putString("profile_id", s.activeProfileId)
            .putString("profiles_json", profilesJson)
            .apply()
    }

    private companion object {
        /** Per-device keys (synced separately in `settings_tv` / `settings_phone`) plus keys owned by other sections. */
        val SYNC_EXCLUDED_KEYS = setOf(
            "version",
            "hiddenCatalogKeys",
            "navigationStyle",
            "cardSize",
            "fontScale",
            "bufferProfile",
            "playerEngine",
            "preferExternalPlayer",
            "preferredExternalPlayerPackage",
            "appIcon",
            "uiModeOverride",
            "screenScalePercent",
            "matchFrameRate"
        )
    }
}
