package com.quern.app.player

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.quern.app.R
import com.quern.app.ui.theme.qString
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackInfoPanel(
    player: ExoPlayer,
    view: View,
    providerName: String,
    frameRate: () -> PlaybackFrameRate?,
    modifier: Modifier = Modifier
) {
    val bandwidth = remember(player) { longArrayOf(0L) }
    DisposableEffect(player) {
        val listener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                bandwidth[0] = bitrateEstimate
            }
        }
        player.addAnalyticsListener(listener)
        onDispose { player.removeAnalyticsListener(listener) }
    }
    var info by remember(player) { mutableStateOf<PlaybackInfo?>(null) }
    LaunchedEffect(player, view) {
        while (true) {
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            info = PlaybackInfo(
                video = player.videoFormat,
                audio = player.audioFormat,
                rate = frameRate(),
                displayHz = view.display?.mode?.refreshRate,
                dropped = counters?.droppedBufferCount,
                bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L),
                bandwidth = bandwidth[0]
            )
            delay(1_000)
        }
    }
    val snapshot = info ?: return
    val video = snapshot.video
    val audio = snapshot.audio
    val fps = snapshot.rate?.let { "${decimal(it.fps, 3)} fps (${it.source})" } ?: "—"
    val resolution = video?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { "${it.width}×${it.height}" } ?: "—"
    val channels = audio?.channelCount?.takeIf { it > 0 }?.toString() ?: "—"
    val display = snapshot.displayHz?.let { "${decimal(it, 3)} Hz" } ?: "—"
    val source = providerName.trim()
        .takeIf { it.isNotEmpty() && it.none { char -> char in "/\\?@=&" } }
        ?.take(80) ?: "—"
    val lines = listOf(
        qString(R.string.player_info_video, resolution, codec(video), dynamicRange(video), fps),
        qString(R.string.player_info_audio, codec(audio), channels),
        qString(R.string.player_info_display, display),
        qString(R.string.player_info_bitrate, bitrate(video?.bitrate?.toLong()), bitrate(snapshot.bandwidth)),
        qString(R.string.player_info_buffer, decimal(snapshot.bufferMs / 1000f, 1), snapshot.dropped?.toString() ?: "—"),
        qString(R.string.player_info_source, source)
    )
    Column(
        modifier = modifier.widthIn(max = 320.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(qString(R.string.player_info_title), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        lines.forEach { line ->
            Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class PlaybackInfo(
    val video: Format?,
    val audio: Format?,
    val rate: PlaybackFrameRate?,
    val displayHz: Float?,
    val dropped: Int?,
    val bufferMs: Long,
    val bandwidth: Long
)

private fun codec(format: Format?): String = format?.sampleMimeType?.substringAfter('/') ?: "—"

@OptIn(UnstableApi::class)
private fun dynamicRange(format: Format?): String = when {
    format == null -> "—"
    format.sampleMimeType == "video/dolby-vision" -> "Dolby Vision"
    format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
    format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR (PQ)"
    format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_SDR -> "SDR"
    else -> "—"
}

private fun bitrate(value: Long?): String =
    if (value != null && value > 0) "${decimal(value / 1_000_000f, 2)} Mbps" else "—"

private fun decimal(value: Float, places: Int): String = String.format(Locale.ROOT, "%.${places}f", value)
