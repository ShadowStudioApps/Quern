package com.quern.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.transform.Transformation
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.QuernPalette
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Soft, blurred colour glows behind full-screen pages (profile picker at launch).
 * Pure drawing, no images shipped: large radial gradients are painted once into a small bitmap
 * (1/4 of the screen) that is stretched with bilinear filtering, which blurs it further. Each frame
 * then costs a single bitmap draw instead of several full-screen gradients.
 *
 * Measured on the Google TV Streamer: animating the gradients was ~79% janky frames (p50 85 ms),
 * and Modifier.blur ~99% (p50 109 ms). Keep it still, and don't add a RenderEffect blur.
 */
@Composable
fun SoftGlowBackground(
    baseColor: Color,
    glowColors: List<Color>,
    modifier: Modifier = Modifier,
    /** 1 = full strength (profile picker); lower behind busy pages so posters and text stay readable. */
    intensity: Float = 1f,
    picker: Boolean = false
) {
    val clamped = intensity.coerceIn(0f, 1f)
    val primaryAlpha = 0.62f * clamped
    val secondaryAlpha = 0.46f * clamped
    // Always four soft orbs; cycle colours when there are fewer.
    val colors = glowColors.ifEmpty { listOf(Color(0xFF294E7A)) }.let { list -> List(4) { list[it % list.size] } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bw = (size.width / SCALE).roundToInt().coerceAtLeast(1)
                val bh = (size.height / SCALE).roundToInt().coerceAtLeast(1)
                val bitmap = ImageBitmap(bw, bh)
                CanvasDrawScope().draw(this, LayoutDirection.Ltr, Canvas(bitmap), Size(bw.toFloat(), bh.toFloat())) {
                    val w = bw.toFloat()
                    val h = bh.toFloat()
                    val rLarge = max(w, h) * 0.64f
                    val rMed = max(w, h) * 0.52f
                    drawRect(baseColor)
                    val orbs = listOf(
                        Triple(Offset(w * 0.14f, h * 0.22f), rLarge, primaryAlpha),
                        Triple(Offset(w * 0.84f, h * 0.26f), rLarge, secondaryAlpha),
                        Triple(Offset(w * 0.28f, h * 0.86f), rMed, secondaryAlpha),
                        Triple(Offset(w * 0.78f, h * 0.82f), rLarge, primaryAlpha * 0.85f)
                    )
                    (if (picker) orbs.take(2) else orbs).forEachIndexed { i, (center, radius, alpha) ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to colors[i].copy(alpha = alpha),
                                    0.45f to colors[i].copy(alpha = alpha * 0.48f),
                                    1.0f to Color.Transparent
                                ),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                    }
                    // Subtle frosted haze & soft outer vignette so text and posters stay effortlessly legible.
                    drawRect(Color.Black.copy(alpha = 0.14f))
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (picker) 0.82f else 0.52f)),
                            center = Offset(w * 0.5f, h * 0.48f),
                            radius = max(w, h) * 0.78f
                        )
                    )
                }
                val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                onDrawBehind {
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bw, bh),
                        dstOffset = IntOffset.Zero,
                        dstSize = dst,
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
    )
}

private const val SCALE = 8f
private const val AMBIENT_WIDTH_PX = 48
private const val AMBIENT_HEIGHT_PX = 27
const val HERO_DECODE_WIDTH_PX = 720
const val HERO_DECODE_HEIGHT_PX = 405

fun buildAmbientVeilTransformation(palette: QuernPalette): Transformation {
    val isDarkTheme = palette.isGlossy || palette.isMonochrome
    val veilColor = if (isDarkTheme) {
        lerp(Color.Black, palette.background, 0.22f).copy(
            alpha = if (palette.isGlossy) 0.72f else 0.66f
        )
    } else {
        lerp(Color.Black, palette.background, 0.35f).copy(alpha = 0.50f)
    }
    return AmbientVeilTransformation(veilArgb = veilColor.toArgb(), isDarkTheme = isDarkTheme)
}

