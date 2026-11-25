package com.quern.app.ui.components

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.transform.Transformation
import com.quern.app.R
import com.quern.app.domain.model.UserProfile
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.QuernPalette
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Quern original "light painting" profile background preset.
 * Rendered purely in code once into a small offscreen ImageBitmap (zero Modifier.blur,
 * zero RenderEffect, zero per-frame animation) and stretched with hardware bilinear filtering.
 */
data class ProfileBackgroundPreset(
    val key: String,
    @StringRes val nameRes: Int,
    val baseTop: Long,
    val baseBottom: Long,
    val ribbonPrimary: Long,
    val ribbonSecondary: Long,
    val bloomColor: Long,
    val ribbonAngleDeg: Float,
    val ribbonCurveBias: Float,
    val grainAlpha: Float,
    val grainSeed: Int,
    val followsTheme: Boolean = false
)

const val DEFAULT_PROFILE_BACKGROUND_KEY = "midnight"

val ProfileBackgroundSet: List<ProfileBackgroundPreset> = listOf(
    ProfileBackgroundPreset(
        key = "midnight",
        nameRes = R.string.profile_bg_midnight,
        baseTop = 0xFF0A0D14,
        baseBottom = 0xFF050609,
        ribbonPrimary = 0xFF284266,
        ribbonSecondary = 0xFF1A2A42,
        bloomColor = 0xFF34527A,
        ribbonAngleDeg = -18f,
        ribbonCurveBias = 0.28f,
        grainAlpha = 0.045f,
        grainSeed = 101,
        followsTheme = true
    ),
    ProfileBackgroundPreset(
        key = "ember",
        nameRes = R.string.profile_bg_ember,
        baseTop = 0xFF140907,
        baseBottom = 0xFF070404,
        ribbonPrimary = 0xFFE86A28,
        ribbonSecondary = 0xFFA82B42,
        bloomColor = 0xFFF59E42,
        ribbonAngleDeg = -24f,
        ribbonCurveBias = 0.34f,
        grainAlpha = 0.06f,
        grainSeed = 202
    ),
    ProfileBackgroundPreset(
        key = "aurora",
        nameRes = R.string.profile_bg_aurora,
        baseTop = 0xFF061116,
        baseBottom = 0xFF04070C,
        ribbonPrimary = 0xFF10B981,
        ribbonSecondary = 0xFF4F46E5,
        bloomColor = 0xFF22D3EE,
        ribbonAngleDeg = 16f,
        ribbonCurveBias = 0.22f,
        grainAlpha = 0.06f,
        grainSeed = 303
    ),
    ProfileBackgroundPreset(
        key = "orchid",
        nameRes = R.string.profile_bg_orchid,
        baseTop = 0xFF130815,
        baseBottom = 0xFF060409,
        ribbonPrimary = 0xFFDB2777,
        ribbonSecondary = 0xFF7C3AED,
        bloomColor = 0xFFF472B6,
        ribbonAngleDeg = -14f,
        ribbonCurveBias = 0.40f,
        grainAlpha = 0.055f,
        grainSeed = 404
    ),
    ProfileBackgroundPreset(
        key = "tide",
        nameRes = R.string.profile_bg_tide,
        baseTop = 0xFF050E1A,
        baseBottom = 0xFF03070D,
        ribbonPrimary = 0xFF0EA5E9,
        ribbonSecondary = 0xFF0F766E,
        bloomColor = 0xFF38BDF8,
        ribbonAngleDeg = 10f,
        ribbonCurveBias = 0.30f,
        grainAlpha = 0.05f,
        grainSeed = 505
    ),
    ProfileBackgroundPreset(
        key = "dune",
        nameRes = R.string.profile_bg_dune,
        baseTop = 0xFF140F09,
        baseBottom = 0xFF070504,
        ribbonPrimary = 0xFFD99B38,
        ribbonSecondary = 0xFF9A4E2C,
        bloomColor = 0xFFF2C97D,
        ribbonAngleDeg = -8f,
        ribbonCurveBias = 0.45f,
        grainAlpha = 0.065f,
        grainSeed = 606
    ),
    ProfileBackgroundPreset(
        key = "nebula",
        nameRes = R.string.profile_bg_nebula,
        baseTop = 0xFF0C081A,
        baseBottom = 0xFF05040A,
        ribbonPrimary = 0xFF8B5CF6,
        ribbonSecondary = 0xFF2563EB,
        bloomColor = 0xFFC084FC,
        ribbonAngleDeg = 22f,
        ribbonCurveBias = 0.32f,
        grainAlpha = 0.085f,
        grainSeed = 707
    ),
    ProfileBackgroundPreset(
        key = "monsoon",
        nameRes = R.string.profile_bg_monsoon,
        baseTop = 0xFF0A1016,
        baseBottom = 0xFF05080B,
        ribbonPrimary = 0xFF64748B,
        ribbonSecondary = 0xFF0891B2,
        bloomColor = 0xFF94A3B8,
        ribbonAngleDeg = -32f,
        ribbonCurveBias = 0.25f,
        grainAlpha = 0.055f,
        grainSeed = 808
    ),
    ProfileBackgroundPreset(
        key = "obsidian",
        nameRes = R.string.profile_bg_obsidian,
        baseTop = 0xFF07080B,
        baseBottom = 0xFF020204,
        ribbonPrimary = 0xFF525866,
        ribbonSecondary = 0xFF272B35,
        bloomColor = 0xFF8D95A5,
        ribbonAngleDeg = -16f,
        ribbonCurveBias = 0.35f,
        grainAlpha = 0.04f,
        grainSeed = 909
    )
)

