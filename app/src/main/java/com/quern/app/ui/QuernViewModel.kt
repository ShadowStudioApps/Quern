package com.quern.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quern.app.data.QuernRepository
import com.quern.app.data.addons.AddonHealthResult
import com.quern.app.data.companion.CompanionSessionState
import com.quern.app.data.companion.TvWebCompanionServer
import com.quern.app.data.local.PinMigrationState
import com.quern.app.data.local.SettingsStore
import com.quern.app.data.servers.ServerTestResult
import com.quern.app.data.servers.WebDavEntry
import com.quern.app.data.sync.CloudAccountState
import com.quern.app.data.sync.CloudSyncManager
import com.quern.app.data.sync.AccountError
import com.quern.app.data.sync.AccountException
import com.quern.app.data.sync.AccountUiMessage
import com.quern.app.data.sync.QuernAccount
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.data.sync.QuernSyncEngine
import com.quern.app.data.sync.SyncStatus
import com.quern.app.domain.model.AppSettings
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.QuernDevicePreset
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.SubtitleTrack
import com.quern.app.domain.model.TmdbConfig
import com.quern.app.domain.model.TraktAuthState
import com.quern.app.domain.model.TraktDeviceCode
import com.quern.app.domain.model.WatchProgress
import com.quern.app.player.PlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = false,
    val detail: MediaDetail? = null,
    val selectedSeason: Int = 1,
    val selectedEpisode: Episode? = null,
    val isLoadingStreams: Boolean = false,
    val streams: List<StreamSource> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val resolvingStreamId: String? = null,
    val statusMessage: String? = null,
    val savedPositionMs: Long = 0L,
    val savedDurationMs: Long = 0L
)

