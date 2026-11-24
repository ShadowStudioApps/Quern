package com.quern.app.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.memory.MemoryCache
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.quern.app.domain.model.CardCornerPreset
import com.quern.app.domain.model.CardShapePreset
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.domain.model.CatalogRow
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.domain.model.WatchProgress
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LocalQuernScaledDensity
import com.quern.app.ui.theme.LocalQuernStrings
import com.quern.app.ui.theme.LumaAmber
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFill
import com.quern.app.ui.theme.LumaGlassFillStrong
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.glossySheen
import kotlin.math.abs

private val PillShape = RoundedCornerShape(999.dp)

/**
 * High-contrast, theme-aware Switch colors for both ON and OFF states.
 * Explicitly sets unchecked thumb, track, and subtle border so OFF switches never disappear
 * when the theme's Material3 outline color is transparent, and stays crisp across light and dark accents.
 */
@Composable
fun quernSwitchColors(onAccentSurface: Boolean = false): SwitchColors {
    val palette = LocalQuernPalette.current
    val isLightAccent = palette.onAccent != Color.White
    return SwitchDefaults.colors(
        checkedThumbColor = when {
            onAccentSurface && isLightAccent -> Color.White
            onAccentSurface -> palette.accent
            else -> palette.onAccent
        },
        checkedTrackColor = when {
            onAccentSurface && isLightAccent -> LumaGreen
            onAccentSurface -> Color.White
            else -> palette.accent
        },
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color(0xFFCBD5E1),
        uncheckedTrackColor = if (onAccentSurface) Color(0xFF0F131A).copy(alpha = 0.78f) else Color(0xFF1D232E),
        uncheckedBorderColor = if (onAccentSurface && isLightAccent) {
            Color(0xFF0F131A).copy(alpha = 0.45f)
        } else {
            Color(0xFF4B5568)
        }
    )
}

/**
 * TV-safe single-node focus + click modifier.
 * IMPORTANT: Never combine .focusable() with .clickable() in Jetpack Compose —
 * clickable() already registers a focus target that handles both D-Pad focus and OK/Enter!
 * Uses indication = null so Material3's default rectangular focus overlay/ripple node
 * isn't allocated or drawn behind custom TV focus cards.
 *
 * When rendered inside [LocalContentFocusMemory], automatically registers a remembered
 * [FocusRequester] and records it whenever this item gains focus so returning RIGHT (LEFT in RTL)
 * from the floating TV navigation rail restores this exact item reliably.
 */