private val ProfileBackgroundByKey: Map<String, ProfileBackgroundPreset> =
    ProfileBackgroundSet.associateBy { it.key }

fun profileBackgroundFor(key: String?): ProfileBackgroundPreset =
    key?.let { ProfileBackgroundByKey[it] } ?: ProfileBackgroundSet.first()

/** True when the profile explicitly chose a non-default background preset or a valid custom HTTPS URL. */
fun UserProfile?.hasCustomBackground(): Boolean {
    if (this == null) return false
    if (normalizeCustomAvatarUrl(backgroundUrl) != null) return true
    val key = backgroundKey?.takeIf { it.isNotBlank() } ?: return false
    return key != DEFAULT_PROFILE_BACKGROUND_KEY && ProfileBackgroundByKey.containsKey(key)
}

/**
 * Full-screen or tile background for a [UserProfile]:
 * - Code-drawn "light painting" bitmap painted ONCE inside drawWithCache (no blur, no animation).
 * - Optional custom HTTPS background image loaded once via Coil, downscaled to screen/tile and
 *   dimmed off-thread for crisp text legibility, falling back to the preset on error.
 */
@Composable
fun ProfileBackgroundLayer(
    profile: UserProfile?,
    palette: QuernPalette = LocalQuernPalette.current,
    modifier: Modifier = Modifier,
    isTile: Boolean = false
) {
    val preset = remember(profile?.backgroundKey) { profileBackgroundFor(profile?.backgroundKey) }
    val customUrl = remember(profile?.backgroundUrl) { normalizeCustomAvatarUrl(profile?.backgroundUrl) }
    ProfileBackgroundSurface(
        preset = preset,
        customUrl = customUrl,
        palette = palette,
        modifier = modifier,
        isTile = isTile
    )
}

