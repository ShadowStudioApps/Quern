package com.quern.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.quern.app.R
import com.quern.app.domain.model.Episode
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.StreamSource
import com.quern.app.ui.DetailUiState
import com.quern.app.ui.components.AmbientBlurredBackdrop
import com.quern.app.ui.components.HERO_DECODE_HEIGHT_PX
import com.quern.app.ui.components.HERO_DECODE_WIDTH_PX
import com.quern.app.ui.components.HeroMeltTransformation
import com.quern.app.ui.components.LumaBadgeChip
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaAmber
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFillStrong
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRose
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

private const val FILTER_ALL = "__ALL__"
private const val FILTER_4K = "__4K__"
private const val FILTER_CACHED = "__CACHED__"

@Composable
fun DetailScreen(
    state: DetailUiState,
    isFavorite: Boolean,
    isTv: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onPlayStream: (StreamSource) -> Unit
) {
    val palette = LocalQuernPalette.current
    val context = LocalContext.current
    val detail = state.detail
    if (detail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.focusRing)
        }
        return
    }

    val item = detail.item
    var showStreamsOverlay by remember(item.id) { mutableStateOf(false) }
    var streamFilter by remember(item.id) { mutableStateOf(FILTER_ALL) }

    BackHandler(enabled = showStreamsOverlay) {
        showStreamsOverlay = false
    }

    val providerTabs = remember(state.streams) {
        val providers = state.streams.map { it.providerName.trim() }.filter { it.isNotBlank() }.distinct()
        buildList {
            add(FILTER_ALL)
            addAll(providers)
            if (state.streams.any { it.title.contains("4K", true) || it.title.contains("2160", true) }) add(FILTER_4K)
            if (state.streams.any { it.isCached }) add(FILTER_CACHED)
        }
    }

    val filteredStreams = remember(state.streams, streamFilter) {
        when (streamFilter) {
            FILTER_ALL -> state.streams
            FILTER_4K -> state.streams.filter { s ->
                s.qualityBadges.any { it.contains("4K", true) } || s.title.contains("4K", true) || s.title.contains("2160", true)
            }
            FILTER_CACHED -> state.streams.filter { it.isCached }
            else -> state.streams.filter { it.providerName.equals(streamFilter, ignoreCase = true) }
        }
    }

    val artworkUrl = item.backdropUrl ?: item.posterUrl
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val detailMelt = remember(isTv, isRtl) {
        HeroMeltTransformation(
            aspectBucket = if (isTv) 26 else 16,
            fadeStartEdge = true,
            isRtl = isRtl,
            bottomFadeStart = 0.44f,
            startFadeEnd = 0.48f,
            bakeTextShadow = true,
            textShadowCenterY = 0.48f,
            textShadowRadiusFrac = if (isTv) 0.50f else 0.85f,
            bakeTopWhisper = false
        )
    }
    val backdropReq = remember(artworkUrl, detailMelt) {
        if (artworkUrl.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(HERO_DECODE_WIDTH_PX, HERO_DECODE_HEIGHT_PX)
                .precision(Precision.INEXACT)
                .transformations(detailMelt)
                .crossfade(true)
                .build()
        }
    }

    val formattedRuntime = remember(detail.runtime) {
        formatRuntimeReadable(detail.runtime)
    }

    // Transparent root: falls back to QuernApp's SoftGlowBackground when artworkUrl is null.
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred ambient title backdrop (48x27 bilinear stretch + dark veil + vignette) behind both stages.
        AmbientBlurredBackdrop(imageUrl = artworkUrl)

        AnimatedContent(
            targetState = showStreamsOverlay,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "detailStageAnim"
        ) { inStreamsStage ->
            if (inStreamsStage) {
                // =========================================================================
                // STAGE 2: SPLIT-SCREEN CINEMA STREAM SELECTOR (All • one tab per installed addon)
                // =========================================================================
                val firstStreamFocus = remember { FocusRequester() }
                val allFilterFocus = remember { FocusRequester() }

                LaunchedEffect(filteredStreams.isNotEmpty()) {
                    if (isTv) {
                        if (filteredStreams.isNotEmpty()) {
                            runCatching { firstStreamFocus.requestFocus() }
                        } else {
                            runCatching { allFilterFocus.requestFocus() }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.20f),
                                        0.45f to Color.Black.copy(alpha = 0.28f),
                                        1.0f to Color.Black.copy(alpha = 0.54f)
                                    )
                                )
                            )
                    )

                    if (isTv) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Column (37%): Dramatic Title + Metadata + Series Picker
                            Box(
                                modifier = Modifier
                                    .weight(0.37f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.TopStart),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DetailCircleButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = qString(R.string.detail_back),
                                        onClick = { showStreamsOverlay = false }
                                    )
                                    DetailCircleButton(
                                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = qString(R.string.detail_favorite),
                                        tintOverride = if (isFavorite) LumaRose else null,
                                        onClick = { onToggleFavorite(item) }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.title.uppercase(),
                                        color = Color.White,
                                        fontSize = 36.sp,
                                        lineHeight = 40.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        item.rating?.let {
                                            ImdbInlineBadge(it)
                                        }
                                        item.year?.let {
                                            Text("•  $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                        }
                                        formattedRuntime?.let {
                                            Text("•  $it", color = LumaGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    state.selectedEpisode?.let { ep ->
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "S${ep.seasonNumber} E${ep.episodeNumber} • ${ep.title}",
                                            color = palette.focusRing,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (detail.seasons.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(detail.seasons) { season ->
                                                DetailFilterPill(
                                                    label = "S$season",
                                                    selected = season == state.selectedSeason,
                                                    onClick = { onSelectSeason(season) }
                                                )
                                            }
                                        }
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            itemsIndexed(
                                                detail.episodesForSeason(state.selectedSeason),
                                                key = { idx, ep -> "${ep.id}_$idx" }
                                            ) { _, ep ->
                                                DetailFilterPill(
                                                    label = "E${ep.episodeNumber}",
                                                    selected = ep.id == state.selectedEpisode?.id,
                                                    onClick = { onSelectEpisode(ep) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Right Translucent Streams Panel (63%)
                            StreamsPanelCard(
                                providerTabs = providerTabs,
                                streamFilter = streamFilter,
                                onFilterSelected = { streamFilter = it },
                                filteredStreams = filteredStreams,
                                isLoadingStreams = state.isLoadingStreams,
                                resolvingStreamId = state.resolvingStreamId,
                                subtitlesCount = state.subtitles.size,
                                statusMessage = state.statusMessage,
                                allFilterFocus = allFilterFocus,
                                firstStreamFocus = firstStreamFocus,
                                onPlayStream = onPlayStream,
                                modifier = Modifier
                                    .weight(0.63f)
                                    .fillMaxHeight()
                                    .padding(top = 14.dp, bottom = 14.dp, end = 16.dp)
                            )
                        }
                    } else {
                        // Phone Stage 2: Full-screen streams sheet
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xEB080A0F))
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailCircleButton(Icons.AutoMirrored.Filled.ArrowBack, qString(R.string.detail_back), onClick = { showStreamsOverlay = false })
                                Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(10.dp))
                            StreamsPanelCard(
                                providerTabs = providerTabs,
                                streamFilter = streamFilter,
                                onFilterSelected = { streamFilter = it },
                                filteredStreams = filteredStreams,
                                isLoadingStreams = state.isLoadingStreams,
                                resolvingStreamId = state.resolvingStreamId,
                                subtitlesCount = state.subtitles.size,
                                statusMessage = state.statusMessage,
                                allFilterFocus = allFilterFocus,
                                firstStreamFocus = firstStreamFocus,
                                onPlayStream = onPlayStream,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // =========================================================================
                // STAGE 1: FULL-BLEED CINEMA DETAIL PAGE (Matches Images #2 & #3!)
                // Title + Metadata + Big White Play Pill + Cast Circles + Trailers + Movie Details
                // =========================================================================
                val playCtaFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (isTv) runCatching { playCtaFocus.requestFocus() }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Crisp upper hero artwork melting smoothly to transparent into the blurred
                    // ambient backdrop (both readability shadow and alpha melt baked once via
                    // HeroMeltTransformation — zero offscreen FBO and zero per-frame radial gradients).
                    if (backdropReq != null) {
                        AsyncImage(
                            model = backdropReq,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (isTv) 0.68f else 0.50f)
                                .align(Alignment.TopCenter)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isTv) 36.dp else 18.dp,
                            end = if (isTv) 36.dp else 18.dp,
                            top = if (isTv) 22.dp else 18.dp,
                            bottom = 72.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // Top Navigation Bar (Back + Favorite)
                        item(key = "top_nav") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DetailCircleButton(Icons.AutoMirrored.Filled.ArrowBack, qString(R.string.detail_back), onClick = onBack)
                                DetailCircleButton(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = qString(R.string.detail_favorite),
                                    tintOverride = if (isFavorite) LumaRose else null,
                                    onClick = { onToggleFavorite(item) }
                                )
                            }
                        }

                        // Hero Info + Big White Pill "▶ Play" CTA (Exact match to Image #3!)
                        item(key = "hero_info") {
                            Column(
                                modifier = Modifier.fillMaxWidth(if (isTv) 0.56f else 1f)
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = if (isTv) 40.sp else 30.sp,
                                    lineHeight = if (isTv) 44.sp else 34.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )

                                Spacer(Modifier.height(10.dp))

                                // Year • Runtime • IMDb badge
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item.year?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    formattedRuntime?.let {
                                        Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    item.rating?.takeIf { it.isNotBlank() }?.let {
                                        ImdbInlineBadge(it)
                                    }
                                }

                                // Genres line (e.g. Romance • Comedy)
                                val genreLine = if (item.genres.isNotEmpty()) {
                                    item.genres.take(4).joinToString(" • ")
                                } else {
                                    if (item.type == MediaType.SERIES) {
                                        qString(R.string.detail_series_origin, item.origin.displayName)
                                    } else {
                                        qString(R.string.detail_movie_origin, item.origin.displayName)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = genreLine,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                if (!item.overview.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = item.overview,
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.height(18.dp))

                                // Big White Pill "▶ Play" + Circular Action Button (matches Image #3!)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var playFocused by remember { mutableStateOf(false) }
                                    val hasResume = state.savedPositionMs > 5_000L &&
                                        (state.savedDurationMs <= 0L || state.savedPositionMs < (state.savedDurationMs * 0.95).toLong())
                                    val resumeFraction = if (hasResume && state.savedDurationMs > 0L) {
                                        (state.savedPositionMs.toFloat() / state.savedDurationMs.toFloat()).coerceIn(0.04f, 0.96f)
                                    } else 0f
                                    val ctaText = when {
                                        hasResume -> qString(R.string.detail_resume_time, formatTimeShort(state.savedPositionMs))
                                        state.streams.isNotEmpty() -> qString(R.string.detail_play_count, state.streams.size)
                                        else -> qString(R.string.detail_play)
                                    }
                                    Surface(
                                        color = if (playFocused) palette.accent else Color.White,
                                        shape = RoundedCornerShape(999.dp),
                                        border = if (playFocused) BorderStroke(2.5.dp, palette.focusRing) else null,
                                        modifier = Modifier
                                            .width(if (isTv) 268.dp else 220.dp)
                                            .height(46.dp)
                                            .focusRequester(playCtaFocus)
                                            .clip(RoundedCornerShape(999.dp))
                                            .tvClickable(
                                                onFocusChangedState = { playFocused = it },
                                                onClick = { showStreamsOverlay = true }
                                            )
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (playFocused) palette.onAccent else Color.Black,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = ctaText,
                                                    color = if (playFocused) palette.onAccent else Color.Black,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (resumeFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(
                                                            if (playFocused) palette.onAccent.copy(alpha = 0.2f)
                                                            else Color.Black.copy(alpha = 0.14f)
                                                        )
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(resumeFraction)
                                                            .fillMaxHeight()
                                                            .background(if (playFocused) palette.focusRing else palette.accent)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Circular "More / Sources" button matching Image #3
                                    DetailCircleButton(
                                        icon = Icons.Default.MoreHoriz,
                                        contentDescription = qString(R.string.detail_all_sources),
                                        onClick = { showStreamsOverlay = true }
                                    )
                                }
                            }
                        }

                        // Episodes Row (if Series)
                        if (detail.seasons.isNotEmpty()) {
                            item(key = "episodes_section") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(qString(R.string.detail_episodes), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(detail.seasons) { season ->
                                                DetailFilterPill(
                                                    label = qString(R.string.detail_season_n, season),
                                                    selected = season == state.selectedSeason,
                                                    onClick = { onSelectSeason(season) }
                                                )
                                            }
                                        }
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        itemsIndexed(
                                            detail.episodesForSeason(state.selectedSeason),
                                            key = { idx, ep -> "${ep.id}_$idx" }
                                        ) { _, ep ->
                                            val selected = ep.id == state.selectedEpisode?.id
                                            var focused by remember { mutableStateOf(false) }
                                            Column(
                                                modifier = Modifier
                                                    .width(if (isTv) 210.dp else 176.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (focused) palette.accent else Color(0xCC131720))
                                                    .border(
                                                        width = if (focused || selected) 2.dp else 1.dp,
                                                        color = if (focused) Color.White else if (selected) palette.focusRing else Color.White.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .tvClickable(
                                                        onFocusChangedState = { focused = it },
                                                        onClick = {
                                                            onSelectEpisode(ep)
                                                            showStreamsOverlay = true
                                                        }
                                                    )
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(106.dp)
                                                        .background(Color(0xFF151923))
                                                ) {
                                                    AsyncImage(
                                                        model = ep.thumbnailUrl ?: item.backdropUrl,
                                                        contentDescription = ep.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Surface(
                                                        color = Color.Black.copy(alpha = 0.72f),
                                                        shape = RoundedCornerShape(999.dp),
                                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                                    ) {
                                                        Text(
                                                            "E${ep.episodeNumber}",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = ep.title,
                                                    color = if (focused) palette.onAccent else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(9.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // CAST SECTION (Circular Monogram Avatars — Exact Match to Image #3!)
                        if (detail.cast.isNotEmpty()) {
                            item(key = "cast_section") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = qString(R.string.detail_cast),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(detail.cast.take(12)) { actor ->
                                            val initials = actor.trim()
                                                .split(" ")
                                                .filter { it.isNotBlank() }
                                                .take(2)
                                                .joinToString("") { it.take(1).uppercase() }
                                                .ifBlank { "?" }
                                            var castFocused by remember { mutableStateOf(false) }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .tvClickable(
                                                        onFocusChangedState = { castFocused = it },
                                                        onClick = {}
                                                    )
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(68.dp)
                                                        .clip(CircleShape)
                                                        .background(if (castFocused) palette.accent else Color(0xFF151A23))
                                                        .border(
                                                            width = if (castFocused) 2.5.dp else 1.dp,
                                                            color = if (castFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                                            shape = CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = initials,
                                                        color = if (castFocused) palette.onAccent else Color(0xFFD0D5DD),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(Modifier.height(7.dp))
                                                Text(
                                                    text = actor,
                                                    color = if (castFocused) Color.White else Color.White.copy(alpha = 0.85f),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TRAILERS SECTION (Exact Match to Image #2!)
                        item(key = "trailers_section") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(qString(R.string.detail_trailers), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Surface(
                                        color = Color.White.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = qString(R.string.detail_trailer_chip),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                var trailerFocused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .width(214.dp)
                                        .tvClickable(
                                            onFocusChangedState = { trailerFocused = it },
                                            onClick = {
                                                val targetUrl = detail.trailerUrl?.takeIf { it.isNotBlank() }
                                                    ?: "https://www.youtube.com/results?search_query=${Uri.encode("${item.title} ${item.year.orEmpty()} official trailer")}"
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF151923))
                                            .border(
                                                width = if (trailerFocused) 2.5.dp else 1.dp,
                                                color = if (trailerFocused) Color.White else Color.White.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                    ) {
                                        if (backdropReq != null) {
                                            AsyncImage(
                                                model = backdropReq,
                                                contentDescription = qString(R.string.detail_official_trailer),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                                                )
                                        )
                                        Surface(
                                            color = if (trailerFocused) palette.accent else Color.Black.copy(alpha = 0.65f),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .align(Alignment.Center)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                        Text(
                                            text = qString(R.string.detail_official_trailer),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(10.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = qString(R.string.detail_trailer_chip),
                                        color = if (trailerFocused) Color.White else Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // MOVIE / SERIES DETAILS TABLE (Exact Match to Image #2!)
                        item(key = "movie_details_table") {
                            Column(
                                modifier = Modifier.fillMaxWidth(if (isTv) 0.56f else 1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (item.type == MediaType.SERIES) qString(R.string.detail_series_details) else qString(R.string.detail_movie_details),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))

                                val labelRelease = qString(R.string.detail_release_info)
                                val labelRuntime = qString(R.string.detail_runtime)
                                val labelGenres = qString(R.string.detail_genres)
                                val labelDirector = qString(R.string.detail_director)
                                val labelSources = qString(R.string.detail_available_sources)
                                val labelSubtitles = qString(R.string.detail_subtitles)
                                val valSources = if (state.isLoadingStreams) {
                                    qString(R.string.detail_resolving)
                                } else {
                                    qString(R.string.detail_streams_count, state.streams.size)
                                }
                                val valSubtitles = qString(R.string.detail_subtitles_languages, state.subtitles.size)

                                val rows = buildList {
                                    item.year?.takeIf { it.isNotBlank() }?.let { add(labelRelease to it) }
                                    formattedRuntime?.let { add(labelRuntime to it) }
                                    if (item.genres.isNotEmpty()) add(labelGenres to item.genres.joinToString(", "))
                                    if (detail.director.isNotEmpty()) add(labelDirector to detail.director.joinToString(", "))
                                    add(labelSources to valSources)
                                    if (state.subtitles.isNotEmpty()) add(labelSubtitles to valSubtitles)
                                }

                                rows.forEachIndexed { idx, (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(145.dp)
                                        )
                                        Text(
                                            text = value,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImdbInlineBadge(rating: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            color = Color(0xFFF5C518),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "IMDb",
                color = Color.Black,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
            )
        }
        Text(
            text = rating,
            color = Color(0xFFF5C518),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StreamsPanelCard(
    providerTabs: List<String>,
    streamFilter: String,
    onFilterSelected: (String) -> Unit,
    filteredStreams: List<StreamSource>,
    isLoadingStreams: Boolean,
    resolvingStreamId: String?,
    subtitlesCount: Int,
    statusMessage: String?,
    allFilterFocus: FocusRequester,
    firstStreamFocus: FocusRequester,
    onPlayStream: (StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    Surface(
        color = Color(0xDF0A0C10),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            val labelAll = qString(R.string.detail_filter_all)
            val labelCached = qString(R.string.detail_filter_cached)
            fun filterDisplayLabel(tab: String): String = when (tab) {
                FILTER_ALL -> labelAll
                FILTER_4K -> "4K"
                FILTER_CACHED -> labelCached
                else -> tab
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(providerTabs) { tab ->
                    DetailFilterPill(
                        label = filterDisplayLabel(tab),
                        selected = tab == streamFilter,
                        modifier = if (tab == FILTER_ALL) Modifier.focusRequester(allFilterFocus) else Modifier,
                        onClick = { onFilterSelected(tab) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (streamFilter == FILTER_ALL) {
                            qString(R.string.detail_active_sources_count, filteredStreams.size)
                        } else {
                            qString(R.string.detail_filtered_sources_count, filterDisplayLabel(streamFilter), filteredStreams.size)
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isLoadingStreams && filteredStreams.isNotEmpty()) {
                        CircularProgressIndicator(
                            color = palette.focusRing,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp
                        )
                    }
                }
                if (subtitlesCount > 0) {
                    Text(
                        text = qString(R.string.detail_subtitles_ready, subtitlesCount),
                        color = LumaGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoadingStreams && filteredStreams.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = palette.focusRing, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(qString(R.string.detail_fetching_streams), color = LumaTextSecondary, fontSize = 13.sp)
                    }
                }
            } else if (filteredStreams.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusMessage ?: qString(R.string.detail_no_matching_streams),
                        color = LumaTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredStreams, key = { idx, s -> "${s.id}_$idx" }) { index, stream ->
                        StreamSourceCard(
                            stream = stream,
                            isResolving = resolvingStreamId == stream.id,
                            modifier = if (index == 0) Modifier.focusRequester(firstStreamFocus) else Modifier,
                            onClick = { onPlayStream(stream) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tintOverride: Color? = null,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = if (focused) palette.accent else LumaGlassFillStrong,
        shape = CircleShape,
        border = BorderStroke(if (focused) 2.5.dp else 1.dp, if (focused) palette.focusRing else LumaGlassBorder),
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (focused) palette.onAccent else (tintOverride ?: Color.White),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun DetailFilterPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        selected -> palette.accent.copy(alpha = 0.78f)
        else -> Color(0xFF191D26)
    }
    val fg = if (focused || selected) palette.onAccent else Color.White

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamSourceCard(
    stream: StreamSource,
    isResolving: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    Surface(
        color = if (focused) Color(0xFF1C2330) else Color(0xFF13151B),
        shape = RoundedCornerShape(14.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .tvClickable(
                enabled = !isResolving,
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.title,
                    color = if (focused) palette.focusRing else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!stream.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stream.subtitle,
                        color = Color(0xFFB8C0CC),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (stream.qualityBadges.isNotEmpty() || stream.isCached || stream.needsDebridResolution) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        stream.qualityBadges.take(6).forEach { badge ->
                            Surface(
                                color = if (badge.startsWith("⚡")) Color(0xFF0E2920) else Color(0xFF0B0E14),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (badge.startsWith("⚡")) LumaGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f)
                                )
                            ) {
                                Text(
                                    text = badge,
                                    color = if (badge.startsWith("⚡")) LumaGreen else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (stream.needsDebridResolution && !stream.isCached) {
                            LumaBadgeChip(qString(R.string.detail_resolve_via_debrid), LumaAmber)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.width(78.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isResolving) {
                    CircularProgressIndicator(color = palette.focusRing, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (focused) palette.accent else Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (stream.isCached) Icons.Default.Bolt else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (focused) palette.onAccent else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stream.providerName,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatRuntimeReadable(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val mins = Regex("\\d+").find(raw)?.value?.toIntOrNull() ?: return raw
    return if (mins >= 60) {
        val h = mins / 60
        val m = mins % 60
        if (m > 0) "${h}h ${m}m" else "${h}h"
    } else {
        "${mins}m"
    }
}

private fun formatTimeShort(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", m, s)
    }
}