@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onFocusChangedState: (Boolean) -> Unit = {},
    onFocused: () -> Unit = {},
    onClick: () -> Unit
): Modifier {
    val memory = LocalContentFocusMemory.current
    val rememberRequester = if (memory != null) remember { FocusRequester() } else null
    if (memory != null && rememberRequester != null) {
        DisposableEffect(memory, rememberRequester) {
            onDispose { memory.clear(rememberRequester) }
        }
    }
    return this
        .then(if (rememberRequester != null) Modifier.focusRequester(rememberRequester) else Modifier)
        .onFocusChanged { state ->
            val focused = state.isFocused
            if (focused && memory != null && rememberRequester != null) {
                memory.record(rememberRequester)
            }
            onFocusChangedState(focused)
            if (focused) onFocused()
        }
        .clickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Remembers the last focused tvClickable inside a content area via its real [FocusRequester]
 * so focus coming back from the navigation rail (DPAD_RIGHT / DPAD_LEFT in RTL) always lands on
 * that exact item instead of a stale node or the geometrically nearest card.
 * Provided only around screen content, never around the rail.
 */
class ContentFocusMemory {
    private var lastRequester: FocusRequester? = null
    internal var fallbackEntry: (() -> Boolean)? = null

    fun record(requester: FocusRequester) {
        lastRequester = requester
    }

    fun clear(requester: FocusRequester) {
        if (lastRequester === requester) lastRequester = null
    }

    /** Requests focus on the remembered item, or a screen-registered fallback entry (e.g. Hero CTA). */
    fun restore(): Boolean {
        val req = lastRequester
        if (req != null) {
            val ok = runCatching { req.requestFocus(); true }.getOrDefault(false)
            if (ok) return true
            lastRequester = null
        }
        return fallbackEntry?.invoke() == true
    }
}

val LocalContentFocusMemory = staticCompositionLocalOf<ContentFocusMemory?> { null }

/**
 * Horizontal inset reserved for the floating TV navigation rail so full-bleed backgrounds
 * (like the Home hero backdrop and blurred theme glows) extend edge-to-edge behind the rail
 * while text and rows align cleanly to its right with zero visual separation.
 */
val LocalTvRailInset = staticCompositionLocalOf { 0.dp }

/**
 * Shared Home layout spacing constants used by the hero, Continue Watching, and every catalog
 * row so horizontal card gaps, title-to-card gaps, inter-row vertical gaps, and left/right edges
 * stay uniform across all rows at both 100% and smaller Interface Size scales.
 */
object QuernHomeSpacing {
    fun contentStartPadding(isTv: Boolean, railInset: Dp): Dp =
        if (isTv) railInset + 18.dp else 14.dp

    fun contentEndPadding(isTv: Boolean): Dp =
        if (isTv) 24.dp else 14.dp

    /** Uniform horizontal gap between sibling cards in Continue Watching and poster rows. */
    fun cardHorizontalGap(isTv: Boolean): Dp =
        if (isTv) 15.dp else 10.dp

    /** Uniform vertical gap between a row header title and its cards (leaves room for 6% focus lift). */
    fun titleToCardsGap(isTv: Boolean): Dp =
        if (isTv) 10.dp else 8.dp

    /** Uniform bottom clearance inside each LazyRow so focus scale/border never clips vertically. */
    fun rowCardsBottomPadding(isTv: Boolean): Dp =
        if (isTv) 8.dp else 6.dp

    /** Uniform vertical gap between rows in the Home LazyColumn. */
    fun rowVerticalGap(isTv: Boolean): Dp =
        if (isTv) 14.dp else 12.dp
}

fun isTvHardware(
    context: Context,
    configuration: Configuration = context.resources.configuration
): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun rememberIsTvMode(override: UiModeOverride): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(override, configuration.uiMode, configuration.screenWidthDp) {
        when (override) {
            UiModeOverride.TV -> true
            UiModeOverride.PHONE -> false
            UiModeOverride.AUTO -> isTvHardware(context, configuration)
        }
    }
}

