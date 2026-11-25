@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.quern.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import com.quern.app.ui.components.SoftGlowBackground
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.quern.app.data.local.AppIconManager
import com.quern.app.data.sync.PendingDeviceLink
import com.quern.app.domain.model.ContinueWatchingAction
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.NavigationStyle
import com.quern.app.player.QuernPlayerScreen
import com.quern.app.ui.components.ContentFocusMemory
import com.quern.app.ui.components.DeviceLinkApproveDialog
import com.quern.app.ui.components.LocalContentFocusMemory
import com.quern.app.ui.components.LocalTvRailInset
import com.quern.app.ui.components.QuernBrandMark
import com.quern.app.ui.components.WhatsNewDialog
import com.quern.app.ui.components.currentQuernLayout
import com.quern.app.ui.components.parseReleaseEntries
import com.quern.app.ui.components.isTvHardware
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.screens.DetailScreen
import com.quern.app.ui.screens.HomeScreen
import com.quern.app.ui.screens.LibraryScreen
import com.quern.app.ui.screens.SearchScreen
import com.quern.app.ui.screens.SettingsGroup
import com.quern.app.ui.screens.AccountUi
import com.quern.app.ui.screens.TvDeviceLinkQrPanel
import com.quern.app.ui.screens.HomeEmptyTvQr
import com.quern.app.ui.components.QuernCompanionSignInCard
import com.quern.app.ui.screens.SettingsScreen
import com.quern.app.ui.screens.ProfilePickerScreen
import com.quern.app.ui.components.ProfileAvatarCircle
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.UserProfile
import androidx.compose.runtime.saveable.rememberSaveable
import com.quern.app.R
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernResources
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.QuernGlassBorderBrush
import com.quern.app.ui.theme.QuernTheme
import com.quern.app.ui.theme.glossySheen
import com.quern.app.ui.theme.qString
import okhttp3.OkHttpClient

/**
 * Clean 4-tab primary navigation (Addons & Plugins live neatly inside Settings -> Media).
 */
