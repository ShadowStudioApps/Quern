package com.quern.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.TmdbConfig
import com.quern.app.domain.model.TraktAuthState
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.domain.model.WatchProgress
import com.quern.app.ui.DetailUiState
import com.quern.app.ui.QuernFloatingPhoneDock
import com.quern.app.ui.QuernTab
import com.quern.app.ui.QuernTvNavigationRail
import com.quern.app.ui.screens.DetailScreen
import com.quern.app.ui.screens.HomeScreen
import com.quern.app.ui.screens.ServersScreen
import com.quern.app.ui.theme.LumaBackground
import com.quern.app.ui.theme.QuernTheme

private val sampleMovies = listOf(
    MediaItem(
        id = "tt15239678",
        title = "Dune: Part Two",
        type = MediaType.MOVIE,
        year = "2024",
        rating = "8.8",
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
        origin = SourceOrigin.TMDB
    ),
    MediaItem(
        id = "tt0816692",
        title = "Interstellar",
        type = MediaType.MOVIE,
        year = "2014",
        rating = "8.7",
        origin = SourceOrigin.JELLYFIN
    ),
    MediaItem(
        id = "tt1375666",
        title = "Inception",
        type = MediaType.MOVIE,
        year = "2010",
        rating = "8.8",
        origin = SourceOrigin.ADDON
    ),
    MediaItem(
        id = "tt1856101",
        title = "Blade Runner 2049",
        type = MediaType.MOVIE,
        year = "2017",
        rating = "8.0",
        origin = SourceOrigin.PLEX
    )
)

private val sampleRows = listOf(
    CatalogRow("row_tmdb", "Trending Now", SourceOrigin.TMDB, sampleMovies),
    CatalogRow("row_jf", "Home Jellyfin", SourceOrigin.JELLYFIN, sampleMovies.reversed()),
    CatalogRow("row_addon", "Popular Series", SourceOrigin.ADDON, sampleMovies)
)

private val sampleContinue = listOf(
    WatchProgress(
        contentId = "tt15239678",
        title = "Dune: Part Two",
        type = MediaType.MOVIE,
        posterUrl = null,
        backdropUrl = null,
        positionMs = 4200_000L,
        durationMs = 9960_000L,
        episodeLabel = "4K Dolby Vision • TorBox Instant"
    ),
    WatchProgress(
        contentId = "tt0903747",
        title = "Breaking Bad",
        type = MediaType.SERIES,
        posterUrl = null,
        backdropUrl = null,
        positionMs = 1500_000L,
        durationMs = 2820_000L,
        episodeLabel = "S5 E14 • Ozymandias"
    )
)