@Composable
fun QuernAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    properties: DialogProperties = DialogProperties()
) {
    // Android's Dialog creates its own ComposeView that resets LocalDensity to the system default.
    // Re-apply the app's root scaled density (LocalQuernScaledDensity) so dialogs match Interface Size.
    val scaledDensity = LocalQuernScaledDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        val effectiveDensity = scaledDensity ?: LocalDensity.current
        CompositionLocalProvider(LocalDensity provides effectiveDensity) {
            val defaultWidthMod = if (properties.usePlatformDefaultWidth) {
                Modifier.widthIn(min = 280.dp, max = 560.dp)
            } else {
                Modifier
            }
            Surface(
                color = containerColor,
                shape = AlertDialogDefaults.shape,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = defaultWidthMod.then(modifier)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                title()
                            }
                        }
                    }
                    if (text != null) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(bottom = 20.dp)
                            ) {
                                text()
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
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
    heroHeight: Dp = if (isTv) 405.dp else 280.dp,
    heroAspectRatio: Float = if (isTv) 2.37f else 1.45f,
    activeProgress: WatchProgress? = null,
    onResumeDirect: ((WatchProgress) -> Unit)? = null,
    onHeroFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val strings = LocalQuernStrings.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val startPad = QuernHomeSpacing.contentStartPadding(isTv, railInset)
    val endPad = QuernHomeSpacing.contentEndPadding(isTv)
    val primaryCtaFocus = remember { FocusRequester() }
    val contentFocusMemory = LocalContentFocusMemory.current
    var primaryCtaAttached by remember { mutableStateOf(false) }
    if (isTv && contentFocusMemory != null) {
        DisposableEffect(contentFocusMemory, primaryCtaFocus) {
            val entry: () -> Boolean = {
                if (primaryCtaAttached) {
                    runCatching { primaryCtaFocus.requestFocus(); true }.getOrDefault(false)
                } else {
                    false
                }
            }
            contentFocusMemory.fallbackEntry = entry
            onDispose {
                if (contentFocusMemory.fallbackEntry === entry) {
                    contentFocusMemory.fallbackEntry = null
                }
            }
        }
    }
    val isAr = strings.watchNow == "شاهد الآن"
    val viewDetailsLabel = if (isAr) "عرض التفاصيل" else "View Details"
    val heroTextShadow = remember {
        Shadow(color = Color.Black.copy(alpha = 0.68f), offset = Offset(0f, 1.5f), blurRadius = 5f)
    }
    val typeLabel = when (item.type) {
        com.quern.app.domain.model.MediaType.SERIES -> if (isAr) "مسلسل" else "Series"
        com.quern.app.domain.model.MediaType.MOVIE -> if (isAr) "فيلم" else "Movie"
        else -> item.type.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    val categories = remember(strings) {
        listOf(
            "All" to strings.all,
            "Movies" to strings.movies,
            "Series" to strings.series,
            "Servers" to strings.servers
        )
    }

    val shownBackdropKey = remember { arrayOfNulls<MemoryCache.Key>(1) }
    val imageModel = remember(item.backdropUrl, item.posterUrl, heroAspectRatio, isTv, isRtl) {
        val url = item.backdropUrl ?: item.posterUrl
        if (url.isNullOrBlank()) null else {
            buildHeroBackdropRequest(
                context = context,
                imageUrl = url,
                heroAspectRatio = heroAspectRatio,
                isTv = isTv,
                isRtl = isRtl,
                placeholderKey = shownBackdropKey[0],
                crossfadeMs = 240
            )
        }
    }

    // Seamless edge-to-edge hero: buildHeroBackdropRequest bakes both the localized readability
    // shadow and the bottom/rail alpha melt into one cached 800x450 bitmap off the UI thread
    // (ZERO duplicate original+transformed bitmaps, ZERO Offscreen FBO, ZERO per-frame radial gradients).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
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

        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(
                    start = startPad,
                    end = endPad,
                    top = if (isTv) 14.dp else 8.dp,
                    bottom = 6.dp
                )
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
                                modifier = Modifier.focusProperties { down = primaryCtaFocus },
                                onClick = { onCategorySelected?.invoke(key) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .focusProperties { down = primaryCtaFocus }
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
                    if (isTv) {
                        TvTopRightClock()
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

        // Bottom Spotlight Info: Meta Logo (fallback large text), single metadata line, 2-line synopsis, smart CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = startPad,
                    end = endPad,
                    bottom = if (isTv) 26.dp else 16.dp
                )
                .fillMaxWidth(if (isTv) 0.54f else 0.92f)
        ) {
            val logoUrl = item.logoUrl?.takeIf { it.isNotBlank() }
            var logoFailed by remember(logoUrl) { mutableStateOf(false) }
            if (logoUrl != null && !logoFailed) {
                val logoReq = remember(logoUrl) {
                    ImageRequest.Builder(context)
                        .data(logoUrl)
                        .size(560, 180)
                        .crossfade(180)
                        .build()
                }
                AsyncImage(
                    model = logoReq,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoFailed = true },
                    modifier = Modifier
                        .heightIn(max = if (isTv) 72.dp else 50.dp)
                        .widthIn(max = if (isTv) 300.dp else 210.dp)
                )
            } else {
                Text(
                    text = item.title,
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 30.sp else 22.sp,
                    lineHeight = if (isTv) 34.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(shadow = heroTextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))

            // Single metadata line: type • genre • runtime • year • IMDb
            val metaParts = remember(typeLabel, item.genres, item.runtime, item.year) {
                buildList {
                    add(typeLabel)
                    item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    item.runtime?.takeIf { it.isNotBlank() }?.let { add(it) }
                    item.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                metaParts.forEachIndexed { idx, part ->
                    if (idx > 0) {
                        Text("•", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
                    }
                    Text(
                        text = part,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(shadow = heroTextShadow),
                        maxLines = 1
                    )
                }
                item.rating?.takeIf { it.isNotBlank() }?.let { ratingVal ->
                    Text("•", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
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
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = ratingVal,
                            color = Color(0xFFF5C518),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = heroTextShadow)
                        )
                    }
                }
            }

            if (!item.overview.isNullOrBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = item.overview,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    style = TextStyle(shadow = heroTextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            // Smart Hero CTA: "Resume S1 E15 · 34 min left" + smaller "View Details" when in Continue Watching;
            // otherwise a single primary "View Details" pill.
            val downTargetMod = Modifier.focusProperties {
                down = if (firstRowFocusRequester != null && isFirstRowAttached()) {
                    firstRowFocusRequester
                } else {
                    FocusRequester.Default
                }
            }
            if (activeProgress != null && onResumeDirect != null) {
                val resumeLabel = remember(activeProgress, isAr) {
                    val minsLeft = ((activeProgress.durationMs - activeProgress.positionMs)
                        .coerceAtLeast(0L) / 60_000L)
                    val epTag = activeProgress.episodeLabel
                        ?.substringBefore("•")
                        ?.trim()
                        ?.takeIf { tag ->
                            tag.isNotBlank() &&
                                activeProgress.type == MediaType.SERIES &&
                                !tag.all(Char::isDigit)
                        }
                    when {
                        isAr && epTag != null && minsLeft > 0 -> "استئناف $epTag · متبقي $minsLeft د"
                        isAr && epTag != null -> "استئناف $epTag"
                        isAr && minsLeft > 0 -> "استئناف · متبقي $minsLeft د"
                        isAr -> "استئناف"
                        epTag != null && minsLeft > 0 -> "Resume $epTag · $minsLeft min left"
                        epTag != null -> "Resume $epTag"
                        minsLeft > 0 -> "Resume · $minsLeft min left"
                        else -> "Resume"
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var resumeFocused by remember { mutableStateOf(false) }
                    Surface(
                        color = if (resumeFocused) palette.accent else Color.White,
                        shape = RoundedCornerShape(999.dp),
                        border = if (resumeFocused) BorderStroke(2.dp, palette.focusRing) else null,
                        modifier = Modifier
                            .focusRequester(primaryCtaFocus)
                            .onGloballyPositioned { coords -> primaryCtaAttached = coords.isAttached }
                            .then(downTargetMod)
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(
                                onFocusChangedState = { resumeFocused = it },
                                onFocused = { onHeroFocused?.invoke() },
                                onClick = { onResumeDirect(activeProgress) }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (resumeFocused) palette.onAccent else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = resumeLabel,
                                color = if (resumeFocused) palette.onAccent else Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    var detailsFocused by remember { mutableStateOf(false) }
                    Surface(
                        color = if (detailsFocused) palette.accent else Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            width = if (detailsFocused) 2.dp else 1.dp,
                            color = if (detailsFocused) palette.focusRing else Color.White.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .then(downTargetMod)
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(
                                onFocusChangedState = { detailsFocused = it },
                                onFocused = { onHeroFocused?.invoke() },
                                onClick = { onSelect(item) }
                            )
                    ) {
                        Text(
                            text = viewDetailsLabel,
                            color = if (detailsFocused) palette.onAccent else Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.5.dp)
                        )
                    }
                }
            } else {
                var detailsFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (detailsFocused) palette.accent else Color.White,
                    shape = RoundedCornerShape(999.dp),
                    border = if (detailsFocused) BorderStroke(2.dp, palette.focusRing) else null,
                    modifier = Modifier
                        .focusRequester(primaryCtaFocus)
                        .onGloballyPositioned { coords -> primaryCtaAttached = coords.isAttached }
                        .then(downTargetMod)
                        .clip(RoundedCornerShape(999.dp))
                        .tvClickable(
                            onFocusChangedState = { detailsFocused = it },
                            onFocused = { onHeroFocused?.invoke() },
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
        }

        // Bottom-Right Carousel Indicator Dots (━━ • • • • • • •)
        if (totalSlides > 1) {
            val dotCount = totalSlides.coerceAtMost(8)
            val currentDot = activeSlideIndex.coerceIn(0, dotCount - 1)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = endPad, vertical = if (isTv) 26.dp else 18.dp),
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

/**
 * Isolated low-contrast HH:mm clock for top-right of TV Home; updates only at minute boundaries so
 * neither HomeScreen nor LumaHeroBanner recomposes.
 */
@Composable
private fun TvTopRightClock() {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var timeText by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            timeText = formatter.format(Date(now))
            val msToNextMinute = 60_000L - (now % 60_000L) + 150L
            delay(msToNextMinute)
        }
    }
    Text(
        text = timeText,
        color = Color.White.copy(alpha = 0.52f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 3f))
    )
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
    onItemFocused: ((MediaItem, Int) -> Unit)? = null,
    onPreloadNextItems: ((List<MediaItem>) -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemAttachedChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val startPad = QuernHomeSpacing.contentStartPadding(isTv, railInset)
    val endPad = QuernHomeSpacing.contentEndPadding(isTv)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = endPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = if (isTv) 15.dp else 13.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(palette.focusRing)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = row.title,
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 16.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 4f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.origin.displayName.uppercase(),
                color = palette.focusRing.copy(alpha = 0.85f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        val uniqueItems = remember(row.items) { row.items.distinctBy { "${it.type}_${it.id}" } }
        LazyRow(
            contentPadding = PaddingValues(
                start = startPad,
                end = endPad,
                top = QuernHomeSpacing.titleToCardsGap(isTv),
                bottom = QuernHomeSpacing.rowCardsBottomPadding(isTv)
            ),
            horizontalArrangement = Arrangement.spacedBy(QuernHomeSpacing.cardHorizontalGap(isTv))
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, item -> "${row.id}_${item.type}_${item.id}" },
                contentType = { _, _ -> "poster" }
            ) { index, item ->
                val isFirst = index == 0
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
                    onFocused = if (onItemFocused != null || onPreloadNextItems != null) {
                        {
                            onItemFocused?.invoke(item, index)
                            if (onPreloadNextItems != null) {
                                val nextTwo = buildList(2) {
                                    uniqueItems.getOrNull(index + 1)?.let(::add)
                                    uniqueItems.getOrNull(index + 2)?.let(::add)
                                }
                                if (nextTwo.isNotEmpty()) onPreloadNextItems(nextTwo)
                            }
                        }
                    } else null,
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

    // One smooth critically-eased tween drives scale, sheen, and border inside graphicsLayer/draw only
    // (no recomposition on animation frames, and a calm 6% lift avoids row wobble/jitter).
    val focusProgress = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "posterFocus"
    )
    val focusBorderColor = if (palette.isGlossy) palette.focusRing else Color.White
    val focusGlowColor = palette.focusRing
    val isGlossy = palette.isGlossy
    val density = LocalDensity.current
    val baseWidth = if (isTv) cardSize.tvWidthDp else cardSize.phoneWidthDp
    val cardWidth = if (cardShape == CardShapePreset.LANDSCAPE) (baseWidth * 1.45f).toInt().dp else baseWidth.dp
    val cornerShape = remember(cardCorner) { RoundedCornerShape(cardCorner.radiusDp.dp) }
    val decodeWidthPx = remember(cardWidth, density.density) {
        with(density) { (cardWidth.toPx() * 1.15f).toInt().coerceIn(140, 480) }
    }
    val decodeHeightPx = remember(decodeWidthPx, cardShape) {
        (decodeWidthPx / cardShape.aspectRatio).toInt().coerceIn(140, 640)
    }

    val imageModel = remember(item.posterUrl, item.backdropUrl, cardShape, decodeWidthPx, decodeHeightPx) {
        val url = if (cardShape == CardShapePreset.LANDSCAPE) (item.backdropUrl ?: item.posterUrl) else item.posterUrl
        if (url.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(url)
                .size(decodeWidthPx, decodeHeightPx)
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
                val s = 1f + 0.06f * focusProgress.value
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
                    val thick = 2.dp.toPx()
                    val glowWidth = 7.dp.toPx()
                    val glossBrush = if (isGlossy) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.11f),
                                0.30f to Color.White.copy(alpha = 0.04f),
                                0.70f to Color.White.copy(alpha = 0.01f),
                                1.0f to Color.Transparent
                            )
                        )
                    } else null
                    onDrawWithContent {
                        drawContent()
                        val p = focusProgress.value
                        // Draw soft accent glow + crisp focus ring only while focused (zero GPU work on idle cards)
                        if (p > 0.01f) {
                            if (glossBrush != null) {
                                drawRect(brush = glossBrush, alpha = 0.85f * p)
                            }
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

            // Show rating and primary genre on focused cards ONLY (zero extra nodes on idle cards)
            if (isFocused) {
                if (!item.rating.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(Color.Black.copy(alpha = 0.80f), PillShape)
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, null, tint = LumaAmber, modifier = Modifier.size(8.5.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(item.rating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let { genre ->
                    Text(
                        text = genre,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .background(Color.Black.copy(alpha = 0.78f), PillShape)
                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
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
                    fontWeight = FontWeight.SemiBold,
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