class QuernViewModel(
    val repository: QuernRepository,
    val settingsStore: SettingsStore,
    val cloudSyncManager: CloudSyncManager,
    val quernAccount: QuernAccount,
    val syncEngine: QuernSyncEngine
) : ViewModel() {

    // ---- Account sync ----
    val syncStatus: StateFlow<SyncStatus> = syncEngine.status

    fun syncNow() {
        viewModelScope.launch { syncEngine.syncNow() }
    }

    init {
        // Addons installed or removed on another device / the website: reload the Home rows.
        viewModelScope.launch { syncEngine.addonsApplied.collect { refreshHome() } }
    }

    val appSettings: StateFlow<AppSettings> = settingsStore.settings
    val pinMigrationState: StateFlow<PinMigrationState> = settingsStore.pinMigrationState
    val cloudAccountState: StateFlow<CloudAccountState> = cloudSyncManager.accountState

    // ---- Quern account (Shadow Studio server) ----
    val accountState: StateFlow<QuernAccountState> = quernAccount.state
    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy.asStateFlow()
    private val _accountMessage = MutableStateFlow<AccountUiMessage?>(null)
    val accountMessage: StateFlow<AccountUiMessage?> = _accountMessage.asStateFlow()

    fun clearAccountMessage() {
        _accountMessage.value = null
    }

    private fun runAccountAction(onSuccess: AccountUiMessage, action: suspend () -> Result<Unit>) {
        if (_accountBusy.value) return
        viewModelScope.launch {
            _accountBusy.value = true
            _accountMessage.value = null
            val result = action()
            _accountMessage.value = result.fold(
                onSuccess = { onSuccess },
                onFailure = { AccountUiMessage.Failed((it as? AccountException)?.reason ?: AccountError.SERVER) }
            )
            _accountBusy.value = false
        }
    }

    fun accountSignIn(email: String, password: String) =
        runAccountAction(AccountUiMessage.SignedIn) { quernAccount.signIn(email, password) }

    fun accountSignUp(email: String, password: String) =
        runAccountAction(AccountUiMessage.SignedUp) { quernAccount.signUp(email, password) }

    fun accountResendConfirmation(email: String) =
        runAccountAction(AccountUiMessage.ConfirmationResent) { quernAccount.resendConfirmation(email) }

    fun accountSignOut() =
        runAccountAction(AccountUiMessage.SignedOut) { quernAccount.signOut(); Result.success(Unit) }

    fun accountDelete() =
        runAccountAction(AccountUiMessage.Deleted) { quernAccount.deleteAccount() }

    fun retryPinMigrationIfNeeded() {
        settingsStore.retryPinMigrationIfNeeded()
    }

    private val companionServer = TvWebCompanionServer(repository, settingsStore, cloudSyncManager, quernAccount) {
        refreshHome()
        viewModelScope.launch {
            repository.debridManager.refreshAllStatuses()
            _tmdbConfig.value = repository.tmdbClient.getConfig()
        }
    }

    val companionUrl: String
        get() = companionServer.getLocalUrl()

    val addonsCompanionUrl: String
        get() = companionServer.getLocalUrl("addons")

    val companionSession: StateFlow<CompanionSessionState> = companionServer.sessionState

    private var companionHolders = 0

    private var companionStopJob: Job? = null

    @Synchronized
    fun acquireCompanionServer() {
        companionStopJob?.cancel()
        companionStopJob = null
        companionHolders++
        if (companionHolders == 1) {
            companionServer.start(viewModelScope)
        }
    }

    /**
     * When no TV screen shows the QR any more, keep the phone page alive while a paired phone
     * is still using it, and stop the server once it has been idle for the session window.
     */
    @Synchronized
    fun releaseCompanionServer() {
        companionHolders = (companionHolders - 1).coerceAtLeast(0)
        if (companionHolders != 0) return
        companionStopJob?.cancel()
        companionStopJob = viewModelScope.launch {
            while (companionHolders == 0 && companionServer.hasActivePhone()) {
                delay(30_000L)
            }
            if (companionHolders == 0) companionServer.stop()
        }
    }

    fun refreshCompanionSession() {
        companionServer.refreshSession()
    }

    val addons: StateFlow<List<InstalledAddon>> = repository.addonRepository.addons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<WatchProgress>> = repository.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MediaItem>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaServers: StateFlow<List<MediaServerConfig>> = repository.serverManager.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debridStatuses: StateFlow<Map<DebridServiceType, DebridAccountStatus>> =
        repository.debridManager.statuses

    private val _homeRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val homeRows: StateFlow<List<CatalogRow>> = _homeRows.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val activePlayback: StateFlow<PlaybackRequest?> = _activePlayback.asStateFlow()

    private val _cloudItems = MutableStateFlow<List<DebridCloudItem>>(emptyList())
    val cloudItems: StateFlow<List<DebridCloudItem>> = _cloudItems.asStateFlow()

    private val _isLoadingCloud = MutableStateFlow(false)
    val isLoadingCloud: StateFlow<Boolean> = _isLoadingCloud.asStateFlow()

    private val _webDavEntries = MutableStateFlow<List<WebDavEntry>>(emptyList())
    val webDavEntries: StateFlow<List<WebDavEntry>> = _webDavEntries.asStateFlow()

    private val _tmdbConfig = MutableStateFlow(repository.tmdbClient.getConfig())
    val tmdbConfig: StateFlow<TmdbConfig> = _tmdbConfig.asStateFlow()

    private val _traktState = MutableStateFlow(repository.traktClient.getState())
    val traktState: StateFlow<TraktAuthState> = _traktState.asStateFlow()

    private val _traktDeviceCode = MutableStateFlow<TraktDeviceCode?>(null)
    val traktDeviceCode: StateFlow<TraktDeviceCode?> = _traktDeviceCode.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _addonHealthMap = MutableStateFlow<Map<String, AddonHealthResult>>(emptyMap())
    val addonHealthMap: StateFlow<Map<String, AddonHealthResult>> = _addonHealthMap.asStateFlow()

    private val _checkingHealthUrls = MutableStateFlow<Set<String>>(emptySet())
    val checkingHealthUrls: StateFlow<Set<String>> = _checkingHealthUrls.asStateFlow()

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var streamsJob: Job? = null

    private var lastScrobbleContentId: String? = null
    private var lastScrobbleAction: String? = null

    init {
        refreshHome()
        viewModelScope.launch {
            repository.debridManager.refreshAllStatuses()
        }
    }

    override fun onCleared() {
        companionServer.stop()
        super.onCleared()
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settingsStore.settings.value.profiles.map { it.id }.toSet()
        settingsStore.update(transform)
        val after = settingsStore.settings.value.profiles.map { it.id }.toSet()
        // A deleted profile takes its own watch history and favorites with it.
        val removed = before - after
        if (removed.isNotEmpty()) {
            viewModelScope.launch { removed.forEach { repository.deleteProfileData(it) } }
        }
    }

    fun applyDevicePreset(preset: QuernDevicePreset) {
        settingsStore.applyPreset(preset)
    }

    fun exportSettingsCode(): String = settingsStore.exportNonSecretSettingsCode()

    fun exportSettingsJson(): String = settingsStore.exportNonSecretSettingsPrettyJson()

    fun importSettingsCode(rawCode: String) {
        settingsStore.importNonSecretSettingsCode(rawCode)
            .onSuccess {
                _toastMessage.value = it
                refreshHome()
            }
            .onFailure {
                _toastMessage.value = it.message ?: "Invalid settings code"
            }
    }

    fun refreshHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _isLoadingHome.value = true
            _homeRows.value = runCatching {
                repository.loadHomeCatalogs(appSettings.value.hiddenCatalogKeys)
            }.getOrDefault(emptyList())
            _isLoadingHome.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch() {
        val q = _searchQuery.value.trim()
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = runCatching { repository.searchEverywhere(q) }.getOrDefault(emptyList())
            _isSearching.value = false
        }
    }

    fun openDetail(item: MediaItem) {
        detailJob?.cancel()
        streamsJob?.cancel()
        detailJob = viewModelScope.launch {
            val initialProgress = runCatching { repository.getSavedWatchProgress(item.id) }.getOrNull()
            _detailState.value = DetailUiState(
                isLoading = true,
                detail = MediaDetail(item),
                isLoadingStreams = true,
                savedPositionMs = initialProgress?.positionMs ?: 0L,
                savedDurationMs = initialProgress?.durationMs ?: 0L
            )

            // For movies whose ID is already known, kick off stream loading immediately in parallel with metadata
            val canPreloadMovieStreams = item.type == MediaType.MOVIE && (
                !item.imdbId.isNullOrBlank() ||
                    item.id.startsWith("tt") ||
                    !item.sourceId.isNullOrBlank()
                )
            if (canPreloadMovieStreams) {
                streamsJob = viewModelScope.launch {
                    fetchStreamsForCurrentDetail(MediaDetail(item), null)
                }
            }

            val detail = runCatching { repository.loadDetail(item) }.getOrDefault(MediaDetail(item))
            val historyList = continueWatching.value
            val resumedEpisode = if (detail.item.type == MediaType.SERIES) {
                // If the user previously watched an episode of this series, pre-select that episode for Resume!
                val seriesPrefix = detail.item.imdbId ?: detail.item.id
                val matchingHistory = historyList.firstOrNull { hp ->
                    hp.episodeId != null && (
                        hp.contentId == detail.item.id ||
                            hp.contentId.startsWith("$seriesPrefix:") ||
                            hp.episodeId.startsWith("$seriesPrefix:")
                        )
                }
                matchingHistory?.episodeId?.let { epId ->
                    detail.episodes.firstOrNull { it.id == epId }
                }
            } else null

            val selectedSeason = resumedEpisode?.seasonNumber ?: detail.seasons.firstOrNull() ?: 1
            val selectedEpisode = resumedEpisode ?: if (detail.item.type == MediaType.SERIES) {
                detail.episodesForSeason(selectedSeason).firstOrNull()
            } else {
                null
            }
            val contentKey = selectedEpisode?.id ?: detail.item.id
            val saved = runCatching { repository.getSavedWatchProgress(contentKey) }.getOrNull()
                ?: if (detail.item.imdbId != null && detail.item.imdbId != contentKey) {
                    runCatching { repository.getSavedWatchProgress(detail.item.imdbId) }.getOrNull()
                } else null

            val currentStreams = _detailState.value.streams
            val currentSubs = _detailState.value.subtitles
            _detailState.value = _detailState.value.copy(
                isLoading = false,
                detail = detail,
                selectedSeason = selectedSeason,
                selectedEpisode = selectedEpisode,
                savedPositionMs = saved?.positionMs ?: 0L,
                savedDurationMs = saved?.durationMs ?: 0L
            )

            val needStreamReload = !canPreloadMovieStreams ||
                selectedEpisode != null ||
                (detail.item.imdbId != null && detail.item.imdbId != item.imdbId && currentStreams.isEmpty())
            if (needStreamReload) {
                streamsJob?.cancel()
                _detailState.value = _detailState.value.copy(
                    isLoadingStreams = true,
                    streams = if (selectedEpisode != null) emptyList() else currentStreams,
                    subtitles = if (selectedEpisode != null) emptyList() else currentSubs
                )
                streamsJob = viewModelScope.launch {
                    fetchStreamsForCurrentDetail(detail, selectedEpisode)
                }
            }
        }
    }

    fun selectSeason(season: Int) {
        val current = _detailState.value
        val detail = current.detail ?: return
        val ep = detail.episodesForSeason(season).firstOrNull()
        streamsJob?.cancel()
        streamsJob = viewModelScope.launch {
            val saved = ep?.let { runCatching { repository.getSavedWatchProgress(it.id) }.getOrNull() }
            _detailState.value = current.copy(
                selectedSeason = season,
                selectedEpisode = ep,
                isLoadingStreams = true,
                streams = emptyList(),
                savedPositionMs = saved?.positionMs ?: 0L,
                savedDurationMs = saved?.durationMs ?: 0L
            )
            fetchStreamsForCurrentDetail(detail, ep)
        }
    }

    fun selectEpisode(episode: Episode) {
        val current = _detailState.value
        val detail = current.detail ?: return
        streamsJob?.cancel()
        streamsJob = viewModelScope.launch {
            val saved = runCatching { repository.getSavedWatchProgress(episode.id) }.getOrNull()
            _detailState.value = current.copy(
                selectedEpisode = episode,
                isLoadingStreams = true,
                streams = emptyList(),
                savedPositionMs = saved?.positionMs ?: 0L,
                savedDurationMs = saved?.durationMs ?: 0L
            )
            fetchStreamsForCurrentDetail(detail, episode)
        }
    }

    private suspend fun fetchStreamsForCurrentDetail(detail: MediaDetail, episode: Episode?) {
        runCatching {
            repository.loadStreamsAndSubtitlesProgressive(
                detail = detail,
                episodeId = episode?.id,
                onStreamsUpdated = { latestStreams ->
                    _detailState.value = _detailState.value.copy(
                        streams = latestStreams,
                        statusMessage = if (latestStreams.isNotEmpty()) null else _detailState.value.statusMessage
                    )
                },
                onSubtitlesUpdated = { latestSubs ->
                    _detailState.value = _detailState.value.copy(subtitles = latestSubs)
                }
            )
        }
        val finalStreams = _detailState.value.streams
        _detailState.value = _detailState.value.copy(
            isLoadingStreams = false,
            statusMessage = if (finalStreams.isEmpty()) {
                "No streams found yet. Add a stream Addon or link TorBox/Server in Settings → Services."
            } else {
                null
            }
        )
    }

    fun playStream(stream: StreamSource) {
        val state = _detailState.value
        val detail = state.detail ?: return
        val ep = state.selectedEpisode

        viewModelScope.launch {
            val playable = if (stream.isDirectlyPlayable) {
                stream
            } else {
                _detailState.value = _detailState.value.copy(resolvingStreamId = stream.id)
                val resolved = repository.debridManager.resolveStream(stream)
                _detailState.value = _detailState.value.copy(resolvingStreamId = null)
                if (resolved == null) {
                    _toastMessage.value = "Connect TorBox, Real-Debrid, AllDebrid, or Premiumize in Settings → Services."
                    return@launch
                }
                resolved
            }

            val contentKey = ep?.id ?: detail.item.id
            val savedPos = repository.getSavedPositionMs(contentKey)
            val epLabel = ep?.let { "S${it.seasonNumber} E${it.episodeNumber} • ${it.title}" }
            val nextEp = ep?.let { findNextEpisode(detail, it) }
            val nextEpLabel = nextEp?.let { "S${it.seasonNumber} E${it.episodeNumber} • ${it.title}" }

            _activePlayback.value = PlaybackRequest(
                contentId = contentKey,
                title = detail.item.title,
                subtitle = epLabel ?: detail.item.year,
                mediaType = detail.item.type,
                imdbId = detail.item.imdbId,
                posterUrl = detail.item.posterUrl,
                backdropUrl = detail.item.backdropUrl,
                episodeId = ep?.id,
                nextEpisodeTitle = nextEpLabel,
                stream = playable,
                externalSubtitles = state.subtitles,
                startPositionMs = savedPos
            )
        }
    }

    fun playNextEpisode() {
        val state = _detailState.value
        val detail = state.detail ?: return
        val currentEp = state.selectedEpisode ?: return
        val nextEp = findNextEpisode(detail, currentEp) ?: return

        viewModelScope.launch {
            _detailState.value = state.copy(selectedSeason = nextEp.seasonNumber, selectedEpisode = nextEp, isLoadingStreams = true)
            val (streams, subs) = runCatching {
                repository.loadStreamsAndSubtitles(detail, nextEp.id)
            }.getOrDefault(emptyList<StreamSource>() to emptyList())
            _detailState.value = _detailState.value.copy(isLoadingStreams = false, streams = streams, subtitles = subs)
            val best = streams.firstOrNull { it.isDirectlyPlayable || it.isCached } ?: streams.firstOrNull()
            if (best != null) {
                playStream(best)
            } else {
                _activePlayback.value = null
                _toastMessage.value = "No automatic stream found for S${nextEp.seasonNumber} E${nextEp.episodeNumber}."
            }
        }
    }

    private fun findNextEpisode(detail: MediaDetail, current: Episode): Episode? {
        val ordered = detail.episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        val idx = ordered.indexOfFirst { it.id == current.id }
        return if (idx in 0 until ordered.lastIndex) ordered[idx + 1] else null
    }

    fun playCloudItem(item: DebridCloudItem) {
        viewModelScope.launch {
            val url = repository.debridManager.resolveCloudItemUrl(item)
            if (url.isNullOrBlank()) {
                _toastMessage.value = "Could not generate stream link for ${item.name}"
                return@launch
            }
            val origin = when (item.service) {
                DebridServiceType.TORBOX -> SourceOrigin.TORBOX
                DebridServiceType.REAL_DEBRID -> SourceOrigin.REAL_DEBRID
                DebridServiceType.ALL_DEBRID -> SourceOrigin.ALL_DEBRID
                DebridServiceType.PREMIUMIZE -> SourceOrigin.PREMIUMIZE
            }
            _activePlayback.value = PlaybackRequest(
                contentId = "cloud:${item.service.name}:${item.id}",
                title = item.name,
                subtitle = "${item.service.displayName} Cloud",
                mediaType = MediaType.MOVIE,
                stream = StreamSource(
                    id = item.id,
                    title = item.name,
                    url = url,
                    providerName = item.service.displayName,
                    origin = origin,
                    isCached = true
                )
            )
        }
    }

    fun playWebDavEntry(server: MediaServerConfig, entry: WebDavEntry) {
        val headers = repository.serverManager.getWebDavAuthHeaders(server)
        _activePlayback.value = PlaybackRequest(
            contentId = "dav:${server.id}:${entry.fullUrl.hashCode()}",
            title = entry.name,
            subtitle = "${server.name} • WebDAV",
            mediaType = MediaType.MOVIE,
            stream = StreamSource(
                id = entry.fullUrl,
                title = entry.name,
                url = entry.fullUrl,
                headers = headers,
                providerName = server.name,
                origin = SourceOrigin.WEBDAV,
                isCached = true
            )
        )
    }

    fun onPlaybackProgress(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val req = _activePlayback.value ?: return
        val action = if (isPlaying) "start" else "pause"
        val shouldScrobble = durationMs > 0L && !req.imdbId.isNullOrBlank() &&
            (lastScrobbleContentId != req.contentId || lastScrobbleAction != action)
        if (shouldScrobble) {
            lastScrobbleContentId = req.contentId
            lastScrobbleAction = action
        }
        viewModelScope.launch {
            repository.saveWatchProgress(
                WatchProgress(
                    contentId = req.contentId,
                    title = req.title,
                    type = req.mediaType,
                    posterUrl = req.posterUrl,
                    backdropUrl = req.backdropUrl,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    episodeId = req.episodeId,
                    episodeLabel = req.subtitle,
                    lastStreamUrl = req.stream.url
                )
            )
            if (shouldScrobble) {
                val pct = (positionMs.toFloat() / durationMs.toFloat()) * 100f
                repository.traktClient.scrobble(action, req.imdbId, req.mediaType, pct)
            }
        }
    }

    fun onPlaybackStopped(positionMs: Long, durationMs: Long) {
        val req = _activePlayback.value ?: return
        if (durationMs <= 0L || req.imdbId.isNullOrBlank()) return
        if (lastScrobbleContentId == req.contentId && lastScrobbleAction == "stop") return
        lastScrobbleContentId = req.contentId
        lastScrobbleAction = "stop"
        val pct = (positionMs.toFloat() / durationMs.toFloat()) * 100f
        viewModelScope.launch {
            repository.traktClient.scrobble("stop", req.imdbId, req.mediaType, pct)
        }
    }

    fun closePlayer() {
        val finishedContentId = _activePlayback.value?.contentId
        lastScrobbleContentId = null
        lastScrobbleAction = null
        _activePlayback.value = null
        // Immediately refresh Resume button state on the Detail screen underneath
        val detail = _detailState.value.detail
        val targetId = _detailState.value.selectedEpisode?.id ?: detail?.item?.id ?: finishedContentId
        if (targetId != null) {
            viewModelScope.launch {
                val saved = runCatching { repository.getSavedWatchProgress(targetId) }.getOrNull()
                if (saved != null) {
                    _detailState.value = _detailState.value.copy(
                        savedPositionMs = saved.positionMs,
                        savedDurationMs = saved.durationMs
                    )
                }
            }
        }
    }

    fun resumeWatchProgress(progress: WatchProgress): Boolean {
        val streamUrl = progress.lastStreamUrl?.takeIf { it.isNotBlank() } ?: return false
        val isWebDav = progress.contentId.startsWith("dav:")
        val webDavHeaders = if (isWebDav) {
            val serverId = progress.contentId.split(":").getOrNull(1)
            val server = mediaServers.value.firstOrNull { it.id == serverId } ?: return false
            repository.serverManager.getWebDavAuthHeaders(server)
        } else {
            emptyMap()
        }
        _activePlayback.value = PlaybackRequest(
            contentId = progress.contentId,
            title = progress.title,
            subtitle = progress.episodeLabel,
            mediaType = progress.type,
            imdbId = progress.contentId.substringBefore(":").takeIf { it.startsWith("tt") },
            posterUrl = progress.posterUrl,
            backdropUrl = progress.backdropUrl,
            episodeId = progress.episodeId,
            stream = StreamSource(
                id = "resume_${progress.contentId}",
                title = progress.title,
                url = streamUrl,
                headers = webDavHeaders,
                providerName = if (isWebDav) "WebDAV" else "Resume",
                origin = if (isWebDav) SourceOrigin.WEBDAV else SourceOrigin.ADDON,
                isCached = true
            ),
            startPositionMs = progress.positionMs
        )
        return true
    }

    fun toggleFavorite(item: MediaItem, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(item, currentlyFavorite)
        }
    }

    fun installAddon(url: String, onDone: (Result<InstalledAddon>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.addonRepository.installAddon(url)
            result.onSuccess {
                _toastMessage.value = "Installed addon: ${it.manifest.name}"
                refreshHome()
            }.onFailure {
                _toastMessage.value = it.message ?: "Failed to install addon"
            }
            onDone(result)
        }
    }

    fun toggleAddonEnabled(manifestUrl: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.addonRepository.setAddonEnabled(manifestUrl, enabled)
            refreshHome()
        }
    }

    fun moveAddonUp(manifestUrl: String) {
        viewModelScope.launch {
            repository.addonRepository.moveAddonUp(manifestUrl)
            refreshHome()
        }
    }

    fun moveAddonDown(manifestUrl: String) {
        viewModelScope.launch {
            repository.addonRepository.moveAddonDown(manifestUrl)
            refreshHome()
        }
    }

    fun removeAddon(manifestUrl: String) {
        viewModelScope.launch {
            repository.addonRepository.removeAddon(manifestUrl)
            _addonHealthMap.value = _addonHealthMap.value - manifestUrl
            refreshHome()
        }
    }

    fun checkAddonHealth(manifestUrl: String) {
        viewModelScope.launch {
            _checkingHealthUrls.value = _checkingHealthUrls.value + manifestUrl
            val res = repository.addonRepository.checkAddonHealth(manifestUrl)
            _addonHealthMap.value = _addonHealthMap.value + (manifestUrl to res)
            _checkingHealthUrls.value = _checkingHealthUrls.value - manifestUrl
        }
    }

    fun checkAllAddonsHealth() {
        val current = addons.value
        current.forEach { checkAddonHealth(it.manifestUrl) }
    }

    fun toggleCatalogVisibility(catalogKey: String) {
        settingsStore.update { curr ->
            val next = if (catalogKey in curr.hiddenCatalogKeys) {
                curr.hiddenCatalogKeys - catalogKey
            } else {
                curr.hiddenCatalogKeys + catalogKey
            }
            curr.copy(hiddenCatalogKeys = next)
        }
        refreshHome()
    }

    fun saveDebridKey(type: DebridServiceType, apiKey: String) {
        viewModelScope.launch {
            val status = repository.debridManager.saveAndValidate(type, apiKey)
            _toastMessage.value = if (status.isConnected) {
                "${type.displayName} connected (${status.username ?: status.plan ?: "Active"})"
            } else if (apiKey.isBlank()) {
                "${type.displayName} disconnected"
            } else {
                status.error ?: "Invalid ${type.displayName} API key"
            }
        }
    }

    fun refreshCloudLibrary() {
        viewModelScope.launch {
            _isLoadingCloud.value = true
            _cloudItems.value = runCatching { repository.debridManager.getAllCloudItems() }.getOrDefault(emptyList())
            _isLoadingCloud.value = false
        }
    }

    fun saveMediaServer(config: MediaServerConfig, onResult: (ServerTestResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.serverManager.testAndSaveServer(config)
            _toastMessage.value = result.message
            if (result.success) refreshHome()
            onResult(result)
        }
    }

    fun deleteMediaServer(id: String) {
        viewModelScope.launch {
            repository.serverManager.deleteServer(id)
            refreshHome()
        }
    }

    fun browseWebDav(serverId: String, pathUrl: String? = null) {
        viewModelScope.launch {
            _webDavEntries.value = runCatching {
                repository.serverManager.browseWebDav(serverId, pathUrl)
            }.getOrDefault(emptyList())
        }
    }

    fun saveTmdbConfig(apiKey: String, language: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                repository.tmdbClient.saveConfig(TmdbConfig("", language))
                _tmdbConfig.value = repository.tmdbClient.getConfig()
                _toastMessage.value = "TMDB disconnected"
                refreshHome()
                return@launch
            }
            val check = repository.tmdbClient.testConnection(apiKey)
            check.onSuccess {
                repository.tmdbClient.saveConfig(TmdbConfig(apiKey.trim(), language))
                _tmdbConfig.value = repository.tmdbClient.getConfig()
                _toastMessage.value = it
                refreshHome()
            }.onFailure {
                _toastMessage.value = it.message ?: "TMDB authentication failed"
            }
        }
    }

    fun startTraktDeviceAuth(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            repository.traktClient.saveClientCredentials(clientId, clientSecret)
            val result = repository.traktClient.requestDeviceCode(clientId)
            result.onSuccess { code ->
                _traktDeviceCode.value = code
                _toastMessage.value = "Go to ${code.verificationUrl} and enter code: ${code.userCode}"
            }.onFailure {
                _toastMessage.value = it.message ?: "Trakt device auth failed"
            }
        }
    }

    fun confirmTraktDeviceAuth(clientId: String, clientSecret: String) {
        val code = _traktDeviceCode.value ?: return
        viewModelScope.launch {
            val res = repository.traktClient.pollDeviceToken(code.deviceCode, clientId, clientSecret)
            res.onSuccess { state ->
                _traktState.value = state
                _traktDeviceCode.value = null
                _toastMessage.value = "Trakt connected as ${state.username ?: "User"}"
            }.onFailure {
                _toastMessage.value = it.message ?: "Activation pending..."
            }
        }
    }

    fun disconnectTrakt() {
        repository.traktClient.disconnect()
        _traktState.value = repository.traktClient.getState()
        _traktDeviceCode.value = null
        _toastMessage.value = "Trakt disconnected"
    }





    fun syncFromStremio(email: String, password: String) {
        viewModelScope.launch {
            _toastMessage.value = "Syncing from Stremio..."
            cloudSyncManager.syncFromStremio(email = email, password = password)
                .onSuccess {
                    _toastMessage.value = it
                    refreshHome()
                }
                .onFailure { _toastMessage.value = it.message ?: "Stremio sync failed" }
        }
    }

    fun importFromNuvio(rawData: String) {
        viewModelScope.launch {
            _toastMessage.value = "Importing backup..."
            cloudSyncManager.importFromNuvio(rawData)
                .onSuccess {
                    _toastMessage.value = it
                    refreshHome()
                }
                .onFailure { _toastMessage.value = it.message ?: "Backup import failed" }
        }
    }

    class Factory(
        private val repository: QuernRepository,
        private val settingsStore: SettingsStore,
        private val cloudSyncManager: CloudSyncManager,
        private val quernAccount: QuernAccount,
        private val syncEngine: QuernSyncEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuernViewModel(repository, settingsStore, cloudSyncManager, quernAccount, syncEngine) as T
    }
}
