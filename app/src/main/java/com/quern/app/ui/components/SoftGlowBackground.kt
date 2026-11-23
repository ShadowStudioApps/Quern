package com.quern.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
    intensity: Float = 1f
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
                    orbs.forEachIndexed { i, (center, radius, alpha) ->
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
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f)),
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
