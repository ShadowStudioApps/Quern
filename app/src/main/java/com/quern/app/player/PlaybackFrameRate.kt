package com.quern.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.PlayerView
import com.quern.app.ui.components.isTvHardware
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PlaybackFrameRate(val fps: Float, val source: String)

@OptIn(UnstableApi::class)
@Composable
internal fun MatchPlaybackFrameRate(
    view: PlayerView?,
    player: ExoPlayer,
    lifecycle: Lifecycle,
    enabled: Boolean,
    showInfo: Boolean,
    onFrameRate: (PlaybackFrameRate?) -> Unit
) {
    val reportFrameRate = rememberUpdatedState(onFrameRate)
    val observe = enabled || showInfo
    DisposableEffect(view, player, lifecycle, enabled, observe) {
        if (view == null || !observe) return@DisposableEffect onDispose {}
        val holder = (view.videoSurfaceView as? SurfaceView)?.holder
        val activity = view.context.findActivity()
        val isTv = isTvHardware(view.context)
        val request = FrameRateRequest(activity, isTv)
        var resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        var decoderInputFps: Float? = null
        var measuredFps: Float? = null
        var measurementFinished = false
        var sampler: VideoFrameMetadataListener? = null
        var reportedSource: String? = null
        var reportedFps: Float? = null
        val handler = Handler(player.applicationLooper)

        fun stopSampling() {
            sampler?.let(player::clearVideoFrameMetadataListener)
            sampler = null
        }

        fun applyFps(source: String, fps: Float) {
            if (reportedSource != source || reportedFps != fps) {
                Log.i("QuernFrameRate", "fps source=$source value=$fps")
                reportedSource = source
                reportedFps = fps
                reportFrameRate.value(PlaybackFrameRate(fps, source))
            }
            if (enabled) request.apply(view, holder?.surface, fps)
        }

        fun update() {
            if (!resumed) return
            val selectedFps = player.currentTracks.groups.asSequence()
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group ->
                    (0 until group.length).asSequence()
                        .filter(group::isTrackSelected)
                        .map { group.getTrackFormat(it).frameRate }
                }
                .filter { it.isFinite() && it > 0f }
                .distinct()
                .singleOrNull()
            val decoderFps = decoderInputFps
                ?: player.videoFormat?.frameRate?.takeIf { it.isFinite() && it > 0f }
            val sampledFps = measuredFps
            when {
                selectedFps != null -> applyFps("format", selectedFps)
                decoderFps != null -> applyFps("decoder", decoderFps)
                sampledFps != null -> applyFps("measured", sampledFps)
                else -> {
                    if (!measurementFinished && sampler == null && player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
                        val listener = PresentationTimeSampler { completed, rate, rawRate ->
                            handler.post {
                                if (sampler === completed) {
                                    stopSampling()
                                    measurementFinished = true
                                    measuredFps = rate
                                    Log.i("QuernFrameRate", "fps span=${String.format(Locale.ROOT, "%.3f", rawRate)}")
                                    if (rate != null) applyFps("measured", rate) else {
                                        Log.i("QuernFrameRate", "fps source=unknown value=$rawRate")
                                        request.clear()
                                    }
                                }
                            }
                        }
                        sampler = listener
                        player.setVideoFrameMetadataListener(listener)
                    }
                    return
                }
            }
            stopSampling()
        }

        val playerListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = update()
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                decoderInputFps = null
                measuredFps = null
                measurementFinished = false
                reportedSource = null
                reportedFps = null
                reportFrameRate.value(null)
                request.resetModeDiagnostics()
                stopSampling()
            }
        }
        val formatListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                decoderInputFps = format.frameRate.takeIf { it.isFinite() && it > 0f }
                update()
            }
        }
        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = update()
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = update()
            override fun surfaceDestroyed(holder: SurfaceHolder) = request.clear()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    update()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    resumed = false
                    stopSampling()
                    request.clear()
                }
                else -> Unit
            }
        }
        player.addListener(playerListener)
        player.addAnalyticsListener(formatListener)
        if (!isTv) holder?.addCallback(surfaceCallback)
        lifecycle.addObserver(observer)
        update()
        onDispose {
            reportFrameRate.value(null)
            stopSampling()
            lifecycle.removeObserver(observer)
            if (!isTv) holder?.removeCallback(surfaceCallback)
            player.removeListener(playerListener)
            player.removeAnalyticsListener(formatListener)
            request.clear()
        }
    }
}