@Composable
fun ProfileBackgroundSurface(
    preset: ProfileBackgroundPreset,
    customUrl: String?,
    palette: QuernPalette = LocalQuernPalette.current,
    modifier: Modifier = Modifier,
    isTile: Boolean = false
) {
    val context = LocalContext.current
    var customFailed by remember(customUrl) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LightPaintingCanvas(
            preset = preset,
            palette = palette,
            isTile = isTile,
            modifier = Modifier.fillMaxSize()
        )

        if (customUrl != null && !customFailed) {
            val targetW = if (isTile) 360 else 1280
            val targetH = if (isTile) 220 else 720
            val request = remember(customUrl, targetW, targetH, isTile) {
                ImageRequest.Builder(context)
                    .data(customUrl)
                    .size(targetW, targetH)
                    .precision(Precision.INEXACT)
                    .transformations(ProfileBackgroundDimTransformation(dimAlpha = if (isTile) 0.44f else 0.62f))
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(280)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                onError = { customFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class LightPaintingCacheKey(
    val presetKey: String,
    val baseTopArgb: Int,
    val baseBottomArgb: Int,
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val bloomArgb: Int,
    val width: Int,
    val height: Int,
    val isTile: Boolean
)

/**
 * Shared thread-safe cache for baked light-painting bitmaps.
 * Identical tiles (e.g. multiple profiles on "Who's watching?" sharing Midnight) deduplicate both
 * in-flight background bakes and cached ImageBitmaps so each (preset, palette, size) bakes ONCE.
 */
private object LightPaintingBitmapCache {
    private val cache = LruCache<LightPaintingCacheKey, ImageBitmap>(24)
    private val inFlight = ConcurrentHashMap<LightPaintingCacheKey, CompletableDeferred<ImageBitmap>>()

    fun getCached(key: LightPaintingCacheKey): ImageBitmap? = synchronized(cache) { cache.get(key) }

    suspend fun getOrBake(
        key: LightPaintingCacheKey,
        preset: ProfileBackgroundPreset,
        baseTop: Color,
        baseBottom: Color,
        ribbonPrimary: Color,
        ribbonSecondary: Color,
        bloomColor: Color
    ): ImageBitmap {
        getCached(key)?.let { return it }

        val mine = CompletableDeferred<ImageBitmap>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val baked = withContext(Dispatchers.Default) {
                bakeLightPaintingBitmap(
                    preset = preset,
                    baseTop = baseTop,
                    baseBottom = baseBottom,
                    ribbonPrimary = ribbonPrimary,
                    ribbonSecondary = ribbonSecondary,
                    bloomColor = bloomColor,
                    bw = key.width,
                    bh = key.height,
                    isTile = key.isTile
                )
            }
            synchronized(cache) { cache.put(key, baked) }
            mine.complete(baked)
            baked
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, mine)
        }
    }
}

private fun bakeLightPaintingBitmap(
    preset: ProfileBackgroundPreset,
    baseTop: Color,
    baseBottom: Color,
    ribbonPrimary: Color,
    ribbonSecondary: Color,
    bloomColor: Color,
    bw: Int,
    bh: Int,
    isTile: Boolean
): ImageBitmap {
    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val w = bw.toFloat()
    val h = bh.toFloat()
    val maxDim = max(w, h)
    val strength = if (isTile) 1.08f else 0.96f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Deep obsidian base gradient
    paint.shader = LinearGradient(
        0f, 0f, 0f, h,
        intArrayOf(baseTop.toArgb(), baseBottom.toArgb()),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)

    // 2. Broad secondary atmospheric glow pool (bottom-right)
    val secCx = w * 0.80f
    val secCy = h * 0.78f
    val secRad = (maxDim * 0.70f).coerceAtLeast(1f)
    paint.shader = RadialGradient(
        secCx, secCy, secRad,
        intArrayOf(
            ribbonSecondary.copy(alpha = (0.44f * strength).coerceAtMost(0.58f)).toArgb(),
            ribbonSecondary.copy(alpha = (0.18f * strength).coerceAtMost(0.28f)).toArgb(),
            android.graphics.Color.TRANSPARENT
        ),
        floatArrayOf(0f, 0.50f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(secCx, secCy, secRad, paint)

    fun cubicX(t: Float, x0: Float, x1: Float, x2: Float, x3: Float): Float {
        val u = 1f - t
        return u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3
    }

    fun cubicY(t: Float, y0: Float, y1: Float, y2: Float, y3: Float): Float {
        val u = 1f - t
        return u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3
    }

    // 3. Velvety optical "light painting" ribbons via overlapping feathered radial light dabs
    canvas.save()
    canvas.rotate(preset.ribbonAngleDeg, w * 0.5f, h * 0.5f)

    val ribbonY = h * preset.ribbonCurveBias
    val p0x = -w * 0.14f; val p0y = ribbonY + h * 0.22f
    val p1x = w * 0.24f;  val p1y = ribbonY - h * 0.26f
    val p2x = w * 0.68f;  val p2y = ribbonY + h * 0.36f
    val p3x = w * 1.14f;  val p3y = ribbonY - h * 0.10f

    val coreColor = lerp(bloomColor, Color.White, 0.32f)
    val steps = 20
    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val cx = cubicX(t, p0x, p1x, p2x, p3x)
        val cy = cubicY(t, p0y, p1y, p2y, p3y)
        val env = (4f * t * (1f - t)).coerceIn(0.18f, 1f)
        val dabColor = lerp(ribbonPrimary, bloomColor, t * 0.72f)

        val outerRad = (h * (0.40f + 0.12f * env)).coerceAtLeast(1f)
        paint.shader = RadialGradient(
            cx, cy, outerRad,
            intArrayOf(
                dabColor.copy(alpha = (0.14f * env * strength).coerceAtMost(0.22f)).toArgb(),
                dabColor.copy(alpha = (0.06f * env * strength).coerceAtMost(0.11f)).toArgb(),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRad, paint)

        val innerRad = (h * (0.13f + 0.05f * env)).coerceAtLeast(1f)
        paint.shader = RadialGradient(
            cx, cy, innerRad,
            intArrayOf(
                coreColor.copy(alpha = (0.14f * env * strength).coerceAtMost(0.22f)).toArgb(),
                dabColor.copy(alpha = (0.05f * env * strength).coerceAtMost(0.09f)).toArgb(),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, innerRad, paint)
    }

    val c0x = -w * 0.10f; val c0y = h * 0.86f
    val c1x = w * 0.32f;  val c1y = h * 0.56f
    val c2x = w * 0.72f;  val c2y = h * 0.90f
    val c3x = w * 1.12f;  val c3y = h * 0.48f
    val counterSteps = 14
    for (i in 0..counterSteps) {
        val t = i.toFloat() / counterSteps.toFloat()
        val cx = cubicX(t, c0x, c1x, c2x, c3x)
        val cy = cubicY(t, c0y, c1y, c2y, c3y)
        val env = (4f * t * (1f - t)).coerceIn(0.16f, 1f)
        val dabColor = lerp(ribbonSecondary, ribbonPrimary, t * 0.55f)
        val rad = (h * 0.32f).coerceAtLeast(1f)
        paint.shader = RadialGradient(
            cx, cy, rad,
            intArrayOf(
                dabColor.copy(alpha = (0.12f * env * strength).coerceAtMost(0.18f)).toArgb(),
                dabColor.copy(alpha = (0.045f * env * strength).coerceAtMost(0.08f)).toArgb(),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, rad, paint)
    }
    canvas.restore()

    // 4. Focal cinema bloom orb (upper-left highlight)
    val bloomCx = w * 0.20f
    val bloomCy = h * 0.22f
    val bloomRad = (maxDim * 0.52f).coerceAtLeast(1f)
    paint.shader = RadialGradient(
        bloomCx, bloomCy, bloomRad,
        intArrayOf(
            bloomColor.copy(alpha = (0.28f * strength).coerceAtMost(0.38f)).toArgb(),
            ribbonPrimary.copy(alpha = (0.10f * strength).coerceAtMost(0.16f)).toArgb(),
            android.graphics.Color.TRANSPARENT
        ),
        floatArrayOf(0f, 0.48f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(bloomCx, bloomCy, bloomRad, paint)

    // 5. Subtle deterministic micro-grain / starlight texture baked ONCE (breaks banding)
    paint.shader = null
    var rng = preset.grainSeed.toLong() * 6364136223846793005L + 1442695040888963407L
    val dotCount = if (isTile) 48 else 110
    val brightGrain = lerp(bloomColor, Color.White, 0.72f)
    repeat(dotCount) {
        rng = rng * 6364136223846793005L + 1442695040888963407L
        val fx = ((rng ushr 33) and 0x7FFFFFFFL).toFloat() / 0x7FFFFFFFL.toFloat()
        rng = rng * 6364136223846793005L + 1442695040888963407L
        val fy = ((rng ushr 33) and 0x7FFFFFFFL).toFloat() / 0x7FFFFFFFL.toFloat()
        rng = rng * 6364136223846793005L + 1442695040888963407L
        val fSize = ((rng ushr 33) and 0xFFL).toFloat() / 255f
        val radius = 0.45f + fSize * (if (preset.key == "nebula") 0.95f else 0.55f)
        val alpha = preset.grainAlpha * (0.45f + fSize * 0.85f)
        paint.color = brightGrain.copy(alpha = alpha.coerceAtMost(0.20f)).toArgb()
        canvas.drawCircle(fx * w, fy * h, radius, paint)
    }

    // 6. Soft cinema readability veil + outer vignette
    paint.shader = null
    paint.color = Color.Black.copy(alpha = if (isTile) 0.14f else 0.22f).toArgb()
    canvas.drawRect(0f, 0f, w, h, paint)

    val vigRad = (maxDim * 0.78f).coerceAtLeast(1f)
    paint.shader = RadialGradient(
        w * 0.50f, h * 0.44f, vigRad,
        intArrayOf(
            android.graphics.Color.TRANSPARENT,
            Color.Black.copy(alpha = if (isTile) 0.44f else 0.58f).toArgb()
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)

    bitmap.setHasAlpha(false)
    return bitmap.asImageBitmap()
}

/**
 * Draws Quern's "light painting" composition without blocking Frame 1:
 * - Frame 1 draws only the plain base vertical gradient (< 0.1 ms, zero bitmap work on main thread).
 * - Bakes the compact ImageBitmap once off the main thread (Dispatchers.Default), cached and
 *   shared across identical tiles/presets, then fades in once (~200 ms) with zero recompositions.
 */
@Composable
private fun LightPaintingCanvas(
    preset: ProfileBackgroundPreset,
    palette: QuernPalette,
    isTile: Boolean,
    modifier: Modifier = Modifier
) {
    val baseTop = if (preset.followsTheme) {
        if (palette.isGlossy) Color(preset.baseTop) else lerp(Color(0xFF0A0C12), palette.background, 0.72f)
    } else {
        Color(preset.baseTop)
    }
    val baseBottom = if (preset.followsTheme) {
        if (palette.isGlossy) Color(preset.baseBottom) else lerp(Color.Black, palette.background, 0.45f)
    } else {
        Color(preset.baseBottom)
    }
    val ribbonPrimary = if (preset.followsTheme && !palette.isGlossy) palette.accent else Color(preset.ribbonPrimary)
    val ribbonSecondary = if (preset.followsTheme && !palette.isGlossy) palette.ambientGlow else Color(preset.ribbonSecondary)
    val bloomColor = if (preset.followsTheme && !palette.isGlossy) lerp(palette.accent, Color.White, 0.25f) else Color(preset.bloomColor)

    val baseBrush = remember(baseTop, baseBottom) {
        Brush.verticalGradient(colors = listOf(baseTop, baseBottom))
    }

    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val cacheKey = remember(
        preset.key,
        baseTop,
        baseBottom,
        ribbonPrimary,
        ribbonSecondary,
        bloomColor,
        targetSize,
        isTile
    ) {
        if (targetSize.width <= 0 || targetSize.height <= 0) {
            null
        } else {
            val maxW = if (isTile) 144 else 224
            val maxH = if (isTile) 108 else 126
            val scale = if (isTile) 3.0f else 8.0f
            val bw = (targetSize.width / scale).roundToInt().coerceIn(36, maxW)
            val bh = (targetSize.height / scale).roundToInt().coerceIn(36, maxH)
            LightPaintingCacheKey(
                presetKey = preset.key,
                baseTopArgb = baseTop.toArgb(),
                baseBottomArgb = baseBottom.toArgb(),
                primaryArgb = ribbonPrimary.toArgb(),
                secondaryArgb = ribbonSecondary.toArgb(),
                bloomArgb = bloomColor.toArgb(),
                width = bw,
                height = bh,
                isTile = isTile
            )
        }
    }

    var bakedBitmap by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { LightPaintingBitmapCache.getCached(it) })
    }
    val fadeAlpha = remember(cacheKey) {
        Animatable(if (bakedBitmap != null) 1f else 0f)
    }

    LaunchedEffect(cacheKey) {
        val key = cacheKey ?: return@LaunchedEffect
        if (bakedBitmap != null) {
            if (fadeAlpha.value < 1f) fadeAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        // Let Frame 1 commit cleanly before starting CPU work on Dispatchers.Default
        withFrameNanos { }
        val ready = LightPaintingBitmapCache.getOrBake(
            key = key,
            preset = preset,
            baseTop = baseTop,
            baseBottom = baseBottom,
            ribbonPrimary = ribbonPrimary,
            ribbonSecondary = ribbonSecondary,
            bloomColor = bloomColor
        )
        bakedBitmap = ready
        fadeAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { measured ->
                if (measured.width > 0 && measured.height > 0 && measured != targetSize) {
                    targetSize = measured
                }
            }
            .drawBehind {
                // Always draw plain base gradient on frame 1 (and underneath during the 200 ms fade-in).
                drawRect(brush = baseBrush)
                val bmp = bakedBitmap
                val a = fadeAlpha.value
                if (bmp != null && a > 0.005f) {
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = a,
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
    )
}

/**
 * Bakes dark readability dimming + soft edge vignette ONCE off the UI thread when decoding a
 * custom HTTPS profile background image, so every frame costs ONE single opaque quad draw.
 */
private class ProfileBackgroundDimTransformation(
    private val dimAlpha: Float
) : Transformation {
    override val cacheKey: String = "ProfileBgDim_${(dimAlpha * 100).roundToInt()}"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        val w = input.width.coerceAtLeast(1)
        val h = input.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(input, 0f, 0f, p)

        // Uniform dark readability scrim
        p.shader = null
        p.color = android.graphics.Color.argb((dimAlpha.coerceIn(0f, 0.9f) * 255f).roundToInt(), 5, 7, 12)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Bottom grounding gradient
        p.color = android.graphics.Color.WHITE
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                android.graphics.Color.argb(32, 0, 0, 0),
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb(120, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Soft outer vignette
        p.shader = RadialGradient(
            w * 0.5f, h * 0.46f, max(w, h) * 0.78f,
            intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb(128, 0, 0, 0)
            ),
            floatArrayOf(0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        out.setHasAlpha(false)
        return out
    }
}

/**
 * D-pad friendly horizontal background picker with live light-painting cards, preset names,
 * check badge on the active choice, and a Custom HTTPS background image URL section below.
 */
@Composable
fun ProfileBackgroundPicker(
    selectedKey: String?,
    customUrl: String?,
    onSelectPreset: (String) -> Unit,
    onApplyCustomUrl: (String?) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    val isTv = remember(context) { isTvHardware(context) }

    val activeKey = remember(selectedKey) { profileBackgroundFor(selectedKey).key }
    val validCustomUrl = remember(customUrl) { normalizeCustomAvatarUrl(customUrl) }
    val listState = rememberLazyListState()

    LaunchedEffect(activeKey) {
        val idx = ProfileBackgroundSet.indexOfFirst { it.key == activeKey }.coerceAtLeast(0)
        listState.animateScrollToItem(idx)
    }

    val cardWidth = if (isCompact) 144.dp else 168.dp
    val cardHeight = if (isCompact) 92.dp else 104.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(ProfileBackgroundSet, key = { _, p -> p.key }) { _, preset ->
                val chosen = validCustomUrl == null && preset.key == activeKey
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (focused) 1.05f else 1f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                    label = "bgCardScale"
                )
                Box(
                    modifier = Modifier
                        .size(width = cardWidth, height = cardHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { onSelectPreset(preset.key) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (focused) 2.5.dp else if (chosen) 2.dp else 1.dp,
                                color = when {
                                    focused -> palette.focusRing
                                    chosen -> Color.White.copy(alpha = 0.88f)
                                    else -> Color.White.copy(alpha = 0.16f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        ProfileBackgroundSurface(
                            preset = preset,
                            customUrl = null,
                            palette = palette,
                            isTile = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Bottom label bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = qString(preset.nameRes),
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = if (chosen || focused) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (chosen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(LumaGreen)
                                .border(1.5.dp, Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        CustomBackgroundUrlCard(
            preset = profileBackgroundFor(activeKey),
            currentUrl = validCustomUrl,
            isTv = isTv,
            isCompact = isCompact,
            onApplyUrl = onApplyCustomUrl
        )
    }
}

@Composable
private fun CustomBackgroundUrlCard(
    preset: ProfileBackgroundPreset,
    currentUrl: String?,
    isTv: Boolean,
    isCompact: Boolean,
    onApplyUrl: (String?) -> Unit
) {
    val palette = LocalQuernPalette.current
    var draft by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var showError by remember { mutableStateOf(false) }
    var showTvDialog by remember { mutableStateOf(false) }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    ) {
                        ProfileBackgroundSurface(
                            preset = preset,
                            customUrl = currentUrl,
                            palette = palette,
                            isTile = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = LumaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = qString(R.string.profile_bg_custom_url_title),
                        color = LumaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_privacy),
                        color = LumaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isTv) {
                // On TV hardware, keyboard opens ONLY when user presses OK on this row.
                var rowFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (rowFocused) palette.accent.copy(alpha = 0.28f) else palette.glassElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = if (rowFocused) 2.dp else 1.dp,
                        color = if (rowFocused) palette.focusRing else palette.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .tvClickable(
                            onFocusChangedState = { rowFocused = it },
                            onClick = {
                                draft = currentUrl.orEmpty()
                                showError = false
                                showTvDialog = true
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = currentUrl ?: qString(R.string.profile_avatar_custom_url_tv_hint),
                            color = if (currentUrl != null) LumaTextPrimary else LumaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentUrl != null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(LumaGreen)
                                    .border(1.5.dp, Color.Black, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
                if (currentUrl != null) {
                    BgActionPill(
                        label = qString(R.string.profile_avatar_custom_url_clear),
                        selected = false,
                        isCompact = isCompact,
                        onClick = {
                            draft = ""
                            showError = false
                            onApplyUrl(null)
                        }
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        showError = false
                    },
                    placeholder = {
                        Text(
                            text = qString(R.string.profile_bg_custom_url_placeholder),
                            color = LumaTextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (draft.isBlank()) {
                                showError = false
                                onApplyUrl(null)
                            } else if (normalized != null) {
                                showError = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_invalid),
                        color = Color(0xFFF87171),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BgActionPill(
                        label = qString(R.string.profile_avatar_custom_url_apply),
                        selected = true,
                        isCompact = isCompact,
                        onClick = {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (normalized != null) {
                                showError = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    )
                    if (currentUrl != null || draft.isNotBlank()) {
                        BgActionPill(
                            label = qString(R.string.profile_avatar_custom_url_clear),
                            selected = false,
                            isCompact = isCompact,
                            onClick = {
                                draft = ""
                                showError = false
                                onApplyUrl(null)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTvDialog) {
        AlertDialog(
            onDismissRequest = { showTvDialog = false },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = qString(R.string.profile_bg_custom_url_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            showError = false
                        },
                        placeholder = {
                            Text(
                                text = qString(R.string.profile_bg_custom_url_placeholder),
                                color = LumaTextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        isError = showError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.focusRing,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showError) {
                        Text(
                            text = qString(R.string.profile_avatar_custom_url_invalid),
                            color = Color(0xFFF87171),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_privacy),
                        color = LumaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draft.isBlank()) {
                            showError = false
                            showTvDialog = false
                            onApplyUrl(null)
                        } else {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (normalized != null) {
                                showError = false
                                showTvDialog = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    }
                ) {
                    Text(qString(R.string.profile_avatar_custom_url_apply), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTvDialog = false }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BgActionPill(
    label: String,
    selected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        selected -> palette.accent.copy(alpha = 0.78f)
        else -> palette.glassElevated
    }
    val fg = if (focused || selected) palette.onAccent else LumaTextPrimary
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = Modifier
            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (isCompact) 11.dp else 7.dp
            )
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
