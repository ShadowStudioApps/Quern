package com.quern.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quern.app.R
import com.quern.app.data.companion.CompanionSessionState
import com.quern.app.domain.model.CardCornerPreset
import com.quern.app.domain.model.CardShapePreset
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.ContinueWatchingSort
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.RowOrderPreset
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.WatchProgress
import java.util.Calendar
import com.quern.app.ui.components.LumaHeroBanner
import com.quern.app.ui.components.LumaMediaCarouselRow
import com.quern.app.ui.components.QuernCompanionSignInCard
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFillStrong
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

@Composable
fun HomeScreen(
    rows: List<CatalogRow>,
    continueWatching: List<WatchProgress>,
    isLoading: Boolean,
    isTv: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onContinueClick: (WatchProgress) -> Unit,
    onOpenAddons: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit,
    addonsQrUrl: String = "",
    companionSession: CompanionSessionState = CompanionSessionState("QRN-000", System.currentTimeMillis() + 600_000L),
    onRefreshCompanionSession: () -> Unit = {},
    onBindCompanionServer: () -> Unit = {},
    onUnbindCompanionServer: () -> Unit = {},
    activeServiceBadge: String? = null,
    showHeroBanner: Boolean = true,
    showPosterLabels: Boolean = true,
    hideUnreleasedContent: Boolean = false,
    rowOrder: RowOrderPreset = RowOrderPreset.DEFAULT,
    showContinueWatching: Boolean = true,
    continueWatchingSort: ContinueWatchingSort = ContinueWatchingSort.RECENT,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    cardShape: CardShapePreset = CardShapePreset.PORTRAIT,
    cardCorner: CardCornerPreset = CardCornerPreset.ROUNDED
) {
    val palette = LocalQuernPalette.current
    var selectedCategory by remember { mutableStateOf("All") }
    var heroResetSignal by remember { mutableIntStateOf(0) }
    // Focus changes land here first (no recomposition); HomeHeroSection consumes it after a 220ms
    // dwell so only the hero recomposes — never HomeScreen, LazyColumn, or any poster row.
    val pendingHeroFocus = remember { MutableStateFlow<Pair<MediaItem, Int>?>(null) }
    val onPosterFocused = remember(pendingHeroFocus) {
        { item: MediaItem, idx: Int -> pendingHeroFocus.value = item to idx }
    }
    val firstRowFocusRequester = remember { FocusRequester() }
    var isFirstRowItemAttached by remember { mutableStateOf(false) }
    val onFirstRowAttachedChanged = remember { { attached: Boolean -> isFirstRowItemAttached = attached } }
    val todayYmd = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.YEAR) * 10_000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }
    val currentYear = remember(todayYmd) { todayYmd / 10_000 }

    val sortedContinueWatching = remember(continueWatching, showContinueWatching, continueWatchingSort) {
        if (!showContinueWatching) {
            emptyList()
        } else {
            when (continueWatchingSort) {
                ContinueWatchingSort.RECENT -> continueWatching.sortedByDescending { it.updatedAt }
                ContinueWatchingSort.PROGRESS_DESC -> continueWatching.sortedByDescending { it.progressFraction }
                ContinueWatchingSort.TITLE_AZ -> continueWatching.sortedBy { it.title.lowercase() }
            }
        }
    }

    val filteredRows = remember(rows, selectedCategory, hideUnreleasedContent, rowOrder, currentYear) {
        val base = when (selectedCategory) {
            "Movies" -> rows.mapNotNull { r ->
                val items = r.items.filter { it.type == MediaType.MOVIE }
                if (items.isEmpty()) null else r.copy(items = items)
            }
            "Series" -> rows.mapNotNull { r ->
                val items = r.items.filter { it.type == MediaType.SERIES }
                if (items.isEmpty()) null else r.copy(items = items)
            }
            "Servers" -> rows.filter {
                it.origin in setOf(SourceOrigin.JELLYFIN, SourceOrigin.EMBY, SourceOrigin.PLEX, SourceOrigin.WEBDAV)
            }
            else -> rows
        }

        val released = if (!hideUnreleasedContent) base else base.mapNotNull { r ->
            val kept = r.items.filter { item ->
                val fullDateMatch = Regex("""(\d{4})-(\d{2})-(\d{2})""")
                    .find(item.releaseDate ?: item.year.orEmpty())
                if (fullDateMatch != null) {
                    val y = fullDateMatch.groupValues[1].toIntOrNull()
                    val m = fullDateMatch.groupValues[2].toIntOrNull()
                    val d = fullDateMatch.groupValues[3].toIntOrNull()
                    if (y != null && m != null && d != null) {
                        (y * 10_000 + m * 100 + d) <= todayYmd
                    } else true
                } else {
                    val y = Regex("""\d{4}""").find(item.year.orEmpty())?.value?.toIntOrNull()
                    y == null || y <= currentYear
                }
            }
            if (kept.isEmpty()) null else r.copy(items = kept)
        }

        when (rowOrder) {
            RowOrderPreset.DEFAULT -> released
            RowOrderPreset.MOVIES_FIRST -> released.sortedBy { r ->
                if (r.items.firstOrNull()?.type == MediaType.MOVIE) 0 else 1
            }
            RowOrderPreset.SERIES_FIRST -> released.sortedBy { r ->
                if (r.items.firstOrNull()?.type == MediaType.SERIES) 0 else 1
            }
            RowOrderPreset.SERVERS_FIRST -> released.sortedBy { r ->
                val isServer = r.origin in setOf(SourceOrigin.JELLYFIN, SourceOrigin.EMBY, SourceOrigin.PLEX, SourceOrigin.WEBDAV)
                if (isServer) 0 else 1
            }
        }
    }

    val featuredSlides = remember(filteredRows, rows) {
        val primary = filteredRows.firstOrNull()?.items.orEmpty()
        val fallback = rows.firstOrNull()?.items.orEmpty()
        (if (primary.isNotEmpty()) primary else fallback).take(8)
    }

    val hasContinueWatching = sortedContinueWatching.isNotEmpty()
    val hasAnyContentRow = hasContinueWatching || filteredRows.isNotEmpty()
    val firstRowAnchorKey = if (hasContinueWatching) {
        "cw_${sortedContinueWatching.firstOrNull()?.contentId}"
    } else {
        "cat_${filteredRows.firstOrNull()?.id}_${filteredRows.firstOrNull()?.items?.firstOrNull()?.id}"
    }
    LaunchedEffect(firstRowAnchorKey, hasAnyContentRow) {
        if (!hasAnyContentRow) {
            isFirstRowItemAttached = false
        }
    }

    val onSelectCategory = remember(pendingHeroFocus) {
        { category: String ->
            selectedCategory = category
            pendingHeroFocus.value = null
            heroResetSignal += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        if (isLoading && rows.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = palette.focusRing)
                Spacer(Modifier.height(14.dp))
                Text("Loading your cinema hub...", color = LumaTextSecondary, fontSize = 14.sp)
            }
        } else if (isTv && featuredSlides.isNotEmpty() && showHeroBanner) {
            // Borderless full-bleed TV layout:
            // HomeHeroSection dissolves cleanly into palette.background at its bottom edge so
            // LazyColumn sits directly underneath with ZERO GPU overdraw and ZERO sliced rows.
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeroSection(
                    featuredSlides = featuredSlides,
                    pendingHeroFocus = pendingHeroFocus,
                    resetSignal = heroResetSignal,
                    isTv = true,
                    onSelect = onItemClick,
                    onRefresh = onRefresh,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onSelectCategory,
                    activeServiceBadge = activeServiceBadge,
                    firstRowFocusRequester = if (hasAnyContentRow) firstRowFocusRequester else null,
                    isFirstRowAttached = { isFirstRowItemAttached }
                )

                // When Continue Watching appears (e.g. after switching to a profile that has history),
                // LazyColumn keeps the current first row anchored and inserts the new row above the
                // viewport. Scroll back up so it's actually visible when the user is at the top.
                val tvListState = rememberLazyListState()
                val hasContinueWatching = sortedContinueWatching.isNotEmpty()
                LaunchedEffect(hasContinueWatching) {
                    if (hasContinueWatching && tvListState.firstVisibleItemIndex <= 1) {
                        tvListState.scrollToItem(0)
                    }
                }

                LazyColumn(
                    state = tvListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasContinueWatching) {
                        item(key = "continue_watching", contentType = "continue_watching") {
                            ContinueWatchingRow(
                                items = sortedContinueWatching,
                                isTv = true,
                                onClick = onContinueClick,
                                firstItemFocusRequester = firstRowFocusRequester,
                                onFirstItemAttachedChanged = onFirstRowAttachedChanged
                            )
                        }
                    }

                    items(filteredRows, key = { it.id }, contentType = { "carousel" }) { row ->
                        val attachFirstRow = !hasContinueWatching && row.id == filteredRows.firstOrNull()?.id
                        LumaMediaCarouselRow(
                            row = row,
                            isTv = true,
                            cardSize = cardSize,
                            cardShape = cardShape,
                            cardCorner = cardCorner,
                            showPosterLabels = showPosterLabels,
                            onItemClick = onItemClick,
                            onItemFocused = onPosterFocused,
                            firstItemFocusRequester = if (attachFirstRow) firstRowFocusRequester else null,
                            onFirstItemAttachedChanged = if (attachFirstRow) onFirstRowAttachedChanged else null
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (featuredSlides.isNotEmpty() && showHeroBanner) {
                    item(key = "hero") {
                        HomeHeroSection(
                            featuredSlides = featuredSlides,
                            pendingHeroFocus = pendingHeroFocus,
                            resetSignal = heroResetSignal,
                            isTv = isTv,
                            onSelect = onItemClick,
                            onRefresh = onRefresh,
                            selectedCategory = selectedCategory,
                            onCategorySelected = onSelectCategory,
                            activeServiceBadge = activeServiceBadge,
                            firstRowFocusRequester = null,
                            isFirstRowAttached = { false }
                        )
                    }
                } else if (rows.isEmpty()) {
                    item(key = "welcome_empty") {
                        EmptyHomeCard(
                            isTv = isTv,
                            addonsQrUrl = addonsQrUrl,
                            companionSession = companionSession,
                            onRefreshCompanionSession = onRefreshCompanionSession,
                            onBindCompanionServer = onBindCompanionServer,
                            onUnbindCompanionServer = onUnbindCompanionServer,
                            onOpenAddons = onOpenAddons,
                            onOpenServers = onOpenServers,
                            onRefresh = onRefresh
                        )
                    }
                }

                if (hasContinueWatching) {
                    item(key = "continue_watching", contentType = "continue_watching") {
                        ContinueWatchingRow(
                            items = sortedContinueWatching,
                            isTv = isTv,
                            onClick = onContinueClick
                        )
                    }
                }

                items(filteredRows, key = { it.id }, contentType = { "carousel" }) { row ->
                    LumaMediaCarouselRow(
                        row = row,
                        isTv = isTv,
                        cardSize = cardSize,
                        cardShape = cardShape,
                        cardCorner = cardCorner,
                        showPosterLabels = showPosterLabels,
                        onItemClick = onItemClick,
                        onItemFocused = if (isTv) onPosterFocused else null
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroSection(
    featuredSlides: List<MediaItem>,
    pendingHeroFocus: MutableStateFlow<Pair<MediaItem, Int>?>,
    resetSignal: Int,
    isTv: Boolean,
    onSelect: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    activeServiceBadge: String?,
    firstRowFocusRequester: FocusRequester?,
    isFirstRowAttached: () -> Boolean,
    modifier: Modifier = Modifier
) {
    var focusedHeroItem by remember { mutableStateOf<MediaItem?>(null) }
    var focusedItemIndex by remember { mutableIntStateOf(0) }
    var autoSlideIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(resetSignal) {
        focusedHeroItem = null
        focusedItemIndex = 0
        autoSlideIndex = 0
    }

    LaunchedEffect(pendingHeroFocus) {
        pendingHeroFocus.collectLatest { pending ->
            if (pending == null) return@collectLatest
            delay(220L)
            focusedHeroItem = pending.first
            focusedItemIndex = pending.second
        }
    }

    // Gentle auto-cycle on Hero Spotlight when no poster is actively hovered
    LaunchedEffect(featuredSlides.size, focusedHeroItem) {
        if (featuredSlides.size > 1 && focusedHeroItem == null) {
            while (true) {
                delay(7500L)
                autoSlideIndex = (autoSlideIndex + 1) % featuredSlides.size
            }
        }
    }

    val heroItem = focusedHeroItem
        ?: featuredSlides.getOrNull(autoSlideIndex % featuredSlides.size.coerceAtLeast(1))
        ?: return
    val activeDotIndex = if (focusedHeroItem != null) {
        focusedItemIndex % featuredSlides.size.coerceAtLeast(1)
    } else {
        autoSlideIndex % featuredSlides.size.coerceAtLeast(1)
    }

    LumaHeroBanner(
        item = heroItem,
        isTv = isTv,
        onSelect = onSelect,
        onRefresh = onRefresh,
        selectedCategory = selectedCategory,
        onCategorySelected = onCategorySelected,
        activeServiceBadge = activeServiceBadge,
        firstRowFocusRequester = firstRowFocusRequester,
        isFirstRowAttached = isFirstRowAttached,
        totalSlides = featuredSlides.size,
        activeSlideIndex = activeDotIndex,
        modifier = modifier
    )
}

private val ContinueCardShape = RoundedCornerShape(10.dp)
private val ContinueThumbScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))

@Composable
private fun ContinueWatchingRow(
    items: List<WatchProgress>,
    isTv: Boolean,
    onClick: (WatchProgress) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemAttachedChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val strings = LocalQuernStrings.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTv) 14.dp else 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 2.5.dp, height = 11.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(palette.focusRing)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings.continueWatching,
                color = LumaTextPrimary,
                fontSize = if (isTv) 12.sp else 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (isTv) 16.dp else 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items, key = { _, it -> it.contentId }, contentType = { _, _ -> "cw_card" }) { index, progress ->
                val isFirst = index == 0
                if (isFirst && firstItemFocusRequester != null && onFirstItemAttachedChanged != null) {
                    DisposableEffect(progress.contentId) {
                        onDispose { onFirstItemAttachedChanged(false) }
                    }
                }
                var focused by remember { mutableStateOf(false) }
                val focusScale = animateFloatAsState(
                    targetValue = if (focused) 1.04f else 1f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "cwFocusScale"
                )
                val remainingMins = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L)
                val imgReq = remember(progress.backdropUrl, progress.posterUrl) {
                    val url = progress.backdropUrl ?: progress.posterUrl
                    if (url.isNullOrBlank()) null else {
                        ImageRequest.Builder(context).data(url).size(320, 180).crossfade(false).build()
                    }
                }

                Column(
                    modifier = Modifier
                        .then(
                            if (isFirst && firstItemFocusRequester != null) {
                                Modifier
                                    .focusRequester(firstItemFocusRequester)
                                    .onGloballyPositioned { coords ->
                                        onFirstItemAttachedChanged?.invoke(coords.isAttached)
                                    }
                            } else Modifier
                        )
                        .width(if (isTv) 142.dp else 130.dp)
                        .zIndex(if (focused) 5f else 0f)
                        .graphicsLayer {
                            val s = focusScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(ContinueCardShape)
                        .background(palette.surface)
                        .then(
                            if (focused) Modifier.border(2.dp, palette.focusRing, ContinueCardShape)
                            else Modifier
                        )
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { onClick(progress) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTv) 74.dp else 66.dp)
                            .background(palette.surfaceElevated)
                    ) {
                        if (imgReq != null) {
                            AsyncImage(
                                model = imgReq,
                                contentDescription = progress.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ContinueThumbScrim)
                        )

                        Surface(
                            color = if (focused) palette.accent else LumaGlassFillStrong,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, if (focused) palette.focusRing else LumaGlassBorder),
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (focused) palette.onAccent else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (remainingMins > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "${remainingMins}m left",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.progressFraction.coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(palette.focusRing)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = progress.title,
                            color = if (focused) palette.focusRing else LumaTextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!progress.episodeLabel.isNullOrBlank()) {
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = progress.episodeLabel,
                                color = LumaTextSecondary,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(
    isTv: Boolean,
    addonsQrUrl: String,
    companionSession: CompanionSessionState,
    onRefreshCompanionSession: () -> Unit,
    onBindCompanionServer: () -> Unit,
    onUnbindCompanionServer: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var addBtnFocused by remember { mutableStateOf(false) }
    var settingsBtnFocused by remember { mutableStateOf(false) }
    var refreshFocused by remember { mutableStateOf(false) }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = if (isTv) 28.dp else 18.dp)
    ) {
        Row(
            modifier = Modifier.padding(if (isTv) 28.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = qString(R.string.first_run_title),
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 26.sp else 21.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = qString(R.string.first_run_subtitle),
                    color = LumaTextSecondary,
                    fontSize = if (isTv) 14.sp else 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (addBtnFocused) Color.White else palette.accent,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            width = if (addBtnFocused) 2.5.dp else 1.dp,
                            color = if (addBtnFocused) palette.focusRing else palette.accent
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(
                                onFocusChangedState = { addBtnFocused = it },
                                onClick = onOpenAddons
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                tint = if (addBtnFocused) Color.Black else palette.onAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = qString(R.string.first_run_add_addon),
                                color = if (addBtnFocused) Color.Black else palette.onAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = if (settingsBtnFocused) palette.accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            width = if (settingsBtnFocused) 2.5.dp else 1.dp,
                            color = if (settingsBtnFocused) palette.focusRing else palette.border
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(
                                onFocusChangedState = { settingsBtnFocused = it },
                                onClick = onOpenServers
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (settingsBtnFocused) palette.onAccent else LumaTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = qString(R.string.first_run_open_settings),
                                color = if (settingsBtnFocused) palette.onAccent else LumaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (refreshFocused) palette.accent else palette.surfaceElevated)
                            .border(
                                width = if (refreshFocused) 2.dp else 1.dp,
                                color = if (refreshFocused) palette.focusRing else palette.border,
                                shape = CircleShape
                            )
                            .tvClickable(
                                onFocusChangedState = { refreshFocused = it },
                                onClick = onRefresh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (refreshFocused) palette.onAccent else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (addonsQrUrl.isNotBlank() && isTv) {
                QuernCompanionSignInCard(
                    url = addonsQrUrl,
                    session = companionSession,
                    onRefreshSession = onRefreshCompanionSession,
                    onBindServer = onBindCompanionServer,
                    onUnbindServer = onUnbindCompanionServer,
                    compactVertical = true,
                    modifier = Modifier.width(258.dp)
                )
            }
        }
    }
}