private class PresentationTimeSampler(
    private val onComplete: (PresentationTimeSampler, Float?, Float) -> Unit
) : VideoFrameMetadataListener {
    private val deltas = LongArray(120)
    private var count = 0
    private var skip = 5
    private var firstUs = Long.MIN_VALUE
    private var previousUs = Long.MIN_VALUE

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: android.media.MediaFormat?
    ) {
        if (count == deltas.size) return
        if (skip > 0) {
            skip--
            return
        }
        val previous = previousUs
        previousUs = presentationTimeUs
        if (previous == Long.MIN_VALUE || presentationTimeUs <= previous) {
            firstUs = presentationTimeUs
            count = 0
            return
        }
        deltas[count++] = presentationTimeUs - previous
        if (count != deltas.size) return

        deltas.sort()
        val middle = deltas.size / 2
        val median = (deltas[middle - 1].toDouble() + deltas[middle]) / 2.0
        if (deltas.first() < median * 0.5 || deltas.last() > median * 2.0) {
            firstUs = presentationTimeUs
            count = 0
            return
        }
        val measured = (count * 1_000_000.0 / (presentationTimeUs - firstUs)).toFloat()
        val snapped = STANDARD_RATES.minByOrNull { abs(it - measured) }
            ?.takeIf { abs(it - measured) <= it * 0.005f }
        onComplete(this, snapped, measured)
    }

    private companion object {
        val STANDARD_RATES = floatArrayOf(24_000f / 1001, 24f, 25f, 30_000f / 1001, 30f,
            48_000f / 1001, 48f, 50f, 60_000f / 1001, 60f)
    }
}

private class FrameRateRequest(private val activity: Activity?, private val isTv: Boolean) {
    private var requestedSurface: Surface? = null
    private var requestedFps = 0f
    private var previousModeId: Int? = null
    private var requestedModeId: Int? = null
    private var noMatchFps: Float? = null

    fun resetModeDiagnostics() {
        noMatchFps = null
    }

    fun apply(view: PlayerView, surface: Surface?, fps: Float) {
        if (!isTv) {
            if (Build.VERSION.SDK_INT < 30) return
            if (surface == null || !surface.isValid) return
            if (surface === requestedSurface && fps == requestedFps) return
            if (surface !== requestedSurface) clear()
            if (setSurfaceRate(surface, fps)) {
                requestedSurface = surface
                requestedFps = fps
            }
            return
        }

        val window = activity?.window ?: return
        val display = view.display ?: return
        val current = display.mode
        val matchingModes = display.supportedModes.filter { mode ->
            val multiple = (mode.refreshRate / fps).roundToInt()
            multiple >= 1 && abs(mode.refreshRate - fps * multiple) <= 0.01f &&
                mode.physicalWidth == current.physicalWidth && mode.physicalHeight == current.physicalHeight
        }
        val mode = matchingModes.minWithOrNull(
            compareBy<android.view.Display.Mode> { (it.refreshRate / fps).roundToInt() }
                .thenBy { abs(it.refreshRate - fps * (it.refreshRate / fps).roundToInt()) }
                .thenBy { it.modeId != current.modeId }
        )
        if (mode == null) {
            if (noMatchFps != fps) {
                Log.i("QuernFrameRate", "no matching mode for $fps")
                noMatchFps = fps
            }
            clear()
            return
        }
        if (requestedModeId == mode.modeId || (requestedModeId == null && mode.modeId == current.modeId)) return
        val attrs = window.attributes
        if (previousModeId == null) previousModeId = attrs.preferredDisplayModeId
        attrs.preferredDisplayModeId = mode.modeId
        window.attributes = attrs
        requestedModeId = mode.modeId
        Log.i("QuernFrameRate", "request modeId=${mode.modeId} ${mode.physicalWidth}x${mode.physicalHeight}@${mode.refreshRate}")
    }

    fun clear() {
        requestedSurface?.takeIf { it.isValid }?.let { setSurfaceRate(it, 0f) }
        requestedSurface = null
        requestedFps = 0f
        val window = activity?.window
        val previous = previousModeId
        if (window != null && previous != null) {
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId == requestedModeId) {
                attrs.preferredDisplayModeId = previous
                window.attributes = attrs
                Log.i("QuernFrameRate", "restored modeId=$previous")
            }
        }
        previousModeId = null
        requestedModeId = null
    }

    private fun setSurfaceRate(surface: Surface, fps: Float): Boolean = runCatching {
        val compatibility = if (fps > 0f) Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
            else Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
        when {
            Build.VERSION.SDK_INT >= 31 -> surface.setFrameRate(
                fps, compatibility,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
            )
            Build.VERSION.SDK_INT >= 30 -> surface.setFrameRate(fps, compatibility)
        }
    }.isSuccess
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
