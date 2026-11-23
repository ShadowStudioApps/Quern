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
    modifier: Modifier = Modifier
) {
    // Always three glows; repeat colours when there are fewer.
    val colors = glowColors.ifEmpty { listOf(Color(0xFF294E7A)) }.let { list -> List(3) { list[it % list.size] } }
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
                    val r = max(w, h) * 0.55f
                    drawRect(baseColor)
                    listOf(
                        Offset(w * 0.20f, h * 0.28f),
                        Offset(w * 0.80f, h * 0.36f),
                        Offset(w * 0.52f, h * 0.88f)
                    ).forEachIndexed { i, c ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors[i].copy(alpha = 0.58f), colors[i].copy(alpha = 0f)),
                                center = c,
                                radius = r
                            ),
                            radius = r,
                            center = c
                        )
                    }
                    // Darker edges and a light veil so text and avatars stay readable.
                    drawRect(Color.Black.copy(alpha = 0.10f))
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            center = Offset(w / 2f, h / 2f),
                            radius = max(w, h) * 0.75f
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
                        filterQuality = FilterQuality.Low
                    )
                }
            }
    )
}

private const val SCALE = 4f
