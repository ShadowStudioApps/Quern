package com.quern.app.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.memory.MemoryCache
import com.quern.app.domain.model.CardCornerPreset
import com.quern.app.domain.model.CardShapePreset
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaAmber
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFill
import com.quern.app.ui.theme.LumaGlassFillStrong
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import kotlin.math.abs

/**
 * TV-safe single-node focus + click modifier.
 * IMPORTANT: Never combine .focusable() with .clickable() in Jetpack Compose —
 * clickable() already registers a focus target that handles both D-Pad focus and OK/Enter!
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onFocusChangedState: (Boolean) -> Unit = {},
    onFocused: () -> Unit = {},
    onClick: () -> Unit
): Modifier = this
    .onFocusChanged { state ->
        val focused = state.isFocused
        onFocusChangedState(focused)
        if (focused) onFocused()
    }
    .then(RememberLastFocusedElement)
    .clickable(enabled = enabled, onClick = onClick)

/**
 * Remembers the last focused tvClickable inside a content area so focus coming back
 * from the navigation rail (DPAD_RIGHT) lands on that exact item instead of the
 * geometrically nearest one. Provided only around screen content, never around the rail.
 */
class ContentFocusMemory {
    internal var last: RememberLastFocusedNode? = null

    /** Requests focus on the remembered item. Returns false if there is nothing to restore. */
    fun restore(): Boolean {
        val node = last ?: return false
        if (!node.isAttached) {
            last = null
            return false
        }
        return node.requestFocus()
    }
}

val LocalContentFocusMemory = staticCompositionLocalOf<ContentFocusMemory?> { null }

private object RememberLastFocusedElement : ModifierNodeElement<RememberLastFocusedNode>() {
    override fun create() = RememberLastFocusedNode()
    override fun update(node: RememberLastFocusedNode) = Unit
    override fun hashCode(): Int = "RememberLastFocused".hashCode()
    override fun equals(other: Any?): Boolean = other === this
}

internal class RememberLastFocusedNode :
    Modifier.Node(),
    FocusEventModifierNode,
    FocusRequesterModifierNode,
    CompositionLocalConsumerModifierNode {

    private var memory: ContentFocusMemory? = null

    override fun onFocusEvent(focusState: FocusState) {
        if (!focusState.isFocused) return
        val m = currentValueOf(LocalContentFocusMemory) ?: return
        memory = m
        m.last = this
    }

    override fun onDetach() {
        memory?.let { if (it.last === this) it.last = null }
        memory = null
    }
}

@Composable
fun rememberIsTvMode(override: UiModeOverride): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(override, configuration.uiMode, configuration.screenWidthDp) {
        when (override) {
            UiModeOverride.TV -> true
            UiModeOverride.PHONE -> false
            UiModeOverride.AUTO -> {
                val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                    (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
            }
        }
    }
}

@Composable
fun QuernBrandMark(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.16f
        val radius = (this.size.minDimension - stroke) * 0.44f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            color = palette.accent,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = palette.focusRing,
            start = Offset(center.x + radius * 0.38f, center.y + radius * 0.38f),
            end = Offset(center.x + radius * 0.96f, center.y + radius * 0.96f),
            strokeWidth = stroke * 0.95f,
            cap = StrokeCap.Round
        )
        val tri = Path().apply {
            val r = radius * 0.42f
            moveTo(center.x - r * 0.45f, center.y - r * 0.65f)
            lineTo(center.x + r * 0.78f, center.y)
            lineTo(center.x - r * 0.45f, center.y + r * 0.65f)
            close()
        }
        drawPath(tri, color = Color.White)
    }
}

