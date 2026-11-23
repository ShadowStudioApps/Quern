package com.quern.app.player

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.CodecSpecificDataUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

private const val TAG = "QuernDvFallback"

/**
 * Dolby Vision Profile 7 (dvhe.07, dual layer, e.g. UHD Blu-ray remuxes) has no decoder on most
 * Android TV devices, and Media3 1.5.1 only maps profiles 4/8 -> HEVC, 9 -> AVC, 10 -> AV1.
 * Profile 7's base layer is plain HEVC Main10 (HDR10 for dvhe.07.06); the enhancement layer and
 * RPU travel in NAL types 62/63, which an HEVC decoder ignores. So when no Dolby Vision decoder
 * supports profile 7, we present the track as HEVC and play the base layer (HDR10, no DV).
 */
@OptIn(UnstableApi::class)
internal fun Format.isDolbyVisionProfile7(): Boolean {
    if (sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
    val profile = CodecSpecificDataUtil.getCodecProfileAndLevel(this)?.first
    if (profile != null) return profile == CodecProfileLevel.DolbyVisionProfileDvheDtb
    // Fallback when the codecs string couldn't be parsed into a profile
    val c = codecs?.lowercase() ?: return false
    return c.startsWith("dvhe.07") || c.startsWith("dvh1.07")
}

@OptIn(UnstableApi::class)
internal fun Format.asHevcBaseLayer(): Format =
    buildUpon()
        .setSampleMimeType(MimeTypes.VIDEO_H265)
        // Drop the dvhe.07.xx string so HEVC capability checks don't reject it on the DV profile
        .setCodecs(null)
        .build()

@OptIn(UnstableApi::class)
private class DolbyVisionP7FallbackVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    private val codecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    codecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {
    /** Only fall back when the device has no Dolby Vision decoder that handles profile 7. */
    private fun needsFallback(selector: MediaCodecSelector, format: Format): Boolean =
        format.isDolbyVisionProfile7() &&
            super.getDecoderInfos(selector, format, false).none { it.isFormatSupported(format) }

    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int =
        if (needsFallback(mediaCodecSelector, format)) {
            super.supportsFormat(mediaCodecSelector, format.asHevcBaseLayer())
        } else {
            super.supportsFormat(mediaCodecSelector, format)
        }

    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean
    ): List<MediaCodecInfo> =
        if (needsFallback(mediaCodecSelector, format)) {
            super.getDecoderInfos(mediaCodecSelector, format.asHevcBaseLayer(), requiresSecureDecoder)
        } else {
            super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
        }

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        val format = formatHolder.format
        if (format != null && needsFallback(codecSelector, format)) {
            Log.i(TAG, "Dolby Vision profile 7 (${format.codecs}) unsupported; playing HEVC base layer")
            // Swap before the base class stores it, so codec setup never sees the DV mime/profile
            formatHolder.format = format.asHevcBaseLayer()
        }
        return super.onInputFormatChanged(formatHolder)
    }
}

/** DefaultRenderersFactory that swaps its MediaCodecVideoRenderer for the profile-7 fallback one. */
@OptIn(UnstableApi::class)
class QuernRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        val defaults = ArrayList<Renderer>()
        super.buildVideoRenderers(
            context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            eventHandler, eventListener, allowedVideoJoiningTimeMs, defaults
        )
        defaults.forEach { renderer ->
            out += if (renderer is MediaCodecVideoRenderer) {
                DolbyVisionP7FallbackVideoRenderer(
                    context,
                    codecAdapterFactory,
                    mediaCodecSelector,
                    allowedVideoJoiningTimeMs,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                )
            } else {
                renderer
            }
        }
    }
}