fun buildAmbientBackdropRequest(
    context: Context,
    imageUrl: String,
    palette: QuernPalette,
    placeholderKey: MemoryCache.Key? = null,
    crossfadeMs: Int = 380
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .size(AMBIENT_WIDTH_PX, AMBIENT_HEIGHT_PX)
    .precision(Precision.EXACT)
    .transformations(buildAmbientVeilTransformation(palette))
    .memoryCachePolicy(CachePolicy.ENABLED)
    .apply {
        if (placeholderKey != null) placeholderMemoryCacheKey(placeholderKey)
        if (crossfadeMs > 0) crossfade(crossfadeMs) else crossfade(false)
    }
    .build()

fun buildHeroMeltTransformation(
    heroAspectRatio: Float,
    isTv: Boolean,
    isRtl: Boolean
): HeroMeltTransformation = HeroMeltTransformation(
    aspectBucket = (heroAspectRatio * 10f).roundToInt().coerceIn(12, 60),
    fadeStartEdge = isTv,
    isRtl = isRtl,
    bottomFadeStart = 0.62f,
    startFadeEnd = 0.34f,
    bakeTextShadow = true,
    textShadowCenterY = 0.68f,
    textShadowRadiusFrac = if (isTv) 0.44f else 0.82f,
    bakeTopWhisper = true
)

/**
 * Shared Coil request builder used by BOTH [LumaHeroBanner] and the next-2-cards preloader so
 * Coil caches ONLY the single transformed 800x450 hero bitmap (never both original + transformed).
 */
fun buildHeroBackdropRequest(
    context: Context,
    imageUrl: String,
    heroAspectRatio: Float,
    isTv: Boolean,
    isRtl: Boolean,
    placeholderKey: MemoryCache.Key? = null,
    crossfadeMs: Int = 240
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .size(HERO_DECODE_WIDTH_PX, HERO_DECODE_HEIGHT_PX)
    .precision(Precision.INEXACT)
    .transformations(buildHeroMeltTransformation(heroAspectRatio, isTv, isRtl))
    .memoryCachePolicy(CachePolicy.ENABLED)
    .apply {
        if (placeholderKey != null) placeholderMemoryCacheKey(placeholderKey)
        if (crossfadeMs > 0) crossfade(crossfadeMs) else crossfade(false)
    }
    .build()

/**
 * Full-screen blurred artwork backdrop rendered for free by decoding the title's image at a tiny
 * 48x27 size through Coil (memory-cached), baking the theme dark veil + vignette ONCE into that
 * 48x27 bitmap on Coil's decode thread, and stretching it to full screen with hardware bilinear
 * filtering (ZERO per-frame full-screen radial gradients or alpha layers!). Crossfades for ~380 ms
 * only when [imageUrl] changes; never animates continuously and never uses Modifier.blur or
 * RenderEffect. Leaves the underlying [SoftGlowBackground] visible when [imageUrl] is null/blank.
 */
@Composable
fun AmbientBlurredBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) return
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val shownKey = remember { arrayOfNulls<MemoryCache.Key>(1) }

    val request = remember(imageUrl, palette.background, palette.isGlossy, palette.isMonochrome) {
        buildAmbientBackdropRequest(
            context = context,
            imageUrl = imageUrl,
            palette = palette,
            placeholderKey = shownKey[0],
            crossfadeMs = 380
        )
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        onSuccess = { shownKey[0] = it.result.memoryCacheKey },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Bakes the dark theme veil, bottom grounding gradient, and radial vignette directly into the
 * tiny 48x27 ambient bitmap once off the UI thread so every frame on TV costs ONE single opaque
 * bilinear quad draw instead of multiple full-screen 1080p gradient passes.
 */
private class AmbientVeilTransformation(
    private val veilArgb: Int,
    private val isDarkTheme: Boolean
) : Transformation {
    override val cacheKey: String = "AmbientVeil_${veilArgb}_$isDarkTheme"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        val w = input.width.coerceAtLeast(1)
        val h = input.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(input, 0f, 0f, p)

        // 1. Uniform dark veil tinted with theme background
        p.shader = null
        p.color = veilArgb
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // 2. Gentle bottom grounding so rows remain crisp on bright artwork
        p.color = android.graphics.Color.WHITE
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            if (isDarkTheme) {
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(36, 0, 0, 0),
                    android.graphics.Color.argb(78, 0, 0, 0)
                )
            } else {
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(26, 0, 0, 0),
                    android.graphics.Color.argb(62, 0, 0, 0)
                )
            },
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // 3. Soft radial outer vignette
        p.shader = RadialGradient(
            w * 0.52f,
            h * 0.42f,
            max(w, h) * 0.78f,
            if (isDarkTheme) {
                intArrayOf(
                    android.graphics.Color.argb(16, 0, 0, 0),
                    android.graphics.Color.argb(62, 0, 0, 0),
                    android.graphics.Color.argb(128, 0, 0, 0)
                )
            } else {
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(36, 0, 0, 0),
                    android.graphics.Color.argb(98, 0, 0, 0)
                )
            },
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        out.setHasAlpha(false)
        return out
    }
}

/**
 * Center-crops a hero artwork bitmap to [aspectBucket]/10 and bakes:
 * 1) localized bottom-left readability shadow + subtle top bar whisper (so the artwork stays bright
 *    while white text pops, with ZERO per-frame radial gradient shaders on GPU), and
 * 2) smooth bottom + rail-edge alpha melt (so it dissolves seamlessly into [AmbientBlurredBackdrop]
 *    with ZERO per-frame CompositingStrategy.Offscreen FBO).
 */