@Preview(name = "Quern Phone Home", widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF05060B)
@Composable
fun QuernPhoneHomePreview() {
    QuernTheme {
        Box(modifier = Modifier.fillMaxSize().background(LumaBackground)) {
            HomeScreen(
                rows = sampleRows,
                continueWatching = sampleContinue,
                isLoading = false,
                isTv = false,
                onItemClick = {},
                onContinueClick = {},
                onOpenAddons = {},
                onOpenServers = {},
                onRefresh = {},
                activeServiceBadge = "TorBox"
            )
            QuernFloatingPhoneDock(
                currentTab = QuernTab.HOME,
                onSelectTab = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@Preview(name = "Quern Android TV Home", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF05060B)
@Composable
fun QuernTvHomePreview() {
    QuernTheme {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(LumaBackground)
        ) {
            QuernTvNavigationRail(
                currentTab = QuernTab.HOME,
                onSelectTab = {}
            )
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(
                    rows = sampleRows,
                    continueWatching = sampleContinue,
                    isLoading = false,
                    isTv = true,
                    onItemClick = {},
                    onContinueClick = {},
                    onOpenAddons = {},
                    onOpenServers = {},
                    onRefresh = {},
                    activeServiceBadge = "TorBox Pro"
                )
            }
        }
    }
}

@Preview(name = "Quern Detail & Streams", widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF05060B)
@Composable
fun QuernDetailStreamsPreview() {
    QuernTheme {
        DetailScreen(
            state = DetailUiState(
                detail = MediaDetail(
                    item = sampleMovies.first(),
                    runtime = "166 min",
                    cast = listOf("Timothee Chalamet", "Zendaya", "Rebecca Ferguson")
                ),
                streams = listOf(
                    StreamSource(
                        id = "s1",
                        title = "4K Dolby Vision REMUX • 58.4 GB",
                        subtitle = "Dune.Part.Two.2024.2160p.UHD.BluRay.DV.HDR.TrueHD.Atmos",
                        url = "https://example.com/stream.mkv",
                        providerName = "TorBox Instant",
                        origin = SourceOrigin.TORBOX,
                        qualityBadges = listOf("⚡ TorBox", "4K", "Dolby Vision", "Atmos"),
                        isCached = true
                    ),
                    StreamSource(
                        id = "s2",
                        title = "Home Jellyfin • Direct Play",
                        subtitle = "Zero-transcode native ExoPlayer stream",
                        url = "http://192.168.1.10:8096/stream",
                        providerName = "Home Jellyfin",
                        origin = SourceOrigin.JELLYFIN,
                        qualityBadges = listOf("Jellyfin", "Direct Play"),
                        isCached = true
                    ),
                    StreamSource(
                        id = "s3",
                        title = "1080p WEB-DL • 8.2 GB",
                        subtitle = "Cached via Real-Debrid",
                        infoHash = "abcdef123456",
                        providerName = "Stremio Addon",
                        origin = SourceOrigin.REAL_DEBRID,
                        qualityBadges = listOf("⚡ Real-Debrid", "1080p", "HEVC"),
                        isCached = true
                    )
                )
            ),
            isFavorite = true,
            isTv = false,
            onBack = {},
            onToggleFavorite = {},
            onSelectSeason = {},
            onSelectEpisode = {},
            onPlayStream = {}
        )
    }
}

@Preview(name = "Quern Linked Servers Hub", widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF05060B)
@Composable
fun QuernServersHubPreview() {
    QuernTheme {
        Box(modifier = Modifier.fillMaxSize().background(LumaBackground)) {
            ServersScreen(
                debridStatuses = mapOf(
                    DebridServiceType.TORBOX to DebridAccountStatus(
                        type = DebridServiceType.TORBOX,
                        isConnected = true,
                        username = "user@torbox.app",
                        plan = "Pro"
                    ),
                    DebridServiceType.REAL_DEBRID to DebridAccountStatus(
                        type = DebridServiceType.REAL_DEBRID,
                        isConnected = true,
                        username = "quern_rd",
                        plan = "Premium"
                    ),
                    DebridServiceType.ALL_DEBRID to DebridAccountStatus(
                        type = DebridServiceType.ALL_DEBRID,
                        isConnected = false
                    ),
                    DebridServiceType.PREMIUMIZE to DebridAccountStatus(
                        type = DebridServiceType.PREMIUMIZE,
                        isConnected = false
                    )
                ),
                getDebridKey = { if (it == DebridServiceType.TORBOX) "tb_live_key_demo" else "" },
                mediaServers = listOf(
                    MediaServerConfig("1", "Home Jellyfin", MediaServerType.JELLYFIN, "http://192.168.1.10:8096"),
                    MediaServerConfig("2", "Cloud WebDAV", MediaServerType.WEBDAV, "https://cloud.example.com/dav/")
                ),
                tmdbConfig = TmdbConfig(apiKey = "configured", language = "ar-SA"),
                traktState = TraktAuthState(accessToken = "tok", username = "quern_user"),
                traktDeviceCode = null,
                uiModeOverride = UiModeOverride.AUTO,
                isTv = false,
                onSaveDebridKey = { _, _ -> },
                onSaveMediaServer = { _, _ -> },
                onDeleteMediaServer = {},
                onSaveTmdb = { _, _ -> },
                onStartTraktAuth = { _, _ -> },
                onConfirmTraktAuth = { _, _ -> },
                onDisconnectTrakt = {},
                onUiModeChange = {}
            )
            QuernFloatingPhoneDock(
                currentTab = QuernTab.SETTINGS,
                onSelectTab = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}
