package com.quern.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppSettings
import com.quern.app.domain.model.AppThemePreset
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaDetail
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.UserProfile
import com.quern.app.domain.model.WatchProgress
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.ui.components.SoftGlowBackground
import com.quern.app.ui.screens.AccountUi
import com.quern.app.ui.screens.DetailScreen
import com.quern.app.ui.screens.HomeScreen
import com.quern.app.ui.screens.LibraryScreen
import com.quern.app.ui.screens.ProfilePickerScreen
import com.quern.app.ui.screens.SearchScreen
import com.quern.app.ui.screens.SettingsGroup
import com.quern.app.ui.screens.SettingsScreen
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.QuernTheme

// Strictly generic placeholder data for layout verification previews (never any real content/addon/source).
private val sampleItems = (1..8).map { i ->
    MediaItem(
        id = "sample_$i",
        title = "Sample Title $i",
        type = if (i % 2 == 0) MediaType.SERIES else MediaType.MOVIE,
        year = "${2020 + (i % 6)}",
        rating = "8.$i",
        overview = "A concise two-line placeholder synopsis demonstrating layout spacing, readability, and wrapping across phone, foldable, and tablet widths.",
        genres = listOf(if (i % 2 == 0) "Drama" else "Sci-Fi"),
        runtime = if (i % 2 == 0) "45 min" else "118 min",
        origin = SourceOrigin.ADDON
    )
}

private val sampleRows = listOf(
    CatalogRow(id = "row_1", title = "Featured Movies", items = sampleItems, origin = SourceOrigin.ADDON),
    CatalogRow(id = "row_2", title = "Popular Series", items = sampleItems.reversed(), origin = SourceOrigin.ADDON)
)

private val sampleContinue = listOf(
    WatchProgress(
        contentId = "sample_1",
        title = "Sample Title 1",
        type = MediaType.MOVIE,
        posterUrl = null,
        backdropUrl = null,
        positionMs = 24 * 60_000L,
        durationMs = 118 * 60_000L
    ),
    WatchProgress(
        contentId = "sample_2",
        title = "Sample Title 2",
        type = MediaType.SERIES,
        posterUrl = null,
        backdropUrl = null,
        episodeLabel = "S1 E4 • Episode 4",
        positionMs = 18 * 60_000L,
        durationMs = 45 * 60_000L
    )
)

private val sampleAccountUi = AccountUi(
    state = QuernAccountState(isSignedIn = false),
    busy = false,
    message = null,
    onSignIn = { _, _ -> },
    onSignUp = { _, _ -> },
    onResend = {},
    onSignOut = {},
    onDelete = {}
)