@Composable
fun LumaBadgeChip(
    text: String,
    accentColor: Color = LocalQuernPalette.current.accent,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor.copy(alpha = 0.22f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun LumaHeroBanner(
    item: MediaItem,
    isTv: Boolean,
    onSelect: (MediaItem) -> Unit,
    onRefresh: (() -> Unit)? = null,
    selectedCategory: String = "All",
    onCategorySelected: ((String) -> Unit)? = null,
    activeServiceBadge: String? = null,
    firstRowFocusRequester: FocusRequester? = null,
    isFirstRowAttached: () -> Boolean = { false },
    totalSlides: Int = 1,
    activeSlideIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val strings = LocalQuernStrings.current
    val heroHeight = if (isTv) 262.dp else 252.dp
    val viewDetailsFocus = remember { FocusRequester() }
    val isAr = strings.watchNow == "شاهد الآن"
    val viewDetailsLabel = if (isAr) "عرض التفاصيل" else "View Details"
    val typeLabel = when (item.type) {
        com.quern.app.domain.model.MediaType.SERIES -> if (isAr) "مسلسل" else "Series"
        com.quern.app.domain.model.MediaType.MOVIE -> if (isAr) "فيلم" else "Movie"
        else -> item.type.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    val categories = listOf(
        "All" to strings.all,
        "Movies" to strings.movies,
        "Series" to strings.series,
        "Servers" to strings.servers
    )

    // Memory-cache key of the backdrop currently on screen. The next request uses it as its
    // placeholder, so Coil's single crossfade goes old image -> new image (no blank frame, no
    // second Compose Crossfade layer). Plain holder: updating it must not recompose.
    val shownBackdropKey = remember { arrayOfNulls<MemoryCache.Key>(1) }
    val imageModel = remember(item.backdropUrl, item.posterUrl) {
        val url = item.backdropUrl ?: item.posterUrl
        if (url.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(url)
                .size(960, 540)
                .placeholderMemoryCacheKey(shownBackdropKey[0])
                .crossfade(260)
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(if (isTv) 20.dp else 0.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.accent.copy(alpha = 0.24f), palette.surface, palette.background)
                )
            )
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                onSuccess = { shownBackdropKey[0] = it.result.memoryCacheKey },
                modifier = Modifier.fillMaxSize()
            )
        }

        // One scrim layer: both gradients built once per size, drawn in a single pass
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val vertical = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to palette.background.copy(alpha = 0.42f),
                            0.30f to Color.Transparent,
                            0.72f to palette.background.copy(alpha = 0.65f),
                            1.0f to palette.background
                        )
                    )
                    val horizontal = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to palette.background.copy(alpha = 0.88f),
                            0.38f to palette.background.copy(alpha = 0.48f),
                            0.68f to Color.Transparent,
                            1.0f to Color.Transparent
                        )
                    )
                    onDrawBehind {
                        drawRect(vertical)
                        drawRect(horizontal)
                    }
                }
        )

        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = if (isTv) 14.dp else 12.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!isTv) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuernBrandMark(size = 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "QUERN",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        categories.forEach { (key, label) ->
                            GlassCategoryPill(
                                label = label,
                                selected = key == selectedCategory,
                                modifier = Modifier.focusProperties { down = viewDetailsFocus },
                                onClick = { onCategorySelected?.invoke(key) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!activeServiceBadge.isNullOrBlank()) {
                        Surface(
                            color = LumaGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, LumaGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, null, tint = LumaGreen, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(activeServiceBadge, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (onRefresh != null) {
                        var refFocused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (refFocused) palette.accent else LumaGlassFillStrong,
                            shape = CircleShape,
                            border = BorderStroke(
                                width = if (refFocused) 2.dp else 1.dp,
                                color = if (refFocused) palette.focusRing else LumaGlassBorder
                            ),
                            modifier = Modifier
                                .size(30.dp)
                                .focusProperties { down = viewDetailsFocus }
                                .clip(CircleShape)
                                .tvClickable(
                                    onFocusChangedState = { refFocused = it },
                                    onClick = onRefresh
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = if (refFocused) palette.onAccent else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isTv && onCategorySelected != null) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    categories.forEach { (key, label) ->
                        GlassCategoryPill(
                            label = label,
                            selected = key == selectedCategory,
                            onClick = { onCategorySelected(key) }
                        )
                    }
                }
            }
        }

        // Bottom Spotlight Info (Matches reference: Title, Type • Year, IMDb badge, Synopsis, View Details pill)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = 14.dp)
                .fillMaxWidth(if (isTv) 0.54f else 0.92f)
        ) {
            Text(
                text = item.title,
                color = LumaTextPrimary,
                fontSize = if (isTv) 24.sp else 20.sp,
                lineHeight = if (isTv) 27.sp else 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))

            // Type • Year line (e.g. Series • 2026)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                item.year?.takeIf { it.isNotBlank() }?.let { yr ->
                    Text("•", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
                    Text(
                        text = yr,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Authentic Yellow IMDb Badge + Rating
            item.rating?.takeIf { it.isNotBlank() }?.let { ratingVal ->
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        text = ratingVal,
                        color = Color(0xFFF5C518),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!item.overview.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.overview,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 11.sp,
                    lineHeight = 15.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(11.dp))

            // Sleek White Pill "View Details" CTA
            var detailsFocused by remember { mutableStateOf(false) }
            Surface(
                color = if (detailsFocused) palette.accent else Color.White,
                shape = RoundedCornerShape(999.dp),
                border = if (detailsFocused) BorderStroke(2.dp, palette.focusRing) else null,
                modifier = Modifier
                    .focusRequester(viewDetailsFocus)
                    .focusProperties {
                        // Read at focus-search time, so attaching/detaching the first row never recomposes the hero
                        down = if (firstRowFocusRequester != null && isFirstRowAttached()) {
                            firstRowFocusRequester
                        } else {
                            FocusRequester.Default
                        }
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .tvClickable(
                        onFocusChangedState = { detailsFocused = it },
                        onClick = { onSelect(item) }
                    )
            ) {
                Text(
                    text = viewDetailsLabel,
                    color = if (detailsFocused) palette.onAccent else Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom-Right Carousel Indicator Dots (━━ • • • • • • •)
        if (totalSlides > 1) {
            val dotCount = totalSlides.coerceAtMost(8)
            val currentDot = activeSlideIndex.coerceIn(0, dotCount - 1)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(dotCount) { idx ->
                    val active = idx == currentDot
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 20.dp else 5.dp,
                        animationSpec = tween(180),
                        label = "heroDotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (active) Color.White else Color.White.copy(alpha = 0.42f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    val bg = when {
        focused -> palette.accent
        selected -> Color.White
        else -> LumaGlassFill
    }
    val textColor = when {
        focused -> palette.onAccent
        selected -> Color.Black
        else -> Color.White
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) palette.focusRing else if (selected) Color.White else LumaGlassBorder
        ),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun LumaMediaCarouselRow(
    row: CatalogRow,
    isTv: Boolean,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    cardShape: CardShapePreset = CardShapePreset.PORTRAIT,
    cardCorner: CardCornerPreset = CardCornerPreset.ROUNDED,
    showPosterLabels: Boolean = true,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: ((MediaItem) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemAttachedChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTv) 14.dp else 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(width = 2.5.dp, height = 12.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(palette.focusRing)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.title,
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 12.5.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.origin.displayName.uppercase(),
                color = palette.focusRing,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (isTv) 14.dp else 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp)
        ) {
            items(
                items = row.items,
                key = { "${row.id}_${it.id}" },
                contentType = { "poster" }
            ) { item ->
                val isFirst = item.id == row.items.firstOrNull()?.id
                if (isFirst && firstItemFocusRequester != null && onFirstItemAttachedChanged != null) {
                    DisposableEffect(row.id, item.id) {
                        onDispose { onFirstItemAttachedChanged(false) }
                    }
                }
                LumaPosterCard(
                    item = item,
                    isTv = isTv,
                    cardSize = cardSize,
                    cardShape = cardShape,
                    cardCorner = cardCorner,
                    showPosterLabels = showPosterLabels,
                    onClick = { onItemClick(item) },
                    onFocused = { onItemFocused?.invoke(item) },
                    modifier = if (isFirst && firstItemFocusRequester != null) {
                        Modifier
                            .focusRequester(firstItemFocusRequester)
                            .onGloballyPositioned { coords ->
                                onFirstItemAttachedChanged?.invoke(coords.isAttached)
                            }
                    } else Modifier
                )
            }
        }
    }
}

@Composable
fun LumaPosterCard(
    item: MediaItem,
    isTv: Boolean,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    cardShape: CardShapePreset = CardShapePreset.PORTRAIT,
    cardCorner: CardCornerPreset = CardCornerPreset.ROUNDED,
    showPosterLabels: Boolean = true,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    var isFocused by remember { mutableStateOf(false) }

    // One short tween drives both scale and border. Read only inside graphicsLayer/draw lambdas,
    // so the animation re-draws the card without recomposing it on every frame.
    val focusProgress = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(120),
        label = "posterFocus"
    )
    val idleBorder = palette.border
    val baseWidth = if (isTv) cardSize.tvWidthDp else cardSize.phoneWidthDp
    val cardWidth = if (cardShape == CardShapePreset.LANDSCAPE) (baseWidth * 1.45f).toInt().dp else baseWidth.dp
    val cornerShape = RoundedCornerShape(cardCorner.radiusDp.dp)

    val imageModel = remember(item.posterUrl, item.backdropUrl, cardShape) {
        val url = if (cardShape == CardShapePreset.LANDSCAPE) (item.backdropUrl ?: item.posterUrl) else item.posterUrl
        if (url.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(url)
                .size(if (cardShape == CardShapePreset.LANDSCAPE) 420 else 260, if (cardShape == CardShapePreset.LANDSCAPE) 236 else 390)
                .crossfade(false)
                // No allowRgb565 here: Coil only applies it to ARGB_8888 decodes, which means turning off
                // hardware bitmaps. Measured on the TV, that cost +24 MB PSS and more jank (STEP 5).
                .build()
        }
    }

    val fallbackBg = remember(item.id) {
        val colors = listOf(Color(0xFF151C28), Color(0xFF1A222E), Color(0xFF171922), Color(0xFF121821))
        colors[abs(item.title.hashCode()) % colors.size]
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                val s = 1f + 0.13f * focusProgress.value
                scaleX = s
                scaleY = s
            }
            .tvClickable(
                onFocusChangedState = { isFocused = it },
                onFocused = { onFocused?.invoke() },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardShape.aspectRatio)
                .clip(cornerShape)
                .background(fallbackBg)
                .drawWithCache {
                    val outline = cornerShape.createOutline(size, layoutDirection, this)
                    val thin = 1.dp.toPx()
                    val thick = 3.dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        val p = focusProgress.value
                        // Stroke is centred on the edge and the shape clip hides the outer half,
                        // so double the width to keep the same inside border as Modifier.border.
                        drawOutline(
                            outline = outline,
                            color = lerp(idleBorder, Color.White, p),
                            style = Stroke(width = 2f * (thin + (thick - thin) * p))
                        )
                    }
                }
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = item.origin.displayName.uppercase(),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
            }

            if (!item.rating.isNullOrBlank()) {
                // Plain Row + background: no Surface (no elevation/semantics/content-color providers)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = LumaAmber, modifier = Modifier.size(8.dp))
                    Spacer(Modifier.width(1.5.dp))
                    Text(item.rating, color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showPosterLabels) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = item.title.trim(),
                    color = if (isFocused) palette.focusRing else LumaTextPrimary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.year?.takeIf { it.isNotBlank() }?.let { yr ->
                    Text(
                        text = yr.trim(),
                        color = LumaTextSecondary,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
