package com.quern.app.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Face
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import com.quern.app.ui.components.QuernAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.BuildConfig
import com.quern.app.R
import com.quern.app.data.addons.AddonHealthResult
import com.quern.app.data.companion.CompanionSessionState
import com.quern.app.data.local.PinHasher
import com.quern.app.data.local.PinMigrationState
import com.quern.app.data.servers.ServerTestResult
import com.quern.app.data.update.CurrentVersionNotesState
import com.quern.app.data.update.ManualCheckResult
import com.quern.app.data.update.UpdateDownloadState
import com.quern.app.data.update.UpdateErrorKind
import com.quern.app.data.update.UpdateUiState

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
import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.DefaultAspectMode
import com.quern.app.domain.model.FontScalePreset
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.NavigationStyle
import com.quern.app.domain.model.PlayerEngine
import com.quern.app.domain.model.QuernDevicePreset
import com.quern.app.domain.model.RowOrderPreset
import com.quern.app.domain.model.SubtitleLanguage
import com.quern.app.domain.model.SubtitleSize
import com.quern.app.domain.model.TmdbConfig
import com.quern.app.domain.model.TraktAuthState
import com.quern.app.domain.model.TraktDeviceCode
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.domain.model.UpdateChannel
import com.quern.app.domain.model.UserProfile
import com.quern.app.player.ExternalPlayerHelper
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.LocalTvRailInset
import com.quern.app.ui.components.ProfileAvatarCircle
import com.quern.app.ui.components.ProfileAvatarGrid
import com.quern.app.ui.components.QrCodeCard
import com.quern.app.ui.components.QuernCompanionSignInCard
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.ui.components.WhatsNewDialog
import com.quern.app.ui.components.buildReleaseNotesPreview
import com.quern.app.ui.components.isTvHardware
import com.quern.app.ui.components.parseAvailableUpdateNotes
import com.quern.app.ui.components.parseReleaseEntries
import com.quern.app.ui.components.quernSwitchColors
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.frostedSheen
import com.quern.app.ui.theme.glossySheen
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

enum class SettingsGroup(val icon: ImageVector) {
    ADDONS(Icons.Default.Extension),
    APPEARANCE(Icons.Default.Palette),
    HOME_LAYOUT(Icons.Default.GridView),
    PLAYBACK(Icons.Default.PlayArrow),
    SERVICES(Icons.Default.Dns),
    ACCOUNTS_SYNC(Icons.Default.Person),
    SYSTEM(Icons.Default.Settings)
}

enum class SettingsSubPage {
    NONE,
    ACCOUNT,
    PROFILES,
    MEDIA_SERVERS,
    INTEGRATIONS,
    TRACKING
}

private data class SettingsSearchEntry(
    val title: String,
    val keywords: String,
    val group: SettingsGroup,
    val subPage: SettingsSubPage = SettingsSubPage.NONE
)

