package com.quern.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.quern.app.R
import com.quern.app.data.update.AvailableUpdate
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
import com.quern.app.ui.components.AmbientBlurredBackdrop
import com.quern.app.ui.components.HomeEdgeScrolling
import com.quern.app.ui.components.LocalContentFocusMemory
import com.quern.app.ui.components.LocalTvRailInset
import com.quern.app.ui.components.LumaHeroBanner
import com.quern.app.ui.components.LumaMediaCarouselRow
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.ui.components.QuernHomeSpacing
import com.quern.app.ui.components.WhatsNewDialog
import com.quern.app.ui.components.buildAmbientBackdropRequest
import com.quern.app.ui.components.buildHeroBackdropRequest
import com.quern.app.ui.components.parseAvailableUpdateNotes
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFillStrong
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.frostedSheen
import com.quern.app.ui.theme.qString

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeScreen(
    rows: List<CatalogRow>,
    continueWatching: List<WatchProgress>,
    isLoading: Boolean,
    isTv: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onContinueClick: (WatchProgress) -> Unit,
    onResumeDirect: (WatchProgress) -> Unit = onContinueClick,
    onResolveHeroMeta: (suspend (MediaItem) -> MediaItem)? = null,
    onOpenAddons: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit,
    emptyTvQr: (@Composable () -> Unit)? = null,
    activeServiceBadge: String? = null,
    showHeroBanner: Boolean = true,
    showPosterLabels: Boolean = true,
    hideUnreleasedContent: Boolean = false,
    rowOrder: RowOrderPreset = RowOrderPreset.DEFAULT,
    showContinueWatching: Boolean = true,
    continueWatchingSort: ContinueWatchingSort = ContinueWatchingSort.RECENT,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    cardShape: CardShapePreset = CardShapePreset.PORTRAIT,
    cardCorner: CardCornerPreset = CardCornerPreset.ROUNDED,
    updateBannerRelease: AvailableUpdate? = null,
    onOpenUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    val palette = LocalQuernPalette.current
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var selectedCategory by remember { mutableStateOf("All") }
    var heroResetSignal by remember { mutableIntStateOf(0) }
    // Focus changes land here first (no recomposition); debounced at 220ms into activeHeroState so
    // ONLY HomeAmbientBackdropLayer and HomeHeroSection recompose — never HomeScreen, LazyColumn, or rows.
    val pendingHeroFocus = remember { MutableStateFlow<Pair<MediaItem, Int>?>(null) }
    val activeHeroState = remember { MutableStateFlow<Pair<MediaItem, Int>?>(null) }
    val pendingPreloadItems = remember { MutableStateFlow<List<MediaItem>>(emptyList()) }
    val onPosterFocused = remember(pendingHeroFocus) {
        { item: MediaItem, idx: Int -> pendingHeroFocus.value = item to idx }
    }
    val onPreloadNextItems = remember(pendingPreloadItems) {
        { items: List<MediaItem> -> pendingPreloadItems.value = items }
    }
    val firstRowFocusRequester = remember { FocusRequester() }
    // Track which row ("cw" vs "cat") currently owns firstRowFocusRequester so a disposing row
    // can never clear the attached state of the row replacing it.
    var attachedFirstRowTag by remember { mutableStateOf<String?>(null) }
    val onCwFirstAttachedChanged = remember {
        { attached: Boolean ->
            attachedFirstRowTag = if (attached) "cw" else if (attachedFirstRowTag == "cw") null else attachedFirstRowTag
        }
    }
    val onCatFirstAttachedChanged = remember {
        { attached: Boolean ->
            attachedFirstRowTag = if (attached) "cat" else if (attachedFirstRowTag == "cat") null else attachedFirstRowTag
        }
    }
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

    val continueWatchingByBaseId = remember(sortedContinueWatching) {
        sortedContinueWatching.associateBy { it.contentId.substringBefore(":") }
    }

    val catalogItemsById = remember(rows) {
        buildMap {
            rows.forEach { row ->
                row.items.forEach { item ->
                    putIfAbsent(item.id, item)
                    item.imdbId?.let { putIfAbsent(it, item) }
                }
            }
        }
    }

    val onContinueItemFocused = remember(pendingHeroFocus, pendingPreloadItems, catalogItemsById, sortedContinueWatching) {
        { progress: WatchProgress, idx: Int ->
            fun toMedia(wp: WatchProgress): MediaItem {
                val bId = wp.contentId.substringBefore(":")
                return catalogItemsById[bId] ?: MediaItem(
                    id = bId,
                    title = wp.title,
                    type = wp.type,
                    posterUrl = wp.posterUrl,
                    backdropUrl = wp.backdropUrl
                )
            }
            pendingHeroFocus.value = toMedia(progress) to idx
            val nextTwo = buildList(2) {
                sortedContinueWatching.getOrNull(idx + 1)?.let { add(toMedia(it)) }
                sortedContinueWatching.getOrNull(idx + 2)?.let { add(toMedia(it)) }
            }
            if (nextTwo.isNotEmpty()) pendingPreloadItems.value = nextTwo
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

    val firstFilteredRow = filteredRows.firstOrNull()
    val firstRawRow = rows.firstOrNull()
    val featuredSlides = remember(firstFilteredRow, firstRawRow) {
        val primary = firstFilteredRow?.items.orEmpty()
        val fallback = firstRawRow?.items.orEmpty()
        (if (primary.isNotEmpty()) primary else fallback).take(8)
    }

    val hasContinueWatching = sortedContinueWatching.isNotEmpty()
    val hasAnyContentRow = hasContinueWatching || filteredRows.isNotEmpty()
    val expectedFirstRowTag = if (hasContinueWatching) "cw" else "cat"
    val firstRowAnchorKey = if (hasContinueWatching) {
        "cw_${sortedContinueWatching.firstOrNull()?.contentId}"
    } else {
        "cat_${filteredRows.firstOrNull()?.id}_${filteredRows.firstOrNull()?.items?.firstOrNull()?.id}"
    }
    LaunchedEffect(firstRowAnchorKey, hasAnyContentRow) {
        if (!hasAnyContentRow) {
            attachedFirstRowTag = null
        }
    }

    val onSelectCategory = remember(pendingHeroFocus) {
        { category: String ->
            selectedCategory = category
            pendingHeroFocus.value = null
            heroResetSignal += 1
        }
    }

    // Single 220ms dwell debounce + gentle auto-cycle driving BOTH AmbientBlurredBackdrop and
    // HomeHeroSection via activeHeroState (StateFlow) with zero recomposition of HomeScreen or rows.
    LaunchedEffect(featuredSlides, heroResetSignal) {
        if (featuredSlides.isEmpty()) {
            activeHeroState.value = null
            return@LaunchedEffect
        }
        pendingHeroFocus.collectLatest { pending ->
            if (pending != null) {
                delay(220L)
                activeHeroState.value = pending
            } else {
                var slideIdx = 0
                activeHeroState.value = featuredSlides[0] to 0
                if (featuredSlides.size > 1) {
                    while (true) {
                        delay(7500L)
                        slideIdx = (slideIdx + 1) % featuredSlides.size
                        activeHeroState.value = featuredSlides[slideIdx] to slideIdx
                    }
                }
            }
        }
    }

    // Transparent root: SoftGlowBackground shows through whenever there is no ambient artwork.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val heroHeight = when (LocalQuernLayout.current) {
            QuernLayoutClass.TV -> (maxHeight * 0.72f).coerceIn(340.dp, 540.dp)
            QuernLayoutClass.COMPACT -> (maxHeight * 0.57f).coerceIn(340.dp, 520.dp)
            QuernLayoutClass.WIDE -> (maxHeight * 0.48f).coerceIn(300.dp, 440.dp)
        }
        val heroAspectRatio = (maxWidth.value / heroHeight.value.coerceAtLeast(1f)).coerceIn(1.2f, 6.0f)

        val heroVisibleRef = remember { booleanArrayOf(true) }

        // Preload artwork for the next 2 cards in the focused row using the EXACT same transformed Coil
        // request keys as LumaHeroBanner/AmbientBlurredBackdrop (zero duplicate cache entries).
        // Always preloads the 48x27 ambient backdrop (5 KB, also warms DiskCache for the full backdrop URL),
        // and pre-transforms the 720x405 melted hero bitmap into MemoryCache whenever the Hero is visible
        // (avoids hoarding off-screen software bitmaps in RAM while browsing deep rows).
        LaunchedEffect(heroAspectRatio, isTv, isRtl, palette.background, palette.isGlossy, palette.isMonochrome) {
            val preloadedAmbient = LinkedHashSet<String>()
            val preloadedHero = LinkedHashSet<String>()
            pendingPreloadItems.collectLatest { nextItems ->
                if (nextItems.isEmpty()) return@collectLatest
                delay(160L)
                val loader = context.imageLoader
                val heroOnScreen = heroVisibleRef[0]
                nextItems.forEach { next ->
                    val url = next.backdropUrl ?: next.posterUrl
                    if (!url.isNullOrBlank()) {
                        if (preloadedAmbient.add(url)) {
                            if (preloadedAmbient.size > 48) {
                                preloadedAmbient.iterator().let { it.next(); it.remove() }
                            }
                            loader.enqueue(
                                buildAmbientBackdropRequest(
                                    context = context,
                                    imageUrl = url,
                                    palette = palette,
                                    crossfadeMs = 0
                                )
                            )
                        }
                        if (heroOnScreen && preloadedHero.add(url)) {
                            if (preloadedHero.size > 16) {
                                preloadedHero.iterator().let { it.next(); it.remove() }
                            }
                            loader.enqueue(
                                buildHeroBackdropRequest(
                                    context = context,
                                    imageUrl = url,
                                    heroAspectRatio = heroAspectRatio,
                                    isTv = isTv,
                                    isRtl = isRtl,
                                    crossfadeMs = 0
                                )
                            )
                        }
                    }
                }
            }
        }

        // Full-screen blurred artwork backdrop (48x27 Coil bilinear stretch) behind hero, rail, and rows.
        HomeAmbientBackdropLayer(activeHeroState = activeHeroState)

        if (isLoading && rows.isEmpty()) {
            HomeSkeletonScreen(
                isTv = isTv,
                heroHeight = heroHeight,
                cardSize = cardSize,
                cardShape = cardShape,
                cardCorner = cardCorner
            )
        } else if (rows.isEmpty() && emptyTvQr != null) {
            emptyTvQr()
        } else if (isTv && featuredSlides.isNotEmpty() && showHeroBanner) {
            // Borderless full-bleed TV layout:
            // 75% Hero scrolls naturally as the first item of the LazyColumn — Continue Watching peeks
            // at the bottom of the first screen, zero horizontal clip cut, zero Offscreen FBO overdraw,
            // and rows get the full screen height as you browse down.
            val tvListState = rememberLazyListState()
            val heroItemIndex = if (updateBannerRelease != null) 1 else 0
            var bannerStride by remember(heroItemIndex) { mutableIntStateOf(0) }
            LaunchedEffect(tvListState, heroItemIndex) {
                snapshotFlow { tvListState.layoutInfo }
                    .collect { layout ->
                        heroVisibleRef[0] = tvListState.firstVisibleItemIndex <= heroItemIndex
                        if (heroItemIndex != 0) {
                            layout.visibleItemsInfo.firstOrNull { it.index == 0 }?.let {
                                bannerStride = it.size + layout.mainAxisItemSpacing
                            }
                        }
                    }
            }
            LaunchedEffect(hasContinueWatching) {
                if (hasContinueWatching && tvListState.firstVisibleItemIndex <= 2) {
                    tvListState.scrollToItem(0)
                }
            }
            val rowHeaderPx = with(LocalDensity.current) {
                20.sp.toPx() + QuernHomeSpacing.titleToCardsGap(isTv = true).toPx()
            }
            val verticalFocusSpec = remember(tvListState, heroItemIndex, rowHeaderPx) {
                object : BringIntoViewSpec {
                    override val scrollAnimationSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )

                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                        val layout = tvListState.layoutInfo
                        val visibleItems = layout.visibleItemsInfo
                        val viewportStart = layout.viewportStartOffset
                        val target = visibleItems.firstOrNull { item ->
                            val top = item.offset - viewportStart
                            offset >= top && offset < top + item.size
                        }
                        val hero = visibleItems.firstOrNull { it.key == "hero" }
                        val heroFullyVisible = hero != null && hero.offset >= viewportStart &&
                            hero.offset + hero.size <= layout.viewportEndOffset
                        val first = visibleItems.firstOrNull { it.index == 0 }
                        val distance = if (target?.key == "hero" || target?.index == 0 ||
                            (target?.key == "continue_watching" && heroFullyVisible)
                        ) {
                            (first?.offset ?: hero?.let { it.offset - bannerStride } ?: viewportStart)
                                .minus(viewportStart).toFloat()
                        } else {
                            val rowTop = target?.let { (it.offset - viewportStart).toFloat() }
                                ?: (offset - rowHeaderPx)
                            rowTop - containerSize * 0.10f
                        }
                        val last = visibleItems.lastOrNull()
                        val maximum = if (last != null && last.index == layout.totalItemsCount - 1) {
                            (last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset)
                                .coerceAtLeast(0).toFloat()
                        } else Float.POSITIVE_INFINITY
                        val minimum = first?.let { (it.offset - viewportStart).coerceAtMost(0).toFloat() }
                            ?: Float.NEGATIVE_INFINITY
                        return distance.coerceIn(minimum, maximum)
                    }
                }
            }

            CompositionLocalProvider(LocalBringIntoViewSpec provides verticalFocusSpec) {
                LazyColumn(
                    state = tvListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(QuernHomeSpacing.rowVerticalGap)
                ) {
                    if (updateBannerRelease != null) {
                        item(key = "update_banner", contentType = "update_banner") {
                            HomeUpdateBanner(
                                release = updateBannerRelease,
                                isTv = true,
                                onOpenUpdate = onOpenUpdate,
                                onDismiss = onDismissUpdate
                            )
                        }
                    }

                    item(key = "hero", contentType = "hero") {
                        HomeHeroSection(
                            activeHeroState = activeHeroState,
                            totalSlides = featuredSlides.size,
                            heroHeight = heroHeight,
                            heroAspectRatio = heroAspectRatio,
                            isTv = true,
                            onSelect = onItemClick,
                            onResumeDirect = onResumeDirect,
                            continueWatchingByBaseId = continueWatchingByBaseId,
                            onResolveHeroMeta = onResolveHeroMeta,
                            onRefresh = onRefresh,
                            selectedCategory = selectedCategory,
                            onCategorySelected = onSelectCategory,
                            activeServiceBadge = activeServiceBadge,
                            firstRowFocusRequester = if (hasAnyContentRow) firstRowFocusRequester else null,
                            isFirstRowAttached = { attachedFirstRowTag == expectedFirstRowTag }
                        )
                    }

                    if (hasContinueWatching) {
                        item(key = "continue_watching", contentType = "continue_watching") {
                            ContinueWatchingRow(
                                items = sortedContinueWatching,
                                isTv = true,
                                onClick = onContinueClick,
                                onItemFocused = onContinueItemFocused,
                                firstItemFocusRequester = firstRowFocusRequester,
                                onFirstItemAttachedChanged = onCwFirstAttachedChanged
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
                            onPreloadNextItems = onPreloadNextItems,
                            firstItemFocusRequester = if (attachFirstRow) firstRowFocusRequester else null,
                            onFirstItemAttachedChanged = if (attachFirstRow) onCatFirstAttachedChanged else null
                        )
                    }
                }
            }
        } else {
            val phoneListState = rememberLazyListState()
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            HomeEdgeScrolling(isTv, 0.dp, 44.dp) {
                LazyColumn(
                    state = phoneListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = navBarBottom + 104.dp),
                    verticalArrangement = Arrangement.spacedBy(QuernHomeSpacing.rowVerticalGap)
                ) {
                    if (updateBannerRelease != null) {
                        item(key = "update_banner") {
                            HomeUpdateBanner(
                                release = updateBannerRelease,
                                isTv = isTv,
                                onOpenUpdate = onOpenUpdate,
                                onDismiss = onDismissUpdate
                            )
                        }
                    }
                    if (featuredSlides.isNotEmpty() && showHeroBanner) {
                        item(key = "hero") {
                            HomeHeroSection(
                                activeHeroState = activeHeroState,
                                totalSlides = featuredSlides.size,
                                heroHeight = heroHeight,
                                heroAspectRatio = heroAspectRatio,
                                isTv = isTv,
                                onSelect = onItemClick,
                                onResumeDirect = onResumeDirect,
                                continueWatchingByBaseId = continueWatchingByBaseId,
                                onResolveHeroMeta = onResolveHeroMeta,
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
                            EmptyHomeActions(
                                isTv = isTv,
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
                                cardSize = cardSize,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeUpdateBanner(
    release: AvailableUpdate,
    isTv: Boolean,
    onOpenUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    val isAr = LocalLayoutDirection.current == LayoutDirection.Rtl
    val parsedNotes = remember(release, isAr) { parseAvailableUpdateNotes(release, isAr) }
    var showWhatsNew by remember(release.version) { mutableStateOf(false) }
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

    @Composable
    fun BannerButtons() {
        var updateFocused by remember { mutableStateOf(false) }
        var whatsNewFocused by remember { mutableStateOf(false) }
        var laterFocused by remember { mutableStateOf(false) }
        val pillVertPad = if (isCompact) 9.dp else 5.dp
        val pillHeightMod = if (isCompact) Modifier.heightIn(min = 38.dp) else Modifier

        Surface(
            color = if (updateFocused) palette.focusRing else palette.accent,
            shape = RoundedCornerShape(999.dp),
            border = if (updateFocused) BorderStroke(1.5.dp, Color.White) else null,
            modifier = pillHeightMod
                .clip(RoundedCornerShape(999.dp))
                .tvClickable(
                    onFocusChangedState = { updateFocused = it },
                    onClick = onOpenUpdate
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = pillVertPad)) {
                Text(
                    text = qString(R.string.update_action_view),
                    color = if (updateFocused) Color.Black else palette.onAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (parsedNotes.isNotEmpty()) {
            Surface(
                color = if (whatsNewFocused) palette.accent else palette.surface,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    width = if (whatsNewFocused) 1.5.dp else 1.dp,
                    color = if (whatsNewFocused) palette.focusRing else palette.border
                ),
                modifier = pillHeightMod
                    .focusRequester(whatsNewFocus)
                    .clip(RoundedCornerShape(999.dp))
                    .tvClickable(
                        onFocusChangedState = { whatsNewFocused = it },
                        onClick = {
                            pendingWhatsNewFocusReturn = true
                            showWhatsNew = true
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = pillVertPad)) {
                    Text(
                        text = qString(R.string.update_action_whats_new),
                        color = if (whatsNewFocused) palette.onAccent else LumaTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Surface(
            color = if (laterFocused) palette.accent else palette.surface,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(
                width = if (laterFocused) 1.5.dp else 1.dp,
                color = if (laterFocused) palette.focusRing else palette.border
            ),
            modifier = pillHeightMod
                .clip(RoundedCornerShape(999.dp))
                .tvClickable(
                    onFocusChangedState = { laterFocused = it },
                    onClick = onDismiss
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = pillVertPad)) {
                Text(
                    text = qString(R.string.update_action_later),
                    color = if (laterFocused) palette.onAccent else LumaTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Surface(
        color = palette.surfaceElevated.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = QuernHomeSpacing.contentStartPadding(isTv, railInset),
                end = QuernHomeSpacing.contentEndPadding(isTv),
                top = if (isTv) 8.dp else 10.dp,
                bottom = 2.dp
            )
    ) {
        if (isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(palette.focusRing)
                    )
                    Text(
                        text = qString(R.string.update_banner_text, release.version),
                        color = LumaTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BannerButtons()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.focusRing)
                )
                Text(
                    text = qString(R.string.update_banner_text, release.version),
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 12.5.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                BannerButtons()
            }
        }
    }

    if (showWhatsNew && parsedNotes.isNotEmpty()) {
        WhatsNewDialog(
            release = release,
            sections = parsedNotes,
            isTv = isTv,
            updateLabel = qString(R.string.update_action_view),
            onUpdate = {
                pendingWhatsNewFocusReturn = false
                showWhatsNew = false
                onOpenUpdate()
            },
            onLater = {
                pendingWhatsNewFocusReturn = false
                showWhatsNew = false
                onDismiss()
            },
            onDismiss = { showWhatsNew = false }
        )
    }
}

@Composable
private fun HomeAmbientBackdropLayer(
    activeHeroState: StateFlow<Pair<MediaItem, Int>?>
) {
    val activePair by activeHeroState.collectAsState()
    val item = activePair?.first
    val imageUrl = item?.backdropUrl ?: item?.posterUrl
    AmbientBlurredBackdrop(imageUrl = imageUrl)
}

@Composable
private fun HomeHeroSection(
    activeHeroState: StateFlow<Pair<MediaItem, Int>?>,
    totalSlides: Int,
    heroHeight: Dp,
    heroAspectRatio: Float,
    isTv: Boolean,
    onSelect: (MediaItem) -> Unit,
    onResumeDirect: (WatchProgress) -> Unit,
    continueWatchingByBaseId: Map<String, WatchProgress>,
    onResolveHeroMeta: (suspend (MediaItem) -> MediaItem)?,
    onRefresh: () -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    activeServiceBadge: String?,
    firstRowFocusRequester: FocusRequester?,
    isFirstRowAttached: () -> Boolean,
    onHeroFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activePair by activeHeroState.collectAsState()
    val rawItem = activePair?.first ?: return
    val activeDotIndex = (activePair?.second ?: 0) % totalSlides.coerceAtLeast(1)

    // Enrich logo / runtime / genres asynchronously after a calm 380ms dwell so fast scrolling
    // never fires redundant metadata HTTP requests.
    var enrichedItem by remember(rawItem.id, rawItem.type) { mutableStateOf(rawItem) }
    LaunchedEffect(rawItem.id, rawItem.type, onResolveHeroMeta) {
        enrichedItem = rawItem
        if (onResolveHeroMeta != null && (rawItem.logoUrl.isNullOrBlank() || rawItem.runtime.isNullOrBlank())) {
            delay(380L)
            val resolved = runCatching { onResolveHeroMeta(rawItem) }.getOrNull()
            if (resolved != null && resolved.id == rawItem.id) {
                enrichedItem = resolved
            }
        }
    }

    val matchedProgress = remember(enrichedItem.id, enrichedItem.imdbId, continueWatchingByBaseId) {
        continueWatchingByBaseId[enrichedItem.id.substringBefore(":")]
            ?: enrichedItem.imdbId?.let { continueWatchingByBaseId[it] }
    }

    LumaHeroBanner(
        item = enrichedItem,
        isTv = isTv,
        onSelect = onSelect,
        onRefresh = onRefresh,
        selectedCategory = selectedCategory,
        onCategorySelected = onCategorySelected,
        activeServiceBadge = activeServiceBadge,
        firstRowFocusRequester = firstRowFocusRequester,
        isFirstRowAttached = isFirstRowAttached,
        totalSlides = totalSlides,
        activeSlideIndex = activeDotIndex,
        heroHeight = heroHeight,
        heroAspectRatio = heroAspectRatio,
        activeProgress = matchedProgress,
        onResumeDirect = onResumeDirect,
        onHeroFocused = onHeroFocused,
        modifier = modifier
    )
}

private val ContinueCardShape = RoundedCornerShape(10.dp)
private val ContinueOverlayScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.34f to Color.Transparent,
        0.68f to Color.Black.copy(alpha = 0.58f),
        1.00f to Color.Black.copy(alpha = 0.90f)
    )
)

@Composable
private fun ContinueWatchingRow(
    items: List<WatchProgress>,
    isTv: Boolean,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    onClick: (WatchProgress) -> Unit,
    onItemFocused: ((WatchProgress, Int) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemAttachedChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalQuernPalette.current
    val strings = LocalQuernStrings.current
    val isAr = strings.watchNow == "شاهد الآن"
    val nextUpLabel = if (isAr) "التالي" else "Next up"
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val startPad = QuernHomeSpacing.contentStartPadding(isTv, railInset)
    val endPad = QuernHomeSpacing.contentEndPadding(isTv)
    // True 16:9 landscape cards with title/episode/badge overlaid directly on the artwork (no text block below).
    val cwWidth = continueCardWidth(cardSize)
    val cwHeight = if (isTv) 110.dp else cwWidth * (9f / 16f)
    val cwDecodeW = remember(cwWidth, density.density) {
        with(density) { (cwWidth.toPx() * 1.1f).toInt().coerceIn(220, 480) }
    }
    val cwDecodeH = remember(cwHeight, density.density) {
        with(density) { (cwHeight.toPx() * 1.1f).toInt().coerceIn(124, 270) }
    }
    val focusBorderColor = if (palette.isGlossy) palette.focusRing else Color.White
    val focusGlowColor = palette.focusRing

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = endPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = if (isTv) 15.dp else 13.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(palette.focusRing)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = strings.continueWatching,
                color = LumaTextPrimary,
                fontSize = if (isTv) 16.sp else 14.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 4f))
            )
        }

        val cardGap = QuernHomeSpacing.cardHorizontalGap(LocalQuernLayout.current)
        HomeEdgeScrolling(isTv, startPad + cardGap, endPad + cardGap, horizontal = true) {
            LazyRow(
                contentPadding = PaddingValues(
                    start = startPad,
                    end = endPad,
                    top = QuernHomeSpacing.titleToCardsGap(isTv),
                    bottom = QuernHomeSpacing.rowCardsBottomPadding(isTv)
                ),
                horizontalArrangement = Arrangement.spacedBy(cardGap)
            ) {
                itemsIndexed(items, key = { _, it -> it.contentId }, contentType = { _, _ -> "cw_card" }) { index, progress ->
                    val isFirst = index == 0
                    if (isFirst && firstItemFocusRequester != null && onFirstItemAttachedChanged != null) {
                        DisposableEffect(progress.contentId) {
                            onDispose { onFirstItemAttachedChanged(false) }
                        }
                    }
                    var focused by remember { mutableStateOf(false) }
                    val focusProgress = animateFloatAsState(
                        targetValue = if (focused) 1f else 0f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "cwFocus"
                    )
                    val remainingMins = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L)
                    val timeLeftText = if (isAr) "متبقي ${remainingMins} د" else "${remainingMins}m left"
                    val subLabel = progress.episodeLabel?.takeIf { it.isNotBlank() } ?: nextUpLabel
                    val imgReq = remember(progress.backdropUrl, progress.posterUrl, cwDecodeW, cwDecodeH) {
                        val url = progress.backdropUrl ?: progress.posterUrl
                        if (url.isNullOrBlank()) null else {
                            ImageRequest.Builder(context).data(url).size(cwDecodeW, cwDecodeH).crossfade(false).build()
                        }
                    }

                    // Stable modifier chain (no conditional Modifier.border before tvClickable) so the
                    // remembered FocusRequester never detaches when focus leaves this card.
                    Box(
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
                            .width(cwWidth)
                            .aspectRatio(16f / 9f)
                            .zIndex(if (focused) 10f else 0f)
                            .graphicsLayer {
                                val s = 1f + 0.06f * focusProgress.value
                                scaleX = s
                                scaleY = s
                            }
                            .clip(ContinueCardShape)
                            .background(palette.glassElevated)
                            .drawWithCache {
                                val outline = ContinueCardShape.createOutline(size, layoutDirection, this)
                                val thick = 2.dp.toPx()
                                val glowWidth = 7.dp.toPx()
                                onDrawWithContent {
                                    drawContent()
                                    val p = focusProgress.value
                                    if (p > 0.01f) {
                                        drawOutline(
                                            outline = outline,
                                            color = focusGlowColor.copy(alpha = 0.28f * p),
                                            style = Stroke(width = glowWidth * p)
                                        )
                                        drawOutline(
                                            outline = outline,
                                            color = focusBorderColor.copy(alpha = p),
                                            style = Stroke(width = 2f * thick * p)
                                        )
                                    }
                                }
                            }
                            .tvClickable(
                                onFocusChangedState = { focused = it },
                                onFocused = { onItemFocused?.invoke(progress, index) },
                                onClick = { onClick(progress) }
                            )
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
                                .background(ContinueOverlayScrim)
                        )

                        if (remainingMins > 0) {
                            Text(
                                text = timeLeftText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(7.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 6.5.dp, vertical = 2.dp)
                            )
                        }

                        // Bottom-left overlay: episode label / "Next up" + title right above the progress bar
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
                        ) {
                            Text(
                                text = subLabel,
                                color = palette.focusRing.copy(alpha = 0.95f),
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = progress.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Thin accent progress bar pinned to the bottom edge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.progressFraction.coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(palette.focusRing)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Static low-cost skeleton layout matching the exact final sizes of the 75% Hero, 16:9 Continue
 * Watching row, and 2:3 poster rows while Home catalogs load (no continuous full-screen shimmer).
 */
@Composable
private fun HomeSkeletonScreen(
    isTv: Boolean,
    heroHeight: Dp,
    cardSize: CardSizePreset,
    cardShape: CardShapePreset,
    cardCorner: CardCornerPreset
) {
    val palette = LocalQuernPalette.current
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val startPad = QuernHomeSpacing.contentStartPadding(isTv, railInset)
    val endPad = QuernHomeSpacing.contentEndPadding(isTv)
    val cardGap = QuernHomeSpacing.cardHorizontalGap(LocalQuernLayout.current)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val baseWidth = QuernHomeSpacing.posterWidth(cardSize, LocalQuernLayout.current, screenWidthDp)
    val posterWidth = if (cardShape == CardShapePreset.LANDSCAPE) (baseWidth * 1.45f).toInt().dp else baseWidth.dp
    val posterCorner = remember(cardCorner) { RoundedCornerShape(cardCorner.radiusDp.dp) }
    val fill = palette.surfaceElevated.copy(alpha = 0.34f)
    val fillStrong = palette.surfaceElevated.copy(alpha = 0.48f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(top = 0.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(QuernHomeSpacing.rowVerticalGap)
    ) {
        // Hero skeleton matching heroHeight
        item(key = "sk_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = startPad, end = endPad, bottom = if (isTv) 26.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.width(if (isTv) 240.dp else 180.dp).height(if (isTv) 34.dp else 26.dp).clip(RoundedCornerShape(8.dp)).background(fillStrong))
                    Box(Modifier.width(if (isTv) 180.dp else 140.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                    Box(Modifier.width(if (isTv) 340.dp else 250.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(120.dp).height(34.dp).clip(RoundedCornerShape(999.dp)).background(fillStrong))
                }
            }
        }
        // Continue Watching 16:9 skeleton row
        item(key = "sk_cw") {
            val cwWidth = continueCardWidth(cardSize)
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = startPad).width(150.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(fillStrong))
                Spacer(Modifier.height(QuernHomeSpacing.titleToCardsGap(isTv)))
                Row(
                    modifier = Modifier.padding(
                        start = startPad,
                        end = endPad,
                        bottom = QuernHomeSpacing.rowCardsBottomPadding(isTv)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(cardGap)
                ) {
                    repeat(if (isTv) 5 else 3) {
                        Box(Modifier.width(cwWidth).aspectRatio(16f / 9f).clip(ContinueCardShape).background(fill))
                    }
                }
            }
        }
        // Poster skeleton rows matching exact cardWidth + aspectRatio
        items(2, key = { "sk_row_$it" }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = startPad).width(180.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(fillStrong))
                Spacer(Modifier.height(QuernHomeSpacing.titleToCardsGap(isTv)))
                Row(
                    modifier = Modifier.padding(
                        start = startPad,
                        end = endPad,
                        bottom = QuernHomeSpacing.rowCardsBottomPadding(isTv)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(cardGap)
                ) {
                    repeat(if (isTv) 9 else 4) {
                        Column(modifier = Modifier.width(posterWidth)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(cardShape.aspectRatio).clip(posterCorner).background(fill))
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Box(Modifier.fillMaxWidth(0.78f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(fill))
                                Box(Modifier.fillMaxWidth(0.28f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(fill))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun continueCardWidth(cardSize: CardSizePreset = CardSizePreset.STANDARD): Dp {
    val layout = LocalQuernLayout.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val sizeDelta = cardSize.phoneWidthDp - CardSizePreset.STANDARD.phoneWidthDp
    return when (layout) {
        QuernLayoutClass.TV -> 196.dp
        QuernLayoutClass.WIDE -> {
            val base = if (screenWidthDp >= 800) 216 else 160
            (base + sizeDelta * 1.4f).dp.coerceIn(144.dp, 280.dp)
        }
        QuernLayoutClass.COMPACT -> (screenWidthDp * 0.73f + sizeDelta).dp.coerceIn(210.dp, 340.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHomeActions(
    isTv: Boolean,
    onOpenAddons: () -> Unit,
    onOpenServers: () -> Unit,
    onRefresh: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var addBtnFocused by remember { mutableStateOf(false) }
    var settingsBtnFocused by remember { mutableStateOf(false) }
    var refreshFocused by remember { mutableStateOf(false) }

    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 22.dp, intensity = 0.7f)
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

        }
    }
}

@Composable
internal fun HomeEmptyTvQr(
    signedIn: Boolean,
    signInQr: @Composable (FocusRequester) -> Unit,
    addonQr: @Composable () -> Unit,
    onOpenAddons: () -> Unit
) {
    val entryRequester = remember(signedIn) { FocusRequester() }
    val focusMemory = LocalContentFocusMemory.current
    DisposableEffect(focusMemory, entryRequester) {
        val entry: () -> Boolean = {
            runCatching { entryRequester.requestFocus(); true }.getOrDefault(false)
        }
        focusMemory?.fallbackEntry = entry
        onDispose {
            if (focusMemory?.fallbackEntry === entry) focusMemory.fallbackEntry = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = LocalTvRailInset.current + 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (signedIn) {
                addonQr()
                SelectablePillOption(
                    label = qString(R.string.first_run_open_settings),
                    selected = false,
                    modifier = Modifier.focusRequester(entryRequester),
                    onClick = onOpenAddons
                )
            } else {
                signInQr(entryRequester)
                Text(
                    text = qString(R.string.home_qr_after_sign_in),
                    color = LumaTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    LaunchedEffect(entryRequester) {
        withFrameNanos { }
        entryRequester.requestFocus()
    }
}