enum class QuernTab(val icon: ImageVector) {
    HOME(Icons.Default.Home),
    SEARCH(Icons.Default.Search),
    LIBRARY(Icons.Default.VideoLibrary),
    SETTINGS(Icons.Default.Settings)
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun QuernApp(
    viewModel: QuernViewModel,
    okHttpClient: OkHttpClient,
    pendingDeepLinkAddonUrl: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingDeviceLink: PendingDeviceLink? = null,
    onDeviceLinkDismissed: () -> Unit = {}
) {
    val settings by viewModel.appSettings.collectAsState()
    val pinMigrationState by viewModel.pinMigrationState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    // Interface size: scale LocalDensity by `scale` (fontScale unchanged relative to it) across the
    // full edge-to-edge screen so more posters and rows fit with sharp vector text and exact focus
    // rects (never graphicsLayer scaling). Covers Home, Settings, profile picker, and player.
    val screenScale = settings.screenScalePercent.coerceIn(75, 100) / 100f
    val rootDensity = LocalDensity.current
    val scaledRootDensity = remember(rootDensity, screenScale) {
        if (screenScale >= 0.999f) rootDensity
        else Density(density = rootDensity.density * screenScale, fontScale = rootDensity.fontScale)
    }

    val layoutClass = currentQuernLayout(settings.uiModeOverride)
    CompositionLocalProvider(LocalDensity provides scaledRootDensity, LocalQuernLayout provides layoutClass) {
        QuernTheme(
            themePreset = settings.theme,
            amoledPureBlack = settings.amoledPureBlack,
            language = settings.language,
            accentOverrideHex = settings.accentColor.colorHex,
            fontScaleMultiplier = settings.fontScale.multiplier,
            fontFamilyChoice = settings.fontChoice.fontFamily
        ) {
        val context = LocalContext.current
        val isTv = layoutClass == QuernLayoutClass.TV
        val palette = LocalQuernPalette.current

        val activePlayback by viewModel.activePlayback.collectAsState()
        val toastMessage by viewModel.toastMessage.collectAsState()

        var currentTab by remember { mutableStateOf(QuernTab.HOME) }
        var railFocusSyncSignal by remember { mutableIntStateOf(0) }
        var settingsInitialGroup by remember { mutableStateOf(SettingsGroup.ADDONS) }
        var showingDetail by remember { mutableStateOf(false) }
        // "Who's watching?" at launch when there is more than one profile; also opened from the rail
        // or after the first account sync pulls multiple profiles onto a new device.
        var profilePickerOpen by rememberSaveable { mutableStateOf(settings.profiles.size > 1) }
        var profilePickerShownThisLaunch by rememberSaveable { mutableStateOf(profilePickerOpen) }
        val profilesAppliedCount by viewModel.profilesAppliedCount.collectAsState()

        LaunchedEffect(profilesAppliedCount, settings.profiles.size, activePlayback) {
            if (profilesAppliedCount > 0 && settings.profiles.size > 1 && !profilePickerShownThisLaunch && activePlayback == null) {
                profilePickerOpen = true
                profilePickerShownThisLaunch = true
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }

        BackHandler(enabled = showingDetail) {
            showingDetail = false
        }
        BackHandler(enabled = !showingDetail && currentTab != QuernTab.HOME) {
            currentTab = QuernTab.HOME
            railFocusSyncSignal += 1
        }

        LaunchedEffect(toastMessage) {
            toastMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                viewModel.clearToast()
            }
        }

        LaunchedEffect(pendingDeepLinkAddonUrl) {
            if (!pendingDeepLinkAddonUrl.isNullOrBlank()) {
                currentTab = QuernTab.SETTINGS
                viewModel.installAddon(pendingDeepLinkAddonUrl)
                onDeepLinkConsumed()
            }
        }

        if (pendingDeviceLink != null && !isTvHardware(context)) {
            val linkAccountState by viewModel.accountState.collectAsState()
            DeviceLinkApproveDialog(
                request = pendingDeviceLink,
                accountState = linkAccountState,
                canApprove = viewModel.canApproveDeviceLink(),
                httpClient = okHttpClient,
                onApprove = { req, code6 ->
                    viewModel.approveDeviceLink(req, code6, okHttpClient)
                },
                onOpenAccountSettings = {
                    profilePickerOpen = false
                    settingsInitialGroup = SettingsGroup.ACCOUNTS_SYNC
                    showingDetail = false
                    currentTab = QuernTab.SETTINGS
                },
                onDismiss = onDeviceLinkDismissed
            )
        }

        if (profilePickerOpen && activePlayback == null) {
            ProfilePickerScreen(
                profiles = settings.profiles,
                activeProfileId = settings.activeProfileId,
                isAr = settings.effectiveLanguage == AppLanguage.ARABIC,
                onProfileChosen = { id ->
                    if (id != settings.activeProfileId) {
                        viewModel.updateSettings { it.copy(activeProfileId = id) }
                    }
                    currentTab = QuernTab.HOME
                    showingDetail = false
                    profilePickerOpen = false
                },
                onDismiss = { profilePickerOpen = false },
                onCreateProfile = { profile ->
                    viewModel.updateSettings { it.copy(profiles = it.profiles + profile) }
                }
            )
            return@QuernTheme
        }

        val playback = activePlayback
        if (playback != null) {
            QuernPlayerScreen(
                request = playback,
                settings = settings,
                okHttpClient = okHttpClient,
                onProgressUpdate = { pos, dur, playing -> viewModel.onPlaybackProgress(pos, dur, playing) },
                onPlaybackStop = { pos, dur -> viewModel.onPlaybackStopped(pos, dur) },
                onPlayNextEpisode = { viewModel.playNextEpisode() },
                onResolveStream = { stream -> viewModel.resolveStreamForPlayer(stream) },
                onActiveStreamChanged = { stream -> viewModel.onPlayerActiveStreamChanged(stream) },
                onRecordSourceResult = { stream, ok -> viewModel.recordPlaybackSourceResult(stream, ok) },
                onBack = { viewModel.closePlayer() }
            )
            return@QuernTheme
        }

        val openItemDetail: (MediaItem) -> Unit = { item ->
            viewModel.openDetail(item)
            showingDetail = true
        }

        val accountUi: @Composable () -> AccountUi = {
            val deviceLinkState by viewModel.deviceLinkState.collectAsState()
            val accountState by viewModel.accountState.collectAsState()
            val accountBusy by viewModel.accountBusy.collectAsState()
            val accountMessage by viewModel.accountMessage.collectAsState()
            val syncStatus by viewModel.syncStatus.collectAsState()
            AccountUi(
                state = accountState,
                busy = accountBusy,
                message = accountMessage,
                onSignIn = { e, p -> viewModel.accountSignIn(e, p) },
                onSignUp = { e, p -> viewModel.accountSignUp(e, p) },
                onResend = { e -> viewModel.accountResendConfirmation(e) },
                onSignOut = viewModel::accountSignOut,
                onDelete = { viewModel.accountDelete() },
                sync = syncStatus,
                onSyncNow = { viewModel.syncNow() },
                deviceLink = deviceLinkState,
                deviceLinkUrl = { viewModel.deviceLinkUrl },
                onStartDeviceLink = { name -> viewModel.startDeviceLink(name) },
                onStopDeviceLink = { viewModel.stopDeviceLink() },
                onRefreshDeviceLink = { name -> viewModel.refreshDeviceLink(name) },
                canApproveLink = viewModel.canApproveDeviceLink(),
                httpClient = okHttpClient,
                onApproveDeviceLink = { req, code6 ->
                    viewModel.approveDeviceLink(req, code6, okHttpClient)
                }
            )
        }

        val content: @Composable () -> Unit = {
            AnimatedContent(
                targetState = Pair(showingDetail, currentTab),
                transitionSpec = {
                    fadeIn(tween(150, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(tween(100, easing = FastOutSlowInEasing)))
                },
                label = "mainScreenTransition"
            ) { (isDetail, tab) ->
                if (isDetail) {
                    val detailState by viewModel.detailState.collectAsState()
                    val detailItemId = detailState.detail?.item?.id.orEmpty()
                    val isFav by viewModel.repository.observeIsFavorite(detailItemId).collectAsState(initial = false)
                    DetailScreen(
                        state = detailState,
                        isFavorite = isFav,
                        isTv = isTv,
                        onBack = {
                            viewModel.cancelBestSourceSearch()
                            showingDetail = false
                        },
                        onToggleFavorite = { item -> viewModel.toggleFavorite(item, isFav) },
                        onSelectSeason = { viewModel.selectSeason(it) },
                        onSelectEpisode = { viewModel.selectEpisode(it) },
                        onPlayStream = { viewModel.playStream(it) },
                        onPlayBestSource = { startOver, onNoPlayable ->
                            viewModel.playBestSource(startOver = startOver, onNoPlayableSource = onNoPlayable)
                        },
                        onDebugTestFailover = if (com.quern.app.BuildConfig.DEBUG) {
                            val fixtureId = context.resources.getIdentifier("debug_failover_url", "string", context.packageName)
                            if (fixtureId == 0) null else {
                                { onNoPlayable ->
                                    viewModel.debugPlayWithBrokenFirstSource(context.getString(fixtureId), onNoPlayable)
                                }
                            }
                        } else {
                            null
                        }
                    )
                } else {
                    when (tab) {
                        QuernTab.HOME -> {
                            val homeRows by viewModel.homeRows.collectAsState()
                            val isLoadingHome by viewModel.isLoadingHome.collectAsState()
                            val continueWatching by viewModel.continueWatching.collectAsState()
                            val companionSession by viewModel.companionSession.collectAsState()
                            val debridStatuses by viewModel.debridStatuses.collectAsState()
                            val activeBadge = remember(debridStatuses) {
                                debridStatuses.entries.firstOrNull { it.value.isConnected }?.key?.displayName
                            }
                            val homeAccount by viewModel.accountState.collectAsState()
                            val homeSync by viewModel.syncStatus.collectAsState()
                            val waitingForFirstPull = homeAccount.isSignedIn &&
                                homeSync.firstPullAttemptedFor != homeAccount.userId
                            val homeAddons by viewModel.addons.collectAsState()
                            val showEmptyTvQr = isTvHardware(context) &&
                                currentTab == QuernTab.HOME && !showingDetail && homeAddons.isEmpty()
                            HomeScreen(
                                rows = homeRows,
                                continueWatching = continueWatching,
                                isLoading = isLoadingHome || waitingForFirstPull,
                                isTv = isTv,
                                onItemClick = openItemDetail,
                                onContinueClick = { progress ->
                                    val resumedDirect = settings.continueWatchingAction == ContinueWatchingAction.RESUME_DIRECT &&
                                        viewModel.resumeWatchProgress(progress)
                                    if (!resumedDirect) {
                                        openItemDetail(
                                            MediaItem(
                                                id = progress.contentId.substringBefore(":"),
                                                title = progress.title,
                                                type = progress.type,
                                                posterUrl = progress.posterUrl,
                                                backdropUrl = progress.backdropUrl
                                            )
                                        )
                                    }
                                },
                                onResumeDirect = { progress ->
                                    if (!viewModel.resumeWatchProgress(progress)) {
                                        openItemDetail(
                                            MediaItem(
                                                id = progress.contentId.substringBefore(":"),
                                                title = progress.title,
                                                type = progress.type,
                                                posterUrl = progress.posterUrl,
                                                backdropUrl = progress.backdropUrl
                                            )
                                        )
                                    }
                                },
                                onResolveHeroMeta = { item -> viewModel.resolveHeroMeta(item) },
                                onOpenAddons = {
                                    settingsInitialGroup = SettingsGroup.ADDONS
                                    currentTab = QuernTab.SETTINGS
                                },
                                onOpenServers = {
                                    settingsInitialGroup = SettingsGroup.SERVICES
                                    currentTab = QuernTab.SETTINGS
                                },
                                onRefresh = { viewModel.refreshHome() },
                                emptyTvQr = if (showEmptyTvQr) {
                                    {
                                        val state by viewModel.accountState.collectAsState()
                                        HomeEmptyTvQr(
                                            signedIn = state.isSignedIn,
                                            signInQr = { refreshRequester ->
                                                TvDeviceLinkQrPanel(
                                                    account = accountUi(),
                                                    titleRes = R.string.home_qr_sign_in,
                                                    stepsRes = R.string.home_qr_sign_in_steps,
                                                    refreshModifier = Modifier.focusRequester(refreshRequester)
                                                )
                                            },
                                            addonQr = {
                                                QuernCompanionSignInCard(
                                                    url = viewModel.addonsCompanionUrl,
                                                    session = companionSession,
                                                    onRefreshSession = viewModel::refreshCompanionSession,
                                                    onBindServer = viewModel::acquireCompanionServer,
                                                    onUnbindServer = viewModel::releaseCompanionServer,
                                                    titleRes = R.string.home_qr_add_addons
                                                )
                                            },
                                            onOpenAddons = {
                                                settingsInitialGroup = SettingsGroup.ADDONS
                                                currentTab = QuernTab.SETTINGS
                                            }
                                        )
                                    }
                                } else null,
                                activeServiceBadge = activeBadge,
                                showHeroBanner = settings.showHeroBanner,
                                showPosterLabels = settings.showPosterLabels,
                                hideUnreleasedContent = settings.hideUnreleasedContent,
                                rowOrder = settings.rowOrder,
                                showContinueWatching = settings.showContinueWatching,
                                continueWatchingSort = settings.continueWatchingSort,
                                cardSize = settings.cardSize,
                                cardShape = settings.cardShape,
                                cardCorner = settings.cardCorner,
                                updateBannerRelease = if (updateState.showHomeBanner) updateState.available else null,
                                onOpenUpdate = {
                                    settingsInitialGroup = SettingsGroup.SYSTEM
                                    currentTab = QuernTab.SETTINGS
                                },
                                onDismissUpdate = { viewModel.dismissUpdateBanner() }
                            )
                        }

                        QuernTab.SEARCH -> {
                            val searchQuery by viewModel.searchQuery.collectAsState()
                            val searchResults by viewModel.searchResults.collectAsState()
                            val isSearching by viewModel.isSearching.collectAsState()
                            SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                isSearching = isSearching,
                                isTv = isTv,
                                cardSize = settings.cardSize,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onSearchSubmit = { viewModel.performSearch() },
                                onItemClick = openItemDetail
                            )
                        }

                        QuernTab.LIBRARY -> {
                            val favorites by viewModel.favorites.collectAsState()
                            val cloudItems by viewModel.cloudItems.collectAsState()
                            val isLoadingCloud by viewModel.isLoadingCloud.collectAsState()
                            val mediaServers by viewModel.mediaServers.collectAsState()
                            val webDavEntries by viewModel.webDavEntries.collectAsState()
                            LibraryScreen(
                                favorites = favorites,
                                cloudItems = cloudItems,
                                isLoadingCloud = isLoadingCloud,
                                servers = mediaServers,
                                webDavEntries = webDavEntries,
                                isTv = isTv,
                                cardSize = settings.cardSize,
                                onItemClick = openItemDetail,
                                onRefreshCloud = { viewModel.refreshCloudLibrary() },
                                onPlayCloudItem = { viewModel.playCloudItem(it) },
                                onBrowseWebDav = { srvId, path -> viewModel.browseWebDav(srvId, path) },
                                onPlayWebDavEntry = { srv, entry -> viewModel.playWebDavEntry(srv, entry) }
                            )
                        }

                        QuernTab.SETTINGS -> {
                            val companionSession by viewModel.companionSession.collectAsState()
                            val addons by viewModel.addons.collectAsState()
                            val debridStatuses by viewModel.debridStatuses.collectAsState()
                            val mediaServers by viewModel.mediaServers.collectAsState()
                            val tmdbConfig by viewModel.tmdbConfig.collectAsState()
                            val traktState by viewModel.traktState.collectAsState()
                            val traktDeviceCode by viewModel.traktDeviceCode.collectAsState()
                            val addonHealthMap by viewModel.addonHealthMap.collectAsState()
                            val checkingHealthUrls by viewModel.checkingHealthUrls.collectAsState()
                            val isRefreshingAddons by viewModel.isRefreshingAddons.collectAsState()
                            val quernRes = LocalQuernResources.current ?: context.resources
                            SettingsScreen(
                                settings = settings,
                                pinMigrationState = pinMigrationState,
                                onRetryPinMigration = { viewModel.retryPinMigrationIfNeeded() },
                                companionUrl = viewModel.companionUrl,
                                addonsQrUrl = viewModel.addonsCompanionUrl,
                                companionSession = companionSession,
                                onRefreshCompanionSession = { viewModel.refreshCompanionSession() },
                                onBindCompanionServer = { viewModel.acquireCompanionServer() },
                                onUnbindCompanionServer = { viewModel.releaseCompanionServer() },
                                account = accountUi(),
                                addons = addons,
                                debridStatuses = debridStatuses,
                                getDebridKey = { viewModel.repository.debridManager.getApiKey(it) },
                                mediaServers = mediaServers,
                                tmdbConfig = tmdbConfig,
                                traktState = traktState,
                                traktDeviceCode = traktDeviceCode,
                                isTv = isTv,
                                initialGroup = settingsInitialGroup,
                                addonHealthMap = addonHealthMap,
                                checkingHealthUrls = checkingHealthUrls,
                                isRefreshingAddons = isRefreshingAddons,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onApplyAppIcon = { iconOpt ->
                                    runCatching { AppIconManager.applyIcon(context, iconOpt) }
                                    viewModel.showToast("App icon updated!")
                                },
                                onSyncStremio = { email, pass -> viewModel.syncFromStremio(email, pass) },
                                onImportNuvio = { raw -> viewModel.importFromNuvio(raw) },
                                onInstallAddon = { url, done -> viewModel.installAddon(url) { done() } },
                                onToggleAddon = { url, enabled -> viewModel.toggleAddonEnabled(url, enabled) },
                                onMoveAddonUp = { url -> viewModel.moveAddonUp(url) },
                                onMoveAddonDown = { url -> viewModel.moveAddonDown(url) },
                                onRemoveAddon = { url -> viewModel.removeAddon(url) },
                                onCheckAddonHealth = { url -> viewModel.checkAddonHealth(url) },
                                onCheckAllAddonsHealth = { viewModel.checkAllAddonsHealth() },
                                onRefreshAddons = {
                                    viewModel.refreshInstalledAddons(
                                        formatToast = { ok, total ->
                                            quernRes.getString(R.string.addons_refreshed_toast, ok, total)
                                        },
                                        emptyToast = quernRes.getString(R.string.addons_refresh_empty_toast)
                                    )
                                },
                                onToggleCatalogVisibility = { key -> viewModel.toggleCatalogVisibility(key) },
                                onRefreshHome = { viewModel.refreshHome() },
                                onShowToast = { viewModel.showToast(it) },
                                onApplyPreset = { preset, label ->
                                    viewModel.applyDevicePreset(preset)
                                    viewModel.showToast(label)
                                },
                                onExportSettingsCode = { viewModel.exportSettingsCode() },
                                onExportSettingsJson = { viewModel.exportSettingsJson() },
                                onImportSettingsCode = { viewModel.importSettingsCode(it) },
                                onSaveDebridKey = { type, key -> viewModel.saveDebridKey(type, key) },
                                onSaveMediaServer = { cfg, cb -> viewModel.saveMediaServer(cfg, cb) },
                                onDeleteMediaServer = { viewModel.deleteMediaServer(it) },
                                onSaveTmdb = { key, lang -> viewModel.saveTmdbConfig(key, lang) },
                                onStartTraktAuth = { id, sec -> viewModel.startTraktDeviceAuth(id, sec) },
                                onConfirmTraktAuth = { id, sec -> viewModel.confirmTraktDeviceAuth(id, sec) },
                                onDisconnectTrakt = { viewModel.disconnectTrakt() },
                                onClearCache = {
                                    context.imageLoader.memoryCache?.clear()
                                    context.imageLoader.diskCache?.clear()
                                    viewModel.showToast("Cache cleared!")
                                },
                                updateState = updateState,
                                onCheckForUpdates = { viewModel.checkForUpdatesManual() },
                                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallUpdate(context) },
                                onDismissUpdate = { viewModel.dismissUpdateBanner() },
                                onOpenUnknownSourcesSettings = { viewModel.openUnknownSourcesSettings(context) },
                                onLoadCurrentVersionNotes = { viewModel.loadCurrentVersionNotes() },
                                onClearCurrentVersionNotes = { viewModel.clearCurrentVersionNotesState() },
                                onDebugSimulatePostUpdate = if (com.quern.app.BuildConfig.DEBUG) {
                                    { viewModel.debugSimulatePostUpdate() }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // One soft, blurred colour background behind every page and theme; pages are transparent on top.
        val glowColors = remember(palette.isGlossy, palette.accent, palette.ambientGlow, palette.surfaceElevated) {
            if (palette.isGlossy) {
                listOf(Color(0xFF1F2B3D), Color(0xFF2B384C), Color(0xFF141C28), Color(0xFF222D3F))
            } else {
                listOf(palette.ambientGlow, palette.accent, palette.surfaceElevated, palette.ambientGlow)
            }
        }
        SoftGlowBackground(
            baseColor = palette.background,
            glowColors = glowColors,
            intensity = if (palette.isGlossy) 0.78f else 0.86f
        )

        if (isTv) {
            val contentFocusMemory = remember { ContentFocusMemory() }
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                when {
                    settings.navigationStyle == NavigationStyle.TOP_BAR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (!showingDetail) {
                                QuernTvTopNavBar(
                                    currentTab = currentTab,
                                    hasSettingsUpdateDot = updateState.showSettingsDot,
                                    onSelectTab = { currentTab = it }
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                CompositionLocalProvider(LocalTvRailInset provides 0.dp) {
                                    content()
                                }
                            }
                        }
                    }

                    else -> {
                        // Unified Integrated Sidebar (both Auto-Hide and Always-Expanded):
                        // Content fills 100% edge-to-edge so hero backdrops and blurred glows flow seamlessly
                        // right behind the floating navigation rail — zero separate panel, zero hard vertical cut.
                        val autoHide = settings.navigationStyle == NavigationStyle.SIDEBAR_COLLAPSED
                        var collapsedRailFocused by remember { mutableStateOf(false) }
                        val scrimAlphaState = animateFloatAsState(
                            targetValue = if (autoHide && collapsedRailFocused && !showingDetail) 1f else 0f,
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            label = "railBackdropScrim"
                        )
                        val railInset = when {
                            showingDetail -> 0.dp
                            autoHide -> 66.dp
                            else -> 152.dp
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Focus: RIGHT from the rail is a normal geometric search, and Compose only
                                    // considers this group if its bounds lie to the right of the rail item. So the
                                    // group starts at railInset (not x=0); entering it then restores the last item.
                                    // Don't point the rail's `right` at a FocusRequester instead: on a fresh launch
                                    // nothing is remembered, the request fails and focus stays stuck in the rail.
                                    .padding(start = railInset)
                                    .restoreLastContentFocus(contentFocusMemory)
                                    // Drawing: shift the content back toward the start edge so it fills
                                    // the screen edge-to-edge behind the floating rail in both LTR and RTL.
                                    .layout { measurable, constraints ->
                                        val insetPx = railInset.roundToPx()
                                        val fullWidth = constraints.maxWidth + insetPx
                                        val placeable = measurable.measure(
                                            constraints.copy(minWidth = fullWidth, maxWidth = fullWidth)
                                        )
                                        layout(constraints.maxWidth, placeable.height) {
                                            placeable.placeRelative(-insetPx, 0)
                                        }
                                    }
                            ) {
                                CompositionLocalProvider(
                                    LocalContentFocusMemory provides contentFocusMemory,
                                    LocalTvRailInset provides railInset
                                ) {
                                    content()
                                }
                            }

                            // Smooth cinema start-fade scrim when auto-hide rail expands so rail labels never clash with page text
                            if (autoHide && !showingDetail) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawBehind {
                                            val a = scrimAlphaState.value
                                            if (a > 0.01f) {
                                                val isRtl = layoutDirection == LayoutDirection.Rtl
                                                drawRect(
                                                    Brush.horizontalGradient(
                                                        colorStops = arrayOf(
                                                            0.0f to palette.background.copy(alpha = 0.95f * a),
                                                            0.22f to palette.background.copy(alpha = 0.88f * a),
                                                            0.42f to palette.background.copy(alpha = 0.36f * a),
                                                            0.66f to Color.Transparent,
                                                            1.0f to Color.Transparent
                                                        ),
                                                        startX = if (isRtl) size.width else 0f,
                                                        endX = if (isRtl) 0f else size.width
                                                    )
                                                )
                                            }
                                        }
                                )
                            }

                            if (!showingDetail) {
                                QuernTvNavigationRail(
                                    currentTab = currentTab,
                                    autoHideCollapsed = autoHide,
                                    activeProfile = settings.activeProfile,
                                    onOpenProfiles = {
                                        profilePickerOpen = true
                                        profilePickerShownThisLaunch = true
                                    },
                                    hasSettingsUpdateDot = updateState.showSettingsDot,
                                    focusSyncSignal = railFocusSyncSignal,
                                    onRailFocusChanged = { collapsedRailFocused = it },
                                    onExitToContent = { contentFocusMemory.restore() },
                                    onSelectTab = { currentTab = it }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.statusBars,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content()
                    if (!showingDetail) {
                        QuernFloatingPhoneDock(
                            currentTab = currentTab,
                            hasSettingsUpdateDot = updateState.showSettingsDot,
                            onSelectTab = { currentTab = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }

        val isHomeVisible = !profilePickerOpen &&
            activePlayback == null &&
            !showingDetail &&
            currentTab == QuernTab.HOME &&
            pendingDeviceLink == null
        var homeShownForPostUpdate by remember { mutableStateOf(false) }
        LaunchedEffect(isHomeVisible) {
            if (isHomeVisible) {
                withFrameNanos { }
                delay(250L)
                homeShownForPostUpdate = true
            } else {
                homeShownForPostUpdate = false
            }
        }
        if (isHomeVisible && homeShownForPostUpdate) {
            val postUpdate = updateState.pendingPostUpdate
            if (postUpdate != null) {
                val isAr = LocalLayoutDirection.current == LayoutDirection.Rtl
                val sections = remember(postUpdate, isAr) {
                    parseReleaseEntries(postUpdate.entries, isAr)
                }
                if (sections.isEmpty()) {
                    LaunchedEffect(postUpdate) { viewModel.consumePostUpdateNotes() }
                } else {
                    WhatsNewDialog(
                        version = postUpdate.version,
                        sections = sections,
                        isTv = isTv,
                        primaryLabel = qString(R.string.common_ok),
                        secondaryLabel = null,
                        onPrimary = { viewModel.consumePostUpdateNotes() },
                        onDismiss = { viewModel.consumePostUpdateNotes() }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun tabLabel(tab: QuernTab): String {
    val s = LocalQuernStrings.current
    return when (tab) {
        QuernTab.HOME -> s.navHome
        QuernTab.SEARCH -> s.navSearch
        QuernTab.LIBRARY -> s.navLibrary
        QuernTab.SETTINGS -> s.navSettings
    }
}

@Composable
fun QuernTvTopNavBar(
    currentTab: QuernTab,
    hasSettingsUpdateDot: Boolean = false,
    onSelectTab: (QuernTab) -> Unit
) {
    val palette = LocalQuernPalette.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuernBrandMark(size = 20.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "QUERN",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp
            )
        }

        Surface(
            color = palette.glassSurface,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.glossySheen(palette.isGlossy, cornerRadius = 999.dp, intensity = 0.9f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuernTab.entries.forEach { tab ->
                    var focused by remember { mutableStateOf(false) }
                    val selected = tab == currentTab
                    val label = tabLabel(tab)

                    val scale by animateFloatAsState(
                        targetValue = if (focused) 1.03f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "topTabScale"
                    )
                    val bg by animateColorAsState(
                        targetValue = when {
                            focused -> palette.accent
                            selected -> palette.surfaceElevated
                            else -> Color.Transparent
                        },
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "topTabBg"
                    )
                    val fg = when {
                        focused -> palette.onAccent
                        selected -> palette.focusRing
                        else -> LumaTextSecondary
                    }

                    Row(
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(if (tab == QuernTab.HOME) Modifier.focusRequester(firstFocus) else Modifier)
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) palette.focusRing else Color.Transparent,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .tvClickable(
                                onFocusChangedState = { focused = it },
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Icon(tab.icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
                            if (tab == QuernTab.SETTINGS && hasSettingsUpdateDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(LumaGreen)
                                )
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = label,
                            color = if (focused) palette.onAccent else if (selected) LumaTextPrimary else LumaTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuernFloatingPhoneDock(
    currentTab: QuernTab,
    hasSettingsUpdateDot: Boolean = false,
    onSelectTab: (QuernTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    Surface(
        color = palette.glassSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 16.dp,
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .glossySheen(palette.isGlossy, cornerRadius = 999.dp, intensity = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuernTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val label = tabLabel(tab)
                val bg by animateColorAsState(
                    targetValue = if (selected) palette.accent else Color.Transparent,
                    animationSpec = tween(150),
                    label = "dockBg"
                )
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(bg)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = label,
                                tint = if (selected) palette.onAccent else LumaTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (tab == QuernTab.SETTINGS && hasSettingsUpdateDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(LumaGreen)
                                )
                            }
                        }
                        if (selected) {
                            Text(
                                text = label,
                                color = palette.onAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * When focus enters the content area from outside (DPAD_RIGHT from the rail), put it back on
 * the exact item that had it before. Falls back to normal geometric search if that item is gone.
 */
private fun Modifier.restoreLastContentFocus(memory: ContentFocusMemory): Modifier = this
    .focusProperties {
        enter = { if (memory.restore()) FocusRequester.Cancel else FocusRequester.Default }
    }
    .focusGroup()

private val RailItemShape = RoundedCornerShape(14.dp)

@Composable
fun QuernTvNavigationRail(
    currentTab: QuernTab,
    autoHideCollapsed: Boolean = false,
    activeProfile: UserProfile? = null,
    onOpenProfiles: (() -> Unit)? = null,
    hasSettingsUpdateDot: Boolean = false,
    focusSyncSignal: Int = 0,
    onRailFocusChanged: ((Boolean) -> Unit)? = null,
    onExitToContent: (() -> Boolean)? = null,
    onSelectTab: (QuernTab) -> Unit
) {
    val palette = LocalQuernPalette.current
    val toContentKey = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        Key.DirectionLeft
    } else {
        Key.DirectionRight
    }
    // One requester per tab so focus entering the rail lands on the selected tab, not the geometrically nearest one
    val tabFocusRequesters = remember { QuernTab.entries.associateWith { FocusRequester() } }
    val profileFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = tabFocusRequesters.getValue(QuernTab.HOME)
    val latestTab by rememberUpdatedState(currentTab)
    var railHasFocus by remember { mutableStateOf(false) }

    val expanded = !autoHideCollapsed || railHasFocus
    // Keep animated values as State<T> and read them in layout/graphicsLayer/drawBehind phases only
    // so expanding/collapsing or focusing rail items triggers ZERO recompositions per frame.
    val railWidthState = animateDpAsState(
        targetValue = if (expanded) 148.dp else 64.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "railWidth"
    )
    val labelAlphaState = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "railLabelAlpha"
    )

    LaunchedEffect(Unit) {
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    // Keep rail focus aligned with the active tab when switching tabs programmatically (e.g. BACK to Home).
    LaunchedEffect(currentTab) {
        if (railHasFocus) {
            runCatching { tabFocusRequesters.getValue(currentTab).requestFocus() }
        }
    }
    LaunchedEffect(focusSyncSignal) {
        if (focusSyncSignal > 0) {
            delay(50L)
            runCatching { tabFocusRequesters.getValue(latestTab).requestFocus() }
        }
    }

    val hasProfileButton = activeProfile != null && onOpenProfiles != null

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val widthPx = railWidthState.value.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(minWidth = widthPx, maxWidth = widthPx)
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            .fillMaxHeight()
            .clipToBounds()
            // Whisper-soft neutral left edge feather only (zero solid column, zero color seam
            // against the blurred ambient backdrop); expanded readability in auto-hide mode is
            // handled by the wide smooth cinema scrim behind the rail.
            .drawWithCache {
                val isRtl = layoutDirection == LayoutDirection.Rtl
                val veil = Brush.horizontalGradient(
                    colorStops = if (autoHideCollapsed) {
                        arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.22f),
                            0.55f to Color.Black.copy(alpha = 0.06f),
                            1.0f to Color.Transparent
                        )
                    } else {
                        arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.42f),
                            0.62f to Color.Black.copy(alpha = 0.14f),
                            1.0f to Color.Transparent
                        )
                    },
                    startX = if (isRtl) size.width else 0f,
                    endX = if (isRtl) 0f else size.width
                )
                onDrawBehind { drawRect(veil) }
            }
            // Single focusGroup spanning both the tab column and the bottom profile button so LEFT
            // from any content row (even lower rows) always enters on the selected tab, never the profile button.
            // Pressing RIGHT (LEFT in RTL) hands focus directly to ContentFocusMemory (last focused item
            // or Home Hero CTA), falling back to normal geometric search if nothing is registered.
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == toContentKey) {
                    onExitToContent?.invoke() == true
                } else {
                    false
                }
            }
            .focusProperties { enter = { tabFocusRequesters.getValue(latestTab) } }
            .focusGroup()
            .onFocusChanged {
                railHasFocus = it.hasFocus
                onRailFocusChanged?.invoke(it.hasFocus)
            }
            .padding(top = 13.dp, bottom = 16.dp, start = 10.dp, end = 10.dp)
    ) {
        // Always-visible brand mark aligned horizontally with the Hero top bar pills & tab icons below
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 11.dp, top = 0.dp)
        ) {
            QuernBrandMark(size = 22.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                text = "QUERN",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value }
            )
        }

        // Vertically centered squircle icon column with constant icon X-position (zero horizontal jump on expand/collapse)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            val lastTab = QuernTab.entries.last()
            QuernTab.entries.forEach { tab ->
                var focused by remember { mutableStateOf(false) }
                val selected = currentTab == tab
                val label = tabLabel(tab)

                val scaleState = animateFloatAsState(
                    targetValue = if (focused) 1.03f else 1f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "railItemScale"
                )
                val itemBgState = animateColorAsState(
                    targetValue = when {
                        focused -> palette.accent
                        selected && expanded -> palette.accent.copy(alpha = if (palette.isGlossy) 0.45f else 0.24f)
                        selected -> palette.accent
                        else -> Color.Transparent
                    },
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "railItemBg"
                )
                val itemTint = when {
                    focused -> palette.onAccent
                    selected -> if (expanded) palette.focusRing else palette.onAccent
                    else -> Color(0xFF8E94A0)
                }

                // Single persistent Row focus target so expanding/collapsing never disposes the focused node.
                // Fixed start padding (11.dp) + fixed 22.dp icon centers the icon at 44.dp inner width (11+22+11=44).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .graphicsLayer {
                            val s = scaleState.value
                            scaleX = s
                            scaleY = s
                        }
                        .focusRequester(tabFocusRequesters.getValue(tab))
                        .then(
                            if (tab == lastTab && hasProfileButton) {
                                Modifier.focusProperties { down = profileFocusRequester }
                            } else Modifier
                        )
                        .clip(RailItemShape)
                        .drawBehind { drawRect(itemBgState.value) }
                        .glossySheen(palette.isGlossy && (focused || selected), cornerRadius = 14.dp, intensity = if (focused) 1.15f else 0.7f)
                        .then(
                            if (focused) Modifier.border(1.75.dp, palette.focusRing.copy(alpha = 0.9f), RailItemShape)
                            else Modifier
                        )
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { onSelectTab(tab) }
                        )
                        .padding(start = 11.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = itemTint,
                            modifier = Modifier.size(22.dp)
                        )
                        if (tab == QuernTab.SETTINGS && hasSettingsUpdateDot) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(LumaGreen)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (focused) palette.onAccent else if (selected) Color.White else Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value }
                    )
                }
            }
        }

        // Active profile, pinned bottom-left: reached with DOWN from the last tab; one press opens "Who's watching?".
        if (activeProfile != null && onOpenProfiles != null) {
            var profileFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRequester(profileFocusRequester)
                    .focusProperties { up = tabFocusRequesters.getValue(QuernTab.entries.last()) }
                    .clip(RailItemShape)
                    .drawBehind { if (profileFocused) drawRect(palette.accent) }
                    .then(
                        if (profileFocused) Modifier.border(1.75.dp, palette.focusRing.copy(alpha = 0.9f), RailItemShape)
                        else Modifier
                    )
                    .tvClickable(
                        onFocusChangedState = { profileFocused = it },
                        onClick = onOpenProfiles
                    )
                    .padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatarCircle(activeProfile, size = 28.dp, fontSizeSp = 11f)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = activeProfile.name,
                    color = if (profileFocused) palette.onAccent else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer { alpha = labelAlphaState.value }
                )
            }
        }
    }
}