class HeroMeltTransformation(
    private val aspectBucket: Int,
    private val fadeStartEdge: Boolean,
    private val isRtl: Boolean,
    private val bottomFadeStart: Float = 0.56f,
    private val startFadeEnd: Float = 0.26f,
    private val bakeTextShadow: Boolean = true,
    private val textShadowCenterY: Float = 0.70f,
    private val textShadowRadiusFrac: Float = 0.44f,
    private val bakeTopWhisper: Boolean = true
) : Transformation {
    override val cacheKey: String =
        "HeroMelt2_${aspectBucket}_${fadeStartEdge}_${isRtl}_${(bottomFadeStart * 100).toInt()}_${(startFadeEnd * 100).toInt()}_${bakeTextShadow}_${(textShadowCenterY * 100).toInt()}_${(textShadowRadiusFrac * 100).toInt()}_$bakeTopWhisper"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        val targetAspect = (aspectBucket / 10f).coerceIn(1.2f, 6.0f)
        val srcW = input.width
        val srcH = input.height
        if (srcW <= 0 || srcH <= 0) return input

        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val cropW: Int
        val cropH: Int
        val cropX: Int
        val cropY: Int
        if (srcAspect < targetAspect) {
            cropW = srcW
            cropH = (srcW / targetAspect).roundToInt().coerceIn(1, srcH)
            cropX = 0
            // Slight upper bias (32%) preserves heads/faces in wide TV hero banners
            cropY = ((srcH - cropH) * 0.32f).roundToInt().coerceIn(0, srcH - cropH)
        } else {
            cropH = srcH
            cropW = (srcH * targetAspect).roundToInt().coerceIn(1, srcW)
            cropX = (srcW - cropW) / 2
            cropY = 0
        }

        val outW = cropW.coerceAtMost(HERO_DECODE_WIDTH_PX)
        val outH = (outW / targetAspect).roundToInt().coerceIn(1, HERO_DECODE_HEIGHT_PX)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            input,
            Rect(cropX, cropY, cropX + cropW, cropY + cropH),
            Rect(0, 0, outW, outH),
            drawPaint
        )

        // Localized readability shading baked once off-thread (artwork remains bright on center/right)
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (bakeTextShadow) {
            val cx = if (isRtl) outW * 0.80f else outW * 0.20f
            val cy = outH * textShadowCenterY
            val rad = (outW * textShadowRadiusFrac).coerceAtLeast(1f)
            shadePaint.shader = RadialGradient(
                cx, cy, rad,
                intArrayOf(
                    android.graphics.Color.argb(148, 0, 0, 0),
                    android.graphics.Color.argb(86, 0, 0, 0),
                    android.graphics.Color.argb(20, 0, 0, 0),
                    android.graphics.Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 0.76f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), shadePaint)
        }
        if (bakeTopWhisper) {
            shadePaint.shader = LinearGradient(
                0f, 0f, 0f, outH * 0.20f,
                intArrayOf(android.graphics.Color.argb(66, 0, 0, 0), android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, outW.toFloat(), outH * 0.20f, shadePaint)
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val m1 = bottomFadeStart + (1f - bottomFadeStart) * 0.34f
        val m2 = bottomFadeStart + (1f - bottomFadeStart) * 0.68f
        val m3 = bottomFadeStart + (1f - bottomFadeStart) * 0.88f
        maskPaint.shader = LinearGradient(
            0f, 0f, 0f, outH.toFloat(),
            intArrayOf(
                android.graphics.Color.BLACK,
                android.graphics.Color.BLACK,
                android.graphics.Color.argb(198, 0, 0, 0),
                android.graphics.Color.argb(86, 0, 0, 0),
                android.graphics.Color.argb(20, 0, 0, 0),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, bottomFadeStart, m1, m2, m3, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), maskPaint)

        if (fadeStartEdge) {
            val s0 = if (isRtl) outW.toFloat() else 0f
            val s1 = if (isRtl) 0f else outW.toFloat()
            maskPaint.shader = LinearGradient(
                s0, 0f, s1, 0f,
                intArrayOf(
                    android.graphics.Color.argb(20, 0, 0, 0),
                    android.graphics.Color.argb(132, 0, 0, 0),
                    android.graphics.Color.argb(230, 0, 0, 0),
                    android.graphics.Color.BLACK,
                    android.graphics.Color.BLACK
                ),
                floatArrayOf(0f, startFadeEnd * 0.30f, startFadeEnd * 0.68f, startFadeEnd, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), maskPaint)
        }

        return out
    }
}