@Composable
private fun PreviewShell(
    layout: QuernLayoutClass,
    scalePercent: Int = 100,
    showDock: Boolean = layout != QuernLayoutClass.TV,
    activeTab: QuernTab = QuernTab.HOME,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val scaled = Density(baseDensity.density * (scalePercent / 100f), baseDensity.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled, LocalQuernLayout provides layout) {
        QuernTheme(themePreset = AppThemePreset.GLOSSY_BLACK, language = AppLanguage.ENGLISH) {
            val palette = LocalQuernPalette.current
            Box(Modifier.fillMaxSize()) {
                SoftGlowBackground(
                    baseColor = palette.background,
                    glowColors = listOf(Color(0xFF1F2B3D), Color(0xFF2B384C), Color(0xFF141C28), Color(0xFF222D3F)),
                    intensity = 0.78f
                )
                content()
                if (showDock) {
                    QuernFloatingPhoneDock(
                        currentTab = activeTab,
                        onSelectTab = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Compact Phone 360x800 Home", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun Compact360HomePreview() {
    PreviewShell(QuernLayoutClass.COMPACT) {
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
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Compact Phone 412x915 Home", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun Compact412HomePreview() {
    PreviewShell(QuernLayoutClass.COMPACT) {
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
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Compact 412x915 Scale 85% Large Cards", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun Compact412ScaledHomePreview() {
    PreviewShell(QuernLayoutClass.COMPACT, scalePercent = 85) {
        HomeScreen(
            rows = sampleRows,
            continueWatching = sampleContinue,
            isLoading = false,
            isTv = false,
            cardSize = CardSizePreset.LARGE,
            onItemClick = {},
            onContinueClick = {},
            onOpenAddons = {},
            onOpenServers = {},
            onRefresh = {},
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Wide Foldable 673x841 Home", widthDp = 673, heightDp = 841, showBackground = true)
@Composable
fun WideFoldableHomePreview() {
    PreviewShell(QuernLayoutClass.WIDE) {
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
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Wide Tablet Portrait 800x1280 Home", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
fun WideTabletPortraitHomePreview() {
    PreviewShell(QuernLayoutClass.WIDE) {
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
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Wide Tablet Landscape 1280x800 Home", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun WideTabletLandscapeHomePreview() {
    PreviewShell(QuernLayoutClass.WIDE) {
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
            addonsQrUrl = ""
        )
    }
}

@Preview(name = "Compact 360x800 Library Grid", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactLibraryPreview() {
    PreviewShell(QuernLayoutClass.COMPACT, activeTab = QuernTab.LIBRARY) {
        LibraryScreen(
            favorites = sampleItems,
            cloudItems = emptyList(),
            isLoadingCloud = false,
            servers = emptyList(),
            webDavEntries = emptyList(),
            isTv = false,
            cardSize = CardSizePreset.STANDARD,
            onItemClick = {},
            onRefreshCloud = {},
            onPlayCloudItem = {},
            onBrowseWebDav = { _, _ -> },
            onPlayWebDavEntry = { _, _ -> }
        )
    }
}

@Preview(name = "Compact 360x800 Search Grid", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactSearchPreview() {
    PreviewShell(QuernLayoutClass.COMPACT, activeTab = QuernTab.SEARCH) {
        SearchScreen(
            query = "Sample",
            results = sampleItems,
            isSearching = false,
            isTv = false,
            cardSize = CardSizePreset.STANDARD,
            onQueryChange = {},
            onSearchSubmit = {},
            onItemClick = {}
        )
    }
}

@Preview(name = "Compact 412x915 Details", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CompactDetailsPreview() {
    PreviewShell(QuernLayoutClass.COMPACT, showDock = false) {
        DetailScreen(
            state = DetailUiState(
                detail = MediaDetail(
                    item = sampleItems.first(),
                    cast = listOf("Alex Rivera", "Samira Noor", "Jordan Lee"),
                    runtime = "118 min"
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

@Preview(name = "Wide Tablet 800x1280 Details", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
fun WideTabletDetailsPreview() {
    PreviewShell(QuernLayoutClass.WIDE, showDock = false) {
        DetailScreen(
            state = DetailUiState(
                detail = MediaDetail(
                    item = sampleItems.first(),
                    cast = listOf("Alex Rivera", "Samira Noor", "Jordan Lee"),
                    runtime = "118 min"
                )
            ),
            isFavorite = false,
            isTv = false,
            onBack = {},
            onToggleFavorite = {},
            onSelectSeason = {},
            onSelectEpisode = {},
            onPlayStream = {}
        )
    }
}

@Preview(name = "Compact 412x915 Settings", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CompactSettingsPreview() {
    PreviewShell(QuernLayoutClass.COMPACT, activeTab = QuernTab.SETTINGS) {
        SettingsScreen(
            settings = AppSettings(),
            companionUrl = "",
            addonsQrUrl = "",
            account = sampleAccountUi,
            addons = emptyList(),
            debridStatuses = emptyMap(),
            getDebridKey = { "" },
            mediaServers = emptyList(),
            tmdbConfig = com.quern.app.domain.model.TmdbConfig(),
            traktState = com.quern.app.domain.model.TraktAuthState(),
            traktDeviceCode = null,
            isTv = false,
            initialGroup = SettingsGroup.APPEARANCE,
            onUpdateSettings = {},
            onApplyAppIcon = {},
            onInstallAddon = { _, _ -> },
            onToggleAddon = { _, _ -> },
            onMoveAddonUp = {},
            onMoveAddonDown = {},
            onRemoveAddon = {},
            onSaveDebridKey = { _, _ -> },
            onSaveMediaServer = { _, _ -> },
            onDeleteMediaServer = {},
            onSaveTmdb = { _, _ -> },
            onStartTraktAuth = { _, _ -> },
            onConfirmTraktAuth = { _, _ -> },
            onDisconnectTrakt = {},
            onClearCache = {}
        )
    }
}

@Preview(name = "Wide Tablet 1280x800 Settings", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun WideTabletSettingsPreview() {
    PreviewShell(QuernLayoutClass.WIDE, activeTab = QuernTab.SETTINGS) {
        SettingsScreen(
            settings = AppSettings(),
            companionUrl = "",
            addonsQrUrl = "",
            account = sampleAccountUi,
            addons = emptyList(),
            debridStatuses = emptyMap(),
            getDebridKey = { "" },
            mediaServers = emptyList(),
            tmdbConfig = com.quern.app.domain.model.TmdbConfig(),
            traktState = com.quern.app.domain.model.TraktAuthState(),
            traktDeviceCode = null,
            isTv = false,
            initialGroup = SettingsGroup.APPEARANCE,
            onUpdateSettings = {},
            onApplyAppIcon = {},
            onInstallAddon = { _, _ -> },
            onToggleAddon = { _, _ -> },
            onMoveAddonUp = {},
            onMoveAddonDown = {},
            onRemoveAddon = {},
            onSaveDebridKey = { _, _ -> },
            onSaveMediaServer = { _, _ -> },
            onDeleteMediaServer = {},
            onSaveTmdb = { _, _ -> },
            onStartTraktAuth = { _, _ -> },
            onConfirmTraktAuth = { _, _ -> },
            onDisconnectTrakt = {},
            onClearCache = {}
        )
    }
}

@Preview(name = "TV 960x540 Darker Profile Picker (1 Profile)", widthDp = 960, heightDp = 540, showBackground = true)
@Composable
fun TvDarkerProfilePickerOnePreview() {
    PreviewShell(QuernLayoutClass.TV, showDock = false) {
        ProfilePickerScreen(
            profiles = listOf(
                UserProfile("main", "Owner", 0xFF345372, avatarKey = "bolt")
            ),
            activeProfileId = "main",
            isAr = false,
            onProfileChosen = {},
            onDismiss = {},
            onCreateProfile = {}
        )
    }
}

@Preview(name = "TV 960x540 Darker Profile Picker (3 Profiles)", widthDp = 960, heightDp = 540, showBackground = true)
@Composable
fun TvDarkerProfilePickerThreePreview() {
    PreviewShell(QuernLayoutClass.TV, showDock = false) {
        ProfilePickerScreen(
            profiles = listOf(
                UserProfile("p1", "Owner", 0xFF345372, avatarKey = "bolt"),
                UserProfile("p2", "Family", 0xFFEC4899, avatarKey = "flower"),
                UserProfile("p3", "Kids", 0xFFF59E0B, avatarKey = "smile")
            ),
            activeProfileId = "p1",
            isAr = false,
            onProfileChosen = {},
            onDismiss = {},
            onCreateProfile = {}
        )
    }
}