private fun isGroupModified(group: SettingsGroup, settings: AppSettings): Boolean {
    val d = AppSettings()
    return when (group) {
        SettingsGroup.ADDONS -> settings.hiddenCatalogKeys != d.hiddenCatalogKeys
        SettingsGroup.APPEARANCE -> settings.theme != d.theme ||
            settings.amoledPureBlack != d.amoledPureBlack ||
            settings.accentColor != d.accentColor ||
            settings.navigationStyle != d.navigationStyle ||
            settings.fontChoice != d.fontChoice ||
            settings.fontScale != d.fontScale ||
            settings.screenScalePercent != d.screenScalePercent ||
            settings.language != d.language ||
            settings.appIcon != d.appIcon
        SettingsGroup.HOME_LAYOUT -> settings.showHeroBanner != d.showHeroBanner ||
            settings.showPosterLabels != d.showPosterLabels ||
            settings.hideUnreleasedContent != d.hideUnreleasedContent ||
            settings.rowOrder != d.rowOrder ||
            settings.showContinueWatching != d.showContinueWatching ||
            settings.continueWatchingSort != d.continueWatchingSort ||
            settings.continueWatchingAction != d.continueWatchingAction ||
            settings.cardSize != d.cardSize ||
            settings.cardShape != d.cardShape ||
            settings.cardCorner != d.cardCorner ||
            settings.uiModeOverride != d.uiModeOverride
        SettingsGroup.PLAYBACK -> settings.playerEngine != d.playerEngine ||
            settings.defaultPlaybackSpeed != d.defaultPlaybackSpeed ||
            settings.defaultAspectMode != d.defaultAspectMode ||
            settings.bufferProfile != d.bufferProfile ||
            settings.subtitleLanguage != d.subtitleLanguage ||
            settings.subtitleSize != d.subtitleSize ||
            settings.subtitleColorHex != d.subtitleColorHex ||
            settings.audioLanguage != d.audioLanguage ||
            settings.audioVolumeBoostEnabled != d.audioVolumeBoostEnabled ||
            settings.skipIntroEnabled != d.skipIntroEnabled ||
            settings.skipOutroEnabled != d.skipOutroEnabled ||
            settings.autoPlayNext != d.autoPlayNext ||
            settings.nextEpisodeCountdownSec != d.nextEpisodeCountdownSec ||
            settings.preferExternalPlayer != d.preferExternalPlayer ||
            settings.preferredExternalPlayerPackage != d.preferredExternalPlayerPackage
        SettingsGroup.SERVICES -> false
        SettingsGroup.ACCOUNTS_SYNC -> false
        SettingsGroup.SYSTEM -> settings.updateChannel != d.updateChannel ||
            settings.showWhatsNewAfterUpdate != d.showWhatsNewAfterUpdate
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    pinMigrationState: PinMigrationState = PinMigrationState.DONE,
    onRetryPinMigration: () -> Unit = {},
    companionUrl: String,
    addonsQrUrl: String,
    companionSession: CompanionSessionState = CompanionSessionState("QRN-000", System.currentTimeMillis() + 600_000L),
    onRefreshCompanionSession: () -> Unit = {},
    onBindCompanionServer: () -> Unit = {},
    onUnbindCompanionServer: () -> Unit = {},
    account: AccountUi,
    addons: List<InstalledAddon>,
    debridStatuses: Map<DebridServiceType, DebridAccountStatus>,
    getDebridKey: (DebridServiceType) -> String,
    mediaServers: List<MediaServerConfig>,
    tmdbConfig: TmdbConfig,
    traktState: TraktAuthState,
    traktDeviceCode: TraktDeviceCode?,
    isTv: Boolean,
    initialGroup: SettingsGroup = SettingsGroup.ADDONS,
    addonHealthMap: Map<String, AddonHealthResult> = emptyMap(),
    checkingHealthUrls: Set<String> = emptySet(),
    isRefreshingAddons: Boolean = false,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApplyAppIcon: (AppIconOption) -> Unit,
    onSyncStremio: (String, String) -> Unit = { _, _ -> },
    onImportNuvio: (String) -> Unit = {},
    onInstallAddon: (String, () -> Unit) -> Unit,
    onToggleAddon: (String, Boolean) -> Unit,
    onMoveAddonUp: (String) -> Unit,
    onMoveAddonDown: (String) -> Unit = {},
    onRemoveAddon: (String) -> Unit,
    onCheckAddonHealth: (String) -> Unit = {},
    onCheckAllAddonsHealth: () -> Unit = {},
    onRefreshAddons: () -> Unit = {},
    onToggleCatalogVisibility: (String) -> Unit = {},
    onRefreshHome: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onApplyPreset: (QuernDevicePreset, String) -> Unit = { _, _ -> },
    onExportSettingsCode: () -> String = { "" },
    onExportSettingsJson: () -> String = { "" },
    onImportSettingsCode: (String) -> Unit = {},
    onSaveDebridKey: (DebridServiceType, String) -> Unit,
    onSaveMediaServer: (MediaServerConfig, (ServerTestResult) -> Unit) -> Unit,
    onDeleteMediaServer: (String) -> Unit,
    onSaveTmdb: (String, String) -> Unit,
    onStartTraktAuth: (String, String) -> Unit,
    onConfirmTraktAuth: (String, String) -> Unit,
    onDisconnectTrakt: () -> Unit,
    onClearCache: () -> Unit,
    updateState: UpdateUiState = UpdateUiState(),
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onOpenUnknownSourcesSettings: () -> Boolean = { false },
    onLoadCurrentVersionNotes: () -> Unit = {},
    onClearCurrentVersionNotes: () -> Unit = {},
    onDebugSimulatePostUpdate: (() -> Unit)? = null
) {
    val strings = LocalQuernStrings.current
    val palette = LocalQuernPalette.current
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp

    var selectedGroup by remember(initialGroup) { mutableStateOf(initialGroup) }
    var activeSubPage by remember { mutableStateOf(SettingsSubPage.NONE) }
    var showSearchDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showSearchDialog) {
        showSearchDialog = false
    }
    BackHandler(enabled = !showSearchDialog && activeSubPage != SettingsSubPage.NONE) {
        activeSubPage = SettingsSubPage.NONE
    }

    if (activeSubPage == SettingsSubPage.INTEGRATIONS || activeSubPage == SettingsSubPage.MEDIA_SERVERS || activeSubPage == SettingsSubPage.TRACKING) {
        val initialTab = when (activeSubPage) {
            SettingsSubPage.MEDIA_SERVERS -> 1
            SettingsSubPage.TRACKING -> 2
            else -> 0
        }
        val barTitle = when (activeSubPage) {
            SettingsSubPage.MEDIA_SERVERS -> qString(R.string.services_media_servers_title)
            SettingsSubPage.TRACKING -> qString(R.string.accounts_tracking_title)
            else -> qString(R.string.services_debrid_title)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = railInset + 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 14.dp
                )
        ) {
            SubPageTopBar(
                title = barTitle,
                onBack = { activeSubPage = SettingsSubPage.NONE }
            )
            ServersScreen(
                debridStatuses = debridStatuses,
                getDebridKey = getDebridKey,
                mediaServers = mediaServers,
                tmdbConfig = tmdbConfig,
                traktState = traktState,
                traktDeviceCode = traktDeviceCode,
                uiModeOverride = settings.uiModeOverride,
                isTv = isTv,
                onSaveDebridKey = onSaveDebridKey,
                onSaveMediaServer = onSaveMediaServer,
                onDeleteMediaServer = onDeleteMediaServer,
                onSaveTmdb = onSaveTmdb,
                onStartTraktAuth = onStartTraktAuth,
                onConfirmTraktAuth = onConfirmTraktAuth,
                onDisconnectTrakt = onDisconnectTrakt,
                onUiModeChange = { mode -> onUpdateSettings { it.copy(uiModeOverride = mode) } },
                initialSection = initialTab
            )
        }
        return
    }

    val groupLabel: @Composable (SettingsGroup) -> String = { grp ->
        when (grp) {
            SettingsGroup.ADDONS -> qString(R.string.group_addons)
            SettingsGroup.APPEARANCE -> qString(R.string.group_appearance)
            SettingsGroup.HOME_LAYOUT -> qString(R.string.group_home)
            SettingsGroup.PLAYBACK -> qString(R.string.group_playback)
            SettingsGroup.SERVICES -> qString(R.string.group_services)
            SettingsGroup.ACCOUNTS_SYNC -> qString(R.string.group_accounts)
            SettingsGroup.SYSTEM -> qString(R.string.group_system)
        }
    }

    val isTvDevice = isTvHardware(LocalContext.current)
    val searchIndex = buildList {
        add(
            SettingsSearchEntry(
                qString(R.string.group_addons),
                if (isTvDevice) "addon url qr health catalog manifest stremio اضافات" else "addon url health catalog manifest stremio اضافات",
                SettingsGroup.ADDONS
            )
        )
        add(SettingsSearchEntry(qString(R.string.appearance_theme_title), "theme amoled black accent color المظهر سمة لون", SettingsGroup.APPEARANCE))
        add(SettingsSearchEntry(qString(R.string.appearance_accent_title), "accent color swatch highlight لون التمييز", SettingsGroup.APPEARANCE))
        if (isTv) {
            add(SettingsSearchEntry(qString(R.string.appearance_nav_title), "navigation sidebar top bar التنقل الشريط الجانبي", SettingsGroup.APPEARANCE))
        }
        add(SettingsSearchEntry(qString(R.string.appearance_font_title), "font text scale size خط حجم النص", SettingsGroup.APPEARANCE))
        add(SettingsSearchEntry(qString(R.string.appearance_screen_size_title), "interface screen size scale 75 80 85 90 95 100 حجم الواجهة", SettingsGroup.APPEARANCE))
        add(SettingsSearchEntry(qString(R.string.appearance_lang_title), "language arabic english rtl اللغة عربي", SettingsGroup.APPEARANCE))
        add(SettingsSearchEntry(qString(R.string.appearance_icon_title), "icon banner launcher ايقونة", SettingsGroup.APPEARANCE))
        add(SettingsSearchEntry(qString(R.string.group_home), "hero poster card size labels unreleased row order continue watching الرئيسية بطاقات", SettingsGroup.HOME_LAYOUT))
        add(SettingsSearchEntry(qString(R.string.home_cw_title), "continue watching sort resume history متابعة المشاهدة", SettingsGroup.HOME_LAYOUT))
        add(SettingsSearchEntry(qString(R.string.group_playback), "player exoplayer mpv external subtitle audio speed aspect buffer skip intro التشغيل ترجمة صوت", SettingsGroup.PLAYBACK))
        add(SettingsSearchEntry(qString(R.string.playback_match_frame_rate), "frame rate refresh fps معدل الإطارات تحديث الشاشة", SettingsGroup.PLAYBACK))
        add(SettingsSearchEntry(qString(R.string.playback_buffer_title), "buffer network ram cache 4k remux تخزين مؤقت", SettingsGroup.PLAYBACK))
        add(SettingsSearchEntry(qString(R.string.services_debrid_title), "debrid torbox real-debrid alldebrid premiumize ديبريد", SettingsGroup.SERVICES, SettingsSubPage.INTEGRATIONS))
        add(SettingsSearchEntry(qString(R.string.services_media_servers_title), "jellyfin emby plex webdav server خوادم", SettingsGroup.SERVICES, SettingsSubPage.MEDIA_SERVERS))
        add(SettingsSearchEntry(qString(R.string.accounts_tracking_title), "tmdb trakt metadata tracking تتبع", SettingsGroup.ACCOUNTS_SYNC, SettingsSubPage.TRACKING))
        add(SettingsSearchEntry(qString(R.string.accounts_cloud_title), "cloud sync account stremio backup مزامنة حساب", SettingsGroup.ACCOUNTS_SYNC, SettingsSubPage.ACCOUNT))
        if (!isTvDevice) {
            add(SettingsSearchEntry(qString(R.string.qa_link_tv_btn), "link tv qr scan nsd ربط تلفاز شاشة", SettingsGroup.ACCOUNTS_SYNC, SettingsSubPage.ACCOUNT))
        }
        add(SettingsSearchEntry(qString(R.string.settings_export_import_title), "export import backup code json تصدير استيراد نسخ احتياطي", SettingsGroup.ACCOUNTS_SYNC, SettingsSubPage.ACCOUNT))
        add(SettingsSearchEntry(qString(R.string.accounts_profiles_title), "profile pin lock user بروفايل ملفات", SettingsGroup.ACCOUNTS_SYNC, SettingsSubPage.PROFILES))
        add(SettingsSearchEntry(qString(R.string.update_section_title), "update whats new changelog version تحديثات ما الجديد", SettingsGroup.SYSTEM))
        add(SettingsSearchEntry(qString(R.string.settings_presets_title), "preset tv phone low end quick bundle إعدادات جاهزة", SettingsGroup.SYSTEM))
        add(SettingsSearchEntry(qString(R.string.system_storage_title), "cache storage clear تخزين كاش", SettingsGroup.SYSTEM))
        add(
            SettingsSearchEntry(
                qString(R.string.system_diagnostics_title),
                if (isTvDevice) "diagnostics device ffmpeg media3 companion تشخيص" else "diagnostics device ffmpeg media3 تشخيص",
                SettingsGroup.SYSTEM
            )
        )
        add(SettingsSearchEntry(qString(R.string.system_about_title), "about version licenses shadow studio حول التراخيص", SettingsGroup.SYSTEM))
    }

    val resetToast = qString(R.string.settings_reset_done)

    val renderActiveGroup: @Composable (SettingsGroup, SettingsSubPage) -> Unit = { targetGroup, targetSubPage ->
        when {
            targetGroup == SettingsGroup.ADDONS && targetSubPage == SettingsSubPage.NONE -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsSectionHeaderBar(
                        title = qString(R.string.group_addons),
                        subtitle = qString(R.string.group_addons_desc),
                        isModified = isGroupModified(SettingsGroup.ADDONS, settings),
                        onResetSection = {
                            onUpdateSettings { it.copy(hiddenCatalogKeys = emptySet()) }
                            onRefreshHome()
                            onShowToast(resetToast)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AddonsScreen(
                            addons = addons,
                            addonsQrUrl = addonsQrUrl,
                            isTv = isTv,
                            companionSession = companionSession,
                            onRefreshCompanionSession = onRefreshCompanionSession,
                            onBindCompanionServer = onBindCompanionServer,
                            onUnbindCompanionServer = onUnbindCompanionServer,
                            hiddenCatalogKeys = settings.hiddenCatalogKeys,
                            healthMap = addonHealthMap,
                            checkingUrls = checkingHealthUrls,
                            isRefreshingAddons = isRefreshingAddons,
                            onInstallAddon = onInstallAddon,
                            onToggleEnabled = onToggleAddon,
                            onMoveUp = onMoveAddonUp,
                            onMoveDown = onMoveAddonDown,
                            onRemove = onRemoveAddon,
                            onCheckHealth = onCheckAddonHealth,
                            onCheckAllHealth = onCheckAllAddonsHealth,
                            onRefreshAddons = onRefreshAddons,
                            onToggleCatalogVisibility = onToggleCatalogVisibility
                        )
                    }
                }
            }

            targetGroup == SettingsGroup.APPEARANCE && targetSubPage == SettingsSubPage.NONE -> {
                AppearanceSectionPane(
                    settings = settings,
                    isModified = isGroupModified(SettingsGroup.APPEARANCE, settings),
                    onUpdateSettings = onUpdateSettings,
                    onApplyAppIcon = onApplyAppIcon,
                    onResetSection = {
                        val d = AppSettings()
                        val iconChanged = settings.appIcon != d.appIcon
                        onUpdateSettings {
                            it.copy(
                                theme = d.theme,
                                amoledPureBlack = d.amoledPureBlack,
                                accentColor = d.accentColor,
                                navigationStyle = d.navigationStyle,
                                fontChoice = d.fontChoice,
                                fontScale = d.fontScale,
                                screenScalePercent = d.screenScalePercent,
                                language = d.language,
                                appIcon = d.appIcon
                            )
                        }
                        if (iconChanged) {
                            onApplyAppIcon(d.appIcon)
                        }
                        onShowToast(resetToast)
                    }
                )
            }

            targetGroup == SettingsGroup.HOME_LAYOUT && targetSubPage == SettingsSubPage.NONE -> {
                HomeLayoutSectionPane(
                    settings = settings,
                    isModified = isGroupModified(SettingsGroup.HOME_LAYOUT, settings),
                    onUpdateSettings = onUpdateSettings,
                    onResetSection = {
                        val d = AppSettings()
                        onUpdateSettings {
                            it.copy(
                                showHeroBanner = d.showHeroBanner,
                                showPosterLabels = d.showPosterLabels,
                                hideUnreleasedContent = d.hideUnreleasedContent,
                                rowOrder = d.rowOrder,
                                showContinueWatching = d.showContinueWatching,
                                continueWatchingSort = d.continueWatchingSort,
                                continueWatchingAction = d.continueWatchingAction,
                                cardSize = d.cardSize,
                                cardShape = d.cardShape,
                                cardCorner = d.cardCorner,
                                uiModeOverride = d.uiModeOverride
                            )
                        }
                        onShowToast(resetToast)
                    }
                )
            }

            targetGroup == SettingsGroup.PLAYBACK && targetSubPage == SettingsSubPage.NONE -> {
                PlaybackSectionPane(
                    settings = settings,
                    isModified = isGroupModified(SettingsGroup.PLAYBACK, settings) ||
                        settings.matchFrameRate != isTvDevice,
                    onUpdateSettings = onUpdateSettings,
                    onResetSection = {
                        val d = AppSettings()
                        onUpdateSettings {
                            it.copy(
                                playerEngine = d.playerEngine,
                                matchFrameRate = isTvDevice,
                                defaultPlaybackSpeed = d.defaultPlaybackSpeed,
                                defaultAspectMode = d.defaultAspectMode,
                                bufferProfile = d.bufferProfile,
                                subtitleLanguage = d.subtitleLanguage,
                                subtitleSize = d.subtitleSize,
                                subtitleColorHex = d.subtitleColorHex,
                                audioLanguage = d.audioLanguage,
                                audioVolumeBoostEnabled = d.audioVolumeBoostEnabled,
                                skipIntroEnabled = d.skipIntroEnabled,
                                skipOutroEnabled = d.skipOutroEnabled,
                                autoPlayNext = d.autoPlayNext,
                                nextEpisodeCountdownSec = d.nextEpisodeCountdownSec,
                                preferExternalPlayer = d.preferExternalPlayer,
                                preferredExternalPlayerPackage = d.preferredExternalPlayerPackage
                            )
                        }
                        onShowToast(resetToast)
                    }
                )
            }

            targetGroup == SettingsGroup.SYSTEM && targetSubPage == SettingsSubPage.NONE -> {
                SystemSectionPane(
                    settings = settings,
                    companionUrl = companionUrl,
                    addons = addons,
                    isTv = isTv,
                    isModified = isGroupModified(SettingsGroup.SYSTEM, settings),
                    updateState = updateState,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                    onDismissUpdate = onDismissUpdate,
                    onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
                    onLoadCurrentVersionNotes = onLoadCurrentVersionNotes,
                    onClearCurrentVersionNotes = onClearCurrentVersionNotes,
                    onDebugSimulatePostUpdate = onDebugSimulatePostUpdate,
                    onShowToast = onShowToast,
                    onUpdateSettings = onUpdateSettings,
                    onApplyPreset = onApplyPreset,
                    onExportSettingsJson = onExportSettingsJson,
                    onRefreshHome = onRefreshHome,
                    onClearCache = onClearCache,
                    onResetSection = {
                        val d = AppSettings()
                        onUpdateSettings {
                            it.copy(
                                updateChannel = d.updateChannel,
                                showWhatsNewAfterUpdate = d.showWhatsNewAfterUpdate,
                                crashReportingOptIn = d.crashReportingOptIn
                            )
                        }
                        onShowToast(resetToast)
                    }
                )
            }

            targetSubPage == SettingsSubPage.NONE -> {
                SettingsCategoryOverviewPane(
                    group = targetGroup,
                    groupTitle = groupLabel(targetGroup),
                    isModified = isGroupModified(targetGroup, settings),
                    settings = settings,
                    account = account,
                    connectedDebridCount = debridStatuses.values.count { it.isConnected },
                    mediaServerCount = mediaServers.size,
                    tmdbConfigured = tmdbConfig.isConfigured,
                    traktConnected = traktState.isAuthenticated,
                    onOpenSubPage = { activeSubPage = it }
                )
            }

            else -> {
                SettingsSubPageContent(
                    subPage = targetSubPage,
                    isTv = isTv,
                    settings = settings,
                    pinMigrationState = pinMigrationState,
                    onRetryPinMigration = onRetryPinMigration,
                    companionUrl = companionUrl,
                    companionSession = companionSession,
                    onRefreshCompanionSession = onRefreshCompanionSession,
                    onBindCompanionServer = onBindCompanionServer,
                    onUnbindCompanionServer = onUnbindCompanionServer,
                    account = account,
                    onBack = { activeSubPage = SettingsSubPage.NONE },
                    onUpdateSettings = onUpdateSettings,
                    onSyncStremio = onSyncStremio,
                    onImportNuvio = onImportNuvio,
                    onExportSettingsCode = onExportSettingsCode,
                    onImportSettingsCode = onImportSettingsCode
                )
            }
        }
    }

    if (isTv) {
        val groupFocusRequesters = remember { SettingsGroup.entries.associateWith { FocusRequester() } }
        LaunchedEffect(Unit) {
            runCatching { groupFocusRequesters[selectedGroup]?.requestFocus() }
        }
        LaunchedEffect(activeSubPage) {
            if (activeSubPage == SettingsSubPage.NONE) {
                runCatching { groupFocusRequesters[selectedGroup]?.requestFocus() }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = railInset)
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(214.dp)
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "title") {
                    Text(
                        text = qString(R.string.settings_title),
                        color = LumaTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                item(key = "global_search_btn") {
                    var searchFocused by remember { mutableStateOf(false) }
                    Surface(
                        color = if (searchFocused) palette.accent else palette.glassSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = if (searchFocused) BorderStroke(2.dp, palette.focusRing) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .frostedSheen(cornerRadius = 14.dp, intensity = 0.65f)
                            .tvClickable(
                                onFocusChangedState = { searchFocused = it },
                                onClick = { showSearchDialog = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = if (searchFocused) palette.onAccent else palette.focusRing,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = qString(R.string.settings_search_placeholder),
                                color = if (searchFocused) palette.onAccent else LumaTextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                items(SettingsGroup.entries, key = { it.name }) { grp ->
                    SettingsCategoryRailItem(
                        label = groupLabel(grp),
                        icon = grp.icon,
                        selected = selectedGroup == grp,
                        isModified = isGroupModified(grp, settings),
                        modifier = Modifier.focusRequester(groupFocusRequesters.getValue(grp)),
                        onSelect = {
                            selectedGroup = grp
                            activeSubPage = SettingsSubPage.NONE
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                AnimatedContent(
                    targetState = Pair(selectedGroup, activeSubPage),
                    transitionSpec = {
                        fadeIn(tween(140, easing = FastOutSlowInEasing))
                            .togetherWith(fadeOut(tween(90, easing = FastOutSlowInEasing)))
                    },
                    label = "settings_pane_anim"
                ) { (targetGroup, targetSubPage) ->
                    renderActiveGroup(targetGroup, targetSubPage)
                }
            }
        }
    } else {
        val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp, bottom = navBarBottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val wideMaxWidthMod = if (isCompact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 840.dp).fillMaxWidth()
            if (activeSubPage == SettingsSubPage.NONE) {
                Row(
                    modifier = wideMaxWidthMod
                        .padding(horizontal = if (isCompact) 14.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = qString(R.string.settings_title),
                        color = LumaTextPrimary,
                        fontSize = if (isCompact) 22.sp else 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(onClick = { showSearchDialog = true })
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isCompact) 12.dp else 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Search, null, tint = palette.focusRing, modifier = Modifier.size(16.dp))
                            Text(qString(R.string.settings_search_placeholder), color = LumaTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    modifier = wideMaxWidthMod,
                    contentPadding = PaddingValues(horizontal = if (isCompact) 14.dp else 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SettingsGroup.entries) { grp ->
                        val active = grp == selectedGroup
                        Surface(
                            color = if (active) palette.accent else palette.surface,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, if (active) palette.focusRing else palette.border),
                            modifier = Modifier
                                .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(onClick = { selectedGroup = grp })
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isCompact) 12.dp else 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(grp.icon, null, tint = if (active) palette.onAccent else LumaTextSecondary, modifier = Modifier.size(15.dp))
                                Text(
                                    groupLabel(grp),
                                    color = if (active) palette.onAccent else LumaTextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(modifier = wideMaxWidthMod.weight(1f).padding(horizontal = if (isCompact) 14.dp else 20.dp)) {
                    renderActiveGroup(selectedGroup, activeSubPage)
                }
            } else {
                Box(modifier = wideMaxWidthMod.weight(1f).padding(horizontal = if (isCompact) 14.dp else 20.dp)) {
                    renderActiveGroup(selectedGroup, activeSubPage)
                }
            }
        }
    }

    if (showSearchDialog) {
        SettingsGlobalSearchDialog(
            entries = searchIndex,
            onDismiss = { showSearchDialog = false },
            onSelect = { entry ->
                selectedGroup = entry.group
                activeSubPage = entry.subPage
                showSearchDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeaderBar(
    title: String,
    subtitle: String,
    isModified: Boolean,
    onResetSection: (() -> Unit)? = null
) {
    val palette = LocalQuernPalette.current
    var resetFocused by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = LumaTextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isModified) {
                    Surface(
                        color = palette.accent.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = qString(R.string.settings_changed_badge),
                            color = palette.focusRing,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = LumaTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (isModified && onResetSection != null) {
            Surface(
                color = if (resetFocused) palette.accent else palette.surfaceElevated,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    width = if (resetFocused) 2.dp else 1.dp,
                    color = if (resetFocused) palette.focusRing else palette.border
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .tvClickable(
                        onFocusChangedState = { resetFocused = it },
                        onClick = { showResetConfirm = true }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = if (resetFocused) palette.onAccent else LumaTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = qString(R.string.settings_reset_section),
                        color = if (resetFocused) palette.onAccent else LumaTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showResetConfirm && onResetSection != null) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = palette.surfaceElevated,
            title = {
                Text(qString(R.string.settings_reset_confirm_title, title), color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(qString(R.string.settings_reset_confirm_body), color = LumaTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            },
            confirmButton = {
                SelectablePillOption(
                    label = qString(R.string.settings_reset_section),
                    selected = true,
                    onClick = {
                        showResetConfirm = false
                        onResetSection()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }
}

@Composable
internal fun HelpTextLine(text: String) {
    Text(
        text = text,
        color = LumaTextSecondary,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppearanceSectionPane(
    settings: AppSettings,
    isModified: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApplyAppIcon: (AppIconOption) -> Unit,
    onResetSection: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC
    val isTvLayout = LocalQuernLayout.current == QuernLayoutClass.TV

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            SettingsSectionHeaderBar(
                title = qString(R.string.group_appearance),
                subtitle = qString(R.string.group_appearance_desc),
                isModified = isModified,
                onResetSection = onResetSection
            )
        }

        // 1. Theme Atmosphere + True AMOLED
        item(key = "theme_amoled") {
            SettingSectionCard(title = qString(R.string.appearance_theme_title)) {
                SettingToggleRow(
                    label = qString(R.string.appearance_amoled_label),
                    checked = settings.amoledPureBlack,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(amoledPureBlack = v) } }
                )
                HelpTextLine(qString(R.string.appearance_amoled_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppThemePreset.entries.forEach { preset ->
                        val selected = settings.theme == preset
                        var focused by remember { mutableStateOf(false) }
                        Surface(
                            color = when {
                                focused -> preset.accentColor
                                selected -> preset.surfaceElevatedTint
                                else -> palette.surfaceElevated
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = if (focused) {
                                BorderStroke(2.dp, preset.focusRingColor)
                            } else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = {
                                        onUpdateSettings {
                                            it.copy(
                                                theme = preset,
                                                amoledPureBlack = (preset == AppThemePreset.GLOSSY_BLACK)
                                            )
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(preset.accentColor, preset.backgroundColor)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(Icons.Default.Check, null, tint = preset.onAccentColor, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Text(
                                    text = if (isAr) preset.displayNameAr else preset.displayNameEn,
                                    color = if (focused) preset.onAccentColor else LumaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Independent Accent Color Override
        item(key = "accent_color") {
            SettingSectionCard(title = qString(R.string.appearance_accent_title)) {
                HelpTextLine(qString(R.string.appearance_accent_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentColorChoice.entries.forEach { choice ->
                        val selected = settings.accentColor == choice
                        var focused by remember { mutableStateOf(false) }
                        val swatchColor = choice.colorHex?.let { Color(it) } ?: settings.theme.accentColor
                        Surface(
                            color = if (focused) palette.accent else if (selected) palette.surfaceElevated else palette.surface,
                            shape = RoundedCornerShape(999.dp),
                            border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = { onUpdateSettings { it.copy(accentColor = choice) } }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                )
                                Text(
                                    text = if (isAr) choice.labelAr else choice.labelEn,
                                    color = if (focused) palette.onAccent else LumaTextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Navigation Style (TV rail / top bar only — non-TV uses the floating bottom dock)
        if (isTvLayout) {
            item(key = "navigation_style") {
                SettingSectionCard(title = qString(R.string.appearance_nav_title)) {
                    HelpTextLine(qString(R.string.appearance_nav_help))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavigationStyle.entries.forEach { style ->
                            SelectablePillOption(
                                label = if (isAr) style.labelAr else style.labelEn,
                                selected = settings.navigationStyle == style,
                                onClick = { onUpdateSettings { it.copy(navigationStyle = style) } }
                            )
                        }
                    }
                }
            }
        }

        // 4. Font Family & Text Scale
        item(key = "font_and_scale") {
            SettingSectionCard(title = qString(R.string.appearance_font_title)) {
                HelpTextLine(qString(R.string.appearance_font_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppFontChoice.entries.forEach { fc ->
                        SelectablePillOption(
                            label = if (isAr) fc.labelAr else fc.labelEn,
                            selected = settings.fontChoice == fc,
                            onClick = { onUpdateSettings { it.copy(fontChoice = fc) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontScalePreset.entries.forEach { fs ->
                        SelectablePillOption(
                            label = if (isAr) fs.labelAr else fs.labelEn,
                            selected = settings.fontScale == fs,
                            onClick = { onUpdateSettings { it.copy(fontScale = fs) } }
                        )
                    }
                }
            }
        }

        // 4b. Interface Size (Full-screen LocalDensity scale, synced per device class)
        item(key = "screen_size") {
            val steps = remember { listOf(75, 80, 85, 90, 95, 100) }
            val stepRequesters = remember { steps.associateWith { FocusRequester() } }
            val activeStep by rememberUpdatedState(
                steps.minByOrNull { kotlin.math.abs(it - settings.screenScalePercent) } ?: 100
            )
            SettingSectionCard(title = qString(R.string.appearance_screen_size_title)) {
                HelpTextLine(qString(R.string.appearance_screen_size_help))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Entering this row lands on the currently selected step so scrolling past
                        // vertically never changes it, while moving LEFT/RIGHT applies live immediately.
                        .focusProperties { enter = { stepRequesters.getValue(activeStep) } }
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEach { pct ->
                        SelectablePillOption(
                            label = "$pct%",
                            selected = settings.screenScalePercent == pct,
                            modifier = Modifier.focusRequester(stepRequesters.getValue(pct)),
                            onFocused = {
                                if (settings.screenScalePercent != pct) {
                                    onUpdateSettings { it.copy(screenScalePercent = pct) }
                                }
                            },
                            onClick = {
                                if (settings.screenScalePercent != pct) {
                                    onUpdateSettings { it.copy(screenScalePercent = pct) }
                                }
                            }
                        )
                    }
                }
            }
        }

        // 5. Language (System Default + Manual, RTL-safe)
        item(key = "language") {
            SettingSectionCard(title = qString(R.string.appearance_lang_title)) {
                HelpTextLine(qString(R.string.appearance_lang_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        SelectablePillOption(
                            label = lang.displayName,
                            selected = settings.language == lang,
                            onClick = { onUpdateSettings { it.copy(language = lang) } }
                        )
                    }
                }
            }
        }

        // 6. App Icon & TV Banner Choice (with restart-warning confirmation dialog)
        item(key = "app_icon") {
            var pendingIcon by remember { mutableStateOf<AppIconOption?>(null) }

            SettingSectionCard(title = qString(R.string.appearance_icon_title)) {
                HelpTextLine(qString(R.string.appearance_icon_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIconOption.entries.forEach { iconOpt ->
                        val selected = settings.appIcon == iconOpt
                        var focused by remember { mutableStateOf(false) }
                        val gradColors = iconOpt.gradientHexColors?.map { Color(it) }
                        Surface(
                            color = if (focused) palette.accent else if (selected) palette.surfaceElevated else palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = {
                                        if (iconOpt != settings.appIcon) {
                                            pendingIcon = iconOpt
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .then(
                                            if (gradColors != null) {
                                                Modifier.background(Brush.linearGradient(gradColors))
                                            } else {
                                                Modifier.background(Color(iconOpt.previewColorHex).copy(alpha = 0.22f))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Q",
                                        color = if (gradColors != null) Color.White else Color(iconOpt.previewColorHex),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = if (isAr) iconOpt.displayNameAr else iconOpt.displayNameEn,
                                    color = if (focused) palette.onAccent else LumaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            pendingIcon?.let { chosen ->
                AlertDialog(
                    onDismissRequest = { pendingIcon = null },
                    containerColor = palette.surfaceElevated,
                    title = {
                        Text(qString(R.string.icon_confirm_title), color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(qString(R.string.icon_confirm_body), color = LumaTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    },
                    confirmButton = {
                        SelectablePillOption(
                            label = qString(R.string.icon_confirm_apply),
                            selected = true,
                            onClick = {
                                pendingIcon = null
                                onUpdateSettings { it.copy(appIcon = chosen) }
                                onApplyAppIcon(chosen)
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingIcon = null }) {
                            Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsibleAdvancedCard(
    content: @Composable () -> Unit
) {
    val palette = LocalQuernPalette.current
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(18.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 18.dp, intensity = 0.75f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
                    .tvClickable(
                        onFocusChangedState = { focused = it },
                        onClick = { expanded = !expanded }
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = qString(R.string.settings_advanced_toggle),
                        color = LumaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = qString(R.string.settings_advanced_help),
                        color = LumaTextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (focused) palette.focusRing else LumaTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeLayoutSectionPane(
    settings: AppSettings,
    isModified: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onResetSection: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val s = LocalQuernStrings.current
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            SettingsSectionHeaderBar(
                title = qString(R.string.group_home),
                subtitle = qString(R.string.group_home_desc),
                isModified = isModified,
                onResetSection = onResetSection
            )
        }

        // 1. Hero, Poster Labels & Hide Unreleased
        item(key = "home_visibility") {
            SettingSectionCard(title = qString(R.string.group_home)) {
                SettingToggleRow(
                    label = qString(R.string.home_hero_label),
                    checked = settings.showHeroBanner,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(showHeroBanner = v) } }
                )
                HelpTextLine(qString(R.string.home_hero_help))

                SettingToggleRow(
                    label = qString(R.string.home_poster_labels_label),
                    checked = settings.showPosterLabels,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(showPosterLabels = v) } }
                )
                HelpTextLine(qString(R.string.home_poster_labels_help))

                SettingToggleRow(
                    label = qString(R.string.home_hide_unreleased_label),
                    checked = settings.hideUnreleasedContent,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(hideUnreleasedContent = v) } }
                )
                HelpTextLine(qString(R.string.home_hide_unreleased_help))
            }
        }

        // 2. Poster Card Style (Size, Shape, Corners + Live Preview)
        item(key = "poster_style") {
            SettingSectionCard(title = qString(R.string.home_card_style_title)) {
                Text(s.posterSizeLabel, color = LumaTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardSizePreset.entries.forEach { preset ->
                        SelectablePillOption(
                            label = if (isAr) preset.labelAr else preset.labelEn,
                            selected = settings.cardSize == preset,
                            onClick = { onUpdateSettings { it.copy(cardSize = preset) } }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(s.posterShapeLabel, color = LumaTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardShapePreset.entries.forEach { shape ->
                        SelectablePillOption(
                            label = if (isAr) shape.labelAr else shape.labelEn,
                            selected = settings.cardShape == shape,
                            onClick = { onUpdateSettings { it.copy(cardShape = shape) } }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(s.posterCornerLabel, color = LumaTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardCornerPreset.entries.forEach { corner ->
                        SelectablePillOption(
                            label = if (isAr) corner.labelAr else corner.labelEn,
                            selected = settings.cardCorner == corner,
                            onClick = { onUpdateSettings { it.copy(cardCorner = corner) } }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                val previewWidth = when (settings.cardSize) {
                    CardSizePreset.COMPACT -> 76.dp
                    CardSizePreset.STANDARD -> 92.dp
                    CardSizePreset.LARGE -> 110.dp
                    CardSizePreset.XLARGE -> 126.dp
                }
                val previewShape = RoundedCornerShape(settings.cardCorner.radiusDp.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.background)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Box(
                            modifier = Modifier
                                .width(previewWidth)
                                .aspectRatio(settings.cardShape.aspectRatio)
                                .clip(previewShape)
                                .background(Brush.linearGradient(listOf(palette.accent, palette.surfaceElevated)))
                                .border(1.5.dp, palette.focusRing, previewShape),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = "4K HDR",
                                color = palette.onAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (settings.showPosterLabels) {
                            Spacer(Modifier.height(4.dp))
                            Text("Sample Title", color = LumaTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("2026", color = LumaTextSecondary, fontSize = 8.5.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isAr) settings.cardSize.labelAr else settings.cardSize.labelEn,
                            color = LumaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${if (isAr) settings.cardShape.labelAr else settings.cardShape.labelEn} • ${if (isAr) settings.cardCorner.labelAr else settings.cardCorner.labelEn}",
                            color = LumaTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // 3. Row Order
        item(key = "row_order") {
            SettingSectionCard(title = qString(R.string.home_row_order_title)) {
                HelpTextLine(qString(R.string.home_row_order_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RowOrderPreset.entries.forEach { preset ->
                        SelectablePillOption(
                            label = if (isAr) preset.labelAr else preset.labelEn,
                            selected = settings.rowOrder == preset,
                            onClick = { onUpdateSettings { it.copy(rowOrder = preset) } }
                        )
                    }
                }
            }
        }

        // 4. Continue Watching (Show/Hide, Sort, Action on Select)
        item(key = "continue_watching") {
            SettingSectionCard(title = qString(R.string.home_cw_title)) {
                SettingToggleRow(
                    label = qString(R.string.home_cw_show),
                    checked = settings.showContinueWatching,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(showContinueWatching = v) } }
                )

                if (settings.showContinueWatching) {
                    Spacer(Modifier.height(10.dp))
                    Text(qString(R.string.home_cw_sort), color = LumaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContinueWatchingSort.entries.forEach { sort ->
                            SelectablePillOption(
                                label = if (isAr) sort.labelAr else sort.labelEn,
                                selected = settings.continueWatchingSort == sort,
                                onClick = { onUpdateSettings { it.copy(continueWatchingSort = sort) } }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(qString(R.string.home_cw_action), color = LumaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContinueWatchingAction.entries.forEach { act ->
                            SelectablePillOption(
                                label = if (isAr) act.labelAr else act.labelEn,
                                selected = settings.continueWatchingAction == act,
                                onClick = { onUpdateSettings { it.copy(continueWatchingAction = act) } }
                            )
                        }
                    }
                }
            }
        }

        // 5. Collapsed Advanced Options (Interface Mode Override)
        item(key = "home_advanced") {
            CollapsibleAdvancedCard {
                Text(
                    text = qString(R.string.home_interface_mode),
                    color = LumaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UiModeOverride.entries.forEach { mode ->
                        SelectablePillOption(
                            label = mode.label,
                            selected = settings.uiModeOverride == mode,
                            onClick = { onUpdateSettings { it.copy(uiModeOverride = mode) } }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSectionPane(
    settings: AppSettings,
    isModified: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onResetSection: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val s = LocalQuernStrings.current
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC
    val installedExternalPlayers = remember(context) { ExternalPlayerHelper.detectInstalledPlayers(context) }
    val supportsVolumeBoost = remember { ExternalPlayerHelper.isVolumeBoostSupported() }
    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val subColors = listOf(
        0xFFFFFFFFL to if (isAr) "أبيض" else "White",
        0xFFFACC15L to if (isAr) "أصفر" else "Yellow",
        0xFF38BDF8L to if (isAr) "سماوي" else "Cyan",
        0xFF4ADE80L to if (isAr) "أخضر" else "Green"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            SettingsSectionHeaderBar(
                title = qString(R.string.group_playback),
                subtitle = qString(R.string.group_playback_desc),
                isModified = isModified,
                onResetSection = onResetSection
            )
        }

        item(key = "match_frame_rate") {
            SettingSectionCard(title = qString(R.string.playback_display_title)) {
                SettingToggleRow(
                    label = qString(R.string.playback_match_frame_rate),
                    checked = settings.matchFrameRate,
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(matchFrameRate = enabled) } }
                )
                HelpTextLine(qString(R.string.playback_match_frame_rate_help))
            }
        }

        // 1. Player Engine
        item(key = "player_engine") {
            SettingSectionCard(title = qString(R.string.playback_engine_title)) {
                HelpTextLine(qString(R.string.playback_engine_help))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerEngine.entries.forEach { engine ->
                        val selected = settings.playerEngine == engine
                        var focused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (focused) palette.accent else if (selected) palette.surfaceElevated else palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (focused || selected) 2.5.dp else 1.dp,
                                color = if (focused || selected) palette.focusRing else palette.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = { onUpdateSettings { it.copy(playerEngine = engine) } }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isAr) engine.labelAr else engine.labelEn,
                                        color = if (focused) palette.onAccent else LumaTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isAr) engine.descAr else engine.descEn,
                                        color = if (focused) palette.onAccent.copy(alpha = 0.85f) else LumaTextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (focused) palette.onAccent else palette.focusRing,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Default Speed & Aspect Ratio
        item(key = "speed_aspect") {
            SettingSectionCard(title = qString(R.string.playback_defaults_title)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedOptions.forEach { sp ->
                        SelectablePillOption(
                            label = "${sp}x",
                            selected = settings.defaultPlaybackSpeed == sp,
                            onClick = { onUpdateSettings { it.copy(defaultPlaybackSpeed = sp) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultAspectMode.entries.forEach { aspect ->
                        SelectablePillOption(
                            label = if (isAr) aspect.labelAr else aspect.labelEn,
                            selected = settings.defaultAspectMode == aspect,
                            onClick = { onUpdateSettings { it.copy(defaultAspectMode = aspect) } }
                        )
                    }
                }
            }
        }

        // 3. Default Audio Track & Normalization (if supported by device audio HAL)
        item(key = "audio_sub_lang") {
            SettingSectionCard(title = qString(R.string.playback_audio_lang_title)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioLanguage.entries.forEach { lang ->
                        SelectablePillOption(
                            label = if (isAr) lang.displayNameAr else lang.displayNameEn,
                            selected = settings.audioLanguage == lang,
                            onClick = { onUpdateSettings { it.copy(audioLanguage = lang) } }
                        )
                    }
                }
                if (supportsVolumeBoost) {
                    Spacer(Modifier.height(12.dp))
                    SettingToggleRow(
                        label = qString(R.string.playback_audio_boost_label),
                        checked = settings.audioVolumeBoostEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(audioVolumeBoostEnabled = v) } }
                    )
                    HelpTextLine(qString(R.string.playback_audio_boost_help))
                }
            }
        }

        item(key = "sub_lang") {
            SettingSectionCard(title = qString(R.string.playback_sub_lang_title)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleLanguage.entries.forEach { lang ->
                        SelectablePillOption(
                            label = if (isAr) lang.displayNameAr else lang.displayNameEn,
                            selected = settings.subtitleLanguage == lang,
                            onClick = { onUpdateSettings { it.copy(subtitleLanguage = lang) } }
                        )
                    }
                }
            }
        }

        // 4. Subtitle Appearance (Size + Color + Preview)
        item(key = "sub_style") {
            SettingSectionCard(title = qString(R.string.playback_sub_style_title)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleSize.entries.forEach { sz ->
                        SelectablePillOption(
                            label = if (isAr) sz.displayNameAr else sz.displayNameEn,
                            selected = settings.subtitleSize == sz,
                            onClick = { onUpdateSettings { it.copy(subtitleSize = sz) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subColors.forEach { (hex, label) ->
                        SelectablePillOption(
                            label = label,
                            selected = settings.subtitleColorHex == hex,
                            onClick = { onUpdateSettings { it.copy(subtitleColorHex = hex) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAr) "معاينة الترجمة المباشرة • Quern Cinema" else "Live Subtitle Preview • Quern Cinema",
                        color = Color(settings.subtitleColorHex),
                        fontSize = (settings.subtitleSize.spValue * 0.72f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 5. Up Next & Chapter Skip (Marker-Only Skip Intro!)
        item(key = "automation") {
            SettingSectionCard(title = qString(R.string.playback_automation_title)) {
                SettingToggleRow(
                    label = qString(R.string.playback_autoplay_label),
                    checked = settings.autoPlayNext,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoPlayNext = v) } }
                )
                if (settings.autoPlayNext) {
                    Spacer(Modifier.height(8.dp))
                    Text(qString(R.string.playback_countdown_label), color = LumaTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { sec ->
                            SelectablePillOption(
                                label = "${sec}s",
                                selected = settings.nextEpisodeCountdownSec == sec,
                                onClick = { onUpdateSettings { it.copy(nextEpisodeCountdownSec = sec) } }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SettingToggleRow(
                    label = qString(R.string.playback_skip_outro_label),
                    checked = settings.skipOutroEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(skipOutroEnabled = v) } }
                )
                HelpTextLine(qString(R.string.playback_skip_outro_help))
                SettingToggleRow(
                    label = qString(R.string.playback_skip_intro_label),
                    checked = settings.skipIntroEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(skipIntroEnabled = v) } }
                )
                HelpTextLine(qString(R.string.playback_skip_intro_help))
            }
        }

        // 6. Collapsed Advanced Options (Buffer Profile + Always External)
        item(key = "playback_advanced") {
            CollapsibleAdvancedCard {
                Text(
                    text = qString(R.string.playback_buffer_title),
                    color = LumaTextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                HelpTextLine(qString(R.string.playback_buffer_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BufferProfile.entries.forEach { bp ->
                        SelectablePillOption(
                            label = if (isAr) bp.labelAr else bp.labelEn,
                            selected = settings.bufferProfile == bp,
                            onClick = { onUpdateSettings { it.copy(bufferProfile = bp) } }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingToggleRow(
                    label = qString(R.string.playback_external_label),
                    checked = settings.preferExternalPlayer,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(preferExternalPlayer = v) } }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = qString(R.string.playback_external_app_title),
                    color = LumaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                HelpTextLine(qString(R.string.playback_external_app_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectablePillOption(
                        label = qString(R.string.playback_external_chooser),
                        selected = settings.preferredExternalPlayerPackage.isBlank() ||
                            installedExternalPlayers.none { it.packageName == settings.preferredExternalPlayerPackage },
                        onClick = { onUpdateSettings { it.copy(preferredExternalPlayerPackage = "") } }
                    )
                    installedExternalPlayers.forEach { player ->
                        SelectablePillOption(
                            label = player.displayName,
                            selected = settings.preferredExternalPlayerPackage == player.packageName,
                            onClick = { onUpdateSettings { it.copy(preferredExternalPlayerPackage = player.packageName) } }
                        )
                    }
                }
                if (installedExternalPlayers.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = qString(R.string.playback_external_none_installed),
                        color = LumaTextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

private fun calculateDirBytes(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var total = 0L
    val children = dir.listFiles() ?: return 0L
    for (child in children) {
        total += if (child.isDirectory) calculateDirBytes(child) else child.length()
    }
    return total
}

private fun formatBytesReadable(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemSectionPane(
    settings: AppSettings,
    companionUrl: String,
    addons: List<InstalledAddon>,
    isTv: Boolean,
    isModified: Boolean,
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Boolean,
    onLoadCurrentVersionNotes: () -> Unit,
    onClearCurrentVersionNotes: () -> Unit,
    onDebugSimulatePostUpdate: (() -> Unit)?,
    onShowToast: (String) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onApplyPreset: (QuernDevicePreset, String) -> Unit,
    onExportSettingsJson: () -> String,
    onRefreshHome: () -> Unit,
    onClearCache: () -> Unit,
    onResetSection: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC
    var cacheRefreshTick by remember { mutableStateOf(0) }
    val cacheSizeText = remember(cacheRefreshTick) {
        formatBytesReadable(calculateDirBytes(context.cacheDir))
    }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showDebugJsonDialog by remember { mutableStateOf(false) }

    val tvLabel = qString(R.string.preset_tv)
    val phoneLabel = qString(R.string.preset_phone)
    val lowEndLabel = qString(R.string.preset_low_end)
    val tvAppliedToast = qString(R.string.preset_applied, tvLabel)
    val phoneAppliedToast = qString(R.string.preset_applied, phoneLabel)
    val lowEndAppliedToast = qString(R.string.preset_applied, lowEndLabel)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            SettingsSectionHeaderBar(
                title = qString(R.string.group_system),
                subtitle = qString(R.string.group_system_desc),
                isModified = isModified,
                onResetSection = onResetSection
            )
        }

        // 0. App Updates (official GitHub Releases)
        item(key = "app_updates") {
            val avail = updateState.available
            val dl = updateState.downloadState
            val isDownloading = dl is UpdateDownloadState.Downloading
            val isReady = dl is UpdateDownloadState.ReadyToInstall
            val isBlocked = dl is UpdateDownloadState.UnknownSourcesBlocked
            SettingSectionCard(
                title = if (avail != null) qString(R.string.update_available_title) else qString(R.string.update_section_title)
            ) {
                if (avail != null) {
                    val parsedNotes = remember(avail, isAr) { parseAvailableUpdateNotes(avail, isAr) }
                    val previewText = remember(parsedNotes) { buildReleaseNotesPreview(parsedNotes) }
                    var showWhatsNew by remember(avail.version) { mutableStateOf(false) }
                    val whatsNewFocus = remember { FocusRequester() }
                    var pendingWhatsNewFocusReturn by remember { mutableStateOf(false) }
                    val windowInfo = LocalWindowInfo.current

                    LaunchedEffect(showWhatsNew, windowInfo.isWindowFocused, pendingWhatsNewFocusReturn) {
                        if (!isTv || showWhatsNew || !pendingWhatsNewFocusReturn || !windowInfo.isWindowFocused) {
                            return@LaunchedEffect
                        }
                        withFrameNanos { }
                        delay(50L)
                        if (!showWhatsNew) {
                            runCatching { whatsNewFocus.requestFocus() }
                            pendingWhatsNewFocusReturn = false
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = palette.accent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "v${avail.version}",
                                color = palette.focusRing,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        if (avail.isPrerelease) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = qString(R.string.update_prerelease_badge),
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (avail.publishedDateText.isNotBlank()) {
                            Text(
                                text = qString(R.string.update_released_on, avail.publishedDateText),
                                color = LumaTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (parsedNotes.isNotEmpty()) {
                            Text(
                                text = previewText,
                                color = LumaTextPrimary.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        } else {
                            Text(
                                text = qString(R.string.update_no_notes),
                                color = LumaTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (dl is UpdateDownloadState.Downloading) {
                        Spacer(Modifier.height(10.dp))
                        val pct = (dl.progress * 100f).toInt().coerceIn(0, 100)
                        val sizeText = if (dl.totalBytes > 0L) {
                            "${formatBytesReadable(dl.downloadedBytes)} / ${formatBytesReadable(dl.totalBytes)}"
                        } else {
                            formatBytesReadable(dl.downloadedBytes)
                        }
                        Text(
                            text = qString(R.string.update_downloading, pct, sizeText),
                            color = palette.focusRing,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { dl.progress },
                            color = palette.focusRing,
                            trackColor = palette.surfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    } else if (isBlocked) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = qString(R.string.update_unknown_sources_blocked),
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (dl is UpdateDownloadState.Error) {
                        Spacer(Modifier.height(8.dp))
                        val errRes = when (dl.kind) {
                            UpdateErrorKind.DOWNLOAD_FAILED -> R.string.update_download_failed
                            UpdateErrorKind.INSTALLER_UNAVAILABLE -> R.string.update_installer_unavailable
                        }
                        Text(
                            text = qString(errRes),
                            color = LumaRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectablePillOption(
                            label = when {
                                isDownloading -> qString(R.string.update_action_downloading)
                                isReady || isBlocked -> qString(R.string.update_action_install_now)
                                else -> qString(R.string.update_action_download_install)
                            },
                            selected = true,
                            onClick = { if (!isDownloading) onDownloadAndInstallUpdate() }
                        )
                        if (parsedNotes.isNotEmpty()) {
                            SelectablePillOption(
                                label = qString(R.string.update_action_whats_new),
                                selected = false,
                                modifier = Modifier.focusRequester(whatsNewFocus),
                                onClick = {
                                    pendingWhatsNewFocusReturn = true
                                    showWhatsNew = true
                                }
                            )
                        }
                        if (isBlocked) {
                            SelectablePillOption(
                                label = qString(R.string.update_action_open_permission),
                                selected = false,
                                onClick = { onOpenUnknownSourcesSettings() }
                            )
                        }
                        if (updateState.showHomeBanner) {
                            SelectablePillOption(
                                label = qString(R.string.update_action_later),
                                selected = false,
                                onClick = onDismissUpdate
                            )
                        }
                        SelectablePillOption(
                            label = if (updateState.checking) qString(R.string.update_action_checking)
                                else qString(R.string.update_action_check),
                            selected = false,
                            onClick = { if (!updateState.checking) onCheckForUpdates() }
                        )
                    }

                    if (showWhatsNew && parsedNotes.isNotEmpty()) {
                        WhatsNewDialog(
                            release = avail,
                            sections = parsedNotes,
                            isTv = isTv,
                            updateLabel = if (isDownloading) qString(R.string.update_action_downloading)
                                else qString(R.string.update_action_view),
                            updateEnabled = !isDownloading,
                            onUpdate = {
                                pendingWhatsNewFocusReturn = false
                                showWhatsNew = false
                                if (!isDownloading) onDownloadAndInstallUpdate()
                            },
                            onLater = { showWhatsNew = false },
                            onDismiss = { showWhatsNew = false }
                        )
                    }
                } else {
                    Text(
                        text = qString(R.string.update_current_version, BuildConfig.VERSION_NAME),
                        color = LumaTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    when (updateState.manualCheckResult) {
                        ManualCheckResult.UP_TO_DATE -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = qString(R.string.update_up_to_date),
                                color = LumaGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        ManualCheckResult.FAILED_RATE_LIMIT -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = qString(R.string.update_check_rate_limited),
                                color = LumaRed,
                                fontSize = 12.sp
                            )
                        }
                        ManualCheckResult.FAILED_NETWORK -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = qString(R.string.update_check_failed),
                                color = LumaRed,
                                fontSize = 12.sp
                            )
                        }
                        else -> Unit
                    }
                    Spacer(Modifier.height(10.dp))
                    SelectablePillOption(
                        label = if (updateState.checking) qString(R.string.update_action_checking)
                            else qString(R.string.update_action_check),
                        selected = true,
                        onClick = { if (!updateState.checking) onCheckForUpdates() }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = qString(R.string.update_channel_label),
                        color = LumaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    UpdateChannel.entries.forEach { ch ->
                        SelectablePillOption(
                            label = if (isAr) ch.labelAr else ch.labelEn,
                            selected = settings.updateChannel == ch,
                            onClick = {
                                if (settings.updateChannel != ch) {
                                    onUpdateSettings { it.copy(updateChannel = ch) }
                                    onCheckForUpdates()
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))

                SettingToggleRow(
                    label = qString(R.string.update_show_whats_new_after_label),
                    checked = settings.showWhatsNewAfterUpdate,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(showWhatsNewAfterUpdate = enabled) }
                    }
                )

                Spacer(Modifier.height(8.dp))

                var showCurrentVersionNotes by remember { mutableStateOf(false) }
                val currentNotesFocus = remember { FocusRequester() }
                var pendingCurrentNotesFocusReturn by remember { mutableStateOf(false) }
                val currentNotesWindowInfo = LocalWindowInfo.current
                val currentNotesState = updateState.currentVersionNotesState
                val isLoadingCurrentNotes = currentNotesState is CurrentVersionNotesState.Loading
                val notesUnavailableText = qString(R.string.update_current_notes_unavailable, BuildConfig.VERSION_NAME)

                LaunchedEffect(showCurrentVersionNotes, currentNotesWindowInfo.isWindowFocused, pendingCurrentNotesFocusReturn) {
                    if (!isTv || showCurrentVersionNotes || !pendingCurrentNotesFocusReturn || !currentNotesWindowInfo.isWindowFocused) {
                        return@LaunchedEffect
                    }
                    withFrameNanos { }
                    delay(50L)
                    if (!showCurrentVersionNotes) {
                        runCatching { currentNotesFocus.requestFocus() }
                        pendingCurrentNotesFocusReturn = false
                    }
                }

                var currentRowFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (LocalQuernLayout.current == QuernLayoutClass.COMPACT) Modifier.heightIn(min = 48.dp) else Modifier)
                        .focusRequester(currentNotesFocus)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentRowFocused) palette.accent else Color.Transparent)
                        .border(
                            width = if (currentRowFocused) 2.5.dp else 0.dp,
                            color = if (currentRowFocused) palette.focusRing else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .tvClickable(
                            enabled = !isLoadingCurrentNotes,
                            onFocusChangedState = { currentRowFocused = it },
                            onClick = {
                                pendingCurrentNotesFocusReturn = true
                                showCurrentVersionNotes = true
                                onLoadCurrentVersionNotes()
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = qString(R.string.update_whats_new_this_version),
                        color = if (currentRowFocused) palette.onAccent else LumaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (currentRowFocused) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isLoadingCurrentNotes) qString(R.string.update_loading_notes)
                        else "v${BuildConfig.VERSION_NAME}",
                        color = if (currentRowFocused) palette.onAccent.copy(alpha = 0.9f) else LumaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showCurrentVersionNotes) {
                    when (currentNotesState) {
                        is CurrentVersionNotesState.Loaded -> {
                            val sections = remember(currentNotesState.payload, isAr) {
                                parseReleaseEntries(currentNotesState.payload.entries, isAr)
                            }
                            if (sections.isEmpty()) {
                                LaunchedEffect(currentNotesState) {
                                    showCurrentVersionNotes = false
                                    onClearCurrentVersionNotes()
                                    onShowToast(notesUnavailableText)
                                }
                            } else {
                                WhatsNewDialog(
                                    version = currentNotesState.payload.version,
                                    sections = sections,
                                    isTv = isTv,
                                    primaryLabel = qString(R.string.common_ok),
                                    secondaryLabel = null,
                                    onPrimary = {
                                        showCurrentVersionNotes = false
                                        onClearCurrentVersionNotes()
                                    },
                                    onDismiss = {
                                        showCurrentVersionNotes = false
                                        onClearCurrentVersionNotes()
                                    }
                                )
                            }
                        }
                        CurrentVersionNotesState.Unavailable -> {
                            LaunchedEffect(Unit) {
                                showCurrentVersionNotes = false
                                onClearCurrentVersionNotes()
                                onShowToast(notesUnavailableText)
                            }
                        }
                        CurrentVersionNotesState.Idle,
                        CurrentVersionNotesState.Loading -> Unit
                    }
                }
            }
        }

        // 1. One-Tap Quern Device Presets (TV / Phone / Low-end Device)
        item(key = "quick_presets") {
            SettingSectionCard(title = qString(R.string.settings_presets_title)) {
                HelpTextLine(qString(R.string.settings_presets_help))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectablePillOption(
                        label = tvLabel,
                        selected = settings.uiModeOverride == UiModeOverride.TV && settings.bufferProfile == BufferProfile.BALANCED,
                        onClick = { onApplyPreset(QuernDevicePreset.TV, tvAppliedToast) }
                    )
                    SelectablePillOption(
                        label = phoneLabel,
                        selected = settings.uiModeOverride == UiModeOverride.PHONE && settings.cardSize == CardSizePreset.COMPACT,
                        onClick = { onApplyPreset(QuernDevicePreset.PHONE, phoneAppliedToast) }
                    )
                    SelectablePillOption(
                        label = lowEndLabel,
                        selected = !settings.showHeroBanner && settings.bufferProfile == BufferProfile.LOW,
                        onClick = { onApplyPreset(QuernDevicePreset.LOW_END, lowEndAppliedToast) }
                    )
                }
            }
        }

        // 2. Storage & Cache
        item(key = "storage_cache") {
            SettingSectionCard(title = qString(R.string.system_storage_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = qString(R.string.system_cache_size, cacheSizeText),
                        color = LumaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    SelectablePillOption(
                        label = qString(R.string.system_clear_cache),
                        selected = true,
                        onClick = {
                            onClearCache()
                            cacheRefreshTick++
                        }
                    )
                }
            }
        }

        // 3. Diagnostics (Read-Only, Zero Secrets)
        item(key = "diagnostics") {
            SettingSectionCard(title = qString(R.string.system_diagnostics_title)) {
                val diagIsTvDevice = isTvHardware(LocalContext.current)
                HelpTextLine(qString(R.string.system_diagnostics_help))
                val diagLines = buildList {
                    add("App Version" to "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
                    add("Device" to "${Build.MANUFACTURER} ${Build.MODEL}")
                    add("Android OS" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    add("CPU ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"))
                    add("Media Engine" to "Media3 ExoPlayer 1.5.1 + FFmpeg Audio Extension")
                    if (diagIsTvDevice) {
                        add("Local Companion" to companionUrl)
                    }
                    add("Installed Addons" to "${addons.count { it.enabled }} enabled / ${addons.size} total")
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    diagLines.forEach { (k, v) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, color = LumaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(12.dp))
                            Text(v, color = LumaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Update Channel & Crash Reporting intentionally hidden until backed by real runtime behavior

        // 4. About & Open-Source Licenses
        item(key = "about_licenses") {
            SettingSectionCard(title = qString(R.string.system_about_title)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.shadow_studio_logo),
                        contentDescription = "SHADOW STUDIO",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Quern v${BuildConfig.VERSION_NAME}",
                            color = LumaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "By SHADOW STUDIO",
                            color = palette.focusRing,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Client-only Native Android & Android TV Media Player",
                            color = LumaTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SelectablePillOption(
                    label = qString(R.string.system_licenses_btn),
                    selected = false,
                    onClick = { showLicensesDialog = true }
                )
            }
        }

        // 6. Developer Debug Section — STRICTLY VISIBLE IN DEBUG BUILDS ONLY
        if (BuildConfig.DEBUG) {
            item(key = "debug_only") {
                CollapsibleAdvancedCard {
                    Text(
                        text = qString(R.string.system_debug_title),
                        color = palette.focusRing,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Mode: ${if (isTv) "TV" else "Phone"} • Build: ${BuildConfig.BUILD_TYPE} • AppID: ${BuildConfig.APPLICATION_ID}",
                        color = LumaTextSecondary,
                        fontSize = 11.5.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectablePillOption(
                            label = "Inspect Non-Secret Settings JSON",
                            selected = false,
                            onClick = { showDebugJsonDialog = true }
                        )
                        SelectablePillOption(
                            label = "Force Refresh Home Catalogs",
                            selected = false,
                            onClick = onRefreshHome
                        )
                        if (onDebugSimulatePostUpdate != null) {
                            SelectablePillOption(
                                label = "Simulate Post-Update What's New",
                                selected = false,
                                onClick = {
                                    onDebugSimulatePostUpdate()
                                    onShowToast("Simulated update to v${BuildConfig.VERSION_NAME} — go back to Home")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        val licenses = listOf(
            "AndroidX & Jetpack Compose / Material3" to "Apache License 2.0",
            "AndroidX Media3 (ExoPlayer)" to "Apache License 2.0",
            "Jellyfin Media3 FFmpeg Decoder" to "LGPL-2.1+ / GPL-compatible native decoder",
            "AndroidX Room & Security Crypto" to "Apache License 2.0",
            "Kotlin Coroutines & Serialization" to "Apache License 2.0",
            "OkHttp & Okio (Square)" to "Apache License 2.0",
            "Coil Image Loader" to "Apache License 2.0",
            "ZXing Core (QR Generation)" to "Apache License 2.0"
        )
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text(qString(R.string.system_licenses_btn), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(licenses) { (lib, lic) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(lib, color = LumaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(lic, color = LumaTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text(qString(R.string.common_close), color = LumaTextSecondary)
                }
            }
        )
    }

    if (showDebugJsonDialog && BuildConfig.DEBUG) {
        val snapshotJson = remember { onExportSettingsJson() }
        AlertDialog(
            onDismissRequest = { showDebugJsonDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text("Non-Secret Settings JSON (Debug)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = snapshotJson,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDebugJsonDialog = false }) {
                    Text(qString(R.string.common_close), color = LumaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsGlobalSearchDialog(
    entries: List<SettingsSearchEntry>,
    onDismiss: () -> Unit,
    onSelect: (SettingsSearchEntry) -> Unit
) {
    val palette = LocalQuernPalette.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, entries) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) entries else entries.filter {
            it.title.lowercase().contains(q) || it.keywords.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = { Text(qString(R.string.settings_search_placeholder), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(qString(R.string.settings_search_placeholder), fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Text(qString(R.string.settings_search_no_results), color = LumaTextSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { "${it.group}_${it.subPage}_${it.title}" }) { entry ->
                            var itemFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (itemFocused) palette.accent else palette.surface)
                                    .border(
                                        width = if (itemFocused) 2.dp else 1.dp,
                                        color = if (itemFocused) palette.focusRing else palette.border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .tvClickable(
                                        onFocusChangedState = { itemFocused = it },
                                        onClick = { onSelect(entry) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    entry.group.icon,
                                    contentDescription = null,
                                    tint = if (itemFocused) palette.onAccent else palette.focusRing,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = entry.title,
                                    color = if (itemFocused) palette.onAccent else LumaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(qString(R.string.common_close), color = LumaTextSecondary)
            }
        }
    )
}

@Composable
private fun SettingsCategoryRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    isModified: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = when {
            focused -> palette.accent
            selected -> palette.accent.copy(alpha = 0.24f)
            else -> Color.Transparent
        },
        animationSpec = tween(130),
        label = "rail_bg"
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "rail_scale"
    )
    val fg = when {
        focused -> palette.onAccent
        selected -> palette.focusRing
        else -> LumaTextSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(1.75.dp, palette.focusRing, RoundedCornerShape(14.dp))
                else Modifier
            )
            .tvClickable(
                onFocusChangedState = { focused = it },
                onFocused = onSelect,
                onClick = onSelect
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (focused) Color.Black.copy(alpha = 0.16f) else if (selected) palette.accent.copy(alpha = 0.28f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (focused) palette.onAccent else if (selected) LumaTextPrimary else LumaTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (isModified) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (focused) palette.onAccent else palette.focusRing)
            )
        }
    }
}

private data class SettingsGroupRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val target: SettingsSubPage
)

@Composable
private fun SettingsCategoryOverviewPane(
    group: SettingsGroup,
    groupTitle: String,
    isModified: Boolean,
    settings: AppSettings,
    account: AccountUi,
    connectedDebridCount: Int = 0,
    mediaServerCount: Int = 0,
    tmdbConfigured: Boolean = false,
    traktConnected: Boolean = false,
    onOpenSubPage: (SettingsSubPage) -> Unit
) {
    val s = LocalQuernStrings.current
    val palette = LocalQuernPalette.current
    val isTvDevice = isTvHardware(LocalContext.current)
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC

    val rows = when (group) {
        SettingsGroup.ADDONS,
        SettingsGroup.APPEARANCE,
        SettingsGroup.HOME_LAYOUT,
        SettingsGroup.PLAYBACK,
        SettingsGroup.SYSTEM -> emptyList()
        SettingsGroup.SERVICES -> listOf(
            SettingsGroupRow(
                Icons.Default.Link,
                qString(R.string.services_debrid_title),
                if (connectedDebridCount > 0) "$connectedDebridCount connected • ${qString(R.string.services_debrid_desc)}"
                else qString(R.string.services_debrid_desc),
                SettingsSubPage.INTEGRATIONS
            ),
            SettingsGroupRow(
                Icons.Default.Dns,
                qString(R.string.services_media_servers_title),
                if (mediaServerCount > 0) "$mediaServerCount configured • ${qString(R.string.services_media_servers_desc)}"
                else qString(R.string.services_media_servers_desc),
                SettingsSubPage.MEDIA_SERVERS
            )
        )
        SettingsGroup.ACCOUNTS_SYNC -> listOf(
            SettingsGroupRow(
                Icons.Default.Sync,
                qString(R.string.accounts_tracking_title),
                buildString {
                    append(if (tmdbConfigured) "TMDB ✓" else "TMDB —")
                    append(" • ")
                    append(if (traktConnected) "Trakt ✓" else "Trakt —")
                },
                SettingsSubPage.TRACKING
            ),
            SettingsGroupRow(
                Icons.Default.Person,
                qString(R.string.accounts_cloud_title),
                if (account.state.isSignedIn) account.state.email else qString(R.string.qa_not_signed_in),
                SettingsSubPage.ACCOUNT
            ),
            SettingsGroupRow(
                Icons.Default.Group,
                qString(R.string.accounts_profiles_title),
                "${s.switchProfileSubtitle} (${settings.activeProfile.name})",
                SettingsSubPage.PROFILES
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsSectionHeaderBar(
            title = groupTitle,
            subtitle = when (group) {
                SettingsGroup.SERVICES -> qString(R.string.services_notice)
                else -> ""
            },
            isModified = isModified
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            color = palette.glassSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .frostedSheen(cornerRadius = 20.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rows.forEachIndexed { idx, row ->
                    var focused by remember { mutableStateOf(false) }
                    val rowBg by animateColorAsState(
                        targetValue = if (focused) palette.accent else Color.Transparent,
                        animationSpec = tween(130),
                        label = "overview_row_bg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(rowBg)
                            .border(
                                width = if (focused) 2.5.dp else 0.dp,
                                color = if (focused) palette.focusRing else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .tvClickable(
                                onFocusChangedState = { focused = it },
                                onClick = { onOpenSubPage(row.target) }
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (focused) Color.Black.copy(alpha = 0.18f) else palette.accent.copy(alpha = 0.26f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                row.icon,
                                contentDescription = null,
                                tint = if (focused) palette.onAccent else palette.focusRing,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                color = if (focused) palette.onAccent else LumaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = row.subtitle,
                                color = if (focused) palette.onAccent.copy(alpha = 0.85f) else LumaTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSubPageContent(
    subPage: SettingsSubPage,
    isTv: Boolean = false,
    settings: AppSettings,
    pinMigrationState: PinMigrationState = PinMigrationState.DONE,
    onRetryPinMigration: () -> Unit = {},
    companionUrl: String,
    companionSession: CompanionSessionState,
    onRefreshCompanionSession: () -> Unit,
    onBindCompanionServer: () -> Unit = {},
    onUnbindCompanionServer: () -> Unit = {},
    account: AccountUi,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSyncStremio: (String, String) -> Unit = { _, _ -> },
    onImportNuvio: (String) -> Unit = {},
    onExportSettingsCode: () -> String = { "" },
    onImportSettingsCode: (String) -> Unit = {}
) {
    val s = LocalQuernStrings.current
    val palette = LocalQuernPalette.current
    val isTvDevice = isTvHardware(LocalContext.current)
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(subPage) {
        runCatching { backFocus.requestFocus() }
    }

    val title = when (subPage) {
        SettingsSubPage.ACCOUNT -> s.accountTitle
        SettingsSubPage.PROFILES -> s.switchProfileTitle
        else -> s.settingsTitle
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "topbar") {
            SubPageTopBar(title = title, onBack = onBack, backFocusRequester = backFocus)
        }

        when (subPage) {
            SettingsSubPage.ACCOUNT -> {
                item(key = "quern_account") {
                    QuernAccountCard(account = account, isTv = isTv)
                }

                // Portable Settings Backup (Strictly NO Secrets)
                item(key = "settings_export_import") {
                    var showExportDialog by remember { mutableStateOf(false) }
                    var showImportDialog by remember { mutableStateOf(false) }
                    val exportedCode = remember(showExportDialog) {
                        if (showExportDialog) onExportSettingsCode() else ""
                    }

                    SettingSectionCard(title = qString(R.string.settings_export_import_title)) {
                        HelpTextLine(qString(R.string.settings_export_import_help))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectablePillOption(
                                label = qString(R.string.settings_export_btn),
                                selected = true,
                                onClick = { showExportDialog = true }
                            )
                            SelectablePillOption(
                                label = qString(R.string.settings_import_btn),
                                selected = false,
                                onClick = { showImportDialog = true }
                            )
                        }
                    }

                    if (showExportDialog) {
                        AlertDialog(
                            onDismissRequest = { showExportDialog = false },
                            containerColor = palette.surfaceElevated,
                            title = { Text(qString(R.string.settings_export_import_title), color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(qString(R.string.settings_export_import_help), color = LumaTextSecondary, fontSize = 12.sp)
                                    if (exportedCode.isNotBlank()) {
                                        QrCodeCard(content = exportedCode, size = 150.dp)
                                        OutlinedTextField(
                                            value = exportedCode,
                                            onValueChange = {},
                                            readOnly = true,
                                            maxLines = 4,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = palette.focusRing,
                                                unfocusedBorderColor = palette.border,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showExportDialog = false }) {
                                    Text(qString(R.string.common_close), color = LumaTextSecondary)
                                }
                            }
                        )
                    }

                    if (showImportDialog) {
                        var draftCode by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            containerColor = palette.surfaceElevated,
                            title = { Text(qString(R.string.settings_import_btn), color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(qString(R.string.settings_export_import_help), color = LumaTextSecondary, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = draftCode,
                                        onValueChange = { draftCode = it },
                                        placeholder = { Text("QUERN-CFG-1:...", fontSize = 12.sp) },
                                        maxLines = 5,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = palette.focusRing,
                                            unfocusedBorderColor = palette.border,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                SelectablePillOption(
                                    label = qString(R.string.common_save),
                                    selected = true,
                                    onClick = {
                                        if (draftCode.isNotBlank()) {
                                            onImportSettingsCode(draftCode)
                                            showImportDialog = false
                                        }
                                    }
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                                }
                            }
                        )
                    }
                }

                if (isTvDevice && account.state.isSignedIn) item(key = "qr_phone_setup") {
                    QuernCompanionSignInCard(
                        url = companionUrl,
                        session = companionSession,
                        onRefreshSession = onRefreshCompanionSession,
                        onBindServer = onBindCompanionServer,
                        onUnbindServer = onUnbindCompanionServer,
                        compactVertical = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Stremio & Nuvio Direct Sync Card
                item(key = "stremio_nuvio_sync") {
                    var showStremioDialog by remember { mutableStateOf(false) }
                    var showNuvioDialog by remember { mutableStateOf(false) }

                    SettingSectionCard(title = qString(R.string.account_stremio_nuvio_title)) {
                        Text(
                            text = if (isTvDevice) {
                                qString(R.string.account_stremio_nuvio_desc, companionUrl)
                            } else {
                                qString(R.string.account_stremio_nuvio_desc_phone)
                            },
                            color = LumaTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectablePillOption(
                                label = qString(R.string.account_sync_stremio_btn),
                                selected = true,
                                onClick = { showStremioDialog = true }
                            )
                            SelectablePillOption(
                                label = qString(R.string.account_import_nuvio_btn),
                                selected = false,
                                onClick = { showNuvioDialog = true }
                            )
                        }
                    }

                    if (showStremioDialog) {
                        var stEmail by remember { mutableStateOf("") }
                        var stPass by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showStremioDialog = false },
                            containerColor = palette.surfaceElevated,
                            title = { Text(qString(R.string.account_stremio_dialog_title), color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(qString(R.string.account_stremio_dialog_desc), color = LumaTextSecondary, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = stEmail,
                                        onValueChange = { stEmail = it },
                                        label = { Text(qString(R.string.account_stremio_email)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = palette.focusRing,
                                            unfocusedBorderColor = palette.border,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = stPass,
                                        onValueChange = { stPass = it },
                                        label = { Text(qString(R.string.account_stremio_password)) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = palette.focusRing,
                                            unfocusedBorderColor = palette.border,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                SelectablePillOption(
                                    label = qString(R.string.account_sync_now),
                                    selected = true,
                                    onClick = {
                                        showStremioDialog = false
                                        onSyncStremio(stEmail, stPass)
                                    }
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = { showStremioDialog = false }) { Text(qString(R.string.common_cancel), color = LumaTextSecondary) }
                            }
                        )
                    }

                    if (showNuvioDialog) {
                        var nuvioInput by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showNuvioDialog = false },
                            containerColor = palette.surfaceElevated,
                            title = { Text(qString(R.string.account_import_nuvio_btn), color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (isTvDevice) {
                                            qString(R.string.account_nuvio_dialog_desc, companionUrl)
                                        } else {
                                            qString(R.string.account_nuvio_dialog_desc_phone)
                                        },
                                        color = LumaTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    OutlinedTextField(
                                        value = nuvioInput,
                                        onValueChange = { nuvioInput = it },
                                        label = { Text(qString(R.string.account_nuvio_field)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = palette.focusRing,
                                            unfocusedBorderColor = palette.border,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                SelectablePillOption(
                                    label = qString(R.string.account_import_btn),
                                    selected = true,
                                    onClick = {
                                        showNuvioDialog = false
                                        onImportNuvio(nuvioInput)
                                    }
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = { showNuvioDialog = false }) { Text(qString(R.string.common_cancel), color = LumaTextSecondary) }
                            }
                        )
                    }
                }

            }

            SettingsSubPage.PROFILES -> {
                item(key = "profiles_manager") {
                    ProfilesManagerSection(
                        settings = settings,
                        pinMigrationState = pinMigrationState,
                        onRetryPinMigration = onRetryPinMigration,
                        isAr = isAr,
                        onUpdateSettings = onUpdateSettings
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun SubPageTopBar(
    title: String,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester? = null
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (focused) palette.accent else palette.surfaceElevated,
            shape = CircleShape,
            border = BorderStroke(if (focused) 2.5.dp else 1.dp, if (focused) palette.focusRing else palette.border),
            modifier = Modifier
                .size(42.dp)
                .then(if (backFocusRequester != null) Modifier.focusRequester(backFocusRequester) else Modifier)
                .clip(CircleShape)
                .tvClickable(
                    onFocusChangedState = { focused = it },
                    onClick = onBack
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (focused) palette.onAccent else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = LumaTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalQuernPalette.current
    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 18.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = LumaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun SelectablePillOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        selected -> palette.accent.copy(alpha = 0.82f)
        else -> palette.glassElevated
    }
    val fg = if (focused || selected) palette.onAccent else LumaTextPrimary

    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = modifier
            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onFocused = onFocused,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (isCompact) 12.dp else 10.dp
            )
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalQuernPalette.current
    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) palette.accent else Color.Transparent)
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) palette.focusRing else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (focused) palette.onAccent else LumaTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = quernSwitchColors(onAccentSurface = focused),
            modifier = Modifier.focusProperties { canFocus = false }
        )
    }
}

private enum class ProfilePinAction {
    UNLOCK_SWITCH,
    ENTER_MANAGE,
    REMOVE_PIN,
    CHANGE_PIN,
    DELETE_PROFILE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilesManagerSection(
    settings: AppSettings,
    pinMigrationState: PinMigrationState = PinMigrationState.DONE,
    onRetryPinMigration: () -> Unit = {},
    isAr: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val palette = LocalQuernPalette.current
    val s = LocalQuernStrings.current
    var manageMode by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pinChallenge by remember { mutableStateOf<Pair<String, ProfilePinAction>?>(null) }
    var pendingPinChallenge by remember { mutableStateOf<Pair<String, ProfilePinAction>?>(null) }
    var managePinOptionsProfileId by remember { mutableStateOf<String?>(null) }
    var forgotPinProfileId by remember { mutableStateOf<String?>(null) }
    var failedMigrationProfileId by remember { mutableStateOf<String?>(null) }
    var setPinTargetProfile by remember { mutableStateOf<UserProfile?>(null) }
    var avatarPickerProfileId by remember { mutableStateOf<String?>(null) }

    // Deterministic single-profile authority for gating Manage Profiles when any profile is locked:
    // Primary profile (index 0) if it is locked; otherwise the first locked profile in profile order.
    val manageGateProfile: UserProfile? = remember(settings.profiles) {
        val primary = settings.profiles.firstOrNull()
        if (primary != null && primary.isLocked) primary else settings.profiles.firstOrNull { it.isLocked }
    }

    fun requestPinChallenge(profile: UserProfile, action: ProfilePinAction) {
        when {
            pinMigrationState == PinMigrationState.MIGRATING -> {
                pendingPinChallenge = profile.id to action
            }
            PinHasher.isHashed(profile.pinCode) -> {
                pinChallenge = profile.id to action
            }
            else -> {
                failedMigrationProfileId = profile.id
            }
        }
    }

    // Wait for PIN migration to complete before opening any PIN challenge so it never fails closed during the race
    LaunchedEffect(pendingPinChallenge, pinMigrationState, settings.profiles) {
        val (pendingId, action) = pendingPinChallenge ?: return@LaunchedEffect
        if (pinMigrationState == PinMigrationState.MIGRATING) return@LaunchedEffect
        val live = settings.profiles.firstOrNull { it.id == pendingId }
        pendingPinChallenge = null
        if (live == null) return@LaunchedEffect
        when {
            !live.isLocked -> {
                when (action) {
                    ProfilePinAction.UNLOCK_SWITCH -> onUpdateSettings { it.copy(activeProfileId = live.id) }
                    ProfilePinAction.ENTER_MANAGE -> manageMode = true
                    else -> Unit
                }
            }
            PinHasher.isHashed(live.pinCode) -> pinChallenge = live.id to action
            else -> failedMigrationProfileId = live.id
        }
    }

    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 22.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pinMigrationState == PinMigrationState.MIGRATING) {
                Text(
                    text = if (isAr) "جارٍ تأمين رموز PIN للملفات…" else "Securing profile PINs…",
                    color = palette.focusRing,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            } else if (pinMigrationState == PinMigrationState.FAILED) {
                Text(
                    text = if (isAr) "تعذر ترقية رمز PIN قديم. اختر الملف لإعادة المحاولة أو إعادة تعيين الرمز."
                    else "A legacy profile PIN could not be upgraded. Select the profile to retry or reset its PIN.",
                    color = LumaRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                settings.profiles.forEach { profile ->
                    val selected = profile.id == settings.activeProfileId
                    var focused by remember { mutableStateOf(false) }
                    val avatarColor = Color(profile.avatarColorHex)
                    val avatarScale by animateFloatAsState(
                        targetValue = if (focused) 1.05f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "profile_scale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(102.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .graphicsLayer { scaleX = avatarScale; scaleY = avatarScale }
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = {
                                        if (manageMode) {
                                            if (profile.isLocked) {
                                                // Never clear PIN directly; open Change / Remove / Forgot PIN options requiring current PIN
                                                managePinOptionsProfileId = profile.id
                                            } else {
                                                setPinTargetProfile = profile
                                            }
                                        } else {
                                            if (profile.isLocked && profile.id != settings.activeProfileId) {
                                                requestPinChallenge(profile, ProfilePinAction.UNLOCK_SWITCH)
                                            } else {
                                                onUpdateSettings { it.copy(activeProfileId = profile.id) }
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (focused) 4.dp else if (selected) 3.dp else 3.dp,
                                        color = when {
                                            focused -> palette.focusRing
                                            selected -> Color.White
                                            else -> avatarColor.copy(alpha = 0.45f)
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                ProfileAvatarCircle(profile, size = 86.dp, fontSizeSp = 28f)
                            }

                            // Bottom-right dark circular Lock badge (matches reference screenshot!)
                            if (profile.isLocked) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF151921))
                                        .border(1.5.dp, Color.Black, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color(0xFFD0D5DD),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else if (selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(LumaGreen)
                                        .border(1.5.dp, Color.Black, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = profile.name,
                            color = if (focused || selected) Color.White else LumaTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        if (manageMode) {
                            Spacer(Modifier.height(8.dp))
                            MiniActionChip(
                                icon = Icons.Default.Face,
                                label = qString(R.string.profile_picture_label),
                                tint = palette.focusRing,
                                onClick = { avatarPickerProfileId = profile.id }
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniActionChip(
                                    icon = if (profile.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    label = if (profile.isLocked) (if (isAr) "الرمز" else "PIN") else (if (isAr) "قفل" else "Lock"),
                                    tint = if (profile.isLocked) LumaGreen else palette.focusRing,
                                    onClick = {
                                        if (profile.isLocked) {
                                            // Require current PIN via Change / Remove / Forgot options; never clear silently
                                            managePinOptionsProfileId = profile.id
                                        } else {
                                            setPinTargetProfile = profile
                                        }
                                    }
                                )
                                if (settings.profiles.size > 1) {
                                    MiniActionChip(
                                        icon = Icons.Default.Delete,
                                        label = "",
                                        tint = LumaRed,
                                        onClick = {
                                            if (profile.isLocked) {
                                                requestPinChallenge(profile, ProfilePinAction.DELETE_PROFILE)
                                            } else {
                                                onUpdateSettings { st ->
                                                    val remaining = st.profiles.filterNot { it.id == profile.id }
                                                    val nextActive = if (st.activeProfileId == profile.id) remaining.first().id else st.activeProfileId
                                                    st.copy(profiles = remaining, activeProfileId = nextActive)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectablePillOption(
                    label = if (manageMode) (if (isAr) "تم" else "Done") else s.manageProfilesLabel,
                    selected = manageMode,
                    onClick = {
                        if (manageMode) {
                            manageMode = false
                        } else if (manageGateProfile != null) {
                            requestPinChallenge(manageGateProfile, ProfilePinAction.ENTER_MANAGE)
                        } else {
                            manageMode = true
                        }
                    }
                )
                if (manageMode) {
                    SelectablePillOption(
                        label = "+ ${s.addProfileLabel}",
                        selected = true,
                        onClick = { showCreateDialog = true }
                    )
                }
            }
        }
    }

    val profileScope = rememberCoroutineScope()

    if (showCreateDialog) {
        CreateProfileDialog(
            isAr = isAr,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, colorHex, pin, avatarKey ->
                showCreateDialog = false
                profileScope.launch {
                    val newProfile = buildNewProfile(name, colorHex, pin, avatarKey)
                    onUpdateSettings { st ->
                        st.copy(
                            profiles = st.profiles + newProfile,
                            activeProfileId = newProfile.id
                        )
                    }
                }
            }
        )
    }

    val avatarPickerProfile = avatarPickerProfileId?.let { id ->
        settings.profiles.firstOrNull { it.id == id }
    }
    avatarPickerProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { avatarPickerProfileId = null },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = "${qString(R.string.profile_choose_picture)} — ${target.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileAvatarGrid(
                        profileName = target.name,
                        colorHex = target.avatarColorHex,
                        selectedKey = target.avatarKey,
                        onSelect = { key ->
                            avatarPickerProfileId = null
                            onUpdateSettings { st ->
                                st.copy(profiles = st.profiles.map {
                                    if (it.id == target.id) it.copy(avatarKey = key) else it
                                })
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { avatarPickerProfileId = null }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }

    val manageOptionsProfile = managePinOptionsProfileId?.let { id ->
        settings.profiles.firstOrNull { it.id == id }
    }
    manageOptionsProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { managePinOptionsProfileId = null },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = if (isAr) "إدارة رمز PIN — ${target.name}" else "Manage PIN — ${target.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isAr) {
                            "يتطلب تغيير أو إزالة الرمز إدخال الرمز الحالي أولاً."
                        } else {
                            "Changing or removing the PIN requires verifying the current PIN first."
                        },
                        color = LumaTextSecondary,
                        fontSize = 13.sp
                    )
                    SelectablePillOption(
                        label = if (isAr) "تغيير رمز PIN" else "Change PIN",
                        selected = true,
                        onClick = {
                            managePinOptionsProfileId = null
                            requestPinChallenge(target, ProfilePinAction.CHANGE_PIN)
                        }
                    )
                    SelectablePillOption(
                        label = if (isAr) "إزالة رمز PIN" else "Remove PIN",
                        selected = false,
                        onClick = {
                            managePinOptionsProfileId = null
                            requestPinChallenge(target, ProfilePinAction.REMOVE_PIN)
                        }
                    )
                    SelectablePillOption(
                        label = if (isAr) "نسيت الرمز؟" else "Forgot PIN?",
                        selected = false,
                        onClick = {
                            managePinOptionsProfileId = null
                            forgotPinProfileId = target.id
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { managePinOptionsProfileId = null }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }

    val activeChallenge = pinChallenge?.let { (id, action) ->
        settings.profiles.firstOrNull { it.id == id }?.let { it to action }
    }
    activeChallenge?.let { (target, action) ->
        val (dialogTitle, dialogSubtitle) = when (action) {
            ProfilePinAction.UNLOCK_SWITCH ->
                qString(R.string.profile_unlock_title, target.name) to qString(R.string.profile_unlock_subtitle)
            ProfilePinAction.ENTER_MANAGE ->
                (if (isAr) "التحقق لإدارة الملفات" else "Verify PIN to Manage Profiles") to
                    (if (isAr) "أدخل رمز PIN الحالي لملف ${target.name}" else "Enter current PIN for ${target.name}")
            ProfilePinAction.REMOVE_PIN ->
                (if (isAr) "تأكيد إزالة رمز PIN" else "Verify Current PIN") to
                    (if (isAr) "أدخل الرمز الحالي لإزالة القفل عن ${target.name}" else "Enter current PIN to remove lock on ${target.name}")
            ProfilePinAction.CHANGE_PIN ->
                (if (isAr) "تأكيد تغيير رمز PIN" else "Verify Current PIN") to
                    (if (isAr) "أدخل الرمز الحالي لملف ${target.name} لتغييره" else "Enter current PIN for ${target.name} before setting a new one")
            ProfilePinAction.DELETE_PROFILE ->
                (if (isAr) "تأكيد حذف الملف" else "Verify PIN to Delete") to
                    (if (isAr) "أدخل الرمز الحالي لحذف ${target.name}" else "Enter current PIN to delete ${target.name}")
        }
        PinKeypadDialog(
            profileId = target.id,
            title = dialogTitle,
            subtitle = dialogSubtitle,
            expectedPinHash = target.pinCode,
            isAr = isAr,
            onForgotPin = {
                pinChallenge = null
                forgotPinProfileId = target.id
            },
            onDismiss = { pinChallenge = null },
            onPinAccepted = {
                pinChallenge = null
                when (action) {
                    ProfilePinAction.UNLOCK_SWITCH -> {
                        onUpdateSettings { it.copy(activeProfileId = target.id) }
                    }
                    ProfilePinAction.ENTER_MANAGE -> {
                        manageMode = true
                    }
                    ProfilePinAction.REMOVE_PIN -> {
                        onUpdateSettings { st ->
                            st.copy(
                                profiles = st.profiles.map {
                                    if (it.id == target.id) it.copy(pinCode = null) else it
                                }
                            )
                        }
                    }
                    ProfilePinAction.CHANGE_PIN -> {
                        setPinTargetProfile = target
                    }
                    ProfilePinAction.DELETE_PROFILE -> {
                        onUpdateSettings { st ->
                            if (st.profiles.size <= 1) st else {
                                val remaining = st.profiles.filterNot { it.id == target.id }
                                val nextActive = if (st.activeProfileId == target.id) remaining.first().id else st.activeProfileId
                                st.copy(profiles = remaining, activeProfileId = nextActive)
                            }
                        }
                    }
                }
            }
        )
    }

    val forgotPinProfile = forgotPinProfileId?.let { id ->
        settings.profiles.firstOrNull { it.id == id }
    }
    forgotPinProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { forgotPinProfileId = null },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = if (isAr) "نسيت رمز PIN؟" else "Forgot PIN?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isAr) {
                        "سيؤدي هذا إلى إزالة رمز PIN للملف '${target.name}' فقط. لن تتأثر رموز الملفات الأخرى."
                    } else {
                        "This will remove the PIN for '${target.name}' only. Other profiles will not be affected."
                    },
                    color = LumaTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                SelectablePillOption(
                    label = if (isAr) "إزالة رمز هذا الملف" else "Remove PIN for Profile",
                    selected = true,
                    onClick = {
                        forgotPinProfileId = null
                        PinAttemptTracker.recordSuccess(target.id)
                        onUpdateSettings { st ->
                            st.copy(
                                profiles = st.profiles.map {
                                    if (it.id == target.id) it.copy(pinCode = null) else it
                                }
                            )
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { forgotPinProfileId = null }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }

    val failedMigrationProfile = failedMigrationProfileId?.let { id ->
        settings.profiles.firstOrNull { it.id == id }
    }
    failedMigrationProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { failedMigrationProfileId = null },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = if (isAr) "تعذر ترقية رمز PIN" else "PIN Upgrade Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isAr) {
                        "لم تكتمل ترقية رمز PIN القديم للملف '${target.name}'. يمكنك إعادة المحاولة أو إعادة تعيين الرمز لفتح الملف دون فقدان الوصول."
                    } else {
                        "The legacy PIN for '${target.name}' could not be upgraded automatically. You can retry the upgrade or reset the PIN to open the profile."
                    },
                    color = LumaTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                SelectablePillOption(
                    label = if (isAr) "إعادة المحاولة" else "Retry Upgrade",
                    selected = true,
                    onClick = {
                        failedMigrationProfileId = null
                        pendingPinChallenge = target.id to ProfilePinAction.UNLOCK_SWITCH
                        onRetryPinMigration()
                    }
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectablePillOption(
                        label = if (isAr) "إزالة الرمز والفتح" else "Reset PIN & Open",
                        selected = false,
                        onClick = {
                            failedMigrationProfileId = null
                            onUpdateSettings { st ->
                                st.copy(
                                    profiles = st.profiles.map {
                                        if (it.id == target.id) it.copy(pinCode = null) else it
                                    },
                                    activeProfileId = target.id
                                )
                            }
                        }
                    )
                    TextButton(onClick = { failedMigrationProfileId = null }) {
                        Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                    }
                }
            }
        )
    }

    setPinTargetProfile?.let { target ->
        PinKeypadDialog(
            profileId = target.id,
            title = qString(R.string.profile_set_pin_title, target.name),
            subtitle = qString(R.string.profile_set_pin_subtitle),
            expectedPinHash = null,
            isAr = isAr,
            onDismiss = { setPinTargetProfile = null },
            onPinAccepted = { newPin ->
                setPinTargetProfile = null
                profileScope.launch {
                    val hashed = PinHasher.hashPinAsync(newPin)
                    onUpdateSettings { st ->
                        st.copy(
                            profiles = st.profiles.map {
                                if (it.id == target.id) it.copy(pinCode = hashed) else it
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun MiniActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = if (focused) palette.accent else palette.surfaceElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) palette.focusRing else palette.border),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label.isEmpty()) 8.dp else 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = if (focused) palette.onAccent else tint, modifier = Modifier.size(14.dp))
            if (label.isNotEmpty()) {
                Text(label, color = if (focused) palette.onAccent else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** New profile from the create dialog; a 4-digit PIN is hashed off the main thread. */
internal suspend fun buildNewProfile(name: String, colorHex: Long, pin: String, avatarKey: String?): UserProfile {
    val hashedPin = pin.takeIf { it.length == 4 }?.let { PinHasher.hashPinAsync(it) }
    return UserProfile(
        id = "prof_${UUID.randomUUID().toString().take(8)}",
        name = name.trim(),
        avatarColorHex = colorHex,
        pinCode = hashedPin,
        avatarKey = avatarKey
    )
}

@Composable
internal fun CreateProfileDialog(
    isAr: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Long, String, String?) -> Unit
) {
    val palette = LocalQuernPalette.current
    val focusManager = LocalFocusManager.current
    val firstColorRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var avatarKey by remember { mutableStateOf<String?>(null) }
    val avatarColors = listOf(
        0xFFD97736L,
        0xFF8C3B5EL,
        0xFF0E7C7BL,
        0xFF345372L,
        0xFF10B981L,
        0xFFD3D3D3L
    )
    var selectedColor by remember { mutableStateOf(avatarColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = {
            Text(
                text = qString(R.string.profile_create_title),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(qString(R.string.profile_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { firstColorRequester.requestFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                firstColorRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                )

                Text(
                    text = qString(R.string.profile_avatar_color),
                    color = LumaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    avatarColors.forEachIndexed { idx, hex ->
                        val chosen = selectedColor == hex
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .then(if (idx == 0) Modifier.focusRequester(firstColorRequester) else Modifier)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (focused || chosen) 3.dp else 1.dp,
                                    color = if (focused) palette.focusRing else if (chosen) Color.White else palette.border,
                                    shape = CircleShape
                                )
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = { selectedColor = hex }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (chosen) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = if (hex == 0xFFD3D3D3L) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = qString(R.string.profile_picture_label),
                    color = LumaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ProfileAvatarGrid(
                    profileName = name,
                    colorHex = selectedColor,
                    selectedKey = avatarKey,
                    itemSize = 44.dp,
                    onSelect = { avatarKey = it }
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input -> pin = input.filter { it.isDigit() }.take(4) },
                    label = { Text(qString(R.string.profile_pin_optional)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        focusManager.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )
            }
        },
        confirmButton = {
            SelectablePillOption(
                label = qString(R.string.profile_create_btn),
                selected = true,
                onClick = {
                    if (name.isNotBlank()) onCreate(name, selectedColor, pin, avatarKey)
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(qString(R.string.common_cancel), color = LumaTextSecondary)
            }
        }
    )
}

private object PinAttemptTracker {
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L
    private val failedCounts = mutableMapOf<String, Int>()
    private val lockUntilMap = mutableMapOf<String, Long>()

    @Synchronized
    fun lockoutRemainingSec(profileId: String, nowMs: Long = System.currentTimeMillis()): Long {
        val until = lockUntilMap[profileId] ?: 0L
        return ((until - nowMs + 999L) / 1000L).coerceAtLeast(0L)
    }

    @Synchronized
    fun recordFailure(profileId: String, nowMs: Long = System.currentTimeMillis()): Int {
        val next = (failedCounts[profileId] ?: 0) + 1
        if (next >= MAX_ATTEMPTS) {
            failedCounts[profileId] = 0
            lockUntilMap[profileId] = nowMs + LOCKOUT_MS
        } else {
            failedCounts[profileId] = next
        }
        return next
    }

    @Synchronized
    fun recordSuccess(profileId: String) {
        failedCounts.remove(profileId)
        lockUntilMap.remove(profileId)
    }
}

@Composable
internal fun PinKeypadDialog(
    profileId: String,
    title: String,
    subtitle: String,
    expectedPinHash: String?,
    isAr: Boolean,
    onForgotPin: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPinAccepted: (String) -> Unit
) {
    val palette = LocalQuernPalette.current
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var lockoutSec by remember(profileId) { mutableStateOf(PinAttemptTracker.lockoutRemainingSec(profileId)) }

    LaunchedEffect(profileId, lockoutSec) {
        while (lockoutSec > 0L) {
            delay(1000L)
            lockoutSec = PinAttemptTracker.lockoutRemainingSec(profileId)
        }
    }

    fun pushDigit(d: String) {
        if (isVerifying || lockoutSec > 0L || entered.length >= 4) return
        wrongPin = false
        val next = entered + d
        entered = next
        if (next.length == 4) {
            if (expectedPinHash == null) {
                PinAttemptTracker.recordSuccess(profileId)
                onPinAccepted(next)
            } else {
                isVerifying = true
                scope.launch {
                    val ok = PinHasher.verifyPinAsync(next, expectedPinHash)
                    isVerifying = false
                    if (ok) {
                        PinAttemptTracker.recordSuccess(profileId)
                        onPinAccepted(next)
                    } else {
                        PinAttemptTracker.recordFailure(profileId)
                        lockoutSec = PinAttemptTracker.lockoutRemainingSec(profileId)
                        wrongPin = true
                        entered = ""
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Lock, null, tint = palette.focusRing, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(6.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = LumaTextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 4 PIN Dots
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { idx ->
                        val filled = idx < entered.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (filled) palette.focusRing else Color.Transparent)
                                .border(2.dp, if (wrongPin) LumaRed else palette.focusRing, CircleShape)
                        )
                    }
                }
                if (lockoutSec > 0L) {
                    Text(
                        text = qString(R.string.profile_pin_locked_out, lockoutSec),
                        color = LumaRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (wrongPin) {
                    Text(
                        text = qString(R.string.profile_pin_wrong),
                        color = LumaRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // TV-D-Pad friendly 3x4 Number Pad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                var focused by remember { mutableStateOf(false) }
                                Surface(
                                    color = if (focused) palette.accent else palette.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (focused) 2.dp else 1.dp,
                                        color = if (focused) palette.focusRing else palette.border
                                    ),
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .tvClickable(
                                            onFocusChangedState = { focused = it },
                                            onClick = {
                                                when (key) {
                                                    "C" -> { entered = ""; wrongPin = false }
                                                    "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                                    else -> pushDigit(key)
                                                }
                                            }
                                        )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            color = if (focused) palette.onAccent else Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (expectedPinHash != null && onForgotPin != null) {
                TextButton(onClick = onForgotPin) {
                    Text(
                        text = if (isAr) "نسيت الرمز؟" else "Forgot PIN?",
                        color = palette.focusRing,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(qString(R.string.common_cancel), color = LumaTextSecondary)
            }
        }
    )
}
